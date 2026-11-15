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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserRole
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import com.example.util.BengaliUtils

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToSavings: () -> Unit,
    onNavigateToLoans: () -> Unit,
    onNavigateToMembers: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToApprovals: () -> Unit,
    onNavigateToFines: () -> Unit = {},
    onOpenLogin: () -> Unit = {}
) {
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val role by viewModel.userRole.collectAsStateWithLifecycle()
    val userProfile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    var showAccountLoginDialog by remember { mutableStateOf(false) }
    var syncFeedbackMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Banner with ONLY Login Icon action on the right
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_header_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(EmeraldGreenDark, EmeraldGreenPrimary, NavyBlueSecondary)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.orgName,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = settings.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Only Login Icon on Dashboard Header
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                        ) {
                            IconButton(
                                onClick = { showAccountLoginDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("dashboard_login_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Login,
                                    contentDescription = "লগইন",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
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
                color = MaterialTheme.colorScheme.onBackground
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
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                                    .background(ekotaBadgeWarningBg()),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = "জরিমানা শিট",
                                    tint = ekotaWarningTextColor(),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "জরিমানা শিট (৳১০/সপ্তাহ)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
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
                            color = NavyBlueSecondary
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
                color = MaterialTheme.colorScheme.onBackground
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
                    onClick = onNavigateToMembers
                )
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
                .heightIn(min = 108.dp)
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.96f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = value,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
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
        modifier = modifier.height(74.dp)
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
