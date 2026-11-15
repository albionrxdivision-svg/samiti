package com.example.ui.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Loan
import com.example.data.model.Member
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import com.example.util.LoanCalculator

private enum class InterestInputMode {
    PERCENT,
    FIXED_AMOUNT
}

private enum class BorrowerSourceMode {
    EXISTING_MEMBER,
    NEW_LOAN_MEMBER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewLoanDistributionForm(
    members: List<Member>,
    defaultInterestRatePercent: Double,
    isSaving: Boolean,
    onDistribute: (Loan, Member?) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val activeMembers = remember(members) {
        members.filter { it.isActive }.sortedBy { it.memberSerial }
    }

    var borrowerMode by remember(activeMembers.isEmpty()) {
        mutableStateOf(
            if (activeMembers.isEmpty()) BorrowerSourceMode.NEW_LOAN_MEMBER
            else BorrowerSourceMode.EXISTING_MEMBER
        )
    }

    var selectedMember by remember(activeMembers) {
        mutableStateOf(activeMembers.firstOrNull())
    }
    LaunchedEffect(activeMembers) {
        if (selectedMember == null || activeMembers.none { it.id == selectedMember?.id }) {
            selectedMember = activeMembers.firstOrNull()
        }
    }

    var memberDropdownExpanded by remember { mutableStateOf(false) }

    // Direct New Loan Member inputs (works even with zero savings members)
    var newBorrowerName by remember { mutableStateOf("") }
    var newBorrowerSerial by remember { mutableStateOf("") }
    var newBorrowerMobile by remember { mutableStateOf("") }
    var newBorrowerArea by remember { mutableStateOf("যাদবপুর") }
    var alsoAddToMembersList by remember { mutableStateOf(false) }

    val initialRate = if (defaultInterestRatePercent > 0.0) defaultInterestRatePercent else 20.0
    var principalText by remember { mutableStateOf("3000") }
    var interestMode by remember { mutableStateOf(InterestInputMode.PERCENT) }
    var interestRateText by remember {
        mutableStateOf(
            if (initialRate % 1.0 == 0.0) initialRate.toInt().toString() else initialRate.toString()
        )
    }
    var fixedInterestText by remember {
        mutableStateOf(
            LoanCalculator.calculateInterestFromRate(3000.0, initialRate).let {
                if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
            }
        )
    }
    var weeksText by remember { mutableStateOf("12") }
    var isCustomInstallment by remember { mutableStateOf(false) }
    var weeklyInstallmentText by remember {
        val calc = LoanCalculator.calculateFromRate(3000.0, initialRate, 12)
        mutableStateOf(
            if (calc.weeklyInstallment % 1.0 == 0.0) calc.weeklyInstallment.toInt().toString() else calc.weeklyInstallment.toString()
        )
    }

    var loanDate by remember { mutableStateOf(BengaliUtils.getCurrentDateStr()) }
    var isNextDateManuallyEdited by remember { mutableStateOf(false) }
    var nextInstallmentDate by remember { mutableStateOf(BengaliUtils.addDaysToDateStr(loanDate, 7)) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val principal = principalText.toDoubleOrNull() ?: 0.0
    val weeks = weeksText.toIntOrNull() ?: 0

    val calculation = remember(principal, interestMode, interestRateText, fixedInterestText, weeks) {
        when (interestMode) {
            InterestInputMode.PERCENT -> {
                val rate = interestRateText.toDoubleOrNull() ?: 0.0
                LoanCalculator.calculateFromRate(principal, rate, weeks)
            }
            InterestInputMode.FIXED_AMOUNT -> {
                val fixed = fixedInterestText.toDoubleOrNull() ?: 0.0
                LoanCalculator.calculateFromFixedInterest(principal, fixed, weeks)
            }
        }
    }

    fun formatInputNumber(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }

    // Keep derived interest & auto-calculated installment in sync unless overridden
    LaunchedEffect(calculation, isCustomInstallment) {
        if (interestMode == InterestInputMode.PERCENT) {
            fixedInterestText = formatInputNumber(calculation.interest)
        } else if (principal > 0.0) {
            interestRateText = formatInputNumber(calculation.effectiveRatePercent)
        }
        if (!isCustomInstallment) {
            weeklyInstallmentText = if (calculation.weeklyInstallment > 0.0) {
                formatInputNumber(calculation.weeklyInstallment)
            } else ""
        }
    }

    val finalWeeklyInstallment = weeklyInstallmentText.toDoubleOrNull() ?: 0.0
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Form Banner Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "নতুন ঋণ বিতরণ ফর্ম (অ্যাডমিন)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "মূল ঋণ, সুদ ও সপ্তাহ অনুযায়ী মোট পরিশোধযোগ্য ও কিস্তি স্বয়ংক্রিয়ভাবে হিসাব হবে",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // 1. Member Selection or New Loan Borrower Entry
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "১. ঋণগ্রহীতা নির্বাচন / নতুন ঋণ মেম্বার",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ekotaNavyTextColor()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (activeMembers.isNotEmpty()) {
                            FilterChip(
                                selected = borrowerMode == BorrowerSourceMode.EXISTING_MEMBER,
                                onClick = {
                                    borrowerMode = BorrowerSourceMode.EXISTING_MEMBER
                                    validationError = null
                                },
                                label = { Text("বিদ্যমান সদস্য") },
                                leadingIcon = if (borrowerMode == BorrowerSourceMode.EXISTING_MEMBER) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.testTag("borrower_mode_existing")
                            )
                        }
                        FilterChip(
                            selected = borrowerMode == BorrowerSourceMode.NEW_LOAN_MEMBER,
                            onClick = {
                                borrowerMode = BorrowerSourceMode.NEW_LOAN_MEMBER
                                validationError = null
                            },
                            label = { Text("+ নতুন ঋণের মেম্বার এড") },
                            leadingIcon = if (borrowerMode == BorrowerSourceMode.NEW_LOAN_MEMBER) {
                                { Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("borrower_mode_new")
                        )
                    }

                    if (borrowerMode == BorrowerSourceMode.EXISTING_MEMBER && activeMembers.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = memberDropdownExpanded,
                            onExpandedChange = { memberDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedMember?.let {
                                    "${BengaliUtils.toBengaliDigits(it.memberSerial)} • ${it.name}"
                                } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("সদস্য (ক্রমিক ও নাম)") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberDropdownExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                                    .testTag("loan_member_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = memberDropdownExpanded,
                                onDismissRequest = { memberDropdownExpanded = false }
                            ) {
                                activeMembers.forEach { member ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = "${BengaliUtils.toBengaliDigits(member.memberSerial)} • ${member.name}",
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "এলাকা: ${member.area} | মোবাইল: ${member.mobile}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedMember = member
                                            memberDropdownExpanded = false
                                            validationError = null
                                        }
                                    )
                                }
                            }
                        }

                        selectedMember?.let { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "এলাকা: ${member.area}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "মোবাইল: ${member.mobile}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        if (activeMembers.isEmpty()) {
                            Text(
                                text = "সঞ্চয় সদস্য তালিকা খালি থাকলেও সরাসরি নতুন ঋণের মেম্বার এড করতে পারবেন।",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = newBorrowerName,
                            onValueChange = {
                                newBorrowerName = it
                                validationError = null
                            },
                            label = { Text("ঋণগ্রহীতার নাম *") },
                            leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("loan_new_member_name_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = newBorrowerSerial,
                                onValueChange = { newBorrowerSerial = it },
                                label = { Text("ক্রমিক নং (ঐচ্ছিক)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.45f)
                                    .testTag("loan_new_member_serial_input")
                            )
                            OutlinedTextField(
                                value = newBorrowerMobile,
                                onValueChange = { newBorrowerMobile = it },
                                label = { Text("মোবাইল (ঐচ্ছিক)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(0.55f)
                                    .testTag("loan_new_member_mobile_input")
                            )
                        }

                        OutlinedTextField(
                            value = newBorrowerArea,
                            onValueChange = { newBorrowerArea = it },
                            label = { Text("এলাকা / ঠিকানা (ঐচ্ছিক)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("loan_new_member_area_input")
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = alsoAddToMembersList,
                                onCheckedChange = { alsoAddToMembersList = it },
                                modifier = Modifier.testTag("loan_save_to_members_checkbox")
                            )
                            Text(
                                text = "সদস্য তালিকাতেও সংরক্ষণ করুন (ঐচ্ছিক)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 2. Principal, Interest & Weekly Installment Inputs
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "২. ঋণের পরিমাণ ও সুদের হিসাব",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ekotaNavyTextColor()
                    )

                    OutlinedTextField(
                        value = principalText,
                        onValueChange = { input ->
                            principalText = input.filter { it.isDigit() || it == '.' }
                            validationError = null
                        },
                        label = { Text("মূল ঋণের পরিমাণ (৳)") },
                        prefix = { Text("৳ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("loan_principal_input")
                    )

                    // Quick principal presets
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(2000, 3000, 5000, 10000, 15000).forEach { preset ->
                            FilterChip(
                                selected = principal == preset.toDouble(),
                                onClick = {
                                    principalText = preset.toString()
                                    validationError = null
                                },
                                label = { Text(BengaliUtils.formatTaka(preset.toDouble())) },
                                modifier = Modifier.testTag("principal_preset_$preset")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Interest Mode Selection
                    Text(
                        text = "সুদ নির্ধারণের পদ্ধতি",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = interestMode == InterestInputMode.PERCENT,
                            onClick = { interestMode = InterestInputMode.PERCENT },
                            label = { Text("শতাংশ (%)") },
                            leadingIcon = if (interestMode == InterestInputMode.PERCENT) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("interest_mode_percent")
                        )
                        FilterChip(
                            selected = interestMode == InterestInputMode.FIXED_AMOUNT,
                            onClick = { interestMode = InterestInputMode.FIXED_AMOUNT },
                            label = { Text("নির্ধারিত টাকা (৳)") },
                            leadingIcon = if (interestMode == InterestInputMode.FIXED_AMOUNT) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("interest_mode_fixed")
                        )
                    }

                    if (interestMode == InterestInputMode.PERCENT) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = interestRateText,
                                onValueChange = { input ->
                                    interestRateText = input.filter { it.isDigit() || it == '.' }
                                    validationError = null
                                },
                                label = { Text("সুদের হার (%)") },
                                suffix = { Text("%") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("loan_interest_rate_input")
                            )
                            OutlinedTextField(
                                value = BengaliUtils.formatTaka(calculation.interest),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("হিসাবকৃত সুদ") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(10, 15, 20, 25).forEach { ratePreset ->
                                FilterChip(
                                    selected = (interestRateText.toDoubleOrNull() ?: -1.0) == ratePreset.toDouble(),
                                    onClick = { interestRateText = ratePreset.toString() },
                                    label = { Text("${BengaliUtils.toBengaliDigits(ratePreset)}%") }
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = fixedInterestText,
                                onValueChange = { input ->
                                    fixedInterestText = input.filter { it.isDigit() || it == '.' }
                                    validationError = null
                                },
                                label = { Text("সুদের পরিমাণ (৳)") },
                                prefix = { Text("৳ ") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("loan_interest_amount_input")
                            )
                            OutlinedTextField(
                                value = "${BengaliUtils.toBengaliDigits(calculation.effectiveRatePercent)}%",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("কার্যকর হার") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Installment Duration & Weekly Installment
                    Text(
                        text = "৩. মেয়াদ ও সাপ্তাহিক কিস্তি",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ekotaNavyTextColor()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 12, 16, 20, 24).forEach { weekPreset ->
                            FilterChip(
                                selected = weeks == weekPreset,
                                onClick = {
                                    weeksText = weekPreset.toString()
                                    isCustomInstallment = false
                                    validationError = null
                                },
                                label = { Text("${BengaliUtils.toBengaliDigits(weekPreset)} সপ্তাহ") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = weeksText,
                            onValueChange = { input ->
                                weeksText = input.filter { it.isDigit() }
                                isCustomInstallment = false
                                validationError = null
                            },
                            label = { Text("মোট সপ্তাহ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.85f)
                                .testTag("loan_weeks_input")
                        )

                        OutlinedTextField(
                            value = weeklyInstallmentText,
                            onValueChange = { input ->
                                weeklyInstallmentText = input.filter { it.isDigit() || it == '.' }
                                isCustomInstallment = true
                                validationError = null
                            },
                            label = { Text("সাপ্তাহিক কিস্তি (৳)") },
                            prefix = { Text("৳ ") },
                            trailingIcon = {
                                if (isCustomInstallment) {
                                    IconButton(
                                        onClick = { isCustomInstallment = false }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "স্বয়ংক্রিয় কিস্তি হিসাব করুন"
                                        )
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.15f)
                                .testTag("loan_weekly_installment_input")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = loanDate,
                            onValueChange = { newDate ->
                                loanDate = newDate
                                if (!isNextDateManuallyEdited) {
                                    nextInstallmentDate = BengaliUtils.addDaysToDateStr(newDate, 7)
                                }
                                validationError = null
                            },
                            label = { Text("বিতরণের তারিখ") },
                            placeholder = { Text("dd/MM/yy") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("loan_date_input")
                        )

                        OutlinedTextField(
                            value = nextInstallmentDate,
                            onValueChange = {
                                nextInstallmentDate = it
                                isNextDateManuallyEdited = true
                                validationError = null
                            },
                            label = { Text("প্রথম কিস্তির তারিখ") },
                            placeholder = { Text("dd/MM/yy") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("loan_next_date_input")
                        )
                    }
                }
            }

            // 3. Live Calculation Summary Card (Vibrant Gradient)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("loan_calculation_summary_card")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(colors = CardBlueGradient))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ঋণ হিসাব সারাংশ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        LoanSummaryRow(
                            label = "মূল ঋণ (Principal)",
                            value = BengaliUtils.formatTaka(calculation.principal)
                        )
                        LoanSummaryRow(
                            label = "নির্ধারিত সুদ (${BengaliUtils.toBengaliDigits(calculation.effectiveRatePercent)}%)",
                            value = BengaliUtils.formatTaka(calculation.interest)
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.28f))
                        LoanSummaryRow(
                            label = "মোট পরিশোধযোগ্য (Total Payable)",
                            value = BengaliUtils.formatTaka(calculation.totalPayable),
                            valueColor = Color(0xFFFEF08A),
                            isBold = true
                        )
                        LoanSummaryRow(
                            label = "সাপ্তাহিক কিস্তি (${BengaliUtils.toBengaliDigits(weeks.coerceAtLeast(0))} সপ্তাহ)",
                            value = BengaliUtils.formatTaka(finalWeeklyInstallment),
                            valueColor = Color(0xFFDCFCE7),
                            isBold = true
                        )
                    }
                }
            }

            if (validationError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = validationError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onCancel != null) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.4f)
                            .height(52.dp)
                            .testTag("cancel_new_loan_button")
                    ) {
                        Text("বাতিল")
                    }
                }

                Button(
                    onClick = {
                        val isDirectNewBorrower = borrowerMode == BorrowerSourceMode.NEW_LOAN_MEMBER || activeMembers.isEmpty()
                        val existingMember = selectedMember
                        val interestVal = calculation.interest
                        when {
                            isDirectNewBorrower && newBorrowerName.isBlank() ->
                                validationError = "নতুন ঋণের মেম্বারের নাম লিখুন"
                            !isDirectNewBorrower && existingMember == null ->
                                validationError = "অনুগ্রহ করে একজন সদস্য নির্বাচন করুন"
                            principal <= 0.0 -> validationError = "মূল ঋণের পরিমাণ সঠিকভাবে লিখুন"
                            interestVal < 0.0 -> validationError = "সুদের পরিমাণ ঋণাত্মক হতে পারবে না"
                            finalWeeklyInstallment <= 0.0 -> validationError = "সাপ্তাহিক কিস্তির পরিমাণ সঠিকভাবে লিখুন"
                            finalWeeklyInstallment > calculation.totalPayable ->
                                validationError = "সাপ্তাহিক কিস্তি মোট পরিশোধযোগ্য টাকার বেশি হতে পারবে না"
                            loanDate.isBlank() -> validationError = "ঋণ বিতরণের তারিখ লিখুন"
                            else -> {
                                validationError = null
                                val targetMemberId: String
                                val targetMemberName: String
                                val targetMemberSerial: String
                                val optionalNewMember: Member?

                                if (isDirectNewBorrower) {
                                    targetMemberId = "loan_mem_${System.currentTimeMillis()}"
                                    targetMemberName = newBorrowerName.trim()
                                    targetMemberSerial = newBorrowerSerial.trim()
                                    optionalNewMember = if (alsoAddToMembersList) {
                                        Member(
                                            id = targetMemberId,
                                            memberSerial = targetMemberSerial,
                                            name = targetMemberName,
                                            mobile = newBorrowerMobile.trim(),
                                            area = newBorrowerArea.trim().ifBlank { "যাদবপুর" },
                                            joinDate = loanDate.trim(),
                                            weeklySavingsAmount = 100.0,
                                            isActive = true
                                        )
                                    } else null
                                } else {
                                    targetMemberId = existingMember!!.id
                                    targetMemberName = existingMember.name
                                    targetMemberSerial = existingMember.memberSerial
                                    optionalNewMember = null
                                }

                                val newLoan = Loan(
                                    memberId = targetMemberId,
                                    memberName = targetMemberName,
                                    memberSerial = targetMemberSerial,
                                    loanDate = loanDate.trim(),
                                    principal = calculation.principal,
                                    interest = interestVal,
                                    totalPayable = calculation.totalPayable,
                                    weeklyInstallment = finalWeeklyInstallment,
                                    totalPaid = 0.0,
                                    remainingLoan = calculation.totalPayable,
                                    nextInstallmentDate = nextInstallmentDate.trim().ifBlank {
                                        BengaliUtils.addDaysToDateStr(loanDate.trim(), 7)
                                    }
                                )
                                onDistribute(newLoan, optionalNewMember)
                            }
                        }
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreenPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(if (onCancel != null) 0.6f else 1f)
                        .height(52.dp)
                        .testTag("submit_new_loan_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("সংরক্ষণ হচ্ছে...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ঋণ বিতরণ সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanSummaryRow(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.88f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = if (isBold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            softWrap = false
        )
    }
}
