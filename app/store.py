"""In-memory хранилище соответствий payload_id → замаскированные данные.

Потокобезопасно (threading.Lock). Поддерживает TTL для автоматической
очистки устаревших записей. Для одного инстанса достаточно; для
масштабирования на несколько инстансов можно заменить на Redis.
"""
from __future__ import annotations

import threading
import time
from dataclasses import dataclass, field

from .masking import MaskedSpan


@dataclass
class Record:
    """Запись соответствия для одного payload_id."""
    original_text: str
    masked_text: str
    spans: list[MaskedSpan] = field(default_factory=list)
    created_at: float = field(default_factory=time.time)


class MaskStore:
    """Потокобезопасное хранилище соответствий."""

    def __init__(self, ttl_sec: float = 3600.0, max_entries: int = 1_000_000):
        self._ttl = ttl_sec
        self._max_entries = max_entries
        self._data: dict[str, Record] = {}
        self._lock = threading.Lock()

    def put(self, payload_id: str, record: Record) -> None:
        """Сохраняет запись. При переполнении удаляет самые старые."""
        with self._lock:
            self._data[payload_id] = record
            if len(self._data) > self._max_entries:
                self._evict_oldest()

    def get(self, payload_id: str) -> Record | None:
        """Возвращает запись или None, если её нет/истекла."""
        with self._lock:
            record = self._data.get(payload_id)
            if record is None:
                return None
            if time.time() - record.created_at > self._ttl:
                del self._data[payload_id]
                return None
            return record

    def _evict_oldest(self) -> None:
        """Удаляет самую старую запись."""
        if not self._data:
            return
        oldest_id = min(self._data, key=lambda k: self._data[k].created_at)
        del self._data[oldest_id]

    def __len__(self) -> int:
        with self._lock:
            return len(self._data)


# Глобальный экземпляр хранилища
store = MaskStore()