# Отчёт о ревью реализации: Модуль безопасности персональных данных (AlphaHack)

> **⚠️ ВНИМАНИЕ:** Этот отчёт **устарел** и не соответствует текущему коду. Он был составлен на раннем этапе реализации. Актуальное состояние см. в [criteria-review.md](criteria-review.md) и [README.md](../README.md).
>
> **Что изменилось с момента отчёта:**
> - `maxPayloadSize`, `unmaskingEnabled`, `maskFormat` — **реализованы** и используются.
> - Тесты на 429/503/403/безопасность — **добавлены** (`RateLimitTest`, `CircuitBreakerTest`, `SystemAccessTest`, `SecurityLogTest`).
> - Все 17 категорий ПД + загранпаспорт + СНИЛС — **реализованы** детекторы.
> - Даты текстом, регистронезависимость ФИО — **реализованы**.
> - Метрики TPS/RPS — **добавлены**.
> - Привязка записи к системе — **реализована**.

> **Дата:** 2026-09-22
> **Ветка:** `feature/backend-design`
> **Объект ревью:** `src/main/kotlin/com/alfahack/pii/`, `src/test/kotlin/com/alfahack/pii/`, `src/test/resources/*.csv`, `docs/architecture.md`, `docs/test-matrix.md`
> **Метод:** ручной анализ кода и тестов на соответствие функциональным/нефункциональным требованиям и полноту параметризованного покрытия.

---

## 1. Сводка

| Блок | Оценка | Комментарий |
|------|--------|-------------|
| Функциональные требования (17 категорий ПД) | **Выполнено (16/17 детекторов, 17/17 типов)** | Все 17 типов имеют детектор. Но 11 из них покрыты только юнит-тестами, не параметризованными CSV. |
| Нефункциональные требования | **Частично (≈ 6/12)** | Виртуальные потоки, rate limiting, circuit breaker, LogMasker реализованы. **Нет тестов** на 429/503/403/безопасность. `maxPayloadSize`, `unmaskingEnabled`, `maskFormat` **не используются**. |
| Параметризованные тесты (CSV) | **Неполно (6/17 категорий)** | `masking-cases.csv` покрывает 6 категорий + MULTI; `negative-cases.csv` — 6 категорий + контекст. 11 категорий не в CSV. |
| Документация | **Устарела** | `docs/test-matrix.md` утверждает, что реализованы только 6 детекторов, но в коде их 16. Матрица не отражает текущее состояние. |

**Итоговая оценка:** реализация функционального ядра (детекция/маскирование/демаскирование/Redis/идемпотентность) выполнена качественно и покрыта тестами. Критичные пробелы — в нефункциональных требованиях (безопасность, отказоустойчивость, ограничение размера payload) и в полноте параметризованного покрытия.

---

## 2. Функциональные требования (17 категорий ПД)

> **Важно:** `docs/test-matrix.md` (раздел 0.4, 8.1) утверждает, что реализованы только 6 regex-детекторов, а 11 категорий «не имеют детектора». **Это устаревшая информация.** В текущем коде реализованы все 17 категорий (11 regex + 5 словарных детекторов). Матрица должна быть обновлена.

