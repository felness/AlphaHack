package com.alfahack.pii.detection.regex

import com.alfahack.pii.detection.ChecksumValidator
import com.alfahack.pii.detection.ContextRules
import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор номеров банковских карт с Luhn-валидацией.
 *
 * Номер карты: 13-19 цифр, часто сгруппирован по 4 (4276 1234 5678 9012).
 *
 * Стратегия «лучше перемаскировать»: при позитивном контексте («карта»)
 * маскируется даже невалидный по Luhn номер (пример из ТЗ 4276 1234 5678 9012
 * не проходит Luhn, но должен маскироваться при контексте «карта»).
 */
@Component
class CardDetector(
    private val checksumValidator: ChecksumValidator,
    private val contextRules: ContextRules
) : Detector {

    override val supportedTypes: Set<PiiType> = setOf(PiiType.CARD_NUMBER)

    private val pattern: Pattern = Pattern.compile(CARD_REGEX)

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val value = matcher.group().replace(" ", "").replace("-", "")
            val valid = checksumValidator.isValidLuhn(value)
            val hasContext = contextRules.hasPositiveContext(text, start, end)

            // Валидный Luhn → маскируем всегда. Невалидный → маскируем при позитивном контексте «карта».
            val confidence = when {
                valid -> 0.95
                hasContext -> 0.8
                else -> 0.5
            }
            result.add(
                DetectedEntity(
                    type = PiiType.CARD_NUMBER,
                    start = start,
                    end = end,
                    confidence = confidence,
                    source = DetectorSource.REGEX,
                    validated = valid
                )
            )
        }

        return result
    }

    companion object {
        // Номер карты: 13-19 цифр, сгруппирован по 4 (с пробелами или дефисами)
        private const val CARD_REGEX =
            "\\b(?:\\d[ -]*?){13,19}\\b"
    }
}