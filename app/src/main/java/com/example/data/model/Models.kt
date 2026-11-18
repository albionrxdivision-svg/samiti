package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole {
    ADMIN,
    FIELD_OFFICER
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = UserRole.FIELD_OFFICER.name,
    val assignedOfficerId: String = "",
    val assignedArea: String = "",
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Member(
    @DocumentId val id: String = "",
    val memberSerial: String = "",
    val name: String = "",
    val mobile: String = "",
    val address: String = "",
    val area: String = "",
    val joinDate: String = "",
    val weeklySavingsAmount: Double = 100.0,
    val assignedOfficerId: String = "",
    val assignedOfficerName: String = "",
    val isActive: Boolean = true,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Officer(
    @DocumentId val id: String = "",
    val officerId: String = "",
    val name: String = "",
    val email: String = "",
    val mobile: String = "",
    val assignedArea: String = "",
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class TransactionStatus {
    PENDING,
    APPROVED,
    REJECTED
}

@IgnoreExtraProperties
data class SavingsRecord(
    @DocumentId val id: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val memberSerial: String = "",
    val collectionDate: String = "",
    val amount: Double = 0.0,
    val isPaid: Boolean = true,
    val officerId: String = "",
    val officerName: String = "",
    val status: String = TransactionStatus.APPROVED.name,
    val createdBy: String = "",
    val approvedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class LoanStatus {
    ACTIVE,
    PAID,
    DEFAULTED
}

@IgnoreExtraProperties
data class Loan(
    @DocumentId val id: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val memberSerial: String = "",
    val loanDate: String = "",
    val principal: Double = 0.0,
    val interest: Double = 0.0,
    val totalPayable: Double = 0.0,
    val weeklyInstallment: Double = 0.0,
    val totalPaid: Double = 0.0,
    val remainingLoan: Double = 0.0,
    val nextInstallmentDate: String = "",
    val missedInstallments: Int = 0,
    val status: String = LoanStatus.ACTIVE.name,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LoanInstallment(
    @DocumentId val id: String = "",
    val loanId: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val serialNumber: String = "",
    val installmentDate: String = "",
    val amount: Double = 0.0,
    val isMissed: Boolean = false,
    val officerId: String = "",
    val officerName: String = "",
    val status: String = TransactionStatus.APPROVED.name,
    val createdBy: String = "",
    val approvedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class FineType {
    SAVINGS,
    LOAN
}

@IgnoreExtraProperties
data class FineRecord(
    @DocumentId val id: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val memberSerial: String = "",
    val fineDate: String = "",
    val missedDate: String = "",
    val fineType: String = FineType.SAVINGS.name,
    val amount: Double = 10.0,
    val isPaid: Boolean = false,
    val paidDate: String = "",
    val referenceId: String = "",
    val officerId: String = "",
    val officerName: String = "",
    val status: String = TransactionStatus.APPROVED.name,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class LedgerType {
    SAVINGS_COLLECTION,
    LOAN_INSTALLMENT,
    LOAN_DISBURSEMENT,
    WITHDRAWAL,
    EXPENSE,
    DEPOSIT,
    INITIAL
}

@IgnoreExtraProperties
data class FundLedgerEntry(
    @DocumentId val id: String = "",
    val date: String = "",
    val description: String = "",
    val moneyIn: Double = 0.0,
    val moneyOut: Double = 0.0,
    val balance: Double = 0.0,
    val referenceId: String = "",
    val type: String = LedgerType.DEPOSIT.name,
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CollectionPeriod(
    @DocumentId val id: String = "",
    val dateStr: String = "",
    val monthYear: String = "November 2026",
    val isClosed: Boolean = false,
    val totalSavings: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val action: String = "",
    val collection: String = "",
    val documentId: String = "",
    val oldValue: String = "",
    val newValue: String = "",
    val performedBy: String = "",
    val performedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppSettings(
    @DocumentId val id: String = "main",
    val orgName: String = "যাদবপুর যুব সমাজ সমিতি",
    val subtitle: String = "সাপ্তাহিক সঞ্চয় ও ঋণ ব্যবস্থাপনা",
    val currency: String = "৳",
    val currentMonthHeading: String = "সাপ্তাহিক সঞ্চয়",
    val interestRatePercent: Double = 20.0,
    val appDownloadUrl: String = "",
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val updateNotes: String = "",
    val githubRepoSlug: String = "",
    val autoUpdateEnabled: Boolean = true,
    val keepPermanentLink: Boolean = true,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AdminNotification(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val transactionType: String = "",
    val transactionId: String = "",
    val memberName: String = "",
    val amount: Double = 0.0,
    val officerId: String = "",
    val officerName: String = "",
    val targetRole: String = UserRole.ADMIN.name,
    val targetTopic: String = "admin_approvals",
    val targetScreen: String = "APPROVALS",
    val deepLink: String = "ekotasamiti://approvals",
    val recipientTokens: List<String> = emptyList(),
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

object AuthSession {
    private const val PREFS_NAME = "ekota_auth_session"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_ROLE = "user_role"
    private const val KEY_NAME = "display_name"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "user_email"

    var currentUserProfile: UserProfile? = null

    val isAuthenticated: Boolean
        get() = currentUserProfile != null

    val currentUserId: String?
        get() = currentUserProfile?.userId

    fun init(context: android.content.Context): UserProfile? {
        if (currentUserProfile != null) return currentUserProfile
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_IS_LOGGED_IN, false)) {
            val role = prefs.getString(KEY_ROLE, UserRole.ADMIN.name) ?: UserRole.ADMIN.name
            val name = prefs.getString(KEY_NAME, "নিতাই (অ্যাডমিন)") ?: "নিতাই (অ্যাডমিন)"
            val uid = prefs.getString(KEY_USER_ID, "admin_nitai") ?: "admin_nitai"
            val email = prefs.getString(KEY_EMAIL, "netai.nemai@gmail.com") ?: "netai.nemai@gmail.com"
            val profile = UserProfile(
                id = uid,
                userId = uid,
                email = email,
                displayName = name,
                role = role,
                assignedArea = "যাদবপুর",
                isActive = true
            )
            currentUserProfile = profile
            return profile
        }
        return null
    }

    fun setAdminSession(context: android.content.Context? = null): UserProfile {
        val profile = UserProfile(
            id = "admin_nitai",
            userId = "admin_nitai",
            email = "netai.nemai@gmail.com",
            displayName = "নিতাই (অ্যাডমিন)",
            role = UserRole.ADMIN.name,
            assignedArea = "যাদবপুর",
            isActive = true
        )
        currentUserProfile = profile
        context?.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)?.edit()?.apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_ROLE, UserRole.ADMIN.name)
            putString(KEY_NAME, profile.displayName)
            putString(KEY_USER_ID, profile.userId)
            putString(KEY_EMAIL, profile.email)
            apply()
        }
        return profile
    }

    fun setOfficerSession(name: String = "মাঠ কর্মকর্তা", context: android.content.Context? = null): UserProfile {
        val cleanName = name.trim().ifBlank { "মাঠ কর্মকর্তা" }
        val profile = UserProfile(
            id = "officer_1",
            userId = "officer_1",
            email = "officer@ekotasamiti.org",
            displayName = cleanName,
            role = UserRole.FIELD_OFFICER.name,
            assignedArea = "যাদবপুর",
            isActive = true
        )
        currentUserProfile = profile
        context?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)?.edit()?.apply {
                putBoolean(KEY_IS_LOGGED_IN, true)
                putString(KEY_ROLE, UserRole.FIELD_OFFICER.name)
                putString(KEY_NAME, profile.displayName)
                putString(KEY_USER_ID, profile.userId)
                putString(KEY_EMAIL, profile.email)
                apply()
            }
            com.example.util.PasswordManager.forContext(ctx).recordOfficerKnown(cleanName)
        }
        return profile
    }

    fun clear(context: android.content.Context? = null) {
        currentUserProfile = null
        context?.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
    }
}
