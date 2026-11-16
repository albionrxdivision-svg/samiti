package com.example

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalDataStore
import com.example.data.model.AuthSession
import com.example.data.model.Loan
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.EkotaRepository
import com.example.ui.MainViewModel
import com.example.ui.components.SuccessPopupDialog
import com.example.ui.loans.LoanCard
import com.example.ui.savings.MemberSavingsInputCard
import com.example.ui.savings.SingleMemberSavingsDepositForm
import com.example.data.local.StorageQuotaManager
import com.example.ui.auth.AuthScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.StorageSettingsDialog
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

    @Test
    fun savingsCardDisplaysCollectorNameAndLoanCardHidesSubmitButtonAfterSubmission() {
        val m1 = Member(id = "m1", memberSerial = "1", name = "রহিম", weeklySavingsAmount = 100.0)
        val record = SavingsRecord(
            id = "m1_14-11-26",
            memberId = "m1",
            memberName = "রহিম",
            memberSerial = "1",
            collectionDate = "14/11/26",
            amount = 100.0,
            isPaid = true,
            officerId = "officer_karim",
            officerName = "করিম",
            status = TransactionStatus.APPROVED.name
        )
        val loan = Loan(
            id = "loan1",
            memberId = "m1",
            memberName = "রহিম",
            memberSerial = "1",
            principal = 3000.0,
            interest = 600.0,
            totalPayable = 3600.0,
            weeklyInstallment = 300.0,
            totalPaid = 300.0,
            remainingLoan = 3300.0,
            loanDate = "14/11/26"
        )

        composeTestRule.setContent {
            EkotaSamitiTheme(darkTheme = false) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    MemberSavingsInputCard(
                        member = m1,
                        amountText = "100",
                        existingRecord = record,
                        isSubmittedThisWeek = true,
                        onAmountChange = {}
                    )
                    LoanCard(
                        loan = loan,
                        installmentsCount = 1,
                        isAdmin = true,
                        isSubmittedThisWeek = true,
                        latestCollectorName = "করিম",
                        onCardClick = {},
                        onCollectInstallment = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("savings_submitted_by_m1", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("loan_installment_submitted_pill_loan1", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("collect_installment_btn_loan1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun adminSettingsScreenShowsAiAutoCallOption() {
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")

        composeTestRule.setContent {
            EkotaSamitiTheme(darkTheme = false) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onSignOut = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_ai_auto_call_item").assertIsDisplayed()
    }

    @Test
    fun singleMemberSavingsDepositFormSubmitsToCallbackAndLocksSameWeek() {
        val m1 = Member(id = "m1", memberSerial = "1", name = "রহিম", area = "উত্তর", weeklySavingsAmount = 100.0)
        var submittedAmount = 0.0
        var submittedDate = ""
        val recordsState = androidx.compose.runtime.mutableStateOf(emptyList<SavingsRecord>())

        composeTestRule.setContent {
            EkotaSamitiTheme(darkTheme = false) {
                SingleMemberSavingsDepositForm(
                    members = listOf(m1),
                    selectedDate = "15/11/26",
                    savingsRecords = recordsState.value,
                    isCloudAuthenticated = true,
                    isOnline = true,
                    isAdmin = true,
                    collectorName = "নিতাই",
                    onDateChange = {},
                    onSubmitDeposit = { member, colDate, amount ->
                        submittedDate = colDate
                        submittedAmount = amount
                        recordsState.value = listOf(
                            SavingsRecord(
                                id = "${member.id}_${colDate.replace("/", "-")}",
                                memberId = member.id,
                                memberName = member.name,
                                collectionDate = colDate,
                                amount = amount,
                                isPaid = amount > 0.0,
                                officerName = "নিতাই",
                                status = TransactionStatus.APPROVED.name
                            )
                        )
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("single_deposit_firestore_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("submit_single_savings_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals("15/11/26", submittedDate)
        assertEquals(100.0, submittedAmount, 0.01)
        composeTestRule.onNodeWithTag("single_deposit_completed_badge").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("submit_single_savings_button").assertDoesNotExist()
    }

    @Test
    fun storageSettingsDialogAllowsSelecting100GbAndSavingBackup() {
        StorageQuotaManager.setConfiguredLimitBytes(context, StorageQuotaManager.STORAGE_100_MB)
        var updatedLimit = 0L

        composeTestRule.setContent {
            EkotaSamitiTheme(darkTheme = false) {
                StorageSettingsDialog(
                    canEditQuota = true,
                    backupJsonProvider = { """{"membersCount":1,"savingsRecordsCount":1}""" },
                    onLimitUpdated = { updatedLimit = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("storage_settings_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("storage_option_100gb").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(StorageQuotaManager.STORAGE_100_GB, updatedLimit)
        assertEquals(StorageQuotaManager.STORAGE_100_GB, StorageQuotaManager.getConfiguredLimitBytes(context))

        composeTestRule.onNodeWithTag("save_local_backup_archive_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        val archives = StorageQuotaManager.listBackupArchives(context)
        assertTrue(archives.isNotEmpty())
        archives.forEach { StorageQuotaManager.deleteBackupArchive(context, it.fileName) }
    }

    @Test
    fun authScreenDoesNotExposeAdminCredentialsOrGoogleSyncSection() {
        composeTestRule.setContent {
            EkotaSamitiTheme(darkTheme = false) {
                AuthScreen(onAuthSuccess = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("username_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("password_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_button").performScrollTo().assertIsDisplayed()

        // Admin credentials (Nitai/Nemai) and Cloud Sync section must NOT be shown on Login screen
        composeTestRule.onNodeWithText("Nitai", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Nemai", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("google_sign_in_button").assertDoesNotExist()
    }
}
