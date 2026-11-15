package com.example

import com.example.data.model.FineType
import com.example.data.model.Loan
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import com.example.util.BengaliUtils
import com.example.util.LoanCalculator
import com.example.util.SavingsAndFineCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EkotaBusinessLogicTest {

    @Test
    fun testBengaliNumberConversion() {
        assertEquals("১২৩৪৫", BengaliUtils.toBengaliDigits(12345))
        assertEquals("৳১৩০০", BengaliUtils.formatTaka(1300.0))
    }

    @Test
    fun testSampleLoanCalculation() {
        // Nitai: Principal = 3000, Interest = 600, Total Payable = 3600
        val principal = 3000.0
        val interest = 600.0
        val totalPayable = principal + interest
        val weeklyInstallment = 300.0
        val fourInstallmentsPaid = weeklyInstallment * 4 // 1200
        val remainingLoan = totalPayable - fourInstallmentsPaid // 2400

        assertEquals(3600.0, totalPayable, 0.01)
        assertEquals(1200.0, fourInstallmentsPaid, 0.01)
        assertEquals(2400.0, remainingLoan, 0.01)
    }

    @Test
    fun testLoanCalculatorFromRate() {
        val result = LoanCalculator.calculateFromRate(
            principal = 3000.0,
            ratePercent = 20.0,
            weeks = 12
        )
        assertEquals(3000.0, result.principal, 0.01)
        assertEquals(600.0, result.interest, 0.01)
        assertEquals(3600.0, result.totalPayable, 0.01)
        assertEquals(300.0, result.weeklyInstallment, 0.01)
        assertEquals(20.0, result.effectiveRatePercent, 0.01)

        val custom = LoanCalculator.calculateFromRate(
            principal = 5000.0,
            ratePercent = 15.0,
            weeks = 10
        )
        assertEquals(750.0, custom.interest, 0.01)
        assertEquals(5750.0, custom.totalPayable, 0.01)
        assertEquals(575.0, custom.weeklyInstallment, 0.01)
    }

    @Test
    fun testLoanCalculatorFromFixedInterestAndZeroGuards() {
        val fixed = LoanCalculator.calculateFromFixedInterest(
            principal = 4000.0,
            interest = 800.0,
            weeks = 16
        )
        assertEquals(4800.0, fixed.totalPayable, 0.01)
        assertEquals(300.0, fixed.weeklyInstallment, 0.01)
        assertEquals(20.0, fixed.effectiveRatePercent, 0.01)

        val zeroWeeks = LoanCalculator.calculateFromRate(
            principal = 3000.0,
            ratePercent = 20.0,
            weeks = 0
        )
        assertEquals(3600.0, zeroWeeks.totalPayable, 0.01)
        assertEquals(0.0, zeroWeeks.weeklyInstallment, 0.01)
    }

    @Test
    fun testAddDaysToDateStr() {
        assertEquals("19/03/26", BengaliUtils.addDaysToDateStr("12/03/26", 7))
        assertEquals("02/04/26", BengaliUtils.addDaysToDateStr("26/03/26", 7))
    }

    @Test
    fun testSampleLedgerCalculation() {
        // Previous Balance + Money In - Money Out = Current Balance
        var balance = 0.0
        balance += 1600.0
        assertEquals(1600.0, balance, 0.01)
        balance += 1500.0
        assertEquals(3100.0, balance, 0.01)
        balance -= 1000.0
        assertEquals(2100.0, balance, 0.01)
        balance += 1400.0
        assertEquals(3500.0, balance, 0.01)
        balance += 1600.0
        assertEquals(5100.0, balance, 0.01)
    }

    @Test
    fun testWeeklySavingsMatrixTotals() {
        val col1 = 100 * 8 + 200 + 300
        val col2 = 100 * 7 + 0 + 200 + 300
        val col3 = 100 * 6 + 0 + 0 + 200 + 300
        val col4 = 100 * 8 + 200 + 300

        assertEquals(1300, col1)
        assertEquals(1200, col2)
        assertEquals(1100, col3)
        assertEquals(1300, col4)
        assertEquals(4900, col1 + col2 + col3 + col4)
    }

    @Test
    fun testAppDownloadUrlNormalization() {
        assertEquals("", com.example.ui.settings.normalizeUrl("   "))
        assertEquals("https://example.com/ekota.apk", com.example.ui.settings.normalizeUrl("https://example.com/ekota.apk"))
        assertEquals("https://drive.google.com/file/d/abc", com.example.ui.settings.normalizeUrl("drive.google.com/file/d/abc"))
        assertEquals(null, com.example.ui.settings.normalizeUrl("invalid url"))
    }

    @Test
    fun testMissedSavingsDoubleCatchUpAllocation() {
        val member = Member(
            id = "m1",
            memberSerial = "1",
            name = "রহিম",
            weeklySavingsAmount = 100.0,
            joinDate = "01/03/26"
        )
        val dates = listOf("05/03/26", "12/03/26", "19/03/26")
        val existingRecords = listOf(
            SavingsRecord(
                id = "m1_05-03-26",
                memberId = "m1",
                memberName = "রহিম",
                collectionDate = "05/03/26",
                amount = 100.0,
                isPaid = true,
                status = TransactionStatus.APPROVED.name
            ),
            SavingsRecord(
                id = "m1_12-03-26",
                memberId = "m1",
                memberName = "রহিম",
                collectionDate = "12/03/26",
                amount = 0.0,
                isPaid = false,
                status = TransactionStatus.APPROVED.name
            )
        )

        val missed = SavingsAndFineCalculator.findMissedEarlierSavingsDates(
            member = member,
            targetDate = "19/03/26",
            orderedDates = dates,
            existingRecords = existingRecords
        )
        assertEquals(listOf("12/03/26"), missed)

        // Member pays double (200) on 19/03/26 -> counts 100 for missed 12/03/26 + 100 for 19/03/26
        val allocation = SavingsAndFineCalculator.allocateSavingsPayment(
            member = member,
            targetDate = "19/03/26",
            enteredAmount = 200.0,
            orderedDates = dates,
            existingRecords = existingRecords
        )
        assertEquals(listOf("12/03/26"), allocation.catchUpDates)
        assertEquals(2, allocation.allocations.size)
        assertEquals(100.0, allocation.allocations[0].newTotalAmount, 0.01)
        assertTrue(allocation.allocations[0].isCatchUpForMissedWeek)
        assertEquals("19/03/26", allocation.allocations[1].collectionDate)
        assertEquals(100.0, allocation.allocations[1].newTotalAmount, 0.01)
        assertFalse(allocation.allocations[1].isCatchUpForMissedWeek)
    }

    @Test
    fun testSeparateTenTakaFinesForSavingsAndLoans() {
        val member = Member(id = "m2", memberSerial = "2", name = "করিম", weeklySavingsAmount = 100.0)
        val dates = listOf("05/03/26", "12/03/26")
        val nextDate = SavingsAndFineCalculator.resolveFineAppliedDate("05/03/26", dates)
        assertEquals("12/03/26", nextDate)

        val savFine = SavingsAndFineCalculator.buildSavingsFineRecord(
            member = member,
            missedDate = "05/03/26",
            appliedDate = nextDate,
            officerId = "admin",
            officerName = "অ্যাডমিন"
        )
        assertEquals(10.0, savFine.amount, 0.01)
        assertEquals(FineType.SAVINGS.name, savFine.fineType)
        assertEquals("12/03/26", savFine.fineDate)
        assertFalse(savFine.isPaid)

        val loan = Loan(
            id = "l1",
            memberId = "m2",
            memberName = "করিম",
            memberSerial = "2",
            principal = 3000.0,
            interest = 600.0,
            totalPayable = 3600.0,
            weeklyInstallment = 300.0,
            totalPaid = 300.0,
            remainingLoan = 3300.0,
            missedInstallments = 1
        )
        val loanFine = SavingsAndFineCalculator.buildLoanFineRecord(
            loan = loan,
            missedDate = "12/03/26",
            appliedDate = "19/03/26",
            officerId = "admin",
            officerName = "অ্যাডমিন"
        )
        assertEquals(10.0, loanFine.amount, 0.01)
        assertEquals(FineType.LOAN.name, loanFine.fineType)

        // Paying double loan installment (600) catches up the 1 missed installment without mixing the 10 BDT fine
        val catchUp = SavingsAndFineCalculator.calculateLoanCatchUp(loan, 600.0)
        assertEquals(1, catchUp.coveredMissedWeeks)
        assertEquals(0, catchUp.remainingMissedInstallments)
        assertEquals(900.0, catchUp.newTotalPaid, 0.01)
        assertEquals(2700.0, catchUp.newRemainingLoan, 0.01)
    }

    @Test
    fun testAdminAndOfficerPasswordChangeAndVerification() {
        val store = com.example.util.InMemoryCredentialStore()
        val manager = com.example.util.PasswordManager(store)

        // Default credentials work initially
        assertTrue(manager.verifyAdminLogin("Nitai", "Nemai"))
        assertFalse(manager.verifyAdminLogin("Nitai", "wrong"))
        assertTrue(manager.verifyOfficerLogin("মাঠ কর্মকর্তা ১", "123"))

        // Admin wrong current password rejected
        val badAdminChange = manager.changeOwnPassword(
            role = com.example.data.model.UserRole.ADMIN,
            officerUsername = "Nitai",
            currentPassword = "wrong",
            newPassword = "newAdmin99",
            confirmPassword = "newAdmin99"
        )
        assertTrue(badAdminChange is com.example.util.PasswordChangeResult.Error)

        // Admin changes own password
        val okAdminChange = manager.changeOwnPassword(
            role = com.example.data.model.UserRole.ADMIN,
            officerUsername = "Nitai",
            currentPassword = "Nemai",
            newPassword = "newAdmin99",
            confirmPassword = "newAdmin99"
        )
        assertTrue(okAdminChange is com.example.util.PasswordChangeResult.Success)
        assertFalse(manager.verifyAdminLogin("Nitai", "Nemai"))
        assertTrue(manager.verifyAdminLogin("Nitai", "newAdmin99"))

        // Field Officer changes own password
        val officerMismatch = manager.changeOwnPassword(
            role = com.example.data.model.UserRole.FIELD_OFFICER,
            officerUsername = "রহিম",
            currentPassword = "123",
            newPassword = "off456",
            confirmPassword = "off999"
        )
        assertTrue(officerMismatch is com.example.util.PasswordChangeResult.Error)

        val okOfficerChange = manager.changeOwnPassword(
            role = com.example.data.model.UserRole.FIELD_OFFICER,
            officerUsername = "রহিম",
            currentPassword = "123",
            newPassword = "off456",
            confirmPassword = "off456"
        )
        assertTrue(okOfficerChange is com.example.util.PasswordChangeResult.Success)
        assertFalse(manager.verifyOfficerLogin("রহিম", "123"))
        assertTrue(manager.verifyOfficerLogin("রহিম", "off456"))

        // Admin sets another officer's password directly
        val adminSetOfficer = manager.adminSetOfficerPassword(
            officerUsername = "করিম",
            newPassword = "karim789",
            confirmPassword = "karim789"
        )
        assertTrue(adminSetOfficer is com.example.util.PasswordChangeResult.Success)
        assertFalse(manager.verifyOfficerLogin("করিম", "123"))
        assertTrue(manager.verifyOfficerLogin("করিম", "karim789"))
        assertFalse(manager.verifyOfficerLogin("Nitai", "123"))
    }

    @Test
    fun testDateParsingValidationForSavingsSheetUpdate() {
        assertTrue(SavingsAndFineCalculator.parseDateMillis("14/11/26") != null)
        assertTrue(SavingsAndFineCalculator.parseDateMillis("32/11/26") == null)
        assertTrue(SavingsAndFineCalculator.parseDateMillis("invalid") == null)
        val sorted = SavingsAndFineCalculator.sortDateStrings(listOf("20/11/26", "06/11/26", "14/11/26"))
        assertEquals(listOf("06/11/26", "14/11/26", "20/11/26"), sorted)
    }
}
