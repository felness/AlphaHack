package com.alfahack.pii.config

import com.alfahack.pii.config.PiiProperties.SystemConfig
import com.alfahack.pii.detection.PiiType
import com.alfahack.pii.exception.SystemNotAllowedException
import org.springframework.stereotype.Component

/**
 * Реестр систем и их правил маскирования.
 *
 * Определяет систему по заголовку запроса (X-System-Id) или «по умолчанию»,
 * проверяет доступ системы и фильтрует типы ПД по правилам системы.
 */
@Component
class SystemRegistry(
    private val properties: PiiProperties
) {

    /**
     * Определить систему по идентификатору из заголовка.
     *
     * @param systemId идентификатор системы из заголовка (может быть null)
     * @return конфигурация системы
     * @throws SystemNotAllowedException если система неизвестна или отключена
     */
    fun resolve(systemId: String?): SystemConfig {
        val id = systemId ?: DEFAULT_SYSTEM
        val config = properties.systems[id]
            ?: throw SystemNotAllowedException(id)
        if (!config.enabled) {
            throw SystemNotAllowedException(id)
        }
        return config
    }

    /**
     * Отфильтровать типы ПД по правилам системы.
     *
     * @param config конфигурация системы
     * @param detected обнаруженные типы ПД
     * @return типы ПД, которые система разрешает маскировать
     */
    fun filterTypes(config: SystemConfig, detected: Set<PiiType>): Set<PiiType> {
        val allowed = config.maskingTypes
        if (allowed.contains(ALL)) {
            return detected
        }
        val allowedTypes = allowed.mapNotNull { runCatching { PiiType.valueOf(it) }.getOrNull() }.toSet()
        return detected.intersect(allowedTypes)
    }

    companion object {
        const val DEFAULT_SYSTEM = "default"
        const val ALL = "ALL"
    }
}