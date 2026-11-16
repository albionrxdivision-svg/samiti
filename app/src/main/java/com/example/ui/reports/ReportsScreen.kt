package com.example.ui.reports

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FineType
import com.example.data.model.LoanStatus
import com.example.data.model.TransactionStatus
import com.example.ui.MainViewModel
import com.example.ui.components.EkotaCompactTopBar
import com.example.ui.theme.*
import com.example.util.BengaliUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val members by viewModel.members.collectAsState()
    val savingsRecords by viewModel.savingsRecords.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val installments by viewModel.loanInstallments.collectAsState()
    val fundLedger by viewModel.fundLedger.collectAsState()
    val fines by viewModel.fines.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsState()

    var selectedReportIndex by remember { mutableStateOf(0) }
    val reportTitles = listOf(
        "১. সদস্যভিত্তিক সঞ্চয় রিপোর্ট",
        "২. তারিখভিত্তিক সঞ্চয় রিপোর্ট",
        "৩. কর্মকর্তাভিত্তিক আদায় রিপোর্ট",
        "৪. ঋণ বিতরণ রিপোর্ট",
        "৫. ঋণ কিস্তি আদায় রিপোর্ট",
        "৬. বকেয়া ঋণ রিপোর্ট (Due List)",
        "৭. মোট জমা ও খরচ লেজার",
        "৮. দৈনিক লেনদেন রিপোর্ট",
        "৯. মাসিক আর্থিক সারসংক্ষেপ",
        "১০. সঞ্চয় ও ঋণ জরিমানা রিপোর্ট (৳১০)"
    )

    Scaffold(
        topBar = {
            EkotaCompactTopBar(
                title = "প্রতিবেদন ও রিপোর্ট",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        shareReportSummary(context, settings.orgName, reportTitles[selectedReportIndex])
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "রিপোর্ট শেয়ার করুন")
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
                .padding(16.dp)
        ) {
            // Report Selector
            var dropdownExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = reportTitles[selectedReportIndex],
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("রিপোর্ট নির্বাচন করুন") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = HeaderSlateNavy,
                        focusedLabelColor = HeaderSlateNavy
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    reportTitles.forEachIndexed { index, title ->
                        DropdownMenuItem(
                            text = { Text(title) },
                            onClick = {
                                selectedReportIndex = index
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Report Header Card (Vibrant Blue Gradient matching dashboard)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(CardBlueGradient))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "সংগঠন: ${settings.orgName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "প্রতিবেদন: ${reportTitles[selectedReportIndex]}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.95f)
                    )
                    Text(
                        text = "তারিখ: ${BengaliUtils.getCurrentDateStr()} | সময়কাল: ${settings.currentMonthHeading}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.88f)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                shareReportSummary(context, settings.orgName, "${reportTitles[selectedReportIndex]} (PDF)")
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = {
                                shareReportSummary(context, settings.orgName, "${reportTitles[selectedReportIndex]} (Excel)")
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel", maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = {
                                shareReportSummary(context, settings.orgName, "${reportTitles[selectedReportIndex]} (Image)")
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PNG", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Report Content based on selection
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxSize()
            ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (selectedReportIndex) {
                    0 -> { // Member-wise savings
                        item {
                            Text("সদস্যভিত্তিক মোট সঞ্চয় বিবরণী", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                        items(members.size) { i ->
                            val m = members[i]
                            val totalSav = savingsRecords.filter { it.memberId == m.id && it.status == "APPROVED" }.sumOf { it.amount }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${BengaliUtils.toBengaliDigits(m.memberSerial)}. ${m.name} (${m.area})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    BengaliUtils.formatTaka(totalSav),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        item {
                            val grandSav = savingsRecords.filter { it.status == "APPROVED" }.sumOf { it.amount }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("সর্বমোট সঞ্চয়:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(BengaliUtils.formatTaka(grandSav), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
                            }
                        }
                    }
                    1 -> { // Date-wise savings
                        item {
                            Text("তারিখভিত্তিক সঞ্চয় বিবরণী", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                        val dateGroups = savingsRecords.filter { it.status == "APPROVED" }.groupBy { it.collectionDate }
                        dateGroups.forEach { (date, recs) ->
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        "তারিখ: $date (${BengaliUtils.toBengaliDigits(recs.size)} টি আদায়)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        BengaliUtils.formatTaka(recs.sumOf { it.amount }),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    3 -> { // Loan distribution
                        item {
                            Text("ঋণ বিতরণ তালিকা", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                        items(loans.size) { i ->
                            val loan = loans[i]
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "${loan.memberName} (${loan.loanDate})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "মূল: ${BengaliUtils.formatTaka(loan.principal)} (মোট: ${BengaliUtils.formatTaka(loan.totalPayable)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    5 -> { // Outstanding loan report (Due List)
                        item {
                            Text("বকেয়া ঋণের তালিকা (Due List)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ekotaDangerTextColor())
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                        val activeLoans = loans.filter { it.status == LoanStatus.ACTIVE.name && it.remainingLoan > 0 }
                        items(activeLoans.size) { i ->
                            val loan = activeLoans[i]
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "${loan.memberName} (${loan.loanDate})",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(BengaliUtils.formatTaka(loan.remainingLoan), fontWeight = FontWeight.Bold, color = ekotaDangerTextColor())
                            }
                        }
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("মোট বকেয়া ঋণ:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(BengaliUtils.formatTaka(activeLoans.sumOf { it.remainingLoan }), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ekotaDangerTextColor())
                            }
                        }
                    }
                    6 -> { // Deposit and withdrawal ledger
                        item {
                            Text("জমা ও খরচের সার্বিক হিসাব", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                        items(fundLedger.size) { i ->
                            val entry = fundLedger[i]
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "${entry.date} - ${entry.description}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (entry.moneyIn > 0) "+${BengaliUtils.formatTaka(entry.moneyIn)}" else "-${BengaliUtils.formatTaka(entry.moneyOut)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (entry.moneyIn > 0) ekotaSuccessTextColor() else ekotaDangerTextColor()
                                )
                            }
                        }
                    }
                    9 -> { // Separate Fine Report
                        val activeFines = fines.filter { it.status != TransactionStatus.REJECTED.name }
                        item {
                            Text(
                                "সঞ্চয় ও ঋণ জরিমানা বিবরণী (মূল টাকা থেকে আলাদা)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ekotaDangerTextColor()
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        }
                        if (activeFines.isEmpty()) {
                            item {
                                Text(
                                    "কোনো জরিমানা রেকর্ড পাওয়া যায়নি",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(members.size) { i ->
                                val m = members[i]
                                val memberFines = activeFines.filter { it.memberId == m.id || it.memberName == m.name }
                                if (memberFines.isNotEmpty()) {
                                    val savFine = memberFines.filter { it.fineType == FineType.SAVINGS.name }.sumOf { it.amount }
                                    val loanFine = memberFines.filter { it.fineType == FineType.LOAN.name }.sumOf { it.amount }
                                    val paidFine = memberFines.filter { it.isPaid }.sumOf { it.amount }
                                    val dueFine = (savFine + loanFine - paidFine).coerceAtLeast(0.0)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${BengaliUtils.toBengaliDigits(m.memberSerial)}. ${m.name}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                "সঞ্চয়: ${BengaliUtils.formatTaka(savFine)} | ঋণ: ${BengaliUtils.formatTaka(loanFine)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "বাকি: ${BengaliUtils.formatTaka(dueFine)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (dueFine > 0.0) ekotaDangerTextColor() else ekotaSuccessTextColor()
                                        )
                                    }
                                }
                            }
                            item {
                                val totalFines = activeFines.sumOf { it.amount }
                                val paidFines = activeFines.filter { it.isPaid }.sumOf { it.amount }
                                val dueFines = (totalFines - paidFines).coerceAtLeast(0.0)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                SummaryRow("মোট ধার্যকৃত জরিমানা:", BengaliUtils.formatTaka(totalFines))
                                SummaryRow("জরিমানা আদায়:", BengaliUtils.formatTaka(paidFines))
                                SummaryRow("বাকি জরিমানা:", BengaliUtils.formatTaka(dueFines))
                            }
                        }
                    }
                    else -> { // Default monthly financial summary
                        item {
                            Text("মাসিক আর্থিক সামগ্রিক সারসংক্ষেপ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            SummaryRow("মোট সদস্য সংখ্যা:", "${BengaliUtils.toBengaliDigits(members.size)} জন")
                            SummaryRow("মোট সঞ্চয় তহবিল:", BengaliUtils.formatTaka(savingsRecords.filter { it.status == "APPROVED" }.sumOf { it.amount }))
                            SummaryRow("মোট ঋণ বিতরণ:", BengaliUtils.formatTaka(loans.sumOf { it.principal }))
                            SummaryRow("মোট ঋণ আদায়:", BengaliUtils.formatTaka(installments.filter { it.status == "APPROVED" && !it.isMissed }.sumOf { it.amount }))
                            SummaryRow("মোট বকেয়া ঋণ:", BengaliUtils.formatTaka(loans.filter { it.status == LoanStatus.ACTIVE.name }.sumOf { it.remainingLoan }))
                            SummaryRow("সমিতির বর্তমান ফান্ড ব্যালেন্স:", BengaliUtils.formatTaka(fundLedger.lastOrNull()?.balance ?: 0.0))
                            val activeFines = fines.filter { it.status != TransactionStatus.REJECTED.name }
                            SummaryRow("আলাদা জরিমানা বাকি (মূল তহবিল বহির্ভূত):", BengaliUtils.formatTaka(activeFines.filter { !it.isPaid }.sumOf { it.amount }))
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun SummaryRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false
        )
    }
}

fun shareReportSummary(context: Context, orgName: String, reportTitle: String) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(
            Intent.EXTRA_TEXT,
            """
            $orgName
            প্রতিবেদন: $reportTitle
            তারিখ: ${BengaliUtils.getCurrentDateStr()}
            মুদ্রা: BDT (৳)
            
            বিস্তারিত তথ্যের জন্য একতা সমিতি অ্যাপ দেখুন।
            """.trimIndent()
        )
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "রিপোর্ট শেয়ার করুন")
    context.startActivity(shareIntent)
}
