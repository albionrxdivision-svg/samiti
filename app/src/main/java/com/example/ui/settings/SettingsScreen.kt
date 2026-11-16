package com.example.ui.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.StorageQuotaManager
import com.example.data.model.AppSettings
import com.example.data.model.AuthSession
import com.example.data.model.FineRecord
import com.example.data.model.FineType
import com.example.data.model.FundLedgerEntry
import com.example.data.model.Loan
import com.example.data.model.LoanInstallment
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import com.example.data.model.UserRole
import com.example.ui.MainViewModel
import com.example.ui.auth.GoogleSignInButton
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import com.example.util.PasswordChangeResult
import com.example.util.PasswordManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.appSettings.collectAsState()
    val role by viewModel.userRole.collectAsState()
    val members by viewModel.members.collectAsState()
    val savingsRecords by viewModel.savingsRecords.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val loanInstallments by viewModel.loanInstallments.collectAsState()
    val fines by viewModel.fines.collectAsState()
    val fundLedger by viewModel.fundLedger.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val aiCallUiState by viewModel.aiCallUiState.collectAsState()

    var showOrgSettingsDialog by remember { mutableStateOf(false) }
    var showRoleSwitcherDialog by remember { mutableStateOf(false) }
    var showApkManageDialog by remember { mutableStateOf(false) }
    var showOfflineSyncDialog by remember { mutableStateOf(false) }
    var showStorageSettingsDialog by remember { mutableStateOf(false) }
    var showAiAutoCallDialog by remember { mutableStateOf(false) }
    var configuredStorageLimitBytes by remember {
        mutableLongStateOf(StorageQuotaManager.getConfiguredLimitBytes(context))
    }
    val storageLimitLabel = StorageQuotaManager.formatBytesBn(configuredStorageLimitBytes)

    var backupJsonDialog by remember { mutableStateOf<String?>(null) }
    var showChangeOwnPasswordDialog by remember { mutableStateOf(false) }
    var showAdminOfficerPasswordDialog by remember { mutableStateOf(false) }
    var showDataDeleteManagerDialog by remember { mutableStateOf(false) }
    var showResetDataDialog by remember { mutableStateOf(false) }
    var isResettingData by remember { mutableStateOf(false) }

    val passwordManager = remember { PasswordManager.forContext(context) }
    val isRealAdmin = AuthSession.currentUserProfile?.role == UserRole.ADMIN.name
    val isAdminView = role == UserRole.ADMIN

    val activeDisplayName = AuthSession.currentUserProfile?.displayName
        ?: Firebase.auth.currentUser?.displayName
        ?: if (isRealAdmin) "নিতাই (অ্যাডমিন)" else "মাঠ কর্মকর্তা"
    val activeEmail = Firebase.auth.currentUser?.email
        ?: AuthSession.currentUserProfile?.email
        ?: "অজানা"
    val syncSummary = if (syncStatus.isOnline) {
        "অনলাইন (স্বয়ংক্রিয় সিঙ্ক) • অপেক্ষমাণ: ${BengaliUtils.toBengaliDigits(syncStatus.pendingCount)}টি"
    } else {
        "অফলাইন ক্যাশ সক্রিয় • অপেক্ষমাণ: ${BengaliUtils.toBengaliDigits(syncStatus.pendingCount)}টি"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isAdminView) "সেটিংস ও ব্যাকআপ" else "অ্যাকাউন্ট ও প্রোফাইল",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HeaderSlateNavy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ekotaSilverBackgroundBrush())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isAdminView) {
                // SECTION 1: Organization, AI Auto-Call & App Settings (Admin Only)
                item {
                    SettingsSectionTitle("সমিতি, AI ও সাধারণ সেটিংস")
                    SettingsListCard {
                        SettingsListRow(
                            title = "সমিতির সাধারণ সেটিংস",
                            subtitle = "${settings.orgName} • সুদ ${BengaliUtils.toBengaliDigits(settings.interestRatePercent.toInt())}%",
                            icon = Icons.Default.Business,
                            iconTint = CardEmeraldGradient.first(),
                            testTagName = "settings_item_org",
                            onClick = { showOrgSettingsDialog = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        SettingsListRow(
                            title = "AI অটো কল ও ভয়েস অ্যাসিস্ট্যান্ট",
                            subtitle = "বকেয়া যাচাই করে স্বয়ংক্রিয় কল তালিকা ও বাংলা ভয়েস",
                            icon = Icons.Default.SupportAgent,
                            iconTint = CardPurpleGradient.first(),
                            trailingBadge = "Gemini AI",
                            trailingBadgeColor = CardPurpleGradient.first(),
                            testTagName = "settings_ai_auto_call_item",
                            onClick = { showAiAutoCallDialog = true }
                        )
                        if (isRealAdmin) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            SettingsListRow(
                                title = "প্যানেল ভিউ পরিবর্তন",
                                subtitle = "অ্যাডমিন প্যানেল ও মাঠ কর্মকর্তা প্যানেল দেখুন",
                                icon = Icons.Default.SwitchAccount,
                                iconTint = CardBlueGradient.first(),
                                trailingBadge = "অ্যাডমিন",
                                trailingBadgeColor = CardBlueGradient.first(),
                                testTagName = "settings_item_role",
                                onClick = { showRoleSwitcherDialog = true }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        val savedUrl = settings.appDownloadUrl.trim()
                        SettingsListRow(
                            title = "মোবাইল অ্যাপ ডাউনলোড (APK)",
                            subtitle = savedUrl.ifBlank { "অ্যাপ ডাউনলোড লিংক সেট বা শেয়ার করুন" },
                            icon = Icons.Default.PhoneAndroid,
                            iconTint = CardTealGradient.first(),
                            trailingBadge = if (savedUrl.isNotBlank()) "লিংক সক্রিয়" else null,
                            trailingBadgeColor = CardTealGradient.first(),
                            testTagName = "settings_item_apk",
                            onClick = { showApkManageDialog = true }
                        )
                    }
                }

                // SECTION 2: Password & Security
                item {
                    SettingsSectionTitle("পাসওয়ার্ড ও নিরাপত্তা")
                    SettingsListCard {
                        SettingsListRow(
                            title = if (isRealAdmin) "অ্যাডমিন পাসওয়ার্ড পরিবর্তন" else "নিজের পাসওয়ার্ড পরিবর্তন",
                            subtitle = "অ্যাকাউন্ট: $activeDisplayName",
                            icon = Icons.Default.LockReset,
                            iconTint = CardOrangeGradient.first(),
                            testTagName = "change_own_password_button",
                            onClick = { showChangeOwnPasswordDialog = true }
                        )
                        if (isRealAdmin) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            SettingsListRow(
                                title = "মাঠ কর্মকর্তার পাসওয়ার্ড সেট",
                                subtitle = "মাঠ কর্মকর্তাদের লগইন পাসওয়ার্ড নির্ধারণ করুন",
                                icon = Icons.Default.ManageAccounts,
                                iconTint = CardPinkGradient.first(),
                                testTagName = "admin_change_officer_password_button",
                                onClick = { showAdminOfficerPasswordDialog = true }
                            )
                        }
                    }
                }

                // SECTION 3: Offline Cache, Cloud Sync, Backup & Admin Data Control
                item {
                    SettingsSectionTitle("ডাটা, সিঙ্ক ও ব্যাকআপ")
                    SettingsListCard(modifier = Modifier.testTag("admin_data_delete_card")) {
                        SettingsListRow(
                            title = "অফলাইন ক্যাশ ও ক্লাউড সিঙ্ক",
                            subtitle = syncSummary,
                            icon = if (syncStatus.isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            iconTint = if (syncStatus.isOnline) EmeraldGreenPrimary else ekotaWarningTextColor(),
                            trailingBadge = if (syncStatus.isOnline) "অনলাইন" else "অফলাইন",
                            trailingBadgeColor = if (syncStatus.isOnline) ekotaSuccessTextColor() else ekotaWarningTextColor(),
                            testTagName = "settings_offline_sync_card",
                            onClick = { showOfflineSyncDialog = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        SettingsListRow(
                            title = "ডাটা স্টোরেজ ও ব্যাকআপ ভল্ট",
                            subtitle = "সর্বোচ্চ ১০০ GB পর্যন্ত ডাটা সংরক্ষণ (সক্রিয়: $storageLimitLabel)",
                            icon = Icons.Default.Storage,
                            iconTint = CardTealGradient.first(),
                            trailingBadge = storageLimitLabel,
                            trailingBadgeColor = EmeraldGreenPrimary,
                            testTagName = "settings_storage_limit_item",
                            onClick = { showStorageSettingsDialog = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        SettingsListRow(
                            title = "তথ্য ব্যাকআপ (JSON Backup)",
                            subtitle = "সদস্য, সঞ্চয়, ঋণ ও লেজারের JSON ব্যাকআপ তৈরি করুন",
                            icon = Icons.Default.CloudDownload,
                            iconTint = NavyBlueSecondary,
                            testTagName = "settings_item_backup",
                            onClick = {
                                backupJsonDialog = generateBackupJson(members, savingsRecords, loans, fundLedger)
                            }
                        )
                        if (isRealAdmin) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            SettingsListRow(
                                title = "এন্ট্রি ডাটা সুরক্ষা ও ডিলিট",
                                subtitle = "সদস্য ${BengaliUtils.toBengaliDigits(members.size)} • সঞ্চয় ${BengaliUtils.toBengaliDigits(savingsRecords.size)} • ঋণ ${BengaliUtils.toBengaliDigits(loans.size)} (শুধুমাত্র অ্যাডমিন)",
                                icon = Icons.Default.DeleteSweep,
                                iconTint = ekotaDangerTextColor(),
                                testTagName = "open_data_delete_manager_button",
                                onClick = { showDataDeleteManagerDialog = true }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            SettingsListRow(
                                title = "সব ডাটা জিরো / রিসেট (Reset)",
                                subtitle = if (isResettingData) "রিসেট হচ্ছে..." else "সকল হিসাব ০ করুন (অ্যাডমিন আইডি ও পাসওয়ার্ড সহ)",
                                icon = Icons.Default.RestartAlt,
                                iconTint = ekotaDangerTextColor(),
                                titleColor = ekotaDangerTextColor(),
                                testTagName = "reset_all_data_button",
                                onClick = { if (!isResettingData) showResetDataDialog = true }
                            )
                        }
                    }
                }
            } else {
                // FIELD OFFICER MOBILE VIEW (Internal Admin settings hidden)
                item {
                    SettingsSectionTitle("মাঠ কর্মকর্তা প্রোফাইল ও সিঙ্ক")
                    SettingsListCard {
                        SettingsListRow(
                            title = "মাঠ কর্মকর্তা অ্যাকাউন্ট",
                            subtitle = "অভ্যন্তরীণ সেটিংস, ব্যাকআপ এবং ডিলিট/রিসেট অ্যাডমিনের জন্য সংরক্ষিত।",
                            icon = Icons.Default.Lock,
                            iconTint = CardPurpleGradient.first(),
                            testTagName = "officer_restricted_settings_notice",
                            onClick = null
                        )
                        if (isRealAdmin) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            SettingsListRow(
                                title = "প্যানেল ভিউ পরিবর্তন",
                                subtitle = "বর্তমান: মাঠ কর্মকর্তা ভিউ (অ্যাডমিন ভিউতে ফিরে যান)",
                                icon = Icons.Default.SwitchAccount,
                                iconTint = CardBlueGradient.first(),
                                trailingBadge = "মাঠ কর্মকর্তা",
                                trailingBadgeColor = CardBlueGradient.first(),
                                testTagName = "settings_item_role",
                                onClick = { showRoleSwitcherDialog = true }
                            )
                        } else {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            SettingsListRow(
                                title = "নিজের পাসওয়ার্ড পরিবর্তন",
                                subtitle = "অ্যাকাউন্ট: $activeDisplayName",
                                icon = Icons.Default.LockReset,
                                iconTint = CardOrangeGradient.first(),
                                testTagName = "change_own_password_button",
                                onClick = { showChangeOwnPasswordDialog = true }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        SettingsListRow(
                            title = "অফলাইন ক্যাশ ও ক্লাউড সিঙ্ক",
                            subtitle = syncSummary,
                            icon = if (syncStatus.isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            iconTint = if (syncStatus.isOnline) EmeraldGreenPrimary else ekotaWarningTextColor(),
                            trailingBadge = if (syncStatus.isOnline) "অনলাইন" else "অফলাইন",
                            trailingBadgeColor = if (syncStatus.isOnline) ekotaSuccessTextColor() else ekotaWarningTextColor(),
                            testTagName = "settings_offline_sync_card",
                            onClick = { showOfflineSyncDialog = true }
                        )
                    }
                }
            }

            // SECTION: Account Info & Sign Out (Both Admin and Field Officer)
            item {
                SettingsSectionTitle("অ্যাকাউন্ট তথ্য")
                SettingsListCard {
                    SettingsListRow(
                        title = activeDisplayName,
                        subtitle = "ইমেইল: $activeEmail",
                        icon = Icons.Default.AccountCircle,
                        iconTint = CardTealGradient.first(),
                        onClick = null
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    SettingsListRow(
                        title = "লগ আউট করুন (Sign Out)",
                        subtitle = "বর্তমান অ্যাকাউন্ট থেকে বের হয়ে লগইন স্ক্রিনে যান",
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        iconTint = DangerRed,
                        titleColor = ekotaDangerTextColor(),
                        testTagName = "settings_sign_out_button",
                        onClick = onSignOut
                    )
                }
            }
        }
    }

    if (showOrgSettingsDialog && isAdminView) {
        OrgSettingsDialog(
            initialSettings = settings,
            onDismiss = { showOrgSettingsDialog = false },
            onSave = { updated ->
                viewModel.updateSettings(
                    settings = updated,
                    onSuccess = {
                        showOrgSettingsDialog = false
                        Toast.makeText(context, "সেটিংস সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
    }

    if (showRoleSwitcherDialog && isRealAdmin) {
        RoleSwitcherDialog(
            currentRole = role,
            onSelectRole = { newRole ->
                viewModel.switchUserRole(newRole)
                showRoleSwitcherDialog = false
            },
            onDismiss = { showRoleSwitcherDialog = false }
        )
    }

    if (showApkManageDialog && isAdminView) {
        ApkManagementDialog(
            settings = settings,
            isAdmin = role == UserRole.ADMIN,
            onDismiss = { showApkManageDialog = false },
            onSaveUrl = { normalizedUrl, onDone ->
                viewModel.updateSettings(
                    settings.copy(appDownloadUrl = normalizedUrl),
                    onSuccess = {
                        onDone(true, if (normalizedUrl.isBlank()) "ডাউনলোড লিংক মুছে ফেলা হয়েছে" else "অ্যাপ ডাউনলোড লিংক সংরক্ষিত হয়েছে")
                    },
                    onError = { err -> onDone(false, err) }
                )
            }
        )
    }

    if (showOfflineSyncDialog) {
        OfflineCloudSyncDialog(
            syncStatus = syncStatus,
            storageLimitLabel = storageLimitLabel,
            onOpenStorageSettings = {
                showOfflineSyncDialog = false
                showStorageSettingsDialog = true
            },
            onTriggerSync = { onResult ->
                viewModel.triggerManualSync(onResult)
            },
            onDismiss = { showOfflineSyncDialog = false }
        )
    }

    if (showStorageSettingsDialog) {
        StorageSettingsDialog(
            canEditQuota = true,
            backupJsonProvider = { generateBackupJson(members, savingsRecords, loans, fundLedger) },
            onLimitUpdated = { newLimitBytes ->
                configuredStorageLimitBytes = newLimitBytes
            },
            onDismiss = { showStorageSettingsDialog = false }
        )
    }

    if (showAiAutoCallDialog && isAdminView) {
        AiAutoCallDialog(
            orgName = settings.orgName,
            aiCallUiState = aiCallUiState,
            onGeneratePlan = { instruction ->
                viewModel.generateAiCallPlan(instruction)
            },
            onDismiss = {
                showAiAutoCallDialog = false
                viewModel.resetAiCallState()
            }
        )
    }

    backupJsonDialog?.let { json ->
        AlertDialog(
            onDismissRequest = { backupJsonDialog = null },
            title = { Text("ডেটা ব্যাকআপ প্রস্তুত", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("সমিতির সকল সদস্য, সঞ্চয়, ঋণ এবং লেজার ডেটা ব্যাকআপ JSON ফরম্যাটে তৈরি হয়েছে:")
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 190.dp)
                    ) {
                        Text(
                            text = json.take(600) + if (json.length > 600) "... [truncated]" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            StorageQuotaManager.saveBackupToLocalArchive(context, json)
                                .onSuccess { saved ->
                                    Toast.makeText(
                                        context,
                                        "ফোনের স্টোরেজে সেভ হয়েছে (${StorageQuotaManager.formatBytesBn(saved.sizeBytes)})",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .onFailure { err ->
                                    Toast.makeText(context, err.message ?: "সংরক্ষণ করা যায়নি", Toast.LENGTH_LONG).show()
                                }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_backup_to_device_vault_button")
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ফোনের স্টোরেজে সেভ করুন ($storageLimitLabel ভল্ট)")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Ekota Samiti Backup", json)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "ক্লিপবোর্ডে কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                        backupJsonDialog = null
                    }
                ) {
                    Text("কপি করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { backupJsonDialog = null }) {
                    Text("বন্ধ করুন")
                }
            }
        )
    }

    if (showChangeOwnPasswordDialog) {
        val currentSessionRole = if (isRealAdmin) UserRole.ADMIN else UserRole.FIELD_OFFICER
        val currentOfficerName = AuthSession.currentUserProfile?.displayName ?: "মাঠ কর্মকর্তা ১"
        ChangeOwnPasswordDialog(
            accountLabel = if (isRealAdmin) "অ্যাডমিন অ্যাকাউন্ট" else "মাঠ কর্মকর্তা ($currentOfficerName)",
            onDismiss = { showChangeOwnPasswordDialog = false },
            onSave = { currentPw, newPw, confirmPw ->
                when (val result = passwordManager.changeOwnPassword(
                    role = currentSessionRole,
                    officerUsername = currentOfficerName,
                    currentPassword = currentPw,
                    newPassword = newPw,
                    confirmPassword = confirmPw
                )) {
                    is PasswordChangeResult.Success -> {
                        showChangeOwnPasswordDialog = false
                        Toast.makeText(
                            context,
                            "পাসওয়ার্ড সফলভাবে পরিবর্তন হয়েছে! পরবর্তী লগইনে নতুন পাসওয়ার্ড ব্যবহার করুন।",
                            Toast.LENGTH_LONG
                        ).show()
                        null
                    }
                    is PasswordChangeResult.Error -> result.message
                }
            }
        )
    }

    if (showAdminOfficerPasswordDialog && isRealAdmin) {
        AdminOfficerPasswordDialog(
            knownOfficers = passwordManager.getKnownOfficers(),
            onDismiss = { showAdminOfficerPasswordDialog = false },
            onSave = { officerName, newPw, confirmPw, applyDefault ->
                when (val result = passwordManager.adminSetOfficerPassword(
                    officerUsername = officerName,
                    newPassword = newPw,
                    confirmPassword = confirmPw,
                    setAsDefaultForNewOfficers = applyDefault
                )) {
                    is PasswordChangeResult.Success -> {
                        showAdminOfficerPasswordDialog = false
                        val successMsg = if (applyDefault) {
                            "নতুন সকল মাঠ কর্মকর্তার ডিফল্ট পাসওয়ার্ড সংরক্ষিত হয়েছে"
                        } else {
                            "'$officerName'-এর পাসওয়ার্ড সফলভাবে পরিবর্তন হয়েছে"
                        }
                        Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show()
                        null
                    }
                    is PasswordChangeResult.Error -> result.message
                }
            }
        )
    }

    if (showDataDeleteManagerDialog && isRealAdmin && isAdminView) {
        AdminDataDeleteManagerDialog(
            members = members,
            savings = savingsRecords,
            loans = loans,
            installments = loanInstallments,
            fines = fines,
            ledger = fundLedger,
            passwordManager = passwordManager,
            onDeleteMember = { id, onOk, onErr -> viewModel.deleteMember(id, onOk, onErr) },
            onDeleteSavings = { id, onOk, onErr -> viewModel.deleteSavingsRecord(id, onOk, onErr) },
            onDeleteLoan = { id, onOk, onErr -> viewModel.deleteLoan(id, onOk, onErr) },
            onDeleteInstallment = { id, onOk, onErr -> viewModel.deleteLoanInstallment(id, onOk, onErr) },
            onDeleteFine = { id, onOk, onErr -> viewModel.deleteFine(id, onOk, onErr) },
            onDeleteLedger = { id, onOk, onErr -> viewModel.deleteLedgerEntry(id, onOk, onErr) },
            onDismiss = { showDataDeleteManagerDialog = false }
        )
    }

    if (showResetDataDialog && isRealAdmin && isAdminView) {
        AdminResetDataPermissionDialog(
            isResetting = isResettingData,
            onDismiss = { if (!isResettingData) showResetDataDialog = false },
            onConfirmReset = { adminId, adminPassword, onDialogError ->
                if (!passwordManager.verifyAdminLogin(adminId, adminPassword)) {
                    onDialogError("অ্যাডমিনের লগইন আইডি বা পাসওয়ার্ড সঠিক নয়! অনুগ্রহ করে সঠিক তথ্য দিন।")
                    return@AdminResetDataPermissionDialog
                }
                isResettingData = true
                viewModel.resetAllDataToZero(
                    onSuccess = {
                        isResettingData = false
                        showResetDataDialog = false
                        Toast.makeText(
                            context,
                            "সব ডাটা সফলভাবে জিরো (০) ও রিসেট করা হয়েছে",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onError = { err ->
                        isResettingData = false
                        onDialogError(err)
                    }
                )
            }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = ekotaSectionTitleColor(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsListCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsListRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailingBadge: String? = null,
    trailingBadgeColor: Color = MaterialTheme.colorScheme.primary,
    testTagName: String? = null,
    onClick: (() -> Unit)?
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 60.dp)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .let { if (testTagName != null) it.testTag(testTagName) else it }
        .padding(horizontal = 14.dp, vertical = 12.dp)

    Row(
        modifier = baseModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!trailingBadge.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = trailingBadgeColor.copy(alpha = 0.14f)
            ) {
                Text(
                    text = trailingBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = trailingBadgeColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun OrgSettingsDialog(
    initialSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var orgName by remember(initialSettings) { mutableStateOf(initialSettings.orgName) }
    var subtitle by remember(initialSettings) { mutableStateOf(initialSettings.subtitle) }
    var interestRate by remember(initialSettings) { mutableStateOf(initialSettings.interestRatePercent.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("সমিতির সাধারণ সেটিংস", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = orgName,
                    onValueChange = { orgName = it },
                    label = { Text("সমিতির নাম") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("উপশিরোনাম") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = interestRate,
                    onValueChange = { interestRate = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("সাধারণ ঋণের সুদের হার (%)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialSettings.copy(
                            orgName = orgName.ifBlank { initialSettings.orgName },
                            subtitle = subtitle,
                            interestRatePercent = interestRate.toDoubleOrNull() ?: initialSettings.interestRatePercent
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenPrimary, contentColor = Color.White)
            ) {
                Text("সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
private fun RoleSwitcherDialog(
    currentRole: UserRole,
    onSelectRole: (UserRole) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("প্যানেল ভিউ পরিবর্তন", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "বর্তমান ভিউ: ${if (currentRole == UserRole.ADMIN) "অ্যাডমিন প্যানেল (সবকিছু এডিট ও ডিলিট অনুমোদিত)" else "মাঠ কর্মকর্তা প্যানেল (অভ্যন্তরীণ সেটিংস লুকানো)"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSelectRole(UserRole.ADMIN) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentRole == UserRole.ADMIN) EmeraldGreenPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentRole == UserRole.ADMIN) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("অ্যাডমিন", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                    Button(
                        onClick = { onSelectRole(UserRole.FIELD_OFFICER) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentRole == UserRole.FIELD_OFFICER) NavyBlueSecondary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (currentRole == UserRole.FIELD_OFFICER) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("মাঠ কর্মকর্তা", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        }
    )
}

@Composable
private fun ApkManagementDialog(
    settings: AppSettings,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onSaveUrl: (String, (Boolean, String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val savedUrl = settings.appDownloadUrl.trim()
    var appDownloadUrlInput by remember(settings) { mutableStateOf(settings.appDownloadUrl) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("মোবাইল অ্যাপ ডাউনলোড (APK)", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (savedUrl.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openDownloadUrl(context, savedUrl) }
                            .testTag("apk_link_preview_box")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = savedUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = ekotaNavyTextColor(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Button(
                        onClick = { openDownloadUrl(context, savedUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("download_mobile_app_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenPrimary, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ডাউনলোড ও ইন্সটল করুন", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Ekota App APK Link", savedUrl))
                                Toast.makeText(context, "ডাউনলোড লিংক কপি হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_apk_link_button")
                        ) {
                            Text("লিংক কপি", maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = { shareAppDownloadLink(context, settings.orgName, savedUrl) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_apk_link_button")
                        ) {
                            Text("শেয়ার করুন", maxLines = 1, softWrap = false)
                        }
                    }
                } else {
                    Text(
                        text = "এখনো মোবাইল অ্যাপ (APK) ডাউনলোড লিংক যুক্ত করা হয়নি। নিচে ডাউনলোড লিংক সংরক্ষণ করুন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isAdmin) {
                    HorizontalDivider()
                    OutlinedTextField(
                        value = appDownloadUrlInput,
                        onValueChange = { appDownloadUrlInput = it },
                        label = { Text("APK ডাউনলোড লিংক (URL)") },
                        placeholder = { Text("https://.../ekota-samiti.apk") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apk_download_url_input")
                    )
                    Button(
                        onClick = {
                            val normalized = normalizeUrl(appDownloadUrlInput)
                            if (appDownloadUrlInput.isNotBlank() && normalized == null) {
                                Toast.makeText(context, "সঠিক ওয়েব লিংক দিন (https:// দিয়ে শুরু)", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSaving = true
                            onSaveUrl(normalized ?: "") { ok, msg ->
                                isSaving = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_apk_url_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlueSecondary, contentColor = Color.White)
                    ) {
                        Text(if (isSaving) "সংরক্ষণ হচ্ছে..." else "লিংক সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        }
    )
}

@Composable
fun OfflineCloudSyncDialog(
    syncStatus: com.example.data.repository.SyncStatusState,
    storageLimitLabel: String,
    onOpenStorageSettings: () -> Unit,
    onTriggerSync: ((Boolean, String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("অফলাইন ক্যাশ ও ক্লাউড সিঙ্ক", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (syncStatus.isOnline) "নেটওয়ার্ক: অনলাইন (স্বয়ংক্রিয় সিঙ্ক সক্রিয়)" else "নেটওয়ার্ক: অফলাইন (ফোনের ক্যাশে সংরক্ষিত হচ্ছে)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (syncStatus.isOnline) ekotaSuccessTextColor() else ekotaWarningTextColor()
                )
                Text(
                    text = "অপেক্ষমাণ অফলাইন এন্ট্রি: ${BengaliUtils.toBengaliDigits(syncStatus.pendingCount)} টি\nলোকাল অফলাইন ক্যাশ: $storageLimitLabel সক্রিয়",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onOpenStorageSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("sync_dialog_open_storage_button")
                ) {
                    Icon(Icons.Default.Storage, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("স্টোরেজ সীমা পরিবর্তন করুন ($storageLimitLabel)")
                }
                if (syncStatus.isCloudAuthenticated) {
                    Button(
                        onClick = {
                            onTriggerSync { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = !syncStatus.isSyncing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("settings_manual_sync_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenPrimary, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (syncStatus.isSyncing) "সিঙ্ক হচ্ছে..." else "এখনই ক্লাউডে সিঙ্ক করুন",
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "Google লগইন করলে Firestore অফলাইন ক্যাশ চালু থাকবে — নেট ছাড়াও এন্ট্রি করা যাবে এবং নেট এলে অটো সিঙ্ক হবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GoogleSignInButton(
                        label = "Google দিয়ে ক্লাউড সিঙ্ক চালু করুন",
                        onAuthSuccess = {
                            Toast.makeText(context, "ক্লাউড সিঙ্ক ও Firestore অফলাইন ক্যাশ চালু হয়েছে!", Toast.LENGTH_LONG).show()
                        },
                        onAuthError = { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        }
    )
}

private enum class DeleteCategory(val label: String) {
    SAVINGS("সঞ্চয়"),
    MEMBERS("সদস্য"),
    LOANS("ঋণ"),
    INSTALLMENTS("কিস্তি"),
    FINES("জরিমানা"),
    LEDGER("লেজার")
}

private data class DeletableRecordItem(
    val id: String,
    val category: DeleteCategory,
    val title: String,
    val subtitle: String,
    val badgeText: String,
    val isPending: Boolean = false
)

@Composable
private fun DataCountChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun translateStatusLabel(status: String): String = when (status) {
    TransactionStatus.PENDING.name -> "অপেক্ষমাণ (মাঠকর্মী)"
    TransactionStatus.REJECTED.name -> "প্রত্যাখ্যাত"
    else -> "অনুমোদিত"
}

@Composable
fun AdminDataDeleteManagerDialog(
    members: List<Member>,
    savings: List<SavingsRecord>,
    loans: List<Loan>,
    installments: List<LoanInstallment>,
    fines: List<FineRecord>,
    ledger: List<FundLedgerEntry>,
    passwordManager: PasswordManager,
    onDeleteMember: (String, () -> Unit, (String) -> Unit) -> Unit,
    onDeleteSavings: (String, () -> Unit, (String) -> Unit) -> Unit,
    onDeleteLoan: (String, () -> Unit, (String) -> Unit) -> Unit,
    onDeleteInstallment: (String, () -> Unit, (String) -> Unit) -> Unit,
    onDeleteFine: (String, () -> Unit, (String) -> Unit) -> Unit,
    onDeleteLedger: (String, () -> Unit, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(DeleteCategory.SAVINGS) }
    var targetToDelete by remember { mutableStateOf<DeletableRecordItem?>(null) }
    var sessionVerified by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val itemsForCategory = remember(selectedCategory, members, savings, loans, installments, fines, ledger) {
        when (selectedCategory) {
            DeleteCategory.SAVINGS -> savings.map { s ->
                DeletableRecordItem(
                    id = s.id,
                    category = DeleteCategory.SAVINGS,
                    title = "${s.memberName} (${BengaliUtils.toBengaliDigits(s.memberSerial)})",
                    subtitle = "তারিখ: ${s.collectionDate} • ${BengaliUtils.formatTaka(s.amount)} • এন্ট্রি: ${s.officerName.ifBlank { "অ্যাডমিন" }}",
                    badgeText = translateStatusLabel(s.status),
                    isPending = s.status == TransactionStatus.PENDING.name
                )
            }
            DeleteCategory.MEMBERS -> members.map { m ->
                DeletableRecordItem(
                    id = m.id,
                    category = DeleteCategory.MEMBERS,
                    title = "${m.name} (${BengaliUtils.toBengaliDigits(m.memberSerial)})",
                    subtitle = "সাপ্তাহিক: ${BengaliUtils.formatTaka(m.weeklySavingsAmount)} • এলাকা: ${m.area.ifBlank { m.mobile.ifBlank { "-" } }}",
                    badgeText = if (m.isActive) "সক্রিয়" else "নিষ্ক্রিয়"
                )
            }
            DeleteCategory.LOANS -> loans.map { l ->
                DeletableRecordItem(
                    id = l.id,
                    category = DeleteCategory.LOANS,
                    title = "${l.memberName} (${BengaliUtils.toBengaliDigits(l.memberSerial)})",
                    subtitle = "তারিখ: ${l.loanDate} • আসল: ${BengaliUtils.formatTaka(l.principal)} • বাকি: ${BengaliUtils.formatTaka(l.remainingLoan)}",
                    badgeText = if (l.status == "PAID") "পরিশোধিত" else "চলমান"
                )
            }
            DeleteCategory.INSTALLMENTS -> installments.map { inst ->
                DeletableRecordItem(
                    id = inst.id,
                    category = DeleteCategory.INSTALLMENTS,
                    title = "${inst.memberName} (কিস্তি #${inst.serialNumber})",
                    subtitle = "তারিখ: ${inst.installmentDate} • ${if (inst.isMissed) "বাকি (মিস)" else BengaliUtils.formatTaka(inst.amount)}",
                    badgeText = translateStatusLabel(inst.status),
                    isPending = inst.status == TransactionStatus.PENDING.name
                )
            }
            DeleteCategory.FINES -> fines.map { f ->
                val typeLabel = if (f.fineType == FineType.SAVINGS.name) "সঞ্চয় জরিমানা" else "ঋণ জরিমানা"
                DeletableRecordItem(
                    id = f.id,
                    category = DeleteCategory.FINES,
                    title = "${f.memberName} ($typeLabel)",
                    subtitle = "বকেয়া: ${f.missedDate} • ধার্য: ${f.fineDate} • ${BengaliUtils.formatTaka(f.amount)}",
                    badgeText = if (f.isPaid) "আদায় ✓" else "বাকি"
                )
            }
            DeleteCategory.LEDGER -> ledger.map { e ->
                val amtText = if (e.moneyIn > 0) "জমা: ${BengaliUtils.formatTaka(e.moneyIn)}" else "খরচ: ${BengaliUtils.formatTaka(e.moneyOut)}"
                DeletableRecordItem(
                    id = e.id,
                    category = DeleteCategory.LEDGER,
                    title = e.description.ifBlank { "লেজার এন্ট্রি" },
                    subtitle = "তারিখ: ${e.date} • $amtText • জের: ${BengaliUtils.formatTaka(e.balance)}",
                    badgeText = e.type
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp)
                .testTag("admin_data_delete_manager_dialog")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "এন্ট্রি ডাটা ম্যানেজার (অ্যাডমিন)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "শুধুমাত্র অ্যাডমিন এখান থেকে নির্বাচিত এন্ট্রি ডিলিট করতে পারবেন",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeleteCategory.entries.forEach { category ->
                        val count = when (category) {
                            DeleteCategory.SAVINGS -> savings.size
                            DeleteCategory.MEMBERS -> members.size
                            DeleteCategory.LOANS -> loans.size
                            DeleteCategory.INSTALLMENTS -> installments.size
                            DeleteCategory.FINES -> fines.size
                            DeleteCategory.LEDGER -> ledger.size
                        }
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = {
                                Text(
                                    text = "${category.label} (${BengaliUtils.toBengaliDigits(count)})",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldGreenPrimary,
                                selectedLabelColor = Color.White,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("delete_category_chip_${category.name.lowercase()}")
                        )
                    }
                }

                if (itemsForCategory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${selectedCategory.label} তালিকায় কোনো এন্ট্রি নেই",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(itemsForCategory, key = { "${it.category.name}_${it.id}" }) { item ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (item.isPending) ekotaBadgeWarningBg() else MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = item.badgeText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (item.isPending) ekotaWarningTextColor() else MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = { targetToDelete = item },
                                        enabled = item.id.isNotBlank() && !isDeleting,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = ekotaDangerTextColor()
                                        ),
                                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.55f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("delete_record_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "ডিলিট",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "ডিলিট",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("বন্ধ করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    targetToDelete?.let { item ->
        AdminDeleteSingleRecordPermissionDialog(
            item = item,
            requiresCredentials = !sessionVerified,
            isDeleting = isDeleting,
            onDismiss = { if (!isDeleting) targetToDelete = null },
            onConfirmDelete = { adminId, adminPassword, keepUnlocked, onDialogError ->
                if (!sessionVerified && !passwordManager.verifyAdminLogin(adminId, adminPassword)) {
                    onDialogError("অ্যাডমিনের লগইন আইডি বা পাসওয়ার্ড সঠিক নয়!")
                    return@AdminDeleteSingleRecordPermissionDialog
                }
                if (keepUnlocked) {
                    sessionVerified = true
                }
                isDeleting = true
                val onOk = {
                    isDeleting = false
                    targetToDelete = null
                    Toast.makeText(
                        context,
                        "${item.category.label} এন্ট্রি সফলভাবে ডিলিট হয়েছে",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                val onErr: (String) -> Unit = { err ->
                    isDeleting = false
                    onDialogError(err)
                }
                when (item.category) {
                    DeleteCategory.MEMBERS -> onDeleteMember(item.id, onOk, onErr)
                    DeleteCategory.SAVINGS -> onDeleteSavings(item.id, onOk, onErr)
                    DeleteCategory.LOANS -> onDeleteLoan(item.id, onOk, onErr)
                    DeleteCategory.INSTALLMENTS -> onDeleteInstallment(item.id, onOk, onErr)
                    DeleteCategory.FINES -> onDeleteFine(item.id, onOk, onErr)
                    DeleteCategory.LEDGER -> onDeleteLedger(item.id, onOk, onErr)
                }
            }
        )
    }
}

@Composable
private fun AdminDeleteSingleRecordPermissionDialog(
    item: DeletableRecordItem,
    requiresCredentials: Boolean,
    isDeleting: Boolean,
    onDismiss: () -> Unit,
    onConfirmDelete: (adminId: String, adminPassword: String, keepUnlocked: Boolean, onError: (String) -> Unit) -> Unit
) {
    var adminIdInput by remember { mutableStateOf("") }
    var adminPasswordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var keepUnlocked by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = "এন্ট্রি ডিলিট নিশ্চিতকরণ",
                fontWeight = FontWeight.Bold,
                color = ekotaDangerTextColor()
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = ekotaBadgeDangerBg(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ধরন: ${item.category.label}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ekotaDangerTextColor()
                        )
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (requiresCredentials) {
                    OutlinedTextField(
                        value = adminIdInput,
                        onValueChange = {
                            adminIdInput = it
                            errorMessage = null
                        },
                        label = { Text("অ্যাডমিন লগইন আইডি") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("single_delete_admin_id_input")
                    )

                    OutlinedTextField(
                        value = adminPasswordInput,
                        onValueChange = {
                            adminPasswordInput = it
                            errorMessage = null
                        },
                        label = { Text("অ্যাডমিন পাসওয়ার্ড") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("single_delete_admin_password_input")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showPassword = !showPassword }
                    ) {
                        Checkbox(checked = showPassword, onCheckedChange = { showPassword = it })
                        Text(
                            text = "পাসওয়ার্ড দেখান",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { keepUnlocked = !keepUnlocked }
                    ) {
                        Checkbox(checked = keepUnlocked, onCheckedChange = { keepUnlocked = it })
                        Text(
                            text = "এই সেশনে বারবার পাসওয়ার্ড চাইবেন না",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Text(
                        text = "অ্যাডমিন যাচাই সম্পন্ন আছে। নিশ্চিত করলে এই এন্ট্রিটি স্থায়ীভাবে ডিলিট হয়ে যাবে।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (requiresCredentials && (adminIdInput.isBlank() || adminPasswordInput.isBlank())) {
                        errorMessage = "অনুগ্রহ করে অ্যাডমিনের লগইন আইডি ও পাসওয়ার্ড দিন"
                    } else {
                        onConfirmDelete(adminIdInput, adminPasswordInput, keepUnlocked) { msg ->
                            errorMessage = msg
                        }
                    }
                },
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DangerRed,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_single_delete_button")
            ) {
                Text(
                    text = if (isDeleting) "ডিলিট হচ্ছে..." else "ডিলিট নিশ্চিত করুন",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text("বাতিল", maxLines = 1, softWrap = false)
            }
        }
    )
}

@Composable
fun ChangeOwnPasswordDialog(
    accountLabel: String,
    onDismiss: () -> Unit,
    onSave: (currentPassword: String, newPassword: String, confirmPassword: String) -> String?
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val transformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("পাসওয়ার্ড পরিবর্তন", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "অ্যাকাউন্ট: $accountLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        errorMessage = null
                    },
                    label = { Text("বর্তমান পাসওয়ার্ড") },
                    singleLine = true,
                    visualTransformation = transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("current_password_input")
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("নতুন পাসওয়ার্ড (কমপক্ষে ৩ অক্ষর)") },
                    singleLine = true,
                    visualTransformation = transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_password_input")
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("নতুন পাসওয়ার্ড নিশ্চিত করুন") },
                    singleLine = true,
                    visualTransformation = transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_password_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showPasswords = !showPasswords }
                ) {
                    Checkbox(
                        checked = showPasswords,
                        onCheckedChange = { showPasswords = it }
                    )
                    Text(
                        text = "পাসওয়ার্ড দেখান",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val err = onSave(currentPassword, newPassword, confirmPassword)
                    if (err != null) {
                        errorMessage = err
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreenPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("save_own_password_button")
            ) {
                Text("পরিবর্তন করুন", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
fun AdminOfficerPasswordDialog(
    knownOfficers: List<String>,
    onDismiss: () -> Unit,
    onSave: (officerName: String, newPassword: String, confirmPassword: String, applyDefault: Boolean) -> String?
) {
    var officerName by remember { mutableStateOf(knownOfficers.firstOrNull() ?: "মাঠ কর্মকর্তা ১") }
    var applyDefaultForAllNew by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val transformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("মাঠ কর্মকর্তার পাসওয়ার্ড সেট", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (knownOfficers.isNotEmpty() && !applyDefaultForAllNew) {
                    Text(
                        text = "দ্রুত নির্বাচন করুন:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        knownOfficers.forEach { officer ->
                            FilterChip(
                                selected = officerName.trim().equals(officer, ignoreCase = true),
                                onClick = {
                                    officerName = officer
                                    errorMessage = null
                                },
                                label = { Text(officer) }
                            )
                        }
                    }
                }

                if (!applyDefaultForAllNew) {
                    OutlinedTextField(
                        value = officerName,
                        onValueChange = {
                            officerName = it
                            errorMessage = null
                        },
                        label = { Text("মাঠ কর্মকর্তার আইডি / নাম") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("officer_name_for_pw_input")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { applyDefaultForAllNew = !applyDefaultForAllNew }
                ) {
                    Checkbox(
                        checked = applyDefaultForAllNew,
                        onCheckedChange = {
                            applyDefaultForAllNew = it
                            errorMessage = null
                        }
                    )
                    Text(
                        text = "সকল নতুন মাঠ কর্মকর্তার ডিফল্ট পাসওয়ার্ড পরিবর্তন করুন",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("নতুন পাসওয়ার্ড (কমপক্ষে ৩ অক্ষর)") },
                    singleLine = true,
                    visualTransformation = transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("officer_new_password_input")
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("নতুন পাসওয়ার্ড নিশ্চিত করুন") },
                    singleLine = true,
                    visualTransformation = transformation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("officer_confirm_password_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showPasswords = !showPasswords }
                ) {
                    Checkbox(
                        checked = showPasswords,
                        onCheckedChange = { showPasswords = it }
                    )
                    Text(
                        text = "পাসওয়ার্ড দেখান",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val err = onSave(officerName, newPassword, confirmPassword, applyDefaultForAllNew)
                    if (err != null) {
                        errorMessage = err
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavyBlueSecondary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("save_officer_password_button")
            ) {
                Text("সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
fun AdminResetDataPermissionDialog(
    isResetting: Boolean,
    onDismiss: () -> Unit,
    onConfirmReset: (adminId: String, adminPassword: String, onError: (String) -> Unit) -> Unit
) {
    var adminIdInput by remember { mutableStateOf("") }
    var adminPasswordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "অ্যাডমিন অনুমতি প্রয়োজন (Reset)",
                fontWeight = FontWeight.Bold,
                color = ekotaDangerTextColor()
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = ekotaBadgeDangerBg(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "সতর্কতা: এটি নিশ্চিত করলে সমিতির সকল সদস্য, সঞ্চয়, ঋণ, কিস্তি, জরিমানা এবং লেজার ডাটা সম্পূর্ণ মুছে শূন্য (৳০) হয়ে যাবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = ekotaDangerTextColor(),
                        modifier = Modifier.padding(10.dp)
                    )
                }

                OutlinedTextField(
                    value = adminIdInput,
                    onValueChange = {
                        adminIdInput = it
                        errorMessage = null
                    },
                    label = { Text("অ্যাডমিন লগইন আইডি") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_admin_id_input")
                )

                OutlinedTextField(
                    value = adminPasswordInput,
                    onValueChange = {
                        adminPasswordInput = it
                        errorMessage = null
                    },
                    label = { Text("অ্যাডমিন পাসওয়ার্ড") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_admin_password_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showPassword = !showPassword }
                ) {
                    Checkbox(
                        checked = showPassword,
                        onCheckedChange = { showPassword = it }
                    )
                    Text(
                        text = "পাসওয়ার্ড দেখান",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (adminIdInput.isBlank() || adminPasswordInput.isBlank()) {
                        errorMessage = "অনুগ্রহ করে অ্যাডমিনের লগইন আইডি ও পাসওয়ার্ড লিখুন"
                    } else {
                        onConfirmReset(adminIdInput, adminPasswordInput) { msg ->
                            errorMessage = msg
                        }
                    }
                },
                enabled = !isResetting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DangerRed,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_reset_all_data_button")
            ) {
                Text(
                    text = if (isResetting) "রিসেট হচ্ছে..." else "নিশ্চিত রিসেট করুন",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isResetting) {
                Text("বাতিল")
            }
        }
    )
}

fun normalizeUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else if (trimmed.contains('.') && !trimmed.contains(' ')) {
        "https://$trimmed"
    } else {
        return null
    }
    return try {
        val parsed = java.net.URI(withScheme)
        if (!parsed.host.isNullOrBlank()) withScheme else null
    } catch (_: Exception) {
        null
    }
}

fun openDownloadUrl(context: Context, url: String) {
    val normalized = normalizeUrl(url)
    if (normalized.isNullOrBlank()) {
        Toast.makeText(context, "সঠিক ডাউনলোড লিংক পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "লিংক খোলার জন্য কোনো ব্রাউজার পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "ডাউনলোড লিংক খোলা যায়নি", Toast.LENGTH_SHORT).show()
    }
}

fun shareAppDownloadLink(context: Context, orgName: String, url: String) {
    val normalized = normalizeUrl(url)
    if (normalized.isNullOrBlank()) return
    val shareText = "$orgName মোবাইল অ্যাপ ডাউনলোড ও ইন্সটল করতে নিচের লিংকে ক্লিক করুন:\n$normalized"
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "$orgName - মোবাইল অ্যাপ ডাউনলোড লিংক")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    try {
        context.startActivity(Intent.createChooser(sendIntent, "অ্যাপ লিংক শেয়ার করুন"))
    } catch (_: Exception) {
        Toast.makeText(context, "শেয়ার অপশন পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
    }
}

fun generateBackupJson(
    members: List<Member>,
    savings: List<SavingsRecord>,
    loans: List<Loan>,
    ledger: List<FundLedgerEntry>
): String {
    val root = JSONObject()
    root.put("organization", "যাদবপুর যুব সমাজ সমিতি")
    root.put("membersCount", members.size)
    root.put("savingsRecordsCount", savings.size)
    root.put("loansCount", loans.size)
    root.put("ledgerCount", ledger.size)
    root.put("exportDate", BengaliUtils.getCurrentDateStr())

    val membersArr = JSONArray()
    members.forEach { m ->
        membersArr.put(
            JSONObject().apply {
                put("id", m.id)
                put("memberSerial", m.memberSerial)
                put("name", m.name)
                put("mobile", m.mobile)
                put("area", m.area)
                put("weeklySavingsAmount", m.weeklySavingsAmount)
                put("isActive", m.isActive)
            }
        )
    }
    root.put("members", membersArr)

    val savingsArr = JSONArray()
    savings.forEach { s ->
        savingsArr.put(
            JSONObject().apply {
                put("id", s.id)
                put("memberId", s.memberId)
                put("memberName", s.memberName)
                put("collectionDate", s.collectionDate)
                put("amount", s.amount)
                put("status", s.status)
            }
        )
    }
    root.put("savings", savingsArr)

    val loansArr = JSONArray()
    loans.forEach { l ->
        loansArr.put(
            JSONObject().apply {
                put("id", l.id)
                put("memberId", l.memberId)
                put("memberName", l.memberName)
                put("loanDate", l.loanDate)
                put("principal", l.principal)
                put("totalPayable", l.totalPayable)
                put("totalPaid", l.totalPaid)
                put("remainingLoan", l.remainingLoan)
                put("status", l.status)
            }
        )
    }
    root.put("loans", loansArr)

    val ledgerArr = JSONArray()
    ledger.forEach { e ->
        ledgerArr.put(
            JSONObject().apply {
                put("id", e.id)
                put("date", e.date)
                put("description", e.description)
                put("moneyIn", e.moneyIn)
                put("moneyOut", e.moneyOut)
                put("balance", e.balance)
                put("type", e.type)
            }
        )
    }
    root.put("ledger", ledgerArr)

    return root.toString(2)
}
