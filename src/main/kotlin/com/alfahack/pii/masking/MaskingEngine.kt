package com.alfahack.pii.masking

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.store.MaskedSpan
import org.springframework.stereotype.Component

/**
 * Движок маскирования: токенизация с сохранением длины.
 *
 * Каждый символ ПД заменяется на символ маски (по умолчанию '*'),
 * разделители (точки, дефисы, пробелы внутри значения) сохраняются.
 */
@Component
class MaskingEngine {
    /**
     * Замаскировать текст по найденным сущностям.
     *
     * @param text исходный текст
     * @param entities найденные сущности ПД
     * @param format формат маски (STAR по умолчанию)
     * @return результат маскирования
     */
    fun mask(
        text: String,
        entities: List<DetectedEntity>,
        format: MaskFormat = MaskFormat.STAR,
    ): MaskingResult {
        // Фильтруем сущности: только валидные spans в пределах текста
        val valid =
            entities
                .filter { it.start >= 0 && it.end <= text.length && it.start < it.end }
                .sortedBy { it.start }

        // Убираем перекрывающиеся spans: оставляем только неперекрывающиеся
        // (при перекрытии оставляем первую по позиции)
        val nonOverlapping = mutableListOf<DetectedEntity>()
        for (entity in valid) {
            val last = nonOverlapping.lastOrNull()
            if (last == null || last.end <= entity.start) {
                nonOverlapping.add(entity)
            }
            // Иначе — перекрытие, пропускаем (spans должны быть разрешены DetectionEngine)
        }

        val spans = mutableListOf<MaskedSpan>()
        val sb = StringBuilder(text)

        // Применяем маскирование с конца, чтобы не сдвигать позиции
        for (entity in nonOverlapping.asReversed()) {
            val original = text.substring(entity.start, entity.end)
            val masked = maskValue(original, format)
            sb.replace(entity.start, entity.end, masked)
            // Сохраняем только валидные spans (start < end, в пределах текста)
            if (entity.start < entity.end && entity.end <= text.length) {
                spans.add(
                    MaskedSpan(
                        type = entity.type,
                        start = entity.start,
                        end = entity.end,
                        original = original,
                    ),
                )
            }
        }

        return MaskingResult(
            maskedText = sb.toString(),
            spans = spans.sortedBy { it.start },
        )
    }

    /**
     * Замаскировать отдельное значение, сохраняя длину и разделители.
     */
    private fun maskValue(
        value: String,
        format: MaskFormat,
    ): String =
        when (format) {
            MaskFormat.STAR -> replaceAlphanumeric(value, STAR)

            MaskFormat.TOKEN -> replaceAlphanumeric(value, TOKEN)

            // Синтетические данные: замена на случайные символы (сохраняя длину и разделители)
            MaskFormat.SYNTHETIC -> value.map(::synthesizeChar).joinToString("")
        }

    /**
     * Заменить буквы и цифры на [replacement], оставив разделители на месте.
     */
    private fun replaceAlphanumeric(
        value: String,
        replacement: Char,
    ): String = value.map { ch -> if (ch.isLetterOrDigit()) replacement else ch }.joinToString("")

    /**
     * Подменить символ правдоподобным случайным того же класса.
     */
    private fun synthesizeChar(ch: Char): Char =
        when {
            ch.isDigit() -> '0' + random.nextInt(DIGITS)
            ch.isLetter() -> 'а' + random.nextInt(CYRILLIC_LETTERS)
            else -> ch
        }

    companion object {
        private const val STAR = '*'
        private const val TOKEN = 'X'
        private const val DIGITS = 10
        private const val CYRILLIC_LETTERS = 32

        // Синтетика подменяет ПД, поэтому источник должен быть непредсказуемым
        private val random = java.security.SecureRandom()
    }
}
