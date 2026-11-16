package com.example.ui.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserRole
import com.example.notifications.NotificationHelper
import com.example.ui.MainViewModel
import com.example.ui.approvals.ApprovalsScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.fines.FineSheetScreen
import com.example.ui.ledger.DepositLedgerScreen
import com.example.ui.loans.LoanManagementScreen
import com.example.ui.members.MemberManagementScreen
import com.example.ui.members.MemberRegistrationScreen
import com.example.ui.officers.OfficersManagementScreen
import com.example.ui.reports.ReportsScreen
import com.example.ui.savings.WeeklySavingsScreen
import com.example.ui.settings.SettingsScreen

enum class AppDestination(val titleBengali: String, val icon: ImageVector) {
    DASHBOARD("হোম", Icons.Default.Dashboard),
    SAVINGS("সঞ্চয়", Icons.Default.Savings),
    LOANS("ঋণ", Icons.Default.CreditScore),
    MEMBERS("সদস্য", Icons.Default.People),
    MEMBER_REGISTRATION("সদস্য নিবন্ধন", Icons.Default.PersonAdd),
    LEDGER("লেজার", Icons.Default.AccountBalanceWallet),
    REPORTS("রিপোর্ট", Icons.Default.Assessment),
    FINES("জরিমানা", Icons.Default.ReceiptLong),
    APPROVALS("অনুমোদন", Icons.Default.Rule),
    OFFICERS("কর্মকর্তা", Icons.Default.Badge),
    SETTINGS("সেটিংস", Icons.Default.Settings)
}

