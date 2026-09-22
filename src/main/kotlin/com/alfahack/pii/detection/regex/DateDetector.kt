package com.alfahack.pii.detection.regex

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор дат (дата рождения, дата выдачи паспорта).
 *
 * Поддерживает форматы:
 * - Числовые: дд.мм.гггг, мм.дд.гггг, гггг-мм-дд, дд/мм/гггг, дд-мм-гггг
 * - Текстовые: «двенадцатого мая 1990 года», «12 мая 1990»
 *
 * Различает тип по контексту: «дата рождения» → DATE_OF_BIRTH,
 * «дата выдачи»/«выдан» → PASSPORT_ISSUE_DATE.
 */
@Component
class DateDetector : Detector {

    override val supportedTypes: Set<PiiType> = setOf(
        PiiType.DATE_OF_BIRTH,
        PiiType.PASSPORT_ISSUE_DATE
    )

    // Формат дд.мм.гггг / мм.дд.гггг / дд/мм/гггг / дд-мм-гггг
    private val dayFirstPattern: Pattern = Pattern.compile(DAY_FIRST_REGEX)

    // Формат гггг-мм-дд (год в начале)
    private val yearFirstPattern: Pattern = Pattern.compile(YEAR_FIRST_REGEX)

    // Текстовый формат: «12 мая 1990» (число + месяц + год)
    private val textNumericPattern: Pattern = Pattern.compile(TEXT_NUMERIC_REGEX, Pattern.UNICODE_CHARACTER_CLASS)

    // Текстовый формат: «двенадцатого мая 1990 года» (числительное + месяц + год)
    private val textWordPattern: Pattern = Pattern.compile(TEXT_WORD_REGEX, Pattern.UNICODE_CHARACTER_CLASS)

    override fun detect(text: String): List<DetectedEntity> {
        val result = mutableListOf<DetectedEntity>()

        val dayFirstMatcher = dayFirstPattern.matcher(text)
        while (dayFirstMatcher.find()) {
            result.add(buildEntity(text, dayFirstMatcher.start(), dayFirstMatcher.end()))
        }

        val yearFirstMatcher = yearFirstPattern.matcher(text)
        while (yearFirstMatcher.find()) {
            result.add(buildEntity(text, yearFirstMatcher.start(), yearFirstMatcher.end()))
        }

        val textNumericMatcher = textNumericPattern.matcher(text)
        while (textNumericMatcher.find()) {
            result.add(buildEntity(text, textNumericMatcher.start(), textNumericMatcher.end()))
        }

        val textWordMatcher = textWordPattern.matcher(text)
        while (textWordMatcher.find()) {
            result.add(buildEntity(text, textWordMatcher.start(), textWordMatcher.end()))
        }

        return result
    }

    private fun buildEntity(text: String, start: Int, end: Int): DetectedEntity {
        val type = resolveType(text, start, end)
        return DetectedEntity(
            type = type,
            start = start,
            end = end,
            confidence = 0.85,
            source = DetectorSource.REGEX,
            validated = true
        )
    }

    /**
     * Определить тип даты по контексту.
     */
    private fun resolveType(text: String, start: Int, end: Int): PiiType {
        val from = (start - CONTEXT_WINDOW).coerceAtLeast(0)
        val to = (end + CONTEXT_WINDOW).coerceAtMost(text.length)
        val window = text.substring(from, to)

        return if (ISSUE_DATE_KEYWORDS.any { window.contains(it, ignoreCase = true) }) {
            PiiType.PASSPORT_ISSUE_DATE
        } else {
            PiiType.DATE_OF_BIRTH
        }
    }

    companion object {
        private const val CONTEXT_WINDOW = 30

        // Даты: дд.мм.гггг, мм.дд.гггг, дд/мм/гггг, дд-мм-гггг
        private const val DAY_FIRST_REGEX =
            "\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{4}\\b"

        // Даты: гггг-мм-дд (год в начале)
        private const val YEAR_FIRST_REGEX =
            "\\b\\d{4}-\\d{1,2}-\\d{1,2}\\b"

        // Текстовый формат: «12 мая 1990» (число + месяц + год)
        private const val TEXT_NUMERIC_REGEX =
            "\\b\\d{1,2}\\s+(?:января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\\s+\\d{4}\\b"

        // Текстовый формат: «двенадцатого мая 1990 года» (числительное + месяц + год, «года» не входит в span)
        private const val TEXT_WORD_REGEX =
            "\\b(?:первого|второго|третьего|четвертого|четвёртого|пятого|шестого|седьмого|восьмого|девятого|десятого|одиннадцатого|двенадцатого|тринадцатого|четырнадцатого|пятнадцатого|шестнадцатого|семнадцатого|восемнадцатого|девятнадцатого|двадцатого|двадцать\\s+первого|двадцать\\s+второго|двадцать\\s+третьего|двадцать\\s+четвертого|двадцать\\s+четвёртого|двадцать\\s+пятого|двадцать\\s+шестого|двадцать\\s+седьмого|двадцать\\s+восьмого|двадцать\\s+девятого|тридцатого|тридцать\\s+первого)\\s+(?:января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\\s+\\d{4}(?=\\s+года)\\b"

        // Ключевые слова даты выдачи паспорта
        private val ISSUE_DATE_KEYWORDS = listOf(
            "дата выдачи", "выдан", "выдано", "выдана"
        )
    }
}