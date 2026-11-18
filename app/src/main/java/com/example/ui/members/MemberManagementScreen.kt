package com.example.ui.members

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Member
import com.example.data.model.UserRole
import com.example.ui.MainViewModel
import com.example.ui.components.EkotaCompactTopBar
import com.example.ui.theme.*
import com.example.util.BengaliUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberManagementScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMemberRegistration: (() -> Unit)? = null
) {
    val members by viewModel.members.collectAsState()
    val role by viewModel.userRole.collectAsState()
    val userProfile by viewModel.currentUserProfile.collectAsState()
    val isAdmin = role == UserRole.ADMIN

    var selectedAreaFilter by remember { mutableStateOf("সকল এলাকা") }
    var showAddDialog by remember { mutableStateOf(false) }
    var memberToEdit by remember { mutableStateOf<Member?>(null) }
    var memberDetailsToShow by remember { mutableStateOf<Member?>(null) }
    val suggestedSerial = remember(members) { viewModel.suggestNextMemberSerial(members) }

    // Area-based filtering for Field Officer vs Admin
    val accessibleMembers = remember(members, role, userProfile) {
        if (role == UserRole.FIELD_OFFICER && !userProfile?.assignedArea.isNullOrBlank()) {
            val officerArea = userProfile?.assignedArea ?: ""
            members.filter { it.area.contains(officerArea, ignoreCase = true) || officerArea.contains(it.area, ignoreCase = true) }
        } else {
            members
        }
    }

    val filteredMembers = remember(accessibleMembers, selectedAreaFilter) {
        if (selectedAreaFilter == "সকল এলাকা") {
            accessibleMembers
        } else {
            accessibleMembers.filter { it.area == selectedAreaFilter }
        }
    }

    val areaList = remember(members) {
        listOf("সকল এলাকা") + members.map { it.area }.filter { it.isNotBlank() }.distinct()
    }

    Scaffold(
        topBar = {
            EkotaCompactTopBar(
                title = "সদস্য ব্যবস্থাপনা (${BengaliUtils.toBengaliDigits(filteredMembers.size)} জন)",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "ফিরে যান")
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("members_register_top_button")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "সদস্য যোগ করুন")
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
                .padding(16.dp)
        ) {
            // Area Filter Chips
            ScrollableTabRow(
                selectedTabIndex = areaList.indexOf(selectedAreaFilter).coerceAtLeast(0),
                containerColor = Color.Transparent,
                edgePadding = 0.dp,
                divider = {},
                indicator = {}
            ) {
                areaList.forEach { area ->
                    val isSelected = area == selectedAreaFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedAreaFilter = area },
                        label = { Text(area, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, softWrap = false) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = HeaderSlateNavy,
                            selectedLabelColor = Color.White,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Member List with SL | নাম | টাকা Header
            if (filteredMembers.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "কোনো সদস্য পাওয়া যায়নি",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGreenPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("নতুন সদস্য যোগ করুন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Table / List Header: SL | নাম | টাকা
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HeaderSlateNavy)
                                .padding(horizontal = 12.dp, vertical = 11.dp)
                                .testTag("member_list_header"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SL",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.width(52.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "নাম",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "টাকা",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.width(88.dp)
                            )
                            if (isAdmin) {
                                Spacer(modifier = Modifier.width(48.dp))
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("member_list_body")
                        ) {
                            itemsIndexed(filteredMembers, key = { _, m -> m.id }) { index, member ->
                                MemberCard(
                                    member = member,
                                    isAdmin = isAdmin,
                                    cardAccentIndex = index,
                                    onCardClick = { memberDetailsToShow = member },
                                    onEditClick = { memberToEdit = member }
                                )
                                if (index < filteredMembers.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 0.75.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Member Dialog
    if (showAddDialog) {
        MemberFormDialog(
            title = "নতুন সদস্য যোগ করুন",
            initialMember = null,
            suggestedSerial = suggestedSerial,
            onDismiss = { showAddDialog = false },
            onSave = { newMember ->
                viewModel.addMember(newMember, onSuccess = { showAddDialog = false }, onError = {})
            }
        )
    }

    // Edit Member Dialog (Admin Only)
    if (isAdmin) {
        memberToEdit?.let { member ->
            MemberFormDialog(
                title = "সদস্য তথ্য সংশোধন",
                initialMember = member,
                onDismiss = { memberToEdit = null },
                onSave = { updated ->
                    viewModel.updateMember(updated, onSuccess = { memberToEdit = null }, onError = {})
                }
            )
        }
    }

    // Member Details Sheet / Dialog
    memberDetailsToShow?.let { member ->
        MemberDetailsDialog(
            member = member,
            isAdmin = isAdmin,
            onDismiss = { memberDetailsToShow = null },
            onEdit = {
                memberDetailsToShow = null
                if (isAdmin) {
                    memberToEdit = member
                }
            }
        )
    }
}

@Composable
fun MemberCard(
    member: Member,
    isAdmin: Boolean = false,
    cardAccentIndex: Int = 0,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val isDark = LocalIsDarkEkotaTheme.current
    val accent = VibrantCardAccents[cardAccentIndex % VibrantCardAccents.size]
    val rowBg = when {
        isDark && cardAccentIndex % 2 == 1 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        !isDark && cardAccentIndex % 2 == 1 -> accent.softSurface.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .background(rowBg)
            .clickable { onCardClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("member_card_${member.memberSerial}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Column 1: SL (01, 02, ...)
        Text(
            text = BengaliUtils.formatTwoDigitSerial(member.memberSerial),
            fontWeight = FontWeight.Bold,
            color = if (isDark) accent.gradient.last() else HeaderSlateNavy,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(52.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Column 2: নাম
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = member.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (!member.isActive) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = ekotaBadgeDangerBg()
                ) {
                    Text(
                        text = "নিষ্ক্রিয়",
                        color = ekotaDangerTextColor(),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Column 3: টাকা (100, 150, ...)
        Text(
            text = BengaliUtils.formatCleanAmount(member.weeklySavingsAmount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isDark) accent.gradient.last() else accent.accentInk,
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(88.dp)
        )

        if (isAdmin) {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("edit_member_btn_${member.id}")
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "সম্পাদনা",
                    tint = if (isDark) accent.gradient.last() else accent.accentInk,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun normalizeBengaliNumberText(raw: String): String {
    val sb = StringBuilder(raw.length)
    for (ch in raw) {
        sb.append(
            when (ch) {
                '০' -> '0'; '১' -> '1'; '২' -> '2'; '৩' -> '3'; '৪' -> '4'
                '৫' -> '5'; '৬' -> '6'; '৭' -> '7'; '৮' -> '8'; '৯' -> '9'
                else -> ch
            }
        )
    }
    return sb.toString()
}

@Composable
fun MemberFormDialog(
    title: String,
    initialMember: Member?,
    suggestedSerial: String = "1",
    onDismiss: () -> Unit,
    onSave: (Member) -> Unit
) {
    val isNewMember = initialMember == null
    var serial by remember(initialMember, suggestedSerial) {
        mutableStateOf(initialMember?.memberSerial?.ifBlank { suggestedSerial } ?: suggestedSerial)
    }
    var name by remember(initialMember) { mutableStateOf(initialMember?.name ?: "") }
    var mobile by remember(initialMember) { mutableStateOf(initialMember?.mobile ?: "") }
    var address by remember(initialMember) { mutableStateOf(initialMember?.address ?: "") }
    var area by remember(initialMember) { mutableStateOf(initialMember?.area ?: "যাদবপুর উত্তর") }
    var weeklySavings by remember(initialMember) {
        mutableStateOf(initialMember?.weeklySavingsAmount?.toInt()?.toString() ?: "100")
    }
    var isActive by remember(initialMember) { mutableStateOf(initialMember?.isActive ?: true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = serial,
                    onValueChange = {
                        serial = it
                        errorMsg = null
                    },
                    label = { Text("ক্রমিক নং (SL অটো)") },
                    trailingIcon = {
                        if (isNewMember) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ekotaBadgeSuccessBg(),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "অটো",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ekotaSuccessTextColor(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_form_serial_input")
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMsg = null
                    },
                    label = { Text("সদস্যের নাম") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_form_name_input")
                )
                OutlinedTextField(
                    value = weeklySavings,
                    onValueChange = {
                        weeklySavings = it
                        errorMsg = null
                    },
                    label = { Text("টাকা (সাপ্তাহিক সঞ্চয়)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_form_taka_input")
                )

                if (!isNewMember) {
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = { mobile = it },
                        label = { Text("মোবাইল নম্বর") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("ঠিকানা") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = area,
                        onValueChange = { area = it },
                        label = { Text("এলাকা (যেমন: যাদবপুর দক্ষিণ)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = isActive, onCheckedChange = { isActive = it })
                        Text(text = "সদস্য সক্রিয় রাখুন", style = MaterialTheme.typography.bodyMedium)
                    }
                }

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
                    val cleanSerial = normalizeBengaliNumberText(serial.trim()).ifBlank { suggestedSerial }
                    val cleanName = name.trim()
                    val parsedTaka = normalizeBengaliNumberText(weeklySavings.trim()).toDoubleOrNull()
                    when {
                        cleanName.isBlank() -> errorMsg = "অনুগ্রহ করে সদস্যের নাম লিখুন"
                        parsedTaka == null || parsedTaka <= 0.0 -> errorMsg = "সঠিক টাকার পরিমাণ লিখুন"
                        else -> {
                            val member = (initialMember ?: Member()).copy(
                                memberSerial = cleanSerial,
                                name = cleanName,
                                mobile = mobile.trim(),
                                address = address.trim(),
                                area = area.trim().ifBlank { "যাদবপুর উত্তর" },
                                weeklySavingsAmount = parsedTaka,
                                isActive = isActive
                            )
                            onSave(member)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreenPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("member_form_save_button")
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

@Composable
fun MemberDetailsDialog(
    member: Member,
    isAdmin: Boolean = false,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = member.name, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow("ক্রমিক নং:", BengaliUtils.toBengaliDigits(member.memberSerial))
                DetailRow("মোবাইল নম্বর:", member.mobile.ifBlank { "-" })
                DetailRow("ঠিকানা:", member.address.ifBlank { "যাদবপুর" })
                DetailRow("এলাকা:", member.area.ifBlank { "-" })
                DetailRow("যোগদানের তারিখ:", member.joinDate.ifBlank { "-" })
                DetailRow("সাপ্তাহিক সঞ্চয়:", BengaliUtils.formatTaka(member.weeklySavingsAmount))
                DetailRow("মাঠ কর্মকর্তা:", member.assignedOfficerName.ifBlank { "-" })
                DetailRow("অবস্থা:", if (member.isActive) "সক্রিয়" else "নিষ্ক্রিয়")
            }
        },
        confirmButton = {
            if (isAdmin) {
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreenPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("সংশোধন")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
