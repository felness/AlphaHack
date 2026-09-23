package com.alfahack.pii.detection.regex

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор CVV-кода банковской карты.
 *
 * CVV: 3 цифры, обычно в контексте «CVV», «cvv», «код».
 * Без контекста 3 цифры — слишком слабый сигнал (не маскируется).
 */
@Component
class CvvDetector : Detector {
    override val supportedTypes: Set<PiiType> = setOf(PiiType.CVV)

    private val pattern: Pattern = Pattern.compile(CVV_REGEX, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CHARACTER_CLASS)

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            // Span = только 3 цифры (группа 1), служебное слово «cvv»/«код» не маскируется
            result.add(
                DetectedEntity(
                    type = PiiType.CVV,
                    start = matcher.start(1),
                    end = matcher.end(1),
                    confidence = 0.9,
                    source = DetectorSource.REGEX,
                    validated = true,
                ),
            )
        }

        return result
    }

    companion object {
        // CVV: слово «cvv»/«код» + 3 цифры — группа 1 = цифры
        private const val CVV_REGEX =
            "\\b(?:cvv|cvc|код)\\s*[:\\s]?\\s*(\\d{3})\\b"
    }
}
