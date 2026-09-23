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
 * - Числовые с днём впереди: дд.мм.гггг, мм.дд.гггг, дд/мм/гггг, дд-мм-гггг
 * - Числовые с годом впереди: гггг.мм.дд, гггг-мм-дд, гггг/мм/дд
 * - Месяц словом: «12 мая 1990», «12 янв. 1990»
 * - Полностью текстовые: «двенадцатого мая 1990 года», «двенадцатого марта
 *   тысяча девятьсот девяностого года»
 *
 * Шаблоны собираются из словарей во время выполнения — так их можно читать
 * и дополнять, не редактируя одну гигантскую строку.
 *
 * Тип определяется по контексту: «дата рождения» → DATE_OF_BIRTH,
 * «дата выдачи»/«выдан» → PASSPORT_ISSUE_DATE.
 */
@Component
class DateDetector : Detector {
    override val supportedTypes: Set<PiiType> =
        setOf(
            PiiType.DATE_OF_BIRTH,
            PiiType.PASSPORT_ISSUE_DATE,
        )

    /** Месяц словом: полная форма или сокращение с необязательной точкой. */
    private val monthAlternation: String =
        (FULL_MONTHS + SHORT_MONTHS.map { "$it\\.?" }).joinToString("|")

    /** День словом: «первого» … «тридцать первого». */
    private val dayWordAlternation: String = DAY_WORDS.joinToString("|")

    /** Год: четыре цифры либо прописью («тысяча девятьсот девяностого»). */
    private val yearAlternation: String = "\\d{4}|тысяча(?:\\s+\\p{IsCyrillic}+){1,3}"

    private val patterns: List<Pattern> =
        listOf(
            // дд.мм.гггг, мм.дд.гггг, дд/мм/гггг, дд-мм-гггг
            "\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{4}\\b",
            // гггг.мм.дд, гггг-мм-дд, гггг/мм/дд
            "\\b\\d{4}[./-]\\d{1,2}[./-]\\d{1,2}\\b",
            // 12 мая 1990, 12 янв. 1990
            "\\b\\d{1,2}\\s+(?:$monthAlternation)\\s+\\d{4}\\b",
            // двенадцатого мая 1990, двенадцатого марта тысяча девятьсот девяностого
            "\\b(?:$dayWordAlternation)\\s+(?:$monthAlternation)\\s+(?:$yearAlternation)",
        ).map { Pattern.compile(it, Pattern.UNICODE_CHARACTER_CLASS) }

    override fun detect(text: String): List<DetectedEntity> {
        val result = mutableListOf<DetectedEntity>()

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                result.add(buildEntity(text, matcher.start(), matcher.end()))
            }
        }

        return result
    }

    private fun buildEntity(
        text: String,
        start: Int,
        end: Int,
    ): DetectedEntity =
        DetectedEntity(
            type = resolveType(text, start, end),
            start = start,
            end = end,
            confidence = 0.85,
            source = DetectorSource.REGEX,
            validated = true,
        )

    /**
     * Определить тип даты по контексту.
     */
    private fun resolveType(
        text: String,
        start: Int,
        end: Int,
    ): PiiType {
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

        /** Месяцы в родительном падеже — как их пишут в датах. */
        private val FULL_MONTHS =
            listOf(
                "января",
                "февраля",
                "марта",
                "апреля",
                "мая",
                "июня",
                "июля",
                "августа",
                "сентября",
                "октября",
                "ноября",
                "декабря",
            )

        /** Сокращения месяцев. Длинные раньше коротких: иначе «сен» перекроет «сент». */
        private val SHORT_MONTHS =
            listOf(
                "янв",
                "февр",
                "фев",
                "мар",
                "апр",
                "июн",
                "июл",
                "авг",
                "сент",
                "сен",
                "окт",
                "нояб",
                "ноя",
                "дек",
            )

        /** Числительные-дни. Составные раньше простых: иначе «двадцать первого» распадётся. */
        private val DAY_WORDS =
            listOf(
                "двадцать первого",
                "двадцать второго",
                "двадцать третьего",
                "двадцать четвертого",
                "двадцать четвёртого",
                "двадцать пятого",
                "двадцать шестого",
                "двадцать седьмого",
                "двадцать восьмого",
                "двадцать девятого",
                "тридцать первого",
                "тридцатого",
                "одиннадцатого",
                "двенадцатого",
                "тринадцатого",
                "четырнадцатого",
                "пятнадцатого",
                "шестнадцатого",
                "семнадцатого",
                "восемнадцатого",
                "девятнадцатого",
                "двадцатого",
                "первого",
                "второго",
                "третьего",
                "четвертого",
                "четвёртого",
                "пятого",
                "шестого",
                "седьмого",
                "восьмого",
                "девятого",
                "десятого",
            ).map { it.replace(" ", "\\s+") }

        /** Ключевые слова даты выдачи паспорта. */
        private val ISSUE_DATE_KEYWORDS =
            listOf(
                "дата выдачи",
                "выдан",
                "выдано",
                "выдана",
            )
    }
}
