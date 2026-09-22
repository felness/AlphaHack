package com.alfahack.pii.detection.regex

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор загранпаспорта РФ.
 *
 * Формат: 2 буквы/цифры + 7 цифр (например, 71 1234567).
 * Маскируется только при контексте «загранпаспорт»/«паспорт»,
 * чтобы не маскировать произвольные 9-значные числа.
 */
@Component
class ForeignPassportDetector : Detector {

    override val supportedTypes: Set<PiiType> = setOf(PiiType.FOREIGN_PASSPORT)

    private val pattern: Pattern = Pattern.compile(FOREIGN_PASSPORT_REGEX, Pattern.UNICODE_CHARACTER_CLASS)

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            // Загранпаспорт маскируется только при контексте «загранпаспорт»/«паспорт»
            if (hasPassportContext(text, start, end)) {
                result.add(
                    DetectedEntity(
                        type = PiiType.FOREIGN_PASSPORT,
                        start = start,
                        end = end,
                        confidence = 0.85,
                        source = DetectorSource.REGEX,
                        validated = true
                    )
                )
            }
        }

        return result
    }

    /**
     * Проверить, есть ли рядом контекст загранпаспорта.
     */
    private fun hasPassportContext(text: String, start: Int, end: Int): Boolean {
        val from = (start - CONTEXT_WINDOW).coerceAtLeast(0)
        val to = (end + CONTEXT_WINDOW).coerceAtMost(text.length)
        val window = text.substring(from, to)
        return PASSPORT_KEYWORDS.any { window.contains(it, ignoreCase = true) }
    }

    companion object {
        private const val CONTEXT_WINDOW = 40

        // Загранпаспорт: 2 буквы/цифры + 7 цифр (с пробелом или без)
        private const val FOREIGN_PASSPORT_REGEX =
            "\\b(?:[А-ЯЁ]{2}|\\d{2})\\s?\\d{7}\\b"

        // Ключевые слова загранпаспорта
        private val PASSPORT_KEYWORDS = listOf(
            "загранпаспорт", "загран", "паспорт"
        )
    }
}