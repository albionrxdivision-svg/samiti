package com.example.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.round

object BengaliUtils {
    private val englishToBengaliDigits = mapOf(
        '0' to '০',
        '1' to '১',
        '2' to '২',
        '3' to '৩',
        '4' to '৪',
        '5' to '৫',
        '6' to '৬',
        '7' to '৭',
        '8' to '৮',
        '9' to '৯'
    )

    fun toBengaliDigits(input: Any?): String {
        if (input == null) return "০"
        val str = when (input) {
            is Double -> if (input % 1.0 == 0.0) input.toLong().toString() else DecimalFormat("#.##").format(input)
            is Float -> if (input % 1.0f == 0.0f) input.toLong().toString() else DecimalFormat("#.##").format(input)
            is Number -> input.toLong().toString()
            else -> input.toString()
        }
        val builder = StringBuilder()
        for (char in str) {
            builder.append(englishToBengaliDigits[char] ?: char)
        }
        return builder.toString()
    }

    fun formatTaka(amount: Double): String {
        return "৳" + toBengaliDigits(amount)
    }

    fun formatTwoDigitSerial(serial: String): String {
        val trimmed = serial.trim()
        if (trimmed.isEmpty()) return "00"
        val ascii = StringBuilder(trimmed.length)
        for (ch in trimmed) {
            ascii.append(
                when (ch) {
                    '০' -> '0'; '১' -> '1'; '২' -> '2'; '৩' -> '3'; '৪' -> '4'
                    '৫' -> '5'; '৬' -> '6'; '৭' -> '7'; '৮' -> '8'; '৯' -> '9'
                    else -> ch
                }
            )
        }
        val num = ascii.toString().toIntOrNull()
        return if (num != null && num >= 0) {
            num.toString().padStart(2, '0')
        } else {
            trimmed
        }
    }

    fun formatCleanAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            amount.toLong().toString()
        } else {
            DecimalFormat("#.##").format(amount)
        }
    }

    fun getCurrentDateStr(): String {
        val sdf = SimpleDateFormat("dd/MM/yy", Locale.US)
        return sdf.format(Date())
    }

    fun getDateAfterDaysStr(days: Int = 7): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        val sdf = SimpleDateFormat("dd/MM/yy", Locale.US)
        return sdf.format(cal.time)
    }

    fun addDaysToDateStr(dateStr: String, days: Int = 7): String {
        val sdf = SimpleDateFormat("dd/MM/yy", Locale.US).apply {
            isLenient = false
        }
        return try {
            val parsed = sdf.parse(dateStr.trim()) ?: return getDateAfterDaysStr(days)
            val cal = Calendar.getInstance()
            cal.time = parsed
            cal.add(Calendar.DAY_OF_YEAR, days)
            sdf.format(cal.time)
        } catch (_: Exception) {
            getDateAfterDaysStr(days)
        }
    }

    fun getCurrentIsoDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }
}

data class LoanCalculationResult(
    val principal: Double,
    val interest: Double,
    val totalPayable: Double,
    val weeklyInstallment: Double,
    val effectiveRatePercent: Double
)

object LoanCalculator {
    private fun roundTwoDecimals(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    fun calculateInterestFromRate(principal: Double, ratePercent: Double): Double {
        if (principal <= 0.0 || ratePercent <= 0.0) return 0.0
        return roundTwoDecimals(principal * ratePercent / 100.0)
    }

    fun calculateTotalPayable(principal: Double, interest: Double): Double {
        val safePrincipal = principal.coerceAtLeast(0.0)
        val safeInterest = interest.coerceAtLeast(0.0)
        return roundTwoDecimals(safePrincipal + safeInterest)
    }

    fun calculateWeeklyInstallment(totalPayable: Double, weeks: Int): Double {
        if (totalPayable <= 0.0 || weeks <= 0) return 0.0
        return roundTwoDecimals(totalPayable / weeks)
    }

    fun calculateEffectiveRatePercent(principal: Double, interest: Double): Double {
        if (principal <= 0.0 || interest <= 0.0) return 0.0
        return roundTwoDecimals((interest / principal) * 100.0)
    }

    fun calculateFromRate(principal: Double, ratePercent: Double, weeks: Int): LoanCalculationResult {
        val safePrincipal = principal.coerceAtLeast(0.0)
        val interest = calculateInterestFromRate(safePrincipal, ratePercent)
        val totalPayable = calculateTotalPayable(safePrincipal, interest)
        val weeklyInstallment = calculateWeeklyInstallment(totalPayable, weeks)
        val effectiveRate = calculateEffectiveRatePercent(safePrincipal, interest)
        return LoanCalculationResult(
            principal = roundTwoDecimals(safePrincipal),
            interest = interest,
            totalPayable = totalPayable,
            weeklyInstallment = weeklyInstallment,
            effectiveRatePercent = effectiveRate
        )
    }

    fun calculateFromFixedInterest(principal: Double, interest: Double, weeks: Int): LoanCalculationResult {
        val safePrincipal = principal.coerceAtLeast(0.0)
        val safeInterest = interest.coerceAtLeast(0.0)
        val totalPayable = calculateTotalPayable(safePrincipal, safeInterest)
        val weeklyInstallment = calculateWeeklyInstallment(totalPayable, weeks)
        val effectiveRate = calculateEffectiveRatePercent(safePrincipal, safeInterest)
        return LoanCalculationResult(
            principal = roundTwoDecimals(safePrincipal),
            interest = roundTwoDecimals(safeInterest),
            totalPayable = totalPayable,
            weeklyInstallment = weeklyInstallment,
            effectiveRatePercent = effectiveRate
        )
    }
}

