package com.example.ui.loans

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Loan
import com.example.data.model.LoanInstallment
import com.example.data.model.UserRole
import com.example.ui.MainViewModel
import com.example.ui.components.EkotaCompactTopBar
import com.example.ui.components.SuccessPopupDialog
import com.example.ui.savings.AddCollectionDateDialog
import com.example.ui.savings.EditCollectionDateDialog
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import com.example.util.DailyAccountCalculator
import com.example.util.SavingsAndFineCalculator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LoanManagementScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToFines: () -> Unit = {}
) {
    val loans by viewModel.loans.collectAsStateWithLifecycle()
    val installments by viewModel.loanInstallments.collectAsStateWithLifecycle()
    val collectionPeriods by viewModel.collectionPeriods.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val role by viewModel.userRole.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isSavingLoan by viewModel.isSavingLoan.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val pendingInstallmentIds by viewModel.pendingInstallmentIds.collectAsStateWithLifecycle()

    val isAdmin = role == UserRole.ADMIN
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 0: Weekly Installment Collection Form (default), 1: Loan Matrix Sheet
    var selectedModeTab by remember { mutableIntStateOf(0) }

    var showAddDateDialog by remember { mutableStateOf(false) }
    var dateToEdit by remember { mutableStateOf<String?>(null) }
    var entryDialogTarget by remember { mutableStateOf<Pair<Loan, String>?>(null) }
    var loanToMarkMissed by remember { mutableStateOf<Loan?>(null) }
    var loanDetailsTarget by remember { mutableStateOf<Loan?>(null) }
    var showNewLoanDialog by remember { mutableStateOf(false) }
    var showTopMoreMenu by remember { mutableStateOf(false) }
    var successPopupData by remember { mutableStateOf<Pair<String, String?>?>(null) }

    val sortedDates = remember(collectionPeriods, installments) {
        val periodDates = collectionPeriods.map { SavingsAndFineCalculator.normalizeDateStr(it.dateStr) }
        val instDates = installments.map { SavingsAndFineCalculator.normalizeDateStr(it.installmentDate) }
        val combined = SavingsAndFineCalculator.sortDateStrings(periodDates + instDates)
        if (combined.isEmpty()) listOf(BengaliUtils.getCurrentDateStr()) else combined
    }

    val selectedDate by viewModel.selectedLoanDate.collectAsStateWithLifecycle()
    LaunchedEffect(sortedDates) {
        if (selectedDate !in sortedDates && sortedDates.isNotEmpty()) {
            viewModel.setSelectedLoanDate(sortedDates.last())
        }
    }

    val sortedLoans = remember(loans) {
        loans.sortedWith(
            compareBy<Loan>(
                { it.memberSerial.toIntOrNull() ?: Int.MAX_VALUE },
                { it.memberSerial },
                { it.memberName }
            )
        )
    }

    // Map: (loanId, date) -> LoanInstallment (supports raw + normalized date keys)
    val installmentMap = remember(installments) {
        val map = mutableMapOf<String, LoanInstallment>()
        for (inst in installments) {
            val normDate = DailyAccountCalculator.normalizeDateKey(inst.installmentDate)
            map["${inst.loanId}_${inst.installmentDate}"] = inst
            if (normDate.isNotBlank()) {
                map["${inst.loanId}_$normDate"] = inst
            }
        }
        map
    }

    val normalizedSelectedDate = remember(selectedDate) {
        DailyAccountCalculator.normalizeDateKey(selectedDate).ifBlank { selectedDate }
    }

    // Editable amounts map for the Collection Form: loanId -> String input
    val amountInputs = remember { mutableStateMapOf<String, String>() }

    // Sync/pre-populate form inputs whenever selectedDate, loans, or installments change
    LaunchedEffect(selectedDate, normalizedSelectedDate, sortedLoans, installments) {
        sortedLoans.forEach { loan ->
            val existing = installmentMap["${loan.id}_$selectedDate"]
                ?: installmentMap["${loan.id}_$normalizedSelectedDate"]
            val initialText = when {
                existing != null && !existing.isMissed && existing.amount > 0.0 ->
                    BengaliUtils.formatCleanAmount(existing.amount)
                existing != null && existing.isMissed -> "0"
                loan.remainingLoan <= 0.0 -> "0"
                else -> BengaliUtils.formatCleanAmount(loan.weeklyInstallment)
            }
            amountInputs[loan.id] = initialText
        }
    }

    // Column totals calculation for matrix sheet
    val columnTotals = remember(sortedDates, installments, sortedLoans) {
        sortedDates.associateWith { date ->
            val normDate = DailyAccountCalculator.normalizeDateKey(date)
            sortedLoans.sumOf { loan ->
                val rec = installmentMap["${loan.id}_$date"]
                    ?: installmentMap["${loan.id}_$normDate"]
                if (rec != null && !rec.isMissed && rec.status != "REJECTED") rec.amount else 0.0
            }
        }
    }

    val grandTotal = remember(columnTotals) { columnTotals.values.sum() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            EkotaCompactTopBar(
                title = "ঋণ ব্যবস্থাপনা",
                subtitle = "ঋণ: ${BengaliUtils.toBengaliDigits(sortedLoans.size)} জন | মোট আদায়: ${BengaliUtils.formatTaka(grandTotal)}",
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshMembersFromFirestore() },
                        modifier = Modifier.testTag("refresh_loans_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Firestore থেকে রিফ্রেশ")
                    }
                    if (isAdmin) {
                        IconButton(
                            onClick = { showNewLoanDialog = true },
                            modifier = Modifier.testTag("top_new_loan_button")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "নতুন ঋণ বিতরণ")
                        }
                        Box {
                            IconButton(
                                onClick = { showTopMoreMenu = true },
                                modifier = Modifier.testTag("loans_more_actions_button")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "আরও অপশন")
                            }
                            DropdownMenu(
                                expanded = showTopMoreMenu,
                                onDismissRequest = { showTopMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("নতুন কালেকশন তারিখ") },
                                    leadingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                                    modifier = Modifier.testTag("add_loan_date_button"),
                                    onClick = {
                                        showTopMoreMenu = false
                                        showAddDateDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("জরিমানা শিট (৳১০)") },
                                    leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                                    modifier = Modifier.testTag("loans_more_fines_item"),
                                    onClick = {
                                        showTopMoreMenu = false
                                        onNavigateToFines()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ekotaSilverBackgroundBrush())
        ) {
            // Mode Switch Tabs: Collection Form vs Loan Matrix Sheet (identical to WeeklySavingsScreen)
            TabRow(
                selectedTabIndex = selectedModeTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = HeaderSlateNavy
            ) {
                Tab(
                    selected = selectedModeTab == 0,
                    onClick = { selectedModeTab = 0 },
                    selectedContentColor = HeaderSlateNavy,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("tab_loan_collection_form"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = "সাপ্তাহিক কিস্তি ফর্ম",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedModeTab == 0) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedModeTab == 1,
                    onClick = { selectedModeTab = 1 },
                    selectedContentColor = HeaderSlateNavy,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("tab_loan_sheet"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = "ঋণ শিট (${BengaliUtils.toBengaliDigits(sortedDates.size)} সপ্তাহ)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedModeTab == 1) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
            }

            if (selectedModeTab == 0) {
                // -------------------------------------------------------------
                // TAB 0: WEEKLY LOAN INSTALLMENT COLLECTION FORM
                // -------------------------------------------------------------
                val dateControlsContent: @Composable () -> Unit = {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "কালেকশন তারিখ:",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AssistChip(
                                        onClick = onNavigateToFines,
                                        label = {
                                            Text(
                                                text = "জরিমানা শিট (৳১০)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = ekotaWarningTextColor()
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.ReceiptLong,
                                                contentDescription = null,
                                                tint = ekotaWarningTextColor(),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("loans_to_fines_button")
                                    )
                                    val syncChipText = when {
                                        !syncStatus.isCloudAuthenticated -> "লোকাল"
                                        !syncStatus.isOnline && syncStatus.pendingCount > 0 ->
                                            "অফলাইন (${BengaliUtils.toBengaliDigits(syncStatus.pendingCount)})"
                                        !syncStatus.isOnline -> "অফলাইন ক্যাশ"
                                        syncStatus.isSyncing || syncStatus.pendingCount > 0 ->
                                            "সিঙ্ক বাকি (${BengaliUtils.toBengaliDigits(syncStatus.pendingCount)})"
                                        else -> "সিঙ্কড"
                                    }
                                    val syncChipIcon = when {
                                        !syncStatus.isCloudAuthenticated -> Icons.Default.Storage
                                        !syncStatus.isOnline -> Icons.Default.CloudOff
                                        syncStatus.isSyncing || syncStatus.pendingCount > 0 -> Icons.Default.CloudUpload
                                        else -> Icons.Default.CloudDone
                                    }
                                    val syncChipTint = when {
                                        !syncStatus.isCloudAuthenticated -> MaterialTheme.colorScheme.primary
                                        !syncStatus.isOnline -> ekotaWarningTextColor()
                                        syncStatus.isSyncing || syncStatus.pendingCount > 0 -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                    AssistChip(
                                        onClick = {
                                            if (syncStatus.isCloudAuthenticated) {
                                                viewModel.triggerManualSync { _, msg ->
                                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                                }
                                            } else {
                                                viewModel.refreshMembersFromFirestore()
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = syncChipText,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = syncChipIcon,
                                                contentDescription = null,
                                                tint = syncChipTint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("loans_sync_status_chip")
                                    )
                                }
                            }

                            // Horizontal scrollable date chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                sortedDates.forEach { date ->
                                    FilterChip(
                                        selected = selectedDate == date,
                                        onClick = { viewModel.setSelectedLoanDate(date) },
                                        label = {
                                            Text(
                                                text = date,
                                                fontWeight = if (selectedDate == date) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        leadingIcon = if (selectedDate == date) {
                                            { Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            selectedContainerColor = HeaderSlateNavy,
                                            selectedLabelColor = Color.White,
                                            selectedLeadingIconColor = Color.White,
                                            labelColor = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier.testTag("loan_date_chip_$date")
                                    )
                                }
                                if (isAdmin) {
                                    AssistChip(
                                        onClick = { dateToEdit = selectedDate },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "তারিখ আপডেট",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        modifier = Modifier.testTag("chip_edit_selected_loan_date")
                                    )
                                    AssistChip(
                                        onClick = { showAddDateDialog = true },
                                        label = {
                                            Text(
                                                text = "+ নতুন তারিখ",
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        modifier = Modifier.testTag("chip_add_new_loan_date")
                                    )
                                }
                            }
                        }
                    }
                }

                if (sortedLoans.isEmpty()) {
                    dateControlsContent()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "কোনো ঋণের তথ্য পাওয়া যায়নি",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isAdmin) {
                                Button(
                                    onClick = { showNewLoanDialog = true },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("empty_new_loan_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldGreenPrimary,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("নতুন ঋণ বিতরণ করুন", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("weekly_loan_form_list")
                    ) {
                        item(key = "date_controls_header") {
                            dateControlsContent()
                        }
                        stickyHeader(key = "loan_list_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(top = 6.dp)
                                    .background(HeaderSlateNavy, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                    .testTag("loan_list_header"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SL",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.width(40.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "নাম",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Start,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "টাকা",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.width(72.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "জমা",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.width(86.dp)
                                )
                            }
                        }
                        itemsIndexed(sortedLoans, key = { _, loan -> loan.id }) { index, loan ->
                            val existing = installmentMap["${loan.id}_$selectedDate"]
                                ?: installmentMap["${loan.id}_$normalizedSelectedDate"]
                            val submittedThisWeek = remember(loan.id, selectedDate, normalizedSelectedDate, installments) {
                                installments.any {
                                    it.loanId == loan.id &&
                                        it.status != "REJECTED" &&
                                        (DailyAccountCalculator.normalizeDateKey(it.installmentDate) == normalizedSelectedDate ||
                                            SavingsAndFineCalculator.isSameWeek(it.installmentDate, selectedDate))
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                            ) {
                                MemberLoanInputRow(
                                    loan = loan,
                                    amountText = amountInputs[loan.id] ?: BengaliUtils.formatCleanAmount(loan.weeklyInstallment),
                                    existingInstallment = existing,
                                    isAdmin = isAdmin,
                                    isSubmittedThisWeek = submittedThisWeek,
                                    isPendingSync = existing != null && (existing.id in pendingInstallmentIds),
                                    cardAccentIndex = index,
                                    onAmountChange = { newText ->
                                        amountInputs[loan.id] = newText
                                    },
                                    onMemberClick = { loanDetailsTarget = loan },
                                    onSubmitLoan = {
                                        val rawAmt = amountInputs[loan.id] ?: BengaliUtils.formatCleanAmount(loan.weeklyInstallment)
                                        val parsedAmt = rawAmt.toDoubleOrNull()
                                        if (parsedAmt == null || parsedAmt <= 0.0) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("অনুগ্রহ করে সঠিক কিস্তির পরিমাণ লিখুন")
                                            }
                                        } else {
                                            viewModel.recordLoanInstallment(
                                                loan = loan,
                                                date = selectedDate,
                                                amount = parsedAmt,
                                                onSuccess = {
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            "${loan.memberName}-এর $selectedDate তারিখের ${BengaliUtils.formatTaka(parsedAmt)} কিস্তি সাবমিট হয়েছে"
                                                        )
                                                    }
                                                },
                                                onError = { err ->
                                                    coroutineScope.launch {
                                                        snackbarHostState.showSnackbar(err)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                )
                                if (index < sortedLoans.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 0.75.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                // -------------------------------------------------------------
                // TAB 1: FULL MATRIX LOAN SHEET (Pinned Serial & Name)
                // -------------------------------------------------------------
                if (sortedLoans.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "এখনো কোনো ঋণ বিতরণ করা হয়নি",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            if (isAdmin) {
                                Button(
                                    onClick = { showNewLoanDialog = true },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldGreenPrimary,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("প্রথম ঋণ বিতরণ করুন", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    WeeklyLoanMatrixTable(
                        loans = sortedLoans,
                        sortedDates = sortedDates,
                        installmentMap = installmentMap,
                        columnTotals = columnTotals,
                        grandTotal = grandTotal,
                        onCellClick = { loan, date ->
                            val normCellDate = DailyAccountCalculator.normalizeDateKey(date)
                            val alreadySubmittedWeek = installments.any {
                                it.loanId == loan.id &&
                                    it.status != "REJECTED" &&
                                    (DailyAccountCalculator.normalizeDateKey(it.installmentDate) == normCellDate ||
                                        SavingsAndFineCalculator.isSameWeek(it.installmentDate, date))
                            }
                            if (alreadySubmittedWeek) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("এই তারিখে ($date) ইতিমধ্যেই কিস্তি সাবমিট করা হয়েছে। এক তারিখে দুইবার সাবমিট করা যাবে না।")
                                }
                            } else if (loan.remainingLoan <= 0.0) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("${loan.memberName}-এর ঋণ সম্পূর্ণ পরিশোধ হয়েছে।")
                                }
                            } else {
                                entryDialogTarget = Pair(loan, date)
                            }
                        },
                        onLoanClick = { loan -> loanDetailsTarget = loan },
                        onDateHeaderClick = if (isAdmin) { date: String -> dateToEdit = date } else null
                    )
                }
            }
        }
    }

    // Installment Entry Dialog for Matrix Table Cell
    entryDialogTarget?.let { (loan, date) ->
        LoanInstallmentDialog(
            loan = loan,
            initialDate = date,
            onDismiss = { entryDialogTarget = null },
            onSave = { amount, chosenDate ->
                viewModel.recordLoanInstallment(
                    loan = loan,
                    date = chosenDate,
                    amount = amount,
                    onSuccess = {
                        entryDialogTarget = null
                        val statusNote = if (role == UserRole.FIELD_OFFICER) "অনুমোদনের জন্য পাঠানো হয়েছে" else null
                        successPopupData = Pair(
                            "${loan.memberName}-এর $chosenDate তারিখের ${BengaliUtils.formatTaka(amount)} কিস্তি সফলভাবে জমা হয়েছে।",
                            statusNote
                        )
                    },
                    onError = { err ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            }
        )
    }

    // Add New Collection Date Dialog
    if (showAddDateDialog) {
        AddCollectionDateDialog(
            currentMonthHeading = appSettings.currentMonthHeading,
            onDismiss = { showAddDateDialog = false },
            onAdd = { newDate ->
                viewModel.addCollectionDate(
                    dateStr = newDate,
                    monthYear = appSettings.currentMonthHeading,
                    onSuccess = {
                        viewModel.setSelectedLoanDate(newDate)
                        showAddDateDialog = false
                    },
                    onError = { err ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            }
        )
    }

    // Update Existing Collection Date Dialog (Admin only)
    dateToEdit?.let { oldDate ->
        EditCollectionDateDialog(
            oldDate = oldDate,
            onDismiss = { dateToEdit = null },
            onSave = { newDate ->
                viewModel.updateCollectionDate(
                    oldDate = oldDate,
                    newDate = newDate,
                    monthYear = appSettings.currentMonthHeading,
                    onSuccess = {
                        if (selectedDate == oldDate) {
                            viewModel.setSelectedLoanDate(newDate)
                        }
                        dateToEdit = null
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("তারিখ '$oldDate' থেকে '$newDate'-তে আপডেট হয়েছে")
                        }
                    },
                    onError = { err ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            }
        )
    }

    // Missed Loan Installment Dialog (+10 BDT Fine on separate Fine Sheet)
    loanToMarkMissed?.let { loan ->
        var missedDateText by remember(loan.id) {
            mutableStateOf(selectedDate.ifBlank { BengaliUtils.getCurrentDateStr() })
        }
        AlertDialog(
            onDismissRequest = { loanToMarkMissed = null },
            title = { Text("বকেয়া কিস্তি ও ৳১০ জরিমানা", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${loan.memberName} (${BengaliUtils.formatTwoDigitSerial(loan.memberSerial)}) এই সপ্তাহে কিস্তি না দিলে পরের সপ্তাহে ৳১০ জরিমানা আলাদা জরিমানা শিটে যুক্ত হবে। মূল ঋণের টাকা অপরিবর্তিত থাকবে।",
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
            isAdmin = isAdmin,
            onMarkMissed = {
                loanDetailsTarget = null
                loanToMarkMissed = loan
            },
            onDismiss = { loanDetailsTarget = null }
        )
    }

    // New Loan Distribution Dialog (Admin only)
    if (isAdmin && showNewLoanDialog) {
        Dialog(
            onDismissRequest = { showNewLoanDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Scaffold(
                topBar = {
                    EkotaCompactTopBar(
                        title = "নতুন ঋণ বিতরণ",
                        navigationIcon = {
                            IconButton(onClick = { showNewLoanDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "বন্ধ করুন")
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(ekotaSilverBackgroundBrush())
                        .padding(16.dp)
                ) {
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
                                    showNewLoanDialog = false
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
                        onCancel = { showNewLoanDialog = false }
                    )
                }
            }
        }
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
