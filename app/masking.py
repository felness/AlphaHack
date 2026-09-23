"""Маскирование, токенизация и демаскирование персональных данных.

Маскирование заменяет найденные ПД на символы маски с сохранением длины
и позиций. Токенизация заменяет ПД на плейсхолдеры вида {TYPE_N}.
Демаскирование/детокенизация восстанавливает исходные значения.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from .detectors import Match, detect_all
from .pii_types import PiiType, get_pii_type


@dataclass
class MaskedSpan:
    """Замаскированный фрагмент с информацией для восстановления."""
    start: int
    end: int
    original: str
    pii_key: str
    token: str | None = None  # токен для токенизации, например "{FIO_1}"


@dataclass
class MaskResult:
    """Результат маскирования."""
    masked_text: str
    spans: list[MaskedSpan] = field(default_factory=list)


def _mask_stars(value: str, pii_type: PiiType) -> str:
    """Маскирует значение звёздочками, сохраняя длину и края."""
    n = len(value)
    keep_start = min(pii_type.keep_start, n)
    keep_end = min(pii_type.keep_end, n - keep_start)
    if keep_start + keep_end >= n:
        return pii_type.mask_char * n
    return (
        value[:keep_start]
        + pii_type.mask_char * (n - keep_start - keep_end)
        + value[n - keep_end:]
    )


def _mask_initials(value: str, pii_type: PiiType) -> str:
    """Маскирует ФИО инициалами: 'Иванов Иван Иванович' -> 'И. И. И.'."""
    words = [w for w in value.split() if w]
    if not words:
        return value
    initials = " ".join(w[0] + "." for w in words if w[0].isalpha())
    return initials if initials else pii_type.mask_char * len(value)


def _mask_preserve_separators(value: str, pii_type: PiiType) -> str:
    """Маскирует цифры, сохраняя разделители (пробелы/дефисы/скобки).

    '4509 123456' -> '45** ****56'
    '+7 900 123-45-67' -> '+7 *** ***-**-**'
    """
    keep_start = pii_type.keep_start
    keep_end = pii_type.keep_end
    result = []
    digit_count = 0
    total_digits = sum(1 for c in value if c.isdigit())
    for ch in value:
        if ch.isdigit():
            digit_count += 1
            if digit_count <= keep_start or digit_count > total_digits - keep_end:
                result.append(ch)
            else:
                result.append(pii_type.mask_char)
        else:
            result.append(ch)
    return "".join(result)


def _mask_value(value: str, pii_type: PiiType) -> str:
    """Маскирует значение по стратегии типа ПД."""
    strategy = pii_type.mask_strategy
    if strategy == "initials":
        return _mask_initials(value, pii_type)
    if strategy == "preserve_separators":
        return _mask_preserve_separators(value, pii_type)
    return _mask_stars(value, pii_type)


def _filter_matches(matches: list[Match], mask_types: list[str] | None) -> list[Match]:
    """Фильтрует совпадения по типам ПД."""
    if mask_types and "*" not in mask_types:
        allowed = set(mask_types)
        matches = [m for m in matches if m.pii_key in allowed]
    matches.sort(key=lambda m: m.start)
    return matches


def mask_text(text: str, mask_types: list[str] | None = None) -> MaskResult:
    """Находит ПД в тексте и маскирует их.

    mask_types: список ключей типов ПД для маскирования. None или ["*"] — все типы.
    """
    matches = _filter_matches(detect_all(text), mask_types)

    result = []
    spans: list[MaskedSpan] = []
    cursor = 0
    for m in matches:
        pii_type = get_pii_type(m.pii_key)
        if pii_type is None:
            continue
        # Пропускаем вложенные совпадения
        if m.start < cursor:
            continue
        # Добавляем текст до совпадения
        result.append(text[cursor:m.start])
        # Маскируем
        masked = _mask_value(m.value, pii_type)
        result.append(masked)
        spans.append(MaskedSpan(m.start, m.end, m.value, m.pii_key))
        cursor = m.end

    result.append(text[cursor:])
    return MaskResult(masked_text="".join(result), spans=spans)


def tokenize_text(text: str, mask_types: list[str] | None = None) -> MaskResult:
    """Заменяет ПД на токены вида {TYPE_N}.

    В отличие от маскирования, длина строки меняется, поэтому демаскирование
    выполняется по замене токенов на исходные значения.
    """
    matches = _filter_matches(detect_all(text), mask_types)

    result = []
    spans: list[MaskedSpan] = []
    cursor = 0
    counters: dict[str, int] = {}
    for m in matches:
        if m.start < cursor:
            continue
        # Добавляем текст до совпадения
        result.append(text[cursor:m.start])
        # Генерируем токен
        counters[m.pii_key] = counters.get(m.pii_key, 0) + 1
        token = f"{{{m.pii_key.upper()}_{counters[m.pii_key]}}}"
        result.append(token)
        spans.append(MaskedSpan(m.start, m.end, m.value, m.pii_key, token=token))
        cursor = m.end

    result.append(text[cursor:])
    return MaskResult(masked_text="".join(result), spans=spans)


def unmask_text(masked_text: str, spans: list[MaskedSpan]) -> str:
    """Восстанавливает исходный текст по замаскированным позициям.

    Замаскированная строка имеет ту же длину, что и исходная, поэтому
    позиции совпадают.
    """
    if not spans:
        return masked_text

    # Сортируем по позиции
    spans = sorted(spans, key=lambda s: s.start)

    result = []
    cursor = 0
    for span in spans:
        result.append(masked_text[cursor:span.start])
        result.append(span.original)
        cursor = span.end

    result.append(masked_text[cursor:])
    return "".join(result)


def detokenize_text(tokenized_text: str, spans: list[MaskedSpan]) -> str:
    """Восстанавливает исходный текст, заменяя токены на исходные значения."""
    if not spans:
        return tokenized_text

    result = tokenized_text
    # Заменяем токены на исходные значения (в обратном порядке, чтобы не конфликтовать)
    for span in sorted(spans, key=lambda s: len(s.token or ""), reverse=True):
        if span.token:
            result = result.replace(span.token, span.original)
    return result