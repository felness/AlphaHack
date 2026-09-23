# Модуль безопасности персональных данных

Высоконагруженный сервис маскирования/демаскирования персональных данных (ПД) перед обращением к LLM. Реализует контракт `POST /process` из ТЗ.

## Возможности

- **Идентификация ПД**: ФИО (в т.ч. 2-словные и с инициалами), даты, паспорт, гражданство, орган выдачи, код подразделения, в/у, адрес, email, телефон, ИНН, номер карты (с проверкой Луна), CVV, ПИН, имя держателя.
- **Маскирование**: инициалы для ФИО (`И. И. И.`), звёздочки с сохранением разделителей для номеров (`45** ****56`), маскируется только значение, не метка.
- **Демаскирование**: возврат сохранённого оригинала по `payload_id` (100% точность).
- **Защита от ложных срабатываний**: границы слов, стоп-лист известных персон (Пушкин и др.), адреса отделений банков не маскируются.
- **Безопасность**: проверка систем (`X-System-Id`), rate-limiting (429 + Retry-After), таймаут запроса.
- **Надёжность**: Redis-хранилище с L1-кэшем и деградацией при недоступности Redis.
- **Метрики**: latency, RPS, TPS в Prometheus-формате на `/metrics` (агрегируются в Redis между воркерами).
- **Мониторинг**: готовый стек Prometheus + Grafana.

## Быстрый старт

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 4
```

Требуется Redis: `docker run -d --name redis -p 6379:6379 redis:7-alpine`.

Или через Docker Compose (приложение + Redis):

```bash
docker compose up -d --build
```

## Контракт API

```bash
# Маскирование (первый запрос с новым payload_id)
curl -X POST http://localhost:8000/process \
  -H "Content-Type: application/json" \
  -d '{"payload": "Клиент Иванов Иван Иванович, паспорт 4509 123456", "payload_id": "abc123"}'
# → {"result": "Клиент И. И. И., паспорт 45** ****56"}

# Демаскирование (второй запрос с тем же payload_id)
curl -X POST http://localhost:8000/process \
  -H "Content-Type: application/json" \
  -d '{"payload": "Клиент И. И. И., паспорт 45** ****56", "payload_id": "abc123"}'
# → {"result": "Клиент Иванов Иван Иванович, паспорт 4509 123456"}
```

## Конфигурация

Параметры задаются через переменные окружения:

| Переменная | По умолчанию | Описание |
|---|---|---|
| `REDIS_HOST` | `redis` | Хост Redis |
| `REDIS_PORT` | `6379` | Порт Redis |
| `ALLOWED_SYSTEMS` | `default,test,prod` | Разрешённые системы (`X-System-Id`) |
| `ALLOW_ANONYMOUS` | `true` | Разрешить запросы без `X-System-Id` |
| `RATE_LIMIT_RPS` | `1000` | Лимит RPS на систему |
| `MAX_PAYLOAD_CHARS` | `1000000` | Макс. размер payload |
| `REQUEST_TIMEOUT_SEC` | `9.0` | Таймаут запроса (504 при превышении) |
| `WORKERS` | `4` | Число воркеров uvicorn |

## Мониторинг

Стек Prometheus + Grafana в отдельных контейнерах:

```bash
docker compose -f docker-compose.monitoring.yml up -d
```

- **Prometheus**: `http://localhost:9090` — собирает метрики с `/metrics`
- **Grafana**: `http://localhost:3000` (admin/admin) — дашборд "PII Module Monitoring"

Метрики: `pii_rps`, `pii_tps`, `pii_avg_latency_ms`, `pii_p95_latency_ms`, `pii_total_requests`, `pii_total_errors`.

## Тесты

```bash
pytest tests/ -v
```

Требуется Redis (или `REDIS_HOST=localhost` при локальном Redis).

## Структура

```
app/
  main.py       # FastAPI приложение, POST /process
  detectors.py  # детекторы ПД (regex + словари + суффиксные правила)
  masking.py    # маскирование/демаскирование (стратегии на тип ПД)
  store.py      # Redis-хранилище с L1-кэшем и деградацией
  security.py   # проверка систем, защита логов
  ratelimit.py  # rate-limiting (429 + Retry-After)
  metrics.py    # метрики latency/RPS/TPS (Redis-агрегация)
  config.py     # конфигурация
  pii_types.py  # типы ПД и стратегии маскирования
tests/          # тесты
monitoring/     # конфиги Prometheus и Grafana
```