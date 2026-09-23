package com.alfahack.pii.detection

/**
 * Типы персональных данных (17 категорий из ТЗ).
 */
enum class PiiType {
    /** ФИО */
    FULL_NAME,

    /** Дата рождения */
    DATE_OF_BIRTH,

    /** Место рождения */
    PLACE_OF_BIRTH,

    /** Серия и номер паспорта */
    PASSPORT_SERIES_NUMBER,

    /** Гражданство */
    CITIZENSHIP,

    /** Орган, выдавший паспорт */
    PASSPORT_ISSUER,

    /** Код подразделения */
    PASSPORT_DEPARTMENT_CODE,

    /** Дата выдачи паспорта */
    PASSPORT_ISSUE_DATE,

    /** Серия и номер водительского удостоверения */
    DRIVER_LICENSE,

    /** Адрес (страна, индекс, город, улица, дом, квартира) */
    ADDRESS,

    /** Email */
    EMAIL,

    /** Номер телефона */
    PHONE,

    /** ИНН */
    INN,

    /** Номер платёжной банковской карты */
    CARD_NUMBER,

    /** CVV-код */
    CVV,

    /** Пин-код карты */
    PIN,

    /** Имя держателя карты */
    CARD_HOLDER_NAME,

    /** Загранпаспорт (2 буквы + 7 цифр) */
    FOREIGN_PASSPORT,

    /** СНИЛС (11 цифр) */
    SNILS,
}
