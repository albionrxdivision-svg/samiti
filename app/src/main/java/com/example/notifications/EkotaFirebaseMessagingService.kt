package com.example.notifications

import android.util.Log
import com.example.R
import com.example.data.model.AuthSession
import com.example.data.model.UserRole
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class EkotaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("EkotaFCM", "Refreshed FCM token")
        NotificationHelper.saveCachedToken(applicationContext, token)

        val currentUser = Firebase.auth.currentUser
        val sessionProfile = AuthSession.init(applicationContext)
        val isAdmin = sessionProfile?.role == UserRole.ADMIN.name ||
            currentUser?.email.equals("netai.nemai@gmail.com", ignoreCase = true)

        if (isAdmin && currentUser != null) {
            try {
                val db = FirebaseFirestore.getInstance(
                    applicationContext.getString(R.string.firestore_database_id)
                )
                val payload = mapOf(
                    "token" to token,
                    "userId" to currentUser.uid,
                    "email" to (currentUser.email ?: ""),
                    "role" to UserRole.ADMIN.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                db.collection("adminFcmTokens").document(currentUser.uid).set(payload)
            } catch (e: Exception) {
                Log.w("EkotaFCM", "Could not upload refreshed FCM token: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val notif = remoteMessage.notification

        val sessionProfile = AuthSession.init(applicationContext)
        val currentUser = Firebase.auth.currentUser
        val isAdminDevice = sessionProfile?.role == UserRole.ADMIN.name ||
            currentUser?.email.equals("netai.nemai@gmail.com", ignoreCase = true)

        // Only surface Admin approval alerts to Admin devices unless explicitly bypassed
        if (!isAdminDevice && data["targetRole"] == UserRole.ADMIN.name) {
            return
        }

        val title = notif?.title
            ?: data["title"]
            ?: "নতুন অনুমোদন অনুরোধ"
        val summary = notif?.body
            ?: data["summary"]
            ?: data["body"]
            ?: "মাঠ কর্মকর্তা একটি নতুন লেনদেন জমা দিয়েছেন। অনুমোদনের জন্য ট্যাপ করুন।"
        val txType = data["transactionType"] ?: ""
        val txId = data["transactionId"] ?: ""

        NotificationHelper.showApprovalNotification(
            context = applicationContext,
            title = title,
            summary = summary,
            transactionType = txType,
            transactionId = txId
        )
    }
}
