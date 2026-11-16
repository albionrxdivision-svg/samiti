package com.example.ui.officers

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
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.MainViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficersManagementScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val allUsers by viewModel.allUsers.collectAsState()
    var userToEdit by remember { mutableStateOf<UserProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "মাঠ কর্মকর্তা ও ব্যবহারকারী",
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
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "অ্যাডমিন এখান থেকে মাঠ কর্মকর্তাদের দায়িত্বপ্রাপ্ত এলাকা ও রোল নির্ধারণ করতে পারেন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(allUsers) { index, user ->
                    val isAdmin = user.role == UserRole.ADMIN.name
                    val accent = if (isAdmin) VibrantCardAccents[0] else VibrantCardAccents[(index + 1) % VibrantCardAccents.size]
                    val isDark = LocalIsDarkEkotaTheme.current
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) MaterialTheme.colorScheme.surface else accent.softSurface.copy(alpha = 0.85f)
                        ),
                        border = BorderStroke(1.dp, accent.gradient.first().copy(alpha = 0.4f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(accent.gradient)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.displayName.ifBlank { "ব্যবহারকারী" },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isDark) MaterialTheme.colorScheme.onSurface else accent.accentInk
                                )
                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "এলাকা: ${user.assignedArea} | পদবী: ${if (isAdmin) "অ্যাডমিন" else "মাঠ কর্মকর্তা"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) accent.gradient.last() else accent.accentInk,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            IconButton(onClick = { userToEdit = user }) {
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
        }
    }

    userToEdit?.let { user ->
        EditUserRoleDialog(
            user = user,
            onDismiss = { userToEdit = null },
            onSave = { newRole, newArea, isActive ->
                viewModel.updateUserRole(
                    userId = user.userId,
                    role = newRole,
                    area = newArea,
                    isActive = isActive,
                    onSuccess = { userToEdit = null },
                    onError = {}
                )
            }
        )
    }
}

@Composable
fun EditUserRoleDialog(
    user: UserProfile,
    onDismiss: () -> Unit,
    onSave: (role: String, area: String, isActive: Boolean) -> Unit
) {
    var selectedRole by remember { mutableStateOf(user.role) }
    var assignedArea by remember { mutableStateOf(user.assignedArea) }
    var isActive by remember { mutableStateOf(user.isActive) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("কর্মকর্তার দায়িত্ব ও রোল সম্পাদনা", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "নাম: ${user.displayName}", fontWeight = FontWeight.SemiBold)
                Text(text = "ইমেইল: ${user.email}", style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()

                Text("রোল নির্বাচন:", fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = selectedRole == UserRole.ADMIN.name,
                        onClick = { selectedRole = UserRole.ADMIN.name },
                        label = { Text("অ্যাডমিন", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = selectedRole == UserRole.FIELD_OFFICER.name,
                        onClick = { selectedRole = UserRole.FIELD_OFFICER.name },
                        label = { Text("মাঠ কর্মকর্তা", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = assignedArea,
                    onValueChange = { assignedArea = it },
                    label = { Text("দায়িত্বপ্রাপ্ত এলাকা (যেমন: যাদবপুর উত্তর)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isActive, onCheckedChange = { isActive = it })
                    Text("অ্যাকাউন্ট সক্রিয় রাখুন (Active)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedRole, assignedArea, isActive) },
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
