package com.example.util

import com.example.data.model.FundLedgerEntry
import com.example.data.model.LedgerType
import com.example.data.model.Loan
import com.example.data.model.LoanInstallment
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DailyAccountSummary(
    val dateKey: String,
    val displayDate: String,
    val sortMillis: Long,
    val savingsTotal: Double,
    val loanCollectedTotal: Double,
    val otherInflowTotal: Double,
    val loanDisbursedTotal: Double,
    val otherOutflowTotal: Double,
    val runningBalance: Double
) {
    val grossCollection: Double
        get() = savingsTotal + loanCollectedTotal + otherInflowTotal

    val wentOutTotal: Double
        get() = loanDisbursedTotal + otherOutflowTotal

    val netDeposit: Double
        get() = grossCollection - wentOutTotal
}

data class DailyHisabOverview(
    val summariesNewestFirst: List<DailyAccountSummary>,
    val totalSavings: Double,
    val totalLoanCollected: Double,
    val totalOtherInflow: Double,
    val totalWentOut: Double,
    val currentNetBalance: Double
)

object DailyAccountCalculator {

    private val AUTO_LEDGER_TYPES = setOf(
        LedgerType.SAVINGS_COLLECTION.name,
        LedgerType.LOAN_INSTALLMENT.name,
        LedgerType.LOAN_DISBURSEMENT.name
    )

    private fun toAsciiDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val mapped = when (ch) {
                '০' -> '0'
                '১' -> '1'
                '২' -> '2'
                '৩' -> '3'
                '৪' -> '4'
                '৫' -> '5'
                '৬' -> '6'
                '৭' -> '7'
                '৮' -> '8'
                '৯' -> '9'
                else -> ch
            }
            sb.append(mapped)
        }
        return sb.toString()
    }

    fun parseFlexibleDateMillis(rawDate: String): Long? {
        val ascii = toAsciiDigits(rawDate.trim())
            .replace('-', '/')
            .replace('.', '/')
        if (ascii.isBlank()) return null

        val parts = ascii.split("/")
        val candidatePatterns = when {
            parts.size == 3 && parts[0].length == 4 -> listOf("yyyy/MM/dd", "yyyy/M/d")
            parts.size == 3 && parts[2].length == 4 -> listOf("dd/MM/yyyy", "d/M/yyyy")
            parts.size == 3 && parts[2].length == 2 -> listOf("dd/MM/yy", "d/M/yy")
            else -> listOf("dd/MM/yy", "dd/MM/yyyy", "d/M/yy", "d/M/yyyy", "yyyy/MM/dd")
        }

        for (pattern in candidatePatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                }
                val parsed = sdf.parse(ascii)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    fun normalizeDateKey(rawDate: String): String {
        val millis = parseFlexibleDateMillis(rawDate)
        if (millis != null) {
            val sdf = SimpleDateFormat("dd/MM/yy", Locale.US)
            return sdf.format(Date(millis))
        }
        return toAsciiDigits(rawDate.trim())
    }

    fun formatDisplayDate(rawDate: String): String {
        val millis = parseFlexibleDateMillis(rawDate)
        if (millis != null) {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
            return BengaliUtils.toBengaliDigits(sdf.format(Date(millis)))
        }
        return BengaliUtils.toBengaliDigits(rawDate.trim().ifBlank { "অনির্ধারিত" })
    }

    fun buildOverview(
        savingsRecords: List<SavingsRecord>,
        loanInstallments: List<LoanInstallment>,
        loans: List<Loan>,
        fundLedger: List<FundLedgerEntry>
    ): DailyHisabOverview {
        val buckets = linkedMapOf<String, MutableDayBucket>()

        fun bucketFor(rawDate: String): MutableDayBucket {
            val key = normalizeDateKey(rawDate).ifBlank { "অনির্ধারিত" }
            return buckets.getOrPut(key) {
                MutableDayBucket(
                    dateKey = key,
                    displayDate = formatDisplayDate(rawDate),
                    sortMillis = parseFlexibleDateMillis(rawDate) ?: Long.MAX_VALUE
                )
            }
        }

        for (rec in savingsRecords) {
            if (rec.status == TransactionStatus.APPROVED.name && rec.isPaid && rec.amount > 0.0) {
                bucketFor(rec.collectionDate).savingsTotal += rec.amount
            }
        }

        for (inst in loanInstallments) {
            if (inst.status == TransactionStatus.APPROVED.name && !inst.isMissed && inst.amount > 0.0) {
                bucketFor(inst.installmentDate).loanCollectedTotal += inst.amount
            }
        }

        for (loan in loans) {
            if (loan.principal > 0.0) {
                bucketFor(loan.loanDate).loanDisbursedTotal += loan.principal
            }
        }

        for (entry in fundLedger) {
            if (entry.type in AUTO_LEDGER_TYPES) continue
            if (entry.moneyIn > 0.0 || entry.moneyOut > 0.0) {
                val bucket = bucketFor(entry.date)
                bucket.otherInflowTotal += entry.moneyIn.coerceAtLeast(0.0)
                bucket.otherOutflowTotal += entry.moneyOut.coerceAtLeast(0.0)
            }
        }

        val sortedChronological = buckets.values.sortedWith(
            compareBy<MutableDayBucket>({ it.sortMillis }, { it.dateKey })
        )

        var runningBalance = 0.0
        val summariesChronological = sortedChronological.map { b ->
            val dayNet = (b.savingsTotal + b.loanCollectedTotal + b.otherInflowTotal) -
                (b.loanDisbursedTotal + b.otherOutflowTotal)
            runningBalance += dayNet
            DailyAccountSummary(
                dateKey = b.dateKey,
                displayDate = b.displayDate,
                sortMillis = b.sortMillis,
                savingsTotal = b.savingsTotal,
                loanCollectedTotal = b.loanCollectedTotal,
                otherInflowTotal = b.otherInflowTotal,
                loanDisbursedTotal = b.loanDisbursedTotal,
                otherOutflowTotal = b.otherOutflowTotal,
                runningBalance = runningBalance
            )
        }

        return DailyHisabOverview(
            summariesNewestFirst = summariesChronological.reversed(),
            totalSavings = summariesChronological.sumOf { it.savingsTotal },
            totalLoanCollected = summariesChronological.sumOf { it.loanCollectedTotal },
            totalOtherInflow = summariesChronological.sumOf { it.otherInflowTotal },
            totalWentOut = summariesChronological.sumOf { it.wentOutTotal },
            currentNetBalance = runningBalance
        )
    }

    private data class MutableDayBucket(
        val dateKey: String,
        val displayDate: String,
        val sortMillis: Long,
        var savingsTotal: Double = 0.0,
        var loanCollectedTotal: Double = 0.0,
        var otherInflowTotal: Double = 0.0,
        var loanDisbursedTotal: Double = 0.0,
        var otherOutflowTotal: Double = 0.0
    )
}
