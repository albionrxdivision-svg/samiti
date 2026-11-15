package com.example.ui.savings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.ui.theme.*
import com.example.util.BengaliUtils

@Composable
fun MemberSavingsInputCard(
    member: Member,
    amountText: String,
    existingRecord: SavingsRecord?,
    missedDatesCount: Int = 0,
    isAdmin: Boolean = true,
    isSubmittedThisWeek: Boolean = false,
    isPendingSync: Boolean = false,
    onAmountChange: (String) -> Unit
) {
    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
    val isPaid = parsedAmount > 0.0
    val defaultAmt = member.weeklySavingsAmount.toInt().toString()
    val doubleAmt = (member.weeklySavingsAmount * 2).toInt().toString()
    val isLocked = isSubmittedThisWeek || (!isAdmin && existingRecord != null && existingRecord.status != "REJECTED")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("member_savings_card_${member.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPaid) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Serial Number Badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = BengaliUtils.toBengaliDigits(member.memberSerial),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Member Name & Area Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${member.area.ifBlank { "যাদবপুর" }} • নির্ধারিত: ${BengaliUtils.formatTaka(member.weeklySavingsAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Existing Saved Status Badge + Pending Offline Sync Badge
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (existingRecord != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (existingRecord.isPaid && existingRecord.amount > 0)
                                ekotaBadgeSuccessBg()
                            else
                                ekotaBadgeDangerBg()
                        ) {
                            Text(
                                text = if (existingRecord.isPaid && existingRecord.amount > 0)
                                    "সংরক্ষিত: ${BengaliUtils.formatTaka(existingRecord.amount)}"
                                else
                                    "বাকি (-)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                color = if (existingRecord.isPaid && existingRecord.amount > 0)
                                    ekotaSuccessTextColor()
                                else
                                    ekotaDangerTextColor(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (isPendingSync) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ekotaBadgeWarningBg(),
                            modifier = Modifier.testTag("pending_sync_badge_${member.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = "সিঙ্ক বাকি",
                                    tint = ekotaWarningTextColor(),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "সিঙ্ক বাকি",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = ekotaWarningTextColor()
                                )
                            }
                        }
                    }
                }
            }

            if (missedDatesCount > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ekotaBadgeDangerBg(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("missed_weeks_badge_${member.id}")
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
                            text = "পূর্বের বকেয়া: ${BengaliUtils.toBengaliDigits(missedDatesCount)} সপ্তাহ (পরের সপ্তাহে ৳১০ জরিমানা প্রযোজ্য)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ekotaDangerTextColor()
                        )
                    }
                }
            }

            // Amount Input Field (full width when editable; inline lock badge when already submitted this week)
            if (isLocked) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("সঞ্চয় (৳)") },
                        prefix = {
                            Text(
                                text = "৳ ",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("savings_amount_input_${member.id}"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSubmittedThisWeek) ekotaBadgeSuccessBg() else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.testTag("locked_savings_badge_${member.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSubmittedThisWeek) Icons.Default.CheckCircle else Icons.Default.Lock,
                                contentDescription = "এই সপ্তাহে জমা সম্পন্ন",
                                tint = if (isSubmittedThisWeek) ekotaSuccessTextColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSubmittedThisWeek) "এই সপ্তাহে জমা ✓" else "শুধু অ্যাডমিন",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isSubmittedThisWeek) ekotaSuccessTextColor() else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        onAmountChange(filtered)
                    },
                    label = { Text("সঞ্চয় পরিমাণ (৳)") },
                    prefix = {
                        Text(
                            text = "৳ ",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("savings_amount_input_${member.id}"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { onAmountChange(defaultAmt) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("preset_default_btn_${member.id}"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (amountText == defaultAmt) EmeraldGreenPrimary else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (amountText == defaultAmt) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = BengaliUtils.formatTaka(member.weeklySavingsAmount),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalButton(
                        onClick = { onAmountChange(doubleAmt) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("preset_double_btn_${member.id}"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (amountText == doubleAmt) NavyBlueSecondary else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (amountText == doubleAmt) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "২x ডাবল",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FilledTonalButton(
                        onClick = { onAmountChange("0") },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("preset_zero_btn_${member.id}"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (amountText == "0" || amountText.isBlank()) ekotaBadgeDangerBg() else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (amountText == "0" || amountText.isBlank()) ekotaDangerTextColor() else MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "বাকি (০৳)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (parsedAmount > member.weeklySavingsAmount && missedDatesCount > 0) {
                Text(
                    text = "ডাবল/অতিরিক্ত জমা পূর্বের বকেয়া সপ্তাহে স্বয়ংক্রিয়ভাবে কাউন্ট হবে",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun WeeklySavingsMatrixTable(
    members: List<Member>,
    sortedDates: List<String>,
    savingsMap: Map<String, SavingsRecord>,
    columnTotals: Map<String, Double>,
    grandTotal: Double,
    onCellClick: (Member, String) -> Unit,
    onMemberClick: (Member) -> Unit,
    onDateHeaderClick: ((String) -> Unit)? = null
) {
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScrollState)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // 1. LEFT PINNED PANE (Fixed width 192.dp: Never scrolls away!)
                Column(
                    modifier = Modifier
                        .width(192.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .background(NavyBlueSecondary),
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
                                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                                .clickable { onMemberClick(member) },
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
                            .height(52.dp)
                            .background(NavyBlueSecondary),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell(
                            text = "মোট টাকা =",
                            width = 192.dp,
                            isHeader = true,
                            textColor = GoldenAccent,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // 2. RIGHT HORIZONTALLY SCROLLABLE PANE (Dates & Totals)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier
                            .height(52.dp)
                            .background(NavyBlueSecondary),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        sortedDates.forEach { date ->
                            if (onDateHeaderClick != null) {
                                Row(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .fillMaxHeight()
                                        .clickable { onDateHeaderClick(date) }
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = date,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "তারিখ আপডেট",
                                        tint = GoldenAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            } else {
                                TableCell(text = date, width = 96.dp, isHeader = true)
                            }
                        }
                        TableCell(text = "মোট সঞ্চয়", width = 108.dp, isHeader = true)
                    }

                    members.forEachIndexed { index, member ->
                        val isEven = index % 2 == 0
                        val memberTotal = sortedDates.sumOf { date ->
                            val rec = savingsMap["${member.id}_$date"] ?: savingsMap["${member.name}_$date"]
                            if (rec != null && rec.isPaid && rec.status != "REJECTED") rec.amount else 0.0
                        }

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
                                val record = savingsMap["${member.id}_$date"] ?: savingsMap["${member.name}_$date"]
                                val amount = record?.amount ?: 0.0
                                val isPaid = record != null && record.isPaid && amount > 0.0
                                val displayText = if (isPaid) BengaliUtils.toBengaliDigits(amount.toInt()) else "-"
                                val textColor = if (isPaid) MaterialTheme.colorScheme.onPrimaryContainer else ekotaDangerTextColor()

                                Box(
                                    modifier = Modifier
                                        .width(96.dp)
                                        .fillMaxHeight()
                                        .clickable { onCellClick(member, date) }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isPaid) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            0.5.dp,
                                            if (isPaid) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = displayText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isPaid) FontWeight.Bold else FontWeight.SemiBold,
                                                color = textColor,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }

                            TableCell(
                                text = BengaliUtils.formatTaka(memberTotal),
                                width = 108.dp,
                                fontWeight = FontWeight.Bold,
                                textColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .height(52.dp)
                            .background(NavyBlueSecondary),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        sortedDates.forEach { date ->
                            val colTotal = columnTotals[date] ?: 0.0
                            TableCell(
                                text = BengaliUtils.toBengaliDigits(colTotal.toInt()),
                                width = 96.dp,
                                isHeader = true,
                                textColor = GoldenAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TableCell(
                            text = BengaliUtils.formatTaka(grandTotal),
                            width = 108.dp,
                            isHeader = true,
                            textColor = GoldenAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TableCell(
    text: String,
    width: Dp,
    isHeader: Boolean = false,
    fontWeight: FontWeight = FontWeight.Normal,
    textColor: Color? = null,
    textAlign: TextAlign = TextAlign.Center
) {
    val color = textColor ?: if (isHeader) Color.White else MaterialTheme.colorScheme.onSurface
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp),
        textAlign = textAlign,
        style = if (isHeader) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        fontWeight = if (isHeader) FontWeight.Bold else fontWeight,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun SavingsEntryDialog(
    member: Member,
    date: String,
    currentAmount: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf(currentAmount.toInt().toString()) }
    var isAbsent by remember { mutableStateOf(currentAmount <= 0.0) }

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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "সাপ্তাহিক নির্ধারিত সঞ্চয়: ${BengaliUtils.formatTaka(member.weeklySavingsAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "দ্রুত পরিমাণ নির্বাচন করুন:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = !isAbsent && amountText == "100",
                        onClick = {
                            isAbsent = false
                            amountText = "100"
                        },
                        label = { Text("১০০ ৳") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isAbsent && amountText == "200",
                        onClick = {
                            isAbsent = false
                            amountText = "200"
                        },
                        label = { Text("২০০ ৳") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isAbsent && amountText == "300",
                        onClick = {
                            isAbsent = false
                            amountText = "300"
                        },
                        label = { Text("৩০০ ৳") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        isAbsent = !isAbsent
                        amountText = if (isAbsent) "0" else member.weeklySavingsAmount.toInt().toString()
                    }
                ) {
                    Checkbox(
                        checked = isAbsent,
                        onCheckedChange = {
                            isAbsent = it
                            amountText = if (it) "0" else member.weeklySavingsAmount.toInt().toString()
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "অনুপস্থিত / বাকি (-) [০ টাকা]",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isAbsent) ekotaDangerTextColor() else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isAbsent) FontWeight.Bold else FontWeight.Normal
                    )
                }

                if (!isAbsent) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("অন্যান্য পরিমাণ (টাকা)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalAmount = if (isAbsent) 0.0 else (amountText.toDoubleOrNull() ?: 0.0)
                    onSave(finalAmount)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreenPrimary,
                    contentColor = Color.White
                )
            ) {
                Text("আপডেট সংরক্ষণ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun AddCollectionDateDialog(
    currentMonthHeading: String,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var newDate by remember { mutableStateOf(BengaliUtils.getCurrentDateStr()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন কালেকশন তারিখ যোগ করুন") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("চলতি সময়কাল: $currentMonthHeading", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = newDate,
                    onValueChange = { newDate = it },
                    label = { Text("তারিখ (যেমন: 12/11/26)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (newDate.isNotBlank()) onAdd(newDate.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreenPrimary,
                    contentColor = Color.White
                )
            ) {
                Text("যোগ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun EditCollectionDateDialog(
    oldDate: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var updatedDate by remember(oldDate) { mutableStateOf(oldDate) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("সঞ্চয় শিটের তারিখ আপডেট করুন", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "পূর্বের তারিখ: $oldDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = updatedDate,
                    onValueChange = {
                        updatedDate = it
                        errorMsg = null
                    },
                    label = { Text("নতুন তারিখ (dd/MM/yy যেমন: 14/11/26)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_collection_date_input")
                )
                errorMsg?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = ekotaDangerTextColor()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = updatedDate.trim()
                    if (clean.isEmpty() || com.example.util.SavingsAndFineCalculator.parseDateMillis(clean) == null) {
                        errorMsg = "সঠিক তারিখ দিন (যেমন: 14/11/26)"
                    } else {
                        onSave(clean)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreenPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_update_date_button")
            ) {
                Text("আপডেট করুন", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun EditHeadingDialog(
    currentHeading: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var heading by remember { mutableStateOf(currentHeading) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("সাপ্তাহিক সঞ্চয় শিরোনাম পরিবর্তন") },
        text = {
            OutlinedTextField(
                value = heading,
                onValueChange = { heading = it },
                label = { Text("মাস ও বছরের শিরোনাম") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (heading.isNotBlank()) onSave(heading.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreenPrimary,
                    contentColor = Color.White
                )
            ) {
                Text("আপডেট করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}
