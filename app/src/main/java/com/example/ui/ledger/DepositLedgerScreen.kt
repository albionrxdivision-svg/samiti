package com.example.ui.ledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FundLedgerEntry
import com.example.data.model.UserRole
import com.example.ui.MainViewModel
import com.example.ui.components.EkotaCompactTopBar
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import com.example.util.DailyAccountCalculator
import com.example.util.DailyAccountSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositLedgerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val ledgerEntries by viewModel.fundLedger.collectAsStateWithLifecycle()
    val savingsRecords by viewModel.savingsRecords.collectAsStateWithLifecycle()
    val loanInstallments by viewModel.loanInstallments.collectAsStateWithLifecycle()
    val loans by viewModel.loans.collectAsStateWithLifecycle()
    val role by viewModel.userRole.collectAsStateWithLifecycle()

    var showAddEntryDialog by remember { mutableStateOf(false) }
    // 0: তারিখ অনুযায়ী হিসাব (default), 1: বিস্তারিত লেজার
    var selectedTab by remember { mutableIntStateOf(0) }

    val hisabOverview = remember(savingsRecords, loanInstallments, loans, ledgerEntries) {
        DailyAccountCalculator.buildOverview(
            savingsRecords = savingsRecords,
            loanInstallments = loanInstallments,
            loans = loans,
            fundLedger = ledgerEntries
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            EkotaCompactTopBar(
                title = "হিসাব",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    if (role == UserRole.ADMIN) {
                        IconButton(
                            onClick = { showAddEntryDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("top_add_ledger_entry_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "নতুন হিসাব এন্ট্রি")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        val isDark = LocalIsDarkEkotaTheme.current
        val sheetHeaderBg = if (isDark) Color(0xFF1A4352) else Color(0xFF9ED2E3)
        val sheetHeaderText = if (isDark) Color(0xFFE6F6FA) else Color(0xFF0A1418)
        val sheetSurfaceBg = if (isDark) MaterialTheme.colorScheme.surface else Color.White
        val sheetBorderColor = if (isDark) {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        } else {
            Color(0xFFB7C9D6)
        }
        val chronologicalSummaries = hisabOverview.summariesChronological

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ekotaSilverBackgroundBrush())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode switcher chips: Date-wise Summary Sheet vs Detailed Ledger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "তারিখ অনুযায়ী হিসাব",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HeaderSlateNavy,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hisab_tab_daily")
                )

                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "বিস্তারিত লেনদেন",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HeaderSlateNavy,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hisab_tab_detailed")
                )
            }

            // Overall Top Cash Banner (pinned): ক্যাশ মোট= <balance> টাকা
            val hasSheetRowsBelow = selectedTab == 0 && chronologicalSummaries.isNotEmpty()
            val bannerShape = if (hasSheetRowsBelow) {
                RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
            } else {
                RoundedCornerShape(12.dp)
            }
            Surface(
                shape = bannerShape,
                color = sheetHeaderBg,
                border = BorderStroke(1.dp, sheetBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .then(
                        if (selectedTab == 1) Modifier.padding(bottom = 10.dp) else Modifier
                    )
                    .testTag("hisab_summary_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ক্যাশ মোট=",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = sheetHeaderText,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.56f)
                    )
                    Text(
                        text = BengaliUtils.toBengaliDigits(hisabOverview.currentNetBalance),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = sheetHeaderText,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.weight(0.28f)
                    )
                    Text(
                        text = "টাকা",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = sheetHeaderText,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .weight(0.16f)
                            .padding(start = 8.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("daily_hisab_list"),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                if (selectedTab == 0) {
                if (chronologicalSummaries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.EventNote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "এখনো কোনো তারিখের সঞ্চয় বা ঋণের হিসাব এন্ট্রি হয়নি",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(
                        count = chronologicalSummaries.size,
                        key = { index -> chronologicalSummaries[index].dateKey }
                    ) { index ->
                        val summary = chronologicalSummaries[index]
                        val isFirst = index == 0
                        val isLast = index == chronologicalSummaries.lastIndex
                        val blockShape = if (isLast) {
                            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        } else {
                            RoundedCornerShape(0.dp)
                        }
                        Surface(
                            shape = blockShape,
                            color = sheetSurfaceBg,
                            border = BorderStroke(0.5.dp, sheetBorderColor.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .testTag("daily_hisab_card_${summary.dateKey}")
                        ) {
                            DailyHisabSheetBlock(
                                summary = summary,
                                isFirst = isFirst,
                                isLast = isLast,
                                hasMultipleDates = chronologicalSummaries.size > 1
                            )
                        }
                    }
                }
            } else {
                if (ledgerEntries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "এখনো কোনো লেনদেন এন্ট্রি করা হয়নি",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(ledgerEntries.reversed()) { entry ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .padding(bottom = 8.dp)
                        ) {
                            LedgerEntryCard(entry = entry)
                        }
                    }
                }
            }
            }
        }
    }

    if (showAddEntryDialog) {
        AddLedgerEntryDialog(
            onDismiss = { showAddEntryDialog = false },
            onSave = { date, desc, moneyIn, moneyOut ->
                viewModel.addLedgerEntry(
                    date = date,
                    desc = desc,
                    moneyIn = moneyIn,
                    moneyOut = moneyOut,
                    onSuccess = { showAddEntryDialog = false },
                    onError = {}
                )
            }
        )
    }
}

private data class SheetLineEntry(
    val label: String,
    val amount: Double,
    val isOutflow: Boolean = false
)

@Composable
fun DailyHisabSheetBlock(
    summary: DailyAccountSummary,
    isFirst: Boolean,
    isLast: Boolean,
    hasMultipleDates: Boolean
) {
    val isDark = LocalIsDarkEkotaTheme.current
    val dividerBlue = if (isDark) Color(0xFF60A5FA) else Color(0xFF3A70B5)
    val normalInk = MaterialTheme.colorScheme.onSurface
    val dangerInk = ekotaDangerTextColor()
    val dateLabel = BengaliUtils.toBengaliDigits(summary.dateKey)

    val entryLines = remember(summary) {
        buildList {
            if (summary.savingsTotal > 0.0) {
                add(SheetLineEntry(label = "সঞ্চয়=", amount = summary.savingsTotal))
            }
            if (summary.loanCollectedTotal > 0.0) {
                val loanLabel = if (summary.loanDisbursedTotal > 0.0) "ঋণ আদায়=" else "ঋণ="
                add(SheetLineEntry(label = loanLabel, amount = summary.loanCollectedTotal))
            }
            if (summary.otherInflowTotal > 0.0) {
                add(SheetLineEntry(label = "অন্যান্য জমা=", amount = summary.otherInflowTotal))
            }
            if (summary.loanDisbursedTotal > 0.0) {
                add(
                    SheetLineEntry(
                        label = "ঋণ (প্রদান)=",
                        amount = summary.loanDisbursedTotal,
                        isOutflow = true
                    )
                )
            }
            if (summary.otherOutflowTotal > 0.0) {
                add(
                    SheetLineEntry(
                        label = "অন্যান্য খরচ=",
                        amount = summary.otherOutflowTotal,
                        isOutflow = true
                    )
                )
            }
            if (isEmpty()) {
                add(SheetLineEntry(label = "সঞ্চয়=", amount = 0.0))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        // Entry rows: date appears on the first line of each date block
        entryLines.forEachIndexed { idx, line ->
            val valueColor = if (line.isOutflow) dangerInk else normalInk
            HisabSheetRow(
                dateText = if (idx == 0) dateLabel else "",
                labelText = line.label,
                amountText = BengaliUtils.toBengaliDigits(line.amount),
                unitText = "টাকা",
                isBold = false,
                labelColor = if (line.isOutflow) dangerInk else normalInk,
                amountColor = valueColor,
                unitColor = valueColor
            )
        }

        // First blue horizontal line under date entries
        HorizontalDivider(
            thickness = 2.dp,
            color = dividerBlue,
            modifier = Modifier.padding(vertical = 3.dp)
        )

        // Day net total: মোট আদায়=
        HisabSheetRow(
            dateText = "",
            labelText = "মোট আদায়=",
            amountText = BengaliUtils.toBengaliDigits(summary.netDeposit),
            unitText = "টাকা",
            isBold = true,
            labelColor = normalInk,
            amountColor = if (summary.netDeposit < 0.0) dangerInk else normalInk,
            unitColor = normalInk
        )

        if (!isFirst) {
            // Previous balance carried forward: ক্যাশ জমা=
            HisabSheetRow(
                dateText = "",
                labelText = "ক্যাশ জমা=",
                amountText = BengaliUtils.toBengaliDigits(summary.previousBalance),
                unitText = "টাকা",
                isBold = true,
                labelColor = normalInk,
                amountColor = if (summary.previousBalance < 0.0) dangerInk else normalInk,
                unitColor = normalInk
            )

            // Second blue horizontal line after combining day net + previous cash
            HorizontalDivider(
                thickness = 2.dp,
                color = dividerBlue,
                modifier = Modifier.padding(vertical = 3.dp)
            )

            // Combined running balance (final block shows ক্যাশ মোট=, intermediate shows ক্যাশ জমা=)
            val combinedLabel = if (isLast) "ক্যাশ মোট=" else "ক্যাশ জমা="
            HisabSheetRow(
                dateText = "",
                labelText = combinedLabel,
                amountText = BengaliUtils.toBengaliDigits(summary.runningBalance),
                unitText = "টাকা",
                isBold = true,
                labelColor = normalInk,
                amountColor = if (summary.runningBalance < 0.0) dangerInk else normalInk,
                unitColor = normalInk
            )
        } else if (!hasMultipleDates) {
            // Single-date ledger also closes cleanly with ক্যাশ মোট=
            HorizontalDivider(
                thickness = 2.dp,
                color = dividerBlue,
                modifier = Modifier.padding(vertical = 3.dp)
            )
            HisabSheetRow(
                dateText = "",
                labelText = "ক্যাশ মোট=",
                amountText = BengaliUtils.toBengaliDigits(summary.runningBalance),
                unitText = "টাকা",
                isBold = true,
                labelColor = normalInk,
                amountColor = if (summary.runningBalance < 0.0) dangerInk else normalInk,
                unitColor = normalInk
            )
        }
    }
}

@Composable
private fun HisabSheetRow(
    dateText: String,
    labelText: String,
    amountText: String,
    unitText: String,
    isBold: Boolean,
    labelColor: Color,
    amountColor: Color,
    unitColor: Color
) {
    val weight = if (isBold) FontWeight.Bold else FontWeight.Medium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.25f)
        )
        Text(
            text = labelText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            color = labelColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.40f)
        )
        Text(
            text = amountText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            color = amountColor,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(0.20f)
        )
        Text(
            text = unitText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            color = unitColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .weight(0.15f)
                .padding(start = 6.dp)
        )
    }
}

