"""Rate-limiting для защиты от перегрузки.

Возвращает 429 (Too Many Requests) с заголовком Retry-After, когда число
запросов превышает лимит. Использует скользящее окно (in-memory).
"""
from __future__ import annotations

import threading
import time
from collections import deque


class RateLimiter:
    """Скользящее окно rate-limiting по IP/системе."""

    def __init__(self, max_requests: int = 1000, window_sec: float = 1.0):
        self._max_requests = max_requests
        self._window = window_sec
        self._lock = threading.Lock()
        self._buckets: dict[str, deque[float]] = {}

    def allow(self, key: str) -> tuple[bool, float]:
        """Проверяет, разрешён ли запрос. Возвращает (разрешено, retry_after_sec)."""
        now = time.monotonic()
        cutoff = now - self._window
        with self._lock:
            bucket = self._buckets.get(key)
            if bucket is None:
                bucket = deque()
                self._buckets[key] = bucket
            # Убираем устаревшие
            while bucket and bucket[0] < cutoff:
                bucket.popleft()
            if len(bucket) >= self._max_requests:
                # Вычисляем, когда освободится место
                retry_after = max(0.0, bucket[0] + self._window - now)
                return False, retry_after
            bucket.append(now)
            return True, 0.0

    def cleanup(self) -> None:
        """Удаляет пустые бакеты."""
        now = time.monotonic()
        cutoff = now - self._window
        with self._lock:
            for key in list(self._buckets.keys()):
                bucket = self._buckets[key]
                while bucket and bucket[0] < cutoff:
                    bucket.popleft()
                if not bucket:
                    del self._buckets[key]


# Глобальный экземпляр. Лимит по умолчанию — 1000 RPS на систему.
rate_limiter = RateLimiter(max_requests=1000, window_sec=1.0)