# AlphaHack — Модуль безопасности персональных данных

Прокси-сервис между системой-потребителем и LLM: **идентификация → маскирование → демаскирование** персональных данных (17 категорий).

```
Система-потребитель → Модуль (идентификация → маскирование) → LLM
Система-потребитель ← Модуль (демаскирование) ← LLM
```

## Технологии

- **Kotlin** 2.1.21
- **Spring Boot** 3.5.16 (Spring Framework 6.2)
- **Java** 21+ (виртуальные потоки)
- **Redis** 7 (Lettuce)
- **Micrometer / Prometheus** (метрики)
- **Resilience4j** (circuit breaker)
- **Bucket4j** (rate limiting)

## Быстрый старт

### 1. Запуск Redis

```bash
cd local-environment
docker compose up -d redis
```

### 2. Сборка и запуск сервиса

```bash
./gradlew bootJar
java -jar build/libs/pii-security-module-0.0.1-SNAPSHOT.jar --server.port=8080
```

### 3. Проверка

```bash
# Health check
curl http://localhost:8080/actuator/health

# Маскирование (первый запрос с новым payload_id)
curl -X POST http://localhost:8080/process \
  -H "Content-Type: application/json" \
  -d '{"payload":"паспорт 4509 123456, email ivanov@mail.ru","payload_id":"test-1"}'
# → {"result":"паспорт **** ******, email ******@****.**"}

# Демаскирование (второй запрос с тем же payload_id)
curl -X POST http://localhost:8080/process \
  -H "Content-Type: application/json" \
  -d '{"payload":"паспорт **** ******, email ******@****.**","payload_id":"test-1"}'
# → {"result":"паспорт 4509 123456, email ivanov@mail.ru"}
```

## API-контракт

```
POST /process
Content-Type: application/json

Запрос:  { "payload": "<строка>", "payload_id": "<идентификатор>" }
Ответ:   { "result": "<строка>" }
```

| Шаг | payload_id | payload | Действие | Ответ |
|-----|------------|---------|----------|-------|
| 1 | новый | исходная строка | **маскирование** | маска |
| 2 | тот же | ваша маска | **демаскирование** | исходная строка |

### Коды ответов

| Код | Описание |
|-----|----------|
| **200** | Успешная обработка |
| **400** | Некорректный запрос (нет payload/payload_id, payload слишком большой) |
| **403** | Неизвестная или отключённая система (`X-System-Id`) |
| **404** | Соответствие по payload_id не найдено |
| **429** | Too Many Requests (с `Retry-After`) |
| **500** | Внутренняя ошибка |
| **503** | Redis недоступен (деградация) |

## Идентифицируемые типы ПДН (17 категорий)

| № | Тип | Пример | Детектор |
|---|-----|--------|----------|
| 1 | ФИО | Иванов Иван Иванович | `NameDetector` (словарный, контекст) |
| 2 | Дата рождения | 12.05.1990 | `DateDetector` |
| 3 | Место рождения | г. Москва | `PlaceOfBirthDetector` (словарный, контекст) |
| 4 | Серия и номер паспорта | 4509 123456 | `PassportDetector` |
| 5 | Гражданство | гражданин РФ | `CitizenshipDetector` (словарный) |
| 6 | Орган, выдавший паспорт | ОУФМС России по г. Москве | `PassportIssuerDetector` (словарный, контекст) |
| 7 | Код подразделения | 770-001 | `DepartmentCodeDetector` (контекст) |
| 8 | Дата выдачи паспорта | 15.03.2015 | `DateDetector` (по контексту) |
| 9 | Водительское удостоверение | 77 12 345678 | `DriverLicenseDetector` (контекст) |
| 10 | Адрес | г. Москва, ул. Тверская, д. 1 | `AddressDetector` (словарный, контекст) |
| 11 | Email | ivanov@mail.ru | `EmailDetector` |
| 12 | Телефон | +7 (900) 123-45-67 | `PhoneDetector` |
| 13 | ИНН | 770100000079 | `InnDetector` (checksum) |
| 14 | Номер карты | 4276 1234 5678 9014 | `CardDetector` (Luhn) |
| 15 | CVV | 123 | `CvvDetector` (контекст) |
| 16 | ПИН-код | 1234 | `PinDetector` (контекст) |
| 17 | Имя держателя карты | IVANOV IVAN | `CardHolderNameDetector` (контекст) |

## Настройка

### Конфигурация систем-потребителей

В `src/main/resources/application.yml` (секция `pii.systems`):

```yaml
pii:
  systems:
    default:                    # система по умолчанию (запросы без заголовка X-System-Id)
      enabled: true
      masking-types: [ALL]      # какие типы ПДН маскировать (ALL = все)
      unmasking-enabled: true   # демаскирование вкл/выкл
      mask-format: STAR         # формат маски (STAR | TOKEN)
    system-a:
      enabled: true
      masking-types: [FULL_NAME, PASSPORT_SERIES_NUMBER, PHONE, EMAIL]
      unmasking-enabled: true
    system-b:
      enabled: false            # доступ отключён
```

