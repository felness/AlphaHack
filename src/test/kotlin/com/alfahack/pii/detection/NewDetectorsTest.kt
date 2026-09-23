package com.alfahack.pii.detection

import com.alfahack.pii.detection.dictionary.AddressDetector
import com.alfahack.pii.detection.dictionary.CitizenshipDetector
import com.alfahack.pii.detection.dictionary.NameDetector
import com.alfahack.pii.detection.dictionary.PassportIssuerDetector
import com.alfahack.pii.detection.dictionary.PlaceOfBirthDetector
import com.alfahack.pii.detection.regex.CardHolderNameDetector
import com.alfahack.pii.detection.regex.CvvDetector
import com.alfahack.pii.detection.regex.DepartmentCodeDetector
import com.alfahack.pii.detection.regex.DriverLicenseDetector
import com.alfahack.pii.detection.regex.PinDetector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Тесты новых детекторов (11 категорий, добавленных для полного покрытия).
 */
class NewDetectorsTest {
    private val contextRules = ContextRules()
    private val nameDetector = NameDetector(contextRules)
    private val addressDetector = AddressDetector(contextRules)
    private val citizenshipDetector = CitizenshipDetector()
    private val placeOfBirthDetector = PlaceOfBirthDetector(contextRules)
    private val passportIssuerDetector = PassportIssuerDetector(contextRules)
    private val cvvDetector = CvvDetector()
    private val pinDetector = PinDetector()
    private val departmentCodeDetector = DepartmentCodeDetector()
    private val driverLicenseDetector = DriverLicenseDetector()
    private val cardHolderNameDetector = CardHolderNameDetector()

    @Test
    fun `name detector finds full name with positive context`() {
        val entities = nameDetector.detect("Иванов Иван Иванович, паспорт 4509 123456")
        assertTrue(entities.isNotEmpty())
        assertEquals(PiiType.FULL_NAME, entities[0].type)
    }

    @Test
    fun `name detector does not mask without context`() {
        val entities = nameDetector.detect("Александр Пушкин")
        assertTrue(entities.isEmpty())
    }

    @Test
    fun `name detector does not mask famous person with negative context`() {
        val entities = nameDetector.detect("поэт Александр Пушкин")
        assertTrue(entities.isEmpty())
    }

    @Test
    fun `address detector finds address with positive context`() {
        val entities = addressDetector.detect("адрес: г. Москва, ул. Тверская, д. 1, кв. 5")
        assertTrue(entities.isNotEmpty())
        assertEquals(PiiType.ADDRESS, entities[0].type)
    }

    @Test
    fun `address detector does not mask bank branch`() {
        val entities = addressDetector.detect("отделение Банка по адресу г. Москва, ул. Тверская, д. 1")
        assertTrue(entities.isEmpty())
    }

    @Test
    fun `citizenship detector finds citizenship`() {
        val entities = citizenshipDetector.detect("гражданин РФ")
        assertTrue(entities.isNotEmpty())
        assertEquals(PiiType.CITIZENSHIP, entities[0].type)
    }

    @Test
    fun `place of birth detector finds place with context`() {
        val entities = placeOfBirthDetector.detect("место рождения: г. Москва")
        assertTrue(entities.isNotEmpty())
        assertEquals(PiiType.PLACE_OF_BIRTH, entities[0].type)
    }

    @Test
    fun `passport issuer detector finds issuer with context`() {
        val entities = passportIssuerDetector.detect("выдан ОУФМС России по г. Москве")
        assertTrue(entities.isNotEmpty())
        assertEquals(PiiType.PASSPORT_ISSUER, entities[0].type)
    }

    @Test
    fun `cvv detector finds cvv with context`() {
        val entities = cvvDetector.detect("CVV 123")
        assertTrue(entities.isNotEmpty())
        assertEquals(PiiType.CVV, entities[0].type)
    }

    @Test
    fun `pin detector finds pin with context`() {
        val entities = pinDetector.detect("ПИН-код 1234")
        assertTrue(entities.isNotEmpty())
        assertEquals(PiiType.PIN, entities[0].type)
    }

    @Test
    fun `department code detector finds code`() {
        val entities = departmentCodeDetector.detect("код подразделения 770-001")
        assertTrue(entities.isNotEmpty())
        assertEquals(PiiType.PASSPORT_DEPARTMENT_CODE, entities[0].type)
    }

    @Test
    fun `driver license detector finds license`() {
        val entities = driverLicenseDetector.detect("водительское удостоверение 77 12 345678")
        assertTrue(entities.isNotEmpty())
        assertEquals(PiiType.DRIVER_LICENSE, entities[0].type)
    }

    @Test
    fun `card holder name detector finds name`() {
        val entities = cardHolderNameDetector.detect("CARD HOLDER: IVANOV IVAN")
        assertTrue(entities.isNotEmpty())
        assertEquals(PiiType.CARD_HOLDER_NAME, entities[0].type)
    }
}
