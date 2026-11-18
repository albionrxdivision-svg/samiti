package com.example.ui.fines

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.FineRecord
import com.example.data.model.FineType
import com.example.data.model.Member
import com.example.ui.savings.TableCell
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import com.example.util.DailyAccountCalculator
import com.example.util.SavingsAndFineCalculator

@Composable
fun MemberFineInputRow(
    member: Member,
    amountText: String,
    existingFineOnDate: FineRecord?,
    memberDueTotal: Double,
    isSubmittedThisWeek: Boolean = false,
    cardAccentIndex: Int = 0,
    onAmountChange: (String) -> Unit,
    onSubmitFine: (() -> Unit)? = null,
    onMemberClick: (() -> Unit)? = null
) {
    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
    val hasValidAmount = parsedAmount > 0.0
    val isSubmitted = isSubmittedThisWeek || (existingFineOnDate != null && existingFineOnDate.status != "REJECTED")
    val isLocked = isSubmitted
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
            .then(
                if (onMemberClick != null) Modifier.clickable { onMemberClick() } else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("member_fine_row_${member.id}"),
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

        // Column 2: নাম (Member Name + inline fine status notes)
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

            if (existingFineOnDate != null) {
                val submitterName = existingFineOnDate.officerName.ifBlank { "অ্যাডমিন" }
                val paidLabel = if (existingFineOnDate.isPaid) "আদায় ✓" else "ধার্য (বাকি)"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.testTag("fine_submitted_by_${member.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonPin,
                        contentDescription = "এন্ট্রিকারী",
                        tint = if (isSubmitted) submittedBluePrimary else accent.accentInk,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "$paidLabel • $submitterName",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (existingFineOnDate.isPaid) submittedBluePrimary else ekotaDangerTextColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (memberDueTotal > 0.0) {
                Text(
                    text = "মোট বাকি: ${BengaliUtils.formatTaka(memberDueTotal)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ekotaDangerTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("member_fine_due_badge_${member.id}")
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
                    .testTag("locked_fine_badge_${member.id}")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .testTag("fine_amount_input_${member.id}"),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = amountText.ifBlank { "10" },
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
                        !hasValidAmount -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
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
                                    text = "10",
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
                            .testTag("fine_amount_input_${member.id}")
                    )
                }
            }
        }

        // Column 4: জমা (Per-Member Submit / Submitted Blue Status Button)
        if (onSubmitFine != null || isLocked) {
            Spacer(modifier = Modifier.width(6.dp))
            if (isLocked) {
                Button(
                    onClick = { onMemberClick?.invoke() },
                    enabled = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = submittedBluePillBg,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .width(86.dp)
                        .heightIn(min = 48.dp)
                        .testTag("submitted_fine_badge_${member.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "সাবমিট সম্পন্ন",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (existingFineOnDate?.isPaid == true) "আদায় ✓" else "জমা ✓",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                Button(
                    onClick = { onSubmitFine?.invoke() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreenPrimary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .width(86.dp)
                        .heightIn(min = 48.dp)
                        .testTag("submit_member_fine_${member.id}")
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
fun FineMatrixTable(
    members: List<Member>,
    sortedDates: List<String>,
    fines: List<FineRecord>,
    onCellClick: (Member, String) -> Unit,
    onMemberClick: (Member) -> Unit,
    onDateHeaderClick: ((String) -> Unit)? = null
) {
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()

    val columnTotals = remember(sortedDates, fines) {
        sortedDates.associateWith { date ->
            val normDate = DailyAccountCalculator.normalizeDateKey(date)
            fines.filter {
                DailyAccountCalculator.normalizeDateKey(it.fineDate) == normDate || it.fineDate == date
            }.sumOf { it.amount }
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
                // LEFT PINNED PANE (SL + Name — matches WeeklySavingsMatrixTable)
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

                // RIGHT SCROLLABLE PANE (Dates + Breakdown Totals)
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
                                        .width(100.dp)
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
                                TableCell(text = date, width = 100.dp, isHeader = true)
                            }
                        }
                        TableCell(text = "সঞ্চয় জরি.", width = 98.dp, isHeader = true)
                        TableCell(text = "ঋণ জরি.", width = 96.dp, isHeader = true)
                        TableCell(text = "মোট জরি.", width = 102.dp, isHeader = true)
                        TableCell(text = "বাকি জরি.", width = 102.dp, isHeader = true)
                    }

                    members.forEachIndexed { index, member ->
                        val isEven = index % 2 == 0
                        val memberFines = fines.filter {
                            it.memberId == member.id || (member.id.isBlank() && it.memberName == member.name) || it.memberName == member.name
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
                                val normDate = DailyAccountCalculator.normalizeDateKey(date)
                                val dateFines = memberFines.filter {
                                    DailyAccountCalculator.normalizeDateKey(it.fineDate) == normDate || it.fineDate == date
                                }
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
                                val isDarkTheme = LocalIsDarkEkotaTheme.current
                                val cellBg = when {
                                    !hasAny -> MaterialTheme.colorScheme.surfaceVariant
                                    allPaid && isDarkTheme -> Color(0xFF1E3A8A)
                                    allPaid -> Color(0xFFDBEAFE)
                                    else -> ekotaBadgeDangerBg()
                                }
                                val cellFg = when {
                                    !hasAny -> MaterialTheme.colorScheme.onSurfaceVariant
                                    allPaid && isDarkTheme -> Color(0xFFBFDBFE)
                                    allPaid -> Color(0xFF1E3A8A)
                                    else -> ekotaDangerTextColor()
                                }
                                val borderColor = when {
                                    !hasAny -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    allPaid -> Color(0xFF1E4FD6)
                                    else -> cellFg.copy(alpha = 0.5f)
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
                                        border = BorderStroke(0.75.dp, borderColor),
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
                            .height(52.dp)
                            .background(HeaderSlateNavy),
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
fun FineCellDetailDialog(
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
                text = "${member.name} (${BengaliUtils.formatTwoDigitSerial(member.memberSerial)}) - $date",
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
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
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
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("+ সঞ্চয় ৳১০", maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(
                        onClick = { onAddFine(FineType.LOAN, SavingsAndFineCalculator.DEFAULT_WEEKLY_FINE_AMOUNT) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
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
fun AddFineDialog(
    members: List<Member>,
    initialDate: String,
    initialFineType: FineType,
    onDismiss: () -> Unit,
    onSave: (Member, FineType, String, String, Double, Boolean) -> Unit
) {
    var selectedMember by remember { mutableStateOf(members.firstOrNull()) }
    var memberDropdownExpanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(initialFineType) }
    var missedDate by remember { mutableStateOf(initialDate.ifBlank { BengaliUtils.getCurrentDateStr() }) }
    var fineDate by remember { mutableStateOf(initialDate.ifBlank { BengaliUtils.getCurrentDateStr() }) }
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
                        value = selectedMember?.let { "${BengaliUtils.formatTwoDigitSerial(it.memberSerial)}. ${it.name}" } ?: "সদস্য নির্বাচন করুন",
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
                                text = { Text("${BengaliUtils.formatTwoDigitSerial(m.memberSerial)}. ${m.name}") },
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
                    label = { Text("জরিমানা ধার্যের তারিখ") },
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
