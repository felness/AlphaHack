package com.alfahack.pii.detection.regex

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор имени держателя банковской карты.
 *
 * Имя латиницей (2 слова заглавными), обычно в контексте карты
 * («CARD HOLDER», «cardholder», рядом с номером карты).
 */
@Component
class CardHolderNameDetector : Detector {
    override val supportedTypes: Set<PiiType> = setOf(PiiType.CARD_HOLDER_NAME)

    private val pattern: Pattern =
        Pattern.compile(
            CARD_HOLDER_REGEX,
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CHARACTER_CLASS,
        )

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            // Span = только имя (группа 1), служебное слово «card holder» не маскируется
            result.add(
                DetectedEntity(
                    type = PiiType.CARD_HOLDER_NAME,
                    start = matcher.start(1),
                    end = matcher.end(1),
                    confidence = 0.85,
                    source = DetectorSource.REGEX,
                    validated = true,
                ),
            )
        }

        return result
    }

    companion object {
        // Служебные слова, после которых идёт имя держателя (рус. и англ.)
        private const val TRIGGER =
            "card\\s*holder|cardholder|держател\\p{IsCyrillic}+(?:\\s+карты)?|имя\\s+на\\s+карте"

        // Имя латиницей: 2-3 слова (\p{IsLatin} — Unicode-aware, только латиница)
        private const val LATIN_NAME = "\\p{IsLatin}{2,}(?:\\s+\\p{IsLatin}{2,}){1,2}"

        // Группа 1 = имя; служебное слово в span не попадает и остаётся читаемым
        private const val CARD_HOLDER_REGEX = "\\b(?:$TRIGGER)[\\s:]+($LATIN_NAME)"
    }
}
