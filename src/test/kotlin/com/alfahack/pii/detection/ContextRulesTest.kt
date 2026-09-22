package com.alfahack.pii.detection

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Тесты контекстных правил (позитивный/негативный контекст).
 */
class ContextRulesTest {

    private val rules = ContextRules()

    @Test
    fun `positive context detected`() {
        // «паспорт» перед ФИО — позитивный контекст
        assertTrue(rules.hasPositiveContext("паспорт Иванов Иван Иванович", 8, 26))
        // «дата рождения» перед датой
        assertTrue(rules.hasPositiveContext("дата рождения 12.05.1990", 14, 24))
        // «карта» перед номером
        assertTrue(rules.hasPositiveContext("карта 4276 1234 5678 9014", 6, 22))
    }

    @Test
    fun `no positive context`() {
        assertFalse(rules.hasPositiveContext("Александр Пушкин", 0, 16))
        assertFalse(rules.hasPositiveContext("Иванов Иван", 0, 11))
    }

    @Test
    fun `negative context detected`() {
        assertTrue(rules.hasNegativeContext("поэт Александр Пушкин", 5, 21))
        assertTrue(rules.hasNegativeContext("отделение Банка по адресу г. Москва", 20, 30))
    }

    @Test
    fun `no negative context`() {
        assertFalse(rules.hasNegativeContext("паспорт 4509 123456", 8, 18))
    }
}