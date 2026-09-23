package com.alfahack.pii.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * Тест circuit breaker (503) при недоступности Redis.
 *
 * Мокает StringRedisTemplate, чтобы он выбрасывал RedisConnectionFailureException.
 * После N ошибок circuit breaker открывается и возвращает 503.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "pii.store.type=redis",
        "resilience4j.circuitbreaker.instances.redisStore.minimum-number-of-calls=2",
        "resilience4j.circuitbreaker.instances.redisStore.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.redisStore.sliding-window-size=2",
    ],
)
class CircuitBreakerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var redisTemplate: StringRedisTemplate

    @Test
    fun `redis unavailable returns 503 after circuit breaker opens`() {
        // Мокаем ValueOperations, чтобы get выбрасывал RedisConnectionFailureException
        @Suppress("UNCHECKED_CAST")
        val valueOps = Mockito.mock(ValueOperations::class.java) as ValueOperations<String, String>
        `when`(redisTemplate.opsForValue()).thenReturn(valueOps)
        `when`(valueOps.get(anyString())).thenThrow(RedisConnectionFailureException("Redis down"))

        // Первые вызовы — ошибка Redis, затем circuit breaker открывается → 503
        repeat(3) {
            val response =
                mockMvc
                    .perform(
                        post("/process")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"payload":"паспорт 4509 123456","payload_id":"cb-$it"}"""),
                    ).andReturn()
            // После открытия circuit breaker — 503
            assertEquals(503, response.response.status)
        }
    }
}
