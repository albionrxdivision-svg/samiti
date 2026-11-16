package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BackupManager
import com.example.data.local.LocalDataStore
import com.example.data.model.AppSettings
import com.example.data.model.AuthSession
import com.example.data.model.CollectionPeriod
import com.example.data.model.FineRecord
import com.example.data.model.FineType
import com.example.data.model.FundLedgerEntry
import com.example.data.model.LedgerType
import com.example.data.model.Loan
import com.example.data.model.LoanInstallment
import com.example.data.model.LoanStatus
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.EkotaRepository
import com.example.ui.MainViewModel
import com.example.ui.navigation.MainAppNavigation
import com.example.ui.settings.ApkManagementDialog
import com.example.ui.theme.EkotaSamitiTheme
import com.example.util.AppUpdateChecker
import com.example.util.AppUpdateInfo
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
import org.junit.Assert.assertNull
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
class AppUpdateAndBackupTest {

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
    fun appUpdateCheckerParsesGitHubTagsAndReleaseApkAssetsAccurately() {
        assertEquals(35, AppUpdateChecker.parseVersionCodeFromTag("v1.0.25"))
        assertEquals(12, AppUpdateChecker.parseVersionCodeFromTag("1.0.2"))
        assertEquals(42, AppUpdateChecker.parseVersionCodeFromTag("v42"))

        assertEquals("nitai/ekota-samiti", AppUpdateChecker.extractGitHubRepoSlug("nitai/ekota-samiti"))
        assertEquals(
            "nitai/ekota-samiti",
            AppUpdateChecker.extractGitHubRepoSlug("https://github.com/nitai/ekota-samiti/releases/latest")
        )
        assertEquals(
            "nitai/ekota-samiti",
            AppUpdateChecker.extractGitHubRepoSlug("https://api.github.com/repos/nitai/ekota-samiti/releases")
        )

        val releaseJson = """
            {
              "tag_name": "v1.0.25",
              "html_url": "https://github.com/nitai/ekota-samiti/releases/tag/v1.0.25",
              "body": "হিসাব ও অটো-আপডেট উন্নতি",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "Ekota-Samiti.apk",
                  "browser_download_url": "https://github.com/nitai/ekota-samiti/releases/download/v1.0.25/Ekota-Samiti.apk"
                }
              ]
            }
        """.trimIndent()

        val newer = AppUpdateChecker.parseGitHubReleaseJson(
            jsonString = releaseJson,
            currentVersionCode = 15,
            currentVersionName = "1.0.5"
        )
        assertNotNull(newer)
        assertEquals(35, newer!!.latestVersionCode)
        assertEquals("1.0.25", newer.latestVersionName)
        assertEquals(
            "https://github.com/nitai/ekota-samiti/releases/download/v1.0.25/Ekota-Samiti.apk",
            newer.downloadUrl
        )

        // Same or older version should not trigger update notice
        val notNewer = AppUpdateChecker.parseGitHubReleaseJson(
            jsonString = releaseJson,
            currentVersionCode = 35,
            currentVersionName = "1.0.25"
        )
        assertNull(notNewer)
    }

    @Test
    fun evaluateSettingsUpdateDetectsManualCloudVersionPush() {
        val settings = AppSettings(
            appDownloadUrl = "https://example.com/ekota.apk",
            latestVersionCode = 20,
            latestVersionName = "2.0",
            updateNotes = "নতুন ফিচার যুক্ত হয়েছে"
        )
        val update = AppUpdateChecker.evaluateSettingsUpdate(
            settings = settings,
            currentVersionCode = 2,
            currentVersionName = "1.1"
        )
        assertNotNull(update)
        assertEquals(20, update!!.latestVersionCode)
        assertEquals("2.0", update.latestVersionName)
        assertEquals("https://example.com/ekota.apk", update.downloadUrl)
    }

