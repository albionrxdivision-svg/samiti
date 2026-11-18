package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.cloud.GoogleDriveBackupManager
import com.example.data.cloud.GoogleDriveConnectionState
import com.example.data.local.LocalDataStore
import com.example.data.model.AuthSession
import com.example.data.model.FineType
import com.example.data.model.Member
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.EkotaRepository
import com.example.ui.MainViewModel
import com.example.ui.fines.FineSheetScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.EkotaSamitiTheme
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FineSheetAndGoogleDriveTest {

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
        GoogleDriveBackupManager.disconnect(context)
        AuthSession.currentUserProfile = UserProfile(
            id = "admin_nitai",
            userId = "admin_nitai",
            displayName = "নিতাই",
            role = UserRole.ADMIN.name
        )
    }

    @After
    fun tearDown() {
        GoogleDriveBackupManager.disconnect(context)
        AuthSession.currentUserProfile = null
        Dispatchers.resetMain()
    }

    @Test
    fun fineSheetMatchesSavingsSheetFormAndMatrixAndBlocksDuplicateSameDate() {
        val m1 = Member(id = "m1", memberSerial = "1", name = "Netai", weeklySavingsAmount = 100.0)
        dataStore.saveMembers(listOf(m1))
        dataStore.saveSelectedFineDate("18/11/26")
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")
        viewModel.setSelectedFineDate("18/11/26")

        composeTestRule.setContent {
            EkotaSamitiTheme {
                FineSheetScreen(
                    viewModel = viewModel,
                    onNavigateBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Both tabs present
        composeTestRule.onNodeWithTag("tab_fine_collection_form").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_fine_sheet").assertIsDisplayed()

        // Sticky table-style header matching Savings Sheet: SL | নাম | টাকা | জমা
        composeTestRule.onNodeWithTag("fine_list_header").assertIsDisplayed()
        composeTestRule.onNodeWithText("SL").assertIsDisplayed()
        composeTestRule.onNodeWithText("নাম").assertIsDisplayed()
        composeTestRule.onNodeWithText("টাকা").assertIsDisplayed()
        composeTestRule.onNodeWithText("জমা").assertIsDisplayed()

        // Member row shows "01", "Netai", editable amount and Submit button
        composeTestRule.onNodeWithTag("member_fine_row_m1").assertIsDisplayed()
        composeTestRule.onNodeWithText("01").assertIsDisplayed()
        composeTestRule.onNodeWithText("Netai").assertIsDisplayed()
        composeTestRule.onNodeWithTag("submit_member_fine_m1").assertIsDisplayed()

        // Submit fine -> locks button to "জমা ✓" (submitted_fine_badge_m1) and persists
        composeTestRule.onNodeWithTag("submit_member_fine_m1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("submitted_fine_badge_m1").assertIsDisplayed()
        assertEquals(1, viewModel.fines.value.size)
        assertEquals(10.0, viewModel.fines.value.first().amount, 0.01)

        // Duplicate same date & type submission is blocked
        var dupErr: String? = null
        viewModel.addManualFine(
            member = m1,
            fineType = FineType.SAVINGS,
            missedDate = "18/11/26",
            fineDate = "18/11/26",
            amount = 10.0,
            isPaid = false,
            onSuccess = {},
            onError = { dupErr = it }
        )
        assertTrue(dupErr != null && dupErr!!.contains("ইতিমধ্যেই"))

        // Switching to tab 1 displays FineMatrixTable with pinned SL & নাম
        composeTestRule.onNodeWithTag("tab_fine_sheet").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("মোট টাকা =").assertIsDisplayed()
        composeTestRule.onNodeWithText("Netai").assertIsDisplayed()
    }

    @Test
    fun selectedFineDatePersistsAcrossViewModelRestart() {
        dataStore.saveSelectedFineDate("19/11/26")
        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")
        assertEquals("19/11/26", viewModel.selectedFineDate.value)

        viewModel.setSelectedFineDate("26/11/26")
        val restartedVm = MainViewModel(EkotaRepository(context), "admin_nitai")
        assertEquals("26/11/26", restartedVm.selectedFineDate.value)
    }

    @Test
    fun googleDriveBackupManagerPersistsConnectionAndSettingsOpensDriveDialog() {
        // Verify GoogleDriveBackupManager persists state across reads
        GoogleDriveBackupManager.saveConnectionState(
            context,
            GoogleDriveConnectionState(
                isConnected = true,
                accountEmail = "nitai.samiti@gmail.com",
                accountName = "নিতাই",
                folderId = "folder_123",
                lastBackupTimeMillis = 1731900000000L,
                lastBackupFileName = "ekota_samiti_backup_20261118.json",
                lastBackupFileId = "file_abc",
                autoBackupEnabled = true
            )
        )
        val loaded = GoogleDriveBackupManager.loadConnectionState(context)
        assertTrue(loaded.isConnected)
        assertEquals("nitai.samiti@gmail.com", loaded.accountEmail)
        assertEquals("folder_123", loaded.folderId)
        assertTrue(loaded.autoBackupEnabled)

        GoogleDriveBackupManager.disconnect(context)
        assertFalse(GoogleDriveBackupManager.loadConnectionState(context).isConnected)

        val viewModel = MainViewModel(EkotaRepository(context), "admin_nitai")
        composeTestRule.setContent {
            EkotaSamitiTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onSignOut = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Scroll LazyColumn to Google Drive Data Storage row inside Settings and open dialog
        composeTestRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_google_drive_item"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_google_drive_item")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("google_drive_storage_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("drive_auto_backup_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("connect_google_drive_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("saf_save_to_drive_button").performScrollTo().assertIsDisplayed()
    }
}
