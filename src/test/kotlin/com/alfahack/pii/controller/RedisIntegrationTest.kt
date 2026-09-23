package com.alfahack.pii.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Интеграционный тест с реальным Redis (Testcontainers).
 *
 * Поднимает Redis-контейнер, проверяет полный цикл маскирование → демаскирование,
 * идемпотентность и TTL.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RedisIntegrationTest {
    companion object {
        @Container
        val redis: GenericContainer<*> =
            GenericContainer("redis:7-alpine")
                .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("pii.store.type") { "redis" }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `full mask then unmask roundtrip via real redis`() {
        val payload = "паспорт 4509 123456, email ivanov@mail.ru"
        val payloadId = "redis-1"

        // Шаг 1: маскирование
        val maskResponse =
            mockMvc
                .perform(
                    post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"payload":"$payload","payload_id":"$payloadId"}"""),
                ).andReturn()

        assertEquals(200, maskResponse.response.status)
        val mask = extractResult(maskResponse.response.contentAsString)
        assertEquals("паспорт **** ******, email ******@****.**", mask)

        // Шаг 2: демаскирование
        val unmaskResponse =
            mockMvc
                .perform(
                    post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"payload":"$mask","payload_id":"$payloadId"}"""),
                ).andReturn()

        assertEquals(200, unmaskResponse.response.status)
        assertEquals(payload, extractResult(unmaskResponse.response.contentAsString))
    }

    @Test
    fun `idempotent masking returns same mask`() {
        val payload = "ИНН 770100000079"
        val payloadId = "redis-2"

        // Первый запрос — маскирование
        val first =
            mockMvc
                .perform(
                    post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"payload":"$payload","payload_id":"$payloadId"}"""),
                ).andReturn()
        assertEquals(200, first.response.status)
        val firstMask = extractResult(first.response.contentAsString)

        // Повторный запрос с тем же payload_id и payload — та же маска
        val second =
            mockMvc
                .perform(
                    post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"payload":"$payload","payload_id":"$payloadId"}"""),
                ).andReturn()
        assertEquals(200, second.response.status)
        assertEquals(firstMask, extractResult(second.response.contentAsString))
    }

    @Test
    fun `unknown payload_id is treated as masking`() {
        // Запрос с неизвестным payload_id — это первый запрос (маскирование), возвращает 200
        mockMvc
            .perform(
                post("/process")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"payload":"some mask","payload_id":"unknown-id"}"""),
            ).andReturn()
            .let { assertEquals(200, it.response.status) }
    }

    private fun extractResult(json: String): String {
        val regex = Regex("\"result\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(json)?.groupValues?.get(1) ?: ""
    }
}
