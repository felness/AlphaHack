package com.alfahack.pii.detection

/**
 * Найденная сущность ПД.
 *
 * @param type тип ПД
 * @param start начальная позиция в тексте (включительно)
 * @param end конечная позиция в тексте (исключительно)
 * @param confidence уверенность [0..1]
 * @param source источник детекции
 * @param validated прошла ли checksum-валидация
 */
data class DetectedEntity(
    val type: PiiType,
    val start: Int,
    val end: Int,
    val confidence: Double,
    val source: DetectorSource,
    val validated: Boolean = false
)

/**
 * Источник детекции.
 */
enum class DetectorSource {
    REGEX,
    ML,
    DICTIONARY,
    CONTEXT
}