@Composable
fun MainAppNavigation(
    viewModel: MainViewModel,
    openApprovalsFromDeepLink: Boolean = false,
    onDeepLinkConsumed: () -> Unit = {},
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var currentDestination by remember { mutableStateOf(AppDestination.DASHBOARD) }
    var previousBeforeRegistration by remember { mutableStateOf(AppDestination.MEMBERS) }
    val role by viewModel.userRole.collectAsStateWithLifecycle()
    val pendingNotifications by viewModel.pendingAdminNotifications.collectAsStateWithLifecycle()
    val dispatchedNotificationIds = remember { mutableSetOf<String>() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        NotificationHelper.syncAdminFcmRegistration(context, isAdmin = role == UserRole.ADMIN) { token ->
            viewModel.registerAdminFcmToken(token)
        }
    }

    // Request notification permission & sync FCM Admin topic/token when Admin enters app
    LaunchedEffect(role) {
        if (role == UserRole.ADMIN) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationHelper.hasNotificationPermission(context)
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                NotificationHelper.syncAdminFcmRegistration(context, isAdmin = true) { token ->
                    viewModel.registerAdminFcmToken(token)
                }
            }
        } else {
            NotificationHelper.syncAdminFcmRegistration(context, isAdmin = false) {}
        }
    }

    // Navigate directly to ApprovalsScreen when tapped from notification deep link
    LaunchedEffect(openApprovalsFromDeepLink, role) {
        if (openApprovalsFromDeepLink) {
            if (role == UserRole.ADMIN) {
                currentDestination = AppDestination.APPROVALS
            }
            onDeepLinkConsumed()
        }
    }

    // Surface new Officer transaction alerts for Admin with summary & deep link to ApprovalsScreen
    LaunchedEffect(pendingNotifications, role) {
        if (role == UserRole.ADMIN && pendingNotifications.isNotEmpty()) {
            pendingNotifications.forEach { notif ->
                if (notif.id.isNotBlank() && notif.id !in dispatchedNotificationIds) {
                    dispatchedNotificationIds.add(notif.id)
                    NotificationHelper.showApprovalNotification(
                        context = context,
                        title = notif.title.ifBlank { "নতুন অনুমোদনের অনুরোধ" },
                        summary = notif.body.ifBlank { "মাঠ কর্মকর্তা নতুন লেনদেন জমা দিয়েছেন" },
                        transactionType = notif.transactionType,
                        transactionId = notif.transactionId
                    )
                    viewModel.markAdminNotificationRead(notif.id)
                }
            }
        }
    }

    // Handle back button on sub-screens to return to Dashboard
    BackHandler(enabled = currentDestination != AppDestination.DASHBOARD) {
        currentDestination = AppDestination.DASHBOARD
    }

    // Bottom Navigation destinations for primary tasks
    val bottomNavItems = remember(role) {
        if (role == UserRole.ADMIN) {
            listOf(
                AppDestination.DASHBOARD,
                AppDestination.SAVINGS,
                AppDestination.LOANS,
                AppDestination.LEDGER,
                AppDestination.SETTINGS
            )
        } else {
            listOf(
                AppDestination.DASHBOARD,
                AppDestination.SAVINGS,
                AppDestination.LOANS,
                AppDestination.MEMBERS,
                AppDestination.SETTINGS
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 0.dp,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    bottomNavItems.forEach { destination ->
                        val isSelected = currentDestination == destination
                        val navLabel = if (destination == AppDestination.SETTINGS && role != UserRole.ADMIN) {
                            "অ্যাকাউন্ট"
                        } else {
                            destination.titleBengali
                        }
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = if (destination == AppDestination.SETTINGS && role != UserRole.ADMIN) {
                                        Icons.Default.AccountCircle
                                    } else {
                                        destination.icon
                                    },
                                    contentDescription = navLabel
                                )
                            },
                            label = {
                                Text(
                                    text = navLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = HeaderSlateNavy,
                                selectedTextColor = HeaderSlateNavy,
                                indicatorColor = Color(0xFFD2E0F2),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_${destination.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ekotaSilverBackgroundBrush())
                .padding(innerPadding)
        ) {
            when (currentDestination) {
                AppDestination.DASHBOARD -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToSavings = { currentDestination = AppDestination.SAVINGS },
                    onNavigateToLoans = { currentDestination = AppDestination.LOANS },
                    onNavigateToMembers = { currentDestination = AppDestination.MEMBERS },
                    onNavigateToMemberRegistration = {
                        previousBeforeRegistration = AppDestination.DASHBOARD
                        currentDestination = AppDestination.MEMBER_REGISTRATION
                    },
                    onNavigateToLedger = { currentDestination = AppDestination.LEDGER },
                    onNavigateToApprovals = { currentDestination = AppDestination.APPROVALS },
                    onNavigateToFines = { currentDestination = AppDestination.FINES },
                    onOpenLogin = onSignOut
                )
                AppDestination.SAVINGS -> WeeklySavingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentDestination = AppDestination.DASHBOARD },
                    onNavigateToFines = { currentDestination = AppDestination.FINES }
                )
                AppDestination.LOANS -> LoanManagementScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentDestination = AppDestination.DASHBOARD },
                    onNavigateToFines = { currentDestination = AppDestination.FINES }
                )
                AppDestination.MEMBERS -> MemberManagementScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentDestination = AppDestination.DASHBOARD },
                    onNavigateToMemberRegistration = {
                        previousBeforeRegistration = AppDestination.MEMBERS
                        currentDestination = AppDestination.MEMBER_REGISTRATION
                    }
                )
                AppDestination.MEMBER_REGISTRATION -> MemberRegistrationScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentDestination = previousBeforeRegistration },
                    onNavigateToMembers = { currentDestination = AppDestination.MEMBERS }
                )
                AppDestination.LEDGER -> DepositLedgerScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentDestination = AppDestination.DASHBOARD }
                )
                AppDestination.REPORTS -> ReportsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentDestination = AppDestination.DASHBOARD }
                )
                AppDestination.FINES -> FineSheetScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentDestination = AppDestination.DASHBOARD }
                )
                AppDestination.APPROVALS -> ApprovalsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentDestination = AppDestination.DASHBOARD }
                )
                AppDestination.OFFICERS -> OfficersManagementScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentDestination = AppDestination.DASHBOARD }
                )
                AppDestination.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentDestination = AppDestination.DASHBOARD },
                    onSignOut = onSignOut
                )
            }
        }
    }
}