| № | Категория | Детектор (файл) | Юнит-тест | Параметр. CSV (masking) | Негативный CSV | Вариации формата |
|---|-----------|-----------------|-----------|------------------------|----------------|------------------|
| 1 | FULL_NAME | `dictionary/NameDetector.kt` | ✅ `NewDetectorsTest` | ❌ | ✅ `neg-14..18` | ⚠️ только 2–3 слова кириллицей; инициалы `Иванов И. И.` не покрыты |
| 2 | DATE_OF_BIRTH | `regex/DateDetector.kt` | ✅ `DetectorsTest` | ✅ `csv-04..06` | ✅ `neg-13` | ✅ дд.мм.гггг, гггг-мм-дд, дд/мм/гггг; ❌ текстовый формат «двенадцатого мая» |
| 3 | PLACE_OF_BIRTH | `dictionary/PlaceOfBirthDetector.kt` | ✅ `NewDetectorsTest` | ❌ | ❌ | ⚠️ только `г. Город`; «родился в Москве» не покрыт |
| 4 | PASSPORT_SERIES_NUMBER | `regex/PassportDetector.kt` | ✅ `DetectorsTest` | ✅ `csv-01..03` | ✅ `neg-01..03` | ✅ `4509 123456`, `4509-123456`, «серия/номер»; ❌ `4509123456` (без разделителя) не маскируется — расхождение с матрицей PS-03 |
| 5 | CITIZENSHIP | `dictionary/CitizenshipDetector.kt` | ✅ `NewDetectorsTest` | ❌ | ❌ | ⚠️ только «гражданин РФ/России/Российской Федерации» |
| 6 | PASSPORT_ISSUER | `dictionary/PassportIssuerDetector.kt` | ✅ `NewDetectorsTest` | ❌ | ❌ | ⚠️ только ОУФМС/УВМ/МВД России |
| 7 | PASSPORT_DEPARTMENT_CODE | `regex/DepartmentCodeDetector.kt` | ✅ `NewDetectorsTest` | ❌ | ❌ | ⚠️ только `770-001`; `770 001` (пробел) не покрыт |
| 8 | PASSPORT_ISSUE_DATE | `regex/DateDetector.kt` (resolveType) | ❌ (нет отдельного теста) | ❌ | ❌ | ⚠️ различается по контексту «выдан/дата выдачи»; теста на различение типа нет |
| 9 | DRIVER_LICENSE | `regex/DriverLicenseDetector.kt` | ✅ `NewDetectorsTest` | ❌ | ❌ | ⚠️ только с контекстом «водительское»; `77-12-345678` не покрыт |
| 10 | ADDRESS | `dictionary/AddressDetector.kt` | ✅ `NewDetectorsTest` | ❌ | ✅ `neg-16` | ⚠️ только `г. Город, ул. Улица, д. N, кв. N`; индекс не покрыт |
| 11 | EMAIL | `regex/EmailDetector.kt` | ✅ `DetectorsTest` | ✅ `csv-07..09` | ✅ `neg-04..06` | ✅ регистр, `+`, `%`; ❌ кириллический домен `@почта.рф` не покрыт |
| 12 | PHONE | `regex/PhoneDetector.kt` | ✅ `DetectorsTest` | ✅ `csv-10..12` | ✅ `neg-07..08` | ✅ `+7 (900)`, `8900`, `8-900`; ❌ Unicode-пробелы не покрыты |
| 13 | INN | `regex/InnDetector.kt` (checksum) | ✅ `DetectorsTest` + `ChecksumValidatorTest` | ✅ `csv-13..14` | ✅ `neg-09..10` | ✅ 10/12 цифр, валидный/невалидный |
| 14 | CARD_NUMBER | `regex/CardDetector.kt` (Luhn) | ✅ `DetectorsTest` + `ChecksumValidatorTest` | ✅ `csv-15..17` | ✅ `neg-11..12` | ✅ пробелы/дефисы/без разделителя; ❌ 15-значные карты не покрыты |
| 15 | CVV | `regex/CvvDetector.kt` | ✅ `NewDetectorsTest` | ❌ | ❌ | ⚠️ только с контекстом `cvv/cvc/код` |
| 16 | PIN | `regex/PinDetector.kt` | ✅ `NewDetectorsTest` | ❌ | ❌ | ⚠️ только с контекстом `пин/pin` |
| 17 | CARD_HOLDER_NAME | `regex/CardHolderNameDetector.kt` | ✅ `NewDetectorsTest` | ❌ | ❌ | ⚠️ только `CARD HOLDER: IVANOV IVAN` (латиница, 2 слова) |

**Итог по функциональным требованиям:**
- **Детекторы:** все 17 категорий имеют детектор. ✅
- **Юнит-тесты:** 16/17 категорий покрыты юнит-тестами. ❌ Нет отдельного юнит-теста на различение `PASSPORT_ISSUE_DATE` vs `DATE_OF_BIRTH` (проверяется только `DATE_OF_BIRTH` в `DetectorsTest`).
- **Параметризованный CSV:** только 6/17 категорий (PASSPORT, DATE, EMAIL, PHONE, INN, CARD). 11 категорий не в CSV.
- **Негативные кейсы:** 6 категорий + контекстные правила. 11 категорий не имеют негативных кейсов.
- **Вариации формата:** хорошо покрыты для 6 базовых категорий; слабо для словарных/контекстных.

---

## 3. Нефункциональные требования

