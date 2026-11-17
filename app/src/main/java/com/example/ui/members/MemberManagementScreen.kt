package com.example.ui.members

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

            // Member List
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
                            onClick = {
                                if (onNavigateToMemberRegistration != null) {
                                    onNavigateToMemberRegistration()
                                } else {
                                    showAddDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGreenPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("নতুন সদস্য নিবন্ধন করুন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(filteredMembers, key = { _, m -> m.id }) { index, member ->
                        MemberCard(
                            member = member,
                            isAdmin = isAdmin,
                            cardAccentIndex = index,
                            onCardClick = { memberDetailsToShow = member },
                            onEditClick = { memberToEdit = member }
                        )
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
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.surface else accent.softSurface
        ),
        border = BorderStroke(1.dp, accent.gradient.first().copy(alpha = 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("member_card_${member.memberSerial}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vibrant Gradient Serial Circle Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(accent.gradient)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = BengaliUtils.toBengaliDigits(member.memberSerial),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (member.isActive) ekotaBadgeSuccessBg() else ekotaBadgeDangerBg()
                    ) {
                        Text(
                            text = if (member.isActive) "সক্রিয়" else "নিষ্ক্রিয়",
                            color = if (member.isActive) ekotaSuccessTextColor() else ekotaDangerTextColor(),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "মোবাইল: ${member.mobile} | এলাকা: ${member.area}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "সাপ্তাহিক সঞ্চয়: ${BengaliUtils.formatTaka(member.weeklySavingsAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) accent.gradient.last() else accent.accentInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isAdmin) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.testTag("edit_member_btn_${member.id}")
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "সম্পাদনা",
                        tint = if (isDark) accent.gradient.last() else accent.accentInk
                    )
                }
            }
        }
    }
}

@Composable
fun MemberFormDialog(
    title: String,
    initialMember: Member?,
    onDismiss: () -> Unit,
    onSave: (Member) -> Unit
) {
    var serial by remember { mutableStateOf(initialMember?.memberSerial ?: "") }
    var name by remember { mutableStateOf(initialMember?.name ?: "") }
    var mobile by remember { mutableStateOf(initialMember?.mobile ?: "") }
    var address by remember { mutableStateOf(initialMember?.address ?: "") }
    var area by remember { mutableStateOf(initialMember?.area ?: "যাদবপুর উত্তর") }
    var weeklySavings by remember { mutableStateOf(initialMember?.weeklySavingsAmount?.toInt()?.toString() ?: "100") }
    var isActive by remember { mutableStateOf(initialMember?.isActive ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = serial,
                    onValueChange = { serial = it },
                    label = { Text("ক্রমিক নং (যেমন: ১১)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("সদস্যের নাম") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
                OutlinedTextField(
                    value = weeklySavings,
                    onValueChange = { weeklySavings = it },
                    label = { Text("সাপ্তাহিক সঞ্চয়ের পরিমাণ (টাকা)") },
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
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && serial.isNotBlank()) {
                        val member = (initialMember ?: Member()).copy(
                            memberSerial = serial,
                            name = name,
                            mobile = mobile,
                            address = address,
                            area = area,
                            weeklySavingsAmount = weeklySavings.toDoubleOrNull() ?: 100.0,
                            isActive = isActive
                        )
                        onSave(member)
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
