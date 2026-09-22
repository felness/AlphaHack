"""Метрики производительности: latency, RPS, TPS.

Лёгкие in-memory счётчики, экспортируемые через /metrics в Prometheus-формате.
"""
from __future__ import annotations

import threading
import time
from collections import deque


class Metrics:
    """Сбор метрик latency/RPS/TPS."""

    def __init__(self, window_sec: float = 60.0):
        self._window = window_sec
        self._lock = threading.Lock()
        self._requests: deque[float] = deque()      # timestamps запросов
        self._tokens: deque[tuple[float, int]] = deque()  # (timestamp, tokens)
        self._latencies: deque[float] = deque()     # latency каждого запроса
        self._total_requests = 0
        self._total_errors = 0
        self._total_tokens = 0

    def record_request(self, latency_sec: float, tokens: int = 0, error: bool = False) -> None:
        now = time.monotonic()
        with self._lock:
            self._requests.append(now)
            self._latencies.append(latency_sec)
            self._total_requests += 1
            if error:
                self._total_errors += 1
            if tokens > 0:
                self._tokens.append((now, tokens))
                self._total_tokens += tokens
            self._prune(now)

    def _prune(self, now: float) -> None:
        cutoff = now - self._window
        while self._requests and self._requests[0] < cutoff:
            self._requests.popleft()
        while self._latencies and len(self._latencies) > len(self._requests):
            self._latencies.popleft()
        while self._tokens and self._tokens[0][0] < cutoff:
            self._tokens.popleft()

    def rps(self) -> float:
        now = time.monotonic()
        with self._lock:
            self._prune(now)
            return len(self._requests) / self._window

    def avg_latency_ms(self) -> float:
        with self._lock:
            if not self._latencies:
                return 0.0
            return (sum(self._latencies) / len(self._latencies)) * 1000

    def p95_latency_ms(self) -> float:
        with self._lock:
            if not self._latencies:
                return 0.0
            sorted_l = sorted(self._latencies)
            idx = int(len(sorted_l) * 0.95)
            return sorted_l[min(idx, len(sorted_l) - 1)] * 1000

    def tps(self) -> float:
        now = time.monotonic()
        with self._lock:
            self._prune(now)
            return sum(t for _, t in self._tokens) / self._window

    def total_requests(self) -> int:
        with self._lock:
            return self._total_requests

    def total_errors(self) -> int:
        with self._lock:
            return self._total_errors

    def total_tokens(self) -> int:
        with self._lock:
            return self._total_tokens

    def render_prometheus(self) -> str:
        """Возвращает метрики в Prometheus text-формате."""
        lines = [
            "# HELP pii_total_requests Total number of requests",
            "# TYPE pii_total_requests counter",
            f"pii_total_requests {self.total_requests()}",
            "# HELP pii_total_errors Total number of errors",
            "# TYPE pii_total_errors counter",
            f"pii_total_errors {self.total_errors()}",
            "# HELP pii_total_tokens Total tokens processed",
            "# TYPE pii_total_tokens counter",
            f"pii_total_tokens {self.total_tokens()}",
            "# HELP pii_rps Requests per second (window)",
            "# TYPE pii_rps gauge",
            f"pii_rps {self.rps():.2f}",
            "# HELP pii_tps Tokens per second (window)",
            "# TYPE pii_tps gauge",
            f"pii_tps {self.tps():.2f}",
            "# HELP pii_avg_latency_ms Average latency in ms",
            "# TYPE pii_avg_latency_ms gauge",
            f"pii_avg_latency_ms {self.avg_latency_ms():.2f}",
            "# HELP pii_p95_latency_ms P95 latency in ms",
            "# TYPE pii_p95_latency_ms gauge",
            f"pii_p95_latency_ms {self.p95_latency_ms():.2f}",
        ]
        return "\n".join(lines) + "\n"


# Глобальный экземпляр метрик
metrics = Metrics()