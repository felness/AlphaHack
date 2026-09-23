package com.alfahack.pii.detection.dictionary

import com.alfahack.pii.detection.ContextRules
import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор ФИО.
 *
 * Две стратегии с разными требованиями к контексту:
 *
 * 1. **С отчеством** («Иванов Иван Иванович», «Оганесян Гурген Ашотович») —
 *    суффикс отчества сам по себе однозначно указывает на ФИО, поэтому
 *    позитивный контекст не требуется. Регистр не важен: ТЗ требует, чтобы
 *    идентификация от него не зависела, поэтому «иванов иван иванович»
 *    находится наравне с «ИВАНОВ ИВАН ИВАНОВИЧ».
 *
 * 2. **Без отчества** (2-3 слова с заглавной) — признак слабый, поэтому
 *    нужен позитивный контекст ПД (паспорт, дата рождения и т.д.).
 *
 * Обе стратегии подавляются негативным контекстом: упоминание поэта
 * Александра Пушкина — не ПД (требование ТЗ).
 */
@Component
class NameDetector(
    private val contextRules: ContextRules,
) : Detector {
    override val supportedTypes: Set<PiiType> = setOf(PiiType.FULL_NAME)

    private val word = "\\p{IsCyrillic}+"
    private val patronymic = "\\p{IsCyrillic}+(?:${PATRONYMIC_SUFFIXES.joinToString("|")})"

    /** ФИО с отчеством: «Фамилия Имя Отчество» и «Имя Отчество Фамилия». */
    private val patronymicPatterns: List<Pattern> =
        listOf(
            "\\b$word\\s+$word\\s+$patronymic\\b",
            "\\b$word\\s+$patronymic\\s+$word\\b",
        ).map { Pattern.compile(it, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CHARACTER_CLASS) }

    /**
     * ФИО без отчества: 2-3 слова с заглавной буквы.
     *
     * Только кириллица: имя держателя карты пишут латиницей, и его разбирает
     * отдельный детектор — иначе «CARD HOLDER» попал бы сюда как ФИО.
     */
    private val capitalizedPattern: Pattern =
        Pattern.compile(
            "\\b(?:$UPPER$LOWER+|$UPPER{2,})(?:\\s+(?:$UPPER$LOWER+|$UPPER{2,})){1,2}\\b",
            Pattern.UNICODE_CHARACTER_CLASS,
        )

    override fun detect(text: String): List<DetectedEntity> {
        val result = mutableListOf<DetectedEntity>()

        for (pattern in patronymicPatterns) {
            collect(text, pattern, requirePositiveContext = false, result = result)
        }
        collect(text, capitalizedPattern, requirePositiveContext = true, result = result)

        return result
    }

    private fun collect(
        text: String,
        pattern: Pattern,
        requirePositiveContext: Boolean,
        result: MutableList<DetectedEntity>,
    ) {
        val matcher = pattern.matcher(text)
        val confidence = if (requirePositiveContext) CONTEXT_CONFIDENCE else PATRONYMIC_CONFIDENCE

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (isPersonalData(text, start, end, requirePositiveContext)) {
                result.add(
                    DetectedEntity(
                        type = PiiType.FULL_NAME,
                        start = start,
                        end = end,
                        confidence = confidence,
                        source = DetectorSource.DICTIONARY,
                        validated = true,
                    ),
                )
            }
        }
    }

    /**
     * Считать ли найденное сочетание слов персональными данными.
     */
    private fun isPersonalData(
        text: String,
        start: Int,
        end: Int,
        requirePositiveContext: Boolean,
    ): Boolean {
        if (contextRules.hasNegativeContext(text, start, end)) {
            return false
        }
        if (isStopPhrase(text.substring(start, end))) {
            return false
        }
        return !requirePositiveContext || contextRules.hasPositiveContext(text, start, end)
    }

    /**
     * Отсечь сочетания, которые выглядят как ФИО, но им не являются:
     * «Компания ООО Ромашка», «Паспорт РФ», «Российская Федерация».
     */
    private fun isStopPhrase(value: String): Boolean = value.split(WHITESPACE).any { it.lowercase() in STOP_WORDS }

    companion object {
        private const val PATRONYMIC_CONFIDENCE = 0.9
        private const val CONTEXT_CONFIDENCE = 0.85

        private val WHITESPACE = "\\s+".toRegex()

        /** Суффиксы отчеств. Длинные раньше коротких: «ьич» должен победить «ич». */
        private val PATRONYMIC_SUFFIXES =
            listOf("овича", "евича", "овна", "евна", "ична", "ович", "евич", "ьич")

        /** Слова, которые не могут быть частью ФИО. */
        private val STOP_WORDS =
            setOf(
                "ооо",
                "оао",
                "зао",
                "пао",
                "ао",
                "ип",
                "рф",
                "ссср",
                "компания",
                "организация",
                "банк",
                "паспорт",
                "гражданин",
                "гражданка",
                "гражданство",
                "российская",
                "федерация",
                "адрес",
                "город",
                "область",
                "район",
                "улица",
            )

        /** Заглавная кириллическая буква. */
        private const val UPPER = "[\\p{IsCyrillic}&&\\p{Lu}]"

        /** Строчная кириллическая буква. */
        private const val LOWER = "[\\p{IsCyrillic}&&\\p{Ll}]"
    }
}
