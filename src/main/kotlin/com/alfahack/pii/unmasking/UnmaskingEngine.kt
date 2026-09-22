package com.alfahack.pii.unmasking

import com.alfahack.pii.store.MaskedSpan
import org.springframework.stereotype.Component

/**
 * Движок демаскирования: восстановление исходной строки по spans.
 *
 * Т.к. демаскирование всегда получает вашу маску без изменений,
 * восстановление по позициям тривиально и надёжно.
 */
@Component
class UnmaskingEngine {

    /**
     * Восстановить исходную строку из маски по карте соответствий.
     *
     * @param maskedText замаскированный текст
     * @param spans карта соответствий
     * @return исходная строка
     */
    fun unmask(maskedText: String, spans: List<MaskedSpan>): String {
        val sb = StringBuilder(maskedText)

        // Фильтруем только валидные spans (в пределах текста, start < end)
        val valid = spans
            .filter { it.start >= 0 && it.end <= maskedText.length && it.start < it.end }
            .sortedByDescending { it.start }

        // Применяем с конца, чтобы не сдвигать позиции
        for (span in valid) {
            sb.replace(span.start, span.end, span.original)
        }

        return sb.toString()
    }
}