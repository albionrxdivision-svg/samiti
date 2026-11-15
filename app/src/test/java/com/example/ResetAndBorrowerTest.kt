package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalDataStore
import com.example.data.model.Loan
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.util.InMemoryCredentialStore
import com.example.util.PasswordManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResetAndBorrowerTest {

    @Test
    fun resetAllLocalDataClearsAllFinancialRecordsButPreservesCredentials() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = LocalDataStore(context)

        // Populate data
        dataStore.saveMembers(listOf(Member(id = "m1", memberSerial = "1", name = "রহিম")))
        dataStore.saveSavings(listOf(SavingsRecord(id = "s1", memberId = "m1", memberName = "রহিম", amount = 100.0, isPaid = true)))
        dataStore.saveLoans(listOf(Loan(id = "l1", memberId = "loan_mem_1", memberName = "নতুন ঋণ মেম্বার", principal = 3000.0)))

        assertEquals(1, dataStore.loadMembers().size)
        assertEquals(1, dataStore.loadSavings().size)
        assertEquals(1, dataStore.loadLoans().size)

        // Admin credential check required before reset
        val credStore = InMemoryCredentialStore()
        val pwManager = PasswordManager(credStore)
        assertFalse(pwManager.verifyAdminLogin("wrong", "Nemai"))
        assertFalse(pwManager.verifyAdminLogin("Nitai", "wrong"))
        assertTrue(pwManager.verifyAdminLogin("Nitai", "Nemai"))

        // Execute reset
        dataStore.clearAllData()

        assertTrue(dataStore.loadMembers().isEmpty())
        assertTrue(dataStore.loadSavings().isEmpty())
        assertTrue(dataStore.loadCollectionPeriods().isEmpty())
        assertTrue(dataStore.loadLoans().isEmpty())
        assertTrue(dataStore.loadLoanInstallments().isEmpty())
        assertTrue(dataStore.loadFines().isEmpty())
        assertTrue(dataStore.loadFundLedger().isEmpty())
        assertTrue(dataStore.loadAdminNotifications().isEmpty())

        // Admin credentials remain valid after financial reset
        assertTrue(pwManager.verifyAdminLogin("Nitai", "Nemai"))
    }
}
