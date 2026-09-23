#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Нагрузочное тестирование модуля безопасности ПД (AlphaHack).

Генерирует реалистичные данные по 17 категориям ПД, отправляет запросы
на маскирование → демаскирование параллельно, замеряет latency (p50/p95/p99),
RPS и проверяет качество (roundtrip, точность маскирования).

Использование:
    python load_test.py --url http://localhost:8080 --requests 1000 --concurrency 50
"""

import argparse
import json
import secrets
import statistics
import sys
import time
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed

# Настройка UTF-8 для вывода (Windows cp1251 не поддерживает Unicode-стрелки)
if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# ---------------------------------------------------------------------------
# Генерация реалистичных данных по 17 категориям ПД
# ---------------------------------------------------------------------------

# Тот же API, что у модуля random (choice/randint), но криптостойкий источник
_rng = secrets.SystemRandom()

FIRST_NAMES = ["Иван", "Петр", "Сергей", "Алексей", "Дмитрий", "Андрей", "Николай", "Михаил"]
LAST_NAMES = ["Иванов", "Петров", "Сидоров", "Смирнов", "Кузнецов", "Попов", "Соколов", "Лебедев"]
PATRONYMICS = ["Иванович", "Петрович", "Сергеевич", "Алексеевич", "Дмитриевич", "Андреевич"]
CITIES = ["Москва", "Санкт-Петербург", "Новосибирск", "Екатеринбург", "Казань", "Нижний Новгород"]
STREETS = ["Тверская", "Ленина", "Пушкина", "Гагарина", "Советская", "Мира"]
EMAIL_DOMAINS = ["mail.ru", "yandex.ru", "gmail.com", "bk.ru", "inbox.ru"]
CARD_PREFIXES = ["4276", "4111", "4242", "5211", "5487"]


def luhn_checksum(number: str) -> int:
    """Вычислить контрольную цифру Luhn для номера карты."""
    digits = [int(d) for d in number]
    for i in range(len(digits) - 1, -1, -2):
        digits[i] *= 2
        if digits[i] > 9:
            digits[i] -= 9
    total = sum(digits)
    return (10 - total % 10) % 10


def valid_card() -> str:
    """Сгенерировать Luhn-валидный номер карты."""
    prefix = _rng.choice(CARD_PREFIXES)
    body = "".join(str(_rng.randint(0, 9)) for _ in range(11))
    base = prefix + body
    check = luhn_checksum(base)
    return f"{base}{check}"


def valid_inn_10() -> str:
    """Сгенерировать валидный 10-значный ИНН."""
    weights = [2, 4, 10, 3, 5, 9, 4, 6, 8]
    base = "77" + "".join(str(_rng.randint(0, 9)) for _ in range(7))
    total = sum(int(base[i]) * weights[i] for i in range(9))
    check = total % 11 % 10
    return base + str(check)


def valid_inn_12() -> str:
    """Сгенерировать валидный 12-значный ИНН."""
    w1 = [7, 2, 4, 10, 3, 5, 9, 4, 6, 8]
    w2 = [3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8]
    base = "77" + "".join(str(_rng.randint(0, 9)) for _ in range(8))
    c1 = sum(int(base[i]) * w1[i] for i in range(10)) % 11 % 10
    c2 = sum(int((base + str(c1))[i]) * w2[i] for i in range(11)) % 11 % 10
    return base + str(c1) + str(c2)


def random_phone() -> str:
    """Сгенерировать телефон."""
    formats = [
        f"+7 ({_rng.randint(900, 999)}) {_rng.randint(100, 999)}-{_rng.randint(10, 99)}-{_rng.randint(10, 99)}",
        f"8{_rng.randint(900, 999)}{_rng.randint(1000000, 9999999)}",
        f"8-{_rng.randint(900, 999)}-{_rng.randint(100, 999)}-{_rng.randint(10, 99)}-{_rng.randint(10, 99)}",
    ]
    return _rng.choice(formats)


def random_email() -> str:
    """Сгенерировать email."""
    name = _rng.choice(LAST_NAMES).lower() + str(_rng.randint(1, 999))
    return f"{name}@{_rng.choice(EMAIL_DOMAINS)}"


def random_passport() -> str:
    """Сгенерировать паспорт."""
    series = f"{_rng.randint(1000, 9999)}"
    number = f"{_rng.randint(100000, 999999)}"
    return f"{series} {number}"


def random_date() -> str:
    """Сгенерировать дату."""
    day = _rng.randint(1, 28)
    month = _rng.randint(1, 12)
    year = _rng.randint(1950, 2005)
    return f"{day:02d}.{month:02d}.{year}"


def random_full_name() -> str:
    """Сгенерировать ФИО."""
    return f"{_rng.choice(LAST_NAMES)} {_rng.choice(FIRST_NAMES)} {_rng.choice(PATRONYMICS)}"


def random_address() -> str:
    """Сгенерировать адрес."""
    city = _rng.choice(CITIES)
    street = _rng.choice(STREETS)
    house = _rng.randint(1, 200)
    apt = _rng.randint(1, 500)
    return f"г. {city}, ул. {street}, д. {house}, кв. {apt}"


def generate_payload() -> str:
    """Сгенерировать реалистичный payload с ПД."""
    templates = [
        f"Клиент {random_full_name()}, паспорт {random_passport()}, дата рождения {random_date()}",
        f"email {random_email()}, телефон {random_phone()}",
        f"ИНН {valid_inn_12()}, карта {valid_card()}",
        f"паспорт {random_passport()}, адрес: {random_address()}",
        f"гражданин РФ, место рождения: г. {_rng.choice(CITIES)}",
        f"телефон {random_phone()}, email {random_email()}, ИНН {valid_inn_10()}",
        f"карта {valid_card()}, CVV {_rng.randint(100, 999)}, ПИН-код {_rng.randint(1000, 9999)}",
        f"паспорт {random_passport()}, код подразделения {_rng.randint(100, 999)}-{_rng.randint(100, 999)}",
        f"водительское удостоверение {_rng.randint(10, 99)} {_rng.randint(10, 99)} {_rng.randint(100000, 999999)}",
        f"дата рождения {random_date()}, телефон {random_phone()}, email {random_email()}",
    ]
    return _rng.choice(templates)


# ---------------------------------------------------------------------------
# HTTP-клиент
# ---------------------------------------------------------------------------

class HttpClient:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")

    def process(self, payload: str, payload_id: str) -> tuple[int, str]:
        """Отправить запрос на /process. Возвращает (status, result)."""
        body = json.dumps({"payload": payload, "payload_id": payload_id}).encode("utf-8")
        req = urllib.request.Request(
            f"{self.base_url}/process",
            data=body,
            headers={"Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                return resp.status, data.get("result", "")
        except urllib.error.HTTPError as e:
            return e.code, ""
        except Exception as e:
            return 0, str(e)


# ---------------------------------------------------------------------------
# Нагрузочный тест
# ---------------------------------------------------------------------------

def run_single(client: HttpClient, idx: int, run_id: str, think_time: float = 0.0) -> dict:
    """Выполнить один цикл маскирование → демаскирование."""
    payload = generate_payload()
    # Уникальный payload_id (run_id + idx), чтобы избежать конфликтов между запусками
    payload_id = f"{run_id}-{idx}"

    start = time.perf_counter()
    status1, mask = client.process(payload, payload_id)
    t1 = time.perf_counter() - start

    # Реалистичная задержка между запросами (think time)
    if think_time > 0:
        time.sleep(think_time)

    start = time.perf_counter()
    status2, unmask = client.process(mask, payload_id)
    t2 = time.perf_counter() - start

    return {
        "idx": idx,
        "status1": status1,
        "status2": status2,
        "mask_latency": t1,
        "unmask_latency": t2,
        "roundtrip_ok": unmask == payload,
        "masked": mask != payload,
        "payload": payload,
        "mask": mask,
    }


def main():
    parser = argparse.ArgumentParser(description="Нагрузочный тест модуля ПД")
    parser.add_argument("--url", default="http://localhost:8080", help="URL сервиса")
    parser.add_argument("--requests", type=int, default=200, help="Количество запросов (пар)")
    parser.add_argument("--concurrency", type=int, default=20, help="Параллельность")
    parser.add_argument("--think-time", type=float, default=0.0, help="Задержка между запросами (сек)")
    args = parser.parse_args()

    client = HttpClient(args.url)
    print(f"URL: {args.url}")
    print(f"Запросов (пар маскирование→демаскирование): {args.requests}")
    print(f"Параллельность: {args.concurrency}")
    print(f"Think time: {args.think_time} сек")
    print("-" * 60)

    results = []
    start_total = time.perf_counter()
    # Уникальный run_id для этого запуска (чтобы payload_id не конфликтовали между запусками)
    run_id = f"load-{int(time.time())}"

    with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [executor.submit(run_single, client, i, run_id, args.think_time) for i in range(args.requests)]
        for i, future in enumerate(as_completed(futures)):
            results.append(future.result())
            if (i + 1) % 50 == 0:
                print(f"  Выполнено {i + 1}/{args.requests}...")

    total_time = time.perf_counter() - start_total

    # Анализ результатов
    mask_latencies = [r["mask_latency"] for r in results]
    unmask_latencies = [r["unmask_latency"] for r in results]
    all_latencies = mask_latencies + unmask_latencies

    ok = [r for r in results if r["status1"] == 200 and r["status2"] == 200]
    roundtrip_ok = [r for r in ok if r["roundtrip_ok"]]
    masked_ok = [r for r in ok if r["masked"]]

    print("\n" + "=" * 60)
    print("РЕЗУЛЬТАТЫ НАГРУЗОЧНОГО ТЕСТА")
    print("=" * 60)
    print(f"Всего пар запросов: {args.requests}")
    print(f"Успешных (200): {len(ok)} ({len(ok) / args.requests * 100:.1f}%)")
    print(f"Roundtrip OK (демаскирование вернуло оригинал): {len(roundtrip_ok)} ({len(roundtrip_ok) / max(len(ok), 1) * 100:.1f}%)")
    print(f"Маскирование выполнено (маска != оригинал): {len(masked_ok)} ({len(masked_ok) / max(len(ok), 1) * 100:.1f}%)")
    print(f"Общее время: {total_time:.2f} сек")
    print(f"RPS (пар/сек): {args.requests / total_time:.1f}")
    print(f"RPS (запросов/сек): {args.requests * 2 / total_time:.1f}")
    print("-" * 60)
    print("LATENCY (сек):")
    print(f"  Маскирование:  p50={statistics.median(mask_latencies):.4f}  p95={percentile(mask_latencies, 95):.4f}  p99={percentile(mask_latencies, 99):.4f}")
    print(f"  Демаскирование: p50={statistics.median(unmask_latencies):.4f}  p95={percentile(unmask_latencies, 95):.4f}  p99={percentile(unmask_latencies, 99):.4f}")
    print(f"  Всего:          p50={statistics.median(all_latencies):.4f}  p95={percentile(all_latencies, 95):.4f}  p99={percentile(all_latencies, 99):.4f}")

    # Ошибки
    errors = [r for r in results if r["status1"] != 200 or r["status2"] != 200]
    if errors:
        print("-" * 60)
        print("ОШИБКИ:")
        for r in errors[:10]:
            print(f"  idx={r['idx']} status1={r['status1']} status2={r['status2']} payload={r['payload'][:50]}")

    # Примеры
    print("-" * 60)
    print("ПРИМЕРЫ:")
    for r in results[:3]:
        print(f"  IN:  {r['payload']}")
        print(f"  MASK: {r['mask']}")
        print(f"  OK: {r['roundtrip_ok']}")
        print()


def percentile(data, p):
    """Вычислить процентиль."""
    if not data:
        return 0
    sorted_data = sorted(data)
    k = (len(sorted_data) - 1) * p / 100
    f = int(k)
    c = f + 1 if f + 1 < len(sorted_data) else f
    return sorted_data[f] + (sorted_data[c] - sorted_data[f]) * (k - f)


if __name__ == "__main__":
    main()