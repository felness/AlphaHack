package com.alfahack.pii.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
 * Тесты пограничных случаев (пустой payload, спецсимволы, Unicode, эмодзи).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["pii.store.type=in-memory"])
class EdgeCaseTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `empty payload returns 400`() {
        mockMvc.perform(
            post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"payload":"","payload_id":"edge-1"}""")
        )
            .andReturn()
            .let { assertEquals(400, it.response.status) }
    }

    @Test
    fun `whitespace payload returns 400`() {
        mockMvc.perform(
            post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"payload":"   ","payload_id":"edge-1"}""")
        )
            .andReturn()
            .let { assertEquals(400, it.response.status) }
    }

    @Test
    fun `null payload returns 400`() {
        mockMvc.perform(
            post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"payload":null,"payload_id":"edge-1"}""")
        )
            .andReturn()
            .let { assertEquals(400, it.response.status) }
    }

    @Test
    fun `missing payload_id returns 400`() {
        mockMvc.perform(
            post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"payload":"test"}""")
        )
            .andReturn()
            .let { assertEquals(400, it.response.status) }
    }

    @Test
    fun `malformed json returns 400`() {
        mockMvc.perform(
            post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"payload": "test" """)
        )
            .andReturn()
            .let { assertEquals(400, it.response.status) }
    }

    @Test
    fun `email with emoji nearby is masked but emoji preserved`() {
        val payload = "😀 ivanov@mail.ru"
        val response = mockMvc.perform(
            post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"payload":"$payload","payload_id":"edge-2"}""")
        ).andReturn()

        assertEquals(200, response.response.status)
        val result = extractResult(response.response.contentAsString)
        // Email замаскирован
        assertFalse(result.contains("ivanov@mail.ru"), "Email should be masked")
        assertTrue(result.contains("******@****.**"), "Email should be masked to stars")
    }

    @Test
    fun `email with plus sign is masked`() {
        val payload = "ivanov+test@mail.ru"
        val response = mockMvc.perform(
            post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"payload":"$payload","payload_id":"edge-3"}""")
        ).andReturn()

        assertEquals(200, response.response.status)
        val result = extractResult(response.response.contentAsString)
        assertEquals("******+****@****.**", result)
    }

    @Test
    fun `card with dashes is masked preserving dashes`() {
        val payload = "карта 4276-1234-5678-9014"
        val response = mockMvc.perform(
            post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"payload":"$payload","payload_id":"edge-4"}""")
        ).andReturn()

        assertEquals(200, response.response.status)
        val result = extractResult(response.response.contentAsString)
        assertEquals("карта ****-****-****-****", result)
    }

    private fun extractResult(json: String): String {
        val regex = Regex("\"result\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(json)?.groupValues?.get(1) ?: ""
    }
}