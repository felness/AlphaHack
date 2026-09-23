package com.alfahack.pii.service

import com.alfahack.pii.config.PiiProperties
import com.alfahack.pii.config.PiiProperties.SystemConfig
import com.alfahack.pii.config.SystemRegistry
import com.alfahack.pii.detection.DetectionEngine
import com.alfahack.pii.exception.PayloadTooLargeException
import com.alfahack.pii.exception.SystemNotAllowedException
import com.alfahack.pii.masking.MaskFormat
import com.alfahack.pii.masking.MaskingEngine
import com.alfahack.pii.metrics.MetricsService
import com.alfahack.pii.model.ProcessRequest
import com.alfahack.pii.model.ProcessResponse
import com.alfahack.pii.security.LogMasker
import com.alfahack.pii.store.CorrelationRecord
import com.alfahack.pii.store.CorrelationStore
import com.alfahack.pii.unmasking.UnmaskingEngine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Оркестратор обработки запроса.
 *
 * Определяет направление (маскирование/демаскирование) по payload_id:
 * - первый запрос с новым payload_id → маскирование
 * - второй запрос с тем же payload_id → демаскирование
 *
 * Применяет правила системы (фильтрация типов ПД, доступ).
 */
@Service
class ProcessService(
    private val detectionEngine: DetectionEngine,
    private val maskingEngine: MaskingEngine,
    private val unmaskingEngine: UnmaskingEngine,
    private val correlationStore: CorrelationStore,
    private val systemRegistry: SystemRegistry,
    private val logMasker: LogMasker,
    private val metricsService: MetricsService,
    private val properties: PiiProperties,
) {
    private val log = LoggerFactory.getLogger(ProcessService::class.java)

    /**
     * Обработать запрос.
     *
     * @param request запрос
     * @param systemId идентификатор системы из заголовка (может быть null → default)
     */
    fun process(
        request: ProcessRequest,
        systemId: String?,
    ): ProcessResponse {
        val start = System.nanoTime()
        metricsService.recordRequest()

        try {
            // Валидация @NotBlank гарантирует, что payload и payloadId не null
            val payload = requireNotNull(request.payload) { "payload is required" }
            val payloadId = requireNotNull(request.payloadId) { "payload_id is required" }

            // Защита от DoS: ограничение размера payload
            if (payload.length > properties.maxPayloadSize) {
                throw PayloadTooLargeException(properties.maxPayloadSize)
            }

            // Определение системы и проверка доступа
            val system = systemRegistry.resolve(systemId)
            val resolvedSystemId = systemId ?: SystemRegistry.DEFAULT_SYSTEM

            val existing = correlationStore.find(payloadId)

            val response =
                when {
                    existing == null -> mask(payload, payloadId, system, resolvedSystemId)

                    // Идемпотентность: повторный запрос с тем же payload (оригинал) → вернуть сохранённую маску
                    payload == existing.original -> ProcessResponse(existing.mask)

                    // Демаскирование: пришла маска → восстановить оригинал
                    else -> unmask(payload, payloadId, existing, system, resolvedSystemId)
                }

            metricsService.recordLatency(System.nanoTime() - start)
            return response
        } catch (ex: Exception) {
            metricsService.recordError()
            metricsService.recordLatency(System.nanoTime() - start)
            throw ex
        }
    }

    /**
     * Маскирование: первый запрос с новым payload_id.
     */
    private fun mask(
        payload: String,
        payloadId: String,
        system: SystemConfig,
        systemId: String,
    ): ProcessResponse {
        val start = System.nanoTime()
        val maskedId = logMasker.maskPayloadId(payloadId)
        log.debug("Masking request, payloadId={}", maskedId)

        // Идентификация ПД
        val entities = detectionEngine.detect(payload)
        log.debug("Detected {} entities, types={}", entities.size, logMasker.typesSummary(entities.map { it.type }.distinct()))
        entities.forEach { metricsService.recordEntityDetected(it.type) }

        // Фильтрация типов ПД по правилам системы
        val allowedTypes = systemRegistry.filterTypes(system, entities.map { it.type }.toSet())
        val filtered = entities.filter { it.type in allowedTypes }
        log.debug("Filtered to {} entities by system rules", filtered.size)

        // Маскирование
        val maskFormat = runCatching { MaskFormat.valueOf(system.maskFormat) }.getOrDefault(MaskFormat.STAR)
        val maskingResult = maskingEngine.mask(payload, filtered, maskFormat)

        // Сохранение соответствия (идемпотентно), с привязкой к системе-владельцу
        val record =
            CorrelationRecord(
                original = payload,
                mask = maskingResult.maskedText,
                spans = maskingResult.spans,
                systemId = systemId,
            )
        correlationStore.save(payloadId, record)

        metricsService.recordMasking()
        metricsService.recordMaskingLatency(System.nanoTime() - start)
        return ProcessResponse(maskingResult.maskedText)
    }

    /**
     * Демаскирование: второй запрос с тем же payload_id.
     */
    private fun unmask(
        payload: String,
        payloadId: String,
        record: CorrelationRecord,
        system: SystemConfig,
        systemId: String,
    ): ProcessResponse {
        val start = System.nanoTime()
        log.debug("Unmasking request, payloadId={}", logMasker.maskPayloadId(payloadId))

        // Если демаскирование отключено для системы — запретить
        if (!system.unmaskingEnabled) {
            throw SystemNotAllowedException(payloadId)
        }

        // Демаскирование доступно только системе-владельцу записи
        if (record.systemId != systemId) {
            throw SystemNotAllowedException(payloadId)
        }

        // Восстановление исходной строки по spans
        val original = unmaskingEngine.unmask(payload, record.spans)

        metricsService.recordUnmasking()
        metricsService.recordUnmaskingLatency(System.nanoTime() - start)
        return ProcessResponse(original)
    }
}
