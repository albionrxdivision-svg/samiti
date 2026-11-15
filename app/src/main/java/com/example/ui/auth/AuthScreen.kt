package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.ui.theme.EmeraldGreenDark
import com.example.ui.theme.EmeraldGreenLight
import com.example.ui.theme.EmeraldGreenPrimary
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        EmeraldGreenPrimary,
                        EmeraldGreenDark,
                        Color(0xFF072418)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .padding(vertical = 20.dp, horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cooperative Emblem
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(EmeraldGreenLight.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ekota_samiti_icon_1794375763534),
                        contentDescription = "একতা সমিতি লোগো",
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "যাদবপুর যুব সমাজ সমিতি",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "একতা সমিতি - সাপ্তাহিক সঞ্চয় ও ঋণ ব্যবস্থাপনা",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                val passwordManager = remember { com.example.util.PasswordManager.forContext(context) }
                val adminUsesDefault = remember { !passwordManager.isAdminPasswordCustomized() }
                val defaultOfficerName = "মাঠ কর্মকর্তা ১"
                val defaultOfficerHasCustom = remember { passwordManager.hasCustomOfficerPassword(defaultOfficerName) }

                var username by remember { mutableStateOf("Nitai") }
                var password by remember { mutableStateOf(if (adminUsesDefault) "Nemai" else "") }
                var passwordVisible by remember { mutableStateOf(false) }
                var selectedTabIndex by remember { mutableStateOf(0) } // 0: Admin, 1: Field Officer

                // Tabs for Admin vs Field Officer Login
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = {
                            selectedTabIndex = 0
                            username = "Nitai"
                            password = if (!passwordManager.isAdminPasswordCustomized()) "Nemai" else ""
                            errorMessage = null
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                "অ্যাডমিন লগইন",
                                fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = {
                            selectedTabIndex = 1
                            username = defaultOfficerName
                            val hasCustom = passwordManager.hasCustomOfficerPassword(defaultOfficerName)
                            val defaultOffPw = passwordManager.getDefaultOfficerPassword()
                            password = if (!hasCustom && defaultOffPw == com.example.util.PasswordManager.DEFAULT_OFFICER_PASSWORD) {
                                defaultOffPw
                            } else {
                                ""
                            }
                            errorMessage = null
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                "মাঠ কর্মকর্তা",
                                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Role Information & Quick Preset Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "নিরাপত্তা",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val infoText = if (selectedTabIndex == 0) {
                                if (adminUsesDefault) {
                                    "অ্যাডমিন লগইন: Nitai | পাসওয়ার্ড: Nemai"
                                } else {
                                    "অ্যাডমিন আইডি: Nitai (আপনার পরিবর্তিত পাসওয়ার্ড দিন)"
                                }
                            } else {
                                val currentOfficerCustom = passwordManager.hasCustomOfficerPassword(username)
                                val defaultOffPw = passwordManager.getDefaultOfficerPassword()
                                if (!currentOfficerCustom && !defaultOfficerHasCustom && defaultOffPw == com.example.util.PasswordManager.DEFAULT_OFFICER_PASSWORD) {
                                    "মাঠ কর্মকর্তা ডিফল্ট পাসওয়ার্ড: 123 (সেটিংস থেকে পরিবর্তনযোগ্য)"
                                } else {
                                    "মাঠ কর্মকর্তা আইডি ও আপনার নির্ধারিত পাসওয়ার্ড দিন"
                                }
                            }
                            Text(
                                text = infoText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Username TextField
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = {
                        Text(if (selectedTabIndex == 0) "অ্যাডমিন আইডি / ইউজারনেম" else "মাঠ কর্মকর্তা আইডি / নাম")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("username_input"),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Password TextField
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("পাসওয়ার্ড (Password)") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible)
                        androidx.compose.ui.text.input.VisualTransformation.None
                    else
                        androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(
                                text = if (passwordVisible) "লুকান" else "দেখান",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Login Button
                Button(
                    onClick = {
                        val u = username.trim()
                        val p = password.trim()
                        if (u.isBlank() || p.isBlank()) {
                            errorMessage = "ইউজারনেম এবং পাসওয়ার্ড পূরণ করুন"
                            return@Button
                        }

                        if (selectedTabIndex == 0) {
                            // Admin verification via PasswordManager
                            if (passwordManager.verifyAdminLogin(u, p)) {
                                com.example.data.model.AuthSession.setAdminSession(context)
                                onAuthSuccess()
                            } else {
                                errorMessage = if (!passwordManager.isAdminPasswordCustomized()) {
                                    "ভুল তথ্য! অ্যাডমিন লগইন: Nitai এবং পাসওয়ার্ড: Nemai"
                                } else {
                                    "ভুল অ্যাডমিন আইডি বা পাসওয়ার্ড!"
                                }
                            }
                        } else {
                            // Field Officer verification via PasswordManager
                            if (u.equals("Nitai", ignoreCase = true)) {
                                errorMessage = "এটি অ্যাডমিনের আইডি। অ্যাডমিন ট্যাব ব্যবহার করুন অথবা মাঠ কর্মকর্তার নাম দিন"
                            } else if (passwordManager.verifyOfficerLogin(u, p)) {
                                com.example.data.model.AuthSession.setOfficerSession(u, context)
                                onAuthSuccess()
                            } else {
                                errorMessage = "ভুল পাসওয়ার্ড! আপনার সঠিক পাসওয়ার্ড দিন"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("login_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldGreenPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (selectedTabIndex == 0) "অ্যাডমিন হিসেবে প্রবেশ করুন" else "মাঠ কর্মকর্তা হিসেবে প্রবেশ করুন",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "  ক্লাউড ও অফলাইন সিঙ্ক  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(10.dp))

                GoogleSignInButton(
                    label = if (selectedTabIndex == 0) {
                        "Google দিয়ে অ্যাডমিন সিঙ্ক করুন"
                    } else {
                        "Google দিয়ে মাঠ কর্মকর্তা সিঙ্ক করুন"
                    },
                    onAuthSuccess = {
                        errorMessage = null
                        onAuthSuccess()
                    },
                    onAuthError = { msg -> errorMessage = msg }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Google লগইন করলে Firestore অফলাইন ক্যাশ চালু থাকবে — নেট ছাড়াও এন্ট্রি করা যাবে এবং নেট এলে অটো সিঙ্ক হবে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun GoogleSignInButton(
    label: String = "Google দিয়ে সাইন-ইন করুন",
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = {
            isLoading = true
            onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    isLoading = false
                    onAuthSuccess()
                },
                onAuthError = { errorMsg ->
                    isLoading = false
                    onAuthError(errorMsg)
                },
                scope = coroutineScope,
                onAuthCancelled = { isLoading = false }
            )
        },
        enabled = !isLoading,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, EmeraldGreenPrimary.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = EmeraldGreenPrimary
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("google_sign_in_button")
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = EmeraldGreenPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("সংযুক্ত হচ্ছে...")
        } else {
            Icon(
                imageVector = Icons.Default.CloudSync,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In কনফিগারেশন পাওয়া যায়নি")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    scope.launch {
        try {
            val activity = context as? Activity
                ?: throw IllegalStateException("Google Sign-In এর জন্য Activity প্রয়োজন")
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onAuthSuccess()
            } else {
                onAuthError("অপ্রত্যাশিত ক্রেডেনশিয়াল টাইপ")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Google সাইন-ইন ব্যর্থ হয়েছে")
        }
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
