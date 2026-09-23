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
 * Тест доступа систем (403).
 *
 * Проверяет, что неизвестная система через заголовок X-System-Id возвращает 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["pii.store.type=in-memory"])
class SystemAccessTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `unknown system returns 403`() {
        mockMvc
            .perform(
                post("/process")
                    .header("X-System-Id", "unknown-system")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"payload":"паспорт 4509 123456","payload_id":"sys-1"}"""),
            ).andReturn()
            .let { assertEquals(403, it.response.status) }
    }

    @Test
    fun `default system works without header`() {
        mockMvc
            .perform(
                post("/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"payload":"паспорт 4509 123456","payload_id":"sys-2"}"""),
            ).andReturn()
            .let { assertEquals(200, it.response.status) }
    }
}
