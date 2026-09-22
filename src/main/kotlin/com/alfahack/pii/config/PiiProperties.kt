package com.alfahack.pii.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.stereotype.Component

/**
 * Конфигурация модуля ПД (префикс pii).
 *
 * @RefreshScope позволяет перезагружать конфигурацию без рестарта
 * через POST /actuator/refresh.
 */
@Component
@RefreshScope
@ConfigurationProperties(prefix = "pii")
data class PiiProperties(
    var store: Store = Store(),
    var maxPayloadSize: Long = 1_000_000,
    var correlationTtlSeconds: Long = 86_400,
    var rateLimitRps: Int = 2_000,
    var systems: Map<String, SystemConfig> = emptyMap()
) {
    data class Store(
        var type: String = "in-memory"
    )

    data class SystemConfig(
        var enabled: Boolean = true,
        var maskingTypes: List<String> = listOf("ALL"),
        var unmaskingEnabled: Boolean = true,
        var maskFormat: String = "STAR"
    )
}