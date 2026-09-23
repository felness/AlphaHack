package com.alfahack.pii.metrics

import com.alfahack.pii.detection.PiiType
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Метрики модуля ПД (Micrometer + Prometheus).
 *
 * Метрики содержат только агрегированные значения (latency, RPS, количество),
 * БЕЗ значений ПД (требование безопасности).
 */
@Component
class MetricsService(
    private val meterRegistry: MeterRegistry,
) {
    private val requestsTotal: Counter =
        Counter
            .builder("pii_requests_total")
            .description("Total requests")
            .register(meterRegistry)

    private val maskingTotal: Counter =
        Counter
            .builder("pii_requests_masking_total")
            .description("Masking requests")
            .register(meterRegistry)

    private val unmaskingTotal: Counter =
        Counter
            .builder("pii_requests_unmasking_total")
            .description("Unmasking requests")
            .register(meterRegistry)

    private val errorsTotal: Counter =
        Counter
            .builder("pii_requests_errors_total")
            .description("Request errors")
            .register(meterRegistry)

    private val latency: Timer =
        Timer
            .builder("pii_latency_seconds")
            .description("Request latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            // Публикуем histogram buckets для точных перцентилей по времени
            // (histogram_quantile в Prometheus/Grafana)
            .publishPercentileHistogram(true)
            .register(meterRegistry)

    private val maskingLatency: Timer =
        Timer
            .builder("pii_latency_masking_seconds")
            .description("Masking latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram(true)
            .register(meterRegistry)

    private val unmaskingLatency: Timer =
        Timer
            .builder("pii_latency_unmasking_seconds")
            .description("Unmasking latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram(true)
            .register(meterRegistry)

    private val rateLimitedTotal: Counter =
        Counter
            .builder("pii_rate_limited_total")
            .description("Rate limited requests (429)")
            .register(meterRegistry)

    // Текущее значение RPS (запросов в секунду) — обновляется периодически
    private val rpsValue = AtomicLong(0)
    private val tpsValue = AtomicLong(0)

    init {
        Gauge
            .builder("pii_rps", rpsValue) { it.get().toDouble() }
            .description("Requests per second")
            .register(meterRegistry)
        Gauge
            .builder("pii_tps", tpsValue) { it.get().toDouble() }
            .description("Tokens per second")
            .register(meterRegistry)
    }

    fun recordRequest() {
        requestsTotal.increment()
    }

    fun recordMasking() {
        maskingTotal.increment()
    }

    fun recordUnmasking() {
        unmaskingTotal.increment()
    }

    fun recordError() {
        errorsTotal.increment()
    }

    fun recordRateLimited() {
        rateLimitedTotal.increment()
    }

    fun recordLatency(durationNanos: Long) {
        latency.record(durationNanos, TimeUnit.NANOSECONDS)
    }

    fun recordMaskingLatency(durationNanos: Long) {
        maskingLatency.record(durationNanos, TimeUnit.NANOSECONDS)
    }

    fun recordUnmaskingLatency(durationNanos: Long) {
        unmaskingLatency.record(durationNanos, TimeUnit.NANOSECONDS)
    }

    fun recordEntityDetected(type: PiiType) {
        Counter
            .builder("pii_entities_detected_total")
            .description("Detected entities by type")
            .tag("type", type.name)
            .register(meterRegistry)
            .increment()
    }

    /**
     * Обновить текущее значение RPS (запросов в секунду).
     */
    fun updateRps(value: Long) {
        rpsValue.set(value)
    }

    /**
     * Обновить текущее значение TPS (токенов в секунду).
     */
    fun updateTps(value: Long) {
        tpsValue.set(value)
    }
}
