package com.alfahack.pii.controller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * Параметризованный тест полной матрицы маскирования → демаскирования.
 *
 * Читает CSV-файл `masking-cases.csv` и для каждой строки:
 * 1. POST /process {payload, payload_id} → ожидается expected_mask
 * 2. POST /process {payload: expected_mask, payload_id} → ожидается expected_unmask
 *
 * Использует in-memory хранилище (не зависит от Redis).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["pii.store.type=in-memory"])
class MaskingParameterizedTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @ParameterizedTest(name = "[{index}] {4}: {0}")
    @CsvFileSource(resources = ["/masking-cases.csv"], numLinesToSkip = 1, delimiter = ';')
    fun `mask then unmask roundtrip`(
        payload: String,
        payloadId: String,
        expectedMask: String,
        expectedUnmask: String,
        category: String,
    ) {
        // Шаг 1: маскирование
        val maskResponse =
            mockMvc
                .perform(
                    post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"payload":"$payload","payload_id":"$payloadId"}"""),
                ).andReturn()

        assertEquals(200, maskResponse.response.status, "Masking failed for [$category]: $payload")
        val actualMask = extractResult(maskResponse.response.contentAsString)
        assertEquals(expectedMask, actualMask, "Mask mismatch for [$category]: $payload")

        // Шаг 2: демаскирование
        val unmaskResponse =
            mockMvc
                .perform(
                    post("/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"payload":"$actualMask","payload_id":"$payloadId"}"""),
                ).andReturn()

        assertEquals(200, unmaskResponse.response.status, "Unmasking failed for [$category]: $actualMask")
        val actualUnmask = extractResult(unmaskResponse.response.contentAsString)
        assertEquals(expectedUnmask, actualUnmask, "Unmask mismatch for [$category]: $actualMask")
    }

    private fun extractResult(json: String): String {
        val regex = Regex("\"result\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(json)?.groupValues?.get(1) ?: ""
    }
}
