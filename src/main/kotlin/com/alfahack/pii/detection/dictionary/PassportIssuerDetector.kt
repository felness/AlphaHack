package com.alfahack.pii.detection.dictionary

import com.alfahack.pii.detection.ContextRules
import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор органа, выдавшего паспорт (словарный).
 *
 * Находит «ОУФМС России по г. Москве» в контексте «выдан».
 */
@Component
class PassportIssuerDetector(
    private val contextRules: ContextRules,
) : Detector {
    override val supportedTypes: Set<PiiType> = setOf(PiiType.PASSPORT_ISSUER)

    private val pattern: Pattern = Pattern.compile(ISSUER_REGEX, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CHARACTER_CLASS)

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            // Орган выдачи маскируется при позитивном контексте («выдан»)
            if (contextRules.hasPositiveContext(text, start, end)) {
                result.add(
                    DetectedEntity(
                        type = PiiType.PASSPORT_ISSUER,
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
        // Орган выдачи: «ОУФМС России по г. Москве», «УФМС России по ...»
        private const val ISSUER_REGEX =
            "\\b(?:О?УФМС|УВМ|МВД)\\s+России(?:\\s+по\\s+[А-ЯЁ][а-яё-]+(?:\\s+области)?)?\\b"
    }
}
