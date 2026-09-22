package com.alfahack.pii.detection.dictionary

import com.alfahack.pii.detection.ContextRules
import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор места рождения (словарный).
 *
 * Находит «г. Город» в контексте «место рождения», «родился», «родилась».
 */
@Component
class PlaceOfBirthDetector(
    private val contextRules: ContextRules
) : Detector {

    override val supportedTypes: Set<PiiType> = setOf(PiiType.PLACE_OF_BIRTH)

    private val pattern: Pattern = Pattern.compile(PLACE_OF_BIRTH_REGEX, Pattern.UNICODE_CHARACTER_CLASS)

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            // Место рождения маскируется при позитивном контексте и отсутствии негативного
            if (contextRules.hasPositiveContext(text, start, end) &&
                !contextRules.hasNegativeContext(text, start, end)
            ) {
                result.add(
                    DetectedEntity(
                        type = PiiType.PLACE_OF_BIRTH,
                        start = start,
                        end = end,
                        confidence = 0.85,
                        source = DetectorSource.DICTIONARY,
                        validated = true
                    )
                )
            }
        }

        return result
    }

    companion object {
        // Место рождения: «г. Город»
        private const val PLACE_OF_BIRTH_REGEX =
            "\\bг\\.\\s*[А-ЯЁ][а-яё-]+\\b"
    }
}