package com.example.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.cloud.DriveAuthOutcome
import com.example.data.cloud.DriveBackupFile
import com.example.data.cloud.GoogleDriveBackupManager
import com.example.data.cloud.GoogleDriveConnectionState
import com.example.data.local.StorageQuotaManager
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import kotlinx.coroutines.launch

private enum class PendingDriveAction {
    CONNECT_ONLY,
    UPLOAD_BACKUP,
    LIST_BACKUPS,
    DOWNLOAD_RESTORE
}

@Composable
fun GoogleDriveStorageDialog(
    canRestore: Boolean,
    backupJsonProvider: () -> String,
    onRestoreBackupJson: ((String, (Int) -> Unit, (String) -> Unit) -> Unit)? = null,
    onStateUpdated: (GoogleDriveConnectionState) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findOwnerActivity() }
    val coroutineScope = rememberCoroutineScope()

    var driveState by remember { mutableStateOf(GoogleDriveBackupManager.loadConnectionState(context)) }
    var driveFiles by remember { mutableStateOf<List<DriveBackupFile>>(emptyList()) }
    var isBusy by remember { mutableStateOf(false) }
    var statusNotice by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // isError to message
    var pendingAction by remember { mutableStateOf(PendingDriveAction.CONNECT_ONLY) }
    var pendingDownloadFileId by remember { mutableStateOf<String?>(null) }
    var activeAccessToken by remember { mutableStateOf<String?>(null) }

    fun updateDriveState(newState: GoogleDriveConnectionState) {
        driveState = newState
        onStateUpdated(newState)
    }

    fun executeDriveAction(accessToken: String, action: PendingDriveAction, downloadId: String?) {
        activeAccessToken = accessToken
        coroutineScope.launch {
            isBusy = true
            when (action) {
                PendingDriveAction.CONNECT_ONLY -> {
                    val synced = GoogleDriveBackupManager.syncConnectedAccountInfo(context, accessToken)
                    GoogleDriveBackupManager.ensureBackupFolder(context, accessToken)
                    synced.onSuccess { updated ->
                        updateDriveState(updated)
                        val emailLabel = updated.accountEmail.ifBlank { "Google অ্যাকাউন্ট" }
                        statusNotice = false to "Google Drive ($emailLabel) সফলভাবে যুক্ত হয়েছে ✓"
                    }.onFailure { err ->
                        statusNotice = true to (err.message ?: "Google Drive তথ্য আনা যায়নি")
                    }
                    GoogleDriveBackupManager.listBackups(context, accessToken).onSuccess {
                        driveFiles = it
                    }
                }

                PendingDriveAction.UPLOAD_BACKUP -> {
                    val json = backupJsonProvider()
                    GoogleDriveBackupManager.uploadBackupJson(context, accessToken, json)
                        .onSuccess { uploaded ->
                            updateDriveState(GoogleDriveBackupManager.loadConnectionState(context))
                            statusNotice = false to "Google Drive-এ '${uploaded.name}' সফলভাবে সেভ হয়েছে ✓"
                            GoogleDriveBackupManager.listBackups(context, accessToken).onSuccess {
                                driveFiles = it
                            }
                        }
                        .onFailure { err ->
                            statusNotice = true to (err.message ?: "Google Drive আপলোড ব্যর্থ হয়েছে")
                        }
                }

                PendingDriveAction.LIST_BACKUPS -> {
                    GoogleDriveBackupManager.syncConnectedAccountInfo(context, accessToken).onSuccess {
                        updateDriveState(it)
                    }
                    GoogleDriveBackupManager.listBackups(context, accessToken)
                        .onSuccess { list ->
                            driveFiles = list
                            statusNotice = if (list.isEmpty()) {
                                false to "Google Drive-এর '${GoogleDriveBackupManager.BACKUP_FOLDER_NAME}' ফোল্ডারে এখনো কোনো ব্যাকআপ নেই"
                            } else {
                                false to "Google Drive থেকে ${BengaliUtils.toBengaliDigits(list.size)}টি ব্যাকআপ পাওয়া গেছে"
                            }
                        }
                        .onFailure { err ->
                            statusNotice = true to (err.message ?: "ব্যাকআপ তালিকা আনা যায়নি")
                        }
                }

                PendingDriveAction.DOWNLOAD_RESTORE -> {
                    val targetId = downloadId ?: pendingDownloadFileId
                    if (targetId.isNullOrBlank() || onRestoreBackupJson == null) {
                        isBusy = false
                        return@launch
                    }
                    GoogleDriveBackupManager.downloadBackupContent(accessToken, targetId)
                        .onSuccess { rawJson ->
                            onRestoreBackupJson(
                                rawJson,
                                { count ->
                                    statusNotice = false to "Google Drive থেকে ${BengaliUtils.toBengaliDigits(count)}টি রেকর্ড রিস্টোর হয়েছে ✓"
                                    Toast.makeText(context, "Google Drive ব্যাকআপ রিস্টোর সম্পন্ন", Toast.LENGTH_LONG).show()
                                },
                                { err ->
                                    statusNotice = true to err
                                }
                            )
                        }
                        .onFailure { err ->
                            statusNotice = true to (err.message ?: "ডাউনলোড ব্যর্থ হয়েছে")
                        }
                }
            }
            isBusy = false
        }
    }

    val consentResolutionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        val act = activity ?: return@rememberLauncherForActivityResult
        when (val outcome = GoogleDriveBackupManager.extractAuthorizationFromIntent(act, res.data)) {
            is DriveAuthOutcome.Authorized -> {
                executeDriveAction(outcome.accessToken, pendingAction, pendingDownloadFileId)
            }
            is DriveAuthOutcome.Failed -> {
                isBusy = false
                statusNotice = true to outcome.message
            }
            is DriveAuthOutcome.NeedsResolution -> {
                isBusy = false
            }
        }
    }

    fun launchAuthorizedDriveAction(action: PendingDriveAction, fileId: String? = null) {
        pendingAction = action
        pendingDownloadFileId = fileId
        val cachedToken = activeAccessToken
        if (!cachedToken.isNullOrBlank()) {
            executeDriveAction(cachedToken, action, fileId)
            return
        }
        val act = activity
        if (act == null) {
            statusNotice = true to "Google Drive সংযোগ করার জন্য সক্রিয় স্ক্রিন পাওয়া যায়নি"
            return
        }
        coroutineScope.launch {
            isBusy = true
            when (val outcome = GoogleDriveBackupManager.requestDriveAuthorization(act)) {
                is DriveAuthOutcome.Authorized -> {
                    executeDriveAction(outcome.accessToken, action, fileId)
                }
                is DriveAuthOutcome.NeedsResolution -> {
                    isBusy = false
                    consentResolutionLauncher.launch(
                        IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build()
                    )
                }
                is DriveAuthOutcome.Failed -> {
                    isBusy = false
                    statusNotice = true to outcome.message
                }
            }
        }
    }

    // Native Android Document Picker (works directly with Google Drive provider in Files app)
    val safExportToDriveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = backupJsonProvider()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("ফাইল লেখা যায়নি")
            }.onSuccess {
                statusNotice = false to "নির্বাচিত Drive/ফোল্ডারে ব্যাকআপ সংরক্ষিত হয়েছে ✓"
                Toast.makeText(context, "ব্যাকআপ সফলভাবে সেভ হয়েছে ✓", Toast.LENGTH_LONG).show()
            }.onFailure { err ->
                statusNotice = true to (err.localizedMessage ?: "ফাইল সেভ করা যায়নি")
            }
        }
    }

    val safRestoreFromDriveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && onRestoreBackupJson != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: error("ফাইল পড়া যায়নি")
            }.onSuccess { raw ->
                onRestoreBackupJson(
                    raw,
                    { count ->
                        statusNotice = false to "নির্বাচিত ফাইল থেকে ${BengaliUtils.toBengaliDigits(count)}টি রেকর্ড রিস্টোর হয়েছে ✓"
                        Toast.makeText(context, "রিস্টোর সম্পন্ন ✓", Toast.LENGTH_LONG).show()
                    },
                    { err ->
                        statusNotice = true to err
                    }
                )
            }.onFailure { err ->
                statusNotice = true to (err.localizedMessage ?: "ফাইল পড়া যায়নি")
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 700.dp)
                .testTag("google_drive_storage_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(HeaderSlateNavy, HeaderSlateDeep)))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddToDrive,
                                    contentDescription = "Google Drive ডাটা স্টোরেজ",
                                    tint = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = "Google Drive ডাটা স্টোরেজ",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "সকল হিসাব Google Drive-এ ব্যাকআপ ও রিস্টোর করুন",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_google_drive_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "বন্ধ করুন",
                                tint = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Connection Status Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (driveState.isConnected) "Google Drive সংযুক্ত" else "Google Drive যুক্ত নেই",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (driveState.isConnected) ekotaBadgeSuccessBg() else MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = if (driveState.isConnected) "সক্রিয় ✓" else "সংযুক্ত নয়",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (driveState.isConnected) ekotaSuccessTextColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            if (driveState.accountEmail.isNotBlank()) {
                                Text(
                                    text = "অ্যাকাউন্ট: ${driveState.accountName.ifBlank { driveState.accountEmail }} (${driveState.accountEmail})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = "ফোল্ডার: ${GoogleDriveBackupManager.BACKUP_FOLDER_NAME} • শেষ ব্যাকআপ: ${GoogleDriveBackupManager.formatTimestamp(driveState.lastBackupTimeMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (driveState.lastBackupFileName.isNotBlank()) {
                                Text(
                                    text = "শেষ ফাইল: ${driveState.lastBackupFileName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Google Drive অটো-ব্যাকআপ সক্রিয়",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "ডাটা সুরক্ষিত রাখতে Drive সিঙ্ক অগ্রাধিকার দিন",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = driveState.autoBackupEnabled,
                                    onCheckedChange = { checked ->
                                        updateDriveState(
                                            GoogleDriveBackupManager.setAutoBackupEnabled(context, checked)
                                        )
                                    },
                                    modifier = Modifier.testTag("drive_auto_backup_switch")
                                )
                            }
                        }
                    }

                    statusNotice?.let { (isErr, msg) ->
                        Surface(
                            color = if (isErr) MaterialTheme.colorScheme.errorContainer else ekotaBadgeSuccessBg(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isErr) MaterialTheme.colorScheme.onErrorContainer else ekotaSuccessTextColor(),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (isBusy) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Drive-এর সাথে সংযোগ হচ্ছে...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Primary Connect / Disconnect & Upload Buttons
                    if (!driveState.isConnected) {
                        Button(
                            onClick = { launchAuthorizedDriveAction(PendingDriveAction.CONNECT_ONLY) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CardBlueGradient.first(),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("connect_google_drive_button")
                        ) {
                            Icon(Icons.Default.AddToDrive, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Drive অ্যাকাউন্ট যোগ করুন", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { launchAuthorizedDriveAction(PendingDriveAction.UPLOAD_BACKUP) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGreenPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("upload_to_google_drive_button")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("এখনই Google Drive-এ সেভ করুন", fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { launchAuthorizedDriveAction(PendingDriveAction.LIST_BACKUPS) },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("refresh_google_drive_list_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("ব্যাকআপ তালিকা", maxLines = 1, softWrap = false)
                            }
                            OutlinedButton(
                                onClick = {
                                    activeAccessToken = null
                                    driveFiles = emptyList()
                                    updateDriveState(GoogleDriveBackupManager.disconnect(context))
                                    statusNotice = false to "Google Drive সংযোগ বিচ্ছিন্ন করা হয়েছে"
                                },
                                enabled = !isBusy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ekotaDangerTextColor()),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("disconnect_google_drive_button")
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("বিচ্ছিন্ন করুন", maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    // Backup directly even without prior connect step
                    if (!driveState.isConnected) {
                        OutlinedButton(
                            onClick = { launchAuthorizedDriveAction(PendingDriveAction.UPLOAD_BACKUP) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("upload_to_google_drive_button")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Drive-এ সরাসরি ব্যাকআপ সেভ করুন", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Drive Backup Files List
                    if (driveFiles.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Text(
                            text = "Google Drive-এ সংরক্ষিত ব্যাকআপ (${BengaliUtils.toBengaliDigits(driveFiles.size)}টি)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        driveFiles.take(5).forEach { file ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        tint = CardBlueGradient.first()
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${StorageQuotaManager.formatBytesBn(file.sizeBytes)} • ${file.modifiedTime.take(16)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (canRestore && onRestoreBackupJson != null) {
                                        TextButton(
                                            onClick = {
                                                launchAuthorizedDriveAction(PendingDriveAction.DOWNLOAD_RESTORE, file.id)
                                            },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("restore_drive_file_${file.id}")
                                        ) {
                                            Text("রিস্টোর", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    // System Document Provider / Drive Picker Fallback
                    Text(
                        text = "অথবা ফোনের Drive / ফাইল পিকার দিয়ে সংরক্ষণ",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "আপনি চাইলে Android-এর নিজস্ব Google Drive ফাইল পিকার ব্যবহার করেও ব্যাকআপ ফাইল সেভ বা রিস্টোর করতে পারবেন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                safExportToDriveLauncher.launch(GoogleDriveBackupManager.defaultBackupFileName())
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("saf_save_to_drive_button")
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Drive-এ সেভ", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                        if (canRestore && onRestoreBackupJson != null) {
                            OutlinedButton(
                                onClick = {
                                    safRestoreFromDriveLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("saf_restore_from_drive_button")
                            ) {
                                Icon(Icons.Default.RestorePage, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Drive রিস্টোর", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                // Bottom Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("google_drive_dialog_done_button")
                    ) {
                        Text("সম্পন্ন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findOwnerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findOwnerActivity()
    else -> null
}