| Требование | Реализовано? | Тест есть? | Пробелы |
|------------|--------------|------------|---------|
| **Безопасность: ПД не в логах (LogMasker)** | ✅ `security/LogMasker.kt` | ❌ | Нет теста на отсутствие утечек. `ProcessService.unmask` (строка 113) логирует `logMasker.maskPayloadId(record.original)` — хеш **original-строки**, а не payload_id (семантическая ошибка, хотя и не утечка). |
| **Безопасность: нет ПД в метриках** | ✅ `metrics/MetricsService.kt` (только агрегаты + тег `type`) | ❌ | Нет теста. Тег `type` — это имя `PiiType`, не значение ПД — безопасно. |
| **Безопасность: ошибки не раскрывают детали** | ✅ `GlobalExceptionHandler` (500 → «Internal server error») | ❌ | Нет теста на SEC-04. |
| **Rate limiting (429)** | ✅ `config/RateLimitFilter.kt` + `RateLimitService.kt` (Bucket4j) | ❌ | **Нет ни одного теста** на 429/Retry-After. |
| **Circuit breaker (503)** | ✅ `RedisCorrelationStore` (`@CircuitBreaker`) + `GlobalExceptionHandler` | ❌ | **Нет теста** на 503 при недоступности Redis (IT-10..13 из матрицы не реализованы). |
| **Идемпотентность** | ✅ `ProcessService` + `InMemoryCorrelationStore.putIfAbsent` + `RedisCorrelationStore.setIfAbsent` | ✅ `RedisIntegrationTest` (idempotent), `ProcessControllerTest` | Нет теста на гонку (IT-09) и на 404 при несовпадении original (UM-10). |
| **Конфигурация по системам** | ✅ `SystemRegistry` (resolve/filterTypes, 403) | ⚠️ только юнит `SystemRegistryTest` | **Нет controller-теста на 403** (X-System-Id). `unmaskingEnabled` **не используется** в `ProcessService.unmask`. |
| **Виртуальные потоки** | ✅ `application.yml` `spring.threads.virtual.enabled=true` | ❌ | Нет проверки/нагрузочного теста. |
| **Ограничение размера payload** | ❌ **`maxPayloadSize` не используется** | ❌ | `PiiProperties.maxPayloadSize` и `application.yml` `max-payload-size` объявлены, но **нигде не проверяются**. DoS-защита (ED-10) отсутствует. |
| **Таймаут запроса 10 сек** | ⚠️ `server.tomcat.connection-timeout: 10s` | ❌ | Это таймаут соединения, не обработки запроса. Нет явного таймаута на обработку. |
| **Ретраи до 2** | ❌ | ❌ | Не реализовано (клиентская логика, вне сервиса). |
| **Стоп после 5 невалидных** | ❌ | ❌ | Не реализовано. |
| **Масштабируемость** | ⚠️ Redis + виртуальные потоки | ❌ | Нет нагрузочных тестов (PERF-01..05). |

**Критичные пробелы в НФТ:**
1. **`maxPayloadSize` не применяется** — отсутствует защита от DoS большими payload (требование раздела 14.6 и 16.6 архитектуры).
2. **`unmaskingEnabled` не используется** — конфигурация системы не влияет на демаскирование (требование раздела 12).
3. **`maskFormat` не используется** — только `STAR` захардкожен в `MaskingEngine.maskValue` (требование раздела 12.3).
4. **Нет тестов на 429, 503, 403 (controller), безопасность** — ключевые НФТ не верифицированы.

---

## 4. Параметризованные тесты (CSV)

### 4.1. `masking-cases.csv` (20 строк)

| Категория | Строк | Покрытие |
|-----------|-------|----------|
| PASSPORT_SERIES_NUMBER | 3 | ✅ |
| DATE_OF_BIRTH | 3 | ✅ |
| EMAIL | 3 | ✅ |
| PHONE | 3 | ✅ |
| INN | 2 | ✅ |
| CARD_NUMBER | 3 | ✅ |
| MULTI | 3 | ✅ |
| **Итого категорий** | **6 из 17** | ❌ |

**Не покрыты в masking-cases.csv:** FULL_NAME, PLACE_OF_BIRTH, CITIZENSHIP, PASSPORT_ISSUER, PASSPORT_DEPARTMENT_CODE, PASSPORT_ISSUE_DATE, DRIVER_LICENSE, ADDRESS, CVV, PIN, CARD_HOLDER_NAME (11 категорий).

### 4.2. `negative-cases.csv` (18 строк)

| Категория | Строк |
|-----------|-------|
| PASSPORT_SERIES_NUMBER_INVALID | 3 |
| EMAIL_INVALID | 3 |
| PHONE_INVALID | 2 |
| INN_INVALID | 2 |
| CARD_INVALID | 2 |
| DATE_INVALID | 1 |
| CONTEXT_NEGATIVE | 5 |

