"""Метрики производительности: latency, RPS, TPS.

Хранятся в Redis (общее хранилище для всех воркеров), чтобы метрики
отражали реальную нагрузку на весь сервис, а не на один воркер.

Для производительности: каждый воркер накапливает метрики в L1-кэше
и сбрасывает их в Redis раз в секунду (фоновый поток), чтобы не писать
в Redis на каждый запрос.
"""
from __future__ import annotations

import logging
import os
import threading
import time

import redis

logger = logging.getLogger("pii.metrics")

# Ключи Redis
_TOTAL_REQUESTS = "metrics:total_requests"
_TOTAL_ERRORS = "metrics:total_errors"
_TOTAL_TOKENS = "metrics:total_tokens"
_RPS_WINDOW = "metrics:rps_window"      # ZSET: score=timestamp
_TPS_WINDOW = "metrics:tps_window"      # ZSET: score=tokens
_LATENCY_SUM = "metrics:latency_sum"    # сумма latency (мс)
_LATENCY_COUNT = "metrics:latency_count"
_LATENCY_P95 = "metrics:latency_p95"    # ZSET: score=latency

_FLUSH_INTERVAL = 1.0  # сброс в Redis раз в секунду


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
        # L1-агрегация (в памяти воркера)
        self._l1_lock = threading.Lock()
        self._l1_requests = 0
        self._l1_errors = 0
        self._l1_tokens = 0
        self._l1_latency_sum = 0.0
        self._l1_latency_count = 0
        self._l1_rps: list[float] = []      # timestamps
        self._l1_tps: list[tuple[float, int]] = []  # (timestamp, tokens)
        self._l1_p95: list[float] = []      # latency values
        self._worker_id = os.getpid()
        self._stop = False
        self._flush_thread = threading.Thread(target=self._flush_loop, daemon=True)
        self._flush_thread.start()

    def record_request(self, latency_sec: float, tokens: int = 0, error: bool = False) -> None:
        """Накапливает метрику в L1-кэше (быстро, без сети)."""
        now = time.time()
        with self._l1_lock:
            self._l1_requests += 1
            if error:
                self._l1_errors += 1
            if tokens > 0:
                self._l1_tokens += tokens
                self._l1_tps.append((now, tokens))
            self._l1_rps.append(now)
            latency_ms = latency_sec * 1000
            self._l1_latency_sum += latency_ms
            self._l1_latency_count += 1
            self._l1_p95.append(latency_ms)

    def _flush_loop(self) -> None:
        """Фоновый поток: сбрасывает L1-метрики в Redis раз в секунду."""
        while not self._stop:
            time.sleep(_FLUSH_INTERVAL)
            self._flush()

    def _flush(self) -> None:
        """Сбрасывает накопленные метрики в Redis."""
        with self._l1_lock:
            requests = self._l1_requests
            errors = self._l1_errors
            tokens = self._l1_tokens
            latency_sum = self._l1_latency_sum
            latency_count = self._l1_latency_count
            rps = self._l1_rps
            tps = self._l1_tps
            p95 = self._l1_p95
            self._l1_requests = 0
            self._l1_errors = 0
            self._l1_tokens = 0
            self._l1_latency_sum = 0.0
            self._l1_latency_count = 0
            self._l1_rps = []
            self._l1_tps = []
            self._l1_p95 = []

        if requests == 0:
            return

        now = time.time()
        try:
            pipe = self._redis.pipeline()
            pipe.incrby(_TOTAL_REQUESTS, requests)
            if errors:
                pipe.incrby(_TOTAL_ERRORS, errors)
            if tokens:
                pipe.incrby(_TOTAL_TOKENS, tokens)
            if rps:
                pipe.zadd(_RPS_WINDOW, {f"{self._worker_id}:{now}:{len(rps)}": now})
            if tps:
                pipe.zadd(_TPS_WINDOW, {f"{self._worker_id}:{now}:{sum(t for _, t in tps)}": now})
            if latency_sum:
                pipe.incrbyfloat(_LATENCY_SUM, latency_sum)
                pipe.incrby(_LATENCY_COUNT, latency_count)
            if p95:
                pipe.zadd(_LATENCY_P95, {f"{self._worker_id}:{now}": sum(p95) / len(p95)})
                pipe.zremrangebyrank(_LATENCY_P95, 0, -10001)
            pipe.execute()
            self._prune(now)
        except Exception as exc:  # noqa: BLE001
            logger.warning("Не удалось сбросить метрики в Redis: %s", exc)

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
            now = time.time()
            cutoff = now - self._window
            self._redis.zremrangebyscore(_RPS_WINDOW, 0, cutoff)
            entries = self._redis.zrange(_RPS_WINDOW, 0, -1, withscores=True)
            if not entries:
                return 0.0
            total = 0
            for member, ts in entries:
                # member = "worker:now:count"
                try:
                    total += int(member.rsplit(":", 1)[1])
                except (ValueError, IndexError):
                    total += 1
            oldest = entries[0][1]
            elapsed = max(1.0, now - oldest)
            return total / elapsed
        except Exception as exc:  # noqa: BLE001
            logger.warning("Не удалось получить RPS: %s", exc)
            return 0.0

    def tps(self) -> float:
        """Токенов в секунду за окно."""
        try:
            now = time.time()
            cutoff = now - self._window
            self._redis.zremrangebyscore(_TPS_WINDOW, 0, cutoff)
            entries = self._redis.zrange(_TPS_WINDOW, 0, -1, withscores=True)
            if not entries:
                return 0.0
            total = 0
            for member, _ in entries:
                # member = "worker:now:count"
                try:
                    total += int(member.rsplit(":", 1)[1])
                except (ValueError, IndexError):
                    total += 1
            oldest = entries[0][1]
            elapsed = max(1.0, now - oldest)
            return total / elapsed
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
            values = [float(score) for _, score in self._redis.zrange(_LATENCY_P95, 0, -1, withscores=True)]
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