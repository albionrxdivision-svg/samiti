package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.data.local.LocalBackupFileInfo
import com.example.data.local.StorageQuotaManager
import com.example.data.local.StorageUsageStats
import com.example.data.repository.FirestoreClientProvider
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StorageSettingsDialog(
    canEditQuota: Boolean,
    backupJsonProvider: () -> String,
    onLimitUpdated: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(StorageQuotaManager.getStorageUsageStats(context)) }
    var archives by remember { mutableStateOf(StorageQuotaManager.listBackupArchives(context)) }
    var statusNotice by remember { mutableStateOf<String?>(null) }

    fun refreshStorageState() {
        stats = StorageQuotaManager.getStorageUsageStats(context)
        archives = StorageQuotaManager.listBackupArchives(context)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .testTag("storage_settings_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(HeaderSlateNavy, HeaderSlateDeep)
                            )
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp)
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
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = "ডাটা স্টোরেজ",
                                    tint = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = "ডাটা স্টোরেজ ও ক্যাশ (১০০ GB)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "অফলাইন ডাটাবেস, ক্যাশ ও লোকাল ব্যাকআপ সীমা",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_storage_settings_button")
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Active storage telemetry card
                    StorageOverviewCard(stats = stats)

                    statusNotice?.let { msg ->
                        Surface(
                            color = ekotaBadgeSuccessBg(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = ekotaSuccessTextColor(),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Quota Tiers
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "স্টোরেজ সীমা নির্বাচন করুন (সর্বোচ্চ ১০০ GB)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StorageQuotaManager.availableTiers.forEach { tier ->
                            val isSelected = stats.configuredLimitBytes == tier.limitBytes
                            val is100Gb = tier.limitBytes == StorageQuotaManager.STORAGE_100_GB
                            val tag = if (is100Gb) "storage_option_100gb" else "storage_tier_${tier.limitBytes}"

                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    HeaderSlateNavy.copy(alpha = 0.08f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.6.dp else 1.dp,
                                    color = if (isSelected) {
                                        if (is100Gb) EmeraldGreenPrimary else HeaderSlateNavy
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = canEditQuota) {
                                        val appliedNow = FirestoreClientProvider.updateStorageLimit(
                                            context = context,
                                            requestedBytes = tier.limitBytes
                                        )
                                        refreshStorageState()
                                        onLimitUpdated(tier.limitBytes)
                                        statusNotice = if (appliedNow) {
                                            "${tier.labelBn} ডাটা স্টোরেজ সীমা সক্রিয় করা হয়েছে।"
                                        } else {
                                            "${tier.labelBn} স্টোরেজ সীমা সংরক্ষিত হয়েছে! লোকাল ব্যাকআপ এখনই ${tier.labelBn} পর্যন্ত সংরক্ষণ হবে এবং ক্লাউড ক্যাশ পরবর্তী অ্যাপ চালুর সময় কার্যকর হবে।"
                                        }
                                    }
                                    .testTag(tag)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = null,
                                        enabled = canEditQuota
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = tier.labelBn,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (is100Gb) ekotaBadgeSuccessBg() else MaterialTheme.colorScheme.surface
                                            ) {
                                                Text(
                                                    text = tier.badgeBn,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (is100Gb) ekotaSuccessTextColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = tier.subtitleBn,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "নির্বাচিত",
                                            tint = if (is100Gb) EmeraldGreenPrimary else HeaderSlateNavy
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                    // Local 100 GB Archive Vault Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "ফোনের অভ্যন্তরীণ ব্যাকআপ ভল্ট (${BengaliUtils.toBengaliDigits(archives.size)}টি ফাইল)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "সমিতির সম্পূর্ণ হিসাব (সদস্য, সঞ্চয়, ঋণ ও লেজার) সরাসরি অ্যাপের নিরাপদ লোকাল স্টোরেজে সংরক্ষণ করুন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                val json = backupJsonProvider()
                                StorageQuotaManager.saveBackupToLocalArchive(context, json)
                                    .onSuccess { saved ->
                                        refreshStorageState()
                                        statusNotice = "ব্যাকআপ সংরক্ষিত: ${saved.fileName} (${StorageQuotaManager.formatBytesBn(saved.sizeBytes)})"
                                        Toast.makeText(context, "ফোনের স্টোরেজে ব্যাকআপ সেভ হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                    .onFailure { err ->
                                        Toast.makeText(context, err.message ?: "ব্যাকআপ সংরক্ষণ করা যায়নি", Toast.LENGTH_LONG).show()
                                    }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("save_local_backup_archive_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGreenPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "এখনই লোকাল ব্যাকআপ সংরক্ষণ করুন",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (archives.isNotEmpty()) {
                            archives.take(5).forEach { fileInfo ->
                                BackupArchiveItemRow(
                                    fileInfo = fileInfo,
                                    onCopy = {
                                        val content = StorageQuotaManager.readBackupArchiveContent(context, fileInfo.fileName)
                                        if (content != null) {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText(fileInfo.fileName, content))
                                            Toast.makeText(context, "ফাইলের তথ্য কপি হয়েছে", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onDelete = {
                                        if (StorageQuotaManager.deleteBackupArchive(context, fileInfo.fileName)) {
                                            refreshStorageState()
                                            Toast.makeText(context, "ব্যাকআপ ফাইল মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
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
                            .testTag("storage_settings_done_button")
                    ) {
                        Text("সম্পন্ন", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(stats: StorageUsageStats) {
    val progress = if (stats.configuredLimitBytes > 0L) {
        (stats.appUsedBytes.toFloat() / stats.configuredLimitBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
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
                    text = "নির্ধারিত সীমা: ${StorageQuotaManager.formatBytesBn(stats.configuredLimitBytes)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ekotaBadgeSuccessBg()
                ) {
                    Text(
                        text = "সক্রিয়",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ekotaSuccessTextColor(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { progress.coerceAtLeast(0.01f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = EmeraldGreenPrimary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "অ্যাপ ডাটা: ${StorageQuotaManager.formatBytesBn(stats.appUsedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "অফলাইন DB: ${StorageQuotaManager.formatBytesBn(stats.roomDbBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (stats.deviceTotalBytes > 0L) {
                Text(
                    text = "ফোনের খালি জায়গা: ${StorageQuotaManager.formatBytesBn(stats.deviceAvailableBytes)} / মোট ${StorageQuotaManager.formatBytesBn(stats.deviceTotalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun BackupArchiveItemRow(
    fileInfo: LocalBackupFileInfo,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val dateLabel = remember(fileInfo.lastModifiedMillis) {
        SimpleDateFormat("dd/MM/yy hh:mm a", Locale.US).format(Date(fileInfo.lastModifiedMillis))
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = NavyBlueSecondary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileInfo.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$dateLabel • ${StorageQuotaManager.formatBytesBn(fileInfo.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "কপি করুন",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "মুছে ফেলুন",
                    tint = ekotaDangerTextColor()
                )
            }
        }
    }
}
