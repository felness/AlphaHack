package com.alfahack.pii.detection

import com.alfahack.pii.detection.dictionary.AddressDetector
import com.alfahack.pii.detection.dictionary.CitizenshipDetector
import com.alfahack.pii.detection.dictionary.NameDetector
import com.alfahack.pii.detection.dictionary.PassportIssuerDetector
import com.alfahack.pii.detection.dictionary.PlaceOfBirthDetector
import com.alfahack.pii.detection.regex.CardDetector
import com.alfahack.pii.detection.regex.CardHolderNameDetector
import com.alfahack.pii.detection.regex.CvvDetector
import com.alfahack.pii.detection.regex.DateDetector
import com.alfahack.pii.detection.regex.DepartmentCodeDetector
import com.alfahack.pii.detection.regex.DriverLicenseDetector
import com.alfahack.pii.detection.regex.EmailDetector
import com.alfahack.pii.detection.regex.InnDetector
import com.alfahack.pii.detection.regex.PassportDetector
import com.alfahack.pii.detection.regex.PhoneDetector
import com.alfahack.pii.detection.regex.PinDetector
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Тест разрешения пересечений: DetectionEngine не должен возвращать перекрывающиеся spans.
 */
class OverlapTest {
    private val checksumValidator = ChecksumValidator()
    private val contextRules = ContextRules()

    private val engine =
        DetectionEngine(
            listOf(
                EmailDetector(),
                PhoneDetector(),
                InnDetector(checksumValidator, contextRules),
                CardDetector(checksumValidator, contextRules),
                PassportDetector(),
                DateDetector(),
                CvvDetector(),
                PinDetector(),
                DepartmentCodeDetector(),
                DriverLicenseDetector(),
                CardHolderNameDetector(),
                NameDetector(contextRules),
                AddressDetector(contextRules),
                CitizenshipDetector(),
                PlaceOfBirthDetector(contextRules),
                PassportIssuerDetector(contextRules),
            ),
        )

    @Test
    fun `no overlapping spans for complex payload`() {
        val payloads =
            listOf(
                "адрес: г. Москва, ул. Тверская, д. 1, кв. 5",
                "место рождения: г. Москва, паспорт 4509 123456",
                "Иванов Иван Иванович, паспорт 4509 123456, дата рождения 12.05.1990",
                "гражданин РФ, выдан ОУФМС России по г. Москве",
                "карта 4276 1234 5678 9014, CVV 123, ПИН-код 1234",
                "водительское удостоверение 77 12 345678, адрес: г. Москва, ул. Тверская, д. 1",
                "email ivanov@mail.ru, телефон +7 (900) 123-45-67, ИНН 770100000079",
                "паспорт 4509 123456, код подразделения 770-001, дата выдачи 15.03.2015",
            )

        for (payload in payloads) {
            val entities = engine.detect(payload)
            // Проверяем, что spans не перекрываются
            val sorted = entities.sortedBy { it.start }
            for (i in 0 until sorted.size - 1) {
                val current = sorted[i]
                val next = sorted[i + 1]
                assertTrue(
                    current.end <= next.start,
                    "Overlapping spans for [$payload]: ${current.type}@[${current.start},${current.end}) and ${next.type}@[${next.start},${next.end})",
                )
            }
        }
    }
}
