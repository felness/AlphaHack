package com.alfahack.pii.detection

import org.springframework.stereotype.Component

/**
 * Оркестратор детекторов (Analyzer).
 *
 * Объединяет результаты всех зарегистрированных Detector'ов,
 * разрешает пересечения и применяет пороги уверенности.
 */
@Component
class DetectionEngine(
    private val detectors: List<Detector>
) {

    /**
     * Обнаружить ПД в тексте.
     *
     * @param text исходный текст
     * @param minConfidence минимальная уверенность для включения результата
     * @return список найденных сущностей (без пересечений)
     */
    fun detect(text: String, minConfidence: Double = DEFAULT_MIN_CONFIDENCE): List<DetectedEntity> {
        val all = detectors
            .flatMap { detector -> detector.detect(text) }
            .filter { it.confidence >= minConfidence }
            // Отбрасываем невалидные spans (start >= end или вне границ текста)
            .filter { it.start >= 0 && it.end <= text.length && it.start < it.end }
            .sortedBy { it.start }

        return resolveOverlaps(all)
    }

    /**
     * Разрешение пересечений: при перекрытии выбирается более специфичный тип
     * или сущность с более высокой уверенностью.
     */
    private fun resolveOverlaps(entities: List<DetectedEntity>): List<DetectedEntity> {
        // Сортируем по start, затем по приоритету (более специфичный первым)
        val sorted = entities.sortedWith(
            compareBy<DetectedEntity> { it.start }
                .thenByDescending { TYPE_PRIORITY[it.type] ?: 0 }
                .thenByDescending { it.confidence }
        )

        val result = mutableListOf<DetectedEntity>()

        for (entity in sorted) {
            // Удаляем все сущности, которые перекрываются с текущей
            // (текущая приоритетнее, т.к. отсортирована первой при равном start)
            val iterator = result.iterator()
            var removed = false
            while (iterator.hasNext()) {
                val existing = iterator.next()
                if (overlaps(existing, entity)) {
                    // Если существующая приоритетнее — пропускаем текущую
                    val existingPriority = TYPE_PRIORITY[existing.type] ?: 0
                    val entityPriority = TYPE_PRIORITY[entity.type] ?: 0
                    if (existingPriority > entityPriority ||
                        (existingPriority == entityPriority && existing.confidence >= entity.confidence)
                    ) {
                        removed = true
                        break
                    }
                    iterator.remove()
                }
            }
            if (!removed) {
                result.add(entity)
            }
        }

        return result
    }

    private fun overlaps(a: DetectedEntity, b: DetectedEntity): Boolean {
        return a.start < b.end && b.start < a.end
    }

    companion object {
        const val DEFAULT_MIN_CONFIDENCE = 0.7

        /**
         * Приоритет типов (от более специфичного к менее).
         * Используется при разрешении пересечений.
         */
        private val TYPE_PRIORITY = mapOf(
            PiiType.CVV to 100,
            PiiType.PIN to 90,
            PiiType.CARD_NUMBER to 80,
            PiiType.PASSPORT_SERIES_NUMBER to 70,
            PiiType.INN to 60,
            PiiType.PHONE to 50,
            PiiType.EMAIL to 40,
            PiiType.DATE_OF_BIRTH to 30,
            PiiType.FULL_NAME to 20,
            PiiType.ADDRESS to 10
        )
    }
}