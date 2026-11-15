package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalDataStore
import com.example.data.model.AuthSession
import com.example.data.model.CollectionPeriod
import com.example.data.model.FineRecord
import com.example.data.model.FineType
import com.example.data.model.FundLedgerEntry
import com.example.data.model.Loan
import com.example.data.model.LoanInstallment
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.EkotaRepository
import com.example.ui.MainViewModel
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataProtectionAndDeleteTest {

    private lateinit var context: Context
    private lateinit var dataStore: LocalDataStore
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        dataStore = LocalDataStore(context)
        dataStore.clearAllData()
        AuthSession.currentUserProfile = null
    }

    @After
    fun tearDown() {
        AuthSession.currentUserProfile = null
        Dispatchers.resetMain()
    }

    @Test
    fun partialMigrationPreservesUnmigratedAdminCollectionsInLocalStore() {
        dataStore.saveMembers(listOf(Member(id = "m1", memberSerial = "1", name = "রহিম")))
        dataStore.saveSavings(listOf(SavingsRecord(id = "s1", memberId = "m1", memberName = "রহিম", amount = 100.0)))
        dataStore.saveLoanInstallments(listOf(LoanInstallment(id = "i1", loanId = "l1", memberId = "m1", memberName = "রহিম", amount = 250.0)))
        dataStore.saveFines(listOf(FineRecord(id = "f1", memberId = "m1", memberName = "রহিম", amount = 10.0)))

        // Admin-only collections that non-admin migration does not upload
        dataStore.saveLoans(listOf(Loan(id = "l1", memberId = "m1", memberName = "রহিম", principal = 5000.0)))
        dataStore.saveCollectionPeriods(listOf(CollectionPeriod(id = "p1", dateStr = "14/11/26", monthYear = "নভেম্বর ২০২৬")))
        dataStore.saveFundLedger(listOf(FundLedgerEntry(id = "led1", date = "14/11/26", description = "মূল তহবিল", moneyIn = 5000.0, balance = 5000.0)))

        dataStore.clearMigratedKeys(includeAdminKeys = false)

        // Officer-migrated keys cleared, Admin collections remain intact
        assertTrue(dataStore.loadMembers().isEmpty())
        assertTrue(dataStore.loadSavings().isEmpty())
        assertTrue(dataStore.loadLoanInstallments().isEmpty())
        assertTrue(dataStore.loadFines().isEmpty())

        assertEquals(1, dataStore.loadLoans().size)
        assertEquals(1, dataStore.loadCollectionPeriods().size)
        assertEquals(1, dataStore.loadFundLedger().size)
    }

    @Test
    fun fieldOfficerCannotDeleteAnyAdminOrOfficerEnteredData() {
        seedMixedAdminAndOfficerRecords()

        AuthSession.currentUserProfile = UserProfile(
            id = "officer_1",
            userId = "officer_1",
            displayName = "মাঠ কর্মকর্তা ১",
            role = UserRole.FIELD_OFFICER.name
        )
        val viewModel = MainViewModel(EkotaRepository(context), "officer_1")

        var memberErr: String? = null
        var savingsErr: String? = null
        var loanErr: String? = null
        var instErr: String? = null
        var fineErr: String? = null
        var ledgerErr: String? = null
        var resetErr: String? = null

        viewModel.deleteMember("m1", onError = { memberErr = it })
        viewModel.deleteSavingsRecord("s_officer", onError = { savingsErr = it })
        viewModel.deleteLoan("l1", onError = { loanErr = it })
        viewModel.deleteLoanInstallment("i_officer", onError = { instErr = it })
        viewModel.deleteFine("f1", onError = { fineErr = it })
        viewModel.deleteLedgerEntry("led1", onError = { ledgerErr = it })
        viewModel.resetAllDataToZero(onSuccess = {}, onError = { resetErr = it })

        assertFalse(memberErr.isNullOrBlank())
        assertFalse(savingsErr.isNullOrBlank())
        assertFalse(loanErr.isNullOrBlank())
        assertFalse(instErr.isNullOrBlank())
        assertFalse(fineErr.isNullOrBlank())
        assertFalse(ledgerErr.isNullOrBlank())
        assertFalse(resetErr.isNullOrBlank())

        // Every Admin and Field Officer entry must remain untouched
        assertEquals(2, viewModel.members.value.size)
        assertEquals(2, viewModel.savingsRecords.value.size)
        assertEquals(1, viewModel.loans.value.size)
        assertEquals(2, viewModel.loanInstallments.value.size)
        assertEquals(1, viewModel.fines.value.size)
        assertEquals(1, viewModel.fundLedger.value.size)
        assertEquals(2, dataStore.loadSavings().size)
    }

    @Test
    fun adminPreviewingOfficerViewCannotDeleteUntilInAdminSettingsMode() {
        seedMixedAdminAndOfficerRecords()

        AuthSession.currentUserProfile = UserProfile(
            id = "admin_nitai",
            userId = "admin_nitai",
            displayName = "নিতাই",
            role = UserRole.ADMIN.name
        )
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")
        viewModel.switchUserRole(UserRole.FIELD_OFFICER)

        var err: String? = null
        viewModel.deleteSavingsRecord("s_admin", onError = { err = it })
        assertFalse(err.isNullOrBlank())
        assertEquals(2, viewModel.savingsRecords.value.size)
    }

    @Test
    fun adminCanSelectivelyDeleteEntriesFromSettingsAndPreserveRemainingRecords() {
        seedMixedAdminAndOfficerRecords()

        AuthSession.currentUserProfile = UserProfile(
            id = "admin_nitai",
            userId = "admin_nitai",
            displayName = "নিতাই",
            role = UserRole.ADMIN.name
        )
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")

        var deletedSavings = false
        var deletedFine = false
        viewModel.deleteSavingsRecord("s_officer", onSuccess = { deletedSavings = true })
        viewModel.deleteFine("f1", onSuccess = { deletedFine = true })

        assertTrue(deletedSavings)
        assertTrue(deletedFine)

        // Only targeted items removed; all other Admin & Officer entries preserved across reloads
        assertEquals(listOf("s_admin"), viewModel.savingsRecords.value.map { it.id })
        assertTrue(viewModel.fines.value.isEmpty())
        assertEquals(2, viewModel.members.value.size)
        assertEquals(1, viewModel.loans.value.size)
        assertEquals(2, viewModel.loanInstallments.value.size)
        assertEquals(1, viewModel.fundLedger.value.size)

        // Verify persisted on disk
        val reloadedStore = LocalDataStore(context)
        assertEquals(listOf("s_admin"), reloadedStore.loadSavings().map { it.id })
        assertTrue(reloadedStore.loadFines().isEmpty())
        assertEquals(2, reloadedStore.loadMembers().size)
    }

    private fun seedMixedAdminAndOfficerRecords() {
        dataStore.saveMembers(
            listOf(
                Member(id = "m1", memberSerial = "1", name = "রহিম", createdBy = "admin_nitai"),
                Member(id = "m2", memberSerial = "2", name = "করিম", createdBy = "officer_1")
            )
        )
        dataStore.saveSavings(
            listOf(
                SavingsRecord(
                    id = "s_admin",
                    memberId = "m1",
                    memberName = "রহিম",
                    collectionDate = "14/11/26",
                    amount = 100.0,
                    isPaid = true,
                    status = TransactionStatus.APPROVED.name,
                    createdBy = "admin_nitai"
                ),
                SavingsRecord(
                    id = "s_officer",
                    memberId = "m2",
                    memberName = "করিম",
                    collectionDate = "14/11/26",
                    amount = 100.0,
                    isPaid = true,
                    status = TransactionStatus.PENDING.name,
                    officerName = "মাঠ কর্মকর্তা ১",
                    createdBy = "officer_1"
                )
            )
        )
        dataStore.saveLoans(
            listOf(
                Loan(id = "l1", memberId = "m1", memberName = "রহিম", principal = 3000.0, remainingLoan = 3600.0, createdBy = "admin_nitai")
            )
        )
        dataStore.saveLoanInstallments(
            listOf(
                LoanInstallment(id = "i_admin", loanId = "l1", memberId = "m1", memberName = "রহিম", amount = 300.0, status = TransactionStatus.APPROVED.name),
                LoanInstallment(id = "i_officer", loanId = "l1", memberId = "m1", memberName = "রহিম", amount = 300.0, status = TransactionStatus.PENDING.name)
            )
        )
        dataStore.saveFines(
            listOf(
                FineRecord(id = "f1", memberId = "m2", memberName = "করিম", fineType = FineType.SAVINGS.name, amount = 10.0)
            )
        )
        dataStore.saveFundLedger(
            listOf(
                FundLedgerEntry(id = "led1", date = "14/11/26", description = "মূল সঞ্চয়", moneyIn = 100.0, balance = 100.0)
            )
        )
    }
}
