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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ekotaSilverBackgroundBrush())
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Overall Summary Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hisab_summary_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(colors = CardTealGradient))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "সর্বমোট জমা (বর্তমান ব্যালেন্স)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.95f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = BengaliUtils.formatTaka(hisabOverview.currentNetBalance),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.28f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HisabSummaryStatColumn(
                                label = "মোট সঞ্চয়",
                                amount = hisabOverview.totalSavings,
                                modifier = Modifier.weight(1f)
                            )
                            HisabSummaryStatColumn(
                                label = "মোট ঋণ আদায়",
                                amount = hisabOverview.totalLoanCollected,
                                modifier = Modifier.weight(1f)
                            )
                            HisabSummaryStatColumn(
                                label = "বাইরে গেছে",
                                amount = hisabOverview.totalWentOut,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mode switcher chips: Date-wise Summary List vs Detailed Ledger
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            imageVector = Icons.Default.ReceiptLong,
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

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedTab == 0) {
                if (hisabOverview.summariesNewestFirst.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.EventNote,
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
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 18.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("daily_hisab_list")
                    ) {
                        items(
                            items = hisabOverview.summariesNewestFirst,
                            key = { it.dateKey }
                        ) { summary ->
                            DailyHisabDateCard(
                                summary = summary,
                                showRunningBalance = hisabOverview.summariesNewestFirst.size > 1
                            )
                        }
                    }
                }
            } else {
                if (ledgerEntries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "এখনো কোনো লেনদেন এন্ট্রি করা হয়নি",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 18.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("detailed_ledger_list")
                    ) {
                        items(ledgerEntries.reversed()) { entry ->
                            LedgerEntryCard(entry = entry)
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

@Composable
private fun HisabSummaryStatColumn(
    label: String,
    amount: Double,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.92f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = BengaliUtils.formatTaka(amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DailyHisabDateCard(
    summary: DailyAccountSummary,
    showRunningBalance: Boolean
) {
    val isDark = LocalIsDarkEkotaTheme.current
    val hasOutflow = summary.wentOutTotal > 0.0
    val accent = if (hasOutflow) VibrantCardAccents[7] else VibrantCardAccents[2]

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
        ),
        border = BorderStroke(1.dp, accent.gradient.first().copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_hisab_card_${summary.dateKey}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Date Header Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                HeaderSlateNavy.copy(alpha = 0.94f),
                                EmeraldGreenPrimary.copy(alpha = 0.90f)
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "তারিখ: ${summary.displayDate}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.22f)
                ) {
                    Text(
                        text = "মোট জমা ${BengaliUtils.formatTaka(summary.netDeposit)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HisabLineRow(
                    label = "মোট সঞ্চয়",
                    valueText = "= ${BengaliUtils.formatTaka(summary.savingsTotal)}",
                    valueColor = ekotaSuccessTextColor()
                )
                HisabLineRow(
                    label = "মোট ঋণ",
                    valueText = "= ${BengaliUtils.formatTaka(summary.loanCollectedTotal)}",
                    valueColor = MaterialTheme.colorScheme.primary
                )

                if (summary.otherInflowTotal > 0.0) {
                    HisabLineRow(
                        label = "অন্যান্য জমা",
                        valueText = "= ${BengaliUtils.formatTaka(summary.otherInflowTotal)}",
                        valueColor = ekotaSuccessTextColor()
                    )
                }

                if (hasOutflow) {
                    HisabLineRow(
                        label = "সঞ্চয় ও ঋণ যোগ",
                        valueText = "= ${BengaliUtils.formatTaka(summary.grossCollection)}",
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ekotaBadgeDangerBg(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "বাইরে গেছে (ঋণ/খরচ)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = ekotaDangerTextColor()
                            )
                            Text(
                                text = "- ${BengaliUtils.formatTaka(summary.wentOutTotal)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = ekotaDangerTextColor(),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    HisabLineRow(
                        label = "বাইরে গেছে",
                        valueText = "${BengaliUtils.formatTaka(0.0)} (বাইরে যায়নি)",
                        valueColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ekotaBadgeSuccessBg(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "মোট জমা",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ekotaSuccessTextColor()
                        )
                        Text(
                            text = "= ${BengaliUtils.formatTaka(summary.netDeposit)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = ekotaSuccessTextColor(),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (showRunningBalance) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "এই তারিখ পর্যন্ত সর্বমোট জমা",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = BengaliUtils.formatTaka(summary.runningBalance),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HisabLineRow(
    label: String,
    valueText: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            softWrap = false
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
