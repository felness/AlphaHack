"""Тесты маскирования/демаскирования."""
import pytest

from app.masking import detokenize_text, mask_text, tokenize_text, unmask_text


@pytest.mark.parametrize(
    "text,expected_pii",
    [
        ("Клиент Иванов Иван Иванович", ["fio"]),
        ("Телефон: +7 (912) 345-67-89", ["phone"]),
        ("email: ivan@mail.ru", ["email"]),
        ("Дата рождения: 12.03.1990", ["birth_date"]),
        ("ИНН 770123456789", ["inn"]),
        ("Карта 4276 1234 5678 9012", ["card_number"]),
        ("CVV 123", ["cvv"]),
        ("ПИН-код 4567", ["pin"]),
        ("паспорт 4509 123456", ["passport_series_number"]),
        ("Код подразделения 770-123", ["passport_department_code"]),
        ("Водительское удостоверение 77 12 345678", ["driver_license"]),
        ("Гражданство: гражданин Российской Федерации", ["citizenship"]),
        ("Дата выдачи: 15.05.2015", ["passport_issue_date"]),
        ("Паспорт серия 4509 номер 123456", ["passport_series_number"]),
    ],
)
def test_mask_detects_pii(text, expected_pii):
    result = mask_text(text)
    detected = {s.pii_key for s in result.spans}
    for pii in expected_pii:
        assert pii in detected, f"Не найден тип {pii} в: {text}"


@pytest.mark.parametrize(
    "text",
    [
        "Клиент Иванов Иван Иванович, паспорт 4509 123456",
        "Телефон: +7 (912) 345-67-89, email: ivan@mail.ru",
        "Дата рождения: 12.03.1990, ИНН 770123456789",
        "Карта 4276 1234 5678 9012, CVV 123, ПИН-код 4567",
        "Адрес: г. Москва, ул. Ленина, д. 10, кв. 5",
        "Гражданство: гражданин Российской Федерации",
        "Водительское удостоверение 77 12 345678",
        "Код подразделения 770-123",
        "Обычный текст без персональных данных",
        "Сумма заказа 1500 рублей, оплата прошла успешно",
        "Дата выдачи: 15.05.2015, паспорт 4509 123456",
        "Паспорт серия 4509 номер 123456",
        "Адрес: 101000, г. Москва, ул. Ленина, д. 10, кв. 5",
        "Выдан 12 января 2010 года",
    ],
)
def test_unmask_roundtrip(text):
    """Демаскирование должно восстанавливать исходный текст."""
    result = mask_text(text)
    restored = unmask_text(result.masked_text, result.spans)
    assert restored == text


def test_mask_preserves_length():
    """Маскирование сохраняет длину строки."""
    text = "Клиент Иванов Иван Иванович, паспорт 4509 123456"
    result = mask_text(text)
    assert len(result.masked_text) == len(text)


def test_mask_keeps_edges():
    """Маскирование сохраняет края для паспорта."""
    text = "паспорт 4509 123456"
    result = mask_text(text)
    assert "45" in result.masked_text
    assert "56" in result.masked_text


def test_no_false_positive_on_plain_text():
    """Обычный текст не должен маскироваться."""
    text = "Сегодня хорошая погода, температура 25 градусов"
    result = mask_text(text)
    assert result.masked_text == text


def test_mask_types_filter():
    """Фильтрация типов ПД для маскирования."""
    text = "Клиент Иванов Иван Иванович, телефон +7 (912) 345-67-89"
    # Только телефон
    result = mask_text(text, mask_types=["phone"])
    assert "Иванов" in result.masked_text  # ФИО не замаскировано
    assert "345-67" not in result.masked_text  # телефон замаскирован (середина)
    # Только ФИО
    result = mask_text(text, mask_types=["fio"])
    assert "Иванов" not in result.masked_text
    assert "345-67" in result.masked_text


def test_tokenize():
    """Токенизация заменяет ПД на {TYPE_N}."""
    text = "Клиент Иванов Иван Иванович, телефон +7 (912) 345-67-89"
    result = tokenize_text(text)
    assert "{FIO_1}" in result.masked_text
    assert "{PHONE_1}" in result.masked_text
    assert "Иванов" not in result.masked_text
    assert "+7" not in result.masked_text


def test_detokenize_roundtrip():
    """Детокенизация восстанавливает исходный текст."""
    text = "Клиент Иванов Иван Иванович, телефон +7 (912) 345-67-89, email ivan@mail.ru"
    result = tokenize_text(text)
    restored = detokenize_text(result.masked_text, result.spans)
    assert restored == text


def test_tokenize_multiple_same_type():
    """Несколько ПД одного типа получают разные токены."""
    text = "Иван Иванов и Пётр Петров"
    result = tokenize_text(text)
    assert "{FIO_1}" in result.masked_text
    assert "{FIO_2}" in result.masked_text
    restored = detokenize_text(result.masked_text, result.spans)
    assert restored == text


def test_tokenize_filters_types():
    """Токенизация с фильтром по типам."""
    text = "Клиент Иванов Иван Иванович, телефон +7 (912) 345-67-89"
    result = tokenize_text(text, mask_types=["phone"])
    assert "Иванов" in result.masked_text  # ФИО не токенизировано
    assert "{PHONE_1}" in result.masked_text