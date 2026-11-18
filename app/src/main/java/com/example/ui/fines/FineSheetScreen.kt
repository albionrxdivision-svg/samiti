package com.example.ui.fines

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FineRecord
import com.example.data.model.FineType
import com.example.data.model.Member
import com.example.data.model.TransactionStatus
import com.example.data.model.UserRole
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.components.EkotaCompactTopBar
import com.example.ui.savings.AddCollectionDateDialog
import com.example.ui.savings.EditCollectionDateDialog
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import com.example.util.DailyAccountCalculator
import com.example.util.SavingsAndFineCalculator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FineSheetScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val membersUiState by viewModel.membersUiState.collectAsStateWithLifecycle()
    val membersFallback by viewModel.members.collectAsStateWithLifecycle()
    val fines by viewModel.fines.collectAsStateWithLifecycle()
    val collectionPeriods by viewModel.collectionPeriods.collectAsStateWithLifecycle()
    val role by viewModel.userRole.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    val isAdmin = role == UserRole.ADMIN
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 0: Weekly Fine Entry Form (default, identical to WeeklySavingsScreen), 1: Fine Matrix Sheet
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedFineType by remember { mutableStateOf(FineType.SAVINGS) }
    var cellDialogTarget by remember { mutableStateOf<Pair<Member, String>?>(null) }
    var showAddFineDialog by remember { mutableStateOf(false) }
    var showAddDateDialog by remember { mutableStateOf(false) }
    var dateToEdit by remember { mutableStateOf<String?>(null) }
    var showTopMoreMenu by remember { mutableStateOf(false) }

    val baseMembers = when (val state = membersUiState) {
        is UiState.Success -> state.data
        else -> membersFallback
    }

    val activeFines = remember(fines) {
        fines.filter { it.status != TransactionStatus.REJECTED.name }
    }

    val sheetMembers = remember(baseMembers, activeFines) {
        val existingIds = baseMembers.map { it.id }.toSet()
        val existingNames = baseMembers.map { it.name }.toSet()
        val extraBorrowers = activeFines
            .filter { it.memberId !in existingIds && it.memberName !in existingNames }
            .distinctBy { it.memberId.ifBlank { it.memberName } }
            .map {
                Member(
                    id = it.memberId.ifBlank { it.memberName },
                    memberSerial = it.memberSerial.ifBlank { "ঋণ" },
                    name = it.memberName,
                    isActive = true
                )
            }
        (baseMembers.filter { it.isActive } + extraBorrowers).sortedWith(
            compareBy<Member>(
                { it.memberSerial.filter { ch -> ch.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE },
                { it.memberSerial },
                { it.name }
            )
        )
    }

    val sortedFineDates = remember(collectionPeriods, activeFines) {
        val periodDates = collectionPeriods.map { SavingsAndFineCalculator.normalizeDateStr(it.dateStr) }
        val fineDates = activeFines.map { SavingsAndFineCalculator.normalizeDateStr(it.fineDate) }
        val combined = SavingsAndFineCalculator.sortDateStrings(periodDates + fineDates)
        if (combined.isEmpty()) listOf(BengaliUtils.getCurrentDateStr()) else combined
    }

    val selectedDate by viewModel.selectedFineDate.collectAsStateWithLifecycle()
    LaunchedEffect(sortedFineDates) {
        if (selectedDate !in sortedFineDates && sortedFineDates.isNotEmpty()) {
            viewModel.setSelectedFineDate(sortedFineDates.last())
        }
    }

    val normalizedSelectedDate = remember(selectedDate) {
        DailyAccountCalculator.normalizeDateKey(selectedDate).ifBlank { selectedDate }
    }

    val totalAssessed = remember(activeFines) { activeFines.sumOf { it.amount } }
    val totalDue = remember(activeFines) { activeFines.filter { !it.isPaid }.sumOf { it.amount } }

    // Map: "${memberId}_${normalizedDate}_${fineType}" -> FineRecord
    val fineLookupMap = remember(activeFines) {
        val map = mutableMapOf<String, FineRecord>()
        for (fine in activeFines) {
            val normDate = DailyAccountCalculator.normalizeDateKey(fine.fineDate).ifBlank { fine.fineDate }
            if (fine.memberId.isNotBlank()) {
                map["${fine.memberId}_${fine.fineDate}_${fine.fineType}"] = fine
                map["${fine.memberId}_${normDate}_${fine.fineType}"] = fine
            }
            map["${fine.memberName}_${fine.fineDate}_${fine.fineType}"] = fine
            map["${fine.memberName}_${normDate}_${fine.fineType}"] = fine
        }
        map
    }

    // Editable amounts map: memberId -> String
    val amountInputs = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(selectedDate, normalizedSelectedDate, selectedFineType, sheetMembers, activeFines) {
        sheetMembers.forEach { member ->
            val existing = fineLookupMap["${member.id}_${selectedDate}_${selectedFineType.name}"]
                ?: fineLookupMap["${member.id}_${normalizedSelectedDate}_${selectedFineType.name}"]
                ?: fineLookupMap["${member.name}_${selectedDate}_${selectedFineType.name}"]
                ?: fineLookupMap["${member.name}_${normalizedSelectedDate}_${selectedFineType.name}"]
            val initialText = if (existing != null && existing.amount > 0.0) {
                BengaliUtils.formatCleanAmount(existing.amount)
            } else {
                BengaliUtils.formatCleanAmount(SavingsAndFineCalculator.DEFAULT_WEEKLY_FINE_AMOUNT)
            }
            amountInputs[member.id] = initialText
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            EkotaCompactTopBar(
                title = "জরিমানা শিট (সঞ্চয় ও ঋণ)",
                subtitle = "সদস্য: ${BengaliUtils.toBengaliDigits(sheetMembers.size)} জন | মোট: ${BengaliUtils.formatTaka(totalAssessed)} | বাকি: ${BengaliUtils.formatTaka(totalDue)}",
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
                        onClick = { viewModel.refreshMembersFromFirestore() },
                        modifier = Modifier.testTag("refresh_fines_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Firestore থেকে রিফ্রেশ")
                    }
                    IconButton(
                        onClick = { showAddFineDialog = true },
                        modifier = Modifier.testTag("top_add_fine_button")
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "নতুন জরিমানা যোগ")
                    }
                    if (isAdmin) {
                        Box {
                            IconButton(
                                onClick = { showTopMoreMenu = true },
                                modifier = Modifier.testTag("fines_more_actions_button")
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
                                    modifier = Modifier.testTag("add_fine_date_button"),
                                    onClick = {
                                        showTopMoreMenu = false
                                        showAddDateDialog = true
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
            // Mode Switch Tabs: Weekly Fine Form vs Fine Matrix Sheet (identical to WeeklySavingsScreen)
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
                    modifier = Modifier.testTag("tab_fine_collection_form"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = "সাপ্তাহিক জরিমানা ফর্ম",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    selectedContentColor = HeaderSlateNavy,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("tab_fine_sheet"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = "জরিমানা শিট (${BengaliUtils.toBengaliDigits(sortedFineDates.size)} সপ্তাহ)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                )
            }

            if (selectedTab == 0) {
                // -------------------------------------------------------------
                // TAB 0: WEEKLY FINE COLLECTION FORM (SL | নাম | টাকা | জমা)
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
                                        modifier = Modifier.testTag("fines_sync_status_chip")
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
                                sortedFineDates.forEach { date ->
                                    FilterChip(
                                        selected = selectedDate == date,
                                        onClick = { viewModel.setSelectedFineDate(date) },
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
                                        modifier = Modifier.testTag("fine_date_chip_$date")
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
                                        modifier = Modifier.testTag("chip_edit_selected_fine_date")
                                    )
                                    AssistChip(
                                        onClick = { showAddDateDialog = true },
                                        label = {
                                            Text(
                                                text = "+ নতুন তারিখ",
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        modifier = Modifier.testTag("chip_add_new_fine_date")
                                    )
                                }
                            }

                            // Compact Fine Type Selector Row (Savings Fine vs Loan Fine)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = selectedFineType == FineType.SAVINGS,
                                    onClick = { selectedFineType = FineType.SAVINGS },
                                    label = {
                                        Text(
                                            text = "সঞ্চয় জরিমানা (৳১০)",
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = if (selectedFineType == FineType.SAVINGS) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        selectedContainerColor = EmeraldGreenPrimary,
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White,
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("fine_type_chip_savings")
                                )
                                FilterChip(
                                    selected = selectedFineType == FineType.LOAN,
                                    onClick = { selectedFineType = FineType.LOAN },
                                    label = {
                                        Text(
                                            text = "ঋণ জরিমানা (৳১০)",
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = if (selectedFineType == FineType.LOAN) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        selectedContainerColor = NavyBlueSecondary,
                                        selectedLabelColor = Color.White,
                                        selectedLeadingIconColor = Color.White,
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("fine_type_chip_loan")
                                )
                            }
                        }
                    }
                }

                if (sheetMembers.isEmpty()) {
                    dateControlsContent()
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("weekly_fine_form_list")
                    ) {
                        item(key = "fine_date_controls_header") {
                            dateControlsContent()
                        }
                        stickyHeader(key = "fine_list_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(top = 6.dp)
                                    .background(HeaderSlateNavy, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                    .testTag("fine_list_header"),
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

                        itemsIndexed(sheetMembers, key = { _, m -> m.id }) { index, member ->
                            val existing = fineLookupMap["${member.id}_${selectedDate}_${selectedFineType.name}"]
                                ?: fineLookupMap["${member.id}_${normalizedSelectedDate}_${selectedFineType.name}"]
                                ?: fineLookupMap["${member.name}_${selectedDate}_${selectedFineType.name}"]
                                ?: fineLookupMap["${member.name}_${normalizedSelectedDate}_${selectedFineType.name}"]

                            val submittedThisWeek = remember(member.id, selectedDate, normalizedSelectedDate, selectedFineType, activeFines) {
                                activeFines.any {
                                    it.fineType == selectedFineType.name &&
                                        (it.memberId == member.id || (member.id.isBlank() && it.memberName == member.name) || it.memberName == member.name) &&
                                        (DailyAccountCalculator.normalizeDateKey(it.fineDate) == normalizedSelectedDate ||
                                            SavingsAndFineCalculator.isSameWeek(it.fineDate, selectedDate))
                                }
                            }
                            val memberDueTotal = remember(member.id, member.name, activeFines) {
                                activeFines.filter {
                                    !it.isPaid && (it.memberId == member.id || it.memberName == member.name)
                                }.sumOf { it.amount }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                            ) {
                                MemberFineInputRow(
                                    member = member,
                                    amountText = amountInputs[member.id]
                                        ?: BengaliUtils.formatCleanAmount(SavingsAndFineCalculator.DEFAULT_WEEKLY_FINE_AMOUNT),
                                    existingFineOnDate = existing,
                                    memberDueTotal = memberDueTotal,
                                    isSubmittedThisWeek = submittedThisWeek,
                                    cardAccentIndex = index,
                                    onAmountChange = { newText ->
                                        amountInputs[member.id] = newText
                                    },
                                    onMemberClick = {
                                        cellDialogTarget = Pair(member, selectedDate)
                                    },
                                    onSubmitFine = {
                                        val rawAmt = amountInputs[member.id]
                                            ?: BengaliUtils.formatCleanAmount(SavingsAndFineCalculator.DEFAULT_WEEKLY_FINE_AMOUNT)
                                        val parsedAmt = rawAmt.toDoubleOrNull()
                                        if (parsedAmt == null || parsedAmt <= 0.0) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("অনুগ্রহ করে সঠিক জরিমানার পরিমাণ লিখুন")
                                            }
                                        } else {
                                            viewModel.addManualFine(
                                                member = member,
                                                fineType = selectedFineType,
                                                missedDate = selectedDate,
                                                fineDate = selectedDate,
                                                amount = parsedAmt,
                                                isPaid = false,
                                                onSuccess = {
                                                    coroutineScope.launch {
                                                        val label = if (selectedFineType == FineType.SAVINGS) "সঞ্চয়" else "ঋণ"
                                                        snackbarHostState.showSnackbar(
                                                            "${member.name}-এর $selectedDate তারিখের ${BengaliUtils.formatTaka(parsedAmt)} $label জরিমানা সাবমিট হয়েছে"
                                                        )
                                                    }
                                                },
                                                onError = { err ->
                                                    coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                                                }
                                            )
                                        }
                                    }
                                )
                                if (index < sheetMembers.lastIndex) {
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
                // TAB 1: FULL MATRIX FINE SHEET (Pinned SL & Name)
                // -------------------------------------------------------------
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
                                text = if (isAdmin)
                                    "SL ও নাম বামে স্থির থাকবে। যেকোনো ঘরের উপর ট্যাপ করে জরিমানা আদায়/বাকি পরিবর্তন করুন।"
                                else
                                    "SL ও নাম বামে স্থির থাকবে। পূর্বের জরিমানা এডিট বা ডিলিট শুধুমাত্র অ্যাডমিন করতে পারবেন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    FineMatrixTable(
                        members = sheetMembers,
                        sortedDates = sortedFineDates,
                        fines = activeFines,
                        onCellClick = { member, date -> cellDialogTarget = Pair(member, date) },
                        onMemberClick = { member -> cellDialogTarget = Pair(member, selectedDate) },
                        onDateHeaderClick = if (isAdmin) { date: String -> dateToEdit = date } else null
                    )
                }
            }
        }
    }

    cellDialogTarget?.let { (member, date) ->
        val normDate = DailyAccountCalculator.normalizeDateKey(date).ifBlank { date }
        val cellFines = activeFines.filter {
            (it.memberId == member.id || it.memberName == member.name) &&
                (DailyAccountCalculator.normalizeDateKey(it.fineDate) == normDate || it.fineDate == date)
        }
        FineCellDetailDialog(
            member = member,
            date = date,
            finesOnDate = cellFines,
            isAdmin = isAdmin,
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
            members = sheetMembers,
            initialDate = selectedDate,
            initialFineType = selectedFineType,
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

    if (showAddDateDialog) {
        AddCollectionDateDialog(
            currentMonthHeading = appSettings.currentMonthHeading,
            onDismiss = { showAddDateDialog = false },
            onAdd = { newDate ->
                viewModel.addCollectionDate(
                    dateStr = newDate,
                    monthYear = appSettings.currentMonthHeading,
                    onSuccess = {
                        viewModel.setSelectedFineDate(newDate)
                        showAddDateDialog = false
                    },
                    onError = { err ->
                        coroutineScope.launch { snackbarHostState.showSnackbar(err) }
                    }
                )
            }
        )
    }

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
                            viewModel.setSelectedFineDate(newDate)
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
}
