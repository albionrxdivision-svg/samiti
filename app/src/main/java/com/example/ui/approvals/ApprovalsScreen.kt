package com.example.ui.approvals

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import com.example.ui.MainViewModel
import com.example.ui.theme.*
import com.example.util.BengaliUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val savingsRecords by viewModel.savingsRecords.collectAsState()
    val loanInstallments by viewModel.loanInstallments.collectAsState()

    val pendingSavings = remember(savingsRecords) {
        savingsRecords.filter { it.status == TransactionStatus.PENDING.name }
    }
    val pendingInstallments = remember(loanInstallments) {
        loanInstallments.filter { it.status == TransactionStatus.PENDING.name }
    }

    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "মাঠ কর্মকর্তা লেনদেন অনুমোদন",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HeaderSlateNavy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = HeaderSlateNavy
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        selectedContentColor = HeaderSlateNavy,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                text = "সঞ্চয় (${BengaliUtils.toBengaliDigits(pendingSavings.size)})",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        selectedContentColor = HeaderSlateNavy,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Text(
                            text = "কিস্তি (${BengaliUtils.toBengaliDigits(pendingInstallments.size)})",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                if (pendingSavings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("কোনো সঞ্চয় লেনদেন অপেক্ষামান নেই", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(pendingSavings) { index, rec ->
                            val accent = VibrantCardAccents[index % VibrantCardAccents.size]
                            val isDark = LocalIsDarkEkotaTheme.current
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) MaterialTheme.colorScheme.surface else accent.softSurface.copy(alpha = 0.82f)
                                ),
                                border = BorderStroke(1.dp, accent.gradient.first().copy(alpha = 0.42f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(Brush.linearGradient(accent.gradient)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = BengaliUtils.toBengaliDigits(rec.memberSerial),
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${rec.memberName} (${BengaliUtils.toBengaliDigits(rec.memberSerial)})",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "তারিখ: ${rec.collectionDate} | কর্মকর্তা: ${rec.officerName}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = BengaliUtils.formatTaka(rec.amount),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.rejectSavings(rec, {}, {})
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ekotaDangerTextColor()),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("প্রত্যাখ্যান", maxLines = 1, softWrap = false)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                viewModel.approveSavings(rec, {}, {})
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SuccessGreen,
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("অনুমোদন করুন", maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (pendingInstallments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("কোনো ঋণ কিস্তি অপেক্ষামান নেই", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(pendingInstallments) { index, inst ->
                            val accent = VibrantCardAccents[(index + 1) % VibrantCardAccents.size]
                            val isDark = LocalIsDarkEkotaTheme.current
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDark) MaterialTheme.colorScheme.surface else accent.softSurface.copy(alpha = 0.82f)
                                ),
                                border = BorderStroke(1.dp, accent.gradient.first().copy(alpha = 0.42f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(Brush.linearGradient(accent.gradient)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CreditScore,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = inst.memberName,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "কিস্তি: ${BengaliUtils.toBengaliDigits(inst.serialNumber)} | তারিখ: ${inst.installmentDate}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = BengaliUtils.formatTaka(inst.amount),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.approveLoanInstallment(inst, {}, {})
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SuccessGreen,
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text("অনুমোদন করুন", maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
