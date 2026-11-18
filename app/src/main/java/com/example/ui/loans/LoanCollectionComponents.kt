package com.example.ui.loans

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Loan
import com.example.data.model.LoanInstallment
import com.example.data.model.LoanStatus
import com.example.ui.savings.TableCell
import com.example.ui.theme.*
import com.example.util.BengaliUtils

@Composable
fun MemberLoanInputRow(
    loan: Loan,
    amountText: String,
    existingInstallment: LoanInstallment?,
    isAdmin: Boolean = true,
    isSubmittedThisWeek: Boolean = false,
    isPendingSync: Boolean = false,
    cardAccentIndex: Int = 0,
    onAmountChange: (String) -> Unit,
    onSubmitLoan: (() -> Unit)? = null,
    onMemberClick: (() -> Unit)? = null
) {
    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
    val isPaidAmount = parsedAmount > 0.0
    val isFullyPaid = loan.status == LoanStatus.PAID.name || loan.remainingLoan <= 0.0
    val isSubmitted = isSubmittedThisWeek ||
        (existingInstallment != null && !existingInstallment.isMissed && existingInstallment.status != "REJECTED") ||
        isFullyPaid
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
            .testTag("member_loan_row_${loan.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Column 1: SL (01, 02, ...)
        Text(
            text = BengaliUtils.formatTwoDigitSerial(loan.memberSerial),
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

        // Column 2: নাম (Borrower Name + subtle inline status notes)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = loan.memberName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isSubmitted) submittedBlueInk else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (existingInstallment != null && !existingInstallment.isMissed) {
                val submitterName = existingInstallment.officerName.ifBlank { "অ্যাডমিন" }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.testTag("loan_submitted_by_${loan.id}")
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
            } else if (existingInstallment != null && existingInstallment.isMissed) {
                Text(
                    text = "অনুপস্থিত (৳১০ জরিমানা)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ekotaDangerTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("loan_missed_date_badge_${loan.id}")
                )
            }

            if (isFullyPaid) {
                Text(
                    text = "পরিশোধ সম্পন্ন",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = submittedBluePrimary,
                    maxLines = 1,
                    softWrap = false
                )
            } else if (loan.missedInstallments > 0) {
                Text(
                    text = "বকেয়া: ${BengaliUtils.toBengaliDigits(loan.missedInstallments)} সপ্তাহ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ekotaDangerTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("loan_missed_weeks_badge_${loan.id}")
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
                    modifier = Modifier.testTag("loan_pending_sync_badge_${loan.id}")
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
                    .testTag("locked_loan_badge_${loan.id}")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .testTag("loan_amount_input_${loan.id}"),
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
                        !isPaidAmount -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
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
                            .testTag("loan_amount_input_${loan.id}")
                    )
                }
            }
        }

        // Column 4: জমা (Per-Loan Submit / Submitted Blue Status Button)
        if (onSubmitLoan != null || isLocked) {
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
                        .testTag("submitted_loan_badge_${loan.id}")
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
                    onClick = { onSubmitLoan?.invoke() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreenPrimary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier
                        .width(86.dp)
                        .heightIn(min = 48.dp)
                        .testTag("submit_loan_installment_${loan.id}")
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
fun WeeklyLoanMatrixTable(
    loans: List<Loan>,
    sortedDates: List<String>,
    installmentMap: Map<String, LoanInstallment>,
    columnTotals: Map<String, Double>,
    grandTotal: Double,
    onCellClick: (Loan, String) -> Unit,
    onLoanClick: (Loan) -> Unit,
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

                    loans.forEachIndexed { index, loan ->
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
                                .clickable { onLoanClick(loan) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableCell(
                                text = BengaliUtils.formatTwoDigitSerial(loan.memberSerial),
                                width = 64.dp,
                                fontWeight = FontWeight.Bold,
                                textColor = MaterialTheme.colorScheme.primary
                            )
                            TableCell(
                                text = loan.memberName,
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
                        TableCell(text = "মোট আদায়", width = 108.dp, isHeader = true)
                    }

                    loans.forEachIndexed { index, loan ->
                        val isEven = index % 2 == 0
                        val loanCollectedTotal = sortedDates.sumOf { date ->
                            val normDate = com.example.util.DailyAccountCalculator.normalizeDateKey(date)
                            val rec = installmentMap["${loan.id}_$date"]
                                ?: installmentMap["${loan.id}_$normDate"]
                            if (rec != null && !rec.isMissed && rec.status != "REJECTED") rec.amount else 0.0
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
                                val normDate = com.example.util.DailyAccountCalculator.normalizeDateKey(date)
                                val record = installmentMap["${loan.id}_$date"]
                                    ?: installmentMap["${loan.id}_$normDate"]
                                val amount = record?.amount ?: 0.0
                                val isPaid = record != null && !record.isMissed && record.status != "REJECTED" && amount > 0.0
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
                                        .clickable { onCellClick(loan, date) }
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
                                text = BengaliUtils.formatTaka(loanCollectedTotal),
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
fun LoanCard(
    loan: Loan,
    installmentsCount: Int,
    isAdmin: Boolean = false,
    isPendingSync: Boolean = false,
    isSubmittedThisWeek: Boolean = false,
    cardAccentIndex: Int = 0,
    latestCollectorName: String = "",
    onCardClick: () -> Unit,
    onCollectInstallment: () -> Unit,
    onMarkMissedInstallment: () -> Unit = {}
) {
    val isDark = LocalIsDarkEkotaTheme.current
    val accent = VibrantCardAccents[cardAccentIndex % VibrantCardAccents.size]
    val cardContainerColor = when {
        isDark -> MaterialTheme.colorScheme.surface
        isSubmittedThisWeek -> accent.softSurface
        else -> accent.softSurface.copy(alpha = 0.65f)
    }
    val cardBorderColor = accent.gradient.first().copy(alpha = if (isSubmittedThisWeek) 0.72f else 0.36f)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        border = BorderStroke(if (isSubmittedThisWeek) 1.4.dp else 1.dp, cardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("loan_card_${loan.memberSerial}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(colors = accent.gradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = BengaliUtils.toBengaliDigits(loan.memberSerial),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = loan.memberName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (!isDark) accent.accentInk else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "তারিখ: ${loan.loanDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (latestCollectorName.isNotBlank() || isSubmittedThisWeek) {
                            Text(
                                text = "জমাদানকারী: ${latestCollectorName.ifBlank { "অ্যাডমিন" }}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) accent.gradient.last() else accent.accentInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (loan.status == LoanStatus.PAID.name) ekotaBadgeSuccessBg() else ekotaBadgeWarningBg()
                    ) {
                        Text(
                            text = if (loan.status == LoanStatus.PAID.name) "পরিশোধিত" else "চলতি ঋণ",
                            color = if (loan.status == LoanStatus.PAID.name) ekotaSuccessTextColor() else ekotaWarningTextColor(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (isSubmittedThisWeek) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ekotaBadgeSuccessBg(),
                            modifier = Modifier.testTag("loan_week_submitted_${loan.id}")
                        ) {
                            Text(
                                text = "এই সপ্তাহে জমা ✓",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ekotaSuccessTextColor(),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isPendingSync) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ekotaBadgeWarningBg(),
                            modifier = Modifier.testTag("loan_pending_sync_${loan.id}")
                        ) {
                            Text(
                                text = "সিঙ্ক বাকি",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ekotaWarningTextColor(),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (loan.remainingLoan > 0 && !isSubmittedThisWeek) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCollectInstallment,
                        modifier = Modifier
                            .weight(1.35f)
                            .heightIn(min = 48.dp)
                            .testTag("collect_installment_btn_${loan.id}"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent.gradient.first(),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.AddCard, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "কিস্তি জমা (${BengaliUtils.formatTaka(loan.weeklyInstallment)})",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isAdmin) {
                        OutlinedButton(
                            onClick = onMarkMissedInstallment,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("missed_installment_btn_${loan.id}"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ekotaDangerTextColor()
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "দেয়নি (+৳১০)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else if (loan.remainingLoan > 0 && isSubmittedThisWeek) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ekotaBadgeSuccessBg(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loan_installment_submitted_pill_${loan.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ekotaSuccessTextColor(),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "এই সপ্তাহের কিস্তি জমা সম্পন্ন ✓",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = ekotaSuccessTextColor()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoanInstallmentDialog(
    loan: Loan,
    initialDate: String = BengaliUtils.getCurrentDateStr(),
    onDismiss: () -> Unit,
    onSave: (amount: Double, date: String) -> Unit
) {
    val singleAmt = loan.weeklyInstallment.toInt().coerceAtLeast(0).toString()
    val doubleAmt = (loan.weeklyInstallment * 2).coerceAtMost(loan.remainingLoan).toInt().coerceAtLeast(0).toString()
    var amountText by remember {
        mutableStateOf(if (loan.missedInstallments > 0) doubleAmt else singleAmt)
    }
    var dateText by remember(initialDate) { mutableStateOf(initialDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ঋণের কিস্তি জমা", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("সদস্য: ${loan.memberName} (${BengaliUtils.toBengaliDigits(loan.memberSerial)})", fontWeight = FontWeight.SemiBold)
                Text("অবশিষ্ট ঋণ: ${BengaliUtils.formatTaka(loan.remainingLoan)}", color = ekotaDangerTextColor())

                if (loan.missedInstallments > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ekotaBadgeWarningBg(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "পূর্বের বকেয়া: ${BengaliUtils.toBengaliDigits(loan.missedInstallments)}টি কিস্তি। ডাবল (${BengaliUtils.formatTaka(loan.weeklyInstallment * 2)}) দিলে বকেয়া কিস্তি সমন্বয় হবে (৳১০ জরিমানা আলাদা শিটে থাকবে)।",
                            style = MaterialTheme.typography.bodySmall,
                            color = ekotaWarningTextColor(),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = amountText == singleAmt,
                        onClick = { amountText = singleAmt },
                        label = { Text("নির্ধারিত (১x)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = amountText == doubleAmt,
                        onClick = { amountText = doubleAmt },
                        label = { Text("ডাবল (২x)", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input -> amountText = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("কিস্তির পরিমাণ (টাকা)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
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
                    if (amount > 0) {
                        onSave(amount, dateText)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreenPrimary,
                    contentColor = Color.White
                )
            ) {
                Text("জমা দিন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun LoanHistoryDialog(
    loan: Loan,
    installmentHistory: List<LoanInstallment>,
    isAdmin: Boolean = false,
    onMarkMissed: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "${loan.memberName} - কিস্তি পরিশোধ বিবরণী",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "মোট পরিশোধযোগ্য: ${BengaliUtils.formatTaka(loan.totalPayable)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "অবশিষ্ট: ${BengaliUtils.formatTaka(loan.remainingLoan)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = ekotaDangerTextColor(),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Text(
                    text = "মূল ঋণ: ${BengaliUtils.formatTaka(loan.principal)} | আদায়: ${BengaliUtils.formatTaka(loan.totalPaid)} | সাপ্তাহিক কিস্তি: ${BengaliUtils.formatTaka(loan.weeklyInstallment)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                Text("পরিশোধিত কিস্তিসমূহ:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

                if (installmentHistory.isEmpty()) {
                    Text("এখনও কোনো কিস্তি জমা হয়নি", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        items(installmentHistory) { inst ->
                            val isMissedEntry = inst.isMissed || inst.amount <= 0.0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "কিস্তি ${BengaliUtils.toBengaliDigits(inst.serialNumber)} (${inst.installmentDate})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isMissedEntry) ekotaDangerTextColor() else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (inst.officerName.isNotBlank()) {
                                        Text(
                                            text = "জমাদানকারী: ${inst.officerName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = if (isMissedEntry) "অনুপস্থিত (৳১০ জরিমানা)" else BengaliUtils.formatTaka(inst.amount),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMissedEntry) ekotaDangerTextColor() else MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isAdmin && loan.remainingLoan > 0.0 && onMarkMissed != null) {
                    OutlinedButton(
                        onClick = onMarkMissed,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ekotaDangerTextColor()),
                        modifier = Modifier.testTag("history_mark_missed_loan_btn")
                    ) {
                        Text("অনুপস্থিত (+৳১০)")
                    }
                }
                TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
            }
        }
    )
}
