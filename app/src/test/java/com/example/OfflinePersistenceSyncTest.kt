package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalDataStore
import com.example.data.model.FineRecord
import com.example.data.model.LoanInstallment
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import com.example.data.repository.CollectionSnapshotUpdate
import com.example.data.repository.FirestoreClientProvider
import com.example.data.repository.SyncStatusState
import com.google.firebase.firestore.PersistentCacheSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflinePersistenceSyncTest {

    @Test
    fun persistentCacheSettingsConfigures100MbDiskPersistence() {
        val settings = FirestoreClientProvider.buildPersistentCacheSettings()
        val cacheSettings = settings.cacheSettings
        assertTrue(cacheSettings is PersistentCacheSettings)
        assertEquals(100L * 1024L * 1024L, (cacheSettings as PersistentCacheSettings).sizeBytes)
    }

    @Test
    fun provisionedFirebaseConfigStringsArePresent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbId = context.getString(R.string.firestore_database_id)
        val webClientId = context.getString(R.string.default_web_client_id)
        assertTrue(dbId.isNotBlank())
        assertTrue(webClientId.isNotBlank())
    }

    @Test
    fun connectivityCheckDetectsOfflineAndRestoredInternetStates() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = shadowOf(cm)
        val activeNetwork = cm.activeNetwork

        // Simulate connected network without internet capability (e.g., offline / captive)
        val capsOffline = ShadowNetworkCapabilities.newInstance()
        shadowCm.setNetworkCapabilities(activeNetwork, capsOffline)
        assertFalse(FirestoreClientProvider.isNetworkOnline(context))

        // Restore internet capability on active network
        val capsOnline = ShadowNetworkCapabilities.newInstance()
        shadowOf(capsOnline).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowCm.setNetworkCapabilities(activeNetwork, capsOnline)
        assertTrue(FirestoreClientProvider.isNetworkOnline(context))

        // Simulate complete loss of active network
        shadowCm.setActiveNetworkInfo(null)
        assertFalse(FirestoreClientProvider.isNetworkOnline(context))
    }

    @Test
    fun snapshotMetadataTracksPendingOfflineDocumentsAndSyncStatus() {
        val record = SavingsRecord(
            id = "sav_m1_14_11_26",
            memberId = "m1",
            memberName = "করিম",
            collectionDate = "14/11/26",
            amount = 100.0,
            isPaid = true,
            status = TransactionStatus.PENDING.name
        )
        val snapshotUpdate = CollectionSnapshotUpdate(
            items = listOf(record),
            hasPendingWrites = true,
            isFromCache = true,
            pendingDocumentIds = setOf(record.id)
        )
        assertTrue(snapshotUpdate.hasPendingWrites)
        assertTrue(snapshotUpdate.isFromCache)
        assertTrue(record.id in snapshotUpdate.pendingDocumentIds)

        val offlineSyncState = SyncStatusState(
            isOnline = false,
            isCloudAuthenticated = true,
            isSyncing = false,
            isFromCache = true,
            pendingDocumentIds = snapshotUpdate.pendingDocumentIds,
            pendingCount = snapshotUpdate.pendingDocumentIds.size
        )
        assertFalse(offlineSyncState.isOnline)
        assertEquals(1, offlineSyncState.pendingCount)
    }

    @Test
    fun offlineLocalStoreRetainsEntriesUntilClearedAfterCloudMigration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LocalDataStore(context)
        store.clearAllData()

        val offlineMember = Member(id = "mem_off_1", memberSerial = "5", name = "জামাল উদ্দিন", weeklySavingsAmount = 100.0)
        val offlineSavings = SavingsRecord(id = "sav_off_1", memberId = offlineMember.id, memberName = offlineMember.name, amount = 100.0, isPaid = true)
        val offlineInstallment = LoanInstallment(id = "inst_off_1", loanId = "loan_1", memberId = offlineMember.id, memberName = offlineMember.name, amount = 300.0)
        val offlineFine = FineRecord(id = "fine_off_1", memberId = offlineMember.id, memberName = offlineMember.name, amount = 10.0)

        store.saveMembers(listOf(offlineMember))
        store.saveSavings(listOf(offlineSavings))
        store.saveLoanInstallments(listOf(offlineInstallment))
        store.saveFines(listOf(offlineFine))

        assertEquals(1, store.loadMembers().size)
        assertEquals(1, store.loadSavings().size)
        assertEquals(1, store.loadLoanInstallments().size)
        assertEquals(1, store.loadFines().size)

        store.clearAllData()
        assertTrue(store.loadMembers().isEmpty())
        assertTrue(store.loadSavings().isEmpty())
        assertTrue(store.loadLoanInstallments().isEmpty())
        assertTrue(store.loadFines().isEmpty())
    }
}
