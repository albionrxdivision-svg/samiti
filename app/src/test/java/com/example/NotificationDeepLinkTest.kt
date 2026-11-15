package com.example

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.notifications.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationDeepLinkTest {

    @Test
    fun approvalsDeepLinkIntentIsDetectedAndCleared() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = NotificationHelper.buildApprovalsDeepLinkIntent(
            context = context,
            transactionType = "SAVINGS",
            transactionId = "tx_123"
        )

        assertTrue(NotificationHelper.shouldOpenApprovals(intent))
        assertEquals(NotificationHelper.DEEP_LINK_APPROVALS, intent.data.toString())
        assertEquals("SAVINGS", intent.getStringExtra(NotificationHelper.EXTRA_TRANSACTION_TYPE))
        assertEquals("tx_123", intent.getStringExtra(NotificationHelper.EXTRA_TRANSACTION_ID))

        NotificationHelper.clearDeepLinkExtras(intent)
        assertFalse(NotificationHelper.shouldOpenApprovals(intent))
    }

    @Test
    fun fcmPayloadExtrasAlsoTriggerApprovalsNavigation() {
        val fcmPayloadIntent = Intent().apply {
            putExtra("targetScreen", "APPROVALS")
        }
        assertTrue(NotificationHelper.shouldOpenApprovals(fcmPayloadIntent))

        val deepLinkExtraIntent = Intent().apply {
            putExtra("deepLink", NotificationHelper.DEEP_LINK_APPROVALS)
        }
        assertTrue(NotificationHelper.shouldOpenApprovals(deepLinkExtraIntent))

        val uriOnlyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("ekotasamiti://approvals"))
        assertTrue(NotificationHelper.shouldOpenApprovals(uriOnlyIntent))

        val unrelatedIntent = Intent().apply {
            putExtra("targetScreen", "SETTINGS")
        }
        assertFalse(NotificationHelper.shouldOpenApprovals(unrelatedIntent))
    }

    @Test
    fun notificationChannelIsCreated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationHelper.ensureNotificationChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
            assertNotNull(channel)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        }
    }
}
