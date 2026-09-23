"""Метрики производительности: latency, RPS, TPS.

Хранятся в Redis (общее хранилище для всех воркеров), чтобы метрики
отражали реальную нагрузку на весь сервис, а не на один воркер.
"""
from __future__ import annotations

import logging
import os
import time

import redis

logger = logging.getLogger("pii.metrics")

# Ключи Redis
_TOTAL_REQUESTS = "metrics:total_requests"
_TOTAL_ERRORS = "metrics:total_errors"
_TOTAL_TOKENS = "metrics:total_tokens"
_RPS_WINDOW = "metrics:rps_window"      # ZSET: timestamp -> worker_id
_TPS_WINDOW = "metrics:tps_window"      # ZSET: timestamp -> tokens
_LATENCY_SUM = "metrics:latency_sum"    # сумма latency (мс)
_LATENCY_COUNT = "metrics:latency_count"
_LATENCY_P95 = "metrics:latency_p95"    # ZSET: latency -> worker_id


class Metrics:
    """Сбор метрик latency/RPS/TPS в Redis (общий для всех воркеров)."""

    def __init__(self, window_sec: float = 60.0):
        self._window = window_sec
        self._redis = redis.Redis(
            host=os.getenv("REDIS_HOST", "redis"),
            port=int(os.getenv("REDIS_PORT", "6379")),
            decode_responses=True,
            socket_connect_timeout=1.0,
            socket_timeout=2.0,
            health_check_interval=30,
            retry_on_timeout=True,
        )

    def record_request(self, latency_sec: float, tokens: int = 0, error: bool = False) -> None:
        """Записывает метрику запроса в Redis."""
        now = time.time()
        try:
            pipe = self._redis.pipeline()
            pipe.incr(_TOTAL_REQUESTS)
            if error:
                pipe.incr(_TOTAL_ERRORS)
            if tokens > 0:
                pipe.incrby(_TOTAL_TOKENS, tokens)
                pipe.zadd(_TPS_WINDOW, {str(now): tokens})
            pipe.zadd(_RPS_WINDOW, {str(now): 1})
            latency_ms = latency_sec * 1000
            pipe.incrbyfloat(_LATENCY_SUM, latency_ms)
            pipe.incr(_LATENCY_COUNT)
            pipe.zadd(_LATENCY_P95, {str(now): latency_ms})
            # Ограничиваем размер ZSET для p95 (храним последние 10000)
            pipe.zremrangebyrank(_LATENCY_P95, 0, -10001)
            pipe.execute()
            self._prune(now)
        except Exception as exc:  # noqa: BLE001
            logger.warning("Не удалось записать метрику в Redis: %s", exc)

    def _prune(self, now: float) -> None:
        """Удаляет устаревшие записи из окон."""
        cutoff = now - self._window
        try:
            pipe = self._redis.pipeline()
            pipe.zremrangebyscore(_RPS_WINDOW, 0, cutoff)
            pipe.zremrangebyscore(_TPS_WINDOW, 0, cutoff)
            pipe.execute()
        except Exception as exc:  # noqa: BLE001
            logger.warning("Не удалось очистить окна метрик: %s", exc)

    def rps(self) -> float:
        """Запросов в секунду за окно."""
        try:
            count = self._redis.zcard(_RPS_WINDOW)
            return count / self._window
        except Exception as exc:  # noqa: BLE001
            logger.warning("Не удалось получить RPS: %s", exc)
            return 0.0

    def tps(self) -> float:
        """Токенов в секунду за окно."""
        try:
            total = sum(float(v) for v in self._redis.zrange(_TPS_WINDOW, 0, -1, withscores=True))
            return total / self._window
        except Exception as exc:  # noqa: BLE001
            logger.warning("Не удалось получить TPS: %s", exc)
            return 0.0

    def avg_latency_ms(self) -> float:
        """Средняя latency в мс."""
        try:
            total = float(self._redis.get(_LATENCY_SUM) or 0)
            count = int(self._redis.get(_LATENCY_COUNT) or 0)
            return total / count if count else 0.0
        except Exception as exc:  # noqa: BLE001
            logger.warning("Не удалось получить avg latency: %s", exc)
            return 0.0

    def p95_latency_ms(self) -> float:
        """P95 latency в мс."""
        try:
            values = [float(v) for v in self._redis.zrange(_LATENCY_P95, 0, -1, withscores=True)]
            if not values:
                return 0.0
            values.sort()
            idx = int(len(values) * 0.95)
            return values[min(idx, len(values) - 1)]
        except Exception as exc:  # noqa: BLE001
            logger.warning("Не удалось получить p95 latency: %s", exc)
            return 0.0

    def total_requests(self) -> int:
        try:
            return int(self._redis.get(_TOTAL_REQUESTS) or 0)
        except Exception as exc:  # noqa: BLE001
            logger.warning("Не удалось получить total_requests: %s", exc)
            return 0

    def total_errors(self) -> int:
        try:
            return int(self._redis.get(_TOTAL_ERRORS) or 0)
        except Exception as exc:  # noqa: BLE001
            logger.warning("Не удалось получить total_errors: %s", exc)
            return 0

    def total_tokens(self) -> int:
        try:
            return int(self._redis.get(_TOTAL_TOKENS) or 0)
        except Exception as exc:  # noqa: BLE001
            logger.warning("Не удалось получить total_tokens: %s", exc)
            return 0

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