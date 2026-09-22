package com.alfahack.pii.store

/**
 * Хранилище соответствий «маскирование → демаскирование» по payload_id.
 */
interface CorrelationStore {

    /**
     * Сохранить запись соответствия.
     * Идемпотентно: если запись уже существует, возвращает существующую.
     *
     * @return true, если запись создана; false, если уже существовала
     */
    fun save(payloadId: String, record: CorrelationRecord): Boolean

    /**
     * Получить запись соответствия по payload_id.
     *
     * @return запись или null, если не найдена
     */
    fun find(payloadId: String): CorrelationRecord?

    /**
     * Удалить запись соответствия.
     */
    fun delete(payloadId: String)
}