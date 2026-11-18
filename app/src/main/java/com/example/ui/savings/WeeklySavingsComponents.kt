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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
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

import androidx.compose.ui.graphics.Brush

@Composable
fun MemberSavingsInputCard(
    member: Member,
    amountText: String,
    existingRecord: SavingsRecord?,
    missedDatesCount: Int = 0,
    isAdmin: Boolean = true,
    isSubmittedThisWeek: Boolean = false,
    isPendingSync: Boolean = false,
    cardAccentIndex: Int = 0,
    onAmountChange: (String) -> Unit,
    onSubmitMember: (() -> Unit)? = null
) {
    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
    val isPaid = parsedAmount > 0.0
    val isSubmitted = isSubmittedThisWeek || (existingRecord != null && existingRecord.status != "REJECTED")
    val isLocked = isSubmittedThisWeek || (!isAdmin && existingRecord != null && existingRecord.status != "REJECTED")
    val isDark = LocalIsDarkEkotaTheme.current
    val accent = VibrantCardAccents[cardAccentIndex % VibrantCardAccents.size]

    val submittedBlueBg = if (isDark) Color(0xFF172554).copy(alpha = 0.78f) else Color(0xFFDBEAFE)
    val submittedBluePrimary = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    val submittedBlueInk = if (isDark) Color(0xFFBFDBFE) else Color(0xFF1E3A8A)
    val submittedBluePillBg = CardBlueGradient.first()

    val rowBgColor = when {
        isSubmitted -> submittedBlueBg
        isDark && cardAccentIndex % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        !isDark && cardAccentIndex % 2 == 1 -> accent.softSurface.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(rowBgColor)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("member_savings_card_${member.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Column 1: SL (01, 02, ...)
        Text(
            text = BengaliUtils.formatTwoDigitSerial(member.memberSerial),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = when {
                isSubmitted -> submittedBlueInk
                isDark -> accent.gradient.last()
                else -> HeaderSlateNavy
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(40.dp)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Column 2: নাম (Member Name + subtle inline status notes if needed)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = member.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isSubmitted) submittedBlueInk else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (existingRecord != null) {
                val submitterName = existingRecord.officerName.ifBlank { "অ্যাডমিন" }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.testTag("savings_submitted_by_${member.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonPin,
                        contentDescription = "জমাদানকারী",
                        tint = if (isSubmitted) submittedBluePrimary else accent.accentInk,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "জমা: $submitterName",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSubmitted) submittedBluePrimary else accent.accentInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (missedDatesCount > 0) {
                Text(
                    text = "বকেয়া: ${BengaliUtils.toBengaliDigits(missedDatesCount)} সপ্তাহ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ekotaDangerTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("missed_weeks_badge_${member.id}")
                )
            }

            if (isPendingSync) {
                Text(
                    text = "সিঙ্ক বাকি",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ekotaWarningTextColor(),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.testTag("pending_sync_badge_${member.id}")
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Column 3: টাকা (Inline entry cell directly under টাকা header)
        if (isLocked) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isDark) Color(0xFF1E3A8A) else Color(0xFFEFF6FF),
                border = BorderStroke(1.25.dp, submittedBluePrimary),
                modifier = Modifier
                    .width(72.dp)
                    .heightIn(min = 44.dp)
                    .testTag("locked_savings_badge_${member.id}")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .testTag("savings_amount_input_${member.id}"),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = amountText.ifBlank { "0" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = submittedBlueInk,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.White,
                border = BorderStroke(
                    width = 1.25.dp,
                    color = when {
                        !isPaid -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                        isDark -> accent.gradient.last()
                        else -> EmeraldGreenPrimary
                    }
                ),
                modifier = Modifier
                    .width(72.dp)
                    .heightIn(min = 44.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    BasicTextField(
                        value = amountText,
                        onValueChange = { input ->
                            val normalized = buildString {
                                for (ch in input) {
                                    val ascii = when (ch) {
                                        '০' -> '0'; '১' -> '1'; '২' -> '2'; '৩' -> '3'; '৪' -> '4'
                                        '৫' -> '5'; '৬' -> '6'; '৭' -> '7'; '৮' -> '8'; '৯' -> '9'
                                        else -> ch
                                    }
                                    if (ascii.isDigit() || ascii == '.') append(ascii)
                                }
                            }
                            onAmountChange(normalized)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End
                        ),
                        decorationBox = { innerTextField ->
                            if (amountText.isEmpty()) {
                                Text(
                                    text = "0",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("savings_amount_input_${member.id}")
                    )
                }
            }
        }

        // Column 4: Per-Member Submit / Submitted Blue Status Button
        if (onSubmitMember != null || isLocked) {
            Spacer(modifier = Modifier.width(6.dp))
            if (isLocked) {
                Button(
                    onClick = {},
                    enabled = false,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = submittedBluePillBg,
                        disabledContentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .width(86.dp)
                        .heightIn(min = 48.dp)
                        .testTag("submitted_member_badge_${member.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "সাবমিট সম্পন্ন",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "জমা ✓",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                Button(
                    onClick = { onSubmitMember?.invoke() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreenPrimary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .width(86.dp)
                        .heightIn(min = 48.dp)
                        .testTag("submit_member_savings_${member.id}")
                ) {
                    Text(
                        text = "সাবমিট",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
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
                            .background(HeaderSlateNavy),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell(text = "SL", width = 64.dp, isHeader = true)
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
                                text = BengaliUtils.formatTwoDigitSerial(member.memberSerial),
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
                            .background(HeaderSlateNavy),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TableCell(
                            text = "মোট টাকা =",
                            width = 192.dp,
                            isHeader = true,
                            textColor = Color.White,
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
                            .background(HeaderSlateNavy),
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
                                        tint = Color.White.copy(alpha = 0.9f),
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
                                val isDarkTheme = LocalIsDarkEkotaTheme.current
                                val textColor = when {
                                    isPaid && isDarkTheme -> Color(0xFFBFDBFE)
                                    isPaid -> Color(0xFF1E3A8A)
                                    else -> ekotaDangerTextColor()
                                }

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
                                        color = when {
                                            isPaid && isDarkTheme -> Color(0xFF1E3A8A)
                                            isPaid -> Color(0xFFDBEAFE)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        border = BorderStroke(
                                            0.75.dp,
                                            if (isPaid) Color(0xFF1E4FD6) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
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
                            .background(HeaderSlateNavy),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        sortedDates.forEach { date ->
                            val colTotal = columnTotals[date] ?: 0.0
                            TableCell(
                                text = BengaliUtils.toBengaliDigits(colTotal.toInt()),
                                width = 96.dp,
                                isHeader = true,
                                textColor = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TableCell(
                            text = BengaliUtils.formatTaka(grandTotal),
                            width = 108.dp,
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
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("নতুন কালেকশন তারিখ যোগ করুন") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("চলতি সময়কাল: $currentMonthHeading", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = newDate,
                    onValueChange = {
                        newDate = it
                        errorMsg = null
                    },
                    label = { Text("তারিখ (যেমন: 12/11/26)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_collection_date_input")
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
                    val clean = newDate.trim()
                    if (clean.isEmpty() || com.example.util.SavingsAndFineCalculator.parseDateMillis(clean) == null) {
                        errorMsg = "সঠিক তারিখ দিন (যেমন: 12/11/26)"
                    } else {
                        onAdd(com.example.util.SavingsAndFineCalculator.normalizeDateStr(clean))
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreenPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_add_date_button")
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
                        onSave(com.example.util.SavingsAndFineCalculator.normalizeDateStr(clean))
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
