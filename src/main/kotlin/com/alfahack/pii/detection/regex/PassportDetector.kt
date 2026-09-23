package com.alfahack.pii.detection.regex

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор серии и номера паспорта РФ.
 *
 * Формат: серия 4 цифры + номер 6 цифр.
 * Примеры: "4509 123456", "серия 4509 номер 123456", "4509-123456", "4509123456".
 *
 * Для формата «серия 4509 номер 123456» возвращает два отдельных span (4509 и 123456),
 * чтобы служебные слова «серия»/«номер» не маскировались.
 *
 * Формат без разделителя (4509123456) маскируется только при контексте «паспорт»/«серия»/«номер»,
 * чтобы не путать с ИНН (10 цифр).
 */
@Component
class PassportDetector : Detector {
    override val supportedTypes: Set<PiiType> = setOf(PiiType.PASSPORT_SERIES_NUMBER)

    // Компактный формат с разделителем: 4509 123456, 4509-123456
    private val compactPattern: Pattern = Pattern.compile(COMPACT_REGEX, Pattern.UNICODE_CHARACTER_CLASS)

    // Формат без разделителя: 4509123456 (только при контексте паспорта)
    private val noSeparatorPattern: Pattern = Pattern.compile(NO_SEPARATOR_REGEX, Pattern.UNICODE_CHARACTER_CLASS)

    // Серия (4 цифры) после слова «серия» — span = только цифры (группа 1)
    private val seriesPattern: Pattern = Pattern.compile(SERIES_REGEX, Pattern.UNICODE_CHARACTER_CLASS)

    // Номер (6 цифр) после слова «номер» — span = только цифры (группа 1)
    private val numberPattern: Pattern = Pattern.compile(NUMBER_REGEX, Pattern.UNICODE_CHARACTER_CLASS)

    override fun detect(text: String): List<DetectedEntity> {
        val result = mutableListOf<DetectedEntity>()

        // Компактный формат с разделителем: 4509 123456
        val compactMatcher = compactPattern.matcher(text)
        while (compactMatcher.find()) {
            result.add(entity(compactMatcher.start(), compactMatcher.end()))
        }

        // Формат без разделителя: 4509123456 (только при контексте паспорта)
        val noSeparatorMatcher = noSeparatorPattern.matcher(text)
        while (noSeparatorMatcher.find()) {
            val start = noSeparatorMatcher.start()
            val end = noSeparatorMatcher.end()
            if (hasPassportContext(text, start, end)) {
                result.add(entity(start, end))
            }
        }

        // Серия отдельно: «серия 4509» → span = 4509
        val seriesMatcher = seriesPattern.matcher(text)
        while (seriesMatcher.find()) {
            result.add(entity(seriesMatcher.start(1), seriesMatcher.end(1)))
        }

        // Номер отдельно: «номер 123456» → span = 123456
        val numberMatcher = numberPattern.matcher(text)
        while (numberMatcher.find()) {
            result.add(entity(numberMatcher.start(1), numberMatcher.end(1)))
        }

        return result
    }

    /**
     * Проверить, есть ли рядом контекст паспорта.
     */
    private fun hasPassportContext(
        text: String,
        start: Int,
        end: Int,
    ): Boolean {
        val from = (start - CONTEXT_WINDOW).coerceAtLeast(0)
        val to = (end + CONTEXT_WINDOW).coerceAtMost(text.length)
        val window = text.substring(from, to)
        return PASSPORT_KEYWORDS.any { window.contains(it, ignoreCase = true) }
    }

    private fun entity(
        start: Int,
        end: Int,
    ): DetectedEntity =
        DetectedEntity(
            type = PiiType.PASSPORT_SERIES_NUMBER,
            start = start,
            end = end,
            confidence = 0.9,
            source = DetectorSource.REGEX,
            validated = true,
        )

    companion object {
        private const val CONTEXT_WINDOW = 40

        // Серия (4 цифры) + разделитель + номер (6 цифр)
        private const val COMPACT_REGEX =
            "\\b\\d{4}[\\s\\-]\\d{6}\\b"

        // Формат без разделителя: 10 цифр (4+6)
        private const val NO_SEPARATOR_REGEX =
            "\\b\\d{10}\\b"

        // Серия (4 цифры) после слова «серия» — группа 1 = цифры
        private const val SERIES_REGEX =
            "\\bсерия\\s+(\\d{4})\\b"

        // Номер (6 цифр) после слова «номер» — группа 1 = цифры
        private const val NUMBER_REGEX =
            "\\bномер\\s+(\\d{6})\\b"

        // Ключевые слова паспорта
        private val PASSPORT_KEYWORDS =
            listOf(
                "паспорт",
                "серия",
                "номер",
                "документ",
            )
    }
}
