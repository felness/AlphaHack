"""Типы персональных данных и их метаданные.

Каждый тип ПД имеет:
- key: уникальный идентификатор
- label: человекочитаемое название
- mask_char: символ маскирования
- keep_edges: сколько символов оставить видимыми с начала/конца
"""
from dataclasses import dataclass


@dataclass(frozen=True)
class PiiType:
    key: str
    label: str
    mask_char: str = "*"
    keep_start: int = 0   # сколько символов оставить в начале
    keep_end: int = 0     # сколько символов оставить в конце


# Все поддерживаемые типы ПД из ТЗ
PII_TYPES: dict[str, PiiType] = {
    "fio": PiiType("fio", "ФИО", keep_start=1, keep_end=0),
    "birth_date": PiiType("birth_date", "Дата рождения"),
    "birth_place": PiiType("birth_place", "Место рождения"),
    "passport_series_number": PiiType("passport_series_number", "Серия и номер паспорта", keep_start=2, keep_end=2),
    "citizenship": PiiType("citizenship", "Гражданство"),
    "passport_issuer": PiiType("passport_issuer", "Орган, выдавший паспорт"),
    "passport_department_code": PiiType("passport_department_code", "Код подразделения", keep_start=2, keep_end=2),
    "passport_issue_date": PiiType("passport_issue_date", "Дата выдачи паспорта"),
    "driver_license": PiiType("driver_license", "Серия и номер водительского удостоверения", keep_start=2, keep_end=2),
    "address": PiiType("address", "Адрес"),
    "email": PiiType("email", "Email", keep_start=1, keep_end=1),
    "phone": PiiType("phone", "Номер телефона", keep_start=2, keep_end=2),
    "inn": PiiType("inn", "ИНН", keep_start=2, keep_end=2),
    "card_number": PiiType("card_number", "Номер платёжной банковской карты", keep_start=4, keep_end=4),
    "cvv": PiiType("cvv", "CVV-код"),
    "pin": PiiType("pin", "Пин-код карты"),
    "card_holder": PiiType("card_holder", "Имя держателя карты"),
}


def get_pii_type(key: str) -> PiiType | None:
    return PII_TYPES.get(key)