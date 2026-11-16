package com.example.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ai.AiCallTarget
import com.example.ui.AiCallUiState
import com.example.ui.theme.*
import com.example.util.BengaliUtils
import java.util.Locale

@Composable
fun AiAutoCallDialog(
    orgName: String,
    aiCallUiState: AiCallUiState,
    onGeneratePlan: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var instruction by remember {
        mutableStateOf("যাদের সাপ্তাহিক সঞ্চয়, ঋণের কিস্তি বা জরিমানা বকেয়া আছে তাদের অগ্রাধিকার কল তালিকা তৈরি করো")
    }
    var currentCallIndex by remember { mutableIntStateOf(0) }
    var isSpeaking by remember { mutableStateOf(false) }

    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val bnLocale = Locale.forLanguageTag("bn-BD")
                val res = instance?.setLanguage(bnLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    instance?.setLanguage(Locale.getDefault())
                }
                ttsReady = true
            }
        }
        instance
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                tts.stop()
                tts.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    fun speakScript(script: String) {
        if (script.isBlank()) return
        if (ttsReady) {
            isSpeaking = true
            tts.speak(script, TextToSpeech.QUEUE_FLUSH, null, "ekota_ai_call_${System.currentTimeMillis()}")
        } else {
            Toast.makeText(context, "ভয়েস সিস্টেম প্রস্তুত হচ্ছে...", Toast.LENGTH_SHORT).show()
        }
    }

    fun dialAndSpeakTarget(target: AiCallTarget) {
        if (target.callScriptBengali.isNotBlank()) {
            speakScript(target.callScriptBengali)
        }
        launchPhoneDialer(context, target.mobile, target.memberName)
    }

    val quickPresets = remember {
        listOf(
            "সব বকেয়া সদস্য" to "যাদের সাপ্তাহিক সঞ্চয়, ঋণের কিস্তি বা জরিমানা বকেয়া আছে তাদের অগ্রাধিকার কল তালিকা তৈরি করো",
            "ঋণের কিস্তি বকেয়া" to "শুধুমাত্র যাদের ঋণের সাপ্তাহিক কিস্তি মিস হয়েছে বা চলমান ঋণ বাকি আছে তাদের কল তালিকা তৈরি করো",
            "জরিমানা বাকি" to "যাদের অপরিশোধিত জরিমানা বা বকেয়া সঞ্চয় আছে তাদের বিনয়ী অনুস্মারক কল তালিকা তৈরি করো"
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .testTag("ai_auto_call_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ekotaSilverBackgroundBrush())
            ) {
                // Colorful Gradient Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(CardPurpleGradient))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = "AI কল অ্যাসিস্ট্যান্ট",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI অটো কল ও ভয়েস অ্যাসিস্ট্যান্ট",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$orgName • বকেয়া বিশ্লেষণ ও অটোমেটিক ডায়াল",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.90f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("ai_dialog_close_button")
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
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Prompt & Preset Filter Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, CardPurpleGradient.first().copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickPresets.forEach { (label, promptText) ->
                                    val selected = instruction.trim() == promptText.trim()
                                    FilterChip(
                                        selected = selected,
                                        onClick = { instruction = promptText },
                                        label = {
                                            Text(
                                                text = label,
                                                maxLines = 1,
                                                softWrap = false,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CardPurpleGradient.first(),
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = instruction,
                                onValueChange = { instruction = it },
                                label = { Text("AI কল নির্দেশনা") },
                                maxLines = 2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_instruction_input")
                            )

                            Button(
                                onClick = {
                                    currentCallIndex = 0
                                    onGeneratePlan(instruction)
                                },
                                enabled = aiCallUiState !is AiCallUiState.Loading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CardPurpleGradient.first(),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("ai_generate_call_plan_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (aiCallUiState is AiCallUiState.Loading) {
                                        "AI বিশ্লেষণ চলছে..."
                                    } else {
                                        "AI অটো কল তালিকা তৈরি করুন"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Dynamic AI State Display
                    when (aiCallUiState) {
                        AiCallUiState.Idle -> {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneInTalk,
                                        contentDescription = null,
                                        tint = CardTealGradient.first(),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text(
                                        text = "উপরের বাটনে ক্লিক করলে AI সকল সদস্যের সঞ্চয়, ঋণের কিস্তি ও জরিমানা যাচাই করে অটো কল তালিকা এবং বাংলা ভয়েস মেসেজ তৈরি করবে।",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        AiCallUiState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(color = CardPurpleGradient.first())
                                    Text(
                                        text = "AI বকেয়া হিসাব ও কল স্ক্রিপ্ট প্রস্তুত করছে...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        is AiCallUiState.Error -> {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = ekotaBadgeDangerBg()),
                                border = BorderStroke(1.dp, ekotaDangerTextColor().copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_call_error_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = ekotaDangerTextColor()
                                        )
                                        Text(
                                            text = "AI কল তালিকা তৈরি করা যায়নি",
                                            fontWeight = FontWeight.Bold,
                                            color = ekotaDangerTextColor(),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    Text(
                                        text = aiCallUiState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Button(
                                        onClick = { onGeneratePlan(instruction) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DangerRed,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("আবার চেষ্টা করুন", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        is AiCallUiState.Success -> {
                            val plan = aiCallUiState.result
                            val callableTargets = remember(plan.targets) {
                                plan.targets.filter { it.mobile.isNotBlank() }
                            }
                            val safeIndex = currentCallIndex.coerceIn(0, (callableTargets.size - 1).coerceAtLeast(0))

                            // Summary + Auto-Dial Queue Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.horizontalGradient(CardTealGradient))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = plan.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    if (callableTargets.isNotEmpty()) {
                                        val activeTarget = callableTargets[safeIndex]
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = {
                                                    dialAndSpeakTarget(activeTarget)
                                                    if (callableTargets.size > 1) {
                                                        currentCallIndex = (safeIndex + 1) % callableTargets.size
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.White,
                                                    contentColor = Color(0xFF0E7490)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp)
                                                    .testTag("ai_auto_dial_next_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Call,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "অটো কল (${BengaliUtils.toBengaliDigits(safeIndex + 1)}/${BengaliUtils.toBengaliDigits(callableTargets.size)}): ${activeTarget.memberName}",
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isSpeaking) {
                                                FilledIconButton(
                                                    onClick = {
                                                        tts.stop()
                                                        isSpeaking = false
                                                    },
                                                    colors = IconButtonDefaults.filledIconButtonColors(
                                                        containerColor = Color.White.copy(alpha = 0.25f),
                                                        contentColor = Color.White
                                                    ),
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Default.VolumeOff, contentDescription = "ভয়েস থামান")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (plan.targets.isEmpty()) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "বর্তমানে কল করার মতো কোনো বকেয়া সদস্য পাওয়া যায়নি।",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) {
                                    itemsIndexed(plan.targets, key = { idx, t -> "${t.memberId}_$idx" }) { idx, target ->
                                        val accent = VibrantCardAccents[idx % VibrantCardAccents.size]
                                        AiCallTargetCard(
                                            target = target,
                                            accent = accent,
                                            onSpeakClick = { speakScript(target.callScriptBengali) },
                                            onDialClick = { dialAndSpeakTarget(target) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiCallTargetCard(
    target: AiCallTarget,
    accent: CardColorAccent,
    onSpeakClick: () -> Unit,
    onDialClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accent.softSurface),
        border = BorderStroke(1.2.dp, accent.gradient.first().copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_call_target_card_${target.memberId}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(accent.gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = BengaliUtils.toBengaliDigits(target.priority.coerceAtLeast(1)),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (target.memberSerial.isNotBlank()) {
                            "${target.memberName} (ক্রমিক ${BengaliUtils.toBengaliDigits(target.memberSerial)})"
                        } else {
                            target.memberName
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (target.mobile.isNotBlank()) {
                            "মোবাইল: ${target.mobile} • ${target.reason}"
                        } else {
                            "মোবাইল নম্বর নেই • ${target.reason}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = accent.accentInk,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (target.dueSummary.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.85f),
                    border = BorderStroke(0.5.dp, accent.gradient.first().copy(alpha = 0.25f))
                ) {
                    Text(
                        text = target.dueSummary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent.accentInk,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (target.callScriptBengali.isNotBlank()) {
                Text(
                    text = "ভয়েস স্ক্রিপ্ট: \"${target.callScriptBengali}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1F2937)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSpeakClick,
                    border = BorderStroke(1.dp, accent.gradient.first()),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent.accentInk),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ভয়েস শুনুন", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = onDialClick,
                    enabled = target.mobile.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent.gradient.first(),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("কল ও ভয়েস", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

private fun launchPhoneDialer(context: Context, mobile: String, memberName: String) {
    val cleanPhone = mobile.trim().filter { it.isDigit() || it == '+' }
    if (cleanPhone.isBlank()) {
        Toast.makeText(context, "$memberName-এর মোবাইল নম্বর দেওয়া নেই", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "ফোন ডায়ালার পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
    }
}
