package com.example.data.local

import android.content.Context
import com.example.data.model.*
import org.json.JSONArray
import org.json.JSONObject

class LocalDataStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ekota_user_entered_data"
        private const val KEY_MEMBERS = "members"
        private const val KEY_SAVINGS = "savings_records"
        private const val KEY_PERIODS = "collection_periods"
        private const val KEY_LOANS = "loans"
        private const val KEY_INSTALLMENTS = "loan_installments"
        private const val KEY_FINES = "fines"
        private const val KEY_LEDGER = "fund_ledger"
        private const val KEY_SETTINGS = "app_settings"
        private const val KEY_ADMIN_NOTIFS = "admin_notifications"
    }

    fun loadMembers(): List<Member> {
        val raw = prefs.getString(KEY_MEMBERS, null) ?: return emptyList()
        return parseJsonArray(raw) { obj ->
            Member(
                id = obj.optString("id"),
                memberSerial = obj.optString("memberSerial"),
                name = obj.optString("name"),
                mobile = obj.optString("mobile"),
                address = obj.optString("address"),
                area = obj.optString("area"),
                joinDate = obj.optString("joinDate"),
                weeklySavingsAmount = obj.optDouble("weeklySavingsAmount", 100.0),
                assignedOfficerId = obj.optString("assignedOfficerId"),
                assignedOfficerName = obj.optString("assignedOfficerName"),
                isActive = obj.optBoolean("isActive", true),
                createdBy = obj.optString("createdBy")
            )
        }
    }

    fun saveMembers(list: List<Member>) {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("memberSerial", m.memberSerial)
                    put("name", m.name)
                    put("mobile", m.mobile)
                    put("address", m.address)
                    put("area", m.area)
                    put("joinDate", m.joinDate)
                    put("weeklySavingsAmount", m.weeklySavingsAmount)
                    put("assignedOfficerId", m.assignedOfficerId)
                    put("assignedOfficerName", m.assignedOfficerName)
                    put("isActive", m.isActive)
                    put("createdBy", m.createdBy)
                }
            )
        }
        prefs.edit().putString(KEY_MEMBERS, arr.toString()).apply()
    }

    fun loadSavings(): List<SavingsRecord> {
        val raw = prefs.getString(KEY_SAVINGS, null) ?: return emptyList()
        return parseJsonArray(raw) { obj ->
            SavingsRecord(
                id = obj.optString("id"),
                memberId = obj.optString("memberId"),
                memberName = obj.optString("memberName"),
                memberSerial = obj.optString("memberSerial"),
                collectionDate = obj.optString("collectionDate"),
                amount = obj.optDouble("amount", 0.0),
                isPaid = obj.optBoolean("isPaid", false),
                officerId = obj.optString("officerId"),
                officerName = obj.optString("officerName"),
                status = obj.optString("status", TransactionStatus.APPROVED.name),
                createdBy = obj.optString("createdBy"),
                approvedBy = obj.optString("approvedBy")
            )
        }
    }

    fun saveSavings(list: List<SavingsRecord>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("memberId", s.memberId)
                    put("memberName", s.memberName)
                    put("memberSerial", s.memberSerial)
                    put("collectionDate", s.collectionDate)
                    put("amount", s.amount)
                    put("isPaid", s.isPaid)
                    put("officerId", s.officerId)
                    put("officerName", s.officerName)
                    put("status", s.status)
                    put("createdBy", s.createdBy)
                    put("approvedBy", s.approvedBy)
                }
            )
        }
        prefs.edit().putString(KEY_SAVINGS, arr.toString()).apply()
    }

    fun loadCollectionPeriods(): List<CollectionPeriod> {
        val raw = prefs.getString(KEY_PERIODS, null) ?: return emptyList()
        return parseJsonArray(raw) { obj ->
            CollectionPeriod(
                id = obj.optString("id"),
                dateStr = obj.optString("dateStr"),
                monthYear = obj.optString("monthYear"),
                isClosed = obj.optBoolean("isClosed", false),
                totalSavings = obj.optDouble("totalSavings", 0.0)
            )
        }
    }

    fun saveCollectionPeriods(list: List<CollectionPeriod>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("dateStr", p.dateStr)
                    put("monthYear", p.monthYear)
                    put("isClosed", p.isClosed)
                    put("totalSavings", p.totalSavings)
                }
            )
        }
        prefs.edit().putString(KEY_PERIODS, arr.toString()).apply()
    }

    fun loadLoans(): List<Loan> {
        val raw = prefs.getString(KEY_LOANS, null) ?: return emptyList()
        return parseJsonArray(raw) { obj ->
            Loan(
                id = obj.optString("id"),
                memberId = obj.optString("memberId"),
                memberName = obj.optString("memberName"),
                memberSerial = obj.optString("memberSerial"),
                loanDate = obj.optString("loanDate"),
                principal = obj.optDouble("principal", 0.0),
                interest = obj.optDouble("interest", 0.0),
                totalPayable = obj.optDouble("totalPayable", 0.0),
                weeklyInstallment = obj.optDouble("weeklyInstallment", 0.0),
                totalPaid = obj.optDouble("totalPaid", 0.0),
                remainingLoan = obj.optDouble("remainingLoan", 0.0),
                nextInstallmentDate = obj.optString("nextInstallmentDate"),
                missedInstallments = obj.optInt("missedInstallments", 0),
                status = obj.optString("status", LoanStatus.ACTIVE.name),
                createdBy = obj.optString("createdBy")
            )
        }
    }

    fun saveLoans(list: List<Loan>) {
        val arr = JSONArray()
        list.forEach { l ->
            arr.put(
                JSONObject().apply {
                    put("id", l.id)
                    put("memberId", l.memberId)
                    put("memberName", l.memberName)
                    put("memberSerial", l.memberSerial)
                    put("loanDate", l.loanDate)
                    put("principal", l.principal)
                    put("interest", l.interest)
                    put("totalPayable", l.totalPayable)
                    put("weeklyInstallment", l.weeklyInstallment)
                    put("totalPaid", l.totalPaid)
                    put("remainingLoan", l.remainingLoan)
                    put("nextInstallmentDate", l.nextInstallmentDate)
                    put("missedInstallments", l.missedInstallments)
                    put("status", l.status)
                    put("createdBy", l.createdBy)
                }
            )
        }
        prefs.edit().putString(KEY_LOANS, arr.toString()).apply()
    }

    fun loadLoanInstallments(): List<LoanInstallment> {
        val raw = prefs.getString(KEY_INSTALLMENTS, null) ?: return emptyList()
        return parseJsonArray(raw) { obj ->
            LoanInstallment(
                id = obj.optString("id"),
                loanId = obj.optString("loanId"),
                memberId = obj.optString("memberId"),
                memberName = obj.optString("memberName"),
                serialNumber = obj.optString("serialNumber"),
                installmentDate = obj.optString("installmentDate"),
                amount = obj.optDouble("amount", 0.0),
                isMissed = obj.optBoolean("isMissed", false),
                officerId = obj.optString("officerId"),
                officerName = obj.optString("officerName"),
                status = obj.optString("status", TransactionStatus.APPROVED.name),
                createdBy = obj.optString("createdBy"),
                approvedBy = obj.optString("approvedBy")
            )
        }
    }

    fun saveLoanInstallments(list: List<LoanInstallment>) {
        val arr = JSONArray()
        list.forEach { inst ->
            arr.put(
                JSONObject().apply {
                    put("id", inst.id)
                    put("loanId", inst.loanId)
                    put("memberId", inst.memberId)
                    put("memberName", inst.memberName)
                    put("serialNumber", inst.serialNumber)
                    put("installmentDate", inst.installmentDate)
                    put("amount", inst.amount)
                    put("isMissed", inst.isMissed)
                    put("officerId", inst.officerId)
                    put("officerName", inst.officerName)
                    put("status", inst.status)
                    put("createdBy", inst.createdBy)
                    put("approvedBy", inst.approvedBy)
                }
            )
        }
        prefs.edit().putString(KEY_INSTALLMENTS, arr.toString()).apply()
    }

    fun loadFines(): List<FineRecord> {
        val raw = prefs.getString(KEY_FINES, null) ?: return emptyList()
        return parseJsonArray(raw) { obj ->
            FineRecord(
                id = obj.optString("id"),
                memberId = obj.optString("memberId"),
                memberName = obj.optString("memberName"),
                memberSerial = obj.optString("memberSerial"),
                fineDate = obj.optString("fineDate"),
                missedDate = obj.optString("missedDate"),
                fineType = obj.optString("fineType", FineType.SAVINGS.name),
                amount = obj.optDouble("amount", 10.0),
                isPaid = obj.optBoolean("isPaid", false),
                paidDate = obj.optString("paidDate"),
                referenceId = obj.optString("referenceId"),
                officerId = obj.optString("officerId"),
                officerName = obj.optString("officerName"),
                status = obj.optString("status", TransactionStatus.APPROVED.name),
                createdBy = obj.optString("createdBy")
            )
        }
    }

    fun saveFines(list: List<FineRecord>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(
                JSONObject().apply {
                    put("id", f.id)
                    put("memberId", f.memberId)
                    put("memberName", f.memberName)
                    put("memberSerial", f.memberSerial)
                    put("fineDate", f.fineDate)
                    put("missedDate", f.missedDate)
                    put("fineType", f.fineType)
                    put("amount", f.amount)
                    put("isPaid", f.isPaid)
                    put("paidDate", f.paidDate)
                    put("referenceId", f.referenceId)
                    put("officerId", f.officerId)
                    put("officerName", f.officerName)
                    put("status", f.status)
                    put("createdBy", f.createdBy)
                }
            )
        }
        prefs.edit().putString(KEY_FINES, arr.toString()).apply()
    }

    fun loadFundLedger(): List<FundLedgerEntry> {
        val raw = prefs.getString(KEY_LEDGER, null) ?: return emptyList()
        return parseJsonArray(raw) { obj ->
            FundLedgerEntry(
                id = obj.optString("id"),
                date = obj.optString("date"),
                description = obj.optString("description"),
                moneyIn = obj.optDouble("moneyIn", 0.0),
                moneyOut = obj.optDouble("moneyOut", 0.0),
                balance = obj.optDouble("balance", 0.0),
                referenceId = obj.optString("referenceId"),
                type = obj.optString("type", LedgerType.DEPOSIT.name),
                createdBy = obj.optString("createdBy")
            )
        }
    }

    fun saveFundLedger(list: List<FundLedgerEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("date", e.date)
                    put("description", e.description)
                    put("moneyIn", e.moneyIn)
                    put("moneyOut", e.moneyOut)
                    put("balance", e.balance)
                    put("referenceId", e.referenceId)
                    put("type", e.type)
                    put("createdBy", e.createdBy)
                }
            )
        }
        prefs.edit().putString(KEY_LEDGER, arr.toString()).apply()
    }

    fun loadAppSettings(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return try {
            val obj = JSONObject(raw)
            AppSettings(
                orgName = obj.optString("orgName", "যাদবপুর যুব সমাজ সমিতি"),
                subtitle = obj.optString("subtitle", "সাপ্তাহিক সঞ্চয় ও ঋণ ব্যবস্থাপনা"),
                currency = obj.optString("currency", "৳"),
                currentMonthHeading = obj.optString("currentMonthHeading", "সাপ্তাহিক সঞ্চয়"),
                interestRatePercent = obj.optDouble("interestRatePercent", 20.0),
                appDownloadUrl = obj.optString("appDownloadUrl", ""),
                updatedBy = obj.optString("updatedBy")
            )
        } catch (_: Exception) {
            AppSettings()
        }
    }

    fun saveAppSettings(settings: AppSettings) {
        val obj = JSONObject().apply {
            put("orgName", settings.orgName)
            put("subtitle", settings.subtitle)
            put("currency", settings.currency)
            put("currentMonthHeading", settings.currentMonthHeading)
            put("interestRatePercent", settings.interestRatePercent)
            put("appDownloadUrl", settings.appDownloadUrl)
            put("updatedBy", settings.updatedBy)
        }
        prefs.edit().putString(KEY_SETTINGS, obj.toString()).apply()
    }

    fun loadAdminNotifications(): List<AdminNotification> {
        val raw = prefs.getString(KEY_ADMIN_NOTIFS, null) ?: return emptyList()
        return parseJsonArray(raw) { obj ->
            AdminNotification(
                id = obj.optString("id"),
                title = obj.optString("title"),
                body = obj.optString("body"),
                transactionType = obj.optString("transactionType"),
                transactionId = obj.optString("transactionId"),
                memberName = obj.optString("memberName"),
                amount = obj.optDouble("amount", 0.0),
                officerId = obj.optString("officerId"),
                officerName = obj.optString("officerName"),
                targetRole = obj.optString("targetRole", UserRole.ADMIN.name),
                targetTopic = obj.optString("targetTopic", "admin_approvals"),
                targetScreen = obj.optString("targetScreen", "APPROVALS"),
                deepLink = obj.optString("deepLink", "ekotasamiti://approvals"),
                isRead = obj.optBoolean("isRead", false)
            )
        }
    }

    fun saveAdminNotifications(list: List<AdminNotification>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(
                JSONObject().apply {
                    put("id", n.id)
                    put("title", n.title)
                    put("body", n.body)
                    put("transactionType", n.transactionType)
                    put("transactionId", n.transactionId)
                    put("memberName", n.memberName)
                    put("amount", n.amount)
                    put("officerId", n.officerId)
                    put("officerName", n.officerName)
                    put("targetRole", n.targetRole)
                    put("targetTopic", n.targetTopic)
                    put("targetScreen", n.targetScreen)
                    put("deepLink", n.deepLink)
                    put("isRead", n.isRead)
                }
            )
        }
        prefs.edit().putString(KEY_ADMIN_NOTIFS, arr.toString()).apply()
    }

    fun clearMigratedKeys(includeAdminKeys: Boolean) {
        val editor = prefs.edit()
            .remove(KEY_MEMBERS)
            .remove(KEY_SAVINGS)
            .remove(KEY_INSTALLMENTS)
            .remove(KEY_FINES)
        if (includeAdminKeys) {
            editor
                .remove(KEY_PERIODS)
                .remove(KEY_LOANS)
                .remove(KEY_LEDGER)
                .remove(KEY_ADMIN_NOTIFS)
        }
        editor.apply()
    }

    fun clearAllData() {
        clearMigratedKeys(includeAdminKeys = true)
    }

    private inline fun <T> parseJsonArray(raw: String, mapper: (JSONObject) -> T): List<T> {
        return try {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    add(mapper(arr.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
