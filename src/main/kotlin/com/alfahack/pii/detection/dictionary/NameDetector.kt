package com.alfahack.pii.detection.dictionary

import com.alfahack.pii.detection.ContextRules
import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор ФИО (словарный).
 *
 * Находит последовательности из 2-3 слов с заглавной буквы (кириллица).
 * Маскируется ТОЛЬКО при позитивном контексте ПД (паспорт, дата рождения и т.д.),
 * иначе это может быть упоминание известной личности (Пушкин — не ПД).
 */
@Component
class NameDetector(
    private val contextRules: ContextRules,
) : Detector {
    override val supportedTypes: Set<PiiType> = setOf(PiiType.FULL_NAME)

    private val pattern: Pattern = Pattern.compile(NAME_REGEX, Pattern.UNICODE_CHARACTER_CLASS)

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            // ФИО маскируется только при позитивном контексте ПД
            if (contextRules.hasPositiveContext(text, start, end) &&
                !contextRules.hasNegativeContext(text, start, end)
            ) {
                result.add(
                    DetectedEntity(
                        type = PiiType.FULL_NAME,
                        start = start,
                        end = end,
                        confidence = 0.85,
                        source = DetectorSource.DICTIONARY,
                        validated = true,
                    ),
                )
            }
        }

        return result
    }

    companion object {
        // 2-3 слова (кириллица), разделённые пробелами.
        // Допускает: «Иванов Иван» (смешанный), «ИВАНОВ ИВАН» (все заглавные).
        private const val NAME_REGEX =
            "\\b(?:[А-ЯЁ][а-яё]+|[А-ЯЁ]{2,})(?:\\s+(?:[А-ЯЁ][а-яё]+|[А-ЯЁ]{2,})){1,2}\\b"
    }
}
