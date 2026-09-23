package com.alfahack.pii.detection

/**
 * Интерфейс recognizer'а (детектора) ПД.
 *
 * Абстракция для подключения разных стратегий детекции:
 * regex, ML/NER, словари. Новый тип ПД = новый Detector.
 */
interface Detector {
    /**
     * Типы ПД, которые поддерживает этот детектор.
     */
    val supportedTypes: Set<PiiType>

    /**
     * Обнаружить ПД в тексте.
     *
     * @param text исходный текст (без нормализации)
     * @return список найденных сущностей
     */
    fun detect(text: String): List<DetectedEntity>
}
