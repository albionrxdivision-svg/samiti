package com.example.util

import com.example.data.model.FineRecord
import com.example.data.model.FineType
import com.example.data.model.Loan
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor

data class DateSavingsAllocation(
    val collectionDate: String,
    val newTotalAmount: Double,
    val addedDelta: Double,
    val isCatchUpForMissedWeek: Boolean
)

data class SavingsAllocationResult(
    val allocations: List<DateSavingsAllocation>,
    val catchUpDates: List<String>
)

data class LoanCatchUpResult(
    val coveredMissedWeeks: Int,
    val remainingMissedInstallments: Int,
    val newTotalPaid: Double,
    val newRemainingLoan: Double,
    val isFullyPaid: Boolean
)

object SavingsAndFineCalculator {
    const val DEFAULT_WEEKLY_FINE_AMOUNT = 10.0

    fun parseDateMillis(dateStr: String): Long? {
        val trimmed = dateStr.trim()
        if (trimmed.isEmpty()) return null
        val sdf = SimpleDateFormat("dd/MM/yy", Locale.US).apply {
            isLenient = false
        }
        return try {
            sdf.parse(trimmed)?.time
        } catch (_: Exception) {
            null
        }
    }

    fun sortDateStrings(dates: List<String>): List<String> {
        return dates
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(compareBy<String>({ parseDateMillis(it) ?: Long.MAX_VALUE }, { it }))
    }

    /**
     * Checks whether two dd/MM/yy date strings fall within the same cooperative week
     * (Saturday through Friday). Used to prevent double submission within the same week.
     */
    fun isSameWeek(dateStr1: String, dateStr2: String): Boolean {
        val d1 = dateStr1.trim()
        val d2 = dateStr2.trim()
        if (d1.isEmpty() || d2.isEmpty()) return false
        if (d1 == d2) return true

        val millis1 = parseDateMillis(d1) ?: return false
        val millis2 = parseDateMillis(d2) ?: return false

        fun weekStartKey(timeMillis: Long): Long {
            val cal = Calendar.getInstance(Locale.US).apply {
                timeInMillis = timeMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                val dayOfWeek = get(Calendar.DAY_OF_WEEK)
                val daysSinceSaturday = (dayOfWeek - Calendar.SATURDAY + 7) % 7
                add(Calendar.DAY_OF_YEAR, -daysSinceSaturday)
            }
            return cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
        }

        return weekStartKey(millis1) == weekStartKey(millis2)
    }

    /**
     * Returns chronologically earlier collection dates for [member] before [targetDate]
     * where the member has NOT yet paid their full weekly savings amount.
     */
    fun findMissedEarlierSavingsDates(
        member: Member,
        targetDate: String,
        orderedDates: List<String>,
        existingRecords: List<SavingsRecord>
    ): List<String> {
        val sortedDates = sortDateStrings(orderedDates)
        val targetIndex = sortedDates.indexOf(targetDate)
        val targetMillis = parseDateMillis(targetDate)
        val weeklyRate = if (member.weeklySavingsAmount > 0.0) member.weeklySavingsAmount else 100.0
        val joinMillis = parseDateMillis(member.joinDate)

        val memberRecordsByDate = existingRecords
            .filter { (it.memberId == member.id || it.memberName == member.name) && it.status != TransactionStatus.REJECTED.name }
            .associateBy { it.collectionDate }

        return sortedDates.filterIndexed { index, date ->
            val isEarlier = when {
                targetIndex >= 0 -> index < targetIndex
                targetMillis != null -> (parseDateMillis(date) ?: Long.MAX_VALUE) < targetMillis
                else -> false
            }
            if (!isEarlier) return@filterIndexed false

            val rec = memberRecordsByDate[date]
            val existingAmt = if (rec != null && rec.isPaid) rec.amount else 0.0
            val explicitlyMissed = rec != null && (!rec.isPaid || rec.amount <= 0.0)
            val dateMillis = parseDateMillis(date)
            val afterOrOnJoin = joinMillis == null || dateMillis == null || dateMillis >= joinMillis

            // Count as missed if explicitly marked 0/unpaid OR if member joined on/before that date and didn't pay full rate
            (explicitlyMissed || afterOrOnJoin) && existingAmt < weeklyRate
        }
    }

