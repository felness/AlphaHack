package com.alfahack.pii.detection

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Тесты checksum-валидации (ИНН, Luhn).
 */
class ChecksumValidatorTest {

    private val validator = ChecksumValidator()

    @Test
    fun `valid 10-digit inn passes`() {
        assertTrue(validator.isValidInn("7701000001"))
    }

    @Test
    fun `valid 12-digit inn passes`() {
        assertTrue(validator.isValidInn("770100000079"))
    }

    @Test
    fun `invalid inn fails`() {
        assertFalse(validator.isValidInn("770123456789"))
        assertFalse(validator.isValidInn("1234567890"))
    }

    @Test
    fun `wrong length inn fails`() {
        assertFalse(validator.isValidInn("77010000007")) // 11 цифр
        assertFalse(validator.isValidInn("7701000000790")) // 13 цифр
    }

    @Test
    fun `valid luhn card passes`() {
        assertTrue(validator.isValidLuhn("4276123456789014"))
        assertTrue(validator.isValidLuhn("4111111111111111"))
        assertTrue(validator.isValidLuhn("4242424242424242"))
    }

    @Test
    fun `invalid luhn card fails`() {
        assertFalse(validator.isValidLuhn("4276123456789012"))
        assertFalse(validator.isValidLuhn("1234567890123456"))
    }

    @Test
    fun `too short card fails`() {
        assertFalse(validator.isValidLuhn("1234"))
    }
}