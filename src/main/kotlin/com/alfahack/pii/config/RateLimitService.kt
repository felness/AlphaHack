package com.alfahack.pii.config

import com.alfahack.pii.exception.RateLimitException
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Rate limiter на основе Bucket4j.
 *
 * Ограничивает количество запросов в секунду (RPS) на инстанс.
 * При превышении лимита выбрасывает RateLimitException (429).
 */
@Component
class RateLimitService(
    private val properties: PiiProperties
) {

    private val bucket: Bucket = Bucket.builder()
        .addLimit(
            Bandwidth.builder()
                .capacity(properties.rateLimitRps.toLong())
                .refillGreedy(properties.rateLimitRps.toLong(), Duration.ofSeconds(1))
                .build()
        )
        .build()

    /**
     * Попытаться потребить один токен.
     *
     * @throws RateLimitException если лимит превышен
     */
    fun tryConsume() {
        if (!bucket.tryConsume(1)) {
            val retryAfter = bucket.estimateAbilityToConsume(1).nanosToWaitForRefill / 1_000_000_000
            throw RateLimitException(retryAfter.coerceAtLeast(1))
        }
    }
}