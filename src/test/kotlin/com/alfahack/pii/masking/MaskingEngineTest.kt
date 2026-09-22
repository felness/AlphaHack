package com.alfahack.pii.masking

import com.alfahack.pii.detection.DetectedEntity
import com.alfahack.pii.detection.DetectorSource
import com.alfahack.pii.detection.PiiType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class MaskingEngineTest {

    private val engine = MaskingEngine()

    @Test
    fun `mask preserves length and separators`() {
        val text = "паспорт 4509 123456"
        val entity = DetectedEntity(
            type = PiiType.PASSPORT_SERIES_NUMBER,
            start = 8,
            end = 19,
            confidence = 0.9,
            source = DetectorSource.REGEX,
            validated = true
        )

        val result = engine.mask(text, listOf(entity))

        // "4509 123456" (10 символов) → "**** ******" (пробел сохранён)
        assertEquals("паспорт **** ******", result.maskedText)
        assertEquals(1, result.spans.size)
        assertEquals("4509 123456", result.spans[0].original)
    }

    @Test
    fun `mask replaces letters and digits but keeps separators`() {
        val text = "email ivanov@mail.ru"
        val entity = DetectedEntity(
            type = PiiType.EMAIL,
            start = 6,
            end = 20,
            confidence = 0.95,
            source = DetectorSource.REGEX,
            validated = true
        )

        val result = engine.mask(text, listOf(entity))

        // "ivanov@mail.ru" → "******@****.**" (@ и . сохранены)
        assertEquals("email ******@****.**", result.maskedText)
    }

    @Test
    fun `mask multiple entities`() {
        val text = "Иванов Иван, ivanov@mail.ru"
        val name = DetectedEntity(
            type = PiiType.FULL_NAME,
            start = 0,
            end = 11,
            confidence = 0.9,
            source = DetectorSource.REGEX,
            validated = true
        )
        val email = DetectedEntity(
            type = PiiType.EMAIL,
            start = 13,
            end = 27,
            confidence = 0.95,
            source = DetectorSource.REGEX,
            validated = true
        )

        val result = engine.mask(text, listOf(name, email))

        assertEquals("****** ****, ******@****.**", result.maskedText)
        assertEquals(2, result.spans.size)
    }

    @Test
    fun `synthetic format generates random chars preserving length`() {
        val text = "паспорт 4509 123456"
        val entity = DetectedEntity(
            type = PiiType.PASSPORT_SERIES_NUMBER,
            start = 8,
            end = 19,
            confidence = 0.9,
            source = DetectorSource.REGEX,
            validated = true
        )

        val result = engine.mask(text, listOf(entity), MaskFormat.SYNTHETIC)

        // Длина сохранена, разделитель (пробел) сохранён, но символы заменены на случайные
        assertEquals(19, result.maskedText.length)
        assertEquals("паспорт ", result.maskedText.substring(0, 8))
        assertEquals(' ', result.maskedText[12])
        // Синтетические данные не равны оригиналу
        assertNotEquals("4509 123456", result.maskedText.substring(8, 19))
    }
}