    @Test
    fun fullBackupExportAndRestoreSurvivesSimulatedUninstallWithoutDataLoss() {
        // 1. Seed user-entered records into local store
        val member = Member(
            id = "m_101",
            memberSerial = "101",
            name = "আব্দুল রহিম",
            mobile = "01711112222",
            address = "যাদবপুর",
            weeklySavingsAmount = 200.0,
            isActive = true
        )
        val saving = SavingsRecord(
            id = "m_101_14-11-26",
            memberId = "m_101",
            memberName = "আব্দুল রহিম",
            memberSerial = "101",
            collectionDate = "14/11/26",
            amount = 200.0,
            isPaid = true,
            status = TransactionStatus.APPROVED.name
        )
        val period = CollectionPeriod(id = "p_1", dateStr = "14/11/26", monthYear = "November 2026")
        val loan = Loan(
            id = "l_101",
            memberId = "m_101",
            memberName = "আব্দুল রহিম",
            memberSerial = "101",
            loanDate = "14/11/26",
            principal = 10000.0,
            interest = 2000.0,
            totalPayable = 12000.0,
            weeklyInstallment = 1200.0,
            totalPaid = 1200.0,
            remainingLoan = 10800.0,
            status = LoanStatus.ACTIVE.name
        )
        val installment = LoanInstallment(
            id = "inst_1",
            loanId = "l_101",
            memberId = "m_101",
            memberName = "আব্দুল রহিম",
            installmentDate = "21/11/26",
            amount = 1200.0,
            status = TransactionStatus.APPROVED.name
        )
        val fine = FineRecord(
            id = "fine_1",
            memberId = "m_101",
            memberName = "আব্দুল রহিম",
            fineDate = "21/11/26",
            missedDate = "14/11/26",
            fineType = FineType.SAVINGS.name,
            amount = 10.0,
            isPaid = true,
            status = TransactionStatus.APPROVED.name
        )
        val ledger = FundLedgerEntry(
            id = "led_1",
            date = "14/11/26",
            description = "সাপ্তাহিক সঞ্চয়",
            moneyIn = 200.0,
            balance = 200.0,
            type = LedgerType.SAVINGS_COLLECTION.name
        )
        val customSettings = AppSettings(
            orgName = "একতা সঞ্চয় সমিতি",
            interestRatePercent = 15.0,
            githubRepoSlug = "nitai/ekota-samiti"
        )

        dataStore.saveMembers(listOf(member))
        dataStore.saveSavings(listOf(saving))
        dataStore.saveCollectionPeriods(listOf(period))
        dataStore.saveLoans(listOf(loan))
        dataStore.saveLoanInstallments(listOf(installment))
        dataStore.saveFines(listOf(fine))
        dataStore.saveFundLedger(listOf(ledger))
        dataStore.saveAppSettings(customSettings)

        val vmBeforeUninstall = MainViewModel(EkotaRepository(context), "admin_nitai")
        val exportedJson = vmBeforeUninstall.exportFullBackupJson()

        // Sanity check BackupManager parser directly
        val parsed = BackupManager.parseBackupJson(exportedJson).getOrThrow()
        assertEquals(7, parsed.totalRecordsCount)
        assertEquals("একতা সঞ্চয় সমিতি", parsed.appSettings?.orgName)

        // 2. Simulate full uninstall + fresh reinstall (wipe local storage completely)
        dataStore.clearAllData()
        val vmAfterReinstall = MainViewModel(EkotaRepository(context), "admin_nitai")
        assertTrue(vmAfterReinstall.members.value.isEmpty())
        assertTrue(vmAfterReinstall.savingsRecords.value.isEmpty())
        assertTrue(vmAfterReinstall.loans.value.isEmpty())

        // 3. Field Officer cannot restore, only Admin
        vmAfterReinstall.switchUserRole(UserRole.FIELD_OFFICER)
        var officerError: String? = null
        vmAfterReinstall.restoreBackupFromJson(
            rawJson = exportedJson,
            onSuccess = {},
            onError = { officerError = it }
        )
        assertNotNull(officerError)

        // 4. Switch back to Admin and restore everything
        vmAfterReinstall.switchUserRole(UserRole.ADMIN)
        var restoredCount = 0
        var restoreError: String? = null
        vmAfterReinstall.restoreBackupFromJson(
            rawJson = exportedJson,
            onSuccess = { restoredCount = it },
            onError = { restoreError = it }
        )
        assertNull(restoreError)
        assertEquals(7, restoredCount)

        assertEquals(1, vmAfterReinstall.members.value.size)
        assertEquals("আব্দুল রহিম", vmAfterReinstall.members.value.first().name)
        assertEquals(1, vmAfterReinstall.savingsRecords.value.size)
        assertEquals(1, vmAfterReinstall.loans.value.size)
        assertEquals(1, vmAfterReinstall.loanInstallments.value.size)
        assertEquals(1, vmAfterReinstall.fines.value.size)
        assertEquals(1, vmAfterReinstall.fundLedger.value.size)
        assertEquals("একতা সঞ্চয় সমিতি", vmAfterReinstall.appSettings.value.orgName)
        assertEquals("nitai/ekota-samiti", vmAfterReinstall.appSettings.value.githubRepoSlug)
    }

    @Test
    fun bottomMenuBarShowsHisabInsteadOfLedgerWithCompactHeader() {
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")

        composeTestRule.setContent {
            EkotaSamitiTheme {
                MainAppNavigation(
                    viewModel = viewModel,
                    onSignOut = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("bottom_nav_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_ledger").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_ledger").assertTextContains("হিসাব")
        composeTestRule.onNodeWithText("হিসাব").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("লেজার").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("dashboard_header_card").assertIsDisplayed()
    }

    @Test
    fun apkManagementDialogDisplaysInstalledVersionAndUpdateBanner() {
        var checkTriggered = false
        val updateInfo = AppUpdateInfo(
            latestVersionCode = 99,
            latestVersionName = "2.5.0",
            downloadUrl = "https://github.com/nitai/ekota/releases/download/v2.5.0/Ekota-Samiti.apk",
            releaseNotes = "দ্রুত পারফরম্যান্স ও অটো-আপডেট",
            sourceLabel = "GitHub Release"
        )

        composeTestRule.setContent {
            EkotaSamitiTheme {
                ApkManagementDialog(
                    settings = AppSettings(
                        appDownloadUrl = "https://github.com/nitai/ekota/releases/latest",
                        githubRepoSlug = "nitai/ekota"
                    ),
                    isAdmin = true,
                    availableUpdate = updateInfo,
                    isCheckingUpdate = false,
                    onCheckUpdate = { checkTriggered = true },
                    onDismiss = {},
                    onSaveSettings = { _, onDone -> onDone(true, "saved") }
                )
            }
        }

        composeTestRule.onNodeWithTag("apk_dialog_update_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("apk_dialog_update_now_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("check_app_update_button").performScrollTo().performClick()
        assertTrue(checkTriggered)
        composeTestRule.onNodeWithTag("apk_github_repo_input").performScrollTo().assertIsDisplayed()
    }
}
