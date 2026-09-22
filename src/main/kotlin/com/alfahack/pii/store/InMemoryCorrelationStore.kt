package com.alfahack.pii.store

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory реализация хранилища соответствий.
 *
 * Используется как заглушка до подключения Redis.
 * Не масштабируется на несколько инстансов, данные теряются при рестарте.
 */
@Component
@ConditionalOnProperty(name = ["pii.store.type"], havingValue = "in-memory", matchIfMissing = true)
class InMemoryCorrelationStore : CorrelationStore {

    private val store = ConcurrentHashMap<String, CorrelationRecord>()

    override fun save(payloadId: String, record: CorrelationRecord): Boolean {
        return store.putIfAbsent(payloadId, record) == null
    }

    override fun find(payloadId: String): CorrelationRecord? {
        return store[payloadId]
    }

    override fun delete(payloadId: String) {
        store.remove(payloadId)
    }
}