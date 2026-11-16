package com.example.ui.members

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Member
import com.example.data.model.UserRole
import com.example.data.repository.SyncStatusState
import com.example.ui.MainViewModel
import com.example.ui.auth.GoogleSignInButton
import com.example.ui.components.EkotaCompactTopBar
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberRegistrationScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMembers: () -> Unit
) {
    val members by viewModel.members.collectAsStateWithLifecycle()
    val role by viewModel.userRole.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val isSavingMember by viewModel.isSavingMember.collectAsStateWithLifecycle()
    val isAdmin = role == UserRole.ADMIN

    val suggestedSerial = remember(members) { viewModel.suggestNextMemberSerial(members) }
    val areaSuggestions = remember(members) {
        val defaults = listOf("যাদবপুর উত্তর", "যাদবপুর দক্ষিণ", "বাজার পাড়া")
        (members.map { it.area.trim() }.filter { it.isNotBlank() } + defaults).distinct()
    }

    MemberRegistrationContent(
        isAdmin = isAdmin,
        suggestedSerial = suggestedSerial,
        areaSuggestions = areaSuggestions,
        syncStatus = syncStatus,
        isSaving = isSavingMember,
        onRegisterMember = { member, onOk, onErr ->
            viewModel.registerMember(member, onSuccess = onOk, onError = onErr)
        },
        onNavigateBack = onNavigateBack,
        onNavigateToMembers = onNavigateToMembers
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberRegistrationContent(
    isAdmin: Boolean,
    suggestedSerial: String,
    areaSuggestions: List<String>,
    syncStatus: SyncStatusState,
    isSaving: Boolean,
    onRegisterMember: (Member, (Member) -> Unit, (String) -> Unit) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToMembers: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var serial by remember { mutableStateOf(suggestedSerial) }
    var userEditedSerial by remember { mutableStateOf(false) }
    LaunchedEffect(suggestedSerial) {
        if (!userEditedSerial) {
            serial = suggestedSerial
        }
    }

    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var area by remember { mutableStateOf(areaSuggestions.firstOrNull() ?: "যাদবপুর উত্তর") }
    var weeklySavings by remember { mutableStateOf("100") }
    var joinDate by remember { mutableStateOf(BengaliUtils.getCurrentDateStr()) }
    var isActive by remember { mutableStateOf(true) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var serialError by remember { mutableStateOf<String?>(null) }
    var formErrorBanner by remember { mutableStateOf<String?>(null) }
    var savedMemberDialog by remember { mutableStateOf<Member?>(null) }

    BackHandler(enabled = true) {
        onNavigateBack()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            EkotaCompactTopBar(
                title = "নতুন সদস্য নিবন্ধন",
                subtitle = "Firestore ডাটাবেসে নতুন সদস্য সংরক্ষণ",
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("member_reg_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "ফিরে যান"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ekotaSilverBackgroundBrush())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!isAdmin) {
                    // Admin-Only Authorization Guard
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.45f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("member_reg_admin_only_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(ekotaBadgeDangerBg()),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "অ্যাডমিন অনুমতি প্রয়োজন",
                                    tint = ekotaDangerTextColor(),
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                            Text(
                                text = "অ্যাডমিন অনুমোদন প্রয়োজন",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "নতুন সদস্য নিবন্ধন ও তথ্য সংরক্ষণ শুধুমাত্র অ্যাডমিন প্যানেল থেকে করা যাবে।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onNavigateBack,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HeaderSlateNavy,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("ফিরে যান", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Hero Banner Card
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.linearGradient(colors = CardBlueGradient))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "সদস্য নিবন্ধন ফরম (Admin)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "নাম, ফোন নম্বর ও ঠিকানা পূরণ করে সরাসরি Firestore-এ সংরক্ষণ করুন",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.92f)
                                )
                            }
                        }
                    }

                    // Firestore Connection & Offline Sync Banner
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(
                            1.dp,
                            (if (syncStatus.isCloudAuthenticated && syncStatus.isOnline) {
                                EmeraldGreenPrimary
                            } else {
                                ekotaWarningTextColor()
                            }).copy(alpha = 0.45f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("member_reg_firestore_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = when {
                                        !syncStatus.isOnline -> Icons.Default.CloudOff
                                        syncStatus.isCloudAuthenticated -> Icons.Default.CloudDone
                                        else -> Icons.Default.CloudSync
                                    },
                                    contentDescription = "Firestore অবস্থা",
                                    tint = if (syncStatus.isCloudAuthenticated && syncStatus.isOnline) {
                                        ekotaSuccessTextColor()
                                    } else {
                                        ekotaWarningTextColor()
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when {
                                            syncStatus.isCloudAuthenticated && syncStatus.isOnline ->
                                                "Firestore ক্লাউড ডাটাবেস সংযুক্ত ✓"
                                            syncStatus.isCloudAuthenticated ->
                                                "অফলাইন মোড • সংরক্ষিত তথ্য নেট পেলে Firestore-এ সিঙ্ক হবে"
                                            else ->
                                                "ক্লাউড সিঙ্ক (Google) এখনো সংযুক্ত নয়"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (syncStatus.isCloudAuthenticated) {
                                            "নতুন সদস্য যোগ করার সাথে সাথেই মেম্বার ডিরেক্টরি আপডেট হবে।"
                                        } else {
                                            "সরাসরি ক্লাউডে পাঠাতে Google সাইন-ইন করুন, অথবা নিচে সেভ করলে লোকাল স্টোরেজ থেকে পরে সিঙ্ক হবে।"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!syncStatus.isCloudAuthenticated) {
                                GoogleSignInButton(
                                    label = "Google দিয়ে Firestore সংযুক্ত করুন",
                                    onAuthSuccess = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Firestore ক্লাউড ডাটাবেস সফলভাবে সংযুক্ত হয়েছে ✓")
                                        }
                                    },
                                    onAuthError = { err ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(err)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Registration Form Inputs Card
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "সদস্যের ব্যক্তিগত ও সমিতি তথ্য",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ekotaSectionTitleColor()
                            )

                            OutlinedTextField(
                                value = serial,
                                onValueChange = {
                                    serial = it
                                    userEditedSerial = true
                                    serialError = null
                                    formErrorBanner = null
                                },
                                label = { Text("ক্রমিক নং (Member Serial) *") },
                                placeholder = { Text("যেমন: ১১") },
                                leadingIcon = {
                                    Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                isError = serialError != null,
                                supportingText = serialError?.let { msg -> { Text(msg) } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_serial_input")
                            )

                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    nameError = null
                                    formErrorBanner = null
                                },
                                label = { Text("সদস্যের পূর্ণ নাম (Name) *") },
                                placeholder = { Text("সদস্যের পুরো নাম লিখুন") },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                isError = nameError != null,
                                supportingText = nameError?.let { msg -> { Text(msg) } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_name_input")
                            )

                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { input ->
                                    phoneNumber = input.filter { it.isDigit() || it == '+' || it == '-' || it == ' ' }
                                    phoneError = null
                                    formErrorBanner = null
                                },
                                label = { Text("মোবাইল নম্বর (Phone Number) *") },
                                placeholder = { Text("017XXXXXXXX") },
                                leadingIcon = {
                                    Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                isError = phoneError != null,
                                supportingText = phoneError?.let { msg -> { Text(msg) } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_phone_input")
                            )

                            OutlinedTextField(
                                value = address,
                                onValueChange = {
                                    address = it
                                    addressError = null
                                    formErrorBanner = null
                                },
                                label = { Text("ঠিকানা (Address) *") },
                                placeholder = { Text("গ্রাম / মহল্লা, পোস্ট, উপজেলা") },
                                leadingIcon = {
                                    Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                isError = addressError != null,
                                supportingText = addressError?.let { msg -> { Text(msg) } },
                                minLines = 2,
                                maxLines = 3,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_address_input")
                            )

                            OutlinedTextField(
                                value = area,
                                onValueChange = { area = it },
                                label = { Text("এলাকা / কেন্দ্র (Area)") },
                                placeholder = { Text("যেমন: যাদবপুর উত্তর") },
                                leadingIcon = {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_area_input")
                            )

                            if (areaSuggestions.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    areaSuggestions.forEach { suggestedArea ->
                                        val isChosen = area.trim() == suggestedArea
                                        FilterChip(
                                            selected = isChosen,
                                            onClick = { area = suggestedArea },
                                            label = {
                                                Text(
                                                    text = suggestedArea,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = HeaderSlateNavy,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = weeklySavings,
                                onValueChange = { input ->
                                    weeklySavings = input.filter { it.isDigit() }
                                },
                                label = { Text("সাপ্তাহিক সঞ্চয়ের পরিমাণ (টাকা)") },
                                leadingIcon = {
                                    Icon(Icons.Default.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_weekly_savings_input")
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(50, 100, 200, 500).forEach { preset ->
                                    val selected = weeklySavings == preset.toString()
                                    FilterChip(
                                        selected = selected,
                                        onClick = { weeklySavings = preset.toString() },
                                        label = {
                                            Text(
                                                text = "৳${BengaliUtils.toBengaliDigits(preset)}",
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EmeraldGreenPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = joinDate,
                                onValueChange = { joinDate = it },
                                label = { Text("যোগদানের তারিখ (dd/MM/yy)") },
                                leadingIcon = {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reg_join_date_input")
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "সদস্য সক্রিয় রাখুন",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "সক্রিয় সদস্যরা সাপ্তাহিক সঞ্চয় ও ঋণ তালিকায় যুক্ত থাকবেন",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isActive,
                                        onCheckedChange = { isActive = it },
                                        modifier = Modifier.testTag("reg_active_switch")
                                    )
                                }
                            }

                            if (formErrorBanner != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("reg_error_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = formErrorBanner ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    var hasError = false
                                    if (serial.trim().isBlank()) {
                                        serialError = "ক্রমিক নং দিন"
                                        hasError = true
                                    }
                                    if (name.trim().isBlank()) {
                                        nameError = "সদস্যের নাম দিন"
                                        hasError = true
                                    }
                                    val digits = phoneNumber.filter { it.isDigit() }
                                    if (digits.length < 6) {
                                        phoneError = "সঠিক মোবাইল নম্বর দিন (কমপক্ষে ৬ সংখ্যা)"
                                        hasError = true
                                    }
                                    if (address.trim().isBlank()) {
                                        addressError = "সদস্যের ঠিকানা দিন"
                                        hasError = true
                                    }
                                    if (hasError) return@Button

                                    val newMember = Member(
                                        memberSerial = serial.trim(),
                                        name = name.trim(),
                                        mobile = phoneNumber.trim(),
                                        address = address.trim(),
                                        area = area.trim().ifBlank { "যাদবপুর" },
                                        joinDate = joinDate.trim().ifBlank { BengaliUtils.getCurrentDateStr() },
                                        weeklySavingsAmount = weeklySavings.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 100.0,
                                        isActive = isActive
                                    )
                                    onRegisterMember(
                                        newMember,
                                        { registered ->
                                            savedMemberDialog = registered
                                        },
                                        { errMsg ->
                                            formErrorBanner = errMsg
                                        }
                                    )
                                },
                                enabled = !isSaving,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldGreenPrimary,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("reg_submit_button")
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Firestore-এ সংরক্ষণ হচ্ছে...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("সদস্য নিবন্ধন করুন (Firestore)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    savedMemberDialog?.let { registered ->
        AlertDialog(
            onDismissRequest = { savedMemberDialog = null },
            modifier = Modifier.testTag("reg_success_dialog"),
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "সফল",
                    tint = EmeraldGreenPrimary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "সদস্য নিবন্ধন সফল ✓",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "${registered.name} (ক্রমিক: ${BengaliUtils.toBengaliDigits(registered.memberSerial)}) সফলভাবে নিবন্ধিত হয়েছেন।",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "মোবাইল: ${registered.mobile}\nঠিকানা: ${registered.address}\nএলাকা: ${registered.area}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        savedMemberDialog = null
                        onNavigateToMembers()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreenPrimary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("reg_go_to_members_button")
                ) {
                    Text("সদস্য তালিকায় দেখুন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        savedMemberDialog = null
                        name = ""
                        phoneNumber = ""
                        address = ""
                        nameError = null
                        phoneError = null
                        addressError = null
                        serialError = null
                        formErrorBanner = null
                        userEditedSerial = false
                        val nextNum = (registered.memberSerial.filter { it.isDigit() }.toIntOrNull() ?: 0) + 1
                        serial = nextNum.toString()
                    },
                    modifier = Modifier.testTag("reg_add_another_button")
                ) {
                    Text("আরেকজন যোগ করুন", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
