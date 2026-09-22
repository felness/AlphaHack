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
 * Параметризованный тест негативных случаев.
 *
 * Проверяет, что невалидные форматы и негативный контекст НЕ маскируются
 * (результат равен входу). Читает CSV-файл `negative-cases.csv`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = ["pii.store.type=in-memory"])
class NegativeMaskingParameterizedTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @ParameterizedTest(name = "[{index}] {3}: {0}")
    @CsvFileSource(resources = ["/negative-cases.csv"], numLinesToSkip = 1, delimiter = ';')
    fun `invalid or negative context is not masked`(
        payload: String,
        payloadId: String,
        expectedResult: String,
        category: String
    ) {
        val response = mockMvc.perform(
            post("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"payload":"$payload","payload_id":"$payloadId"}""")
        )
            .andReturn()

        assertEquals(200, response.response.status, "Request failed for [$category]: $payload")
        val actual = extractResult(response.response.contentAsString)
        assertEquals(expectedResult, actual, "Expected no masking for [$category]: $payload")
    }

    private fun extractResult(json: String): String {
        val regex = Regex("\"result\"\\s*:\\s*\"([^\"]*)\"")
        return regex.find(json)?.groupValues?.get(1) ?: ""
    }
}