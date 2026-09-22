package com.alfahack.pii.detection.regex

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.Detector
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Детектор ПИН-кода банковской карты.
 *
 * PIN: 4 цифры, обычно в контексте «ПИН», «пин», «pin».
 * Маскируется при контексте «пин» ИЛИ при наличии номера карты в тексте
 * (контекстное маскирование: PIN + карта).
 * Без контекста 4 цифры — слишком слабый сигнал (не маскируется).
 */
@Component
class PinDetector : Detector {

    override val supportedTypes: Set<PiiType> = setOf(PiiType.PIN)

    private val pattern: Pattern = Pattern.compile(PIN_REGEX, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CHARACTER_CLASS)

    override fun detect(text: String): List<DetectedEntity> {
        val matcher = pattern.matcher(text)
        val result = mutableListOf<DetectedEntity>()

        while (matcher.find()) {
            // Span = только 4 цифры (группа 1), служебное слово «пин»/«код» не маскируется
            result.add(
                DetectedEntity(
                    type = PiiType.PIN,
                    start = matcher.start(1),
                    end = matcher.end(1),
                    confidence = 0.9,
                    source = DetectorSource.REGEX,
                    validated = true
                )
            )
        }

        return result
    }

    companion object {
        // PIN: слово «пин»/«pin» (+ опционально «код» с дефисом) + 4 цифры — группа 1 = цифры
        private const val PIN_REGEX =
            "\\b(?:пин|pin)\\s*(?:-?\\s*код\\s*)?[:\\s-]?\\s*(\\d{4})\\b"
    }
}