package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.StorageQuotaManager
import com.example.data.model.UserRole
import com.example.ui.MainViewModel
import com.example.ui.ledger.AddLedgerEntryDialog
import com.example.ui.members.MemberFormDialog
import com.example.ui.settings.OfflineCloudSyncDialog
import com.example.ui.settings.StorageSettingsDialog
import com.example.ui.settings.openDownloadUrl
import com.example.ui.theme.*
import com.example.util.BengaliUtils

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToSavings: () -> Unit,
    onNavigateToLoans: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onNavigateToMemberRegistration: () -> Unit = onNavigateToMembers,
    onNavigateToLedger: () -> Unit,
    onNavigateToApprovals: () -> Unit,
    onNavigateToFines: () -> Unit = {},
    onOpenLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val role by viewModel.userRole.collectAsStateWithLifecycle()
    val userProfile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val updateBannerDismissed by viewModel.updateBannerDismissed.collectAsStateWithLifecycle()

    var showAccountLoginDialog by remember { mutableStateOf(false) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var showStorageSettingsDialog by remember { mutableStateOf(false) }
    var showHeaderAddMenu by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showAddLedgerDialog by remember { mutableStateOf(false) }
    var configuredStorageLimitBytes by remember {
        mutableLongStateOf(StorageQuotaManager.getConfiguredLimitBytes(context))
    }
    val storageLimitLabel = StorageQuotaManager.formatBytesBn(configuredStorageLimitBytes)
    var syncFeedbackMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = Color.Transparent,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_header_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(HeaderSlateNavy, Color(0xFF164E63), EmeraldGreenPrimary)
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.orgName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = settings.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                IconButton(
                                    onClick = { showHeaderAddMenu = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("dashboard_header_add_icon")
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White.copy(alpha = 0.22f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.42f)),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "নতুন যোগ করুন",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = showHeaderAddMenu,
                                    onDismissRequest = { showHeaderAddMenu = false },
                                    modifier = Modifier.testTag("dashboard_header_add_menu")
                                ) {
                                    if (role == UserRole.ADMIN) {
                                        DropdownMenuItem(
                                            text = { Text("নতুন সদস্য যোগ", fontWeight = FontWeight.SemiBold) },
                                            leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                                            modifier = Modifier.testTag("header_add_member_item"),
                                            onClick = {
                                                showHeaderAddMenu = false
                                                showAddMemberDialog = true
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("সঞ্চয় এন্ট্রি", fontWeight = FontWeight.SemiBold) },
                                        leadingIcon = { Icon(Icons.Default.Savings, contentDescription = null) },
                                        modifier = Modifier.testTag("header_add_savings_item"),
                                        onClick = {
                                            showHeaderAddMenu = false
                                            onNavigateToSavings()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("ঋণ / কিস্তি", fontWeight = FontWeight.SemiBold) },
                                        leadingIcon = { Icon(Icons.Default.CreditScore, contentDescription = null) },
                                        modifier = Modifier.testTag("header_add_loan_item"),
                                        onClick = {
                                            showHeaderAddMenu = false
                                            onNavigateToLoans()
                                        }
                                    )
                                    if (role == UserRole.ADMIN) {
                                        DropdownMenuItem(
                                            text = { Text("নতুন হিসাব এন্ট্রি", fontWeight = FontWeight.SemiBold) },
                                            leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                                            modifier = Modifier.testTag("header_add_ledger_item"),
                                            onClick = {
                                                showHeaderAddMenu = false
                                                showAddLedgerDialog = true
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { showCloudSyncDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_cloud_sync_icon")
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = when {
                                                !syncStatus.isOnline -> Icons.Default.CloudOff
                                                syncStatus.isCloudAuthenticated -> Icons.Default.CloudDone
                                                else -> Icons.Default.CloudSync
                                            },
                                            contentDescription = "ক্লাউড ও অফলাইন সিঙ্ক",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { showAccountLoginDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_login_icon")
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Login,
                                            contentDescription = "লগইন",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .background(ekotaSilverBackgroundBrush()),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {


        // Auto-Update Prompt Card when a newer build is released
        availableUpdate?.let { update ->
            if (!updateBannerDismissed) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = ekotaBadgeSuccessBg()),
                        border = BorderStroke(1.dp, EmeraldGreenPrimary.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_update_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "অ্যাপ আপডেট",
                                tint = EmeraldGreenPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "নতুন আপডেট (${update.latestVersionName})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ekotaSuccessTextColor()
                                )
                                Text(
                                    text = "আনইন্সটল ছাড়াই সরাসরি আপডেট করুন — সব ডাটা অক্ষুণ্ণ থাকবে।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { openDownloadUrl(context, update.downloadUrl) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldGreenPrimary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_update_now_button")
                            ) {
                                Text("আপডেট", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                            IconButton(
                                onClick = { viewModel.dismissUpdateBanner() },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "আপডেট নোটিশ বন্ধ করুন",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Offline Persistence & Auto-Sync Status Banner
        if (!syncStatus.isOnline || syncStatus.pendingCount > 0 || syncStatus.isSyncing) {
            item {
                val isOffline = !syncStatus.isOnline
                val containerColor = if (isOffline) ekotaBadgeWarningBg() else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                val accentColor = if (isOffline) ekotaWarningTextColor() else MaterialTheme.colorScheme.primary
                val titleText = when {
                    isOffline && syncStatus.pendingCount > 0 ->
                        "অফলাইন মোড • ${BengaliUtils.toBengaliDigits(syncStatus.pendingCount)}টি এন্ট্রি সিঙ্কের অপেক্ষায়"
                    isOffline ->
                        "অফলাইন মোড চালু (ফোনে সংরক্ষিত হচ্ছে)"
                    syncStatus.isSyncing ->
                        "ক্লাউডে স্বয়ংক্রিয় সিঙ্ক হচ্ছে..."
                    else ->
                        "${BengaliUtils.toBengaliDigits(syncStatus.pendingCount)}টি অফলাইন এন্ট্রি সিঙ্ক বাকি"
                }
                val subtitleText = when {
                    isOffline -> "মাঠ পর্যায়ে নেট ছাড়াও সঞ্চয় ও কিস্তি এন্ট্রি দিন। ইন্টারনেট ফিরলেই অটো সিঙ্ক হবে।"
                    syncStatus.isSyncing -> "অপেক্ষমান ডাটা Firestore সার্ভারে পাঠানো হচ্ছে।"
                    else -> syncFeedbackMsg ?: "সংযোগ চালু আছে — এখন সিঙ্ক করতে ট্যাপ করুন।"
                }
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_offline_sync_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isOffline -> Icons.Default.CloudOff
                                syncStatus.isSyncing -> Icons.Default.CloudSync
                                else -> Icons.Default.CloudUpload
                            },
                            contentDescription = "সিঙ্ক অবস্থা",
                            tint = accentColor,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = subtitleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isOffline && !syncStatus.isSyncing && syncStatus.isCloudAuthenticated) {
                            TextButton(
                                onClick = {
                                    viewModel.triggerManualSync { _, msg ->
                                        syncFeedbackMsg = msg
                                    }
                                },
                                modifier = Modifier.testTag("dashboard_manual_sync_button")
                            ) {
                                Text("সিঙ্ক", fontWeight = FontWeight.Bold, color = accentColor)
                            }
                        }
                    }
                }
            }
        }

        // Pending Approvals Notice for Admin
        if (role == UserRole.ADMIN && summary.pendingApprovalsCount > 0) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToApprovals() }
                        .testTag("pending_approvals_alert")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PendingActions,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "মাঠ কর্মকর্তার কালেকশন অনুমোদনের জন্য অপেক্ষমান",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "মোট ${BengaliUtils.toBengaliDigits(summary.pendingApprovalsCount)} টি এন্ট্রি যাচাই ও অনুমোদনের অপেক্ষায় রয়েছে।",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "যান",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        // Home Dashboard Metric Cards Header
        item {
            Text(
                text = "সমিতির সার্বিক সারসংক্ষেপ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ekotaSectionTitleColor()
            )
        }

        // 8 Key Cards in Grid Rows (Equal height rows & spacious non-breaking labels)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardMetricCard(
                    title = "মোট সদস্য",
                    value = "${BengaliUtils.toBengaliDigits(summary.totalMembers)} জন",
                    icon = Icons.Default.People,
                    gradientColors = CardPurpleGradient,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onNavigateToMembers
                )
                DashboardMetricCard(
                    title = "মোট সঞ্চয়",
                    value = BengaliUtils.formatTaka(summary.totalSavings),
                    icon = Icons.Default.Savings,
                    gradientColors = CardTealGradient,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onNavigateToSavings
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardMetricCard(
                    title = "আজকের সঞ্চয়",
                    value = BengaliUtils.formatTaka(summary.todaySavings),
                    icon = Icons.Default.CalendarToday,
                    gradientColors = CardEmeraldGradient,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onNavigateToSavings
                )
                DashboardMetricCard(
                    title = "মোট ঋণ বিতরণ",
                    value = BengaliUtils.formatTaka(summary.totalLoanDisbursed),
                    icon = Icons.Default.CreditScore,
                    gradientColors = CardAmberGradient,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onNavigateToLoans
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardMetricCard(
                    title = "মোট ঋণ আদায়",
                    value = BengaliUtils.formatTaka(summary.totalLoanCollected),
                    icon = Icons.Default.PriceCheck,
                    gradientColors = CardPinkGradient,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onNavigateToLoans
                )
                DashboardMetricCard(
                    title = "মোট বকেয়া ঋণ",
                    value = BengaliUtils.formatTaka(summary.totalLoanDue),
                    icon = Icons.Default.HourglassEmpty,
                    gradientColors = CardOrangeGradient,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onNavigateToLoans
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardMetricCard(
                    title = "বর্তমান ব্যালেন্স",
                    value = BengaliUtils.formatTaka(summary.currentBalance),
                    icon = Icons.Default.AccountBalanceWallet,
                    gradientColors = CardBlueGradient,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onNavigateToLedger
                )
                DashboardMetricCard(
                    title = "আজকের কালেকশন",
                    value = BengaliUtils.formatTaka(summary.todayCollection),
                    icon = Icons.Default.Payments,
                    gradientColors = CardRoseGradient,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = onNavigateToSavings
                )
            }
        }

        // Dedicated Separate Fine Sheet Card (10 BDT weekly fine kept strictly separate)
        item {
            val fineAccent = VibrantCardAccents[6]
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (LocalIsDarkEkotaTheme.current) MaterialTheme.colorScheme.surface else fineAccent.softSurface
                ),
                border = BorderStroke(1.2.dp, fineAccent.gradient.first().copy(alpha = 0.55f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToFines() }
                    .testTag("dashboard_fines_sheet_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(fineAccent.gradient)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = "জরিমানা শিট",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "জরিমানা শিট (৳১০/সপ্তাহ)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (LocalIsDarkEkotaTheme.current) MaterialTheme.colorScheme.onSurface else fineAccent.accentInk
                                )
                                Text(
                                    text = "মূল সঞ্চয় ও ঋণ থেকে সম্পূর্ণ আলাদা হিসাব",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = fineAccent.gradient.first()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "শিট দেখুন",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "মোট ধার্য",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = BengaliUtils.formatTaka(summary.totalFinesAssessed),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "জরিমানা আদায়",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = BengaliUtils.formatTaka(summary.totalFinesCollected),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ekotaSuccessTextColor(),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "বাকি জরিমানা",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = BengaliUtils.formatTaka(summary.totalFinesDue),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ekotaDangerTextColor(),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Quick Navigation Shortcuts
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "দ্রুত কার্যক্রম",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ekotaSectionTitleColor()
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionButton(
                    title = "সঞ্চয় এন্ট্রি",
                    icon = Icons.Default.AddCircle,
                    containerColor = CardTealGradient.first(),
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToSavings
                )
                QuickActionButton(
                    title = "ঋণের কিস্তি",
                    icon = Icons.Default.ReceiptLong,
                    containerColor = CardPurpleGradient.first(),
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToLoans
                )
                QuickActionButton(
                    title = "নতুন সদস্য",
                    icon = Icons.Default.PersonAdd,
                    containerColor = CardOrangeGradient.first(),
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = if (role == UserRole.ADMIN) {
                        { showAddMemberDialog = true }
                    } else {
                        onNavigateToMembers
                    }
                )
            }
        }
    }
    }

    if (showAccountLoginDialog) {
        AlertDialog(
            onDismissRequest = { showAccountLoginDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Login,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "লগইন ও অ্যাকাউন্ট",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "বর্তমান ব্যবহারকারী: ${userProfile?.displayName ?: "ব্যবহারকারী"}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "পদবী: ${if (role == UserRole.ADMIN) "অ্যাডমিন প্যানেল" else "মাঠ কর্মকর্তা"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "আপনি চাইলে লগইন স্ক্রিনে গিয়ে অন্য অ্যাকাউন্টে প্রবেশ করতে পারেন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAccountLoginDialog = false
                        onOpenLogin()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreenPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_open_login_button")
                ) {
                    Text("লগইন স্ক্রিনে যান", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountLoginDialog = false }) {
                    Text("বাতিল", style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }

    if (showCloudSyncDialog) {
        OfflineCloudSyncDialog(
            syncStatus = syncStatus,
            storageLimitLabel = storageLimitLabel,
            onOpenStorageSettings = {
                showCloudSyncDialog = false
                showStorageSettingsDialog = true
            },
            onTriggerSync = { onResult ->
                viewModel.triggerManualSync(onResult)
            },
            onDismiss = { showCloudSyncDialog = false }
        )
    }

    if (showStorageSettingsDialog) {
        StorageSettingsDialog(
            canEditQuota = true,
            backupJsonProvider = { viewModel.exportFullBackupJson() },
            onRestoreBackupJson = if (role == UserRole.ADMIN) {
                { json, onOk, onErr -> viewModel.restoreBackupFromJson(json, onOk, onErr) }
            } else {
                null
            },
            onLimitUpdated = { newLimitBytes ->
                configuredStorageLimitBytes = newLimitBytes
            },
            onDismiss = { showStorageSettingsDialog = false }
        )
    }

    if (showAddMemberDialog) {
        MemberFormDialog(
            title = "নতুন সদস্য যোগ করুন",
            initialMember = null,
            onDismiss = { showAddMemberDialog = false },
            onSave = { newMember ->
                viewModel.addMember(
                    member = newMember,
                    onSuccess = { showAddMemberDialog = false },
                    onError = {}
                )
            }
        )
    }

    if (showAddLedgerDialog) {
        AddLedgerEntryDialog(
            onDismiss = { showAddLedgerDialog = false },
            onSave = { date, desc, moneyIn, moneyOut ->
                viewModel.addLedgerEntry(
                    date = date,
                    desc = desc,
                    moneyIn = moneyIn,
                    moneyOut = moneyOut,
                    onSuccess = { showAddLedgerDialog = false },
                    onError = {}
                )
            }
        )
    }
}

@Composable
fun DashboardMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .clickable { onClick() }
            .testTag("metric_${title}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 114.dp)
                .background(Brush.linearGradient(colors = gradientColors))
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            // Subtle bottom-right watermark icon like the reference dashboard cards
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.20f),
                modifier = Modifier
                    .size(38.dp)
                    .align(Alignment.BottomEnd)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = value,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun QuickActionButton(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        modifier = modifier.height(78.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
