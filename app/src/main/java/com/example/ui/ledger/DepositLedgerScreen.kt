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
import com.example.data.model.FundLedgerEntry
import com.example.data.model.UserRole
import com.example.ui.MainViewModel
import com.example.ui.components.EkotaCompactTopBar
import com.example.ui.theme.*
import com.example.util.BengaliUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositLedgerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val ledgerEntries by viewModel.fundLedger.collectAsState()
    val role by viewModel.userRole.collectAsState()

    var showAddEntryDialog by remember { mutableStateOf(false) }

    val latestBalance = remember(ledgerEntries) {
        ledgerEntries.lastOrNull()?.balance ?: 0.0
    }
    val totalIn = remember(ledgerEntries) { ledgerEntries.sumOf { it.moneyIn } }
    val totalOut = remember(ledgerEntries) { ledgerEntries.sumOf { it.moneyOut } }

    Scaffold(
        topBar = {
            EkotaCompactTopBar(
                title = "জমার হিসাব ও লেজার",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                }
            )
        },
        floatingActionButton = {
            if (role == UserRole.ADMIN) {
                FloatingActionButton(
                    onClick = { showAddEntryDialog = true },
                    containerColor = CardTealGradient.first(),
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_ledger_entry_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "নতুন লেজার এন্ট্রি")
                }
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
            // Balance Card with vibrant gradient
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(colors = CardTealGradient))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "সর্বশেষ মোট ব্যালেন্স (Current Balance)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.95f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = BengaliUtils.formatTaka(latestBalance),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.28f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "মোট জমা (In)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.94f),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = BengaliUtils.formatTaka(totalIn),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
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
                                    text = "মোট খরচ/বিতরণ (Out)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.95f),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = BengaliUtils.formatTaka(totalOut),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "সূত্র: পূর্বের ব্যালেন্স + জমা - খরচ = বর্তমান ব্যালেন্স",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "লেজার লেনদেনের তালিকা",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ekotaSectionTitleColor()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (ledgerEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "এখনো কোনো লেজার লেনদেন এন্ট্রি করা হয়নি",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 84.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(ledgerEntries) { entry ->
                        LedgerEntryCard(entry = entry)
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
        title = { Text("নতুন লেজার লেনদেন এন্ট্রি", fontWeight = FontWeight.Bold) },
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
