"""Redis-хранилище соответствий payload_id → замаскированные данные.

Общее хранилище для всех воркеров uvicorn. Потокобезопасно (Redis атомарен).
Поддерживает TTL для автоматической очистки устаревших записей.
"""
from __future__ import annotations

import json
import logging
import os
import time
from dataclasses import dataclass, field

import redis

from .masking import MaskedSpan

logger = logging.getLogger("pii.store")


@dataclass
class Record:
    """Запись соответствия для одного payload_id."""
    original_text: str
    masked_text: str
    spans: list[MaskedSpan] = field(default_factory=list)
    created_at: float = field(default_factory=time.time)


class RedisMaskStore:
    """Redis-хранилище соответствий."""

    def __init__(self, ttl_sec: float = 3600.0):
        self._ttl = ttl_sec
        self._redis = redis.Redis(
            host=os.getenv("REDIS_HOST", "redis"),
            port=int(os.getenv("REDIS_PORT", "6379")),
            decode_responses=True,
        )

    def put(self, payload_id: str, record: Record) -> None:
        """Сохраняет запись с TTL."""
        data = {
            "original_text": record.original_text,
            "masked_text": record.masked_text,
            "spans": [s.__dict__ for s in record.spans],
            "created_at": record.created_at,
        }
        self._redis.setex(f"pii:{payload_id}", int(self._ttl), json.dumps(data))

    def get(self, payload_id: str) -> Record | None:
        """Возвращает запись или None, если её нет/истекла."""
        raw = self._redis.get(f"pii:{payload_id}")
        if raw is None:
            return None
        try:
            data = json.loads(raw)
            spans = [MaskedSpan(**s) for s in data.get("spans", [])]
            return Record(
                original_text=data["original_text"],
                masked_text=data["masked_text"],
                spans=spans,
                created_at=data.get("created_at", time.time()),
            )
        except Exception as exc:  # noqa: BLE001
            logger.warning("Ошибка чтения записи из Redis: %s", exc)
            return None

    def __len__(self) -> int:
        return 0

    def clear(self) -> None:
        """Очищает все записи (используется в тестах)."""
        keys = self._redis.keys("pii:*")
        if keys:
            self._redis.delete(*keys)


# Глобальный экземпляр хранилища
store = RedisMaskStore()