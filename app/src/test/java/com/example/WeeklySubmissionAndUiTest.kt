package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalDataStore
import com.example.data.model.AuthSession
import com.example.data.model.Loan
import com.example.data.model.Member
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.EkotaRepository
import com.example.ui.MainViewModel
import com.example.ui.components.SuccessPopupDialog
import com.example.ui.theme.EkotaSamitiTheme
import com.example.ui.theme.SilverBackgroundLight
import com.example.util.SavingsAndFineCalculator
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeeklySubmissionAndUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
        AuthSession.currentUserProfile = UserProfile(
            id = "admin_nitai",
            userId = "admin_nitai",
            displayName = "নিতাই",
            role = UserRole.ADMIN.name
        )
    }

    @After
    fun tearDown() {
        AuthSession.currentUserProfile = null
        Dispatchers.resetMain()
    }

    @Test
    fun isSameWeekUsesSaturdayThroughFridayWindow() {
        // 14/11/26 is Saturday, 20/11/26 is Friday of the same week
        assertTrue(SavingsAndFineCalculator.isSameWeek("14/11/26", "14/11/26"))
        assertTrue(SavingsAndFineCalculator.isSameWeek("14/11/26", "18/11/26"))
        assertTrue(SavingsAndFineCalculator.isSameWeek("14/11/26", "20/11/26"))

        // 21/11/26 is next Saturday -> new week
        assertFalse(SavingsAndFineCalculator.isSameWeek("14/11/26", "21/11/26"))
        // Consecutive Thursdays -> different weeks
        assertFalse(SavingsAndFineCalculator.isSameWeek("12/03/26", "19/03/26"))
    }

    @Test
    fun recordSavingsBlocksSecondSubmissionInSameWeekAndAllowsNextWeek() {
        val m1 = Member(id = "m1", memberSerial = "1", name = "রহিম", weeklySavingsAmount = 100.0)
        dataStore.saveMembers(listOf(m1))
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")

        var firstSuccess = false
        var firstError: String? = null
        viewModel.recordSavings(
            member = m1,
            collectionDate = "14/11/26",
            amount = 100.0,
            onSuccess = { firstSuccess = true },
            onError = { firstError = it }
        )
        assertTrue(firstSuccess)
        assertEquals(null, firstError)
        assertEquals(1, viewModel.savingsRecords.value.size)

        // Second submission in the same week (Wednesday 18/11/26) must fail
        var secondSuccess = false
        var secondError: String? = null
        viewModel.recordSavings(
            member = m1,
            collectionDate = "18/11/26",
            amount = 100.0,
            onSuccess = { secondSuccess = true },
            onError = { secondError = it }
        )
        assertFalse(secondSuccess)
        assertNotNull(secondError)
        assertEquals(1, viewModel.savingsRecords.value.size)

        // Submission in the following week (Saturday 21/11/26) must succeed
        var nextWeekSuccess = false
        viewModel.recordSavings(
            member = m1,
            collectionDate = "21/11/26",
            amount = 100.0,
            onSuccess = { nextWeekSuccess = true },
            onError = {}
        )
        assertTrue(nextWeekSuccess)
        assertEquals(2, viewModel.savingsRecords.value.size)
    }

    @Test
    fun submitWeeklySavingsBatchBlocksDuplicateWeekAndFiltersAlreadySubmittedMembers() {
        val m1 = Member(id = "m1", memberSerial = "1", name = "রহিম", weeklySavingsAmount = 100.0)
        val m2 = Member(id = "m2", memberSerial = "2", name = "করিম", weeklySavingsAmount = 100.0)
        dataStore.saveMembers(listOf(m1, m2))
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")

        // Submit m1 only on 14/11/26
        var firstCount = 0
        viewModel.submitWeeklySavingsBatch(
            collectionDate = "14/11/26",
            memberAmounts = mapOf("m1" to 100.0),
            onSuccess = { count, _ -> firstCount = count },
            onError = {}
        )
        assertEquals(1, firstCount)

        // Re-submitting m1 alone in the same week fails
        var dupError: String? = null
        viewModel.submitWeeklySavingsBatch(
            collectionDate = "16/11/26",
            memberAmounts = mapOf("m1" to 100.0),
            onSuccess = { _, _ -> },
            onError = { dupError = it }
        )
        assertNotNull(dupError)

        // Submitting both m1 and m2 on 16/11/26 records only m2
        var secondCount = 0
        viewModel.submitWeeklySavingsBatch(
            collectionDate = "16/11/26",
            memberAmounts = mapOf("m1" to 100.0, "m2" to 100.0),
            onSuccess = { count, _ -> secondCount = count },
            onError = {}
        )
        assertEquals(1, secondCount)
        assertEquals(2, viewModel.savingsRecords.value.size)
    }

    @Test
    fun loanCreationAndInstallmentBlockDoubleSubmissionInSameWeek() {
        val m1 = Member(id = "m1", memberSerial = "1", name = "রহিম", weeklySavingsAmount = 100.0)
        dataStore.saveMembers(listOf(m1))
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")

        val loan1 = Loan(
            memberId = "m1",
            memberName = "রহিম",
            memberSerial = "1",
            principal = 3000.0,
            interest = 600.0,
            totalPayable = 3600.0,
            weeklyInstallment = 300.0,
            loanDate = "14/11/26"
        )
        var loanCreated = false
        viewModel.createLoan(loan1, onSuccess = { loanCreated = true }, onError = {})
        assertTrue(loanCreated)
        assertEquals(1, viewModel.loans.value.size)

        // Second loan for the same member in the same week must fail
        var secondLoanErr: String? = null
        viewModel.createLoan(
            loan1.copy(loanDate = "18/11/26"),
            onSuccess = {},
            onError = { secondLoanErr = it }
        )
        assertNotNull(secondLoanErr)
        assertEquals(1, viewModel.loans.value.size)

        val savedLoan = viewModel.loans.value.first()
        var inst1Ok = false
        viewModel.recordLoanInstallment(savedLoan, "14/11/26", 300.0, onSuccess = { inst1Ok = true }, onError = {})
        assertTrue(inst1Ok)
        assertEquals(1, viewModel.loanInstallments.value.size)

        // Second installment in the same week must fail
        var instDupErr: String? = null
        viewModel.recordLoanInstallment(savedLoan, "19/11/26", 300.0, onSuccess = {}, onError = { instDupErr = it })
        assertNotNull(instDupErr)
        assertEquals(1, viewModel.loanInstallments.value.size)

        // Next week installment succeeds
        var instNextWeekOk = false
        viewModel.recordLoanInstallment(savedLoan, "21/11/26", 300.0, onSuccess = { instNextWeekOk = true }, onError = {})
        assertTrue(instNextWeekOk)
        assertEquals(2, viewModel.loanInstallments.value.size)
    }

    @Test
    fun silverBackgroundThemeAndSuccessPopupDialogRenderCleanly() {
        var dismissed = false
        var backgroundMatchesSilver = false

        composeTestRule.setContent {
            EkotaSamitiTheme(darkTheme = false) {
                backgroundMatchesSilver = MaterialTheme.colorScheme.background == SilverBackgroundLight
                SuccessPopupDialog(
                    message = "সঞ্চয় সফলভাবে সংরক্ষণ করা হয়েছে।",
                    subtitle = "মোট: ৳৫০০",
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        assertTrue(backgroundMatchesSilver)
        composeTestRule.onNodeWithTag("submission_success_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("সফল হয়েছে (Successful)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("submission_success_ok_button").performClick()
        assertTrue(dismissed)
    }
}
