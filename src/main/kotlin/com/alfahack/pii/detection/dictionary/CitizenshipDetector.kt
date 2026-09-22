package com.alfahack.pii.detection.dictionary

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор гражданства (словарный).
 *
 * Находит конструкции: «гражданин РФ», «гражданство: Российская Федерация».
 */
@Component
class CitizenshipDetector : Detector {

    override val supportedTypes: Set<PiiType> = setOf(PiiType.CITIZENSHIP)

    private val pattern: Pattern = Pattern.compile(CITIZENSHIP_REGEX, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CHARACTER_CLASS)

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            result.add(
                DetectedEntity(
                    type = PiiType.CITIZENSHIP,
                    start = matcher.start(),
                    end = matcher.end(),
                    confidence = 0.85,
                    source = DetectorSource.DICTIONARY,
                    validated = true
                )
            )
        }

        return result
    }

    companion object {
        // Гражданство: «гражданин РФ», «гражданство: Российская Федерация»
        private const val CITIZENSHIP_REGEX =
            "\\b(?:гражданин|гражданство)\\s*[:\\s]*(?:РФ|Российской\\s+Федерации|России)\\b"
    }
}