- **Определение системы:** по заголовку `X-System-Id`. Если заголовка нет → система `default`.
- **Фильтрация типов ПДН:** система маскирует только перечисленные типы.
- **Демаскирование:** `unmasking-enabled: false` запрещает демаскирование для системы.
- **Формат маски:** `STAR` (`*`) или `TOKEN` (`X`).

### Расширение списка ПДН

Новый тип ПДН = новый детектор (реализует интерфейс `Detector`) + значение в `PiiType` + правило в конфиге. Ядро не переписывается.

```kotlin
@Component
class MyDetector : Detector {
    override val supportedTypes: Set<PiiType> = setOf(PiiType.MY_TYPE)
    override fun detect(text: String): List<DetectedEntity> { ... }
}
```

## Безопасность

- **Логирование:** логируются только типы ПДН и агрегаты, **никогда** — значения (`LogMasker`).
- **Метрики:** только агрегаты (latency, RPS, количество), без значений ПДН.
- **Ограничение систем:** только системы из конфигурации имеют доступ (403 для неизвестных).
- **Rate limiting:** Bucket4j → 429 с `Retry-After`.
- **Circuit breaker:** Resilience4j → 503 при недоступности Redis.
- **Ограничение размера payload:** защита от DoS (400 при превышении `max-payload-size`).

## Метрики и мониторинг

- `/actuator/prometheus` — метрики Prometheus
- `/actuator/health` — health-check (включая Redis)

Ключевые метрики: `pii_requests_total`, `pii_requests_masking_total`, `pii_requests_unmasking_total`, `pii_latency_seconds`, `pii_entities_detected_total`, `pii_rate_limited_total`.

### Локальный мониторинг (Prometheus + Grafana)

Мониторинг запускается **локально** на машине разработчика и скрейпит метрики с удалённого сервера через интернет. На сервер ничего ставить не нужно.

```bash
docker compose -f docker-compose.monitoring.yml up -d
```

- **Grafana:** http://localhost:3000 (admin / admin)
- **Prometheus:** http://localhost:9090

Дашборд **«PII Security Module — Overview»** (папка `PII`) показывает:
- RPS (всего / маскирование / демаскирование)
- Latency p50/p95/p99 (общая и по направлениям)
- Количество запросов и ошибок
- Типы обнаруженных ПД (17 категорий)
- Rate limited (429) и error rate

> Конфиги мониторинга (`docker-compose.monitoring.yml`, `monitoring/`) не заливаются на гит — они локальные (см. `.gitignore`).

## Производительность

Результаты нагрузочного теста (`load_test.py`, 100 000 запросов, 100 параллельно):

| Метрика | Значение |
|---------|----------|
| Успешных запросов | 100% |
| Roundtrip OK | 100% |
| RPS | 1210 |
| Latency p50 | 0.028 сек |
| Latency p95 | 0.106 сек |
| Latency p99 | 0.131 сек |

## Инструкция для жюри

1. **Запустить сервис** (см. «Быстрый старт»).
2. **Отправить тестовый текст** на маскирование:
   ```bash
   curl -X POST http://localhost:8080/process \
     -H "Content-Type: application/json" \
     -d '{"payload":"Иванов Иван Иванович, паспорт 4509 123456, email ivanov@mail.ru, телефон +7 (900) 123-45-67","payload_id":"demo-1"}'
   ```
3. **Получить замаскированный результат** — ПДН заменены на `*`.
4. **Отправить маску обратно** с тем же `payload_id` — получить оригинал.
5. **Проверить ловушки**: «поэт Александр Пушкин», «адрес отделения Банка» — не маскируются.
6. **Посмотреть логи** — типы ПДН без значений.
7. **Посмотреть метрики** — `/actuator/prometheus`.

## Ограничения и план развития

### Ограничения
- **Даты текстом** («двенадцатого мая») не распознаются (только числовые форматы).
- **Документы кроме паспорта РФ** (загранпаспорт, СНИЛС) не распознаются.
- **Контекстное маскирование** (PIN + карта) не реализовано.
- **RPS 2000** не достигнут (текущий 1210).

### План развития
- ML/NER-модель для неструктурированных типов (ФИО, адреса) — повышение точности.
- Распознавание дат текстом и дополнительных документов.
- Контекстное маскирование по комбинации типов.
- Оптимизация для RPS 2000 (кэширование, асинхронность).

## Документация

- [Дизайн-документ](docs/architecture.md) — полная архитектура
- [Тестовая матрица](docs/test-matrix.md) — план тест-кейсов
- [Отчёт ревью](docs/review-report.md) — анализ соответствия требованиям