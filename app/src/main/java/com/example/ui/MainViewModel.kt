package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.EkotaRepository
import com.example.data.repository.SyncStatusState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class DashboardSummary(
    val totalMembers: Int = 0,
    val totalSavings: Double = 0.0,
    val todaySavings: Double = 0.0,
    val totalLoanDisbursed: Double = 0.0,
    val totalLoanCollected: Double = 0.0,
    val totalLoanDue: Double = 0.0,
    val currentBalance: Double = 0.0,
    val todayCollection: Double = 0.0,
    val pendingApprovalsCount: Int = 0,
    val totalFinesAssessed: Double = 0.0,
    val totalFinesCollected: Double = 0.0,
    val totalFinesDue: Double = 0.0
)

class MainViewModel(
    private val repository: EkotaRepository,
    val currentUserId: String
) : ViewModel() {

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(AuthSession.currentUserProfile)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    private val _userRole = MutableStateFlow(
        if (AuthSession.currentUserProfile?.role == UserRole.ADMIN.name) UserRole.ADMIN else UserRole.FIELD_OFFICER
    )
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

    private val _allUsers = MutableStateFlow<List<UserProfile>>(emptyList())
    val allUsers: StateFlow<List<UserProfile>> = _allUsers.asStateFlow()

    private val _members = MutableStateFlow<List<Member>>(emptyList())
    val members: StateFlow<List<Member>> = _members.asStateFlow()

    private val _membersUiState = MutableStateFlow<UiState<List<Member>>>(UiState.Loading)
    val membersUiState: StateFlow<UiState<List<Member>>> = _membersUiState.asStateFlow()

    private val _isSavingBatch = MutableStateFlow(false)
    val isSavingBatch: StateFlow<Boolean> = _isSavingBatch.asStateFlow()

    private val _isSavingLoan = MutableStateFlow(false)
    val isSavingLoan: StateFlow<Boolean> = _isSavingLoan.asStateFlow()

    private val _isCloudSynced = MutableStateFlow(repository.getCurrentUser() != null)
    val isCloudSynced: StateFlow<Boolean> = _isCloudSynced.asStateFlow()

    private val _isOnline = MutableStateFlow(repository.isOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    private val _isFromCache = MutableStateFlow(false)
    private val _lastSyncedAtMillis = MutableStateFlow(0L)

    private val _pendingMemberIds = MutableStateFlow<Set<String>>(emptySet())
    private val _pendingSavingsIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingSavingsIds: StateFlow<Set<String>> = _pendingSavingsIds.asStateFlow()

    private val _pendingLoanIds = MutableStateFlow<Set<String>>(emptySet())
    private val _pendingInstallmentIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingInstallmentIds: StateFlow<Set<String>> = _pendingInstallmentIds.asStateFlow()

    private val _pendingFineIds = MutableStateFlow<Set<String>>(emptySet())

    val syncStatus: StateFlow<SyncStatusState> = combine(
        _isOnline,
        _isCloudSynced,
        _isSyncing,
        _isFromCache,
        combine(
            _pendingMemberIds,
            _pendingSavingsIds,
            _pendingLoanIds,
            _pendingInstallmentIds,
            _pendingFineIds
        ) { m, s, l, i, f -> m + s + l + i + f }
    ) { online, cloudAuthed, syncing, fromCache, allPendingIds ->
        SyncStatusState(
            isOnline = online,
            isCloudAuthenticated = cloudAuthed,
            isSyncing = syncing,
            isFromCache = fromCache,
            pendingDocumentIds = allPendingIds,
            pendingCount = allPendingIds.size,
            lastSyncedAtMillis = _lastSyncedAtMillis.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncStatusState(
            isOnline = repository.isOnline(),
            isCloudAuthenticated = repository.getCurrentUser() != null
        )
    )

    private val _savingsRecords = MutableStateFlow<List<SavingsRecord>>(emptyList())
    val savingsRecords: StateFlow<List<SavingsRecord>> = _savingsRecords.asStateFlow()

    private val _collectionPeriods = MutableStateFlow<List<CollectionPeriod>>(emptyList())
    val collectionPeriods: StateFlow<List<CollectionPeriod>> = _collectionPeriods.asStateFlow()

    private val _loans = MutableStateFlow<List<Loan>>(emptyList())
    val loans: StateFlow<List<Loan>> = _loans.asStateFlow()

    private val _loanInstallments = MutableStateFlow<List<LoanInstallment>>(emptyList())
    val loanInstallments: StateFlow<List<LoanInstallment>> = _loanInstallments.asStateFlow()

    private val _fines = MutableStateFlow<List<FineRecord>>(emptyList())
    val fines: StateFlow<List<FineRecord>> = _fines.asStateFlow()

    private val _fundLedger = MutableStateFlow<List<FundLedgerEntry>>(emptyList())
    val fundLedger: StateFlow<List<FundLedgerEntry>> = _fundLedger.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLog>>(emptyList())
    val auditLogs: StateFlow<List<AuditLog>> = _auditLogs.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _pendingAdminNotifications = MutableStateFlow<List<AdminNotification>>(emptyList())
    val pendingAdminNotifications: StateFlow<List<AdminNotification>> = _pendingAdminNotifications.asStateFlow()

    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        combine(_members, _savingsRecords, _loans, _loanInstallments, _fundLedger) {
            membersList, savingsList, loanList, installmentsList, ledgerList ->
            listOf(membersList, savingsList, loanList, installmentsList, ledgerList)
        },
        _fines
    ) { coreLists, finesList ->
        @Suppress("UNCHECKED_CAST")
        val membersList = coreLists[0] as List<Member>
        @Suppress("UNCHECKED_CAST")
        val savingsList = coreLists[1] as List<SavingsRecord>
        @Suppress("UNCHECKED_CAST")
        val loanList = coreLists[2] as List<Loan>
        @Suppress("UNCHECKED_CAST")
        val installmentsList = coreLists[3] as List<LoanInstallment>
        @Suppress("UNCHECKED_CAST")
        val ledgerList = coreLists[4] as List<FundLedgerEntry>

        val today = com.example.util.BengaliUtils.getCurrentDateStr()
        val approvedSavings = savingsList.filter { it.status == TransactionStatus.APPROVED.name }
        val approvedInstallments = installmentsList.filter { it.status == TransactionStatus.APPROVED.name && !it.isMissed }

        val totalSavings = approvedSavings.sumOf { it.amount }
        val todaySavings = approvedSavings.filter { it.collectionDate == today }.sumOf { it.amount }
        val totalDisbursed = loanList.sumOf { it.principal }
        val totalCollectedLoan = approvedInstallments.sumOf { it.amount }
        val todayLoanCollected = approvedInstallments.filter { it.installmentDate == today }.sumOf { it.amount }
        val totalDue = loanList.filter { it.status == LoanStatus.ACTIVE.name }.sumOf { it.remainingLoan }

        val latestBalance = ledgerList.lastOrNull()?.balance ?: 0.0

        val pendingSavings = savingsList.count { it.status == TransactionStatus.PENDING.name }
        val pendingInst = installmentsList.count { it.status == TransactionStatus.PENDING.name }

        val activeFines = finesList.filter { it.status != TransactionStatus.REJECTED.name }
        val totalFinesAssessed = activeFines.sumOf { it.amount }
        val totalFinesCollected = activeFines.filter { it.isPaid }.sumOf { it.amount }
        val totalFinesDue = (totalFinesAssessed - totalFinesCollected).coerceAtLeast(0.0)

        DashboardSummary(
            totalMembers = membersList.count { it.isActive },
            totalSavings = totalSavings,
            todaySavings = todaySavings,
            totalLoanDisbursed = totalDisbursed,
            totalLoanCollected = totalCollectedLoan,
            totalLoanDue = totalDue,
            currentBalance = latestBalance,
            todayCollection = todaySavings + todayLoanCollected,
            pendingApprovalsCount = pendingSavings + pendingInst,
            totalFinesAssessed = totalFinesAssessed,
            totalFinesCollected = totalFinesCollected,
            totalFinesDue = totalFinesDue
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    init {
        initializeData()
    }

    private fun initializeData() {
        val sessionUser = AuthSession.currentUserProfile
        if (sessionUser != null) {
            _currentUserProfile.value = sessionUser
            _userRole.value = if (sessionUser.role == UserRole.ADMIN.name) UserRole.ADMIN else UserRole.FIELD_OFFICER
        }

        observeConnectivityChanges()

        val firebaseUser = repository.getCurrentUser()
        _isCloudSynced.value = firebaseUser != null

        if (firebaseUser == null) {
            // Local AuthSession mode: load only data entered by Admin / Field Officer
            val localMembers = repository.loadLocalMembers()
            _members.value = localMembers
            _membersUiState.value = UiState.Success(localMembers)
            _savingsRecords.value = repository.loadLocalSavings()
            _collectionPeriods.value = repository.loadLocalCollectionPeriods()
            _loans.value = repository.loadLocalLoans()
            _loanInstallments.value = repository.loadLocalLoanInstallments()
            _fines.value = repository.loadLocalFines()
            _fundLedger.value = repository.loadLocalFundLedger()
            _appSettings.value = repository.loadLocalAppSettings()
            _pendingAdminNotifications.value = repository.loadLocalAdminNotifications().filter { !it.isRead }
            return
        }

        viewModelScope.launch {
            var resolvedAdmin = _userRole.value == UserRole.ADMIN
            try {
                val profile = repository.getOrCreateUserProfile(
                    uid = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    name = firebaseUser.displayName ?: "ইউজার"
                )
                _currentUserProfile.value = profile
                resolvedAdmin = profile.role == UserRole.ADMIN.name
                _userRole.value = if (resolvedAdmin) UserRole.ADMIN else UserRole.FIELD_OFFICER
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Continue to attach offline-cached listeners even if profile creation encountered a transient error
            }

            // Migrate any previously entered local entries into Firestore persistent queue
            launch {
                try {
                    val migrated = repository.migrateLocalEntriesToFirestore(isAdmin = resolvedAdmin)
                    if (migrated > 0 && repository.isOnline()) {
                        syncPendingOfflineEntries()
                    }
                } catch (_: Exception) {
                }
            }

            // Observe real-time data with MetadataChanges.INCLUDE & local disk cache support
            launch {
                repository.observeUserProfile(currentUserId)
                    .catch { /* Handled safely */ }
                    .collect { p ->
                        if (p != null) {
                            _currentUserProfile.value = p
                            _userRole.value = if (p.role == UserRole.ADMIN.name) UserRole.ADMIN else UserRole.FIELD_OFFICER
                        }
                    }
            }

            launch {
                repository.observeMembersWithMetadata()
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        _membersUiState.value = UiState.Error(
                            rootError.localizedMessage ?: "Firestore থেকে সদস্য তালিকা লোড করতে সমস্যা হয়েছে"
                        )
                    }
                    .collect { update ->
                        _members.value = update.items
                        _membersUiState.value = UiState.Success(update.items)
                        _pendingMemberIds.value = update.pendingDocumentIds
                        _isFromCache.value = update.isFromCache
                    }
            }

            launch {
                repository.observeSavingsWithMetadata()
                    .catch { /* Keep current cached records if stream fails */ }
                    .collect { update ->
                        _savingsRecords.value = update.items
                        _pendingSavingsIds.value = update.pendingDocumentIds
                        _isFromCache.value = update.isFromCache
                        if (!update.hasPendingWrites && !update.isFromCache) {
                            _lastSyncedAtMillis.value = System.currentTimeMillis()
                        }
                    }
            }

            launch {
                repository.observeCollectionPeriods()
                    .catch { }
                    .collect { _collectionPeriods.value = it }
            }

            launch {
                repository.observeLoansWithMetadata()
                    .catch { }
                    .collect { update ->
                        _loans.value = update.items
                        _pendingLoanIds.value = update.pendingDocumentIds
                    }
            }

            launch {
                repository.observeLoanInstallmentsWithMetadata()
                    .catch { }
                    .collect { update ->
                        _loanInstallments.value = update.items
                        _pendingInstallmentIds.value = update.pendingDocumentIds
                    }
            }

            launch {
                repository.observeFinesWithMetadata()
                    .catch { }
                    .collect { update ->
                        _fines.value = update.items
                        _pendingFineIds.value = update.pendingDocumentIds
                    }
            }

            launch {
                repository.observeFundLedger()
                    .catch { }
                    .collect { _fundLedger.value = it }
            }

            launch {
                repository.observeAllUsers()
                    .catch { }
                    .collect { _allUsers.value = it }
            }

            launch {
                repository.observeAuditLogs()
                    .catch { }
                    .collect { _auditLogs.value = it }
            }

            launch {
                repository.observeAppSettings()
                    .catch { }
                    .collect { s ->
                        if (s != null) _appSettings.value = s
                    }
            }

            launch {
                repository.observePendingAdminNotifications()
                    .catch { }
                    .collect { list ->
                        if (_userRole.value == UserRole.ADMIN) {
                            _pendingAdminNotifications.value = list.filter { !it.isRead }
                        }
                    }
            }
        }
    }

    private fun observeConnectivityChanges() {
        viewModelScope.launch {
            var previouslyOnline = _isOnline.value
            repository.observeConnectivity().collect { online ->
                _isOnline.value = online
                if (online && !previouslyOnline && repository.getCurrentUser() != null) {
                    // Connectivity just returned — flush Firestore persistent write queue automatically
                    syncPendingOfflineEntries()
                }
                previouslyOnline = online
            }
        }
    }

    private suspend fun syncPendingOfflineEntries(): Boolean {
        if (repository.getCurrentUser() == null) return false
        _isSyncing.value = true
        return try {
            val synced = repository.waitForPendingWritesAndSync()
            if (synced) {
                _lastSyncedAtMillis.value = System.currentTimeMillis()
            }
            synced
        } finally {
            _isSyncing.value = false
        }
    }

    fun triggerManualSync(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        if (repository.getCurrentUser() == null) {
            onResult(false, "ক্লাউড সিঙ্ক চালু করতে Google অ্যাকাউন্ট দিয়ে লগইন করুন")
            return
        }
        if (!repository.isOnline()) {
            onResult(false, "ইন্টারনেট সংযোগ নেই — আপনার ডাটা ফোনে নিরাপদে সংরক্ষিত আছে, নেট পেলে অটো সিঙ্ক হবে")
            return
        }
        viewModelScope.launch {
            val ok = syncPendingOfflineEntries()
            if (ok) {
                onResult(true, "সকল অফলাইন এন্ট্রি সফলভাবে ক্লাউডে সিঙ্ক হয়েছে ✓")
            } else {
                onResult(false, "সিঙ্ক প্রক্রিয়া চলমান — সংযোগ স্থিতিশীল হলে সম্পূর্ণ হবে")
            }
        }
    }

    fun refreshMembersFromFirestore() {
        if (repository.getCurrentUser() == null) {
            val localMembers = repository.loadLocalMembers()
            _members.value = localMembers
            _membersUiState.value = UiState.Success(localMembers)
            return
        }
        viewModelScope.launch {
            _membersUiState.value = UiState.Loading
            try {
                val fetched = repository.fetchMembersOnce()
                _members.value = fetched
                _membersUiState.value = UiState.Success(fetched)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _membersUiState.value = UiState.Error(
                    e.localizedMessage ?: "Firestore থেকে সদস্য তালিকা আনা যায়নি"
                )
            }
        }
    }

    // Role preview toggle allowed only when actual logged-in user is ADMIN
    fun switchUserRole(role: UserRole) {
        if (AuthSession.currentUserProfile?.role == UserRole.ADMIN.name) {
            _userRole.value = role
        }
    }

    // Member actions
    fun addMember(member: Member, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val newMember = if (member.id.isBlank()) {
            member.copy(id = "mem_${System.currentTimeMillis()}", createdBy = currentUserId)
        } else member
        val updated = (_members.value + newMember).sortedBy {
            it.memberSerial.filter { ch -> ch.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
        }
        _members.value = updated
        _membersUiState.value = UiState.Success(updated)
        if (repository.getCurrentUser() == null) {
            repository.saveLocalMembers(updated)
        }
        onSuccess()
        viewModelScope.launch {
            try {
                if (repository.getCurrentUser() != null) {
                    repository.addMember(newMember)
                }
            } catch (_: Exception) {
                // Non-blocking
            }
        }
    }

    fun updateMember(member: Member, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != UserRole.ADMIN) {
            onError("সদস্যের তথ্য সংশোধন শুধুমাত্র অ্যাডমিন করতে পারবেন")
            return
        }
        val updated = _members.value.map { if (it.id == member.id) member else it }
        _members.value = updated
        _membersUiState.value = UiState.Success(updated)
        if (repository.getCurrentUser() == null) {
            repository.saveLocalMembers(updated)
        }
        onSuccess()
        viewModelScope.launch {
            try {
                if (repository.getCurrentUser() != null) {
                    repository.updateMember(member)
                }
            } catch (_: Exception) {
                // Non-blocking
            }
        }
    }

    // Savings actions with instant optimistic local update + missed-week double catch-up + 10 BDT fine
    fun recordSavings(
        member: Member,
        collectionDate: String,
        amount: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val isOfficer = _userRole.value == UserRole.FIELD_OFFICER
        val existingInSameWeek = _savingsRecords.value.firstOrNull {
            (it.memberId == member.id || it.memberName == member.name) &&
                it.status != TransactionStatus.REJECTED.name &&
                com.example.util.SavingsAndFineCalculator.isSameWeek(it.collectionDate, collectionDate)
        }
        if (existingInSameWeek != null) {
            onError("এই সদস্যের সঞ্চয় এই সপ্তাহে (${existingInSameWeek.collectionDate}) ইতিমধ্যেই সাবমিট করা হয়েছে। এক সপ্তাহে দুইবার সাবমিট করা যাবে না।")
            return
        }
        val officerName = _currentUserProfile.value?.displayName ?: if (isOfficer) "মাঠ কর্মকর্তা" else "অ্যাডমিন"
        val status = if (isOfficer) TransactionStatus.PENDING.name else TransactionStatus.APPROVED.name

        val orderedDates = com.example.util.SavingsAndFineCalculator.sortDateStrings(
            _collectionPeriods.value.map { it.dateStr } +
                _savingsRecords.value.map { it.collectionDate } +
                listOf(collectionDate)
        )

        val allocationResult = if (isOfficer) {
            // Field Officers can only CREATE new entries for collectionDate; Admin handles catch-up splitting on approval
            com.example.util.SavingsAllocationResult(
                allocations = listOf(
                    com.example.util.DateSavingsAllocation(
                        collectionDate = collectionDate,
                        newTotalAmount = amount,
                        addedDelta = amount,
                        isCatchUpForMissedWeek = false
                    )
                ),
                catchUpDates = emptyList()
            )
        } else {
            com.example.util.SavingsAndFineCalculator.allocateSavingsPayment(
                member = member,
                targetDate = collectionDate,
                enteredAmount = amount,
                orderedDates = orderedDates,
                existingRecords = _savingsRecords.value
            )
        }

        val currentList = _savingsRecords.value.toMutableList()
        val recordsToWrite = mutableListOf<SavingsRecord>()
        var netNewCash = 0.0

        for (alloc in allocationResult.allocations) {
            val recordId = "${member.id}_${alloc.collectionDate.replace("/", "-")}"
            val updatedRecord = SavingsRecord(
                id = recordId,
                memberId = member.id,
                memberName = member.name,
                memberSerial = member.memberSerial,
                collectionDate = alloc.collectionDate,
                amount = alloc.newTotalAmount,
                isPaid = alloc.newTotalAmount > 0.0,
                officerId = currentUserId,
                officerName = officerName,
                status = status,
                createdBy = currentUserId,
                approvedBy = if (status == TransactionStatus.APPROVED.name) currentUserId else ""
            )
            val index = currentList.indexOfFirst {
                (it.memberId == member.id || it.memberName == member.name) && it.collectionDate == alloc.collectionDate
            }
            if (index >= 0) {
                currentList[index] = updatedRecord
            } else {
                currentList.add(updatedRecord)
            }
            recordsToWrite.add(updatedRecord)
            netNewCash += alloc.addedDelta
        }
        _savingsRecords.value = currentList

        // Generate 10 BDT fine if missed (0.0) on collectionDate or earlier missed dates
        val generatedFines = mutableListOf<FineRecord>()
        if (amount <= 0.0) {
            val fineDate = com.example.util.SavingsAndFineCalculator.resolveFineAppliedDate(collectionDate, orderedDates)
            val fine = com.example.util.SavingsAndFineCalculator.buildSavingsFineRecord(
                member = member,
                missedDate = collectionDate,
                appliedDate = fineDate,
                officerId = currentUserId,
                officerName = officerName
            )
            if (_fines.value.none { it.id == fine.id }) {
                generatedFines.add(fine)
            }
        }
        for (catchDate in allocationResult.catchUpDates) {
            val fineId = "fine_sav_${member.id}_${catchDate.replace("/", "-")}"
            if (_fines.value.none { it.id == fineId }) {
                val appliedDate = com.example.util.SavingsAndFineCalculator.resolveFineAppliedDate(catchDate, orderedDates)
                generatedFines.add(
                    com.example.util.SavingsAndFineCalculator.buildSavingsFineRecord(
                        member = member,
                        missedDate = catchDate,
                        appliedDate = appliedDate,
                        officerId = currentUserId,
                        officerName = officerName
                    )
                )
            }
        }
        mergeGeneratedFinesLocally(generatedFines)

        if (repository.getCurrentUser() == null) {
            repository.saveLocalSavings(currentList)
            if (status == TransactionStatus.APPROVED.name && netNewCash > 0.0) {
                val prevBalance = _fundLedger.value.lastOrNull()?.balance ?: 0.0
                val descSuffix = if (allocationResult.catchUpDates.isNotEmpty()) {
                    " [বকেয়া সহ]"
                } else ""
                val newLedger = _fundLedger.value + FundLedgerEntry(
                    id = "ledger_${member.id}_${collectionDate.replace("/", "-")}_${System.currentTimeMillis()}",
                    date = collectionDate,
                    description = "$collectionDate সঞ্চয় (${member.name})$descSuffix",
                    moneyIn = netNewCash,
                    moneyOut = 0.0,
                    balance = prevBalance + netNewCash,
                    referenceId = "${member.id}_${collectionDate.replace("/", "-")}",
                    type = LedgerType.SAVINGS_COLLECTION.name,
                    createdBy = currentUserId
                )
                _fundLedger.value = newLedger
                repository.saveLocalFundLedger(newLedger)
            }
            if (isOfficer) {
                notifyAdminOfOfficerSubmission(
                    title = "নতুন সঞ্চয় অনুমোদনের অনুরোধ",
                    body = "$officerName সদস্য ${member.name}-এর $collectionDate তারিখের ৳${amount.toInt()} সঞ্চয় জমা পাঠিয়েছেন।",
                    transactionType = "SAVINGS",
                    transactionId = "${member.id}_${collectionDate.replace("/", "-")}",
                    memberName = member.name,
                    amount = amount,
                    officerName = officerName
                )
            }
            onSuccess()
            return
        }

        onSuccess()
        viewModelScope.launch {
            try {
                repository.recordSavingsBatchEntries(
                    primaryDate = collectionDate,
                    recordsToWrite = recordsToWrite,
                    netNewCashCollected = netNewCash,
                    isOfficer = isOfficer
                )
                if (generatedFines.isNotEmpty()) {
                    repository.upsertFinesBatch(generatedFines)
                }
                if (isOfficer) {
                    notifyAdminOfOfficerSubmission(
                        title = "নতুন সঞ্চয় অনুমোদনের অনুরোধ",
                        body = "$officerName সদস্য ${member.name}-এর $collectionDate তারিখের ৳${amount.toInt()} সঞ্চয় জমা পাঠিয়েছেন।",
                        transactionType = "SAVINGS",
                        transactionId = "${member.id}_${collectionDate.replace("/", "-")}",
                        memberName = member.name,
                        amount = amount,
                        officerName = officerName
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "সঞ্চয় সংরক্ষণে সমস্যা হয়েছে")
            }
        }
    }

    fun submitWeeklySavingsBatch(
        collectionDate: String,
        memberAmounts: Map<String, Double>,
        onSuccess: (paidCount: Int, totalAmount: Double) -> Unit,
        onError: (String) -> Unit,
        onDetails: (catchUpCount: Int, finesGenerated: Int) -> Unit = { _, _ -> }
    ) {
        val currentMembers = _members.value
        val entries = currentMembers.mapNotNull { member ->
            val amt = memberAmounts[member.id] ?: return@mapNotNull null
            member to amt
        }
        if (entries.isEmpty()) {
            onError("কোনো সদস্যের সঞ্চয় এন্ট্রি পাওয়া যায়নি")
            return
        }

        val isOfficer = _userRole.value == UserRole.FIELD_OFFICER
        // Enforce once-per-week rule: exclude members who already have a non-rejected record in the same week
        val filteredEntries = entries.filter { (member, _) ->
            _savingsRecords.value.none {
                (it.memberId == member.id || it.memberName == member.name) &&
                    it.status != TransactionStatus.REJECTED.name &&
                    com.example.util.SavingsAndFineCalculator.isSameWeek(it.collectionDate, collectionDate)
            }
        }

        if (filteredEntries.isEmpty()) {
            onError("এই সপ্তাহে ($collectionDate) ইতিমধ্যেই সঞ্চয় সাবমিট করা হয়েছে। এক সপ্তাহে দুইবার সাবমিট করা যাবে না।")
            return
        }

        val officerName = _currentUserProfile.value?.displayName ?: "অ্যাডমিন"
        val status = if (isOfficer) TransactionStatus.PENDING.name else TransactionStatus.APPROVED.name

        val orderedDates = com.example.util.SavingsAndFineCalculator.sortDateStrings(
            _collectionPeriods.value.map { it.dateStr } +
                _savingsRecords.value.map { it.collectionDate } +
                listOf(collectionDate)
        )

        val updatedList = _savingsRecords.value.toMutableList()
        val recordsToWrite = mutableListOf<SavingsRecord>()
        val generatedFines = mutableListOf<FineRecord>()
        var catchUpCount = 0
        var netNewCashTotal = 0.0

        for ((member, enteredAmt) in filteredEntries) {
            val missedBefore = com.example.util.SavingsAndFineCalculator.findMissedEarlierSavingsDates(
                member = member,
                targetDate = collectionDate,
                orderedDates = orderedDates,
                existingRecords = updatedList
            )

            val allocationResult = if (isOfficer) {
                // Officers only create new pending entries; Admin allocates catch-up upon approval
                com.example.util.SavingsAllocationResult(
                    allocations = listOf(
                        com.example.util.DateSavingsAllocation(
                            collectionDate = collectionDate,
                            newTotalAmount = enteredAmt,
                            addedDelta = enteredAmt,
                            isCatchUpForMissedWeek = false
                        )
                    ),
                    catchUpDates = emptyList()
                )
            } else {
                com.example.util.SavingsAndFineCalculator.allocateSavingsPayment(
                    member = member,
                    targetDate = collectionDate,
                    enteredAmount = enteredAmt,
                    orderedDates = orderedDates,
                    existingRecords = updatedList
                )
            }

            if (allocationResult.catchUpDates.isNotEmpty()) {
                catchUpCount += allocationResult.catchUpDates.size
            }

            for (alloc in allocationResult.allocations) {
                val recordId = "${member.id}_${alloc.collectionDate.replace("/", "-")}"
                val rec = SavingsRecord(
                    id = recordId,
                    memberId = member.id,
                    memberName = member.name,
                    memberSerial = member.memberSerial,
                    collectionDate = alloc.collectionDate,
                    amount = alloc.newTotalAmount,
                    isPaid = alloc.newTotalAmount > 0.0,
                    officerId = currentUserId,
                    officerName = officerName,
                    status = status,
                    createdBy = currentUserId,
                    approvedBy = if (status == TransactionStatus.APPROVED.name) currentUserId else ""
                )
                val idx = updatedList.indexOfFirst {
                    (it.memberId == member.id || it.memberName == member.name) && it.collectionDate == alloc.collectionDate
                }
                if (idx >= 0) {
                    updatedList[idx] = rec
                } else {
                    updatedList.add(rec)
                }
                recordsToWrite.add(rec)
                netNewCashTotal += alloc.addedDelta
            }

            // Generate 10 BDT fine if current week is missed (0.0)
            if (enteredAmt <= 0.0) {
                val appliedDate = com.example.util.SavingsAndFineCalculator.resolveFineAppliedDate(collectionDate, orderedDates)
                val fine = com.example.util.SavingsAndFineCalculator.buildSavingsFineRecord(
                    member = member,
                    missedDate = collectionDate,
                    appliedDate = appliedDate,
                    officerId = currentUserId,
                    officerName = officerName
                )
                if (_fines.value.none { it.id == fine.id } && generatedFines.none { it.id == fine.id }) {
                    generatedFines.add(fine)
                }
            }

            // Also ensure 10 BDT fine exists for any earlier missed week(s) still unpaid/just caught up
            for (missedPrev in missedBefore) {
                val appliedDate = com.example.util.SavingsAndFineCalculator.resolveFineAppliedDate(missedPrev, orderedDates)
                val fine = com.example.util.SavingsAndFineCalculator.buildSavingsFineRecord(
                    member = member,
                    missedDate = missedPrev,
                    appliedDate = appliedDate,
                    officerId = currentUserId,
                    officerName = officerName
                )
                if (_fines.value.none { it.id == fine.id } && generatedFines.none { it.id == fine.id }) {
                    generatedFines.add(fine)
                }
            }
        }
        _savingsRecords.value = updatedList
        mergeGeneratedFinesLocally(generatedFines)

        val paidCount = filteredEntries.count { it.second > 0.0 }
        val totalEnteredAmount = filteredEntries.sumOf { it.second }

        if (repository.getCurrentUser() == null) {
            repository.saveLocalSavings(updatedList)
            if (status == TransactionStatus.APPROVED.name && netNewCashTotal > 0.0) {
                val prevBalance = _fundLedger.value.lastOrNull()?.balance ?: 0.0
                val newLedger = _fundLedger.value + FundLedgerEntry(
                    id = "ledger_batch_${collectionDate.replace("/", "-")}_${System.currentTimeMillis()}",
                    date = collectionDate,
                    description = "$collectionDate সাপ্তাহিক সঞ্চয় আদায় (${paidCount} জন)",
                    moneyIn = netNewCashTotal,
                    moneyOut = 0.0,
                    balance = prevBalance + netNewCashTotal,
                    referenceId = collectionDate.replace("/", "-"),
                    type = LedgerType.SAVINGS_COLLECTION.name,
                    createdBy = currentUserId
                )
                _fundLedger.value = newLedger
                repository.saveLocalFundLedger(newLedger)
            }
            if (isOfficer) {
                notifyAdminOfOfficerSubmission(
                    title = "নতুন সাপ্তাহিক সঞ্চয় অনুমোদনের অনুরোধ",
                    body = "$officerName $collectionDate তারিখে ${paidCount} জন সদস্যের মোট ৳${totalEnteredAmount.toInt()} সঞ্চয় জমা পাঠিয়েছেন।",
                    transactionType = "SAVINGS_BATCH",
                    transactionId = collectionDate.replace("/", "-"),
                    memberName = "${paidCount} জন সদস্য",
                    amount = totalEnteredAmount,
                    officerName = officerName
                )
            }
            onDetails(catchUpCount, generatedFines.size)
            onSuccess(paidCount, totalEnteredAmount)
            return
        }

        viewModelScope.launch {
            _isSavingBatch.value = true
            try {
                repository.recordSavingsBatchEntries(
                    primaryDate = collectionDate,
                    recordsToWrite = recordsToWrite,
                    netNewCashCollected = netNewCashTotal,
                    isOfficer = isOfficer
                )
                if (generatedFines.isNotEmpty()) {
                    repository.upsertFinesBatch(generatedFines)
                }
                if (isOfficer) {
                    notifyAdminOfOfficerSubmission(
                        title = "নতুন সাপ্তাহিক সঞ্চয় অনুমোদনের অনুরোধ",
                        body = "$officerName $collectionDate তারিখে ${paidCount} জন সদস্যের মোট ৳${totalEnteredAmount.toInt()} সঞ্চয় জমা পাঠিয়েছেন।",
                        transactionType = "SAVINGS_BATCH",
                        transactionId = collectionDate.replace("/", "-"),
                        memberName = "${paidCount} জন সদস্য",
                        amount = totalEnteredAmount,
                        officerName = officerName
                    )
                }
                onDetails(catchUpCount, generatedFines.size)
                onSuccess(paidCount, totalEnteredAmount)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Firestore-এ সঞ্চয় সংরক্ষণ ব্যর্থ হয়েছে")
            } finally {
                _isSavingBatch.value = false
            }
        }
    }

    private fun mergeGeneratedFinesLocally(newFines: List<FineRecord>) {
        if (newFines.isEmpty()) return
        val merged = _fines.value.toMutableList()
        for (fine in newFines) {
            if (merged.none { it.id == fine.id }) {
                merged.add(fine)
            }
        }
        _fines.value = merged
        if (repository.getCurrentUser() == null) {
            repository.saveLocalFines(merged)
        }
    }

    fun approveSavings(record: SavingsRecord, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != UserRole.ADMIN) {
            onError("অনুমোদন শুধুমাত্র অ্যাডমিন করতে পারবেন")
            return
        }
        val member = _members.value.firstOrNull { it.id == record.memberId || it.name == record.memberName }
        val orderedDates = com.example.util.SavingsAndFineCalculator.sortDateStrings(
            _collectionPeriods.value.map { it.dateStr } +
                _savingsRecords.value.map { it.collectionDate } +
                listOf(record.collectionDate)
        )
        val allocationResult = if (member != null && record.amount > member.weeklySavingsAmount) {
            com.example.util.SavingsAndFineCalculator.allocateSavingsPayment(
                member = member,
                targetDate = record.collectionDate,
                enteredAmount = record.amount,
                orderedDates = orderedDates,
                existingRecords = _savingsRecords.value
            )
        } else null

        if (repository.getCurrentUser() == null) {
            val updated = _savingsRecords.value.toMutableList()
            if (allocationResult != null && allocationResult.catchUpDates.isNotEmpty() && member != null) {
                for (alloc in allocationResult.allocations) {
                    val recId = "${member.id}_${alloc.collectionDate.replace("/", "-")}"
                    val newRec = record.copy(
                        id = recId,
                        collectionDate = alloc.collectionDate,
                        amount = alloc.newTotalAmount,
                        isPaid = alloc.newTotalAmount > 0.0,
                        status = TransactionStatus.APPROVED.name,
                        approvedBy = currentUserId
                    )
                    val idx = updated.indexOfFirst { it.id == recId || (it.memberId == member.id && it.collectionDate == alloc.collectionDate) }
                    if (idx >= 0) updated[idx] = newRec else updated.add(newRec)
                }
            } else {
                for (i in updated.indices) {
                    if (updated[i].id == record.id) {
                        updated[i] = updated[i].copy(status = TransactionStatus.APPROVED.name, approvedBy = currentUserId)
                    }
                }
            }
            _savingsRecords.value = updated
            repository.saveLocalSavings(updated)
            if (record.amount > 0.0) {
                val prevBalance = _fundLedger.value.lastOrNull()?.balance ?: 0.0
                val newLedger = _fundLedger.value + FundLedgerEntry(
                    id = "ledger_app_${record.id}_${System.currentTimeMillis()}",
                    date = record.collectionDate,
                    description = "${record.collectionDate} সঞ্চয় (${record.memberName})",
                    moneyIn = record.amount,
                    moneyOut = 0.0,
                    balance = prevBalance + record.amount,
                    referenceId = record.id,
                    type = LedgerType.SAVINGS_COLLECTION.name,
                    createdBy = currentUserId
                )
                _fundLedger.value = newLedger
                repository.saveLocalFundLedger(newLedger)
            }
            onSuccess()
            return
        }
        viewModelScope.launch {
            try {
                if (allocationResult != null && allocationResult.catchUpDates.isNotEmpty() && member != null) {
                    val recordsToWrite = allocationResult.allocations.map { alloc ->
                        record.copy(
                            id = "${member.id}_${alloc.collectionDate.replace("/", "-")}",
                            collectionDate = alloc.collectionDate,
                            amount = alloc.newTotalAmount,
                            isPaid = alloc.newTotalAmount > 0.0,
                            status = TransactionStatus.APPROVED.name,
                            approvedBy = currentUserId
                        )
                    }
                    repository.recordSavingsBatchEntries(
                        primaryDate = record.collectionDate,
                        recordsToWrite = recordsToWrite,
                        netNewCashCollected = record.amount,
                        isOfficer = false
                    )
                } else {
                    repository.approveSavings(record)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "অনুমোদন করতে সমস্যা হয়েছে")
            }
        }
    }

    fun rejectSavings(record: SavingsRecord, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != UserRole.ADMIN) {
            onError("প্রত্যাখ্যান শুধুমাত্র অ্যাডমিন করতে পারবেন")
            return
        }
        if (repository.getCurrentUser() == null) {
            val updated = _savingsRecords.value.map {
                if (it.id == record.id) {
                    it.copy(status = TransactionStatus.REJECTED.name, approvedBy = currentUserId)
                } else it
            }
            _savingsRecords.value = updated
            repository.saveLocalSavings(updated)
            onSuccess()
            return
        }
        viewModelScope.launch {
            try {
                repository.rejectSavings(record)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "প্রত্যাখ্যান করতে ব্যর্থ")
            }
        }
    }

    fun addCollectionDate(dateStr: String, monthYear: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != UserRole.ADMIN) {
            onError("নতুন কালেকশন তারিখ শুধুমাত্র অ্যাডমিন যোগ করতে পারবেন")
            return
        }
        val newPeriod = CollectionPeriod(
            id = "period_${dateStr.replace("/", "-")}",
            dateStr = dateStr,
            monthYear = monthYear,
            isClosed = false,
            totalSavings = 0.0
        )
        if (_collectionPeriods.value.none { it.dateStr == dateStr }) {
            val updated = _collectionPeriods.value + newPeriod
            _collectionPeriods.value = updated
            if (repository.getCurrentUser() == null) {
                repository.saveLocalCollectionPeriods(updated)
            }
        }
        onSuccess()
        viewModelScope.launch {
            try {
                if (repository.getCurrentUser() != null) {
                    repository.addCollectionPeriod(dateStr, monthYear)
                }
            } catch (e: Exception) {
                onError(e.message ?: "তারিখ যোগ করতে সমস্যা হয়েছে")
            }
        }
    }

    fun updateCollectionDate(
        oldDate: String,
        newDate: String,
        monthYear: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_userRole.value != UserRole.ADMIN) {
            onError("তারিখ আপডেট শুধুমাত্র অ্যাডমিন করতে পারবেন")
            return
        }
        val cleanOld = oldDate.trim()
        val cleanNew = newDate.trim()
        if (cleanNew.isBlank() || com.example.util.SavingsAndFineCalculator.parseDateMillis(cleanNew) == null) {
            onError("সঠিক তারিখ দিন (যেমন: 14/11/26)")
            return
        }
        if (cleanOld == cleanNew) {
            onSuccess()
            return
        }
        val existingDates = (_collectionPeriods.value.map { it.dateStr } + _savingsRecords.value.map { it.collectionDate }).distinct()
        if (cleanNew in existingDates) {
            onError("এই তারিখটি ($cleanNew) ইতিমধ্যেই শিটে রয়েছে")
            return
        }

        val updatedPeriods = if (_collectionPeriods.value.any { it.dateStr == cleanOld }) {
            _collectionPeriods.value.map {
                if (it.dateStr == cleanOld) it.copy(dateStr = cleanNew) else it
            }
        } else {
            _collectionPeriods.value + CollectionPeriod(
                id = "period_${cleanNew.replace("/", "-")}",
                dateStr = cleanNew,
                monthYear = monthYear,
                isClosed = false,
                totalSavings = 0.0
            )
        }

        val oldIdsToDelete = mutableListOf<String>()
        val changedSavings = mutableListOf<SavingsRecord>()
        val updatedSavings = _savingsRecords.value.map { rec ->
            if (rec.collectionDate == cleanOld) {
                val newId = "${rec.memberId}_${cleanNew.replace("/", "-")}"
                if (rec.id.isNotBlank() && rec.id != newId) {
                    oldIdsToDelete.add(rec.id)
                }
                val updated = rec.copy(id = newId, collectionDate = cleanNew)
                changedSavings.add(updated)
                updated
            } else rec
        }

        val changedFines = mutableListOf<FineRecord>()
        val updatedFines = _fines.value.map { fine ->
            if (fine.fineType == FineType.SAVINGS.name && (fine.missedDate == cleanOld || fine.fineDate == cleanOld)) {
                val updated = fine.copy(
                    missedDate = if (fine.missedDate == cleanOld) cleanNew else fine.missedDate,
                    fineDate = if (fine.fineDate == cleanOld) cleanNew else fine.fineDate,
                    referenceId = if (fine.referenceId == cleanOld) cleanNew else fine.referenceId
                )
                changedFines.add(updated)
                updated
            } else fine
        }

        val changedLedger = mutableListOf<FundLedgerEntry>()
        val updatedLedger = _fundLedger.value.map { entry ->
            if (entry.type == LedgerType.SAVINGS_COLLECTION.name && entry.date == cleanOld) {
                val updatedDesc = if (entry.description.startsWith(cleanOld)) {
                    entry.description.replaceFirst(cleanOld, cleanNew)
                } else entry.description
                val updated = entry.copy(date = cleanNew, description = updatedDesc)
                changedLedger.add(updated)
                updated
            } else entry
        }

        _collectionPeriods.value = updatedPeriods
        _savingsRecords.value = updatedSavings
        _fines.value = updatedFines
        _fundLedger.value = updatedLedger

        if (repository.getCurrentUser() == null) {
            repository.saveLocalCollectionPeriods(updatedPeriods)
            repository.saveLocalSavings(updatedSavings)
            repository.saveLocalFines(updatedFines)
            repository.saveLocalFundLedger(updatedLedger)
            onSuccess()
            return
        }

        viewModelScope.launch {
            try {
                repository.updateCollectionPeriodDate(
                    oldDate = cleanOld,
                    newDate = cleanNew,
                    monthYear = monthYear,
                    updatedRecords = changedSavings,
                    oldRecordIdsToDelete = oldIdsToDelete,
                    updatedFines = changedFines,
                    updatedLedgerEntries = changedLedger
                )
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "তারিখ আপডেট করতে সমস্যা হয়েছে")
            }
        }
    }

    // Loans actions
    fun createLoan(
        loan: Loan,
        alsoAddMember: Member? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_userRole.value != UserRole.ADMIN) {
            onError("শুধুমাত্র অ্যাডমিন নতুন ঋণ বিতরণ করতে পারবেন")
            return
        }
        if (loan.memberName.isBlank()) {
            onError("অনুগ্রহ করে সদস্যের নাম নির্বাচন বা লিখুন")
            return
        }
        if (loan.principal <= 0.0) {
            onError("মূল ঋণের পরিমাণ শূন্যের বেশি হতে হবে")
            return
        }
        if (loan.interest < 0.0) {
            onError("সুদের পরিমাণ ঋণাত্মক হতে পারবে না")
            return
        }
        if (loan.weeklyInstallment <= 0.0) {
            onError("সাপ্তাহিক কিস্তির পরিমাণ শূন্যের বেশি হতে হবে")
            return
        }
        if (loan.loanDate.isBlank()) {
            onError("ঋণের তারিখ প্রদান করুন")
            return
        }
        val duplicateLoanSameWeek = _loans.value.any { existing ->
            val sameMember = (loan.memberId.isNotBlank() && existing.memberId == loan.memberId) ||
                existing.memberName.trim().equals(loan.memberName.trim(), ignoreCase = true)
            sameMember && com.example.util.SavingsAndFineCalculator.isSameWeek(existing.loanDate, loan.loanDate)
        }
        if (duplicateLoanSameWeek) {
            onError("এই সদস্যের জন্য এই সপ্তাহে (${loan.loanDate}) ইতিমধ্যেই ঋণ বিতরণ করা হয়েছে। এক সপ্তাহে দুইবার সাবমিট করা যাবে না।")
            return
        }

        val resolvedMemberId = loan.memberId.ifBlank { "loan_mem_${System.currentTimeMillis()}" }
        val resolvedSerial = loan.memberSerial.ifBlank {
            "ঋণ-${com.example.util.BengaliUtils.toBengaliDigits(_loans.value.size + 1)}"
        }
        val totalPayable = com.example.util.LoanCalculator.calculateTotalPayable(loan.principal, loan.interest)
        val nextDate = loan.nextInstallmentDate.ifBlank {
            com.example.util.BengaliUtils.addDaysToDateStr(loan.loanDate, 7)
        }
        val normalizedLoan = loan.copy(
            memberId = resolvedMemberId,
            memberSerial = resolvedSerial,
            totalPayable = totalPayable,
            remainingLoan = totalPayable,
            totalPaid = 0.0,
            nextInstallmentDate = nextDate,
            status = LoanStatus.ACTIVE.name
        )

        viewModelScope.launch {
            _isSavingLoan.value = true
            try {
                if (alsoAddMember != null) {
                    val memberToSave = alsoAddMember.copy(
                        id = resolvedMemberId,
                        memberSerial = resolvedSerial,
                        createdBy = currentUserId
                    )
                    val updatedMembers = (_members.value + memberToSave).sortedBy {
                        it.memberSerial.filter { ch -> ch.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE
                    }
                    _members.value = updatedMembers
                    _membersUiState.value = UiState.Success(updatedMembers)
                    if (repository.getCurrentUser() == null) {
                        repository.saveLocalMembers(updatedMembers)
                    } else {
                        repository.addMember(memberToSave)
                    }
                }

                if (repository.getCurrentUser() != null) {
                    val createdId = repository.createLoan(normalizedLoan)
                    if (_loans.value.none { it.id == createdId }) {
                        _loans.value = listOf(normalizedLoan.copy(id = createdId)) + _loans.value
                    }
                    onSuccess()
                } else {
                    // Local AuthSession Admin mode
                    val localId = "loan_${System.currentTimeMillis()}"
                    val savedLoan = normalizedLoan.copy(
                        id = localId,
                        createdBy = currentUserId
                    )
                    val updatedLoans = listOf(savedLoan) + _loans.value
                    _loans.value = updatedLoans
                    repository.saveLocalLoans(updatedLoans)

                    val prevBalance = _fundLedger.value.lastOrNull()?.balance ?: 0.0
                    val newLedger = FundLedgerEntry(
                        id = "ledger_$localId",
                        date = savedLoan.loanDate,
                        description = "ঋণ বিতরণ (${savedLoan.memberName})",
                        moneyIn = 0.0,
                        moneyOut = savedLoan.principal,
                        balance = prevBalance - savedLoan.principal,
                        referenceId = localId,
                        type = LedgerType.LOAN_DISBURSEMENT.name,
                        createdBy = currentUserId
                    )
                    val updatedLedger = _fundLedger.value + newLedger
                    _fundLedger.value = updatedLedger
                    repository.saveLocalFundLedger(updatedLedger)
                    onSuccess()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "ঋণ বিতরণ এন্ট্রি ব্যর্থ হয়েছে")
            } finally {
                _isSavingLoan.value = false
            }
        }
    }

    fun recordLoanInstallment(loan: Loan, date: String, amount: Double, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (amount <= 0.0) {
            onError("কিস্তির পরিমাণ শূন্যের বেশি হতে হবে")
            return
        }
        val duplicateSameWeek = _loanInstallments.value.any { existing ->
            existing.loanId == loan.id &&
                existing.status != TransactionStatus.REJECTED.name &&
                com.example.util.SavingsAndFineCalculator.isSameWeek(existing.installmentDate, date)
        }
        if (duplicateSameWeek) {
            onError("এই ঋণের কিস্তি এই সপ্তাহে ($date) ইতিমধ্যেই সাবমিট করা হয়েছে। এক সপ্তাহে দুইবার সাবমিট করা যাবে না।")
            return
        }
        viewModelScope.launch {
            try {
                val isOfficer = _userRole.value == UserRole.FIELD_OFFICER
                if (repository.getCurrentUser() != null) {
                    repository.recordLoanInstallment(loan, date, amount, isOfficer)
                } else {
                    val instId = "inst_${System.currentTimeMillis()}"
                    val serial = com.example.util.BengaliUtils.toBengaliDigits(
                        _loanInstallments.value.count { it.loanId == loan.id } + 1
                    )
                    val status = if (isOfficer) TransactionStatus.PENDING.name else TransactionStatus.APPROVED.name
                    val newInst = LoanInstallment(
                        id = instId,
                        loanId = loan.id,
                        memberId = loan.memberId,
                        memberName = loan.memberName,
                        serialNumber = serial,
                        installmentDate = date,
                        amount = amount,
                        officerId = currentUserId,
                        status = status,
                        createdBy = currentUserId,
                        approvedBy = if (status == TransactionStatus.APPROVED.name) currentUserId else ""
                    )
                    val updatedInsts = _loanInstallments.value + newInst
                    _loanInstallments.value = updatedInsts
                    repository.saveLocalLoanInstallments(updatedInsts)

                    if (!isOfficer) {
                        val nextDate = com.example.util.BengaliUtils.addDaysToDateStr(date, 7)
                        val updatedLoans = _loans.value.map { existing ->
                            if (existing.id == loan.id) {
                                val catchUp = com.example.util.SavingsAndFineCalculator.calculateLoanCatchUp(existing, amount)
                                val newStatus = if (catchUp.isFullyPaid) LoanStatus.PAID.name else LoanStatus.ACTIVE.name
                                existing.copy(
                                    totalPaid = catchUp.newTotalPaid,
                                    remainingLoan = catchUp.newRemainingLoan,
                                    missedInstallments = catchUp.remainingMissedInstallments,
                                    nextInstallmentDate = nextDate,
                                    status = newStatus
                                )
                            } else existing
                        }
                        _loans.value = updatedLoans
                        repository.saveLocalLoans(updatedLoans)

                        val prevBalance = _fundLedger.value.lastOrNull()?.balance ?: 0.0
                        val updatedLedger = _fundLedger.value + FundLedgerEntry(
                            id = "ledger_$instId",
                            date = date,
                            description = "ঋণের কিস্তি আদায় (${loan.memberName})",
                            moneyIn = amount,
                            moneyOut = 0.0,
                            balance = prevBalance + amount,
                            referenceId = instId,
                            type = LedgerType.LOAN_INSTALLMENT.name,
                            createdBy = currentUserId
                        )
                        _fundLedger.value = updatedLedger
                        repository.saveLocalFundLedger(updatedLedger)
                    }
                }
                if (isOfficer) {
                    val officerDisplay = _currentUserProfile.value?.displayName ?: "মাঠ কর্মকর্তা"
                    notifyAdminOfOfficerSubmission(
                        title = "নতুন ঋণ কিস্তি অনুমোদনের অনুরোধ",
                        body = "$officerDisplay সদস্য ${loan.memberName}-এর ৳${amount.toInt()} ঋণের কিস্তি ($date) পাঠিয়েছেন।",
                        transactionType = "LOAN_INSTALLMENT",
                        transactionId = loan.id,
                        memberName = loan.memberName,
                        amount = amount,
                        officerName = officerDisplay
                    )
                }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "কিস্তি এন্ট্রি ব্যর্থ")
            }
        }
    }

    fun recordMissedLoanInstallment(loan: Loan, missedDate: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != UserRole.ADMIN) {
            onError("শুধুমাত্র অ্যাডমিন ঋণের বকেয়া ও জরিমানা আপডেট করতে পারবেন")
            return
        }
        viewModelScope.launch {
            try {
                val officerName = _currentUserProfile.value?.displayName ?: "অ্যাডমিন"
                val nextDueDate = com.example.util.BengaliUtils.addDaysToDateStr(missedDate, 7)
                val fineRecord = com.example.util.SavingsAndFineCalculator.buildLoanFineRecord(
                    loan = loan,
                    missedDate = missedDate,
                    appliedDate = nextDueDate,
                    officerId = currentUserId,
                    officerName = officerName
                )

                if (repository.getCurrentUser() != null) {
                    repository.recordMissedLoanInstallment(loan, missedDate)
                    repository.upsertFinesBatch(listOf(fineRecord))
                } else {
                    val instId = "inst_miss_${System.currentTimeMillis()}"
                    val serial = com.example.util.BengaliUtils.toBengaliDigits(
                        _loanInstallments.value.count { it.loanId == loan.id } + 1
                    )
                    val missedInst = LoanInstallment(
                        id = instId,
                        loanId = loan.id,
                        memberId = loan.memberId,
                        memberName = loan.memberName,
                        serialNumber = serial,
                        installmentDate = missedDate,
                        amount = 0.0,
                        isMissed = true,
                        officerId = currentUserId,
                        status = TransactionStatus.APPROVED.name,
                        createdBy = currentUserId,
                        approvedBy = currentUserId
                    )
                    val updatedInsts = _loanInstallments.value + missedInst
                    _loanInstallments.value = updatedInsts
                    repository.saveLocalLoanInstallments(updatedInsts)

                    val updatedLoans = _loans.value.map { existing ->
                        if (existing.id == loan.id) {
                            existing.copy(
                                missedInstallments = existing.missedInstallments + 1,
                                nextInstallmentDate = nextDueDate
                            )
                        } else existing
                    }
                    _loans.value = updatedLoans
                    repository.saveLocalLoans(updatedLoans)

                    mergeGeneratedFinesLocally(listOf(fineRecord))
                }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "ঋণের বাকি ও জরিমানা সংরক্ষণ ব্যর্থ")
            }
        }
    }

    fun approveLoanInstallment(installment: LoanInstallment, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != UserRole.ADMIN) {
            onError("অনুমোদন শুধুমাত্র অ্যাডমিন করতে পারবেন")
            return
        }
        if (repository.getCurrentUser() == null) {
            val updatedInsts = _loanInstallments.value.map {
                if (it.id == installment.id) {
                    it.copy(status = TransactionStatus.APPROVED.name, approvedBy = currentUserId)
                } else it
            }
            _loanInstallments.value = updatedInsts
            repository.saveLocalLoanInstallments(updatedInsts)

            val updatedLoans = _loans.value.map { existing ->
                if (existing.id == installment.loanId) {
                    val catchUp = com.example.util.SavingsAndFineCalculator.calculateLoanCatchUp(existing, installment.amount)
                    val newStatus = if (catchUp.isFullyPaid) LoanStatus.PAID.name else LoanStatus.ACTIVE.name
                    existing.copy(
                        totalPaid = catchUp.newTotalPaid,
                        remainingLoan = catchUp.newRemainingLoan,
                        missedInstallments = catchUp.remainingMissedInstallments,
                        nextInstallmentDate = com.example.util.BengaliUtils.addDaysToDateStr(installment.installmentDate, 7),
                        status = newStatus
                    )
                } else existing
            }
            _loans.value = updatedLoans
            repository.saveLocalLoans(updatedLoans)

            val prevBalance = _fundLedger.value.lastOrNull()?.balance ?: 0.0
            val updatedLedger = _fundLedger.value + FundLedgerEntry(
                id = "ledger_app_${installment.id}_${System.currentTimeMillis()}",
                date = installment.installmentDate,
                description = "ঋণের কিস্তি আদায় (${installment.memberName})",
                moneyIn = installment.amount,
                moneyOut = 0.0,
                balance = prevBalance + installment.amount,
                referenceId = installment.id,
                type = LedgerType.LOAN_INSTALLMENT.name,
                createdBy = currentUserId
            )
            _fundLedger.value = updatedLedger
            repository.saveLocalFundLedger(updatedLedger)
            onSuccess()
            return
        }
        viewModelScope.launch {
            try {
                repository.approveLoanInstallment(installment)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "অনুমোদন ব্যর্থ")
            }
        }
    }

    // Fine Sheet actions (10 BDT separate from principal savings & loans)
    fun addManualFine(
        member: Member,
        fineType: FineType,
        missedDate: String,
        fineDate: String,
        amount: Double = com.example.util.SavingsAndFineCalculator.DEFAULT_WEEKLY_FINE_AMOUNT,
        isPaid: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val officerName = _currentUserProfile.value?.displayName ?: "অ্যাডমিন"
        val fineId = "fine_${fineType.name.lowercase()}_${member.id}_${fineDate.replace("/", "-")}_${System.currentTimeMillis()}"
        val newFine = FineRecord(
            id = fineId,
            memberId = member.id,
            memberName = member.name,
            memberSerial = member.memberSerial,
            fineDate = fineDate,
            missedDate = missedDate.ifBlank { fineDate },
            fineType = fineType.name,
            amount = amount,
            isPaid = isPaid,
            paidDate = if (isPaid) com.example.util.BengaliUtils.getCurrentDateStr() else "",
            referenceId = "",
            officerId = currentUserId,
            officerName = officerName,
            status = TransactionStatus.APPROVED.name,
            createdBy = currentUserId
        )

        val updated = _fines.value + newFine
        _fines.value = updated
        if (repository.getCurrentUser() == null) {
            repository.saveLocalFines(updated)
            onSuccess()
            return
        }
        onSuccess()
        viewModelScope.launch {
            try {
                repository.upsertFinesBatch(listOf(newFine))
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "জরিমানা সংরক্ষণে সমস্যা হয়েছে")
            }
        }
    }

    fun toggleFinePaid(fine: FineRecord, isPaid: Boolean, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (_userRole.value != UserRole.ADMIN) {
            onError("জরিমানা সংশোধন শুধুমাত্র অ্যাডমিন করতে পারবেন")
            return
        }
        val today = com.example.util.BengaliUtils.getCurrentDateStr()
        val updated = _fines.value.map {
            if (it.id == fine.id) it.copy(isPaid = isPaid, paidDate = if (isPaid) today else "") else it
        }
        _fines.value = updated
        if (repository.getCurrentUser() == null) {
            repository.saveLocalFines(updated)
            onSuccess()
            return
        }
        onSuccess()
        viewModelScope.launch {
            try {
                repository.updateFinePaymentStatus(fine.id, isPaid, today)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "জরিমানা আপডেট ব্যর্থ")
            }
        }
    }

    private fun isAuthorizedAdminForDelete(): Boolean {
        val sessionRole = _currentUserProfile.value?.role ?: AuthSession.currentUserProfile?.role
        return _userRole.value == UserRole.ADMIN && sessionRole == UserRole.ADMIN.name
    }

    fun deleteFine(fineId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!isAuthorizedAdminForDelete()) {
            onError("জরিমানা ডিলিট করার অনুমতি শুধুমাত্র অ্যাডমিনের আছে")
            return
        }
        if (repository.getCurrentUser() == null) {
            val updated = _fines.value.filterNot { it.id == fineId }
            _fines.value = updated
            repository.saveLocalFines(updated)
            onSuccess()
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteFine(fineId)
                _fines.value = _fines.value.filterNot { it.id == fineId }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "জরিমানা ডিলিট করতে সমস্যা হয়েছে")
            }
        }
    }

    fun deleteMember(memberId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!isAuthorizedAdminForDelete()) {
            onError("সদস্য ডিলিট করার অনুমতি শুধুমাত্র অ্যাডমিনের আছে")
            return
        }
        if (repository.getCurrentUser() == null) {
            val updated = _members.value.filterNot { it.id == memberId }
            _members.value = updated
            _membersUiState.value = UiState.Success(updated)
            repository.saveLocalMembers(updated)
            onSuccess()
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteMember(memberId)
                val updated = _members.value.filterNot { it.id == memberId }
                _members.value = updated
                _membersUiState.value = UiState.Success(updated)
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "সদস্য ডিলিট করতে সমস্যা হয়েছে")
            }
        }
    }

    fun deleteSavingsRecord(recordId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!isAuthorizedAdminForDelete()) {
            onError("সঞ্চয় এন্ট্রি ডিলিট করার অনুমতি শুধুমাত্র অ্যাডমিনের আছে")
            return
        }
        if (repository.getCurrentUser() == null) {
            val updated = _savingsRecords.value.filterNot { it.id == recordId }
            _savingsRecords.value = updated
            repository.saveLocalSavings(updated)
            onSuccess()
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteSavingsRecord(recordId)
                _savingsRecords.value = _savingsRecords.value.filterNot { it.id == recordId }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "সঞ্চয় এন্ট্রি ডিলিট করতে সমস্যা হয়েছে")
            }
        }
    }

    fun deleteLoan(loanId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!isAuthorizedAdminForDelete()) {
            onError("ঋণ এন্ট্রি ডিলিট করার অনুমতি শুধুমাত্র অ্যাডমিনের আছে")
            return
        }
        if (repository.getCurrentUser() == null) {
            val updated = _loans.value.filterNot { it.id == loanId }
            _loans.value = updated
            repository.saveLocalLoans(updated)
            onSuccess()
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteLoan(loanId)
                _loans.value = _loans.value.filterNot { it.id == loanId }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "ঋণ ডিলিট করতে সমস্যা হয়েছে")
            }
        }
    }

    fun deleteLoanInstallment(installmentId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!isAuthorizedAdminForDelete()) {
            onError("কিস্তি এন্ট্রি ডিলিট করার অনুমতি শুধুমাত্র অ্যাডমিনের আছে")
            return
        }
        if (repository.getCurrentUser() == null) {
            val updated = _loanInstallments.value.filterNot { it.id == installmentId }
            _loanInstallments.value = updated
            repository.saveLocalLoanInstallments(updated)
            onSuccess()
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteLoanInstallment(installmentId)
                _loanInstallments.value = _loanInstallments.value.filterNot { it.id == installmentId }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "কিস্তি ডিলিট করতে সমস্যা হয়েছে")
            }
        }
    }

    fun deleteLedgerEntry(entryId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!isAuthorizedAdminForDelete()) {
            onError("লেজার এন্ট্রি ডিলিট করার অনুমতি শুধুমাত্র অ্যাডমিনের আছে")
            return
        }
        if (repository.getCurrentUser() == null) {
            val updated = _fundLedger.value.filterNot { it.id == entryId }
            _fundLedger.value = updated
            repository.saveLocalFundLedger(updated)
            onSuccess()
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteLedgerEntry(entryId)
                _fundLedger.value = _fundLedger.value.filterNot { it.id == entryId }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "লেজার এন্ট্রি ডিলিট করতে সমস্যা হয়েছে")
            }
        }
    }

    // Ledger actions
    fun addLedgerEntry(date: String, desc: String, moneyIn: Double, moneyOut: Double, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != UserRole.ADMIN) {
            onError("লেজার এন্ট্রি শুধুমাত্র অ্যাডমিন করতে পারবেন")
            return
        }
        if (repository.getCurrentUser() == null) {
            val prevBalance = _fundLedger.value.lastOrNull()?.balance ?: 0.0
            val updatedLedger = _fundLedger.value + FundLedgerEntry(
                id = "ledger_${System.currentTimeMillis()}",
                date = date,
                description = desc,
                moneyIn = moneyIn,
                moneyOut = moneyOut,
                balance = prevBalance + moneyIn - moneyOut,
                type = if (moneyOut > 0.0 && moneyIn == 0.0) LedgerType.EXPENSE.name else LedgerType.DEPOSIT.name,
                createdBy = currentUserId
            )
            _fundLedger.value = updatedLedger
            repository.saveLocalFundLedger(updatedLedger)
            onSuccess()
            return
        }
        viewModelScope.launch {
            try {
                repository.appendLedgerEntry(date, desc, moneyIn, moneyOut)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "লেজার এন্ট্রি ব্যর্থ")
            }
        }
    }

    // User management
    fun updateUserRole(userId: String, role: String, area: String, isActive: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != UserRole.ADMIN) {
            onError("ব্যবহারকারী রোল পরিবর্তন শুধুমাত্র অ্যাডমিন করতে পারবেন")
            return
        }
        viewModelScope.launch {
            try {
                repository.updateUserRole(userId, role, area, isActive)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "রোল আপডেট ব্যর্থ")
            }
        }
    }

    fun updateSettings(settings: AppSettings, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (_userRole.value != UserRole.ADMIN) {
            onError("সেটিংস পরিবর্তন শুধুমাত্র অ্যাডমিন করতে পারবেন")
            return
        }
        _appSettings.value = settings
        if (repository.getCurrentUser() == null) {
            repository.saveLocalAppSettings(settings)
        }
        onSuccess()
        viewModelScope.launch {
            try {
                if (repository.getCurrentUser() != null) {
                    repository.updateAppSettings(settings)
                }
            } catch (e: Exception) {
                onError(e.message ?: "সেটিংস আপডেট ব্যর্থ")
            }
        }
    }

    // FCM Token & Admin Approval Notification Helpers
    fun registerAdminFcmToken(token: String) {
        if (AuthSession.currentUserProfile?.role != UserRole.ADMIN.name && _userRole.value != UserRole.ADMIN) {
            return
        }
        viewModelScope.launch {
            repository.registerAdminFcmToken(token)
        }
    }

    fun markAdminNotificationRead(notificationId: String) {
        if (notificationId.isBlank()) return
        val updated = _pendingAdminNotifications.value.filterNot { it.id == notificationId }
        _pendingAdminNotifications.value = updated
        if (repository.getCurrentUser() == null) {
            val all = repository.loadLocalAdminNotifications().map {
                if (it.id == notificationId) it.copy(isRead = true) else it
            }
            repository.saveLocalAdminNotifications(all)
            return
        }
        viewModelScope.launch {
            repository.markAdminNotificationRead(notificationId)
        }
    }

    private fun notifyAdminOfOfficerSubmission(
        title: String,
        body: String,
        transactionType: String,
        transactionId: String,
        memberName: String,
        amount: Double,
        officerName: String
    ) {
        if (repository.getCurrentUser() == null) {
            val newNotif = AdminNotification(
                id = "notif_${System.currentTimeMillis()}",
                title = title,
                body = body,
                transactionType = transactionType,
                transactionId = transactionId,
                memberName = memberName,
                amount = amount,
                officerId = currentUserId,
                officerName = officerName,
                targetRole = UserRole.ADMIN.name,
                targetTopic = "admin_approvals",
                targetScreen = "APPROVALS",
                deepLink = "ekotasamiti://approvals",
                isRead = false
            )
            val all = repository.loadLocalAdminNotifications() + newNotif
            repository.saveLocalAdminNotifications(all)
            _pendingAdminNotifications.value = all.filter { !it.isRead }
            return
        }

        viewModelScope.launch {
            try {
                repository.enqueueAdminApprovalNotification(
                    title = title,
                    body = body,
                    transactionType = transactionType,
                    transactionId = transactionId,
                    memberName = memberName,
                    amount = amount,
                    officerName = officerName
                )
            } catch (_: Exception) {
                // Non-fatal
            }
        }
    }

    fun resetAllDataToZero(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isAuthorizedAdminForDelete()) {
            onError("সব ডাটা রিসেট করার অনুমতি শুধুমাত্র অ্যাডমিনের আছে")
            return
        }

        viewModelScope.launch {
            try {
                if (repository.getCurrentUser() != null) {
                    if (!repository.isOnline()) {
                        onError("ইন্টারনেট সংযোগ ছাড়া ক্লাউড ডাটা রিসেট করা যাবে না")
                        return@launch
                    }
                    repository.resetAllCloudData()
                }
                repository.clearAllLocalData()

                _members.value = emptyList()
                _membersUiState.value = UiState.Success(emptyList())
                _savingsRecords.value = emptyList()
                _collectionPeriods.value = emptyList()
                _loans.value = emptyList()
                _loanInstallments.value = emptyList()
                _fines.value = emptyList()
                _fundLedger.value = emptyList()
                _pendingAdminNotifications.value = emptyList()
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "সব ডাটা রিসেট করতে সমস্যা হয়েছে")
            }
        }
    }
}

class MainViewModelFactory(
    private val repository: EkotaRepository,
    private val currentUserId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(repository, currentUserId) as T
    }
}
