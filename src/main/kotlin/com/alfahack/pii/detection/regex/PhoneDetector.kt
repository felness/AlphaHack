package com.alfahack.pii.detection.regex

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор номеров телефонов (РФ и международные форматы).
 */
@Component
class PhoneDetector : Detector {
    override val supportedTypes: Set<PiiType> = setOf(PiiType.PHONE)

    private val pattern: Pattern = Pattern.compile(PHONE_REGEX, Pattern.UNICODE_CHARACTER_CLASS)

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            result.add(
                DetectedEntity(
                    type = PiiType.PHONE,
                    start = matcher.start(),
                    end = matcher.end(),
                    confidence = 0.9,
                    source = DetectorSource.REGEX,
                    validated = true,
                ),
            )
        }

        return result
    }

    companion object {
        // Разделитель внутри номера: пробел или дефис (необязательный)
        private const val SEP = "[\\s-]?"

        // Телефоны: +7 (900) 123-45-67, 8-900-123-45-67, 89001234567.
        // Границы слова \b с обеих сторон, чтобы не матчить часть более длинного числа.
        private const val PHONE_REGEX =
            "\\b(?:\\+?7|8)$SEP\\(?\\d{3}\\)?$SEP\\d{3}$SEP\\d{2}$SEP\\d{2}\\b"
    }
}
