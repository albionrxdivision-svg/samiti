package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.CachedLoanEntity
import com.example.data.local.CachedMemberEntity
import com.example.data.local.LocalDataStore
import com.example.data.local.OfflineSyncStatus
import com.example.data.local.OfflineTransactionDao
import com.example.data.local.OfflineTransactionDatabase
import com.example.data.local.OfflineTransactionType
import com.example.data.local.PendingOfflineTransactionEntity
import com.example.data.model.*
import com.example.util.BengaliUtils
import com.example.util.OperationType
import com.example.util.SavingsAndFineCalculator
import com.example.util.handleFirestoreError
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class CollectionSnapshotUpdate<T>(
    val items: List<T>,
    val hasPendingWrites: Boolean,
    val isFromCache: Boolean,
    val pendingDocumentIds: Set<String>
)

data class SyncStatusState(
    val isOnline: Boolean = true,
    val isCloudAuthenticated: Boolean = false,
    val isSyncing: Boolean = false,
    val isFromCache: Boolean = false,
    val pendingDocumentIds: Set<String> = emptySet(),
    val pendingCount: Int = 0,
    val lastSyncedAtMillis: Long = 0L
)

class EkotaRepository(
    private val db: FirebaseFirestore,
    private val localStore: LocalDataStore? = null,
    private val appContext: Context? = null,
    private val offlineDao: OfflineTransactionDao? = null
) {

    constructor(context: Context) : this(
        db = FirestoreClientProvider.getInstance(context),
        localStore = LocalDataStore(context.applicationContext),
        appContext = context.applicationContext,
        offlineDao = OfflineTransactionDatabase.getInstance(context.applicationContext).offlineTransactionDao()
    )

    private val syncMutex = Mutex()

    private val auth = Firebase.auth

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: AuthSession.currentUserId
            ?: "admin_nitai"
    }

    fun getCurrentUser() = auth.currentUser

    fun isOnline(): Boolean = appContext?.let { FirestoreClientProvider.isNetworkOnline(it) } ?: true

    fun observeConnectivity(): Flow<Boolean> =
        appContext?.let { FirestoreClientProvider.observeConnectivity(it) } ?: flowOf(true)

    suspend fun waitForPendingWritesAndSync(): Boolean {
        return try {
            db.enableNetwork()
            withTimeoutOrNull(12_000L) {
                db.waitForPendingWrites().await()
                true
            } ?: false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Awaits a Firestore write task without hanging when the device is offline.
     * Writes are immediately persisted to Firestore's SQLite local cache and will
     * sync automatically once connectivity returns.
     */
    private suspend fun awaitWriteOfflineSafe(
        task: Task<Void>,
        opType: OperationType,
        path: String
    ) {
        task.addOnFailureListener { e ->
            if (e !is CancellationException) {
                handleFirestoreError(e, opType, path)
            }
        }
        if (!isOnline()) {
            // Persisted in Firestore offline cache & queued for automatic sync
            return
        }
        try {
            withTimeoutOrNull(1800L) {
                task.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, opType, path)
            throw e
        }
    }

    private suspend fun getQueryOfflineSafe(query: Query, path: String): QuerySnapshot {
        if (!isOnline()) {
            return query.get(Source.CACHE).await()
        }
        return try {
            withTimeoutOrNull(1800L) {
                query.get().await()
            } ?: query.get(Source.CACHE).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                query.get(Source.CACHE).await()
            } catch (_: Exception) {
                handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
        }
    }

    private suspend fun getDocumentOfflineSafe(docRef: DocumentReference, path: String): DocumentSnapshot {
        if (!isOnline()) {
            return docRef.get(Source.CACHE).await()
        }
        return try {
            withTimeoutOrNull(1800L) {
                docRef.get().await()
            } ?: docRef.get(Source.CACHE).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                docRef.get(Source.CACHE).await()
            } catch (_: Exception) {
                handleFirestoreError(e, OperationType.GET, path)
                throw e
            }
        }
    }

    private inline fun <reified T : Any> observeCollectionWithMetadata(
        query: Query,
        path: String,
        crossinline transform: (List<T>) -> List<T> = { it }
    ): Flow<CollectionSnapshotUpdate<T>> = flow {
        emitAll(
            query.snapshots(MetadataChanges.INCLUDE)
                .map { snap ->
                    val items = snap.documents.mapNotNull { doc ->
                        doc.toObject(T::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }
                    val pendingIds = snap.documents
                        .filter { it.metadata.hasPendingWrites() }
                        .map { it.id }
                        .toSet()
                    CollectionSnapshotUpdate(
                        items = transform(items),
                        hasPendingWrites = snap.metadata.hasPendingWrites() || pendingIds.isNotEmpty(),
                        isFromCache = snap.metadata.isFromCache,
                        pendingDocumentIds = pendingIds
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw (firestoreError ?: error)
                }
        )
    }

    // Local user-entered persistence (used when running in local AuthSession mode)
    fun loadLocalMembers(): List<Member> = localStore?.loadMembers() ?: emptyList()
    fun saveLocalMembers(list: List<Member>) { localStore?.saveMembers(list) }
    fun loadLocalSavings(): List<SavingsRecord> = localStore?.loadSavings() ?: emptyList()
    fun saveLocalSavings(list: List<SavingsRecord>) { localStore?.saveSavings(list) }
    fun loadLocalCollectionPeriods(): List<CollectionPeriod> = localStore?.loadCollectionPeriods() ?: emptyList()
    fun saveLocalCollectionPeriods(list: List<CollectionPeriod>) { localStore?.saveCollectionPeriods(list) }
    fun loadLocalLoans(): List<Loan> = localStore?.loadLoans() ?: emptyList()
    fun saveLocalLoans(list: List<Loan>) { localStore?.saveLoans(list) }
    fun loadLocalLoanInstallments(): List<LoanInstallment> = localStore?.loadLoanInstallments() ?: emptyList()
    fun saveLocalLoanInstallments(list: List<LoanInstallment>) { localStore?.saveLoanInstallments(list) }
    fun loadLocalFines(): List<FineRecord> = localStore?.loadFines() ?: emptyList()
    fun saveLocalFines(list: List<FineRecord>) { localStore?.saveFines(list) }
    fun loadLocalFundLedger(): List<FundLedgerEntry> = localStore?.loadFundLedger() ?: emptyList()
    fun saveLocalFundLedger(list: List<FundLedgerEntry>) { localStore?.saveFundLedger(list) }
    fun loadLocalAppSettings(): AppSettings = localStore?.loadAppSettings() ?: AppSettings()
    fun saveLocalAppSettings(settings: AppSettings) { localStore?.saveAppSettings(settings) }
    fun loadLocalAdminNotifications(): List<AdminNotification> = localStore?.loadAdminNotifications() ?: emptyList()
    fun saveLocalAdminNotifications(list: List<AdminNotification>) { localStore?.saveAdminNotifications(list) }
    fun clearAllLocalData() { localStore?.clearAllData() }

    // -------------------------------------------------------------
    // ROOM OFFLINE CACHE & SYNC QUEUE (DATA INTEGRITY + DEDUP)
    // -------------------------------------------------------------
    fun observePendingRoomTransactions(): Flow<List<PendingOfflineTransactionEntity>> =
        offlineDao?.observeUnsyncedTransactions() ?: flowOf(emptyList())

    suspend fun cacheMembersInRoom(members: List<Member>) {
        offlineDao?.replaceCachedMembers(members.map { CachedMemberEntity.fromModel(it) })
    }

    suspend fun loadCachedMembersFromRoom(): List<Member> =
        offlineDao?.getCachedMembers()?.map { it.toModel() } ?: emptyList()

    suspend fun cacheLoansInRoom(loans: List<Loan>) {
        offlineDao?.replaceCachedLoans(loans.map { CachedLoanEntity.fromModel(it) })
    }

    suspend fun loadCachedLoansFromRoom(): List<Loan> =
        offlineDao?.getCachedLoans()?.map { it.toModel() } ?: emptyList()

    suspend fun enqueueOfflineSavingsRecord(record: SavingsRecord, isOfficer: Boolean): Boolean {
        val dao = offlineDao ?: return true
        val wk = SavingsAndFineCalculator.weekStartKey(record.collectionDate) ?: 0L
        if (wk > 0L) {
            val existingInRoom = dao.findByMemberAndWeek(OfflineTransactionType.SAVINGS, record.memberId, wk)
            if (existingInRoom != null) {
                return false
            }
        }
        val docId = record.id.ifBlank { "${record.memberId}_${record.collectionDate.replace("/", "-")}" }
        dao.upsertTransaction(
            PendingOfflineTransactionEntity(
                id = docId,
                transactionType = OfflineTransactionType.SAVINGS,
                memberId = record.memberId,
                memberName = record.memberName,
                memberSerial = record.memberSerial,
                loanId = "",
                collectionDate = record.collectionDate,
                weekKey = wk,
                amount = record.amount,
                officerId = record.officerId.ifBlank { requireUserId() },
                officerName = record.officerName,
                isOfficerSubmission = isOfficer,
                syncStatus = OfflineSyncStatus.PENDING
            )
        )
        return true
    }

    suspend fun enqueueOfflineSavingsBatch(records: List<SavingsRecord>, isOfficer: Boolean): List<SavingsRecord> {
        val dao = offlineDao ?: return records
        val accepted = mutableListOf<SavingsRecord>()
        val entities = mutableListOf<PendingOfflineTransactionEntity>()
        for (rec in records) {
            val wk = SavingsAndFineCalculator.weekStartKey(rec.collectionDate) ?: 0L
            val dupInRoom = if (wk > 0L) {
                dao.findByMemberAndWeek(OfflineTransactionType.SAVINGS, rec.memberId, wk) != null
            } else false
            if (!dupInRoom) {
                val docId = rec.id.ifBlank { "${rec.memberId}_${rec.collectionDate.replace("/", "-")}" }
                accepted.add(rec.copy(id = docId))
                entities.add(
                    PendingOfflineTransactionEntity(
                        id = docId,
                        transactionType = OfflineTransactionType.SAVINGS,
                        memberId = rec.memberId,
                        memberName = rec.memberName,
                        memberSerial = rec.memberSerial,
                        loanId = "",
                        collectionDate = rec.collectionDate,
                        weekKey = wk,
                        amount = rec.amount,
                        officerId = rec.officerId.ifBlank { requireUserId() },
                        officerName = rec.officerName,
                        isOfficerSubmission = isOfficer,
                        syncStatus = OfflineSyncStatus.PENDING
                    )
                )
            }
        }
        if (entities.isNotEmpty()) {
            dao.upsertTransactions(entities)
        }
        return accepted
    }

    suspend fun enqueueOfflineLoanInstallment(installment: LoanInstallment, isOfficer: Boolean): Boolean {
        val dao = offlineDao ?: return true
        val wk = SavingsAndFineCalculator.weekStartKey(installment.installmentDate) ?: 0L
        if (wk > 0L) {
            val dupInRoom = dao.findInstallmentByLoanAndWeek(installment.loanId, wk)
            if (dupInRoom != null) {
                return false
            }
        }
        val docId = installment.id.ifBlank { "inst_${installment.loanId}_${installment.installmentDate.replace("/", "-")}" }
        dao.upsertTransaction(
            PendingOfflineTransactionEntity(
                id = docId,
                transactionType = OfflineTransactionType.LOAN_INSTALLMENT,
                memberId = installment.memberId,
                memberName = installment.memberName,
                memberSerial = installment.serialNumber,
                loanId = installment.loanId,
                collectionDate = installment.installmentDate,
                weekKey = wk,
                amount = installment.amount,
                officerId = installment.officerId.ifBlank { requireUserId() },
                officerName = installment.officerName,
                isOfficerSubmission = isOfficer,
                syncStatus = OfflineSyncStatus.PENDING
            )
        )
        return true
    }

    suspend fun markRoomTransactionSynced(id: String) {
        offlineDao?.updateSyncStatus(
            id = id,
            status = OfflineSyncStatus.SYNCED,
            retryCount = 0,
            error = "",
            syncedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun clearAllRoomData() {
        offlineDao?.clearAllPending()
        offlineDao?.clearCachedMembers()
        offlineDao?.clearCachedLoans()
    }

    /**
     * Synchronizes all pending Room-cached offline transactions to Cloud Firestore idempotently
     * using deterministic document IDs and duplicate week checks for Admin approval.
     */
    suspend fun syncPendingOfflineTransactions(): Int {
        val dao = offlineDao ?: return 0
        if (auth.currentUser == null || !isOnline()) return 0

        return syncMutex.withLock {
            val unsynced = dao.getUnsyncedTransactions()
            if (unsynced.isEmpty()) return@withLock 0

            db.enableNetwork()
            var syncedCount = 0

            for (tx in unsynced) {
                try {
                    dao.updateSyncStatus(tx.id, OfflineSyncStatus.SYNCING, tx.retryCount, "", 0L)
                    when (tx.transactionType) {
                        OfflineTransactionType.SAVINGS -> {
                            val docId = "${tx.memberId}_${tx.collectionDate.replace("/", "-")}"
                            val docRef = db.collection("savings").document(docId)
                            val existingSnap = withTimeoutOrNull(4000L) {
                                docRef.get(Source.SERVER).await()
                            }
                            if (existingSnap == null || !existingSnap.exists() ||
                                existingSnap.getString("status") == TransactionStatus.REJECTED.name
                            ) {
                                val targetStatus = if (tx.isOfficerSubmission) {
                                    TransactionStatus.PENDING.name
                                } else {
                                    TransactionStatus.APPROVED.name
                                }
                                val payload = mapOf(
                                    "memberId" to tx.memberId,
                                    "memberName" to tx.memberName,
                                    "memberSerial" to tx.memberSerial,
                                    "collectionDate" to tx.collectionDate,
                                    "amount" to tx.amount,
                                    "isPaid" to (tx.amount > 0.0),
                                    "officerId" to tx.officerId,
                                    "officerName" to tx.officerName,
                                    "status" to targetStatus,
                                    "createdBy" to tx.officerId,
                                    "approvedBy" to if (targetStatus == TransactionStatus.APPROVED.name) tx.officerId else "",
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                                docRef.set(payload, SetOptions.merge()).await()
                                if (tx.isOfficerSubmission) {
                                    sendIdempotentAdminApprovalNotification(
                                        notifId = "notif_$docId",
                                        title = "নতুন সঞ্চয় অনুমোদনের অনুরোধ",
                                        body = "${tx.officerName} সদস্য ${tx.memberName}-এর ${tx.collectionDate} তারিখের ৳${tx.amount.toInt()} সঞ্চয় অফলাইন সিঙ্ক করেছেন।",
                                        transactionType = "SAVINGS",
                                        transactionId = docId,
                                        memberName = tx.memberName,
                                        amount = tx.amount,
                                        officerId = tx.officerId,
                                        officerName = tx.officerName
                                    )
                                }
                            }
                        }

                        OfflineTransactionType.LOAN_INSTALLMENT -> {
                            val docId = "inst_${tx.loanId}_${tx.collectionDate.replace("/", "-")}"
                            val docRef = db.collection("loanInstallments").document(docId)
                            val existingSnap = withTimeoutOrNull(4000L) {
                                docRef.get(Source.SERVER).await()
                            }
                            if (existingSnap == null || !existingSnap.exists() ||
                                existingSnap.getString("status") == TransactionStatus.REJECTED.name
                            ) {
                                val targetStatus = if (tx.isOfficerSubmission) {
                                    TransactionStatus.PENDING.name
                                } else {
                                    TransactionStatus.APPROVED.name
                                }
                                val countSnap = withTimeoutOrNull(3000L) {
                                    db.collection("loanInstallments").whereEqualTo("loanId", tx.loanId).get().await()
                                }
                                val serialBengali = BengaliUtils.toBengaliDigits((countSnap?.size() ?: 0) + 1)
                                val payload = mapOf(
                                    "loanId" to tx.loanId,
                                    "memberId" to tx.memberId,
                                    "memberName" to tx.memberName,
                                    "serialNumber" to serialBengali,
                                    "installmentDate" to tx.collectionDate,
                                    "amount" to tx.amount,
                                    "isMissed" to false,
                                    "officerId" to tx.officerId,
                                    "officerName" to tx.officerName,
                                    "status" to targetStatus,
                                    "createdBy" to tx.officerId,
                                    "approvedBy" to if (targetStatus == TransactionStatus.APPROVED.name) tx.officerId else "",
                                    "createdAt" to FieldValue.serverTimestamp()
                                )
                                docRef.set(payload, SetOptions.merge()).await()
                                if (targetStatus == TransactionStatus.APPROVED.name) {
                                    applyApprovedInstallmentToLoan(
                                        tx.loanId,
                                        tx.amount,
                                        BengaliUtils.addDaysToDateStr(tx.collectionDate, 7)
                                    )
                                } else {
                                    sendIdempotentAdminApprovalNotification(
                                        notifId = "notif_$docId",
                                        title = "নতুন ঋণের কিস্তি অনুমোদনের অনুরোধ",
                                        body = "${tx.officerName} ${tx.memberName}-এর ${tx.collectionDate} তারিখের ৳${tx.amount.toInt()} কিস্তি অফলাইন সিঙ্ক করেছেন।",
                                        transactionType = "LOAN_INSTALLMENT",
                                        transactionId = docId,
                                        memberName = tx.memberName,
                                        amount = tx.amount,
                                        officerId = tx.officerId,
                                        officerName = tx.officerName
                                    )
                                }
                            }
                        }
                    }
                    dao.updateSyncStatus(
                        id = tx.id,
                        status = OfflineSyncStatus.SYNCED,
                        retryCount = tx.retryCount,
                        error = "",
                        syncedAtMillis = System.currentTimeMillis()
                    )
                    syncedCount++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    dao.updateSyncStatus(
                        id = tx.id,
                        status = OfflineSyncStatus.FAILED,
                        retryCount = tx.retryCount + 1,
                        error = e.localizedMessage ?: "Sync error",
                        syncedAtMillis = 0L
                    )
                }
            }
            syncedCount
        }
    }

    private suspend fun sendIdempotentAdminApprovalNotification(
        notifId: String,
        title: String,
        body: String,
        transactionType: String,
        transactionId: String,
        memberName: String,
        amount: Double,
        officerId: String,
        officerName: String
    ) {
        val tokens = try {
            getQueryOfflineSafe(db.collection("adminFcmTokens"), "adminFcmTokens").documents
                .mapNotNull { it.getString("token")?.takeIf { t -> t.isNotBlank() } }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
        val payload = mapOf(
            "title" to title,
            "body" to body,
            "transactionType" to transactionType,
            "transactionId" to transactionId,
            "memberName" to memberName,
            "amount" to amount,
            "officerId" to officerId,
            "officerName" to officerName,
            "targetRole" to UserRole.ADMIN.name,
            "targetTopic" to "admin_approvals",
            "targetScreen" to "APPROVALS",
            "deepLink" to "ekotasamiti://approvals",
            "recipientTokens" to tokens,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("adminNotifications").document(notifId).set(payload, SetOptions.merge()).await()
    }

    // -------------------------------------------------------------
    // USER PROFILE & ROLE
    // -------------------------------------------------------------
    fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        val path = "users/$userId"
        val reg = db.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.GET, path)
                    close(error)
                    return@addSnapshotListener
                }
                val profile = snapshot?.toObject(UserProfile::class.java)
                trySend(profile)
            }
        awaitClose { reg.remove() }
    }

    suspend fun getOrCreateUserProfile(uid: String, email: String, name: String): UserProfile {
        val path = "users/$uid"
        val docRef = db.collection("users").document(uid)
        val snap = try {
            getDocumentOfflineSafe(docRef, path)
        } catch (_: Exception) {
            null
        }
        if (snap != null && snap.exists()) {
            return snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(id = uid, userId = uid, email = email, displayName = name)
        }
        val isAdminEmail = email.equals("netai.nemai@gmail.com", ignoreCase = true)
        val preferredSessionRole = AuthSession.currentUserProfile?.role
        val isFirstUser = if (isOnline()) {
            try {
                getQueryOfflineSafe(db.collection("users").limit(1), "users").isEmpty
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
        val role = when {
            isAdminEmail || isFirstUser -> UserRole.ADMIN.name
            preferredSessionRole == UserRole.ADMIN.name -> UserRole.ADMIN.name
            else -> UserRole.FIELD_OFFICER.name
        }

        val payload = mapOf(
            "userId" to uid,
            "email" to email,
            "displayName" to name,
            "role" to role,
            "assignedOfficerId" to "",
            "assignedArea" to "যাদবপুর",
            "isActive" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        awaitWriteOfflineSafe(docRef.set(payload), OperationType.CREATE, path)
        return UserProfile(
            id = uid,
            userId = uid,
            email = email,
            displayName = name,
            role = role,
            assignedArea = "যাদবপুর",
            isActive = true
        )
    }

    suspend fun updateUserRole(targetUserId: String, role: String, area: String, isActive: Boolean) {
        val uid = requireUserId()
        val path = "users/$targetUserId"
        val payload = mapOf(
            "role" to role,
            "assignedArea" to area,
            "isActive" to isActive,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            db.collection("users").document(targetUserId).update(payload).await()
            logAudit("UPDATE_USER_ROLE", "users", targetUserId, "role/status", "$role ($isActive)")
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = flow {
        val path = "users"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap -> snap.toObjects(UserProfile::class.java) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw (firestoreError ?: error)
                }
        )
    }

    // -------------------------------------------------------------
    // NO AUTO-SEEDING: Only Admin and Field Officer entries are stored
    // -------------------------------------------------------------
    suspend fun seedInitialDataIfEmpty() {
        // Intentionally disabled: app starts empty and records only user-entered data.
    }

    // -------------------------------------------------------------
    // MEMBERS
    // -------------------------------------------------------------
    private fun parseSerialOrder(serial: String): Int {
        val bengaliDigits = mapOf(
            '০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4',
            '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9'
        )
        val ascii = serial.map { bengaliDigits[it] ?: it }.joinToString("")
        return ascii.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
    }

    fun observeMembersWithMetadata(): Flow<CollectionSnapshotUpdate<Member>> =
        observeCollectionWithMetadata(db.collection("members"), "members") { list ->
            list.sortedBy { parseSerialOrder(it.memberSerial) }
        }

    fun observeMembers(): Flow<List<Member>> =
        observeMembersWithMetadata().map { it.items }

    suspend fun fetchMembersOnce(): List<Member> {
        val path = "members"
        val snap = getQueryOfflineSafe(db.collection(path), path)
        return snap.documents
            .mapNotNull { it.toObject(Member::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .sortedBy { parseSerialOrder(it.memberSerial) }
    }

    suspend fun addMember(member: Member): String {
        val uid = requireUserId()
        val trimmedName = member.name.trim()
        require(trimmedName.isNotBlank()) { "সদস্যের নাম খালি রাখা যাবে না" }
        val path = "members"
        val docRef = if (member.id.isNotBlank()) db.collection(path).document(member.id) else db.collection(path).document()
        val payload = mapOf(
            "memberSerial" to member.memberSerial.trim(),
            "name" to trimmedName,
            "mobile" to member.mobile.trim(),
            "address" to member.address.trim(),
            "area" to member.area.trim(),
            "joinDate" to if (member.joinDate.isBlank()) BengaliUtils.getCurrentDateStr() else member.joinDate.trim(),
            "weeklySavingsAmount" to member.weeklySavingsAmount,
            "assignedOfficerId" to member.assignedOfficerId.trim(),
            "assignedOfficerName" to member.assignedOfficerName.trim(),
            "isActive" to member.isActive,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        awaitWriteOfflineSafe(docRef.set(payload), OperationType.CREATE, path)
        logAudit("CREATE_MEMBER", "members", docRef.id, "", trimmedName)
        return docRef.id
    }

    suspend fun updateMember(member: Member) {
        val uid = requireUserId()
        require(member.id.isNotBlank())
        val path = "members/${member.id}"
        val payload = mapOf(
            "memberSerial" to member.memberSerial,
            "name" to member.name,
            "mobile" to member.mobile,
            "address" to member.address,
            "area" to member.area,
            "joinDate" to member.joinDate,
            "weeklySavingsAmount" to member.weeklySavingsAmount,
            "assignedOfficerId" to member.assignedOfficerId,
            "assignedOfficerName" to member.assignedOfficerName,
            "isActive" to member.isActive,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        awaitWriteOfflineSafe(db.collection("members").document(member.id).update(payload), OperationType.UPDATE, path)
        logAudit("UPDATE_MEMBER", "members", member.id, "", member.name)
    }

    // -------------------------------------------------------------
    // SAVINGS MANAGEMENT
    // -------------------------------------------------------------
    fun observeSavingsWithMetadata(): Flow<CollectionSnapshotUpdate<SavingsRecord>> =
        observeCollectionWithMetadata(db.collection("savings"), "savings")

    fun observeSavings(): Flow<List<SavingsRecord>> =
        observeSavingsWithMetadata().map { it.items }

    suspend fun recordSavings(
        member: Member,
        collectionDate: String,
        amount: Double,
        isOfficer: Boolean,
        officerName: String
    ) {
        val uid = requireUserId()
        val path = "savings"
        // Avoid duplicate entries: deterministic ID per member and date
        val docId = "${member.id}_${collectionDate.replace("/", "-")}"
        val docRef = db.collection(path).document(docId)

        val status = if (isOfficer) TransactionStatus.PENDING.name else TransactionStatus.APPROVED.name
        val isPaid = amount > 0.0

        val payload = mapOf(
            "memberId" to member.id,
            "memberName" to member.name,
            "memberSerial" to member.memberSerial,
            "collectionDate" to collectionDate,
            "amount" to amount,
            "isPaid" to isPaid,
            "officerId" to uid,
            "officerName" to officerName,
            "status" to status,
            "createdBy" to uid,
            "approvedBy" to if (status == TransactionStatus.APPROVED.name) uid else "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        awaitWriteOfflineSafe(docRef.set(payload), OperationType.CREATE, path)
        logAudit("RECORD_SAVINGS", "savings", docId, "", "$amount ৳ ($status)")

        // If auto-approved by admin, post to fund ledger if > 0
        if (status == TransactionStatus.APPROVED.name && amount > 0.0) {
            appendLedgerEntry(
                date = collectionDate,
                description = "$collectionDate সঞ্চয় (${member.name})",
                moneyIn = amount,
                moneyOut = 0.0,
                referenceId = docId,
                type = LedgerType.SAVINGS_COLLECTION.name
            )
        }
    }

    suspend fun recordSavingsBatchEntries(
        primaryDate: String,
        recordsToWrite: List<SavingsRecord>,
        netNewCashCollected: Double,
        isOfficer: Boolean
    ) {
        val path = "savings"
        val status = if (isOfficer) TransactionStatus.PENDING.name else TransactionStatus.APPROVED.name
        val batch = db.batch()

        for (rec in recordsToWrite) {
            val docRef = db.collection(path).document(rec.id)
            val payload = mapOf(
                "memberId" to rec.memberId,
                "memberName" to rec.memberName,
                "memberSerial" to rec.memberSerial,
                "collectionDate" to rec.collectionDate,
                "amount" to rec.amount,
                "isPaid" to rec.isPaid,
                "officerId" to rec.officerId,
                "officerName" to rec.officerName,
                "status" to rec.status,
                "createdBy" to rec.createdBy,
                "approvedBy" to rec.approvedBy,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(docRef, payload)
        }

        awaitWriteOfflineSafe(batch.commit(), OperationType.WRITE, path)
        logAudit(
            action = "RECORD_WEEKLY_SAVINGS_BATCH",
            coll = "savings",
            docId = primaryDate.replace("/", "-"),
            oldVal = "",
            newVal = "${recordsToWrite.size} entries, Net Cash: ৳$netNewCashCollected ($status)"
        )
        if (status == TransactionStatus.APPROVED.name && netNewCashCollected > 0.0) {
            appendLedgerEntry(
                date = primaryDate,
                description = "$primaryDate সাপ্তাহিক সঞ্চয় আদায়",
                moneyIn = netNewCashCollected,
                moneyOut = 0.0,
                referenceId = primaryDate.replace("/", "-"),
                type = LedgerType.SAVINGS_COLLECTION.name
            )
        }
    }

    suspend fun recordWeeklySavingsBatch(
        collectionDate: String,
        entries: List<Pair<Member, Double>>,
        isOfficer: Boolean,
        officerName: String
    ) {
        val uid = requireUserId()
        val path = "savings"
        val status = if (isOfficer) TransactionStatus.PENDING.name else TransactionStatus.APPROVED.name
        val batch = db.batch()
        var totalCollected = 0.0

        for ((member, amount) in entries) {
            val docId = "${member.id}_${collectionDate.replace("/", "-")}"
            val docRef = db.collection(path).document(docId)
            val isPaid = amount > 0.0
            if (isPaid) totalCollected += amount

            val payload = mapOf(
                "memberId" to member.id,
                "memberName" to member.name,
                "memberSerial" to member.memberSerial,
                "collectionDate" to collectionDate,
                "amount" to amount,
                "isPaid" to isPaid,
                "officerId" to uid,
                "officerName" to officerName,
                "status" to status,
                "createdBy" to uid,
                "approvedBy" to if (status == TransactionStatus.APPROVED.name) uid else "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(docRef, payload)
        }

        awaitWriteOfflineSafe(batch.commit(), OperationType.WRITE, path)
        logAudit(
            action = "RECORD_WEEKLY_SAVINGS_BATCH",
            coll = "savings",
            docId = collectionDate.replace("/", "-"),
            oldVal = "",
            newVal = "${entries.size} members, Total: ৳$totalCollected ($status)"
        )
        if (status == TransactionStatus.APPROVED.name && totalCollected > 0.0) {
            appendLedgerEntry(
                date = collectionDate,
                description = "$collectionDate সাপ্তাহিক সঞ্চয় আদায় (${entries.count { it.second > 0 }} জন)",
                moneyIn = totalCollected,
                moneyOut = 0.0,
                referenceId = collectionDate.replace("/", "-"),
                type = LedgerType.SAVINGS_COLLECTION.name
            )
        }
    }

    suspend fun approveSavings(record: SavingsRecord) {
        val uid = requireUserId()
        val path = "savings/${record.id}"
        val payload = mapOf(
            "status" to TransactionStatus.APPROVED.name,
            "approvedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        awaitWriteOfflineSafe(db.collection("savings").document(record.id).update(payload), OperationType.UPDATE, path)
        logAudit("APPROVE_SAVINGS", "savings", record.id, record.status, "APPROVED")

        if (record.amount > 0.0) {
            appendLedgerEntry(
                date = record.collectionDate,
                description = "${record.collectionDate} সঞ্চয় (${record.memberName})",
                moneyIn = record.amount,
                moneyOut = 0.0,
                referenceId = record.id,
                type = LedgerType.SAVINGS_COLLECTION.name
            )
        }
    }

    suspend fun rejectSavings(record: SavingsRecord) {
        val uid = requireUserId()
        val path = "savings/${record.id}"
        val payload = mapOf(
            "status" to TransactionStatus.REJECTED.name,
            "approvedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        awaitWriteOfflineSafe(db.collection("savings").document(record.id).update(payload), OperationType.UPDATE, path)
        logAudit("REJECT_SAVINGS", "savings", record.id, record.status, "REJECTED")
    }

    // -------------------------------------------------------------
    // COLLECTION PERIODS
    // -------------------------------------------------------------
    fun observeCollectionPeriods(): Flow<List<CollectionPeriod>> =
        observeCollectionWithMetadata<CollectionPeriod>(db.collection("collectionPeriods"), "collectionPeriods")
            .map { it.items }

    suspend fun addCollectionPeriod(dateStr: String, monthYear: String) {
        val uid = requireUserId()
        val path = "collectionPeriods"
        val payload = mapOf(
            "dateStr" to dateStr,
            "monthYear" to monthYear,
            "isClosed" to false,
            "totalSavings" to 0.0,
            "createdAt" to FieldValue.serverTimestamp()
        )
        awaitWriteOfflineSafe(db.collection(path).document().set(payload), OperationType.CREATE, path)
        logAudit("ADD_COLLECTION_PERIOD", "collectionPeriods", dateStr, "", monthYear)
    }

    suspend fun updateCollectionPeriodDate(
        oldDate: String,
        newDate: String,
        monthYear: String,
        updatedRecords: List<SavingsRecord>,
        oldRecordIdsToDelete: List<String>,
        updatedFines: List<FineRecord>,
        updatedLedgerEntries: List<FundLedgerEntry>
    ) {
        val path = "collectionPeriods"
        val periodSnap = getQueryOfflineSafe(db.collection(path).whereEqualTo("dateStr", oldDate), path)
        val batch = db.batch()

        if (periodSnap.isEmpty) {
            val newPeriodRef = db.collection(path).document("period_${newDate.replace("/", "-")}")
            batch.set(
                newPeriodRef,
                mapOf(
                    "dateStr" to newDate,
                    "monthYear" to monthYear,
                    "isClosed" to false,
                    "totalSavings" to updatedRecords.sumOf { if (it.isPaid) it.amount else 0.0 },
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            for (doc in periodSnap.documents) {
                batch.update(doc.reference, mapOf("dateStr" to newDate))
            }
        }

        for (oldId in oldRecordIdsToDelete) {
            if (oldId.isNotBlank()) {
                batch.delete(db.collection("savings").document(oldId))
            }
        }

        for (rec in updatedRecords) {
            val recRef = db.collection("savings").document(rec.id)
            val payload = mapOf(
                "memberId" to rec.memberId,
                "memberName" to rec.memberName,
                "memberSerial" to rec.memberSerial,
                "collectionDate" to newDate,
                "amount" to rec.amount,
                "isPaid" to rec.isPaid,
                "officerId" to rec.officerId,
                "officerName" to rec.officerName,
                "status" to rec.status,
                "createdBy" to rec.createdBy,
                "approvedBy" to rec.approvedBy,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(recRef, payload)
        }

        for (fine in updatedFines) {
            if (fine.id.isNotBlank()) {
                batch.update(
                    db.collection("fines").document(fine.id),
                    mapOf(
                        "fineDate" to fine.fineDate,
                        "missedDate" to fine.missedDate,
                        "referenceId" to fine.referenceId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }

        for (ledger in updatedLedgerEntries) {
            if (ledger.id.isNotBlank()) {
                batch.update(
                    db.collection("fundLedger").document(ledger.id),
                    mapOf(
                        "date" to ledger.date,
                        "description" to ledger.description
                    )
                )
            }
        }

        awaitWriteOfflineSafe(batch.commit(), OperationType.WRITE, path)
        logAudit("UPDATE_COLLECTION_DATE", path, oldDate, oldDate, newDate)
    }

    // -------------------------------------------------------------
    // LOANS & INSTALLMENTS
    // -------------------------------------------------------------
    fun observeLoansWithMetadata(): Flow<CollectionSnapshotUpdate<Loan>> =
        observeCollectionWithMetadata(db.collection("loans"), "loans")

    fun observeLoans(): Flow<List<Loan>> =
        observeLoansWithMetadata().map { it.items }

    suspend fun createLoan(loan: Loan): String {
        val uid = requireUserId()
        val path = "loans"
        val docRef = db.collection(path).document()
        val totalPayable = com.example.util.LoanCalculator.calculateTotalPayable(loan.principal, loan.interest)
        val remaining = totalPayable
        val nextDate = loan.nextInstallmentDate.ifBlank { BengaliUtils.addDaysToDateStr(loan.loanDate, 7) }
        val payload = mapOf(
            "memberId" to loan.memberId,
            "memberName" to loan.memberName,
            "memberSerial" to loan.memberSerial,
            "loanDate" to loan.loanDate,
            "principal" to loan.principal,
            "interest" to loan.interest,
            "totalPayable" to totalPayable,
            "weeklyInstallment" to loan.weeklyInstallment,
            "totalPaid" to 0.0,
            "remainingLoan" to remaining,
            "nextInstallmentDate" to nextDate,
            "missedInstallments" to 0,
            "status" to LoanStatus.ACTIVE.name,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        awaitWriteOfflineSafe(docRef.set(payload), OperationType.CREATE, path)
        logAudit("CREATE_LOAN", "loans", docRef.id, "", "${loan.memberName}: ৳${loan.principal} (মোট: ৳$totalPayable)")

        appendLedgerEntry(
            date = loan.loanDate,
            description = "ঋণ বিতরণ (${loan.memberName})",
            moneyIn = 0.0,
            moneyOut = loan.principal,
            referenceId = docRef.id,
            type = LedgerType.LOAN_DISBURSEMENT.name
        )
        return docRef.id
    }

    fun observeLoanInstallmentsWithMetadata(): Flow<CollectionSnapshotUpdate<LoanInstallment>> =
        observeCollectionWithMetadata(db.collection("loanInstallments"), "loanInstallments")

    fun observeLoanInstallments(): Flow<List<LoanInstallment>> =
        observeLoanInstallmentsWithMetadata().map { it.items }

    suspend fun recordLoanInstallment(
        loan: Loan,
        date: String,
        amount: Double,
        isOfficer: Boolean,
        officerName: String = ""
    ) {
        val uid = requireUserId()
        val path = "loanInstallments"
        // Deterministic document ID prevents duplicate installment entries across offline retries & syncs
        val deterministicId = "inst_${loan.id}_${date.replace("/", "-")}"
        val docRef = db.collection(path).document(deterministicId)
        val status = if (isOfficer) TransactionStatus.PENDING.name else TransactionStatus.APPROVED.name

        val countSnap = try {
            getQueryOfflineSafe(db.collection(path).whereEqualTo("loanId", loan.id), path)
        } catch (_: Exception) {
            null
        }
        val serialBengali = BengaliUtils.toBengaliDigits((countSnap?.size() ?: 0) + 1)

        val payload = mapOf(
            "loanId" to loan.id,
            "memberId" to loan.memberId,
            "memberName" to loan.memberName,
            "serialNumber" to serialBengali,
            "installmentDate" to date,
            "amount" to amount,
            "isMissed" to false,
            "officerId" to uid,
            "officerName" to officerName,
            "status" to status,
            "createdBy" to uid,
            "approvedBy" to if (status == TransactionStatus.APPROVED.name) uid else "",
            "createdAt" to FieldValue.serverTimestamp()
        )

        awaitWriteOfflineSafe(docRef.set(payload, SetOptions.merge()), OperationType.CREATE, path)
        logAudit("RECORD_INSTALLMENT", "loanInstallments", docRef.id, "", "${loan.memberName}: ৳$amount ($status)")

        if (status == TransactionStatus.APPROVED.name) {
            applyApprovedInstallmentToLoan(loan.id, amount, BengaliUtils.addDaysToDateStr(date, 7))
            appendLedgerEntry(
                date = date,
                description = "ঋণের কিস্তি আদায় (${loan.memberName})",
                moneyIn = amount,
                moneyOut = 0.0,
                referenceId = docRef.id,
                type = LedgerType.LOAN_INSTALLMENT.name
            )
        }
    }

    suspend fun recordMissedLoanInstallment(loan: Loan, missedDate: String) {
        val uid = requireUserId()
        val path = "loanInstallments"
        val docRef = db.collection(path).document()
        val countSnap = try {
            getQueryOfflineSafe(db.collection(path).whereEqualTo("loanId", loan.id), path)
        } catch (_: Exception) {
            null
        }
        val serialBengali = BengaliUtils.toBengaliDigits((countSnap?.size() ?: 0) + 1)

        val payload = mapOf(
            "loanId" to loan.id,
            "memberId" to loan.memberId,
            "memberName" to loan.memberName,
            "serialNumber" to serialBengali,
            "installmentDate" to missedDate,
            "amount" to 0.0,
            "isMissed" to true,
            "officerId" to uid,
            "status" to TransactionStatus.APPROVED.name,
            "createdBy" to uid,
            "approvedBy" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        awaitWriteOfflineSafe(docRef.set(payload), OperationType.CREATE, path)

        val nextDate = BengaliUtils.addDaysToDateStr(missedDate, 7)
        awaitWriteOfflineSafe(
            db.collection("loans").document(loan.id).update(
                mapOf(
                    "missedInstallments" to (loan.missedInstallments + 1),
                    "nextInstallmentDate" to nextDate,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ),
            OperationType.UPDATE,
            "loans/${loan.id}"
        )
        logAudit("MISSED_INSTALLMENT", "loans", loan.id, "", "${loan.memberName}: $missedDate বাকি")
    }

    suspend fun approveLoanInstallment(installment: LoanInstallment) {
        val uid = requireUserId()
        val path = "loanInstallments/${installment.id}"
        val payload = mapOf(
            "status" to TransactionStatus.APPROVED.name,
            "approvedBy" to uid
        )
        awaitWriteOfflineSafe(
            db.collection("loanInstallments").document(installment.id).update(payload),
            OperationType.UPDATE,
            path
        )
        logAudit("APPROVE_INSTALLMENT", "loanInstallments", installment.id, installment.status, "APPROVED")

        applyApprovedInstallmentToLoan(installment.loanId, installment.amount, BengaliUtils.addDaysToDateStr(installment.installmentDate, 7))
        appendLedgerEntry(
            date = installment.installmentDate,
            description = "ঋণের কিস্তি আদায় (${installment.memberName})",
            moneyIn = installment.amount,
            moneyOut = 0.0,
            referenceId = installment.id,
            type = LedgerType.LOAN_INSTALLMENT.name
        )
    }

    private suspend fun applyApprovedInstallmentToLoan(loanId: String, amount: Double, nextDate: String = "") {
        val loanRef = db.collection("loans").document(loanId)
        val loanDoc = try {
            getDocumentOfflineSafe(loanRef, loanRef.path)
        } catch (_: Exception) {
            return
        }
        if (loanDoc.exists()) {
            val loan = loanDoc.toObject(Loan::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: return
            val catchUp = com.example.util.SavingsAndFineCalculator.calculateLoanCatchUp(loan, amount)
            val newStatus = if (catchUp.isFullyPaid) LoanStatus.PAID.name else LoanStatus.ACTIVE.name
            val resolvedNextDate = if (nextDate.isNotBlank()) nextDate else loan.nextInstallmentDate
            awaitWriteOfflineSafe(
                loanRef.update(
                    mapOf(
                        "totalPaid" to catchUp.newTotalPaid,
                        "remainingLoan" to catchUp.newRemainingLoan,
                        "missedInstallments" to catchUp.remainingMissedInstallments,
                        "nextInstallmentDate" to resolvedNextDate,
                        "status" to newStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ),
                OperationType.UPDATE,
                loanRef.path
            )
        }
    }

    // -------------------------------------------------------------
    // FINES SHEET (জরিমানা শিট - মূল সঞ্চয় ও ঋণ থেকে সম্পূর্ণ আলাদা)
    // -------------------------------------------------------------
    fun observeFinesWithMetadata(): Flow<CollectionSnapshotUpdate<FineRecord>> =
        observeCollectionWithMetadata(db.collection("fines"), "fines")

    fun observeFines(): Flow<List<FineRecord>> =
        observeFinesWithMetadata().map { it.items }

    suspend fun upsertFinesBatch(fines: List<FineRecord>) {
        if (fines.isEmpty()) return
        val uid = requireUserId()
        val path = "fines"
        val batch = db.batch()
        for (fine in fines) {
            val docId = fine.id.ifBlank { "fine_${fine.memberId}_${System.currentTimeMillis()}" }
            val docRef = db.collection(path).document(docId)
            val payload = mapOf(
                "memberId" to fine.memberId,
                "memberName" to fine.memberName,
                "memberSerial" to fine.memberSerial,
                "fineDate" to fine.fineDate,
                "missedDate" to fine.missedDate,
                "fineType" to fine.fineType,
                "amount" to fine.amount,
                "isPaid" to fine.isPaid,
                "paidDate" to fine.paidDate,
                "referenceId" to fine.referenceId,
                "officerId" to fine.officerId.ifBlank { uid },
                "officerName" to fine.officerName,
                "status" to fine.status,
                "createdBy" to fine.createdBy.ifBlank { uid },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(docRef, payload)
        }
        awaitWriteOfflineSafe(batch.commit(), OperationType.WRITE, path)
        logAudit("UPSERT_FINES", "fines", "${fines.size}", "", "৳${fines.sumOf { it.amount }} জরিমানা")
    }

    suspend fun updateFinePaymentStatus(fineId: String, isPaid: Boolean, paidDate: String) {
        val path = "fines/$fineId"
        val payload = mapOf(
            "isPaid" to isPaid,
            "paidDate" to if (isPaid) paidDate else "",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        awaitWriteOfflineSafe(
            db.collection("fines").document(fineId).update(payload),
            OperationType.UPDATE,
            path
        )
        logAudit("UPDATE_FINE_STATUS", "fines", fineId, "", if (isPaid) "PAID ($paidDate)" else "UNPAID")
    }

    suspend fun deleteFine(fineId: String) {
        if (fineId.isBlank()) return
        val path = "fines/$fineId"
        awaitWriteOfflineSafe(
            db.collection("fines").document(fineId).delete(),
            OperationType.DELETE,
            path
        )
        logAudit("DELETE_FINE", "fines", fineId, "", "DELETED")
    }

    suspend fun deleteMember(memberId: String) {
        if (memberId.isBlank()) return
        val path = "members/$memberId"
        awaitWriteOfflineSafe(
            db.collection("members").document(memberId).delete(),
            OperationType.DELETE,
            path
        )
        logAudit("DELETE_MEMBER", "members", memberId, "", "DELETED")
    }

    suspend fun deleteSavingsRecord(recordId: String) {
        if (recordId.isBlank()) return
        val path = "savings/$recordId"
        awaitWriteOfflineSafe(
            db.collection("savings").document(recordId).delete(),
            OperationType.DELETE,
            path
        )
        logAudit("DELETE_SAVINGS", "savings", recordId, "", "DELETED")
    }

    suspend fun deleteLoan(loanId: String) {
        if (loanId.isBlank()) return
        val path = "loans/$loanId"
        awaitWriteOfflineSafe(
            db.collection("loans").document(loanId).delete(),
            OperationType.DELETE,
            path
        )
        logAudit("DELETE_LOAN", "loans", loanId, "", "DELETED")
    }

    suspend fun deleteLoanInstallment(installmentId: String) {
        if (installmentId.isBlank()) return
        val path = "loanInstallments/$installmentId"
        awaitWriteOfflineSafe(
            db.collection("loanInstallments").document(installmentId).delete(),
            OperationType.DELETE,
            path
        )
        logAudit("DELETE_INSTALLMENT", "loanInstallments", installmentId, "", "DELETED")
    }

    suspend fun deleteLedgerEntry(entryId: String) {
        if (entryId.isBlank()) return
        val path = "fundLedger/$entryId"
        awaitWriteOfflineSafe(
            db.collection("fundLedger").document(entryId).delete(),
            OperationType.DELETE,
            path
        )
        logAudit("DELETE_LEDGER", "fundLedger", entryId, "", "DELETED")
    }

    // -------------------------------------------------------------
    // FUND LEDGER (জমার হিসাব)
    // -------------------------------------------------------------
    fun observeFundLedger(): Flow<List<FundLedgerEntry>> =
        observeCollectionWithMetadata<FundLedgerEntry>(db.collection("fundLedger"), "fundLedger")
            .map { it.items }

    suspend fun appendLedgerEntry(
        date: String,
        description: String,
        moneyIn: Double,
        moneyOut: Double,
        referenceId: String = "",
        type: String = LedgerType.DEPOSIT.name
    ) {
        val uid = auth.currentUser?.uid ?: "system"
        val path = "fundLedger"
        val lastEntries = try {
            getQueryOfflineSafe(
                db.collection(path).orderBy("createdAt", Query.Direction.DESCENDING).limit(1),
                path
            )
        } catch (_: Exception) {
            null
        }
        val prevBalance = if (lastEntries != null && !lastEntries.isEmpty) {
            lastEntries.documents[0].getDouble("balance") ?: 0.0
        } else {
            0.0
        }
        val currentBalance = prevBalance + moneyIn - moneyOut
        val payload = mapOf(
            "date" to date,
            "description" to description,
            "moneyIn" to moneyIn,
            "moneyOut" to moneyOut,
            "balance" to currentBalance,
            "referenceId" to referenceId,
            "type" to type,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteOfflineSafe(db.collection(path).document().set(payload), OperationType.CREATE, path)
            logAudit("LEDGER_ENTRY", "fundLedger", "", "", "$description (Bal: ৳$currentBalance)")
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    // -------------------------------------------------------------
    // AUDIT LOGS & SETTINGS
    // -------------------------------------------------------------
    suspend fun logAudit(action: String, coll: String, docId: String, oldVal: String, newVal: String) {
        val uid = auth.currentUser?.uid ?: "anonymous"
        val name = auth.currentUser?.displayName ?: "User"
        val payload = mapOf(
            "action" to action,
            "collection" to coll,
            "documentId" to docId,
            "oldValue" to oldVal,
            "newValue" to newVal,
            "performedBy" to uid,
            "performedByName" to name,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteOfflineSafe(db.collection("auditLogs").document().set(payload), OperationType.CREATE, "auditLogs")
        } catch (_: Exception) {
            // non-fatal
        }
    }

    fun observeAuditLogs(): Flow<List<AuditLog>> =
        observeCollectionWithMetadata<AuditLog>(db.collection("auditLogs"), "auditLogs")
            .map { it.items }

    fun observeAppSettings(): Flow<AppSettings?> = callbackFlow {
        val path = "appSettings/main"
        val reg = db.collection("appSettings").document("main")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) {
                    handleFirestoreError(err, OperationType.GET, path)
                    close(err)
                    return@addSnapshotListener
                }
                trySend(snap?.toObject(AppSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
            }
        awaitClose { reg.remove() }
    }

    suspend fun updateAppSettings(settings: AppSettings) {
        val uid = requireUserId()
        val path = "appSettings/main"
        val payload = mapOf(
            "orgName" to settings.orgName,
            "subtitle" to settings.subtitle,
            "currency" to settings.currency,
            "currentMonthHeading" to settings.currentMonthHeading,
            "interestRatePercent" to settings.interestRatePercent,
            "appDownloadUrl" to settings.appDownloadUrl,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        awaitWriteOfflineSafe(db.collection("appSettings").document("main").set(payload), OperationType.UPDATE, path)
        logAudit("UPDATE_SETTINGS", "appSettings", "main", "", settings.orgName)
    }

    // -------------------------------------------------------------
    // ADMIN PUSH NOTIFICATIONS (FCM TOKENS & APPROVAL ALERTS)
    // -------------------------------------------------------------
    suspend fun registerAdminFcmToken(token: String) {
        val currentUser = auth.currentUser ?: return
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return
        val path = "adminFcmTokens/${currentUser.uid}"
        val payload = mapOf(
            "token" to cleanToken,
            "userId" to currentUser.uid,
            "email" to (currentUser.email ?: ""),
            "role" to UserRole.ADMIN.name,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        awaitWriteOfflineSafe(
            db.collection("adminFcmTokens").document(currentUser.uid).set(payload),
            OperationType.WRITE,
            path
        )
    }

    suspend fun enqueueAdminApprovalNotification(
        title: String,
        body: String,
        transactionType: String,
        transactionId: String,
        memberName: String,
        amount: Double,
        officerName: String
    ) {
        val uid = requireUserId()
        val path = "adminNotifications"
        val docRef = db.collection(path).document()
        val tokens = try {
            getQueryOfflineSafe(db.collection("adminFcmTokens"), "adminFcmTokens").documents
                .mapNotNull { it.getString("token")?.takeIf { t -> t.isNotBlank() } }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }

        val payload = mapOf(
            "title" to title,
            "body" to body,
            "transactionType" to transactionType,
            "transactionId" to transactionId,
            "memberName" to memberName,
            "amount" to amount,
            "officerId" to uid,
            "officerName" to officerName,
            "targetRole" to UserRole.ADMIN.name,
            "targetTopic" to "admin_approvals",
            "targetScreen" to "APPROVALS",
            "deepLink" to "ekotasamiti://approvals",
            "recipientTokens" to tokens,
            "isRead" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            awaitWriteOfflineSafe(docRef.set(payload), OperationType.CREATE, path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    fun observePendingAdminNotifications(): Flow<List<AdminNotification>> =
        observeCollectionWithMetadata<AdminNotification>(
            db.collection("adminNotifications").whereEqualTo("isRead", false),
            "adminNotifications"
        ).map { it.items }

    suspend fun markAdminNotificationRead(notificationId: String) {
        if (notificationId.isBlank()) return
        try {
            awaitWriteOfflineSafe(
                db.collection("adminNotifications").document(notificationId)
                    .update(mapOf("isRead" to true)),
                OperationType.UPDATE,
                "adminNotifications/$notificationId"
            )
        } catch (_: Exception) {
            // Non-fatal
        }
    }

    /**
     * Migrates any locally staged entries stored in [LocalDataStore] before cloud authentication
     * into Firestore's persistent offline queue and clears the temporary staging copy.
     */
    suspend fun migrateLocalEntriesToFirestore(isAdmin: Boolean): Int {
        val uid = auth.currentUser?.uid ?: return 0
        val store = localStore ?: return 0

        val members = store.loadMembers()
        val savings = store.loadSavings()
        val installments = store.loadLoanInstallments()
        val fines = store.loadFines()
        val periods = if (isAdmin) store.loadCollectionPeriods() else emptyList()
        val loans = if (isAdmin) store.loadLoans() else emptyList()
        val ledger = if (isAdmin) store.loadFundLedger() else emptyList()

        val totalCount = members.size + savings.size + installments.size + fines.size + periods.size + loans.size + ledger.size
        if (totalCount == 0) return 0

        val batch = db.batch()
        members.forEach { m ->
            val docRef = if (m.id.isNotBlank()) db.collection("members").document(m.id) else db.collection("members").document()
            batch.set(
                docRef,
                mapOf(
                    "memberSerial" to m.memberSerial,
                    "name" to m.name,
                    "mobile" to m.mobile,
                    "address" to m.address,
                    "area" to m.area,
                    "joinDate" to m.joinDate,
                    "weeklySavingsAmount" to m.weeklySavingsAmount,
                    "assignedOfficerId" to m.assignedOfficerId,
                    "assignedOfficerName" to m.assignedOfficerName,
                    "isActive" to m.isActive,
                    "createdBy" to m.createdBy.ifBlank { uid },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }

        savings.forEach { s ->
            val docId = s.id.ifBlank { "${s.memberId}_${s.collectionDate.replace("/", "-")}" }
            batch.set(
                db.collection("savings").document(docId),
                mapOf(
                    "memberId" to s.memberId,
                    "memberName" to s.memberName,
                    "memberSerial" to s.memberSerial,
                    "collectionDate" to s.collectionDate,
                    "amount" to s.amount,
                    "isPaid" to s.isPaid,
                    "officerId" to s.officerId.ifBlank { uid },
                    "officerName" to s.officerName,
                    "status" to s.status,
                    "createdBy" to s.createdBy.ifBlank { uid },
                    "approvedBy" to s.approvedBy,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }

        installments.forEach { inst ->
            val docRef = if (inst.id.isNotBlank()) db.collection("loanInstallments").document(inst.id) else db.collection("loanInstallments").document()
            batch.set(
                docRef,
                mapOf(
                    "loanId" to inst.loanId,
                    "memberId" to inst.memberId,
                    "memberName" to inst.memberName,
                    "serialNumber" to inst.serialNumber,
                    "installmentDate" to inst.installmentDate,
                    "amount" to inst.amount,
                    "isMissed" to inst.isMissed,
                    "officerId" to inst.officerId.ifBlank { uid },
                    "status" to inst.status,
                    "createdBy" to inst.createdBy.ifBlank { uid },
                    "approvedBy" to inst.approvedBy,
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }

        fines.forEach { f ->
            val docRef = if (f.id.isNotBlank()) db.collection("fines").document(f.id) else db.collection("fines").document()
            batch.set(
                docRef,
                mapOf(
                    "memberId" to f.memberId,
                    "memberName" to f.memberName,
                    "memberSerial" to f.memberSerial,
                    "fineDate" to f.fineDate,
                    "missedDate" to f.missedDate,
                    "fineType" to f.fineType,
                    "amount" to f.amount,
                    "isPaid" to f.isPaid,
                    "paidDate" to f.paidDate,
                    "referenceId" to f.referenceId,
                    "officerId" to f.officerId.ifBlank { uid },
                    "officerName" to f.officerName,
                    "status" to f.status,
                    "createdBy" to f.createdBy.ifBlank { uid },
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }

        if (isAdmin) {
            periods.forEach { p ->
                val docRef = if (p.id.isNotBlank()) db.collection("collectionPeriods").document(p.id) else db.collection("collectionPeriods").document()
                batch.set(
                    docRef,
                    mapOf(
                        "dateStr" to p.dateStr,
                        "monthYear" to p.monthYear,
                        "isClosed" to p.isClosed,
                        "totalSavings" to p.totalSavings,
                        "createdAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
            loans.forEach { l ->
                val docRef = if (l.id.isNotBlank()) db.collection("loans").document(l.id) else db.collection("loans").document()
                batch.set(
                    docRef,
                    mapOf(
                        "memberId" to l.memberId,
                        "memberName" to l.memberName,
                        "memberSerial" to l.memberSerial,
                        "loanDate" to l.loanDate,
                        "principal" to l.principal,
                        "interest" to l.interest,
                        "totalPayable" to l.totalPayable,
                        "weeklyInstallment" to l.weeklyInstallment,
                        "totalPaid" to l.totalPaid,
                        "remainingLoan" to l.remainingLoan,
                        "nextInstallmentDate" to l.nextInstallmentDate,
                        "missedInstallments" to l.missedInstallments,
                        "status" to l.status,
                        "createdBy" to l.createdBy.ifBlank { uid },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
            ledger.forEach { e ->
                val docRef = if (e.id.isNotBlank()) db.collection("fundLedger").document(e.id) else db.collection("fundLedger").document()
                batch.set(
                    docRef,
                    mapOf(
                        "date" to e.date,
                        "description" to e.description,
                        "moneyIn" to e.moneyIn,
                        "moneyOut" to e.moneyOut,
                        "balance" to e.balance,
                        "referenceId" to e.referenceId,
                        "type" to e.type,
                        "createdBy" to e.createdBy.ifBlank { uid },
                        "createdAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
        }

        awaitWriteOfflineSafe(batch.commit(), OperationType.WRITE, "local_migration")
        store.clearMigratedKeys(includeAdminKeys = isAdmin)
        return totalCount
    }

    // -------------------------------------------------------------
    // RESET ALL DATA TO ZERO (ADMIN ONLY)
    // -------------------------------------------------------------
    suspend fun resetAllCloudData() {
        val collectionsToClear = listOf(
            "members",
            "savings",
            "collectionPeriods",
            "loans",
            "loanInstallments",
            "fines",
            "fundLedger",
            "adminNotifications"
        )
        for (collName in collectionsToClear) {
            try {
                val snap = db.collection(collName).get().await()
                val docs = snap.documents
                // Delete in chunks of 400 to stay safely below Firestore 500-op batch limit
                var index = 0
                while (index < docs.size) {
                    val end = minOf(index + 400, docs.size)
                    val batch = db.batch()
                    for (i in index until end) {
                        batch.delete(docs[i].reference)
                    }
                    batch.commit().await()
                    index = end
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.DELETE, collName)
                throw e
            }
        }
        logAudit("RESET_ALL_DATA", "system", "all", "", "সব ডাটা জিরো/রিসেট করা হয়েছে")
    }
}
