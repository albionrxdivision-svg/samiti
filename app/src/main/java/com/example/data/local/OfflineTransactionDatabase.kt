package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.example.data.model.Loan
import com.example.data.model.LoanInstallment
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import kotlinx.coroutines.flow.Flow

object OfflineTransactionType {
    const val SAVINGS = "SAVINGS"
    const val LOAN_INSTALLMENT = "LOAN_INSTALLMENT"
}

object OfflineSyncStatus {
    const val PENDING = "PENDING"
    const val SYNCING = "SYNCING"
    const val SYNCED = "SYNCED"
    const val FAILED = "FAILED"
}

@Entity(tableName = "pending_offline_transactions")
data class PendingOfflineTransactionEntity(
    @PrimaryKey val id: String,
    val transactionType: String,
    val memberId: String,
    val memberName: String,
    val memberSerial: String,
    val loanId: String,
    val collectionDate: String,
    val weekKey: Long,
    val amount: Double,
    val officerId: String,
    val officerName: String,
    val isOfficerSubmission: Boolean,
    val syncStatus: String = OfflineSyncStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val syncedAtMillis: Long = 0L
) {
    fun toSavingsRecord(): SavingsRecord = SavingsRecord(
        id = id,
        memberId = memberId,
        memberName = memberName,
        memberSerial = memberSerial,
        collectionDate = collectionDate,
        amount = amount,
        isPaid = amount > 0.0,
        officerId = officerId,
        officerName = officerName,
        status = if (isOfficerSubmission) TransactionStatus.PENDING.name else TransactionStatus.APPROVED.name,
        createdBy = officerId,
        approvedBy = if (isOfficerSubmission) "" else officerId
    )

    fun toLoanInstallment(serialNumber: String = "১"): LoanInstallment = LoanInstallment(
        id = id,
        loanId = loanId,
        memberId = memberId,
        memberName = memberName,
        serialNumber = serialNumber,
        installmentDate = collectionDate,
        amount = amount,
        isMissed = false,
        officerId = officerId,
        officerName = officerName,
        status = if (isOfficerSubmission) TransactionStatus.PENDING.name else TransactionStatus.APPROVED.name,
        createdBy = officerId,
        approvedBy = if (isOfficerSubmission) "" else officerId
    )
}

@Entity(tableName = "cached_members")
data class CachedMemberEntity(
    @PrimaryKey val id: String,
    val memberSerial: String,
    val name: String,
    val mobile: String,
    val address: String,
    val area: String,
    val joinDate: String,
    val weeklySavingsAmount: Double,
    val assignedOfficerId: String,
    val assignedOfficerName: String,
    val isActive: Boolean,
    val createdBy: String
) {
    fun toModel(): Member = Member(
        id = id,
        memberSerial = memberSerial,
        name = name,
        mobile = mobile,
        address = address,
        area = area,
        joinDate = joinDate,
        weeklySavingsAmount = weeklySavingsAmount,
        assignedOfficerId = assignedOfficerId,
        assignedOfficerName = assignedOfficerName,
        isActive = isActive,
        createdBy = createdBy
    )

    companion object {
        fun fromModel(m: Member): CachedMemberEntity = CachedMemberEntity(
            id = m.id,
            memberSerial = m.memberSerial,
            name = m.name,
            mobile = m.mobile,
            address = m.address,
            area = m.area,
            joinDate = m.joinDate,
            weeklySavingsAmount = m.weeklySavingsAmount,
            assignedOfficerId = m.assignedOfficerId,
            assignedOfficerName = m.assignedOfficerName,
            isActive = m.isActive,
            createdBy = m.createdBy
        )
    }
}

