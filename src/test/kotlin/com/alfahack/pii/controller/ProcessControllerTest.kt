package com.alfahack.pii.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Интеграционный тест контракта /process.
 *
 * Использует in-memory хранилище (не зависит от Redis).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["pii.store.type=in-memory"])
class ProcessControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `masking then unmasking with same payload_id`() {
        val payload = "Клиент Иванов Иван Иванович, email ivanov@mail.ru, телефон +7 (900) 123-45-67"
        val payloadId = "test-1"

        // Шаг 1: маскирование (первый запрос с новым payload_id)
        val maskResponse =
            mockMvc
                .perform(
                    post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"payload":"$payload","payload_id":"$payloadId"}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.result").isNotEmpty)
                .andReturn()

        val mask = jsonPath(maskResponse.response.contentAsString, "$.result")

        // Шаг 2: демаскирование (второй запрос с тем же payload_id)
        mockMvc
            .perform(
                post("/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"payload":"$mask","payload_id":"$payloadId"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value(payload))
    }

    @Test
    fun `masking hides email and phone`() {
        val payload = "email ivanov@mail.ru, телефон +7 (900) 123-45-67"
        val payloadId = "test-2"

        val response =
            mockMvc
                .perform(
                    post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"payload":"$payload","payload_id":"$payloadId"}"""),
                ).andExpect(status().isOk)
                .andReturn()

        val mask = jsonPath(response.response.contentAsString, "$.result")

        // Email и телефон должны быть замаскированы (не содержать оригинальных значений)
        org.junit.jupiter.api.Assertions
            .assertFalse(mask.contains("ivanov@mail.ru"))
        org.junit.jupiter.api.Assertions
            .assertFalse(mask.contains("900"))
    }

    @Test
    fun `invalid request returns 400`() {
        mockMvc
            .perform(
                post("/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"payload":"test"}"""),
            ).andExpect(status().isBadRequest)
    }

    private fun jsonPath(
        json: String,
        path: String,
    ): String {
        // Простое извлечение поля result из JSON
        val regex = Regex("\"result\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(json)?.groupValues?.get(1) ?: ""
    }
}
