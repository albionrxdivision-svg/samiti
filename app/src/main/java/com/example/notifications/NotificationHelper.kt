package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessaging

object NotificationHelper {
    const val CHANNEL_ID = "admin_approval_channel"
    const val ADMIN_TOPIC = "admin_approvals"
    const val DEEP_LINK_APPROVALS = "ekotasamiti://approvals"
    const val EXTRA_DESTINATION = "destination"
    const val EXTRA_TRANSACTION_TYPE = "transactionType"
    const val EXTRA_TRANSACTION_ID = "transactionId"
    const val DEST_APPROVALS = "APPROVALS"

    private const val PREFS_FCM = "ekota_fcm_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.default_notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.default_notification_channel_desc)
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun buildApprovalsDeepLinkIntent(
        context: Context,
        transactionType: String = "",
        transactionId: String = ""
    ): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(DEEP_LINK_APPROVALS), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DESTINATION, DEST_APPROVALS)
            if (transactionType.isNotBlank()) putExtra(EXTRA_TRANSACTION_TYPE, transactionType)
            if (transactionId.isNotBlank()) putExtra(EXTRA_TRANSACTION_ID, transactionId)
        }
    }

    fun shouldOpenApprovals(intent: Intent?): Boolean {
        if (intent == null) return false
        val uri = intent.data
        if (uri != null && uri.scheme == "ekotasamiti" && uri.host == "approvals") {
            return true
        }
        val destExtra = intent.getStringExtra(EXTRA_DESTINATION)
            ?: intent.getStringExtra("targetScreen")
            ?: ""
        if (destExtra.equals(DEST_APPROVALS, ignoreCase = true)) {
            return true
        }
        val deepLinkExtra = intent.getStringExtra("deepLink") ?: ""
        return deepLinkExtra.equals(DEEP_LINK_APPROVALS, ignoreCase = true)
    }

    fun clearDeepLinkExtras(intent: Intent?) {
        intent?.data = null
        intent?.removeExtra(EXTRA_DESTINATION)
        intent?.removeExtra("targetScreen")
        intent?.removeExtra("deepLink")
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showApprovalNotification(
        context: Context,
        title: String,
        summary: String,
        transactionType: String = "",
        transactionId: String = "",
        notificationId: Int = (System.currentTimeMillis() and 0xFFFFFF).toInt()
    ) {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context)) {
            Log.w("EkotaFCM", "POST_NOTIFICATIONS permission not granted; skipping system tray notification")
            return
        }

        val intent = buildApprovalsDeepLinkIntent(
            context = context,
            transactionType = transactionType,
            transactionId = transactionId
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (se: SecurityException) {
            Log.w("EkotaFCM", "Notification blocked by OS permission: ${se.message}")
        }
    }

    fun saveCachedToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_FCM, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FCM_TOKEN, token)
            .apply()
    }

    fun getCachedToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_FCM, Context.MODE_PRIVATE)
            .getString(KEY_FCM_TOKEN, null)
    }

    fun syncAdminFcmRegistration(
        context: Context,
        isAdmin: Boolean,
        onTokenReady: (String) -> Unit
    ) {
        ensureNotificationChannel(context)
        try {
            val messaging = FirebaseMessaging.getInstance()
            if (isAdmin) {
                messaging.subscribeToTopic(ADMIN_TOPIC)
                messaging.token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val token = task.result
                        if (!token.isNullOrBlank()) {
                            saveCachedToken(context, token)
                            onTokenReady(token)
                        }
                    } else {
                        Log.w("EkotaFCM", "Failed to fetch FCM token: ${task.exception?.message}")
                    }
                }
            } else {
                messaging.unsubscribeFromTopic(ADMIN_TOPIC)
            }
        } catch (e: Exception) {
            Log.w("EkotaFCM", "FCM registration skipped: ${e.message}")
        }
    }
}
