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
 * Детектор ИНН с checksum-валидацией.
 *
 * ИНН: 10 цифр (юрлицо) или 12 цифр (физлицо).
 * Валидация по контрольной сумме.
 *
 * Стратегия «лучше перемаскировать»: при позитивном контексте («ИНН»)
 * маскируется даже невалидный по checksum ИНН (пример из ТЗ 770123456789
 * не проходит контрольную сумму, но должен маскироваться при контексте «ИНН»).
 */
@Component
class InnDetector(
    private val checksumValidator: ChecksumValidator,
    private val contextRules: ContextRules
) : Detector {

    override val supportedTypes: Set<PiiType> = setOf(PiiType.INN)

    private val pattern: Pattern = Pattern.compile("\\b\\d{10}(?:\\d{2})?\\b")

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            val value = matcher.group()
            val valid = checksumValidator.isValidInn(value)
            val hasContext = contextRules.hasPositiveContext(text, start, end)

            // Валидный checksum → маскируем всегда. Невалидный → маскируем при позитивном контексте «ИНН».
            val confidence = when {
                valid -> 0.95
                hasContext -> 0.8
                else -> 0.5
            }
            result.add(
                DetectedEntity(
                    type = PiiType.INN,
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
}