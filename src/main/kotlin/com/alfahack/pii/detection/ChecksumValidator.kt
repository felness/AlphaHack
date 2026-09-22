package com.alfahack.pii.detection

import org.springframework.stereotype.Component

/**
 * Checksum-валидация структурированных типов ПД.
 *
 * Снижает ложные срабатывания: ИНН (контрольная сумма), карта (Luhn).
 */
@Component
class ChecksumValidator {

    /**
     * Проверить контрольную сумму ИНН (10 или 12 цифр).
     */
    fun isValidInn(inn: String): Boolean {
        return when (inn.length) {
            10 -> checkSum(inn, INN_10_WEIGHTS) == inn[9].digitToInt()
            12 -> {
                val first = checkSum(inn, INN_12_WEIGHTS_1) == inn[10].digitToInt()
                val second = checkSum(inn, INN_12_WEIGHTS_2) == inn[11].digitToInt()
                first && second
            }
            else -> false
        }
    }

    /**
     * Проверить номер карты по алгоритму Луна.
     */
    fun isValidLuhn(number: String): Boolean {
        if (number.length < 13 || number.length > 19) return false
        var sum = 0
        var double = false
        for (i in number.length - 1 downTo 0) {
            var digit = number[i].digitToInt()
            if (double) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            double = !double
        }
        return sum % 10 == 0
    }

    private fun checkSum(inn: String, weights: IntArray): Int {
        var sum = 0
        for (i in weights.indices) {
            sum += inn[i].digitToInt() * weights[i]
        }
        return sum % 11 % 10
    }

    companion object {
        private val INN_10_WEIGHTS = intArrayOf(2, 4, 10, 3, 5, 9, 4, 6, 8)
        private val INN_12_WEIGHTS_1 = intArrayOf(7, 2, 4, 10, 3, 5, 9, 4, 6, 8)
        private val INN_12_WEIGHTS_2 = intArrayOf(3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8)
    }
}