package com.alfahack.pii.controller

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * Тест безопасности: ПД не должны попадать в логи.
 *
 * Проверяет, что при маскировании/демаскировании в логах нет значений ПД
 * (только типы и агрегаты).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["pii.store.type=in-memory"])
@ExtendWith(OutputCaptureExtension::class)
class SecurityLogTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `masking does not leak PII values to logs`(output: CapturedOutput) {
        val payload = "паспорт 4509 123456, email ivanov@mail.ru, телефон +7 (900) 123-45-67"
        val payloadId = "sec-1"

        mockMvc
            .perform(
                post("/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"payload":"$payload","payload_id":"$payloadId"}"""),
            ).andReturn()

        // В логах не должно быть значений ПД
        assertFalse(output.all.contains("4509 123456"), "Passport number leaked to logs")
        assertFalse(output.all.contains("ivanov@mail.ru"), "Email leaked to logs")
        assertFalse(output.all.contains("900"), "Phone leaked to logs")
    }

    @Test
    fun `unmasking does not leak PII values to logs`(output: CapturedOutput) {
        val payload = "паспорт 4509 123456"
        val payloadId = "sec-2"

        // Маскирование
        val maskResponse =
            mockMvc
                .perform(
                    post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"payload":"$payload","payload_id":"$payloadId"}"""),
                ).andReturn()
        val mask = extractResult(maskResponse.response.contentAsString)

        // Демаскирование
        mockMvc
            .perform(
                post("/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"payload":"$mask","payload_id":"$payloadId"}"""),
            ).andReturn()

        // В логах не должно быть значений ПД
        assertFalse(output.all.contains("4509 123456"), "Passport number leaked to logs")
    }

    private fun extractResult(json: String): String {
        val regex = Regex("\"result\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(json)?.groupValues?.get(1) ?: ""
    }
}
