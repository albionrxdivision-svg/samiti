package com.example.ui.loans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Loan
import com.example.data.model.LoanInstallment
import com.example.data.model.LoanStatus
import com.example.data.model.UserRole
import com.example.ui.MainViewModel
import com.example.ui.components.EkotaCompactTopBar
import com.example.ui.components.SuccessPopupDialog
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import com.example.util.SavingsAndFineCalculator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanManagementScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToFines: () -> Unit = {}
) {
    val loans by viewModel.loans.collectAsStateWithLifecycle()
    val installments by viewModel.loanInstallments.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val role by viewModel.userRole.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isSavingLoan by viewModel.isSavingLoan.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val pendingInstallmentIds by viewModel.pendingInstallmentIds.collectAsStateWithLifecycle()

    val isAdmin = role == UserRole.ADMIN
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var loanToPayInstallment by remember { mutableStateOf<Loan?>(null) }
    var loanToMarkMissed by remember { mutableStateOf<Loan?>(null) }
    var loanDetailsTarget by remember { mutableStateOf<Loan?>(null) }
    var successPopupData by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: All, 1: Active, 2: Paid, 3: New Loan (Admin)

    LaunchedEffect(isAdmin) {
        if (!isAdmin && selectedTab == 3) {
            selectedTab = 0
        }
    }

    val filteredLoans = remember(loans, selectedTab) {
        when (selectedTab) {
            1 -> loans.filter { it.status == LoanStatus.ACTIVE.name }
            2 -> loans.filter { it.status == LoanStatus.PAID.name }
            else -> loans
        }
    }

    val totalDisbursed = remember(loans) { loans.sumOf { it.principal } }
    val totalPaid = remember(loans) { loans.sumOf { it.totalPaid } }
    val totalDue = remember(loans) { loans.sumOf { it.remainingLoan } }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            EkotaCompactTopBar(
                title = "ঋণ ব্যবস্থাপনা",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToFines,
                        modifier = Modifier.testTag("loans_to_fines_button")
                    ) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = "জরিমানা শিট",
                            tint = Color.White
                        )
                    }
                    if (isAdmin && selectedTab != 3) {
                        TextButton(
                            onClick = { selectedTab = 3 },
                            modifier = Modifier.testTag("top_new_loan_button")
                        ) {
                            Icon(
                                Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "নতুন ঋণ",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (isAdmin && selectedTab != 3) {
                ExtendedFloatingActionButton(
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("নতুন ঋণ বিতরণ", fontWeight = FontWeight.Bold) },
                    containerColor = CardPurpleGradient.first(),
                    contentColor = Color.White,
                    modifier = Modifier.testTag("new_loan_fab")
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ekotaSilverBackgroundBrush())
                .padding(16.dp)
        ) {
            // Loan Summary Header Card (Vibrant Gradient matching reference card palette)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(colors = CardPurpleGradient))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryColumn("মোট বিতরণ", BengaliUtils.formatTaka(totalDisbursed), Color.White, Modifier.weight(1f))
                        SummaryColumn("মোট আদায়", BengaliUtils.formatTaka(totalPaid), Color.White, Modifier.weight(1f))
                        SummaryColumn("অবশিষ্ট বকেয়া", BengaliUtils.formatTaka(totalDue), Color.White, Modifier.weight(1f))
                    }
                }
            }

            if (!syncStatus.isOnline || pendingInstallmentIds.isNotEmpty() || syncStatus.isSyncing) {
                Spacer(modifier = Modifier.height(10.dp))
                val isOffline = !syncStatus.isOnline
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isOffline) ekotaBadgeWarningBg() else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(
                        1.dp,
                        (if (isOffline) ekotaWarningTextColor() else MaterialTheme.colorScheme.primary).copy(alpha = 0.4f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loans_offline_sync_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isOffline) Icons.Default.CloudOff else Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = if (isOffline) ekotaWarningTextColor() else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when {
                                isOffline -> "অফলাইন ক্যাশ চালু • কিস্তি এন্ট্রি ফোনে সংরক্ষিত হচ্ছে, নেট ফিরলে অটো সিঙ্ক হবে"
                                syncStatus.isSyncing -> "ক্লাউডে কিস্তির তথ্য সিঙ্ক হচ্ছে..."
                                else -> "${BengaliUtils.toBengaliDigits(pendingInstallmentIds.size)}টি কিস্তির এন্ট্রি সিঙ্কের অপেক্ষায়"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filter & Admin Action Tabs (fitted cleanly within screen width without clipping)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = HeaderSlateNavy,
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        selectedContentColor = HeaderSlateNavy,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                text = "সব ঋণ",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("all_loans_tab")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        selectedContentColor = HeaderSlateNavy,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                text = "চলতি",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("active_loans_tab")
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        selectedContentColor = HeaderSlateNavy,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                text = "পরিশোধিত",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("paid_loans_tab")
                    )
                    if (isAdmin) {
                        Tab(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            selectedContentColor = CardPurpleGradient.first(),
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = {
                                Text(
                                    text = "নতুন ঋণ",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("new_loan_tab")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isAdmin && selectedTab == 3) {
                NewLoanDistributionForm(
                    members = members,
                    defaultInterestRatePercent = appSettings.interestRatePercent,
                    isSaving = isSavingLoan,
                    existingLoans = loans,
                    onDistribute = { newLoan, optionalMember ->
                        viewModel.createLoan(
                            loan = newLoan,
                            alsoAddMember = optionalMember,
                            onSuccess = {
                                selectedTab = 1
                                successPopupData = Pair(
                                    "${newLoan.memberName}-এর নামে ${BengaliUtils.formatTaka(newLoan.principal)} নতুন ঋণ সফলভাবে বিতরণ হয়েছে।",
                                    "সাপ্তাহিক কিস্তি: ${BengaliUtils.formatTaka(newLoan.weeklyInstallment)} | তারিখ: ${newLoan.loanDate}"
                                )
                            },
                            onError = { msg ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        )
                    },
                    onCancel = { selectedTab = 0 }
                )
            } else if (filteredLoans.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "কোনো ঋণের তথ্য নেই",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isAdmin) {
                            OutlinedButton(onClick = { selectedTab = 3 }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("প্রথম ঋণ বিতরণ করুন")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(filteredLoans, key = { _, loan -> loan.id }) { index, loan ->
                        val loanHistory = installments.filter { it.loanId == loan.id && !it.isMissed }
                        val todayStr = BengaliUtils.getCurrentDateStr()
                        val thisWeekInstallment = installments.firstOrNull {
                            it.loanId == loan.id &&
                                it.status != "REJECTED" &&
                                SavingsAndFineCalculator.isSameWeek(it.installmentDate, todayStr)
                        }
                        val submittedThisWeek = thisWeekInstallment != null
                        val latestCollector = thisWeekInstallment?.officerName?.ifBlank { "" }
                            ?: loanHistory.lastOrNull()?.officerName?.ifBlank { "" }
                            ?: ""
                        val hasPendingInstSync = installments.any { it.loanId == loan.id && it.id in pendingInstallmentIds } ||
                            (loan.id in syncStatus.pendingDocumentIds)
                        LoanCard(
                            loan = loan,
                            installmentsCount = loanHistory.size,
                            isAdmin = isAdmin,
                            isPendingSync = hasPendingInstSync,
                            isSubmittedThisWeek = submittedThisWeek,
                            cardAccentIndex = index,
                            latestCollectorName = latestCollector,
                            onCardClick = { loanDetailsTarget = loan },
                            onCollectInstallment = { loanToPayInstallment = loan },
                            onMarkMissedInstallment = { loanToMarkMissed = loan }
                        )
                    }
                }
            }
        }
    }

    // Record Installment Dialog
    loanToPayInstallment?.let { loan ->
        LoanInstallmentDialog(
            loan = loan,
            onDismiss = { loanToPayInstallment = null },
            onSave = { amount, date ->
                viewModel.recordLoanInstallment(
                    loan = loan,
                    date = date,
                    amount = amount,
                    onSuccess = {
                        loanToPayInstallment = null
                        val catchUpNote = if (loan.missedInstallments > 0 && amount >= loan.weeklyInstallment * 2) {
                            "পূর্বের বকেয়া কিস্তিসহ কাউন্ট হয়েছে"
                        } else null
                        val offlineNote = if (syncStatus.isCloudAuthenticated && !syncStatus.isOnline) {
                            "অফলাইনে সংরক্ষিত, নেট ফিরলে অটো সিঙ্ক হবে"
                        } else null
                        val subNote = listOfNotNull(catchUpNote, offlineNote).joinToString("\n").ifBlank { null }
                        successPopupData = Pair(
                            "${loan.memberName}-এর ${BengaliUtils.formatTaka(amount)} ঋণের কিস্তি সফলভাবে জমা হয়েছে।",
                            subNote
                        )
                    },
                    onError = { msg ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                )
            }
        )
    }

    // Missed Loan Installment Dialog (+10 BDT Fine on separate Fine Sheet)
    loanToMarkMissed?.let { loan ->
        var missedDateText by remember(loan.id) {
            mutableStateOf(loan.nextInstallmentDate.ifBlank { BengaliUtils.getCurrentDateStr() })
        }
        AlertDialog(
            onDismissRequest = { loanToMarkMissed = null },
            title = { Text("বকেয়া কিস্তি ও ৳১০ জরিমানা", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${loan.memberName} (${BengaliUtils.toBengaliDigits(loan.memberSerial)}) এই সপ্তাহে কিস্তি না দিলে পরের সপ্তাহে ৳১০ জরিমানা আলাদা জরিমানা শিটে যুক্ত হবে। মূল ঋণের টাকা অপরিবর্তিত থাকবে।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = missedDateText,
                        onValueChange = { missedDateText = it },
                        label = { Text("অনুপস্থিত কিস্তির তারিখ (dd/MM/yy)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dateToSave = missedDateText.trim().ifBlank { BengaliUtils.getCurrentDateStr() }
                        viewModel.recordMissedLoanInstallment(
                            loan = loan,
                            missedDate = dateToSave,
                            onSuccess = {
                                loanToMarkMissed = null
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("অনুপস্থিত কিস্তি রেকর্ড হয়েছে এবং আলাদা জরিমানা শিটে ৳১০ যুক্ত হয়েছে")
                                }
                            },
                            onError = { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerRed,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_missed_loan_btn")
                ) {
                    Text("নিশ্চিত করুন (+৳১০)")
                }
            },
            dismissButton = {
                TextButton(onClick = { loanToMarkMissed = null }) { Text("বাতিল") }
            }
        )
    }

    // Loan Details and Installment History Dialog
    loanDetailsTarget?.let { loan ->
        val history = installments.filter { it.loanId == loan.id }
        LoanHistoryDialog(
            loan = loan,
            installmentHistory = history,
            onDismiss = { loanDetailsTarget = null }
        )
    }

    successPopupData?.let { (msg, sub) ->
        SuccessPopupDialog(
            title = "সফল হয়েছে (Successful)",
            message = msg,
            subtitle = sub,
            onDismiss = { successPopupData = null }
        )
    }
}

@Composable
fun SummaryColumn(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.94f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LoanCard(
    loan: Loan,
    installmentsCount: Int,
    isAdmin: Boolean = false,
    isPendingSync: Boolean = false,
    isSubmittedThisWeek: Boolean = false,
    cardAccentIndex: Int = 0,
    latestCollectorName: String = "",
    onCardClick: () -> Unit,
    onCollectInstallment: () -> Unit,
    onMarkMissedInstallment: () -> Unit = {}
) {
    val isDark = LocalIsDarkEkotaTheme.current
    val accent = VibrantCardAccents[cardAccentIndex % VibrantCardAccents.size]
    val cardContainerColor = when {
        isDark -> MaterialTheme.colorScheme.surface
        isSubmittedThisWeek -> accent.softSurface
        else -> accent.softSurface.copy(alpha = 0.65f)
    }
    val cardBorderColor = accent.gradient.first().copy(alpha = if (isSubmittedThisWeek) 0.72f else 0.36f)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        border = BorderStroke(if (isSubmittedThisWeek) 1.4.dp else 1.dp, cardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("loan_card_${loan.memberSerial}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(colors = accent.gradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = BengaliUtils.toBengaliDigits(loan.memberSerial),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = loan.memberName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (!isDark) accent.accentInk else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "তারিখ: ${loan.loanDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (latestCollectorName.isNotBlank() || isSubmittedThisWeek) {
                            Text(
                                text = "জমাদানকারী: ${latestCollectorName.ifBlank { "অ্যাডমিন" }}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) accent.gradient.last() else accent.accentInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (loan.status == LoanStatus.PAID.name) ekotaBadgeSuccessBg() else ekotaBadgeWarningBg()
                    ) {
                        Text(
                            text = if (loan.status == LoanStatus.PAID.name) "পরিশোধিত" else "চলতি ঋণ",
                            color = if (loan.status == LoanStatus.PAID.name) ekotaSuccessTextColor() else ekotaWarningTextColor(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (isSubmittedThisWeek) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ekotaBadgeSuccessBg(),
                            modifier = Modifier.testTag("loan_week_submitted_${loan.id}")
                        ) {
                            Text(
                                text = "এই সপ্তাহে জমা ✓",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ekotaSuccessTextColor(),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isPendingSync) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ekotaBadgeWarningBg(),
                            modifier = Modifier.testTag("loan_pending_sync_${loan.id}")
                        ) {
                            Text(
                                text = "সিঙ্ক বাকি",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ekotaWarningTextColor(),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (loan.missedInstallments > 0 && loan.remainingLoan > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ekotaBadgeDangerBg(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = ekotaDangerTextColor(),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "বকেয়া কিস্তি: ${BengaliUtils.toBengaliDigits(loan.missedInstallments)} সপ্তাহ (ডাবল কিস্তি দিলে পূর্বেরটি কাউন্ট হবে)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ekotaDangerTextColor()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Breakdown Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "মূল ঋণ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = BengaliUtils.formatTaka(loan.principal),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "নির্ধারিত সুদ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = BengaliUtils.formatTaka(loan.interest),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "পরিশোধযোগ্য",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = BengaliUtils.formatTaka(loan.totalPayable),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "সাপ্তাহিক কিস্তি",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = BengaliUtils.formatTaka(loan.weeklyInstallment),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "জমা কিস্তি (${BengaliUtils.toBengaliDigits(installmentsCount)}টি)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = BengaliUtils.formatTaka(loan.totalPaid),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ekotaSuccessTextColor(),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "অবশিষ্ট ঋণ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = BengaliUtils.formatTaka(loan.remainingLoan),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ekotaDangerTextColor(),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Installment Collection & Missed Week Buttons (Hidden once submitted this week)
            if (loan.remainingLoan > 0 && !isSubmittedThisWeek) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCollectInstallment,
                        modifier = Modifier
                            .weight(1.35f)
                            .heightIn(min = 48.dp)
                            .testTag("collect_installment_btn_${loan.id}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent.gradient.first(),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "কিস্তি জমা (${BengaliUtils.formatTaka(loan.weeklyInstallment)})",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isAdmin) {
                        OutlinedButton(
                            onClick = onMarkMissedInstallment,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("missed_installment_btn_${loan.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ekotaDangerTextColor()
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "দেয়নি (+৳১০)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else if (loan.remainingLoan > 0 && isSubmittedThisWeek) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ekotaBadgeSuccessBg(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loan_installment_submitted_pill_${loan.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ekotaSuccessTextColor(),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "এই সপ্তাহের কিস্তি জমা সম্পন্ন ✓",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = ekotaSuccessTextColor()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoanInstallmentDialog(
    loan: Loan,
    onDismiss: () -> Unit,
    onSave: (amount: Double, date: String) -> Unit
) {
    val singleAmt = loan.weeklyInstallment.toInt().coerceAtLeast(0).toString()
    val doubleAmt = (loan.weeklyInstallment * 2).coerceAtMost(loan.remainingLoan).toInt().coerceAtLeast(0).toString()
    var amountText by remember {
        mutableStateOf(if (loan.missedInstallments > 0) doubleAmt else singleAmt)
    }
    var dateText by remember { mutableStateOf(BengaliUtils.getCurrentDateStr()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ঋণের কিস্তি জমা", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("সদস্য: ${loan.memberName} (${BengaliUtils.toBengaliDigits(loan.memberSerial)})", fontWeight = FontWeight.SemiBold)
                Text("অবশিষ্ট ঋণ: ${BengaliUtils.formatTaka(loan.remainingLoan)}", color = ekotaDangerTextColor())

                if (loan.missedInstallments > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ekotaBadgeWarningBg(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "পূর্বের বকেয়া: ${BengaliUtils.toBengaliDigits(loan.missedInstallments)}টি কিস্তি। ডাবল (${BengaliUtils.formatTaka(loan.weeklyInstallment * 2)}) দিলে বকেয়া কিস্তি সমন্বয় হবে (৳১০ জরিমানা আলাদা শিটে থাকবে)।",
                            style = MaterialTheme.typography.bodySmall,
                            color = ekotaWarningTextColor(),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = amountText == singleAmt,
                        onClick = { amountText = singleAmt },
                        label = { Text("নির্ধারিত (১x)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = amountText == doubleAmt,
                        onClick = { amountText = doubleAmt },
                        label = { Text("ডাবল (২x)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("কিস্তির পরিমাণ (টাকা)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("তারিখ (যেমন: dd/MM/yy)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    if (amount > 0) {
                        onSave(amount, dateText)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreenPrimary,
                    contentColor = Color.White
                )
            ) {
                Text("জমা দিন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun LoanHistoryDialog(
    loan: Loan,
    installmentHistory: List<LoanInstallment>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${loan.memberName} - কিস্তি পরিশোধ বিবরণী",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "মোট পরিশোধযোগ্য: ${BengaliUtils.formatTaka(loan.totalPayable)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "অবশিষ্ট: ${BengaliUtils.formatTaka(loan.remainingLoan)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = ekotaDangerTextColor(),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                HorizontalDivider()

                Text("পরিশোধিত কিস্তিসমূহ:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                if (installmentHistory.isEmpty()) {
                    Text("এখনও কোনো কিস্তি জমা হয়নি", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                    ) {
                        items(installmentHistory) { inst ->
                            val isMissedEntry = inst.isMissed || inst.amount <= 0.0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "কিস্তি ${BengaliUtils.toBengaliDigits(inst.serialNumber)} (${inst.installmentDate})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isMissedEntry) ekotaDangerTextColor() else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (inst.officerName.isNotBlank()) {
                                        Text(
                                            text = "জমাদানকারী: ${inst.officerName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = if (isMissedEntry) "অনুপস্থিত (৳১০ জরিমানা)" else BengaliUtils.formatTaka(inst.amount),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMissedEntry) ekotaDangerTextColor() else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        }
    )
}
