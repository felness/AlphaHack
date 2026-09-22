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
 * Тест rate limiting (429).
 *
 * Задаёт низкий лимит RPS (2) и проверяет, что при превышении возвращается 429
 * с заголовком Retry-After.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "pii.store.type=in-memory",
        "pii.rate-limit-rps=2"
    ]
)
class RateLimitTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `rate limit returns 429 with retry-after`() {
        val payload = "паспорт 4509 123456"
        val payloadId = "rate-1"

        // Первые 2 запроса — в пределах лимита (200)
        repeat(2) {
            val response = mockMvc.perform(
                post("/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"payload":"$payload","payload_id":"$payloadId"}""")
            ).andReturn()
            assertEquals(200, response.response.status)
        }

        // Третий запрос — превышение лимита (429)
        val limited = mockMvc.perform(
            post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"payload":"$payload","payload_id":"$payloadId"}""")
        ).andReturn()

        assertEquals(429, limited.response.status)
        assertEquals("1", limited.response.getHeader("Retry-After"))
    }
}