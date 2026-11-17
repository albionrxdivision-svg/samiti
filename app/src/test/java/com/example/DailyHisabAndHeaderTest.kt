package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalDataStore
import com.example.data.model.AuthSession
import com.example.data.model.FundLedgerEntry
import com.example.data.model.LedgerType
import com.example.data.model.Loan
import com.example.data.model.LoanInstallment
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.EkotaRepository
import com.example.ui.MainViewModel
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.ledger.DepositLedgerScreen
import com.example.ui.theme.EkotaSamitiTheme
import com.example.util.DailyAccountCalculator
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
class DailyHisabAndHeaderTest {

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
            role = UserRole.ADMIN.name,
            isActive = true
        )
    }

    @After
    fun tearDown() {
        dataStore.clearAllData()
        AuthSession.currentUserProfile = null
        Dispatchers.resetMain()
    }

    @Test
    fun dailyCalculatorWithoutLoanOutKeepsFull400Deposit() {
        val savings = listOf(
            SavingsRecord(
                id = "s1",
                memberId = "m1",
                collectionDate = "11/11/26",
                amount = 200.0,
                isPaid = true,
                status = TransactionStatus.APPROVED.name
            )
        )
        val installments = listOf(
            LoanInstallment(
                id = "i1",
                loanId = "l1",
                installmentDate = "11/11/2026",
                amount = 200.0,
                isMissed = false,
                status = TransactionStatus.APPROVED.name
            )
        )

        val overview = DailyAccountCalculator.buildOverview(
            savingsRecords = savings,
            loanInstallments = installments,
            loans = emptyList(),
            fundLedger = emptyList()
        )

        assertEquals(1, overview.summariesNewestFirst.size)
        val day = overview.summariesNewestFirst.first()
        assertEquals("11/11/26", day.dateKey)
        assertEquals("১১/১১/২০২৬", day.displayDate)
        assertEquals(200.0, day.savingsTotal, 0.001)
        assertEquals(200.0, day.loanCollectedTotal, 0.001)
        assertEquals(400.0, day.grossCollection, 0.001)
        assertEquals(0.0, day.wentOutTotal, 0.001)
        assertEquals(400.0, day.netDeposit, 0.001)
        assertEquals(400.0, overview.currentNetBalance, 0.001)
    }

    @Test
    fun dailyCalculatorWith200LoanOutShowsWentOut200AndNet200() {
        val savings = listOf(
            SavingsRecord(
                id = "s1",
                memberId = "m1",
                collectionDate = "11/11/26",
                amount = 200.0,
                isPaid = true,
                status = TransactionStatus.APPROVED.name
            )
        )
        val installments = listOf(
            LoanInstallment(
                id = "i1",
                loanId = "l1",
                installmentDate = "11/11/26",
                amount = 200.0,
                isMissed = false,
                status = TransactionStatus.APPROVED.name
            )
        )
        val loans = listOf(
            Loan(
                id = "l2",
                memberId = "m2",
                loanDate = "11/11/2026",
                principal = 200.0
            )
        )
        // Duplicate auto ledger rows must NOT double count
        val autoLedger = listOf(
            FundLedgerEntry(
                id = "fl1",
                date = "11/11/26",
                moneyIn = 200.0,
                balance = 200.0,
                type = LedgerType.SAVINGS_COLLECTION.name
            ),
            FundLedgerEntry(
                id = "fl2",
                date = "11/11/26",
                moneyIn = 200.0,
                balance = 400.0,
                type = LedgerType.LOAN_INSTALLMENT.name
            ),
            FundLedgerEntry(
                id = "fl3",
                date = "11/11/26",
                moneyOut = 200.0,
                balance = 200.0,
                type = LedgerType.LOAN_DISBURSEMENT.name
            )
        )

        val overview = DailyAccountCalculator.buildOverview(
            savingsRecords = savings,
            loanInstallments = installments,
            loans = loans,
            fundLedger = autoLedger
        )

        assertEquals(1, overview.summariesNewestFirst.size)
        val day = overview.summariesNewestFirst.first()
        assertEquals(200.0, day.savingsTotal, 0.001)
        assertEquals(200.0, day.loanCollectedTotal, 0.001)
        assertEquals(200.0, day.wentOutTotal, 0.001)
        assertEquals(200.0, day.netDeposit, 0.001)
        assertEquals(200.0, overview.currentNetBalance, 0.001)
    }

    @Test
    fun dailyCalculatorAccumulatesAcrossMultipleDatesAndFiltersPendingOrMissed() {
        val savings = listOf(
            SavingsRecord(id = "s1", collectionDate = "11/11/26", amount = 200.0, isPaid = true, status = TransactionStatus.APPROVED.name),
            SavingsRecord(id = "s_pending", collectionDate = "11/11/26", amount = 500.0, isPaid = true, status = TransactionStatus.PENDING.name),
            SavingsRecord(id = "s2", collectionDate = "18/11/26", amount = 300.0, isPaid = true, status = TransactionStatus.APPROVED.name)
        )
        val installments = listOf(
            LoanInstallment(id = "i1", installmentDate = "11/11/26", amount = 200.0, isMissed = false, status = TransactionStatus.APPROVED.name),
            LoanInstallment(id = "i_missed", installmentDate = "11/11/26", amount = 200.0, isMissed = true, status = TransactionStatus.APPROVED.name),
            LoanInstallment(id = "i2", installmentDate = "18/11/26", amount = 100.0, isMissed = false, status = TransactionStatus.APPROVED.name)
        )
        val loans = listOf(
            Loan(id = "l1", loanDate = "11/11/26", principal = 200.0)
        )

        val overview = DailyAccountCalculator.buildOverview(savings, installments, loans, emptyList())
        assertEquals(2, overview.summariesNewestFirst.size)
        // Newest first: 18/11/26, then 11/11/26
        val newest = overview.summariesNewestFirst[0]
        val older = overview.summariesNewestFirst[1]

        assertEquals("18/11/26", newest.dateKey)
        assertEquals(400.0, newest.netDeposit, 0.001)
        assertEquals(600.0, newest.runningBalance, 0.001)

        assertEquals("11/11/26", older.dateKey)
        assertEquals(200.0, older.netDeposit, 0.001)
        assertEquals(200.0, older.runningBalance, 0.001)
        assertEquals(600.0, overview.currentNetBalance, 0.001)
    }

    @Test
    fun depositLedgerScreenRendersDateWiseSavingsLoanAndWentOutList() {
        dataStore.saveSavings(
            listOf(
                SavingsRecord(
                    id = "s1",
                    memberId = "m1",
                    collectionDate = "11/11/26",
                    amount = 200.0,
                    isPaid = true,
                    status = TransactionStatus.APPROVED.name
                )
            )
        )
        dataStore.saveLoanInstallments(
            listOf(
                LoanInstallment(
                    id = "i1",
                    loanId = "l1",
                    installmentDate = "11/11/26",
                    amount = 200.0,
                    isMissed = false,
                    status = TransactionStatus.APPROVED.name
                )
            )
        )
        dataStore.saveLoans(
            listOf(
                Loan(
                    id = "l2",
                    memberId = "m2",
                    loanDate = "11/11/26",
                    principal = 200.0
                )
            )
        )

        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")

        composeTestRule.setContent {
            EkotaSamitiTheme {
                DepositLedgerScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("top_add_ledger_entry_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("daily_hisab_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("daily_hisab_card_11/11/26").assertIsDisplayed()
        composeTestRule.onNodeWithText("তারিখ: ১১/১১/২০২৬").assertIsDisplayed()
        composeTestRule.onNodeWithText("বাইরে গেছে (ঋণ/খরচ)").assertIsDisplayed()
        composeTestRule.onNodeWithText("- ৳২০০").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("= ৳২০০").fetchSemanticsNodes().size >= 3)
    }

    @Test
    fun dashboardTopHeaderHasAddActionAndOpensInlineDialogWithoutExtraPage() {
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")
        var navigatedToRegPage = false

        composeTestRule.setContent {
            EkotaSamitiTheme {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToSavings = {},
                    onNavigateToLoans = {},
                    onNavigateToMembers = {},
                    onNavigateToMemberRegistration = { navigatedToRegPage = true },
                    onNavigateToLedger = {},
                    onNavigateToApprovals = {},
                    onNavigateToFines = {},
                    onOpenLogin = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dashboard_header_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_header_add_icon").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("header_add_member_item").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Inline dialog opens directly without navigating to MemberRegistrationScreen
        composeTestRule.onNodeWithText("নতুন সদস্য যোগ করুন").assertIsDisplayed()
        assertEquals(false, navigatedToRegPage)
    }
}
