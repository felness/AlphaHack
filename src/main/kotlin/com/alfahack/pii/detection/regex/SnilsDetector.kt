package com.alfahack.pii.detection.regex

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор СНИЛС.
 *
 * Формат: 11 цифр (например, 123-456-789 01).
 * Маскируется при контексте «СНИЛС» или по однозначной структуре.
 */
@Component
class SnilsDetector : Detector {

    override val supportedTypes: Set<PiiType> = setOf(PiiType.SNILS)

    private val pattern: Pattern = Pattern.compile(SNILS_REGEX)

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            result.add(
                DetectedEntity(
                    type = PiiType.SNILS,
                    start = matcher.start(),
                    end = matcher.end(),
                    confidence = 0.85,
                    source = DetectorSource.REGEX,
                    validated = true
                )
            )
        }

        return result
    }

    companion object {
        // СНИЛС: 3-3-3 2 цифры (с дефисами и пробелом)
        private const val SNILS_REGEX =
            "\\b\\d{3}-\\d{3}-\\d{3}\\s\\d{2}\\b"
    }
}