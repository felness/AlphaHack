package com.alfahack.pii.detection.regex

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор серии и номера водительского удостоверения РФ.
 *
 * Формат: 2 цифры (регион) + 2 цифры (серия) + 6 цифр (номер).
 * Примеры: "77 12 345678", "77-12-345678".
 *
 * Маскируется только при контексте «водительское удостоверение»,
 * чтобы не маскировать произвольные 10-значные числа (например, невалидный ИНН).
 */
@Component
class DriverLicenseDetector : Detector {
    override val supportedTypes: Set<PiiType> = setOf(PiiType.DRIVER_LICENSE)

    private val pattern: Pattern = Pattern.compile(DRIVER_LICENSE_REGEX, Pattern.UNICODE_CHARACTER_CLASS)

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            // В/у маскируется только при контексте «водительское»
            if (hasDriverLicenseContext(text, start, end)) {
                result.add(
                    DetectedEntity(
                        type = PiiType.DRIVER_LICENSE,
                        start = start,
                        end = end,
                        confidence = 0.85,
                        source = DetectorSource.REGEX,
                        validated = true,
                    ),
                )
            }
        }

        return result
    }

    /**
     * Проверить, есть ли рядом контекст «водительское удостоверение».
     */
    private fun hasDriverLicenseContext(
        text: String,
        start: Int,
        end: Int,
    ): Boolean {
        val from = (start - CONTEXT_WINDOW).coerceAtLeast(0)
        val to = (end + CONTEXT_WINDOW).coerceAtMost(text.length)
        val window = text.substring(from, to)
        return DRIVER_LICENSE_KEYWORDS.any { window.contains(it, ignoreCase = true) }
    }

    companion object {
        private const val CONTEXT_WINDOW = 40

        // В/у: 2 цифры + разделитель + 2 цифры + разделитель + 6 цифр
        private const val DRIVER_LICENSE_REGEX =
            "\\b\\d{2}[\\s\\-]?\\d{2}[\\s\\-]?\\d{6}\\b"

        // Ключевые слова водительского удостоверения
        private val DRIVER_LICENSE_KEYWORDS =
            listOf(
                "водительское",
                "удостоверение",
                "в/у",
                "права",
            )
    }
}
