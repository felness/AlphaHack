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

    /** Название с заглавной, допускает дефис: «Москва», «Санкт-Петербург». */
    private val properName = "\\p{Lu}\\p{IsCyrillic}+(?:-\\p{IsCyrillic}+)*"

    private val index = "\\d{6}"
    private val city = "(?:${CITY_TYPES.joinToString("|")})\\s*$properName"
    private val street = "(?:${STREET_TYPES.joinToString("|")})\\s*$properName"
    private val house = "(?:д\\.|дом)\\s*\\d+\\p{IsCyrillic}?"
    private val flat = "(?:кв\\.|квартира)\\s*\\d+"

    /** Разделитель компонентов адреса: запятая и/или пробелы. */
    private val separator = ",?\\s*"

    /**
     * Адрес: необязательный индекс, город, далее необязательные улица, дом, квартира.
     * Собирается из компонентов, чтобы правила можно было читать и дополнять.
     */
    private val pattern: Pattern =
        Pattern.compile(
            "\\b(?:$index$separator)?$city" +
                "(?:$separator$street)?(?:$separator$house)?(?:$separator$flat)?",
            Pattern.UNICODE_CHARACTER_CLASS,
        )

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
        /** Обозначения населённого пункта. Длинные раньше коротких. */
        private val CITY_TYPES = listOf("город", "гор\\.", "г\\.", "пос\\.", "посёлок", "поселок", "село", "деревня")

        /** Обозначения улицы и прочих проездов. */
        private val STREET_TYPES =
            listOf(
                "улица",
                "ул\\.",
                "проспект",
                "пр-т",
                "просп\\.",
                "переулок",
                "пер\\.",
                "шоссе",
                "ш\\.",
                "бульвар",
                "б-р",
                "набережная",
                "наб\\.",
                "проезд",
            )
    }
}
