"""FastAPI приложение: Модуль безопасности персональных данных.

Единый эндпоинт POST /process по контракту из ТЗ:
- первый запрос с новым payload_id → маскирование
- второй запрос с тем же payload_id → демаскирование
"""
from __future__ import annotations

import logging
import time

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel, Field

from .config import get_settings
from .masking import detokenize_text, mask_text, tokenize_text, unmask_text
from .metrics import metrics
from .ratelimit import rate_limiter
from .security import is_system_allowed, sanitize_for_log
from .store import Record, store

logger = logging.getLogger("pii")


class ProcessRequest(BaseModel):
    payload: str = Field(..., description="Строка для обработки")
    payload_id: str = Field(..., description="Идентификатор корреляции")


class ProcessResponse(BaseModel):
    result: str


app = FastAPI(
    title="Модуль безопасности ПД",
    version="1.0.0",
    description="Прокси-сервис маскирования/демаскирования персональных данных",
)


@app.middleware("http")
async def metrics_middleware(request: Request, call_next):
    """Собирает метрики latency/RPS для каждого запроса."""
    start = time.monotonic()
    try:
        response = await call_next(request)
        latency = time.monotonic() - start
        tokens = 0
        if request.url.path == "/process":
            # грубая оценка токенов по длине payload
            try:
                body = await request.body()
                tokens = len(body) // 4
            except Exception:
                pass
        metrics.record_request(latency, tokens, error=response.status_code >= 500)
        return response
    except Exception:
        latency = time.monotonic() - start
        metrics.record_request(latency, error=True)
        raise


@app.post("/process", response_model=ProcessResponse)
async def process(
    req: ProcessRequest,
    x_system_id: str | None = Header(default=None, alias="X-System-Id"),
):
    """Обрабатывает запрос: маскирование или демаскирование по payload_id."""
    settings = get_settings()

    # Проверка системы-потребителя
    if not is_system_allowed(x_system_id):
        logger.warning("Доступ запрещён для system_id=%s", sanitize_for_log(str(x_system_id)))
        raise HTTPException(status_code=403, detail="System not allowed")

    # Rate-limiting: 429 при перегрузке
    limiter_key = x_system_id or "unknown"
    allowed, retry_after = rate_limiter.allow(limiter_key)
    if not allowed:
        logger.warning("Rate limit превышен для system_id=%s", limiter_key)
        raise HTTPException(
            status_code=429,
            detail="Too Many Requests",
            headers={"Retry-After": str(max(1, int(retry_after)))},
        )

    # Проверка размера payload
    if len(req.payload) > settings.max_payload_chars:
        logger.warning("Payload слишком большой: %d символов", len(req.payload))
        raise HTTPException(status_code=413, detail="Payload too large")

    # Политика системы: какие типы маскировать, режим и разрешено ли демаскирование
    policy = settings.get_system_policy(x_system_id or "default")
    mask_types = policy["mask_types"]
    allow_unmask = policy["allow_unmask"]
    mask_mode = policy["mask_mode"]

    # Проверяем, есть ли уже запись для этого payload_id
    existing = store.get(req.payload_id)

    if existing is None:
        # Прямой шаг: маскирование или токенизация
        logger.info("Обработка (mode=%s) payload_id=%s", mask_mode, req.payload_id)
        if mask_mode == "token":
            mask_result = tokenize_text(req.payload, mask_types)
        else:
            mask_result = mask_text(req.payload, mask_types)
        record = Record(
            original_text=req.payload,
            masked_text=mask_result.masked_text,
            spans=mask_result.spans,
        )
        store.put(req.payload_id, record)
        logger.info(
            "Обработка завершена payload_id=%s, найдено ПД=%d",
            req.payload_id,
            len(mask_result.spans),
        )
        return ProcessResponse(result=mask_result.masked_text)

    # Запись уже существует. Различаем два случая:
    # 1) Пришла наша маска → демаскирование
    # 2) Пришла исходная строка (ретрай маскирования) → идемпотентно вернуть маску
    if req.payload == existing.original_text:
        # Ретрай маскирования: возвращаем сохранённую маску (идемпотентность)
        logger.info("Идемпотентный ответ: повторное маскирование payload_id=%s", req.payload_id)
        return ProcessResponse(result=existing.masked_text)

    # Обратный шаг: демаскирование
    if not allow_unmask:
        logger.warning("Демаскирование запрещено для system_id=%s", x_system_id)
        raise HTTPException(status_code=403, detail="Unmasking not allowed for this system")
    logger.info("Демаскирование payload_id=%s", req.payload_id)
    # Определяем режим по сохранённым span (есть токены → детокенизация)
    has_tokens = any(s.token for s in existing.spans)
    if has_tokens:
        result = detokenize_text(req.payload, existing.spans)
    else:
        result = unmask_text(req.payload, existing.spans)
    logger.info("Демаскирование завершено payload_id=%s", req.payload_id)
    return ProcessResponse(result=result)


@app.get("/health")
async def health():
    """Проверка живости сервиса."""
    return {"status": "ok"}


@app.get("/metrics", response_class=PlainTextResponse)
async def metrics_endpoint():
    """Метрики в Prometheus-формате."""
    return metrics.render_prometheus()