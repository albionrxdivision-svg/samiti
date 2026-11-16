package com.example.ui.fines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FineRecord
import com.example.data.model.FineType
import com.example.data.model.Member
import com.example.data.model.TransactionStatus
import com.example.data.model.UserRole
import com.example.ui.MainViewModel
import com.example.ui.components.EkotaCompactTopBar
import com.example.ui.savings.TableCell
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import com.example.util.SavingsAndFineCalculator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FineSheetScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val members by viewModel.members.collectAsStateWithLifecycle()
    val fines by viewModel.fines.collectAsStateWithLifecycle()
    val role by viewModel.userRole.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Matrix Sheet, 1: List & Collect
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, DUE, PAID, SAVINGS, LOAN
    var cellDialogTarget by remember { mutableStateOf<Pair<Member, String>?>(null) }
    var showAddFineDialog by remember { mutableStateOf(false) }

    val activeFines = remember(fines) {
        fines.filter { it.status != TransactionStatus.REJECTED.name }
    }

    val sortedFineDates = remember(activeFines) {
        val dates = SavingsAndFineCalculator.sortDateStrings(activeFines.map { it.fineDate })
        if (dates.isEmpty()) listOf(BengaliUtils.getCurrentDateStr()) else dates
    }

    val totalAssessed = remember(activeFines) { activeFines.sumOf { it.amount } }
    val totalCollected = remember(activeFines) { activeFines.filter { it.isPaid }.sumOf { it.amount } }
    val totalDue = remember(totalAssessed, totalCollected) { (totalAssessed - totalCollected).coerceAtLeast(0.0) }

    val filteredFinesList = remember(activeFines, selectedFilter) {
        when (selectedFilter) {
            "DUE" -> activeFines.filter { !it.isPaid }
            "PAID" -> activeFines.filter { it.isPaid }
            "SAVINGS" -> activeFines.filter { it.fineType == FineType.SAVINGS.name }
            "LOAN" -> activeFines.filter { it.fineType == FineType.LOAN.name }
            else -> activeFines
        }.sortedByDescending { SavingsAndFineCalculator.parseDateMillis(it.fineDate) ?: 0L }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            EkotaCompactTopBar(
                title = "জরিমানা শিট (সঞ্চয় ও ঋণ)",
                subtitle = "ধার্য: ${BengaliUtils.formatTaka(totalAssessed)} | বাকি: ${BengaliUtils.formatTaka(totalDue)}",
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("fine_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddFineDialog = true },
                        modifier = Modifier.testTag("top_add_fine_button")
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "নতুন জরিমানা যোগ")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddFineDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("জরিমানা যোগ (৳১০)", fontWeight = FontWeight.Bold) },
                containerColor = CardOrangeGradient.first(),
                contentColor = Color.White,
                modifier = Modifier.testTag("add_fine_fab")
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ekotaSilverBackgroundBrush())
        ) {
            // Separate Fund Summary Card (Vibrant Gradient)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(colors = CardOrangeGradient))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FineSummaryMetric("মোট জরিমানা", BengaliUtils.formatTaka(totalAssessed), Color.White, Modifier.weight(1f))
                            FineSummaryMetric("জরিমানা আদায়", BengaliUtils.formatTaka(totalCollected), Color.White, Modifier.weight(1f))
                            FineSummaryMetric("বাকি জরিমানা", BengaliUtils.formatTaka(totalDue), Color.White, Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.28f))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "বিঃদ্রঃ সাপ্তাহিক সঞ্চয় বা ঋণের কিস্তি না দিলে পরের সপ্তাহে ১০ টাকা জরিমানা হয় এবং এই টাকা মূল সঞ্চয় ও ঋণের হিসাব থেকে সম্পূর্ণ আলাদা থাকে।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.95f)
                        )
                    }
                }
            }

            // Tab Switcher
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = HeaderSlateNavy
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    selectedContentColor = HeaderSlateNavy,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                    text = {
                        Text(
                            text = "জরিমানা শিট",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("tab_fine_matrix")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    selectedContentColor = HeaderSlateNavy,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                    text = {
                        Text(
                            text = "আদায় ও তালিকা (${BengaliUtils.toBengaliDigits(activeFines.size)})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.testTag("tab_fine_list")
                )
            }

            val sheetMembers = remember(members, activeFines) {
                val existingIds = members.map { it.id }.toSet()
                val existingNames = members.map { it.name }.toSet()
                val extraLoanBorrowers = activeFines
                    .filter { it.memberId !in existingIds && it.memberName !in existingNames }
                    .distinctBy { it.memberId.ifBlank { it.memberName } }
                    .map {
                        Member(
                            id = it.memberId.ifBlank { it.memberName },
                            memberSerial = it.memberSerial.ifBlank { "ঋণ" },
                            name = it.memberName
                        )
                    }
                members + extraLoanBorrowers
            }

            if (selectedTab == 0) {
                if (sheetMembers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "জরিমানা শিট দেখার জন্য প্রথমে সদস্য বা ঋণ যোগ করুন",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (role == UserRole.ADMIN)
                                    "ক্রমিক নং ও নাম বামে স্থির থাকবে। যেকোনো ঘরের উপর ট্যাপ করে জরিমানা আদায়/বাকি পরিবর্তন করুন।"
                                else
                                    "ক্রমিক নং ও নাম বামে স্থির থাকবে। পূর্বের জরিমানা এডিট বা ডিলিট শুধুমাত্র অ্যাডমিন করতে পারবেন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    FineMatrixTable(
                        members = sheetMembers,
                        sortedDates = sortedFineDates,
                        fines = activeFines,
                        onCellClick = { member, date -> cellDialogTarget = Pair(member, date) }
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf(
                            "ALL" to "সব (${BengaliUtils.toBengaliDigits(activeFines.size)})",
                            "DUE" to "বাকি (${BengaliUtils.toBengaliDigits(activeFines.count { !it.isPaid })})",
                            "PAID" to "আদায় (${BengaliUtils.toBengaliDigits(activeFines.count { it.isPaid })})",
                            "SAVINGS" to "সঞ্চয় জরিমানা",
                            "LOAN" to "ঋণ জরিমানা"
                        )
                        filters.forEach { (key, label) ->
                            FilterChip(
                                selected = selectedFilter == key,
                                onClick = { selectedFilter = key },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldGreenPrimary,
                                    selectedLabelColor = Color.White,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }

                    if (filteredFinesList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "কোনো জরিমানা রেকর্ড পাওয়া যায়নি",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredFinesList, key = { it.id }) { fine ->
                                FineRecordCard(
                                    fine = fine,
                                    isAdmin = role == UserRole.ADMIN,
                                    onTogglePaid = { isPaid ->
                                        viewModel.toggleFinePaid(fine, isPaid)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    cellDialogTarget?.let { (member, date) ->
        val cellFines = activeFines.filter {
            (it.memberId == member.id || it.memberName == member.name) && it.fineDate == date
        }
        FineCellDetailDialog(
            member = member,
            date = date,
            finesOnDate = cellFines,
            isAdmin = role == UserRole.ADMIN,
            onDismiss = { cellDialogTarget = null },
            onTogglePaid = { fine, isPaid -> viewModel.toggleFinePaid(fine, isPaid) },
            onAddFine = { fineType, amount ->
                viewModel.addManualFine(
                    member = member,
                    fineType = fineType,
                    missedDate = date,
                    fineDate = date,
                    amount = amount,
                    isPaid = false,
                    onSuccess = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("${member.name}-এর ${BengaliUtils.formatTaka(amount)} জরিমানা যোগ হয়েছে")
                        }
                    },
                    onError = { err ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            }
        )
    }

    if (showAddFineDialog) {
        AddFineDialog(
            members = members,
            onDismiss = { showAddFineDialog = false },
            onSave = { member, fineType, missedDate, fineDate, amount, isPaid ->
                viewModel.addManualFine(
                    member = member,
                    fineType = fineType,
                    missedDate = missedDate,
                    fineDate = fineDate,
                    amount = amount,
                    isPaid = isPaid,
                    onSuccess = {
                        showAddFineDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("${member.name}-এর ${BengaliUtils.formatTaka(amount)} জরিমানা সংরক্ষিত হয়েছে")
                        }
                    },
                    onError = { err ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            }
        )
    }
}

@Composable
private fun FineSummaryMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
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
        Spacer(modifier = Modifier.height(2.dp))
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
fun FineMatrixTable(
    members: List<Member>,
    sortedDates: List<String>,
    fines: List<FineRecord>,
    onCellClick: (Member, String) -> Unit
) {
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    val columnTotals = remember(sortedDates, fines, members) {
        sortedDates.associateWith { date ->
            fines.filter { it.fineDate == date }.sumOf { it.amount }
        }
    }
    val totalSavingsFines = remember(fines) {
        fines.filter { it.fineType == FineType.SAVINGS.name }.sumOf { it.amount }
    }
    val totalLoanFines = remember(fines) {
        fines.filter { it.fineType == FineType.LOAN.name }.sumOf { it.amount }
    }
    val grandFineTotal = remember(fines) { fines.sumOf { it.amount } }
    val grandDueTotal = remember(fines) { fines.filter { !it.isPaid }.sumOf { it.amount } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(verticalScrollState)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // LEFT PINNED PANE (Serial + Name)
                Column(
                    modifier = Modifier
                        .width(192.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(HeaderSlateNavy),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell(text = "ক্রমিক", width = 64.dp, isHeader = true)
                        TableCell(text = "নাম", width = 128.dp, isHeader = true, textAlign = TextAlign.Start)
                    }

                    members.forEachIndexed { index, member ->
                        val isEven = index % 2 == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .background(
                                    if (isEven) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableCell(
                                text = BengaliUtils.toBengaliDigits(member.memberSerial),
                                width = 64.dp,
                                fontWeight = FontWeight.Bold,
                                textColor = MaterialTheme.colorScheme.primary
                            )
                            TableCell(
                                text = member.name,
                                width = 128.dp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Start,
                                textColor = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(HeaderSlateNavy),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell(
                            text = "মোট জরিমানা=",
                            width = 192.dp,
                            isHeader = true,
                            textColor = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // RIGHT SCROLLABLE PANE (Dates + Breakdown Totals)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier
                            .height(50.dp)
                            .background(HeaderSlateNavy),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        sortedDates.forEach { date ->
                            TableCell(text = date, width = 100.dp, isHeader = true)
                        }
                        TableCell(text = "সঞ্চয় জরি.", width = 98.dp, isHeader = true)
                        TableCell(text = "ঋণ জরি.", width = 96.dp, isHeader = true)
                        TableCell(text = "মোট জরি.", width = 102.dp, isHeader = true)
                        TableCell(text = "বাকি জরি.", width = 102.dp, isHeader = true)
                    }

                    members.forEachIndexed { index, member ->
                        val isEven = index % 2 == 0
                        val memberFines = fines.filter {
                            it.memberId == member.id || it.memberName == member.name
                        }
                        val memberSavFine = memberFines.filter { it.fineType == FineType.SAVINGS.name }.sumOf { it.amount }
                        val memberLoanFine = memberFines.filter { it.fineType == FineType.LOAN.name }.sumOf { it.amount }
                        val memberTotalFine = memberFines.sumOf { it.amount }
                        val memberDueFine = memberFines.filter { !it.isPaid }.sumOf { it.amount }

                        Row(
                            modifier = Modifier
                                .height(52.dp)
                                .background(
                                    if (isEven) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            sortedDates.forEach { date ->
                                val dateFines = memberFines.filter { it.fineDate == date }
                                val cellAmt = dateFines.sumOf { it.amount }
                                val hasAny = dateFines.isNotEmpty()
                                val allPaid = hasAny && dateFines.all { it.isPaid }
                                val hasSav = dateFines.any { it.fineType == FineType.SAVINGS.name }
                                val hasLoan = dateFines.any { it.fineType == FineType.LOAN.name }
                                val tagLabel = when {
                                    hasSav && hasLoan -> "স+ঋ"
                                    hasSav -> "স"
                                    hasLoan -> "ঋ"
                                    else -> ""
                                }
                                val displayText = when {
                                    !hasAny -> "-"
                                    allPaid -> "✓ ${BengaliUtils.toBengaliDigits(cellAmt.toInt())} ($tagLabel)"
                                    else -> "${BengaliUtils.toBengaliDigits(cellAmt.toInt())} ($tagLabel)"
                                }
                                val cellBg = when {
                                    !hasAny -> MaterialTheme.colorScheme.surfaceVariant
                                    allPaid -> ekotaBadgeSuccessBg()
                                    else -> ekotaBadgeDangerBg()
                                }
                                val cellFg = when {
                                    !hasAny -> MaterialTheme.colorScheme.onSurfaceVariant
                                    allPaid -> ekotaSuccessTextColor()
                                    else -> ekotaDangerTextColor()
                                }

                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .fillMaxHeight()
                                        .clickable { onCellClick(member, date) }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = cellBg,
                                        border = BorderStroke(
                                            0.5.dp,
                                            if (hasAny) cellFg.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = displayText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (hasAny) FontWeight.Bold else FontWeight.Medium,
                                                color = cellFg,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            TableCell(
                                text = BengaliUtils.formatTaka(memberSavFine),
                                width = 98.dp,
                                fontWeight = FontWeight.Medium,
                                textColor = MaterialTheme.colorScheme.onSurface
                            )
                            TableCell(
                                text = BengaliUtils.formatTaka(memberLoanFine),
                                width = 96.dp,
                                fontWeight = FontWeight.Medium,
                                textColor = MaterialTheme.colorScheme.onSurface
                            )
                            TableCell(
                                text = BengaliUtils.formatTaka(memberTotalFine),
                                width = 102.dp,
                                fontWeight = FontWeight.Bold,
                                textColor = MaterialTheme.colorScheme.primary
                            )
                            TableCell(
                                text = BengaliUtils.formatTaka(memberDueFine),
                                width = 102.dp,
                                fontWeight = FontWeight.Bold,
                                textColor = if (memberDueFine > 0) ekotaDangerTextColor() else ekotaSuccessTextColor()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .height(50.dp)
                            .background(NavyBlueSecondary),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        sortedDates.forEach { date ->
                            val colTotal = columnTotals[date] ?: 0.0
                            TableCell(
                                text = BengaliUtils.toBengaliDigits(colTotal.toInt()),
                                width = 100.dp,
                                isHeader = true,
                                textColor = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TableCell(
                            text = BengaliUtils.formatTaka(totalSavingsFines),
                            width = 98.dp,
                            isHeader = true,
                            textColor = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        TableCell(
                            text = BengaliUtils.formatTaka(totalLoanFines),
                            width = 96.dp,
                            isHeader = true,
                            textColor = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        TableCell(
                            text = BengaliUtils.formatTaka(grandFineTotal),
                            width = 102.dp,
                            isHeader = true,
                            textColor = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        TableCell(
                            text = BengaliUtils.formatTaka(grandDueTotal),
                            width = 102.dp,
                            isHeader = true,
                            textColor = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FineRecordCard(
    fine: FineRecord,
    isAdmin: Boolean,
    onTogglePaid: (Boolean) -> Unit
) {
    val isSavingsFine = fine.fineType == FineType.SAVINGS.name
    val fineAccent = if (fine.isPaid) VibrantCardAccents[2] else VibrantCardAccents[7]
    val isDark = LocalIsDarkEkotaTheme.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.surface else fineAccent.softSurface.copy(alpha = 0.75f)
        ),
        border = BorderStroke(1.dp, fineAccent.gradient.first().copy(alpha = 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(fineAccent.gradient)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = BengaliUtils.toBengaliDigits(fine.memberSerial),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fine.memberName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSavingsFine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = if (isSavingsFine) "সঞ্চয় জরিমানা" else "ঋণ জরিমানা",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSavingsFine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = BengaliUtils.formatTaka(fine.amount),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (fine.isPaid) ekotaSuccessTextColor() else ekotaDangerTextColor(),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "বকেয়া: ${fine.missedDate} • ধার্য: ${fine.fineDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isAdmin) {
                    if (fine.isPaid) {
                        OutlinedButton(
                            onClick = { onTogglePaid(false) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ekotaSuccessTextColor()),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 42.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("আদায় ✓", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    } else {
                        Button(
                            onClick = { onTogglePaid(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGreenPrimary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 42.dp)
                        ) {
                            Text("আদায় করুন", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (fine.isPaid) ekotaBadgeSuccessBg() else ekotaBadgeDangerBg()
                    ) {
                        Text(
                            text = if (fine.isPaid) "আদায় ✓" else "বাকি",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (fine.isPaid) ekotaSuccessTextColor() else ekotaDangerTextColor(),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FineCellDetailDialog(
    member: Member,
    date: String,
    finesOnDate: List<FineRecord>,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onTogglePaid: (FineRecord, Boolean) -> Unit,
    onAddFine: (FineType, Double) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${member.name} (${BengaliUtils.toBengaliDigits(member.memberSerial)}) - $date",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (finesOnDate.isEmpty()) {
                    Text(
                        text = "এই তারিখে কোনো জরিমানা নেই। চাইলে সঞ্চয় বা ঋণের ৳১০ জরিমানা যোগ করতে পারেন:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    finesOnDate.forEach { fine ->
                        val isSav = fine.fineType == FineType.SAVINGS.name
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isSav) "সঞ্চয় জরিমানা (৳১০)" else "ঋণ জরিমানা (৳১০)",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "বকেয়া: ${fine.missedDate} • ${BengaliUtils.formatTaka(fine.amount)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (isAdmin) {
                                        Button(
                                            onClick = { onTogglePaid(fine, !fine.isPaid) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (fine.isPaid) NavyBlueSecondary else EmeraldGreenPrimary,
                                                contentColor = Color.White
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(if (fine.isPaid) "বাকি করুন" else "আদায় ✓", maxLines = 1, softWrap = false)
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (fine.isPaid) ekotaBadgeSuccessBg() else ekotaBadgeDangerBg()
                                        ) {
                                            Text(
                                                text = if (fine.isPaid) "আদায় ✓" else "বাকি",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (fine.isPaid) ekotaSuccessTextColor() else ekotaDangerTextColor(),
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text("নতুন ৳১০ জরিমানা যোগ করুন:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onAddFine(FineType.SAVINGS, SavingsAndFineCalculator.DEFAULT_WEEKLY_FINE_AMOUNT) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ সঞ্চয় ৳১০", maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(
                        onClick = { onAddFine(FineType.LOAN, SavingsAndFineCalculator.DEFAULT_WEEKLY_FINE_AMOUNT) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ ঋণ ৳১০", maxLines = 1, softWrap = false)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFineDialog(
    members: List<Member>,
    onDismiss: () -> Unit,
    onSave: (Member, FineType, String, String, Double, Boolean) -> Unit
) {
    var selectedMember by remember { mutableStateOf(members.firstOrNull()) }
    var memberDropdownExpanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(FineType.SAVINGS) }
    var missedDate by remember { mutableStateOf(BengaliUtils.getCurrentDateStr()) }
    var fineDate by remember { mutableStateOf(BengaliUtils.getDateAfterDaysStr(7)) }
    var amountText by remember { mutableStateOf("10") }
    var isPaid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন জরিমানা যোগ (আলাদা খাত)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(
                    expanded = memberDropdownExpanded,
                    onExpandedChange = { memberDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedMember?.let { "${BengaliUtils.toBengaliDigits(it.memberSerial)}. ${it.name}" } ?: "সদস্য নির্বাচন করুন",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("সদস্য") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = memberDropdownExpanded,
                        onDismissRequest = { memberDropdownExpanded = false }
                    ) {
                        members.forEach { m ->
                            DropdownMenuItem(
                                text = { Text("${BengaliUtils.toBengaliDigits(m.memberSerial)}. ${m.name}") },
                                onClick = {
                                    selectedMember = m
                                    memberDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedType == FineType.SAVINGS,
                        onClick = { selectedType = FineType.SAVINGS },
                        label = { Text("সঞ্চয় জরিমানা", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreenPrimary,
                            selectedLabelColor = Color.White,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == FineType.LOAN,
                        onClick = { selectedType = FineType.LOAN },
                        label = { Text("ঋণ জরিমানা", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NavyBlueSecondary,
                            selectedLabelColor = Color.White,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = missedDate,
                    onValueChange = { missedDate = it },
                    label = { Text("যে সপ্তাহে দেয়নি (তারিখ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = fineDate,
                    onValueChange = { fineDate = it },
                    label = { Text("জরিমানা ধার্যের তারিখ (পরের সপ্তাহ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("জরিমানার পরিমাণ (টাকা)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isPaid = !isPaid }
                ) {
                    Checkbox(checked = isPaid, onCheckedChange = { isPaid = it })
                    Text("ইতিমধ্যে আদায় হয়েছে (Paid)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val m = selectedMember ?: return@Button
                    val amt = amountText.toDoubleOrNull() ?: SavingsAndFineCalculator.DEFAULT_WEEKLY_FINE_AMOUNT
                    if (amt > 0 && fineDate.isNotBlank()) {
                        onSave(m, selectedType, missedDate, fineDate, amt, isPaid)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreenPrimary,
                    contentColor = Color.White
                )
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}
