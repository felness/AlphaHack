package com.alfahack.pii.detection.regex

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор кода подразделения паспорта РФ.
 *
 * Формат: 3 цифры + дефис + 3 цифры (770-001).
 * Маскируется только при контексте «код подразделения»,
 * чтобы не маскировать произвольные числа вида 3-3.
 */
@Component
class DepartmentCodeDetector : Detector {

    override val supportedTypes: Set<PiiType> = setOf(PiiType.PASSPORT_DEPARTMENT_CODE)

    private val pattern: Pattern = Pattern.compile(DEPARTMENT_CODE_REGEX)

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            // Код подразделения маскируется только при контексте «код подразделения»
            if (hasDepartmentCodeContext(text, start, end)) {
                result.add(
                    DetectedEntity(
                        type = PiiType.PASSPORT_DEPARTMENT_CODE,
                        start = start,
                        end = end,
                        confidence = 0.9,
                        source = DetectorSource.REGEX,
                        validated = true
                    )
                )
            }
        }

        return result
    }

    /**
     * Проверить, есть ли рядом контекст «код подразделения».
     */
    private fun hasDepartmentCodeContext(text: String, start: Int, end: Int): Boolean {
        val from = (start - CONTEXT_WINDOW).coerceAtLeast(0)
        val to = (end + CONTEXT_WINDOW).coerceAtMost(text.length)
        val window = text.substring(from, to)
        return DEPARTMENT_CODE_KEYWORDS.any { window.contains(it, ignoreCase = true) }
    }

    companion object {
        private const val CONTEXT_WINDOW = 40

        // Код подразделения: 3 цифры + дефис + 3 цифры
        private const val DEPARTMENT_CODE_REGEX =
            "\\b\\d{3}-\\d{3}\\b"

        // Ключевые слова кода подразделения
        private val DEPARTMENT_CODE_KEYWORDS = listOf(
            "код подразделения", "подразделение", "код"
        )
    }
}