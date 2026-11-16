package com.example.ui.savings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import com.example.util.SavingsAndFineCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleMemberSavingsDepositForm(
    members: List<Member>,
    selectedDate: String,
    savingsRecords: List<SavingsRecord>,
    isCloudAuthenticated: Boolean,
    isOnline: Boolean,
    isAdmin: Boolean,
    collectorName: String,
    onDateChange: (String) -> Unit,
    onSubmitDeposit: (member: Member, collectionDate: String, amount: Double) -> Unit
) {
    var selectedMemberId by remember(members) {
        mutableStateOf(members.firstOrNull()?.id ?: "")
    }
    val selectedMember = remember(members, selectedMemberId) {
        members.firstOrNull { it.id == selectedMemberId } ?: members.firstOrNull()
    }

    var dropdownExpanded by remember { mutableStateOf(false) }
    var dateInput by remember(selectedDate) { mutableStateOf(selectedDate) }
    var amountInput by remember(selectedMember?.id) {
        mutableStateOf(selectedMember?.weeklySavingsAmount?.toInt()?.toString() ?: "100")
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    val existingThisWeek = remember(selectedMember?.id, dateInput, savingsRecords) {
        val m = selectedMember ?: return@remember null
        val cleanDate = dateInput.trim()
        if (cleanDate.isBlank()) return@remember null
        savingsRecords.firstOrNull {
            (it.memberId == m.id || it.memberName == m.name) &&
                it.status != "REJECTED" &&
                SavingsAndFineCalculator.isSameWeek(it.collectionDate, cleanDate)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Firestore Destination Banner
        val bannerBg = when {
            isCloudAuthenticated && isOnline -> ekotaBadgeSuccessBg()
            isCloudAuthenticated -> ekotaBadgeWarningBg()
            else -> CardBlueGradient.first().copy(alpha = 0.12f)
        }
        val bannerTextColor = when {
            isCloudAuthenticated && isOnline -> ekotaSuccessTextColor()
            isCloudAuthenticated -> ekotaWarningTextColor()
            else -> ekotaNavyTextColor()
        }
        val bannerIcon = when {
            isCloudAuthenticated && isOnline -> Icons.Default.CloudDone
            isCloudAuthenticated -> Icons.Default.CloudOff
            else -> Icons.Default.Storage
        }
        val bannerTitle = when {
            isCloudAuthenticated && isOnline -> "Cloud Firestore সংযুক্ত (সরাসরি ক্লাউডে সংরক্ষণ হবে)"
            isCloudAuthenticated -> "অফলাইন ক্যাশ সক্রিয় (নেট ফিরলে Firestore-এ সিঙ্ক হবে)"
            else -> "লোকাল মোড সক্রিয় (Firestore সিঙ্কের জন্য Google লগইন করুন)"
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = bannerBg,
            border = BorderStroke(1.dp, bannerTextColor.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("single_deposit_firestore_banner")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = bannerIcon,
                    contentDescription = null,
                    tint = bannerTextColor,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = bannerTitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = bannerTextColor
                )
            }
        }

        // Main Form Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, CardTealGradient.first().copy(alpha = 0.45f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("single_member_deposit_form_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(CardTealGradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "সাপ্তাহিক সঞ্চয় জমা ফর্ম",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "জমাদানকারী: $collectorName • ${if (isAdmin) "সরাসরি অনুমোদন" else "অনুমোদনযোগ্য"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Member Selector Dropdown
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val displayMemberText = if (selectedMember != null) {
                        "${BengaliUtils.toBengaliDigits(selectedMember.memberSerial)}. ${selectedMember.name} (${selectedMember.area.ifBlank { "যাদবপুর" }})"
                    } else {
                        "সদস্য নির্বাচন করুন"
                    }
                    OutlinedTextField(
                        value = displayMemberText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("সদস্য নির্বাচন করুন") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("single_deposit_member_dropdown")
                    )

                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        members.forEach { member ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${BengaliUtils.toBengaliDigits(member.memberSerial)}. ${member.name} — ৳${member.weeklySavingsAmount.toInt()}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    selectedMemberId = member.id
                                    amountInput = member.weeklySavingsAmount.toInt().toString()
                                    validationError = null
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (members.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ekotaBadgeWarningBg(),
                        border = BorderStroke(1.dp, ekotaWarningTextColor().copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "কোনো সক্রিয় সদস্য পাওয়া যায়নি। অনুগ্রহ করে উপরের (+) বাটন থেকে আগে সদস্য যোগ করুন।",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ekotaWarningTextColor(),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Selected Member Quick Info Pill
                if (selectedMember != null) {
                    val accent = VibrantCardAccents[0]
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accent.softSurface,
                        border = BorderStroke(1.dp, accent.gradient.first().copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedMember.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (LocalIsDarkEkotaTheme.current) MaterialTheme.colorScheme.onSurface else TextDark
                                )
                                Text(
                                    text = "মোবাইল: ${selectedMember.mobile.ifBlank { "-" }} • নির্ধারিত: ${BengaliUtils.formatTaka(selectedMember.weeklySavingsAmount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (LocalIsDarkEkotaTheme.current) MaterialTheme.colorScheme.onSurfaceVariant else TextDark.copy(alpha = 0.88f)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (existingThisWeek != null) ekotaBadgeSuccessBg() else Color.White
                            ) {
                                Text(
                                    text = if (existingThisWeek != null) "জমা সম্পন্ন ✓" else "বাকি আছে",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (existingThisWeek != null) ekotaSuccessTextColor() else accent.accentInk,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Collection Date Field
                OutlinedTextField(
                    value = dateInput,
                    onValueChange = {
                        dateInput = it
                        validationError = null
                        if (SavingsAndFineCalculator.parseDateMillis(it.trim()) != null) {
                            onDateChange(it.trim())
                        }
                    },
                    label = { Text("কালেকশন তারিখ (dd/MM/yy)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("single_deposit_date_input")
                )

                // Deposit Amount Field
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { newVal ->
                        amountInput = newVal.filter { ch -> ch.isDigit() || ch == '.' }
                        validationError = null
                    },
                    label = { Text("জমার পরিমাণ (টাকা / ৳)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = EmeraldGreenPrimary
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = existingThisWeek == null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CardTealGradient.first(),
                        focusedLabelColor = CardTealGradient.first(),
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = CardTealGradient.first().copy(alpha = 0.60f),
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = EmeraldGreenPrimary,
                        disabledContainerColor = if (LocalIsDarkEkotaTheme.current) MaterialTheme.colorScheme.surfaceVariant else Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("single_deposit_amount_input")
                )

                // Quick Amount Chips
                val baseAmt = selectedMember?.weeklySavingsAmount?.toInt() ?: 100
                val presets = remember(baseAmt) {
                    listOf(0, baseAmt, baseAmt * 2, 500).distinct()
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val isChosen = amountInput.toIntOrNull() == preset
                        FilterChip(
                            selected = isChosen,
                            enabled = existingThisWeek == null,
                            onClick = {
                                amountInput = preset.toString()
                                validationError = null
                            },
                            label = {
                                Text(
                                    text = if (preset == 0) "৳০ (দেয়নি)" else "৳${BengaliUtils.toBengaliDigits(preset)}",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurface,
                                selectedContainerColor = HeaderSlateNavy,
                                selectedLabelColor = Color.White,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledLabelColor = if (isChosen) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                                disabledSelectedContainerColor = HeaderSlateNavy
                            )
                        )
                    }
                }

                validationError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ekotaBadgeDangerBg(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ekotaDangerTextColor(),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // Submit Button (Hidden once already submitted for this week)
                if (existingThisWeek != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ekotaBadgeSuccessBg(),
                        border = BorderStroke(1.dp, ekotaSuccessTextColor().copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("single_deposit_completed_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = ekotaSuccessTextColor(),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${selectedMember?.name ?: "সদস্য"}-এর এই সপ্তাহের (${existingThisWeek.collectionDate}) ${BengaliUtils.formatTaka(existingThisWeek.amount)} সঞ্চয় জমা সম্পন্ন ✓",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = ekotaSuccessTextColor()
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            val member = selectedMember
                            val cleanDate = dateInput.trim()
                            val parsedAmt = amountInput.toDoubleOrNull()
                            when {
                                member == null -> validationError = "অনুগ্রহ করে একজন সদস্য নির্বাচন করুন"
                                cleanDate.isEmpty() || SavingsAndFineCalculator.parseDateMillis(cleanDate) == null ->
                                    validationError = "সঠিক তারিখ লিখুন (যেমন: 15/11/26)"
                                parsedAmt == null || parsedAmt < 0.0 ->
                                    validationError = "সঠিক টাকার পরিমাণ লিখুন"
                                else -> {
                                    validationError = null
                                    onSubmitDeposit(member, cleanDate, parsedAmt)
                                }
                            }
                        },
                        enabled = selectedMember != null,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGreenPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("submit_single_savings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCloudAuthenticated) {
                                "Firestore-এ সঞ্চয় জমা করুন"
                            } else {
                                "সাপ্তাহিক সঞ্চয় জমা করুন"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
