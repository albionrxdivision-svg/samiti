package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CachedLoanEntity
import com.example.data.local.CachedMemberEntity
import com.example.data.local.LocalDataStore
import com.example.data.local.OfflineSyncStatus
import com.example.data.local.OfflineTransactionDatabase
import com.example.data.local.OfflineTransactionType
import com.example.data.local.PendingOfflineTransactionEntity
import com.example.data.local.StorageQuotaManager
import com.example.data.model.FineRecord
import com.example.data.model.Loan
import com.example.data.model.LoanInstallment
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import com.example.data.repository.CollectionSnapshotUpdate
import com.example.data.repository.FirestoreClientProvider
import com.example.data.repository.SyncStatusState
import com.example.util.SavingsAndFineCalculator
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun persistentCacheSettingsSupportsUpTo100GbDiskPersistence() {
        val defaultSettings = FirestoreClientProvider.buildPersistentCacheSettings()
        val defaultCache = defaultSettings.cacheSettings
        assertTrue(defaultCache is PersistentCacheSettings)
        assertEquals(StorageQuotaManager.STORAGE_100_GB, (defaultCache as PersistentCacheSettings).sizeBytes)

        val custom100MbSettings = FirestoreClientProvider.buildPersistentCacheSettings(StorageQuotaManager.STORAGE_100_MB)
        val custom100MbCache = custom100MbSettings.cacheSettings as PersistentCacheSettings
        assertEquals(StorageQuotaManager.STORAGE_100_MB, custom100MbCache.sizeBytes)

        // Requests above 100 GB are safely clamped at 100 GB max
        val overLimitSettings = FirestoreClientProvider.buildPersistentCacheSettings(StorageQuotaManager.STORAGE_100_GB * 2L)
        val overLimitCache = overLimitSettings.cacheSettings as PersistentCacheSettings
        assertEquals(StorageQuotaManager.STORAGE_100_GB, overLimitCache.sizeBytes)
    }

    @Test
    fun storageQuotaManagerPersists100GbLimitAndManagesLocalBackupVault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Default limit is 100 GB
        StorageQuotaManager.setConfiguredLimitBytes(context, StorageQuotaManager.STORAGE_100_GB)
        assertEquals(StorageQuotaManager.STORAGE_100_GB, StorageQuotaManager.getConfiguredLimitBytes(context))
        assertEquals("১০০ GB", StorageQuotaManager.formatBytesBn(StorageQuotaManager.STORAGE_100_GB))

        // Switch to 50 GB and back to 100 GB
        StorageQuotaManager.setConfiguredLimitBytes(context, StorageQuotaManager.STORAGE_50_GB)
        assertEquals(StorageQuotaManager.STORAGE_50_GB, StorageQuotaManager.getConfiguredLimitBytes(context))
        StorageQuotaManager.setConfiguredLimitBytes(context, StorageQuotaManager.STORAGE_100_GB)
        assertEquals(StorageQuotaManager.STORAGE_100_GB, StorageQuotaManager.getConfiguredLimitBytes(context))

        // Save, list, read, and delete local JSON backup archive inside app vault
        val sampleJson = """{"organization":"যাদবপুর যুব সমাজ সমিতি","membersCount":2}"""
        val saveResult = StorageQuotaManager.saveBackupToLocalArchive(context, sampleJson, prefix = "test_backup")
        assertTrue(saveResult.isSuccess)
        val savedInfo = saveResult.getOrThrow()
        assertTrue(savedInfo.sizeBytes > 0L)

        val archives = StorageQuotaManager.listBackupArchives(context)
        assertTrue(archives.any { it.fileName == savedInfo.fileName })
        assertEquals(sampleJson, StorageQuotaManager.readBackupArchiveContent(context, savedInfo.fileName))

        val stats = StorageQuotaManager.getStorageUsageStats(context)
        assertEquals(StorageQuotaManager.STORAGE_100_GB, stats.configuredLimitBytes)
        assertTrue(stats.backupFilesBytes >= savedInfo.sizeBytes)

        assertTrue(StorageQuotaManager.deleteBackupArchive(context, savedInfo.fileName))
        assertFalse(StorageQuotaManager.listBackupArchives(context).any { it.fileName == savedInfo.fileName })
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

    @Test
    fun roomDatabaseStoresPendingOfficerTransactionsAndPreventsDuplicateWeeks() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OfflineTransactionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.offlineTransactionDao()
            val savWeekKey = requireNotNull(SavingsAndFineCalculator.weekStartKey("14/11/26"))
            val sameWeekKey = requireNotNull(SavingsAndFineCalculator.weekStartKey("18/11/26"))
            assertEquals(savWeekKey, sameWeekKey)

            val pendingSavings = PendingOfflineTransactionEntity(
                id = "m1_14-11-26",
                transactionType = OfflineTransactionType.SAVINGS,
                memberId = "m1",
                memberName = "রহিম",
                memberSerial = "1",
                loanId = "",
                collectionDate = "14/11/26",
                weekKey = savWeekKey,
                amount = 100.0,
                officerId = "officer_1",
                officerName = "সাব্বির",
                isOfficerSubmission = true,
                syncStatus = OfflineSyncStatus.PENDING
            )
            val pendingInstallment = PendingOfflineTransactionEntity(
                id = "inst_loan1_14-11-26",
                transactionType = OfflineTransactionType.LOAN_INSTALLMENT,
                memberId = "m1",
                memberName = "রহিম",
                memberSerial = "1",
                loanId = "loan1",
                collectionDate = "14/11/26",
                weekKey = savWeekKey,
                amount = 300.0,
                officerId = "officer_1",
                officerName = "সাব্বির",
                isOfficerSubmission = true,
                syncStatus = OfflineSyncStatus.PENDING
            )

            dao.upsertTransactions(listOf(pendingSavings, pendingInstallment))
            val unsynced = dao.getUnsyncedTransactions()
            assertEquals(2, unsynced.size)
            assertTrue(unsynced.all { it.isOfficerSubmission && it.syncStatus == OfflineSyncStatus.PENDING })
            assertEquals(TransactionStatus.PENDING.name, unsynced.first().toSavingsRecord().status)

            // Same week duplicate lookup works across Sat-Fri days
            val dupSav = dao.findByMemberAndWeek(OfflineTransactionType.SAVINGS, "m1", sameWeekKey)
            assertNotNull(dupSav)
            val dupInst = dao.findInstallmentByLoanAndWeek("loan1", sameWeekKey)
            assertNotNull(dupInst)
            val nextWeekKey = requireNotNull(SavingsAndFineCalculator.weekStartKey("21/11/26"))
            val nextWeekInst = dao.findInstallmentByLoanAndWeek("loan1", nextWeekKey)
            assertNull(nextWeekInst)

            // Cache members and loans for offline lookup
            val member = Member(id = "m1", memberSerial = "1", name = "রহিম", weeklySavingsAmount = 100.0)
            val loan = Loan(id = "loan1", memberId = "m1", memberName = "রহিম", memberSerial = "1", principal = 3000.0, totalPayable = 3600.0, remainingLoan = 3600.0)
            dao.replaceCachedMembers(listOf(CachedMemberEntity.fromModel(member)))
            dao.replaceCachedLoans(listOf(CachedLoanEntity.fromModel(loan)))
            assertEquals(1, dao.getCachedMembers().size)
            assertEquals("রহিম", dao.getCachedMembers().first().toModel().name)
            assertEquals(1, dao.getCachedLoans().size)
            assertEquals("loan1", dao.getCachedLoans().first().toModel().id)

            // Marking SYNCED removes item from pending queue
            dao.updateSyncStatus("m1_14-11-26", OfflineSyncStatus.SYNCED, 0, "", 123456L)
            val remaining = dao.getUnsyncedTransactions()
            assertEquals(1, remaining.size)
            assertEquals("inst_loan1_14-11-26", remaining.first().id)
        } finally {
            db.close()
        }
    }
}
