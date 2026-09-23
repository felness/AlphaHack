package com.alfahack.pii.unmasking

import com.alfahack.pii.detection.PiiType
import com.alfahack.pii.store.MaskedSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UnmaskingEngineTest {
    private val engine = UnmaskingEngine()

    @Test
    fun `unmask restores original from mask`() {
        val mask = "паспорт **** ******"
        val spans =
            listOf(
                MaskedSpan(
                    type = PiiType.PASSPORT_SERIES_NUMBER,
                    start = 8,
                    end = 19,
                    original = "4509 123456",
                ),
            )

        val result = engine.unmask(mask, spans)

        assertEquals("паспорт 4509 123456", result)
    }

    @Test
    fun `unmask multiple spans`() {
        val mask = "****** ****, ******@****.**"
        val spans =
            listOf(
                MaskedSpan(type = PiiType.FULL_NAME, start = 0, end = 11, original = "Иванов Иван"),
                MaskedSpan(type = PiiType.EMAIL, start = 13, end = 27, original = "ivanov@mail.ru"),
            )

        val result = engine.unmask(mask, spans)

        assertEquals("Иванов Иван, ivanov@mail.ru", result)
    }
}
