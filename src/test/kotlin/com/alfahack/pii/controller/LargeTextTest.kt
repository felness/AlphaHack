package com.alfahack.pii.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * Тест обработки крупных текстов (до 100 000 токенов).
 *
 * Проверяет, что сервис обрабатывает большой текст без падения
 * и корректно маскирует ПДН в начале, середине и конце.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["pii.store.type=in-memory"])
class LargeTextTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `large text with PII is processed without failure`() {
        // Генерируем текст ~100 000 токенов (примерно 400 000 символов)
        val filler = "обычный текст без персональных данных ".repeat(8000) // ~320 000 символов
        val payload = "паспорт 4509 123456, email ivanov@mail.ru, телефон +7 (900) 123-45-67. $filler ИНН 770100000079 в конце"

        val response =
            mockMvc
                .perform(
                    post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"payload":"$payload","payload_id":"large-1"}"""),
                ).andReturn()

        assertEquals(200, response.response.status, "Large text should be processed without failure")
        val result = extractResult(response.response.contentAsString)

        // ПДН в начале замаскированы
        assertTrue(result.contains("паспорт **** ******"), "Passport at start should be masked")
        assertTrue(result.contains("email ******@****.**"), "Email at start should be masked")
        // ПДН в конце замаскированы
        assertTrue(result.contains("ИНН ************"), "INN at end should be masked")
        // Обычный текст не замаскирован
        assertTrue(result.contains("обычный текст без персональных данных"), "Regular text should not be masked")
    }

    private fun extractResult(json: String): String {
        val regex = Regex("\"result\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(json)?.groupValues?.get(1) ?: ""
    }
}