@Entity(tableName = "cached_loans")
data class CachedLoanEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val memberName: String,
    val memberSerial: String,
    val loanDate: String,
    val principal: Double,
    val interest: Double,
    val totalPayable: Double,
    val weeklyInstallment: Double,
    val totalPaid: Double,
    val remainingLoan: Double,
    val nextInstallmentDate: String,
    val missedInstallments: Int,
    val status: String,
    val createdBy: String
) {
    fun toModel(): Loan = Loan(
        id = id,
        memberId = memberId,
        memberName = memberName,
        memberSerial = memberSerial,
        loanDate = loanDate,
        principal = principal,
        interest = interest,
        totalPayable = totalPayable,
        weeklyInstallment = weeklyInstallment,
        totalPaid = totalPaid,
        remainingLoan = remainingLoan,
        nextInstallmentDate = nextInstallmentDate,
        missedInstallments = missedInstallments,
        status = status,
        createdBy = createdBy
    )

    companion object {
        fun fromModel(l: Loan): CachedLoanEntity = CachedLoanEntity(
            id = l.id,
            memberId = l.memberId,
            memberName = l.memberName,
            memberSerial = l.memberSerial,
            loanDate = l.loanDate,
            principal = l.principal,
            interest = l.interest,
            totalPayable = l.totalPayable,
            weeklyInstallment = l.weeklyInstallment,
            totalPaid = l.totalPaid,
            remainingLoan = l.remainingLoan,
            nextInstallmentDate = l.nextInstallmentDate,
            missedInstallments = l.missedInstallments,
            status = l.status,
            createdBy = l.createdBy
        )
    }
}

@Dao
interface OfflineTransactionDao {
    @Query("SELECT * FROM pending_offline_transactions WHERE syncStatus != 'SYNCED' ORDER BY createdAtMillis ASC")
    fun observeUnsyncedTransactions(): Flow<List<PendingOfflineTransactionEntity>>

    @Query("SELECT COUNT(*) FROM pending_offline_transactions WHERE syncStatus != 'SYNCED'")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT * FROM pending_offline_transactions WHERE syncStatus != 'SYNCED' ORDER BY createdAtMillis ASC")
    suspend fun getUnsyncedTransactions(): List<PendingOfflineTransactionEntity>

    @Query("SELECT * FROM pending_offline_transactions WHERE transactionType = :type AND memberId = :memberId AND weekKey = :weekKey AND weekKey > 0 LIMIT 1")
    suspend fun findByMemberAndWeek(type: String, memberId: String, weekKey: Long): PendingOfflineTransactionEntity?

    @Query("SELECT * FROM pending_offline_transactions WHERE transactionType = 'LOAN_INSTALLMENT' AND loanId = :loanId AND weekKey = :weekKey AND weekKey > 0 LIMIT 1")
    suspend fun findInstallmentByLoanAndWeek(loanId: String, weekKey: Long): PendingOfflineTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(entity: PendingOfflineTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransactions(entities: List<PendingOfflineTransactionEntity>)

    @Query("UPDATE pending_offline_transactions SET syncStatus = :status, retryCount = :retryCount, lastError = :error, syncedAtMillis = :syncedAtMillis WHERE id = :id")
    suspend fun updateSyncStatus(
        id: String,
        status: String,
        retryCount: Int,
        error: String,
        syncedAtMillis: Long
    )

    @Query("DELETE FROM pending_offline_transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("DELETE FROM pending_offline_transactions")
    suspend fun clearAllPending()

    @Query("SELECT * FROM cached_members")
    fun observeCachedMembers(): Flow<List<CachedMemberEntity>>

    @Query("SELECT * FROM cached_members")
    suspend fun getCachedMembers(): List<CachedMemberEntity>

    @Query("DELETE FROM cached_members")
    suspend fun clearCachedMembers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedMembers(members: List<CachedMemberEntity>)

    @Transaction
    suspend fun replaceCachedMembers(members: List<CachedMemberEntity>) {
        clearCachedMembers()
        if (members.isNotEmpty()) insertCachedMembers(members)
    }

    @Query("SELECT * FROM cached_loans")
    fun observeCachedLoans(): Flow<List<CachedLoanEntity>>

    @Query("SELECT * FROM cached_loans")
    suspend fun getCachedLoans(): List<CachedLoanEntity>

    @Query("DELETE FROM cached_loans")
    suspend fun clearCachedLoans()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedLoans(loans: List<CachedLoanEntity>)

    @Transaction
    suspend fun replaceCachedLoans(loans: List<CachedLoanEntity>) {
        clearCachedLoans()
        if (loans.isNotEmpty()) insertCachedLoans(loans)
    }
}

@Database(
    entities = [
        PendingOfflineTransactionEntity::class,
        CachedMemberEntity::class,
        CachedLoanEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class OfflineTransactionDatabase : RoomDatabase() {
    abstract fun offlineTransactionDao(): OfflineTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: OfflineTransactionDatabase? = null

        fun getInstance(context: Context): OfflineTransactionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OfflineTransactionDatabase::class.java,
                    "ekota_offline_sync.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
