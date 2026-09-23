package com.alfahack.pii.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Ответ на обработку строки.
 *
 * @param result результат обработки (маска на прямом шаге, исходная строка на обратном)
 */
data class ProcessResponse(
    @JsonProperty("result")
    val result: String,
)
