package com.alfahack.pii.detection.dictionary

import com.alfahack.pii.detection.ContextRules
import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор адреса (словарный).
 *
 * Находит адресные конструкции: «г. Москва, ул. Тверская, д. 1, кв. 5».
 * Маскируется при позитивном контексте («адрес», «прописка»),
 * но НЕ маскируется при негативном («отделение Банка», «филиал»).
 */
@Component
class AddressDetector(
    private val contextRules: ContextRules,
) : Detector {
    override val supportedTypes: Set<PiiType> = setOf(PiiType.ADDRESS)

    private val pattern: Pattern = Pattern.compile(ADDRESS_REGEX, Pattern.UNICODE_CHARACTER_CLASS)

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            // Адрес маскируется при позитивном контексте и отсутствии негативного
            if (contextRules.hasPositiveContext(text, start, end) &&
                !contextRules.hasNegativeContext(text, start, end)
            ) {
                result.add(
                    DetectedEntity(
                        type = PiiType.ADDRESS,
                        start = start,
                        end = end,
                        confidence = 0.85,
                        source = DetectorSource.DICTIONARY,
                        validated = true,
                    ),
                )
            }
        }

        return result
    }

    companion object {
        // Адрес: г. Город, ул. Улица, д. N, кв. N
        private const val ADDRESS_REGEX =
            "\\bг\\.\\s*[А-ЯЁ][а-яё-]+(?:,\\s*ул\\.\\s*[А-ЯЁ][а-яё-]+)?(?:,\\s*д\\.\\s*\\d+)?(?:,\\s*кв\\.\\s*\\d+)?\\b"
    }
}
