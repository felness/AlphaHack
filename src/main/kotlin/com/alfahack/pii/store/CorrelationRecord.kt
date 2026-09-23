package com.alfahack.pii.store

import com.alfahack.pii.detection.PiiType

/**
 * Запись соответствия «маскирование → демаскирование» для payload_id.
 *
 * @param original исходная строка
 * @param mask замаскированная строка
 * @param spans карта соответствий для демаскирования
 * @param systemId система-владелец записи (для ограничения демаскирования)
 * @param createdAt timestamp создания (мс)
 */
data class CorrelationRecord(
    val original: String,
    val mask: String,
    val spans: List<MaskedSpan>,
    val systemId: String = "default",
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Замаскированный фрагмент.
 *
 * @param type тип ПД
 * @param start начальная позиция в маске (включительно)
 * @param end конечная позиция в маске (исключительно)
 * @param original исходное значение
 */
data class MaskedSpan(
    val type: PiiType,
    val start: Int,
    val end: Int,
    val original: String,
)