**Не покрыты негативными кейсами:** FULL_NAME (кроме контекста), PLACE_OF_BIRTH, CITIZENSHIP, PASSPORT_ISSUER, PASSPORT_DEPARTMENT_CODE, PASSPORT_ISSUE_DATE, DRIVER_LICENSE, ADDRESS (кроме контекста), CVV, PIN, CARD_HOLDER_NAME.

### 4.3. Оценка полноты

| Аспект | Статус | Комментарий |
|--------|--------|-------------|
| Все 17 категорий в CSV | ❌ | Только 6. |
| Пограничные случаи (пустой payload, спецсимволы, Unicode, эмодзи) | ❌ | **Нет ни одного** параметризованного кейса (ED-01..18 из матрицы не в CSV). |
| Контекстные правила (позитивный/негативный) | ⚠️ | Негативный контекст есть (5 кейсов); позитивный контекст в CSV не выделен отдельно. |
| Checksum-валидация | ✅ | ИНН (валид/невалид), Luhn (валид/невалид) покрыты в CSV и юнит-тестах. |
| Интеграционные тесты с Redis | ✅ | `RedisIntegrationTest` (Testcontainers): полный цикл, идемпотентность. |
| Демаскирование | ✅ | Roundtrip в `MaskingParameterizedTest` (шаг 2). |
| Идемпотентность | ⚠️ | Есть в `RedisIntegrationTest`; нет теста на 404 при несовпадении original. |

**Вывод:** параметризованное покрытие покрывает только 6 из 17 категорий. 11 новых детекторов покрыты исключительно юнит-тестами (`NewDetectorsTest`), которые проверяют только «находит/не находит», без проверки точных позиций маскирования и roundtrip демаскирования.

---

## 5. Критичные пробелы (приоритеты)

### P0 — блокирующие (безопасность/контракт/качество)

1. **`maxPayloadSize` не применяется** (`PiiProperties.kt:13`, `application.yml:61`). Нет проверки размера payload → отсутствует защита от DoS (требование 14.6/16.6). **Действие:** добавить валидацию размера в `ProcessService`/контроллер, вернуть 400 при превышении; добавить тест (ED-10).
2. **Нет тестов на rate limiting (429)**. `RateLimitFilter`/`RateLimitService` не покрыты. **Действие:** добавить тест на 429 + заголовок `Retry-After` (PERF-04).
3. **Нет тестов на circuit breaker (503)**. Деградация Redis не верифицирована. **Действие:** добавить тест с остановленным Redis (IT-10..13).
4. **Нет тестов на безопасность (утечки ПД в логи/метрики)**. `LogMasker` не покрыт. **Действие:** добавить тесты SEC-01..05.

### P1 — важные (полнота контракта и покрытия)

5. **`unmaskingEnabled` не используется** (`PiiProperties.kt:25`). Конфигурация системы не влияет на демаскирование. **Действие:** проверять флаг в `ProcessService.unmask`; добавить тест.
6. **`maskFormat` не используется** (`PiiProperties.kt:26`). Только `STAR` захардкожен. **Действие:** либо реализовать, либо убрать из конфигурации.
7. **CSV-покрытие только 6/17 категорий.** 11 детекторов не в параметризованных тестах. **Действие:** добавить строки в `masking-cases.csv`/`negative-cases.csv` для FULL_NAME, PLACE_OF_BIRTH, CITIZENSHIP, PASSPORT_ISSUER, PASSPORT_DEPARTMENT_CODE, PASSPORT_ISSUE_DATE, DRIVER_LICENSE, ADDRESS, CVV, PIN, CARD_HOLDER_NAME (примеры уже есть в `test-matrix.md` раздел 6.3, строки csv-13..23).
8. **Нет controller-теста на 403** (неизвестная/отключённая система через `X-System-Id`). **Действие:** добавить тест.
9. **`docs/test-matrix.md` устарела** — утверждает, что реализованы только 6 детекторов (раздел 0.4, 8.1). **Действие:** обновить матрицу под текущее состояние (16 детекторов, 17 категорий).

### P2 — улучшения

