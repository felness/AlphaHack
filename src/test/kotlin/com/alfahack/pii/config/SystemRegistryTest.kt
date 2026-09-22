package com.alfahack.pii.config

import com.alfahack.pii.detection.PiiType
import com.alfahack.pii.exception.SystemNotAllowedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Тесты реестра систем (определение системы, фильтрация типов ПД).
 */
class SystemRegistryTest {

    private val properties = PiiProperties(
        systems = mapOf(
            "default" to PiiProperties.SystemConfig(
                enabled = true,
                maskingTypes = listOf("ALL"),
                unmaskingEnabled = true,
                maskFormat = "STAR"
            ),
            "system-a" to PiiProperties.SystemConfig(
                enabled = true,
                maskingTypes = listOf("EMAIL", "PHONE"),
                unmaskingEnabled = true,
                maskFormat = "STAR"
            ),
            "system-b" to PiiProperties.SystemConfig(
                enabled = false,
                maskingTypes = listOf("ALL"),
                unmaskingEnabled = true,
                maskFormat = "STAR"
            )
        )
    )

    private val registry = SystemRegistry(properties)

    @Test
    fun `resolve default system when no header`() {
        val config = registry.resolve(null)
        assertEquals(listOf("ALL"), config.maskingTypes)
    }

    @Test
    fun `resolve known system`() {
        val config = registry.resolve("system-a")
        assertEquals(listOf("EMAIL", "PHONE"), config.maskingTypes)
    }

    @Test
    fun `unknown system throws`() {
        assertThrows(SystemNotAllowedException::class.java) {
            registry.resolve("unknown")
        }
    }

    @Test
    fun `disabled system throws`() {
        assertThrows(SystemNotAllowedException::class.java) {
            registry.resolve("system-b")
        }
    }

    @Test
    fun `filter all types when ALL`() {
        val config = registry.resolve(null)
        val detected = setOf(PiiType.EMAIL, PiiType.PHONE, PiiType.INN)
        val filtered = registry.filterTypes(config, detected)
        assertEquals(detected, filtered)
    }

    @Test
    fun `filter types by system rules`() {
        val config = registry.resolve("system-a")
        val detected = setOf(PiiType.EMAIL, PiiType.PHONE, PiiType.INN)
        val filtered = registry.filterTypes(config, detected)
        assertEquals(setOf(PiiType.EMAIL, PiiType.PHONE), filtered)
        assertTrue(PiiType.INN !in filtered)
    }
}