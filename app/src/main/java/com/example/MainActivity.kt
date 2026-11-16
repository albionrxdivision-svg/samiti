package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.repository.EkotaRepository
import com.example.notifications.NotificationHelper
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.navigation.MainAppNavigation
import com.example.ui.theme.EkotaSamitiTheme
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    private val openApprovalsDeepLinkState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        NotificationHelper.ensureNotificationChannel(this)
        handleDeepLinkIntent(intent)

        setContent {
            EkotaSamitiTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val openApprovalsFromDeepLink by openApprovalsDeepLinkState
                    AppRootContent(
                        openApprovalsFromDeepLink = openApprovalsFromDeepLink,
                        onDeepLinkConsumed = { openApprovalsDeepLinkState.value = false }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        if (NotificationHelper.shouldOpenApprovals(intent)) {
            openApprovalsDeepLinkState.value = true
            NotificationHelper.clearDeepLinkExtras(intent)
        }
    }
}

@Composable
fun AppRootContent(
    openApprovalsFromDeepLink: Boolean = false,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var sessionUser by remember { mutableStateOf(com.example.data.model.AuthSession.init(context)) }
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val activeUid = currentUser?.uid ?: sessionUser?.userId

    if (activeUid == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
                sessionUser = com.example.data.model.AuthSession.currentUserProfile
            }
        )
    } else {
        key(activeUid) {
            val repository = remember(activeUid) { EkotaRepository(context) }
            val viewModel: MainViewModel = viewModel(
                key = activeUid,
                factory = MainViewModelFactory(repository, activeUid)
            )

            MainAppNavigation(
                viewModel = viewModel,
                openApprovalsFromDeepLink = openApprovalsFromDeepLink,
                onDeepLinkConsumed = onDeepLinkConsumed,
                onSignOut = {
                    NotificationHelper.syncAdminFcmRegistration(context, isAdmin = false) {}
                    com.example.data.model.AuthSession.clear(context)
                    sessionUser = null
                    signOut(
                        credentialManager = credentialManager,
                        scope = coroutineScope,
                        onSignOutComplete = {
                            currentUser = null
                        }
                    )
                }
            )
        }
    }
}