@Composable
fun LedgerEntryCard(entry: FundLedgerEntry) {
    val isMoneyIn = entry.moneyIn > 0
    val accent = if (isMoneyIn) VibrantCardAccents[2] else VibrantCardAccents[7]
    val isDark = LocalIsDarkEkotaTheme.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.surface else accent.softSurface.copy(alpha = 0.72f)
        ),
        border = BorderStroke(1.dp, accent.gradient.first().copy(alpha = 0.35f)),
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
                    .background(Brush.linearGradient(accent.gradient)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMoneyIn) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "তারিখ: ${entry.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                if (entry.moneyIn > 0) {
                    Text(
                        text = "+ ${BengaliUtils.formatTaka(entry.moneyIn)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ekotaSuccessTextColor(),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                if (entry.moneyOut > 0) {
                    Text(
                        text = "- ${BengaliUtils.formatTaka(entry.moneyOut)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ekotaDangerTextColor(),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = "ব্যালেন্স: ${BengaliUtils.formatTaka(entry.balance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun AddLedgerEntryDialog(
    onDismiss: () -> Unit,
    onSave: (date: String, desc: String, moneyIn: Double, moneyOut: Double) -> Unit
) {
    var date by remember { mutableStateOf(BengaliUtils.getCurrentDateStr()) }
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var isMoneyIn by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন হিসাব লেনদেন এন্ট্রি", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = isMoneyIn,
                        onClick = { isMoneyIn = true },
                        label = { Text("টাকা জমা (In)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = !isMoneyIn,
                        onClick = { isMoneyIn = false },
                        label = { Text("টাকা খরচ (Out)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("বিবরণ (যেমন: বিবিধ খরচ বা ব্যাংক উত্তোলন)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("টাকার পরিমাণ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
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
                    if (description.isNotBlank() && amount > 0) {
                        if (isMoneyIn) {
                            onSave(date, description, amount, 0.0)
                        } else {
                            onSave(date, description, 0.0, amount)
                        }
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
