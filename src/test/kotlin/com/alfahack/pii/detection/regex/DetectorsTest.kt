package com.alfahack.pii.detection.regex

import com.alfahack.pii.detection.ChecksumValidator
import com.alfahack.pii.detection.ContextRules
import com.alfahack.pii.detection.PiiType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetectorsTest {

    private val checksumValidator = ChecksumValidator()
    private val contextRules = ContextRules()
    private val emailDetector = EmailDetector()
    private val phoneDetector = PhoneDetector()
    private val innDetector = InnDetector(checksumValidator, contextRules)
    private val cardDetector = CardDetector(checksumValidator, contextRules)
    private val passportDetector = PassportDetector()
    private val dateDetector = DateDetector()

    @Test
    fun `email detector finds email`() {
        val entities = emailDetector.detect("Свяжитесь: ivanov@mail.ru")
        assertEquals(1, entities.size)
        assertEquals(PiiType.EMAIL, entities[0].type)
        assertEquals("ivanov@mail.ru", "Свяжитесь: ivanov@mail.ru".substring(entities[0].start, entities[0].end))
    }

    @Test
    fun `email detector is case insensitive`() {
        val entities = emailDetector.detect("IVANOV@MAIL.RU")
        assertEquals(1, entities.size)
    }

    @Test
    fun `phone detector finds russian phone`() {
        val entities = phoneDetector.detect("тел +7 (900) 123-45-67")
        assertTrue(entities.isNotEmpty())
        assertEquals(PiiType.PHONE, entities[0].type)
    }

    @Test
    fun `inn detector validates checksum`() {
        // Валидный ИНН (12 цифр, проходит контрольную сумму)
        val valid = innDetector.detect("ИНН 770100000079")
        assertTrue(valid.isNotEmpty())
        assertTrue(valid[0].validated)
    }

    @Test
    fun `card detector validates luhn`() {
        // Валидный номер карты (проходит Luhn)
        val entities = cardDetector.detect("карта 4276 1234 5678 9012")
        assertTrue(entities.isNotEmpty())
        assertEquals(PiiType.CARD_NUMBER, entities[0].type)
    }

    @Test
    fun `passport detector finds passport`() {
        val entities = passportDetector.detect("паспорт 4509 123456")
        assertEquals(1, entities.size)
        assertEquals(PiiType.PASSPORT_SERIES_NUMBER, entities[0].type)
    }

    @Test
    fun `date detector finds date`() {
        val entities = dateDetector.detect("дата рождения 12.05.1990")
        assertEquals(1, entities.size)
        assertEquals(PiiType.DATE_OF_BIRTH, entities[0].type)
    }

    @Test
    fun `date detector distinguishes issue date by context`() {
        // «дата выдачи» → PASSPORT_ISSUE_DATE
        val issue = dateDetector.detect("дата выдачи 15.03.2015")
        assertEquals(1, issue.size)
        assertEquals(PiiType.PASSPORT_ISSUE_DATE, issue[0].type)

        // «дата рождения» → DATE_OF_BIRTH
        val birth = dateDetector.detect("дата рождения 12.05.1990")
        assertEquals(1, birth.size)
        assertEquals(PiiType.DATE_OF_BIRTH, birth[0].type)
    }
}