package com.alfahack.pii.store

import com.alfahack.pii.config.PiiProperties
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Redis-реализация хранилища соответствий.
 *
 * Ключ: pii:corr:{payload_id}
 * Значение: JSON (original, mask, spans, createdAt)
 * TTL: настраиваемый (по умолчанию 24 часа)
 *
 * Обёрнуто в circuit breaker (Resilience4j): при недоступности Redis
 * запросы быстро возвращают ошибку (503), не блокируя поток.
 */
@Component
@ConditionalOnProperty(name = ["pii.store.type"], havingValue = "redis")
class RedisCorrelationStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val properties: PiiProperties,
) : CorrelationStore {
    @CircuitBreaker(name = "redisStore")
    override fun save(
        payloadId: String,
        record: CorrelationRecord,
    ): Boolean {
        val key = key(payloadId)
        val json = objectMapper.writeValueAsString(record)

        // Идемпотентность: SETNX — создаёт только если ключа нет
        val created = redisTemplate.opsForValue().setIfAbsent(key, json)
        if (created == true) {
            redisTemplate.expire(key, Duration.ofSeconds(properties.correlationTtlSeconds))
        }
        return created == true
    }

    @CircuitBreaker(name = "redisStore")
    override fun find(payloadId: String): CorrelationRecord? {
        val json = redisTemplate.opsForValue().get(key(payloadId)) ?: return null
        return objectMapper.readValue(json, CorrelationRecord::class.java)
    }

    @CircuitBreaker(name = "redisStore")
    override fun delete(payloadId: String) {
        redisTemplate.delete(key(payloadId))
    }

    private fun key(payloadId: String): String = "pii:corr:$payloadId"
}
