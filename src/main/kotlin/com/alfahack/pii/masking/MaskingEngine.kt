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
    fun mask(text: String, entities: List<DetectedEntity>, format: MaskFormat = MaskFormat.STAR): MaskingResult {
        // Фильтруем сущности: только валидные spans в пределах текста
        val valid = entities
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
                        original = original
                    )
                )
            }
        }

        return MaskingResult(
            maskedText = sb.toString(),
            spans = spans.sortedBy { it.start }
        )
    }

    /**
     * Замаскировать отдельное значение, сохраняя длину и разделители.
     */
    private fun maskValue(value: String, format: MaskFormat): String {
        return when (format) {
            MaskFormat.STAR -> value.map { ch -> if (ch.isLetterOrDigit()) '*' else ch }.joinToString("")
            MaskFormat.TOKEN -> value.map { ch -> if (ch.isLetterOrDigit()) 'X' else ch }.joinToString("")
            // Синтетические данные: замена на случайные символы (сохраняя длину и разделители)
            MaskFormat.SYNTHETIC -> value.map { ch ->
                when {
                    ch.isDigit() -> ('0' + random.nextInt(10))
                    ch.isLetter() -> ('а' + random.nextInt(32))
                    else -> ch
                }
            }.joinToString("")
        }
    }

    companion object {
        private val random = java.util.Random()
    }
}