10. **`ProcessService.unmask` (строка 113)** логирует хеш `record.original` вместо `payload_id` — семантическая ошибка. **Действие:** логировать хеш `payloadId`.
11. **`DetectionEngine.resolveOverlaps`** — при замене сущности на более приоритетную не перепроверяется перекрытие с предыдущей сущностью (крайний случай). **Действие:** перепроверять после замены.
12. **`PassportDetector`** не маскирует `4509123456` (без разделителя) — расхождение с матрицей PS-03. **Действие:** решить, нужен ли формат без разделителя.
13. **Нет юнит-теста на различение `PASSPORT_ISSUE_DATE` vs `DATE_OF_BIRTH`** в `DateDetector`.
14. **Нет тестов на пограничные случаи** (пустой payload, Unicode, эмодзи, спецсимволы) — ED-01..18.
15. **Нет нагрузочных тестов** (PERF-01..05) — RPS 1000+, latency p95 < 1 сек.

---

## 6. Рекомендации (конкретные действия)

1. **Реализовать проверку `maxPayloadSize`** в `ProcessService.process` (или фильтре) до детекции: если `payload.length > maxPayloadSize` → 400. Добавить тест.
2. **Добавить тесты на 429**: вызвать `/process` больше `rateLimitRps` раз (в тесте задать низкий лимит через `@TestPropertySource`), проверить статус 429 и заголовок `Retry-After`.
3. **Добавить тест на 503**: использовать `@MockBean`/`@MockitoBean` для `RedisCorrelationStore` (или остановить контейнер), проверить 503 при открытом circuit breaker.
4. **Добавить тесты безопасности**: проверить, что в логах (через `OutputCaptureExtension`) нет значений ПД; что метрики не содержат значений.
5. **Использовать `unmaskingEnabled`** в `ProcessService.unmask` — если `!system.unmaskingEnabled`, вернуть 403/ошибку.
6. **Расширить CSV**: добавить строки для 11 недостающих категорий (взять из `test-matrix.md` раздел 6.3). Это автоматически покроет roundtrip маскирования/демаскирования для всех 17 категорий.
7. **Обновить `docs/test-matrix.md`**: разделы 0.4 и 8.1 не соответствуют коду (16 детекторов, а не 6).
8. **Добавить controller-тест на 403** через заголовок `X-System-Id: unknown`.
9. **Исправить логирование в `ProcessService.unmask`** — логировать хеш `payloadId`, а не `original`.
10. **Добавить юнит-тест на `DateDetector`** для различения `PASSPORT_ISSUE_DATE`/`DATE_OF_BIRTH` по контексту.

---

## Приложение: карта файлов

| Файл | Роль |
|------|------|
| `src/main/kotlin/com/alfahack/pii/detection/PiiType.kt` | Enum 17 категорий |
| `src/main/kotlin/com/alfahack/pii/detection/DetectionEngine.kt` | Оркестратор, разрешение пересечений, порог 0.7 |
| `src/main/kotlin/com/alfahack/pii/detection/ChecksumValidator.kt` | ИНН + Luhn |
| `src/main/kotlin/com/alfahack/pii/detection/ContextRules.kt` | Позитивный/негативный контекст |
| `src/main/kotlin/com/alfahack/pii/detection/regex/*.kt` | 11 regex-детекторов |
| `src/main/kotlin/com/alfahack/pii/detection/dictionary/*.kt` | 5 словарных детекторов |
| `src/main/kotlin/com/alfahack/pii/masking/MaskingEngine.kt` | Токенизация с сохранением длины |
| `src/main/kotlin/com/alfahack/pii/unmasking/UnmaskingEngine.kt` | Восстановление по spans |
| `src/main/kotlin/com/alfahack/pii/service/ProcessService.kt` | Оркестратор маскирование/демаскирование |
| `src/main/kotlin/com/alfahack/pii/store/*.kt` | In-memory + Redis (circuit breaker) |
| `src/main/kotlin/com/alfahack/pii/config/RateLimit*.kt` | Bucket4j → 429 |
| `src/main/kotlin/com/alfahack/pii/security/LogMasker.kt` | Маскирование ПД в логах |
| `src/main/kotlin/com/alfahack/pii/exception/GlobalExceptionHandler.kt` | 400/403/404/429/500/503 |
| `src/test/resources/masking-cases.csv` | 20 строк, 6 категорий |
| `src/test/resources/negative-cases.csv` | 18 строк, 6 категорий + контекст |
| `src/test/kotlin/com/alfahack/pii/controller/MaskingParameterizedTest.kt` | CSV roundtrip |
| `src/test/kotlin/com/alfahack/pii/controller/RedisIntegrationTest.kt` | Testcontainers Redis |