    /**
     * Allocates [enteredAmount] entered on [targetDate].
     * If a member missed earlier week(s) and pays extra / double (> weeklySavingsAmount),
     * the unpaid earlier week(s) are counted first in chronological order, and the current week
     * receives the current share + any remaining surplus.
     */
    fun allocateSavingsPayment(
        member: Member,
        targetDate: String,
        enteredAmount: Double,
        orderedDates: List<String>,
        existingRecords: List<SavingsRecord>
    ): SavingsAllocationResult {
        if (enteredAmount <= 0.0) {
            return SavingsAllocationResult(
                allocations = listOf(
                    DateSavingsAllocation(
                        collectionDate = targetDate,
                        newTotalAmount = 0.0,
                        addedDelta = 0.0,
                        isCatchUpForMissedWeek = false
                    )
                ),
                catchUpDates = emptyList()
            )
        }

        val weeklyRate = if (member.weeklySavingsAmount > 0.0) member.weeklySavingsAmount else 100.0
        val memberRecordsByDate = existingRecords
            .filter { (it.memberId == member.id || it.memberName == member.name) && it.status != TransactionStatus.REJECTED.name }
            .associateBy { it.collectionDate }

        val previousTargetAmt = memberRecordsByDate[targetDate]?.let {
            if (it.isPaid) it.amount else 0.0
        } ?: 0.0

        if (enteredAmount <= weeklyRate) {
            return SavingsAllocationResult(
                allocations = listOf(
                    DateSavingsAllocation(
                        collectionDate = targetDate,
                        newTotalAmount = enteredAmount,
                        addedDelta = (enteredAmount - previousTargetAmt).coerceAtLeast(0.0),
                        isCatchUpForMissedWeek = false
                    )
                ),
                catchUpDates = emptyList()
            )
        }

        val missedDates = findMissedEarlierSavingsDates(
            member = member,
            targetDate = targetDate,
            orderedDates = orderedDates,
            existingRecords = existingRecords
        )

        if (missedDates.isEmpty()) {
            return SavingsAllocationResult(
                allocations = listOf(
                    DateSavingsAllocation(
                        collectionDate = targetDate,
                        newTotalAmount = enteredAmount,
                        addedDelta = (enteredAmount - previousTargetAmt).coerceAtLeast(0.0),
                        isCatchUpForMissedWeek = false
                    )
                ),
                catchUpDates = emptyList()
            )
        }

        var extraToAllocate = enteredAmount - weeklyRate
        val allocations = mutableListOf<DateSavingsAllocation>()
        val catchUpDates = mutableListOf<String>()

        for (missedDate in missedDates) {
            if (extraToAllocate <= 0.0) break
            val rec = memberRecordsByDate[missedDate]
            val existingAmt = if (rec != null && rec.isPaid) rec.amount else 0.0
            val needed = (weeklyRate - existingAmt).coerceAtLeast(0.0)
            val addAmt = minOf(extraToAllocate, needed)
            if (addAmt > 0.0) {
                allocations.add(
                    DateSavingsAllocation(
                        collectionDate = missedDate,
                        newTotalAmount = existingAmt + addAmt,
                        addedDelta = addAmt,
                        isCatchUpForMissedWeek = true
                    )
                )
                catchUpDates.add(missedDate)
                extraToAllocate -= addAmt
            }
        }

        val targetAmount = weeklyRate + extraToAllocate
        allocations.add(
            DateSavingsAllocation(
                collectionDate = targetDate,
                newTotalAmount = targetAmount,
                addedDelta = (targetAmount - previousTargetAmt).coerceAtLeast(0.0),
                isCatchUpForMissedWeek = false
            )
        )

        return SavingsAllocationResult(
            allocations = allocations,
            catchUpDates = catchUpDates
        )
    }

    /**
     * Resolves the next week's date on which a 10 BDT fine is assessed for missing [missedDate].
     */
    fun resolveFineAppliedDate(missedDate: String, orderedDates: List<String>): String {
        val sorted = sortDateStrings(orderedDates)
        val idx = sorted.indexOf(missedDate)
        if (idx >= 0 && idx + 1 < sorted.size) {
            return sorted[idx + 1]
        }
        return BengaliUtils.addDaysToDateStr(missedDate, 7)
    }

    fun buildSavingsFineRecord(
        member: Member,
        missedDate: String,
        appliedDate: String,
        officerId: String,
        officerName: String
    ): FineRecord {
        val safeMissed = missedDate.replace("/", "-")
        return FineRecord(
            id = "fine_sav_${member.id}_$safeMissed",
            memberId = member.id,
            memberName = member.name,
            memberSerial = member.memberSerial,
            fineDate = appliedDate,
            missedDate = missedDate,
            fineType = FineType.SAVINGS.name,
            amount = DEFAULT_WEEKLY_FINE_AMOUNT,
            isPaid = false,
            paidDate = "",
            referenceId = missedDate,
            officerId = officerId,
            officerName = officerName,
            status = TransactionStatus.APPROVED.name,
            createdBy = officerId
        )
    }

    fun buildLoanFineRecord(
        loan: Loan,
        missedDate: String,
        appliedDate: String,
        officerId: String,
        officerName: String
    ): FineRecord {
        val safeMissed = missedDate.replace("/", "-")
        return FineRecord(
            id = "fine_loan_${loan.id}_$safeMissed",
            memberId = loan.memberId,
            memberName = loan.memberName,
            memberSerial = loan.memberSerial,
            fineDate = appliedDate,
            missedDate = missedDate,
            fineType = FineType.LOAN.name,
            amount = DEFAULT_WEEKLY_FINE_AMOUNT,
            isPaid = false,
            paidDate = "",
            referenceId = loan.id,
            officerId = officerId,
            officerName = officerName,
            status = TransactionStatus.APPROVED.name,
            createdBy = officerId
        )
    }

    /**
     * Computes how a loan installment payment counts against missed weeks & remaining balance.
     * Note: Fine money (10 BDT) is NEVER deducted from or added to loan principal/installments.
     */
    fun calculateLoanCatchUp(loan: Loan, paymentAmount: Double): LoanCatchUpResult {
        val safePayment = paymentAmount.coerceAtLeast(0.0)
        val newTotalPaid = loan.totalPaid + safePayment
        val newRemaining = (loan.totalPayable - newTotalPaid).coerceAtLeast(0.0)
        val isFullyPaid = newRemaining <= 0.0

        val coveredMissed = when {
            isFullyPaid -> loan.missedInstallments
            loan.missedInstallments > 0 && loan.weeklyInstallment > 0.0 -> {
                val extra = (safePayment - loan.weeklyInstallment).coerceAtLeast(0.0)
                floor(extra / loan.weeklyInstallment).toInt().coerceIn(0, loan.missedInstallments)
            }
            else -> 0
        }
        val remainingMissed = if (isFullyPaid) 0 else (loan.missedInstallments - coveredMissed).coerceAtLeast(0)

        return LoanCatchUpResult(
            coveredMissedWeeks = coveredMissed,
            remainingMissedInstallments = remainingMissed,
            newTotalPaid = newTotalPaid,
            newRemainingLoan = newRemaining,
            isFullyPaid = isFullyPaid
        )
    }
}
