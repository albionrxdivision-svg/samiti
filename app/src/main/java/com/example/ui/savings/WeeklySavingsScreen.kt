package com.example.ui.savings

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
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.data.model.UserRole
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.components.EkotaCompactTopBar
import com.example.ui.components.SuccessPopupDialog
import com.example.ui.members.MemberFormDialog
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import com.example.util.SavingsAndFineCalculator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WeeklySavingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToFines: () -> Unit = {}
) {
    val membersUiState by viewModel.membersUiState.collectAsStateWithLifecycle()
    val membersFallback by viewModel.members.collectAsStateWithLifecycle()
    val savingsRecords by viewModel.savingsRecords.collectAsStateWithLifecycle()
    val collectionPeriods by viewModel.collectionPeriods.collectAsStateWithLifecycle()
    val role by viewModel.userRole.collectAsStateWithLifecycle()
    val currentUserProfile by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val pendingSavingsIds by viewModel.pendingSavingsIds.collectAsStateWithLifecycle()
    val isAdmin = role == UserRole.ADMIN

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 0: Collection Form (default), 1: Savings Matrix Sheet
    var selectedModeTab by remember { mutableIntStateOf(0) }

    var showAddDateDialog by remember { mutableStateOf(false) }
    var dateToEdit by remember { mutableStateOf<String?>(null) }
    var entryDialogTarget by remember { mutableStateOf<Pair<Member, String>?>(null) }
    var editHeadingDialog by remember { mutableStateOf(false) }
    var memberToEdit by remember { mutableStateOf<Member?>(null) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showTopMoreMenu by remember { mutableStateOf(false) }
    var successPopupData by remember { mutableStateOf<Pair<String, String?>?>(null) }

    // Available collection dates sorted chronologically (defaulting to today for first entry)
    val sortedDates = remember(collectionPeriods, savingsRecords) {
        val periodDates = collectionPeriods.map { SavingsAndFineCalculator.normalizeDateStr(it.dateStr) }
        val recordDates = savingsRecords.map { SavingsAndFineCalculator.normalizeDateStr(it.collectionDate) }
        val combined = SavingsAndFineCalculator.sortDateStrings(periodDates + recordDates)
        if (combined.isEmpty()) listOf(BengaliUtils.getCurrentDateStr()) else combined
    }

    val selectedDate by viewModel.selectedSavingsDate.collectAsStateWithLifecycle()
    LaunchedEffect(sortedDates) {
        if (selectedDate !in sortedDates && sortedDates.isNotEmpty()) {
            viewModel.setSelectedSavingsDate(sortedDates.last())
        }
    }

    // Active members resolved from UiState (Firestore)
    val members = when (val state = membersUiState) {
        is UiState.Success -> state.data
        else -> membersFallback
    }
    val activeMembers = remember(members) {
        members.filter { it.isActive }
    }

    // Map: (memberId, date) -> SavingsRecord (supports raw + normalized date keys)
    val savingsMap = remember(savingsRecords) {
        val map = mutableMapOf<String, SavingsRecord>()
        for (rec in savingsRecords) {
            val normDate = com.example.util.DailyAccountCalculator.normalizeDateKey(rec.collectionDate)
            map["${rec.memberId}_${rec.collectionDate}"] = rec
            map["${rec.memberName}_${rec.collectionDate}"] = rec
            if (normDate.isNotBlank()) {
                map["${rec.memberId}_$normDate"] = rec
                map["${rec.memberName}_$normDate"] = rec
            }
        }
        map
    }

    val normalizedSelectedDate = remember(selectedDate) {
        com.example.util.DailyAccountCalculator.normalizeDateKey(selectedDate).ifBlank { selectedDate }
    }

    // Editable amounts map for the Collection Form: memberId -> String input
    val amountInputs = remember { mutableStateMapOf<String, String>() }

    // Sync/pre-populate form inputs whenever selectedDate, members, or savingsRecords change
    LaunchedEffect(selectedDate, normalizedSelectedDate, activeMembers, savingsRecords) {
        activeMembers.forEach { member ->
            val existing = savingsMap["${member.id}_$selectedDate"]
                ?: savingsMap["${member.name}_$selectedDate"]
                ?: savingsMap["${member.id}_$normalizedSelectedDate"]
                ?: savingsMap["${member.name}_$normalizedSelectedDate"]
            val initialText = when {
                existing != null && existing.amount > 0.0 -> existing.amount.toInt().toString()
                existing != null && !existing.isPaid -> "0"
                else -> member.weeklySavingsAmount.toInt().toString()
            }
            amountInputs[member.id] = initialText
        }
    }

    // Column totals calculation for matrix sheet
    val columnTotals = remember(sortedDates, savingsRecords, members) {
        sortedDates.associateWith { date ->
            val normDate = com.example.util.DailyAccountCalculator.normalizeDateKey(date)
            members.sumOf { member ->
                val rec = savingsMap["${member.id}_$date"]
                    ?: savingsMap["${member.name}_$date"]
                    ?: savingsMap["${member.id}_$normDate"]
                    ?: savingsMap["${member.name}_$normDate"]
                if (rec != null && rec.isPaid && rec.status != "REJECTED") rec.amount else 0.0
            }
        }
    }

    val grandTotal = remember(columnTotals) { columnTotals.values.sum() }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            EkotaCompactTopBar(
                title = settings.currentMonthHeading,
                subtitle = "সদস্য: ${BengaliUtils.toBengaliDigits(activeMembers.size)} জন | মোট: ${BengaliUtils.formatTaka(grandTotal)}",
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
                        modifier = Modifier.testTag("refresh_members_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Firestore থেকে রিফ্রেশ")
                    }
                    IconButton(
                        onClick = { showAddMemberDialog = true },
                        modifier = Modifier.testTag("add_member_button")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "নতুন সদস্য যোগ")
                    }
                    if (isAdmin) {
                        Box {
                            IconButton(
                                onClick = { showTopMoreMenu = true },
                                modifier = Modifier.testTag("savings_more_actions_button")
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
                                    modifier = Modifier.testTag("add_date_button"),
                                    onClick = {
                                        showTopMoreMenu = false
                                        showAddDateDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("শিরোনাম পরিবর্তন") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    modifier = Modifier.testTag("edit_heading_button"),
                                    onClick = {
                                        showTopMoreMenu = false
                                        editHeadingDialog = true
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
            // Mode Switch Tabs: Collection Form vs Savings Sheet
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
                    modifier = Modifier.testTag("tab_collection_form"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = "সাপ্তাহিক সংগ্রহ ফর্ম",
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
                    modifier = Modifier.testTag("tab_savings_sheet"),
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                text = "সঞ্চয় শিট (${BengaliUtils.toBengaliDigits(sortedDates.size)} সপ্তাহ)",
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
                // TAB 0: WEEKLY SAVINGS COLLECTION FORM (Firestore Members)
                // -------------------------------------------------------------
                Column(modifier = Modifier.fillMaxSize()) {
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
                                                modifier = Modifier.testTag("savings_to_fines_chip")
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
                                                modifier = Modifier.testTag("savings_sync_status_chip")
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
                                                onClick = { viewModel.setSelectedSavingsDate(date) },
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
                                                modifier = Modifier.testTag("date_chip_$date")
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
                                                modifier = Modifier.testTag("chip_edit_selected_date")
                                            )
                                            AssistChip(
                                                onClick = { showAddDateDialog = true },
                                                label = {
                                                    Text(
                                                        text = "+ নতুন তারিখ",
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                modifier = Modifier.testTag("chip_add_new_date")
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Member List Form Content driven by UiState
                        when (val state = membersUiState) {
                            is UiState.Loading -> {
                                dateControlsContent()
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(color = EmeraldGreenPrimary)
                                        Text(
                                            text = "Firestore থেকে সদস্য তালিকা আনা হচ্ছে...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            is UiState.Error -> {
                                dateControlsContent()
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(40.dp)
                                            )
                                            Text(
                                                text = state.message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                textAlign = TextAlign.Center
                                            )
                                            Button(
                                                onClick = { viewModel.refreshMembersFromFirestore() },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = EmeraldGreenPrimary,
                                                    contentColor = Color.White
                                                )
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("পুনরায় চেষ্টা করুন")
                                            }
                                        }
                                    }
                                }
                            }

                            is UiState.Success -> {
                                if (activeMembers.isEmpty()) {
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
                                                imageVector = Icons.Default.GroupOff,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Text(
                                                text = "কোনো সক্রিয় সদস্য পাওয়া যায়নি",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Button(
                                                onClick = { showAddMemberDialog = true },
                                                modifier = Modifier.heightIn(min = 48.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = EmeraldGreenPrimary,
                                                    contentColor = Color.White
                                                )
                                            ) {
                                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("নতুন সদস্য যোগ করুন", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .testTag("weekly_savings_form_list")
                                    ) {
                                        item(key = "date_controls_header") {
                                            dateControlsContent()
                                        }
                                        stickyHeader(key = "savings_list_header") {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp)
                                                    .padding(top = 6.dp)
                                                    .background(HeaderSlateNavy, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                                    .testTag("savings_list_header"),
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
                                        itemsIndexed(activeMembers, key = { _, m -> m.id }) { index, member ->
                                            val existing = savingsMap["${member.id}_$selectedDate"]
                                                ?: savingsMap["${member.name}_$selectedDate"]
                                                ?: savingsMap["${member.id}_$normalizedSelectedDate"]
                                                ?: savingsMap["${member.name}_$normalizedSelectedDate"]
                                            val submittedThisWeek = remember(member.id, selectedDate, normalizedSelectedDate, savingsRecords) {
                                                savingsRecords.any {
                                                    (it.memberId == member.id || it.memberName == member.name) &&
                                                        it.status != "REJECTED" &&
                                                        (com.example.util.DailyAccountCalculator.normalizeDateKey(it.collectionDate) == normalizedSelectedDate ||
                                                            SavingsAndFineCalculator.isSameWeek(it.collectionDate, selectedDate))
                                                }
                                            }
                                            val missedEarlier = remember(member.id, selectedDate, sortedDates, savingsRecords) {
                                                SavingsAndFineCalculator.findMissedEarlierSavingsDates(
                                                    member = member,
                                                    targetDate = selectedDate,
                                                    orderedDates = sortedDates,
                                                    existingRecords = savingsRecords
                                                )
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp)
                                            ) {
                                                MemberSavingsInputCard(
                                                    member = member,
                                                    amountText = amountInputs[member.id] ?: BengaliUtils.formatCleanAmount(member.weeklySavingsAmount),
                                                    existingRecord = existing,
                                                    missedDatesCount = missedEarlier.size,
                                                    isAdmin = isAdmin,
                                                    isSubmittedThisWeek = submittedThisWeek,
                                                    isPendingSync = existing != null && (existing.id in pendingSavingsIds),
                                                    cardAccentIndex = index,
                                                    onAmountChange = { newText ->
                                                        amountInputs[member.id] = newText
                                                    },
                                                    onSubmitMember = {
                                                        val rawAmt = amountInputs[member.id] ?: BengaliUtils.formatCleanAmount(member.weeklySavingsAmount)
                                                        val parsedAmt = rawAmt.toDoubleOrNull()
                                                        if (parsedAmt == null || parsedAmt < 0.0) {
                                                            coroutineScope.launch {
                                                                snackbarHostState.showSnackbar("অনুগ্রহ করে সঠিক টাকার পরিমাণ লিখুন")
                                                            }
                                                        } else {
                                                            viewModel.recordSavings(
                                                                member = member,
                                                                collectionDate = selectedDate,
                                                                amount = parsedAmt,
                                                                onSuccess = {
                                                                    coroutineScope.launch {
                                                                        snackbarHostState.showSnackbar(
                                                                            "${member.name}-এর $selectedDate তারিখের ${BengaliUtils.formatTaka(parsedAmt)} সঞ্চয় সাবমিট হয়েছে"
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
                                                if (index < activeMembers.lastIndex) {
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
                            }
                        }
                }
            } else {
                // -------------------------------------------------------------
                // TAB 1: FULL MATRIX SAVINGS SHEET (Pinned Serial & Name)
                // -------------------------------------------------------------
                if (members.isEmpty()) {
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
                                text = "এখনো কোনো সদস্য যোগ করা হয়নি",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { showAddMemberDialog = true },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldGreenPrimary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("প্রথম সদস্য যোগ করুন", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    WeeklySavingsMatrixTable(
                        members = members,
                        sortedDates = sortedDates,
                        savingsMap = savingsMap,
                        columnTotals = columnTotals,
                        grandTotal = grandTotal,
                        onCellClick = { member, date ->
                            val normCellDate = com.example.util.DailyAccountCalculator.normalizeDateKey(date)
                            val alreadySubmittedWeek = savingsRecords.any {
                                (it.memberId == member.id || it.memberName == member.name) &&
                                    it.status != "REJECTED" &&
                                    (com.example.util.DailyAccountCalculator.normalizeDateKey(it.collectionDate) == normCellDate ||
                                        SavingsAndFineCalculator.isSameWeek(it.collectionDate, date))
                            }
                            if (alreadySubmittedWeek) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("এই তারিখে ($date) ইতিমধ্যেই সঞ্চয় সাবমিট করা হয়েছে। এক তারিখে দুইবার সাবমিট করা যাবে না।")
                                }
                            } else {
                                entryDialogTarget = Pair(member, date)
                            }
                        },
                        onMemberClick = { member ->
                            if (isAdmin) {
                                memberToEdit = member
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("সদস্যের তথ্য সংশোধন শুধুমাত্র অ্যাডমিন করতে পারবেন")
                                }
                            }
                        },
                        onDateHeaderClick = if (isAdmin) { date: String -> dateToEdit = date } else null
                    )
                }
            }
        }
    }

    // Savings Entry / Edit Dialog for Matrix Table Cell
    entryDialogTarget?.let { (member, date) ->
        val currentRecord = savingsMap["${member.id}_$date"] ?: savingsMap["${member.name}_$date"]
        val existingAmount = currentRecord?.amount ?: member.weeklySavingsAmount
        SavingsEntryDialog(
            member = member,
            date = date,
            currentAmount = existingAmount,
            onDismiss = { entryDialogTarget = null },
            onSave = { amount ->
                viewModel.recordSavings(
                    member = member,
                    collectionDate = date,
                    amount = amount,
                    onSuccess = {
                        entryDialogTarget = null
                        val statusNote = if (role == UserRole.FIELD_OFFICER) "অনুমোদনের জন্য পাঠানো হয়েছে" else null
                        successPopupData = Pair(
                            "${member.name}-এর $date তারিখের ${BengaliUtils.formatTaka(amount)} সঞ্চয় সফলভাবে জমা হয়েছে।",
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
            currentMonthHeading = settings.currentMonthHeading,
            onDismiss = { showAddDateDialog = false },
            onAdd = { newDate ->
                viewModel.addCollectionDate(
                    dateStr = newDate,
                    monthYear = settings.currentMonthHeading,
                    onSuccess = {
                        viewModel.setSelectedSavingsDate(newDate)
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
                    monthYear = settings.currentMonthHeading,
                    onSuccess = {
                        if (selectedDate == oldDate) {
                            viewModel.setSelectedSavingsDate(newDate)
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

    // Edit Month Heading Dialog
    if (editHeadingDialog) {
        EditHeadingDialog(
            currentHeading = settings.currentMonthHeading,
            onDismiss = { editHeadingDialog = false },
            onSave = { newHeading ->
                viewModel.updateSettings(
                    settings = settings.copy(currentMonthHeading = newHeading),
                    onSuccess = { editHeadingDialog = false },
                    onError = {}
                )
            }
        )
    }

    // Add Member Dialog from Weekly Savings Screen
    if (showAddMemberDialog) {
        val suggestedSerial = remember(members) { viewModel.suggestNextMemberSerial(members) }
        MemberFormDialog(
            title = "নতুন সদস্য যোগ করুন",
            initialMember = null,
            suggestedSerial = suggestedSerial,
            onDismiss = { showAddMemberDialog = false },
            onSave = { newMember ->
                viewModel.addMember(newMember, onSuccess = { showAddMemberDialog = false }, onError = {})
            }
        )
    }

    // Edit Member Dialog from Table row tap
    memberToEdit?.let { member ->
        MemberFormDialog(
            title = "সদস্য তথ্য সংশোধন (${member.name})",
            initialMember = member,
            onDismiss = { memberToEdit = null },
            onSave = { updated ->
                viewModel.updateMember(updated, onSuccess = { memberToEdit = null }, onError = {})
            }
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
