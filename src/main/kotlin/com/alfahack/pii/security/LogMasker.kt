package com.alfahack.pii.security

import org.springframework.stereotype.Component

/**
 * Маскирование ПД в логах.
 *
 * Критичное требование ТЗ: ПД не должны попадать в логи.
 * Логируются только типы ПД и агрегаты, никогда — значения.
 */
@Component
class LogMasker {

    /**
     * Замаскировать payload_id (хеш), чтобы не логировать возможные ПД.
     */
    fun maskPayloadId(payloadId: String): String {
        return "id:${payloadId.hashCode().toUInt().toString(16)}"
    }

    /**
     * Сформировать безопасное сообщение о найденных типах ПД (без значений).
     */
    fun typesSummary(types: Collection<Any>): String {
        return types.joinToString(", ")
    }
}