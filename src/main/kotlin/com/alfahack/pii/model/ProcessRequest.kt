package com.alfahack.pii.model

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotBlank

/**
 * Запрос на обработку строки (маскирование или демаскирование).
 *
 * @param payload строка для обработки
 * @param payloadId идентификатор корреляции пары «маскирование → демаскирование»
 */
data class ProcessRequest(
    @field:NotBlank
    @JsonProperty("payload")
    val payload: String?,

    @field:NotBlank
    @JsonProperty("payload_id")
    val payloadId: String?
)