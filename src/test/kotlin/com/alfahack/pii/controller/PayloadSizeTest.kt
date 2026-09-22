package com.alfahack.pii.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * Тест ограничения размера payload (защита от DoS).
 *
 * Проверяет, что payload больше maxPayloadSize возвращает 400.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "pii.store.type=in-memory",
        "pii.max-payload-size=100"
    ]
)
class PayloadSizeTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `payload within limit is accepted`() {
        mockMvc.perform(
            post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"payload":"паспорт 4509 123456","payload_id":"size-1"}""")
        )
            .andReturn()
            .let { assertEquals(200, it.response.status) }
    }

    @Test
    fun `payload exceeding limit returns 400`() {
        // Payload длиной 150 символов (> 100)
        val longPayload = "x".repeat(150)
        mockMvc.perform(
            post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"payload":"$longPayload","payload_id":"size-2"}""")
        )
            .andReturn()
            .let { assertEquals(400, it.response.status) }
    }
}