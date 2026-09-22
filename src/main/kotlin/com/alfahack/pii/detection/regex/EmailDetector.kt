package com.alfahack.pii.detection.regex

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор email-адресов.
 */
@Component
class EmailDetector : Detector {

    override val supportedTypes: Set<PiiType> = setOf(PiiType.EMAIL)

    private val pattern: Pattern = Pattern.compile(
        EMAIL_REGEX,
        Pattern.CASE_INSENSITIVE
    )

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            result.add(
                DetectedEntity(
                    type = PiiType.EMAIL,
                    start = matcher.start(),
                    end = matcher.end(),
                    confidence = 0.95,
                    source = DetectorSource.REGEX,
                    validated = true
                )
            )
        }

        return result
    }

    companion object {
        // Стандартный regex для email
        private const val EMAIL_REGEX =
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
    }
}