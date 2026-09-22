package com.alfahack.pii.detection

import org.springframework.stereotype.Component

/**
 * Контекстные правила определения принадлежности данных к ПД.
 *
 * Позитивный контекст усиливает уверенность, негативный — снижает.
 * Ключевое требование ТЗ: «упоминание поэта Александра Пушкина — не ПД;
 * адрес отделения Банка — не ПД».
 */
@Component
class ContextRules {

    /**
     * Проверить, есть ли позитивный контекст ПД вокруг позиции [start, end).
     *
     * @param text исходный текст
     * @param start начальная позиция
     * @param end конечная позиция
     * @return true, если рядом есть позитивный контекст
     */
    fun hasPositiveContext(text: String, start: Int, end: Int): Boolean {
        val window = contextWindow(text, start, end)
        return POSITIVE_KEYWORDS.any { window.contains(it, ignoreCase = true) }
    }

    /**
     * Проверить, есть ли негативный контекст (снижает уверенность).
     */
    fun hasNegativeContext(text: String, start: Int, end: Int): Boolean {
        val window = contextWindow(text, start, end)
        return NEGATIVE_KEYWORDS.any { window.contains(it, ignoreCase = true) }
    }

    /**
     * Окно контекста вокруг сущности (по N символов слева и справа).
     */
    private fun contextWindow(text: String, start: Int, end: Int): String {
        val from = (start - CONTEXT_WINDOW).coerceAtLeast(0)
        val to = (end + CONTEXT_WINDOW).coerceAtMost(text.length)
        return text.substring(from, to)
    }

    companion object {
        private const val CONTEXT_WINDOW = 40

        /** Позитивный контекст — усиливает уверенность, что это ПД. */
        private val POSITIVE_KEYWORDS = listOf(
            "паспорт", "серия", "номер", "дата рождения", "карта", "ИНН",
            "телефон", "адрес", "прописка", "выдан", "код подразделения",
            "место рождения", "гражданство", "водительское", "cvv", "пин",
            "card holder", "cardholder", "родился", "родилась", "зарегистрирован"
        )

        /** Негативный контекст — снижает уверенность (упоминание, не ПД). */
        private val NEGATIVE_KEYWORDS = listOf(
            "поэт", "писатель", "отделение банка", "филиал", "памятник",
            "улица", "проспект", "площадь", "набережная"
        )
    }
}