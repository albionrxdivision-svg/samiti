package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalDataStore
import com.example.data.model.AuthSession
import com.example.data.model.FundLedgerEntry
import com.example.data.model.LedgerType
import com.example.data.model.Loan
import com.example.data.model.LoanInstallment
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.EkotaRepository
import com.example.ui.MainViewModel
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.ledger.DepositLedgerScreen
import com.example.ui.loans.LoanManagementScreen
import com.example.ui.members.MemberManagementScreen
import com.example.ui.savings.WeeklySavingsScreen
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
        composeTestRule.onNodeWithTag("daily_hisab_list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("daily_hisab_card_11/11/26"))
        composeTestRule.waitForIdle()
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

    @Test
    fun collectionDateAddAndUpdatePersistAcrossRestart() {
        val viewModel1 = MainViewModel(EkotaRepository(context), "admin_nitai")
        var addErr: String? = null
        viewModel1.addCollectionDate(
            dateStr = "12/11/2026",
            monthYear = "সাপ্তাহিক সঞ্চয়",
            onSuccess = {},
            onError = { addErr = it }
        )
        assertEquals(null, addErr)
        assertEquals("12/11/26", viewModel1.selectedSavingsDate.value)

        // Simulate reopening screen / app restart
        val viewModel2 = MainViewModel(EkotaRepository(context), "admin_nitai")
        assertTrue(viewModel2.collectionPeriods.value.any { it.dateStr == "12/11/26" })

        var updateErr: String? = null
        viewModel2.updateCollectionDate(
            oldDate = "12/11/26",
            newDate = "14/11/2026",
            monthYear = "সাপ্তাহিক সঞ্চয়",
            onSuccess = {},
            onError = { updateErr = it }
        )
        assertEquals(null, updateErr)

        val viewModel3 = MainViewModel(EkotaRepository(context), "admin_nitai")
        assertTrue(viewModel3.collectionPeriods.value.any { it.dateStr == "14/11/26" })
        assertTrue(viewModel3.collectionPeriods.value.none { it.dateStr == "12/11/26" })
    }

    @Test
    fun newMemberFormAutoCountsSlAndSavesNameAndTaka() {
        dataStore.saveMembers(
            listOf(
                Member(id = "m1", memberSerial = "1", name = "রহিম", weeklySavingsAmount = 100.0),
                Member(id = "m2", memberSerial = "2", name = "সালমা", weeklySavingsAmount = 150.0)
            )
        )
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")

        composeTestRule.setContent {
            EkotaSamitiTheme {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToSavings = {},
                    onNavigateToLoans = {},
                    onNavigateToMembers = {},
                    onNavigateToLedger = {},
                    onNavigateToApprovals = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dashboard_header_add_icon").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("header_add_member_item").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("member_form_serial_input").assertIsDisplayed().assertTextContains("3")
        composeTestRule.onNodeWithTag("member_form_name_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("member_form_taka_input").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("মোবাইল নম্বর").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("ঠিকানা").fetchSemanticsNodes().isEmpty())

        composeTestRule.onNodeWithTag("member_form_name_input").performTextInput("করিম মিয়া")
        composeTestRule.onNodeWithTag("member_form_taka_input").performTextReplacement("200")
        composeTestRule.onNodeWithTag("member_form_save_button").performClick()
        composeTestRule.waitForIdle()

        val saved = viewModel.members.value.firstOrNull { it.name == "করিম মিয়া" }
        assertEquals("3", saved?.memberSerial)
        assertEquals(200.0, saved?.weeklySavingsAmount ?: 0.0, 0.001)
        assertEquals("4", viewModel.suggestNextMemberSerial())
    }

    @Test
    fun memberManagementScreenRendersSlNameTakaHeaderedListAndOpensDetails() {
        dataStore.saveMembers(
            listOf(
                Member(id = "m1", memberSerial = "1", name = "রহিম", weeklySavingsAmount = 100.0),
                Member(id = "m2", memberSerial = "2", name = "সালমা", weeklySavingsAmount = 150.0)
            )
        )
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")

        composeTestRule.setContent {
            EkotaSamitiTheme {
                MemberManagementScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("member_list_header").assertIsDisplayed()
        composeTestRule.onNodeWithText("SL").assertIsDisplayed()
        composeTestRule.onNodeWithText("নাম").assertIsDisplayed()
        composeTestRule.onNodeWithText("টাকা").assertIsDisplayed()

        composeTestRule.onNodeWithTag("member_card_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("01").assertIsDisplayed()
        composeTestRule.onNodeWithText("রহিম").assertIsDisplayed()
        composeTestRule.onNodeWithText("100").assertIsDisplayed()

        composeTestRule.onNodeWithTag("member_card_2").assertIsDisplayed()
        composeTestRule.onNodeWithText("02").assertIsDisplayed()
        composeTestRule.onNodeWithText("সালমা").assertIsDisplayed()
        composeTestRule.onNodeWithText("150").assertIsDisplayed()

        composeTestRule.onNodeWithTag("member_card_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ক্রমিক নং:").assertIsDisplayed()
        composeTestRule.onNodeWithText("মোবাইল নম্বর:").assertIsDisplayed()
    }

    @Test
    fun weeklySavingsEntryListRenders01Netai100UnderSlNameTakaWithoutCards() {
        dataStore.saveMembers(
            listOf(
                Member(id = "m1", memberSerial = "1", name = "Netai", weeklySavingsAmount = 100.0)
            )
        )
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")

        composeTestRule.setContent {
            EkotaSamitiTheme {
                WeeklySavingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Top mode TabRow stays visible; redundant sub-toggle chips and bulk fill buttons are removed
        composeTestRule.onNodeWithTag("tab_collection_form").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_savings_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_batch_form_mode").assertDoesNotExist()
        composeTestRule.onNodeWithTag("toggle_single_form_mode").assertDoesNotExist()
        composeTestRule.onNodeWithTag("fill_defaults_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("clear_all_button").assertDoesNotExist()
        assertTrue(composeTestRule.onAllNodesWithText("সকল সদস্য ফর্ম").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("একক জমা ফর্ম").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("নির্ধারিত বসান").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("সব শূন্য (০)").fetchSemanticsNodes().isEmpty())

        composeTestRule.onNodeWithTag("weekly_savings_form_list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("savings_list_header"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("savings_list_header").assertIsDisplayed()
        composeTestRule.onNodeWithText("SL").assertIsDisplayed()
        composeTestRule.onNodeWithText("নাম").assertIsDisplayed()
        composeTestRule.onNodeWithText("টাকা").assertIsDisplayed()

        composeTestRule.onNodeWithTag("weekly_savings_form_list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("member_savings_card_m1"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("member_savings_card_m1").assertIsDisplayed()
        composeTestRule.onNodeWithText("01").assertIsDisplayed()
        composeTestRule.onNodeWithText("Netai").assertIsDisplayed()
        composeTestRule.onNodeWithTag("savings_amount_input_m1").assertIsDisplayed().assertTextContains("100")

        composeTestRule.onNodeWithTag("savings_amount_input_m1").performTextReplacement("200")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("savings_amount_input_m1").assertTextContains("200")

        // Name-wise Submit button beside Netai submits immediately and locks same date with Blue badge
        composeTestRule.onNodeWithTag("submit_member_savings_m1").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, viewModel.savingsRecords.value.size)
        assertEquals(200.0, viewModel.savingsRecords.value.first().amount, 0.001)
        composeTestRule.onNodeWithTag("submit_member_savings_m1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("submitted_member_badge_m1").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("weekly_savings_completed_badge").assertDoesNotExist()
        assertTrue(composeTestRule.onAllNodesWithText("এই সপ্তাহের জমা সম্পন্ন", substring = true).fetchSemanticsNodes().isEmpty())

        // Duplicate submission on the same date (even in dd/MM/yyyy format) must be rejected
        var dupError: String? = null
        val selectedDate = viewModel.selectedSavingsDate.value
        val altSameDate = if (selectedDate.length == 8) selectedDate.substring(0, 6) + "20" + selectedDate.substring(6) else selectedDate
        viewModel.recordSavings(
            member = Member(id = "m1", memberSerial = "1", name = "Netai", weeklySavingsAmount = 100.0),
            collectionDate = altSameDate,
            amount = 100.0,
            onSuccess = {},
            onError = { dupError = it }
        )
        assertTrue(dupError != null)
        assertEquals(1, viewModel.savingsRecords.value.size)
    }

    @Test
    fun weeklyLoanEntryListMatchesSavingsAndLocksSameDate() {
        dataStore.saveLoans(
            listOf(
                Loan(
                    id = "l1",
                    memberId = "m1",
                    memberSerial = "1",
                    memberName = "Netai",
                    principal = 3000.0,
                    interest = 600.0,
                    totalPayable = 3600.0,
                    weeklyInstallment = 300.0,
                    totalPaid = 0.0,
                    remainingLoan = 3600.0,
                    loanDate = "11/11/26"
                )
            )
        )
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")

        composeTestRule.setContent {
            EkotaSamitiTheme {
                LoanManagementScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tab_loan_collection_form").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_loan_sheet").assertIsDisplayed()

        composeTestRule.onNodeWithTag("weekly_loan_form_list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("loan_list_header"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("loan_list_header").assertIsDisplayed()
        composeTestRule.onNodeWithText("SL").assertIsDisplayed()
        composeTestRule.onNodeWithText("নাম").assertIsDisplayed()
        composeTestRule.onNodeWithText("টাকা").assertIsDisplayed()

        composeTestRule.onNodeWithTag("weekly_loan_form_list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("member_loan_row_l1"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("member_loan_row_l1").assertIsDisplayed()
        composeTestRule.onNodeWithText("01").assertIsDisplayed()
        composeTestRule.onNodeWithText("Netai").assertIsDisplayed()
        composeTestRule.onNodeWithTag("loan_amount_input_l1").assertIsDisplayed().assertTextContains("300")

        composeTestRule.onNodeWithTag("loan_amount_input_l1").performTextReplacement("400")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("loan_amount_input_l1").assertTextContains("400")

        composeTestRule.onNodeWithTag("submit_loan_installment_l1").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, viewModel.loanInstallments.value.size)
        assertEquals(400.0, viewModel.loanInstallments.value.first().amount, 0.001)
        composeTestRule.onNodeWithTag("submit_loan_installment_l1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("submitted_loan_badge_l1").assertIsDisplayed().assertIsNotEnabled()

        // Duplicate submission on the same date (even in dd/MM/yyyy format) must be rejected
        var dupError: String? = null
        val selectedDate = viewModel.selectedLoanDate.value
        val altSameDate = if (selectedDate.length == 8) selectedDate.substring(0, 6) + "20" + selectedDate.substring(6) else selectedDate
        viewModel.recordLoanInstallment(
            loan = viewModel.loans.value.first(),
            date = altSameDate,
            amount = 300.0,
            onSuccess = {},
            onError = { dupError = it }
        )
        assertTrue(dupError != null)
        assertEquals(1, viewModel.loanInstallments.value.size)

        // Switching to Loan Matrix Sheet tab displays pinned table cleanly
        composeTestRule.onNodeWithTag("tab_loan_sheet").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("মোট আদায়").assertIsDisplayed()
    }

    @Test
    fun allEnteredDataAndSelectedDatesPersistAcrossRestart() {
        val vm1 = MainViewModel(EkotaRepository(context), "admin_nitai")

        // 1. Add member
        var memErr: String? = null
        val member = Member(id = "mem_persist_1", memberSerial = "1", name = "নিতাই", weeklySavingsAmount = 100.0)
        vm1.addMember(member, onSuccess = {}, onError = { memErr = it })
        assertEquals(null, memErr)

        // 2. Add collection date and select dates for both savings and loans
        vm1.addCollectionDate("11/11/2026", "সাপ্তাহিক", onSuccess = {}, onError = {})
        vm1.setSelectedSavingsDate("11/11/26")
        vm1.setSelectedLoanDate("18/11/26")

        // 3. Record savings entry
        var savErr: String? = null
        vm1.recordSavings(member, "11/11/26", 200.0, onSuccess = {}, onError = { savErr = it })
        assertEquals(null, savErr)

        // 4. Create loan and record loan installment
        var loanErr: String? = null
        val loan = Loan(
            id = "loan_persist_1",
            memberId = member.id,
            memberSerial = member.memberSerial,
            memberName = member.name,
            loanDate = "11/11/26",
            principal = 1000.0,
            interest = 200.0,
            weeklyInstallment = 100.0
        )
        vm1.createLoan(loan, onSuccess = {}, onError = { loanErr = it })
        assertEquals(null, loanErr)

        var instErr: String? = null
        vm1.recordLoanInstallment(vm1.loans.value.first(), "18/11/26", 100.0, onSuccess = {}, onError = { instErr = it })
        assertEquals(null, instErr)

        // 5. Add manual fine and manual ledger entry
        var fineErr: String? = null
        vm1.addManualFine(
            member = member,
            fineType = com.example.data.model.FineType.SAVINGS,
            missedDate = "04/11/26",
            fineDate = "11/11/26",
            amount = 10.0,
            onSuccess = {},
            onError = { fineErr = it }
        )
        assertEquals(null, fineErr)

        var ledgerErr: String? = null
        vm1.addLedgerEntry("11/11/26", "বিবিধ আয়", moneyIn = 50.0, moneyOut = 0.0, onSuccess = {}, onError = { ledgerErr = it })
        assertEquals(null, ledgerErr)

        // 6. Update app settings
        val updatedSettings = com.example.data.model.AppSettings(
            orgName = "একতা শ্রমজীবী সমবায় সমিতি",
            currentMonthHeading = "মাসিক হিসাব - নভেম্বর ২০২৬",
            interestRatePercent = 15.0
        )
        vm1.updateSettings(updatedSettings, onSuccess = {}, onError = {})

        // Simulate full app restart with a fresh Repository + LocalDataStore + MainViewModel
        val vm2 = MainViewModel(EkotaRepository(context), "admin_nitai")

        assertEquals("11/11/26", vm2.selectedSavingsDate.value)
        assertEquals("18/11/26", vm2.selectedLoanDate.value)
        assertEquals(1, vm2.members.value.size)
        assertEquals("নিতাই", vm2.members.value.first().name)
        assertTrue(vm2.collectionPeriods.value.any { it.dateStr == "11/11/26" })
        assertEquals(1, vm2.savingsRecords.value.size)
        assertEquals(200.0, vm2.savingsRecords.value.first().amount, 0.001)
        assertEquals(1, vm2.loans.value.size)
        assertEquals(100.0, vm2.loans.value.first().totalPaid, 0.001)
        assertEquals(1, vm2.loanInstallments.value.size)
        assertEquals(100.0, vm2.loanInstallments.value.first().amount, 0.001)
        assertEquals(1, vm2.fines.value.size)
        assertEquals(10.0, vm2.fines.value.first().amount, 0.001)
        assertTrue(vm2.fundLedger.value.any { it.description == "বিবিধ আয়" && it.moneyIn == 50.0 })
        assertEquals("একতা শ্রমজীবী সমবায় সমিতি", vm2.appSettings.value.orgName)
        assertEquals(15.0, vm2.appSettings.value.interestRatePercent, 0.001)
    }
}
