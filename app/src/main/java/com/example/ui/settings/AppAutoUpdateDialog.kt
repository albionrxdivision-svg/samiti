package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.AppSettings
import com.example.ui.theme.EmeraldGreenPrimary
import com.example.ui.theme.NavyBlueSecondary
import com.example.ui.theme.ekotaNavyTextColor
import com.example.ui.theme.ekotaSuccessTextColor
import com.example.util.AppUpdateChecker
import com.example.util.AppUpdateInfo
import com.example.util.BengaliUtils

@Composable
internal fun ApkManagementDialog(
    settings: AppSettings,
    isAdmin: Boolean,
    availableUpdate: AppUpdateInfo? = null,
    isCheckingUpdate: Boolean = false,
    lastUpdateStatusMessage: String? = null,
    onToggleAutoUpdate: (Boolean) -> Unit = {},
    onCheckUpdate: () -> Unit = {},
    onDismiss: () -> Unit,
    onSaveSettings: (AppSettings, (Boolean, String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val resolvedBundledSlug = remember { AppUpdateChecker.extractGitHubRepoSlug(BuildConfig.GITHUB_REPO_SLUG) }

    var autoUpdateEnabled by remember(settings.autoUpdateEnabled) {
        mutableStateOf(settings.autoUpdateEnabled)
    }
    var keepPermanentLink by remember(settings.keepPermanentLink) {
        mutableStateOf(settings.keepPermanentLink)
    }
    var githubRepoSlugInput by remember(settings.githubRepoSlug, settings.appDownloadUrl) {
        val initialSlug = AppUpdateChecker.resolveRepoSlug(settings).orEmpty()
        mutableStateOf(initialSlug)
    }
    var appDownloadUrlInput by remember(settings.appDownloadUrl, settings.githubRepoSlug, settings.keepPermanentLink) {
        val initialUrl = AppUpdateChecker.resolvePermanentApkUrl(settings)
            .ifBlank { settings.appDownloadUrl.trim() }
        mutableStateOf(initialUrl)
    }
    var latestVersionCodeInput by remember(settings.latestVersionCode) {
        mutableStateOf(if (settings.latestVersionCode > 0) settings.latestVersionCode.toString() else "")
    }
    var latestVersionNameInput by remember(settings.latestVersionName) {
        mutableStateOf(settings.latestVersionName)
    }
    var isSaving by remember { mutableStateOf(false) }

    val effectivePermanentUrl = remember(
        settings,
        appDownloadUrlInput,
        githubRepoSlugInput,
        keepPermanentLink
    ) {
        val previewSettings = settings.copy(
            appDownloadUrl = appDownloadUrlInput.trim(),
            githubRepoSlug = githubRepoSlugInput.trim(),
            keepPermanentLink = keepPermanentLink
        )
        AppUpdateChecker.resolvePermanentApkUrl(previewSettings)
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
                .heightIn(max = 680.dp)
                .testTag("app_auto_update_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = EmeraldGreenPrimary
                    )
                    Text(
                        text = "মোবাইল অ্যাপ অটো-আপডেট",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                // Auto-Update Master Switch right at top for instant access
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (autoUpdateEnabled) {
                        EmeraldGreenPrimary.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (autoUpdateEnabled) EmeraldGreenPrimary.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = null,
                            tint = if (autoUpdateEnabled) EmeraldGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "স্বয়ংক্রিয় অটো-আপডেট যাচাই",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (autoUpdateEnabled) {
                                    "GitHub Push হলেই স্বয়ংক্রিয়ভাবে আপডেট খুঁজবে"
                                } else {
                                    "বন্ধ আছে — শুধু ম্যানুয়াল যাচাই কাজ করবে"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoUpdateEnabled,
                            onCheckedChange = { checked ->
                                autoUpdateEnabled = checked
                                onToggleAutoUpdate(checked)
                            },
                            modifier = Modifier.testTag("auto_update_switch")
                        )
                    }
                }

                // Version & GitHub push assurance card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "বর্তমান সংস্করণ: v${BuildConfig.VERSION_NAME} (কোড ${BengaliUtils.toBengaliDigits(BuildConfig.VERSION_CODE)})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!resolvedBundledSlug.isNullOrBlank()) {
                            Text(
                                text = "GitHub বিল্ড সংযুক্ত: $resolvedBundledSlug",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = ekotaSuccessTextColor()
                            )
                        }
                        Text(
                            text = "GitHub-এ Push করলেই নতুন আপডেট আসবে; লিংক কখনো পরিবর্তন হবে না এবং আনইন্সটল ছাড়াই আপডেট হবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Latest Update Status / Available Card
                if (availableUpdate != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EmeraldGreenPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, EmeraldGreenPrimary.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apk_dialog_update_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldGreenPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "নতুন আপডেট পাওয়া গেছে: v${availableUpdate.latestVersionName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ekotaSuccessTextColor()
                                )
                            }
                            if (availableUpdate.releaseNotes.isNotBlank()) {
                                Text(
                                    text = availableUpdate.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            val targetUpdateUrl = availableUpdate.permanentDownloadUrl
                                .ifBlank { availableUpdate.downloadUrl }
                            if (targetUpdateUrl.isNotBlank()) {
                                Button(
                                    onClick = { openDownloadUrl(context, targetUpdateUrl) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldGreenPrimary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("apk_dialog_update_now_button")
                                ) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("এখনই আপডেট ডাউনলোড করুন", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (!lastUpdateStatusMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = lastUpdateStatusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // Manual immediate check button
                OutlinedButton(
                    onClick = onCheckUpdate,
                    enabled = !isCheckingUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("check_app_update_button")
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCheckingUpdate) "GitHub থেকে আপডেট যাচাই হচ্ছে..." else "এখনই আপডেট যাচাই করুন",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Permanent unchanging link preview + actions
                if (effectivePermanentUrl.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openDownloadUrl(context, effectivePermanentUrl) }
                            .testTag("apk_link_preview_box")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = EmeraldGreenPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "স্থায়ী ও অপরিবর্তনীয় ডাউনলোড লিংক",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ekotaSuccessTextColor()
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = effectivePermanentUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ekotaNavyTextColor(),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Button(
                        onClick = { openDownloadUrl(context, effectivePermanentUrl) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("download_mobile_app_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreenPrimary, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("APK ডাউনলোড ও আপডেট করুন", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Ekota App APK Link", effectivePermanentUrl))
                                Toast.makeText(context, "স্থায়ী ডাউনলোড লিংক কপি হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("copy_apk_link_button")
                        ) {
                            Text("লিংক কপি", maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(
                            onClick = { shareAppDownloadLink(context, settings.orgName, effectivePermanentUrl) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("share_apk_link_button")
                        ) {
                            Text("শেয়ার করুন", maxLines = 1, softWrap = false)
                        }
                    }
                } else {
                    Text(
                        text = "নিচে আপনার GitHub রেপোজিটরি (যেমন: username/repo) বসালে একটি স্থায়ী অপরিবর্তনীয় APK লিংক তৈরি হবে — এরপর প্রতিবার GitHub Push করলেই অ্যাপ নিজে থেকেই আপডেট পাবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isAdmin) {
                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "স্থায়ী লিংক লক রাখুন (অপরিবর্তনীয়)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "সবসময় .../releases/latest/download/Ekota-Samiti.apk ব্যবহার করবে",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = keepPermanentLink,
                            onCheckedChange = { locked ->
                                keepPermanentLink = locked
                                if (locked) {
                                    val slug = AppUpdateChecker.extractGitHubRepoSlug(githubRepoSlugInput)
                                        ?: AppUpdateChecker.extractGitHubRepoSlug(appDownloadUrlInput)
                                        ?: resolvedBundledSlug
                                    if (!slug.isNullOrBlank()) {
                                        appDownloadUrlInput = AppUpdateChecker.buildPermanentApkUrl(slug)
                                    }
                                }
                            },
                            modifier = Modifier.testTag("permanent_link_switch")
                        )
                    }

                    OutlinedTextField(
                        value = githubRepoSlugInput,
                        onValueChange = { newValue ->
                            githubRepoSlugInput = newValue
                            if (keepPermanentLink) {
                                val slug = AppUpdateChecker.extractGitHubRepoSlug(newValue)
                                if (!slug.isNullOrBlank()) {
                                    appDownloadUrlInput = AppUpdateChecker.buildPermanentApkUrl(slug)
                                }
                            }
                        },
                        label = { Text("GitHub রেপোজিটরি (owner/repo)") },
                        placeholder = { Text("username/ekota-samiti") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apk_github_repo_input")
                    )

                    val candidateSlug = AppUpdateChecker.extractGitHubRepoSlug(githubRepoSlugInput)
                        ?: AppUpdateChecker.extractGitHubRepoSlug(appDownloadUrlInput)
                        ?: resolvedBundledSlug
                    if (!candidateSlug.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                githubRepoSlugInput = candidateSlug
                                keepPermanentLink = true
                                appDownloadUrlInput = AppUpdateChecker.buildPermanentApkUrl(candidateSlug)
                                Toast.makeText(
                                    context,
                                    "স্থায়ী GitHub লিংক বসানো হয়েছে — এবার সংরক্ষণ করুন",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("apply_permanent_github_link_button")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("স্থায়ী GitHub লিংক স্বয়ংক্রিয় বসাও")
                        }
                    }

                    OutlinedTextField(
                        value = appDownloadUrlInput,
                        onValueChange = { appDownloadUrlInput = it },
                        label = { Text("স্থায়ী APK ডাউনলোড লিংক (URL)") },
                        placeholder = { Text("https://github.com/user/repo/releases/latest/download/Ekota-Samiti.apk") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("apk_download_url_input")
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latestVersionCodeInput,
                            onValueChange = { latestVersionCodeInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("ভার্সন কোড") },
                            placeholder = { Text("${BuildConfig.VERSION_CODE + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = latestVersionNameInput,
                            onValueChange = { latestVersionNameInput = it },
                            label = { Text("ভার্সন নাম") },
                            placeholder = { Text("1.2") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = {
                            val cleanSlug = AppUpdateChecker.extractGitHubRepoSlug(githubRepoSlugInput)
                                ?: AppUpdateChecker.extractGitHubRepoSlug(appDownloadUrlInput)
                                ?: resolvedBundledSlug
                                ?: githubRepoSlugInput.trim()

                            val candidateUrl = if (keepPermanentLink && AppUpdateChecker.extractGitHubRepoSlug(cleanSlug) != null) {
                                AppUpdateChecker.buildPermanentApkUrl(cleanSlug)
                            } else {
                                appDownloadUrlInput
                            }
                            val normalized = normalizeUrl(candidateUrl)
                            if (candidateUrl.isNotBlank() && normalized == null) {
                                Toast.makeText(context, "সঠিক ওয়েব লিংক দিন (https:// দিয়ে শুরু)", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            appDownloadUrlInput = normalized ?: ""
                            githubRepoSlugInput = cleanSlug

                            isSaving = true
                            val updatedSettings = settings.copy(
                                appDownloadUrl = normalized ?: "",
                                githubRepoSlug = cleanSlug,
                                autoUpdateEnabled = autoUpdateEnabled,
                                keepPermanentLink = keepPermanentLink,
                                latestVersionCode = latestVersionCodeInput.toIntOrNull() ?: 0,
                                latestVersionName = latestVersionNameInput.trim()
                            )
                            onSaveSettings(updatedSettings) { ok, msg ->
                                isSaving = false
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_apk_url_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NavyBlueSecondary, contentColor = Color.White)
                    ) {
                        Text(if (isSaving) "সংরক্ষণ হচ্ছে..." else "অটো-আপডেট সেটিংস সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
                    }
                }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("বন্ধ করুন", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
