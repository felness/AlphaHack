"""Безопасность: проверка систем-потребителей и защита от утечек ПД в логи."""
from __future__ import annotations

import re

from .config import get_settings

# Паттерны для маскирования ПД в логах
_LOG_MASK_PATTERNS = [
    re.compile(r"(?<!\d)(?:\+7|8|7)[\s\-]*(?:\(\d{3}\)|\d{3})[\s\-]*\d{3}[\s\-]*\d{2}[\s\-]*\d{2}(?!\d)"),
    re.compile(r"(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}(?![A-Za-z0-9._%+-])"),
    re.compile(r"(?<!\d)(?:\d{4}[\s\-]?){3}\d{4}(?!\d)"),
    re.compile(r"(?<!\d)\d{10}(?!\d)|(?<!\d)\d{12}(?!\d)"),
    re.compile(r"(?<!\d)(?:\d{2}[\s\-]?\d{2})[\s\-]+(?:\d{6})(?!\d)"),
]


def is_system_allowed(system_id: str | None) -> bool:
    """Проверяет, разрешена ли система-потребитель.

    Если заголовок X-System-Id отсутствует и allow_anonymous=True — разрешено
    (нужно для нагрузочного теста, контракт /process не предусматривает заголовок).
    """
    settings = get_settings()
    if not system_id:
        return settings.allow_anonymous
    return system_id in settings.allowed_systems_list


def sanitize_for_log(text: str) -> str:
    """Маскирует ПД в тексте перед записью в логи."""
    result = text
    for pattern in _LOG_MASK_PATTERNS:
        result = pattern.sub("[MASKED]", result)
    return result