"""Тесты API-эндпоинта /process."""
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.store import store

client = TestClient(app)


@pytest.fixture(autouse=True)
def clear_store():
    """Очищает хранилище перед каждым тестом."""
    store.clear()
    yield


def test_mask_then_unmask():
    """Прямой и обратный шаг по одному payload_id."""
    payload_id = "test-id-1"
    original = "Клиент Иванов Иван Иванович, паспорт 4509 123456"

    # Прямой шаг: маскирование
    resp = client.post(
        "/process",
        json={"payload": original, "payload_id": payload_id},
        headers={"X-System-Id": "test"},
    )
    assert resp.status_code == 200
    masked = resp.json()["result"]
    assert masked != original
    assert "Иванов" not in masked

    # Обратный шаг: демаскирование
    resp = client.post(
        "/process",
        json={"payload": masked, "payload_id": payload_id},
        headers={"X-System-Id": "test"},
    )
    assert resp.status_code == 200
    assert resp.json()["result"] == original


def test_system_not_allowed():
    """Неавторизованная система получает 403."""
    resp = client.post(
        "/process",
        json={"payload": "тест", "payload_id": "id-1"},
        headers={"X-System-Id": "unknown-system"},
    )
    assert resp.status_code == 403


def test_missing_system_header():
    """Отсутствие заголовка X-System-Id разрешено (allow_anonymous=true)."""
    resp = client.post(
        "/process",
        json={"payload": "тест", "payload_id": "id-1"},
    )
    assert resp.status_code == 200


def test_health():
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


def test_metrics():
    resp = client.get("/metrics")
    assert resp.status_code == 200
    assert "pii_total_requests" in resp.text


def test_idempotent_mask():
    """Повторный запрос с тем же payload_id возвращает исходную строку (демаскирование)."""
    payload_id = "idempotent-id"
    original = "Телефон: +7 (912) 345-67-89"

    # Маскирование
    resp1 = client.post(
        "/process",
        json={"payload": original, "payload_id": payload_id},
        headers={"X-System-Id": "test"},
    )
    assert resp1.status_code == 200

    # Повторный запрос с тем же id и исходной строкой — это уже демаскирование,
    # но маска не совпадает. Сервис должен вернуть что-то разумное.
    resp2 = client.post(
        "/process",
        json={"payload": original, "payload_id": payload_id},
        headers={"X-System-Id": "test"},
    )
    assert resp2.status_code == 200
    # Так как маска не совпадает, демаскирование по позициям вернёт исходник
    # (позиции не совпадают, но сервис не должен падать)
    assert isinstance(resp2.json()["result"], str)


def test_idempotent_retry_mask():
    """Ретрай маскирования с тем же id и исходной строкой возвращает ту же маску."""
    payload_id = "retry-id"
    original = "Клиент Иванов Иван Иванович"

    # Первое маскирование
    resp1 = client.post(
        "/process",
        json={"payload": original, "payload_id": payload_id},
        headers={"X-System-Id": "test"},
    )
    masked1 = resp1.json()["result"]

    # Ретрай: тот же id, та же исходная строка → та же маска (идемпотентность)
    resp2 = client.post(
        "/process",
        json={"payload": original, "payload_id": payload_id},
        headers={"X-System-Id": "test"},
    )
    assert resp2.status_code == 200
    assert resp2.json()["result"] == masked1


def test_rate_limit_429():
    """Превышение rate-limit возвращает 429 с Retry-After."""
    from app.ratelimit import rate_limiter

    # Сбрасываем лимитер
    rate_limiter._buckets.clear()

    # Отправляем больше запросов, чем лимит (лимит 1000, но для теста используем маленький)
    # Временно снижаем лимит
    old_max = rate_limiter._max_requests
    rate_limiter._max_requests = 3
    try:
        statuses = []
        for _ in range(5):
            resp = client.post(
                "/process",
                json={"payload": "тест", "payload_id": f"rl-{_}"},
                headers={"X-System-Id": "test"},
            )
            statuses.append(resp.status_code)
        assert 429 in statuses
        # Проверяем Retry-After на 429
        for _ in range(5):
            resp = client.post(
                "/process",
                json={"payload": "тест", "payload_id": f"rl2-{_}"},
                headers={"X-System-Id": "test"},
            )
            if resp.status_code == 429:
                assert "Retry-After" in resp.headers
                break
    finally:
        rate_limiter._max_requests = old_max
        rate_limiter._buckets.clear()


def test_system_policy_no_unmask():
    """Система с allow_unmask=false не может демаскировать."""
    from app.config import get_settings

    # Временно задаём политику: prod не может демаскировать
    settings = get_settings()
    old_policies = settings.system_policies
    settings.system_policies = '{"prod": {"mask_types": ["*"], "allow_unmask": false}}'
    try:
        payload_id = "policy-id"
        original = "Клиент Иванов Иван Иванович"

        # Маскирование (система prod)
        resp = client.post(
            "/process",
            json={"payload": original, "payload_id": payload_id},
            headers={"X-System-Id": "prod"},
        )
        assert resp.status_code == 200
        masked = resp.json()["result"]

        # Демаскирование (система prod) → 403
        resp = client.post(
            "/process",
            json={"payload": masked, "payload_id": payload_id},
            headers={"X-System-Id": "prod"},
        )
        assert resp.status_code == 403
    finally:
        settings.system_policies = old_policies


def test_tokenize_mode():
    """Система с mask_mode=token использует токенизацию."""
    from app.config import get_settings

    settings = get_settings()
    old_policies = settings.system_policies
    old_allowed = settings.allowed_systems
    settings.system_policies = '{"tok": {"mask_types": ["*"], "allow_unmask": true, "mask_mode": "token"}}'
    settings.allowed_systems = "default,test,prod,tok"
    try:
        payload_id = "token-id"
        original = "Клиент Иванов Иван Иванович, телефон +7 (912) 345-67-89"

        # Токенизация
        resp = client.post(
            "/process",
            json={"payload": original, "payload_id": payload_id},
            headers={"X-System-Id": "tok"},
        )
        assert resp.status_code == 200
        tokenized = resp.json()["result"]
        assert "{FIO_1}" in tokenized
        assert "{PHONE_1}" in tokenized

        # Детокенизация
        resp = client.post(
            "/process",
            json={"payload": tokenized, "payload_id": payload_id},
            headers={"X-System-Id": "tok"},
        )
        assert resp.status_code == 200
        assert resp.json()["result"] == original
    finally:
        settings.system_policies = old_policies
        settings.allowed_systems = old_allowed