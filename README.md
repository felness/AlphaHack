# AlphaHack — модуль защиты персональных данных

Backend-сервис на Kotlin/Spring Boot, который обнаруживает персональные данные (ПД) в тексте, маскирует их перед передачей во внешнюю систему и восстанавливает исходные значения в ответе.

```text
Система-потребитель → POST /process → обнаружение ПД → маскирование
Система-потребитель ← POST /process ← восстановление ← замаскированный ответ
```

## Возможности

- единый API `POST /process` для маскирования и демаскирования;
- 17 обязательных категорий ПД из задания и 2 дополнительные: СНИЛС и загранпаспорт;
- regex-, checksum-, словарные и контекстные детекторы;
- сохранение длины и разделителей при маскировании;
- форматы маски `STAR`, `TOKEN` и `SYNTHETIC`;
- Redis с TTL для хранения соответствий либо in-memory режим для локальной разработки;
- отдельные правила для систем-потребителей через заголовок `X-System-Id`;
- rate limiting, circuit breaker и безопасные агрегированные метрики;
- виртуальные потоки Java 21.

## Стек

- Kotlin 2.1.21;
- Java 21;
- Spring Boot 3.5.16;
- Redis 7;
- Gradle 8.14.2;
- Micrometer/Prometheus, Resilience4j, Bucket4j.

## Быстрый старт

### Вариант 1: Docker Compose

Требуется Docker с Compose.

```bash
docker compose up --build
```

Сервис будет доступен на `http://localhost:8080`, Redis — на `localhost:6379`.

### Вариант 2: локальный запуск

Требуются JDK 21 и запущенный Redis:

```bash
docker compose -f local-environment/docker-compose.yml up -d
./gradlew bootRun
```

Для разработки без Redis можно использовать in-memory хранилище:

```bash
PII_STORE_TYPE=in-memory ./gradlew bootRun
```

## API

### Запрос

```http
POST /process
Content-Type: application/json
X-System-Id: default

{
  "payload": "паспорт 4509 123456, email ivanov@mail.ru",
  "payload_id": "demo-1"
}
```

Заголовок `X-System-Id` необязателен: без него используется система `default`.

### Маскирование

Первый запрос с новым `payload_id` обнаруживает ПД, сохраняет соответствие и возвращает маску:

```bash
curl -X POST http://localhost:8080/process \
  -H 'Content-Type: application/json' \
  -d '{"payload":"паспорт 4509 123456, email ivanov@mail.ru","payload_id":"demo-1"}'
```

```json
{"result":"паспорт **** ******, email ******@****.**"}
```

### Демаскирование

Повторный запрос с тем же `payload_id` и полученной маской восстанавливает исходный текст:

```bash
curl -X POST http://localhost:8080/process \
  -H 'Content-Type: application/json' \
  -d '{"payload":"паспорт **** ******, email ******@****.**","payload_id":"demo-1"}'
```

```json
{"result":"паспорт 4509 123456, email ivanov@mail.ru"}
```

Повтор исходного текста с тем же `payload_id` идемпотентен и возвращает ранее созданную маску. Запись привязана к системе-потребителю: демаскирование от имени другой системы запрещено.

### Коды ответов

| Код | Значение |
|---:|---|
| `200` | Запрос обработан |
| `400` | Некорректный JSON, пустые поля или превышен лимит `payload` |
| `403` | Система неизвестна, отключена или не владеет записью |
| `429` | Превышен лимит запросов; возвращается `Retry-After` |
| `500` | Непредвиденная внутренняя ошибка |
| `503` | Redis недоступен или circuit breaker открыт |

## Поддерживаемые типы ПД

Обязательные категории: ФИО, дата и место рождения, паспорт РФ, гражданство, орган выдачи паспорта, код подразделения, дата выдачи паспорта, водительское удостоверение, адрес, email, телефон, ИНН, номер карты, CVV, PIN и имя держателя карты.

Дополнительно реализованы СНИЛС и загранпаспорт. Полная карта детекторов и правила разрешения пересечений описаны в [архитектуре](docs/architecture.md).

## Конфигурация

Основные параметры находятся в `src/main/resources/application.yml`.

| Параметр | Значение по умолчанию | Назначение |
|---|---:|---|
| `pii.store.type` | `redis` | `redis` или `in-memory` |
| `pii.max-payload-size` | `1000000` | Максимальная длина текста в символах |
| `pii.correlation-ttl-seconds` | `86400` | TTL записи в Redis |
| `pii.rate-limit-rps` | `2000` | Лимит запросов в секунду на экземпляр |
| `pii.systems.default.masking-types` | `[ALL]` | Разрешённые типы ПД |
| `pii.systems.default.unmasking-enabled` | `true` | Разрешение демаскирования |
| `pii.systems.default.mask-format` | `STAR` | `STAR`, `TOKEN` или `SYNTHETIC` |

Для Redis используются `REDIS_HOST`, `REDIS_PORT` и `REDIS_PASSWORD`; порт приложения задаётся через `SERVER_PORT`.

## Проверка качества

```bash
./gradlew clean test
./gradlew ktlintCheck
```

Интеграционные тесты Redis выполняются при доступном Docker; без Docker они пропускаются. Скрипт `load_test.py` предназначен для отдельного нагрузочного прогона уже запущенного сервиса.

## Структура

```text
src/main/kotlin/com/alfahack/pii/
├── controller/    # HTTP API
├── service/       # оркестрация потока обработки
├── detection/     # детекторы и разрешение пересечений
├── masking/       # создание маски
├── unmasking/     # восстановление значений
├── store/         # Redis и in-memory хранилища
├── config/        # системы-потребители и rate limiting
├── metrics/       # Micrometer-метрики
├── security/      # безопасное представление данных в логах
└── exception/     # единый формат ошибок
```

Подробное устройство и ограничения: [docs/architecture.md](docs/architecture.md).
