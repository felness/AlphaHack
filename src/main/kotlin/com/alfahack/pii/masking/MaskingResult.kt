package com.alfahack.pii.masking

import com.alfahack.pii.store.MaskedSpan

/**
 * Результат маскирования.
 *
 * @param maskedText замаскированный текст
 * @param spans карта соответствий для демаскирования
 */
data class MaskingResult(
    val maskedText: String,
    val spans: List<MaskedSpan>,
)
