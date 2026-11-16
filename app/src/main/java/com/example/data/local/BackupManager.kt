package com.example.data.local

import com.example.data.model.AppSettings
import com.example.data.model.CollectionPeriod
import com.example.data.model.FineRecord
import com.example.data.model.FineType
import com.example.data.model.FundLedgerEntry
import com.example.data.model.LedgerType
import com.example.data.model.Loan
import com.example.data.model.LoanInstallment
import com.example.data.model.LoanStatus
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import com.example.util.BengaliUtils
import org.json.JSONArray
import org.json.JSONObject

data class ParsedBackupData(
    val members: List<Member>,
    val savings: List<SavingsRecord>,
    val collectionPeriods: List<CollectionPeriod>,
    val loans: List<Loan>,
    val loanInstallments: List<LoanInstallment>,
    val fines: List<FineRecord>,
    val fundLedger: List<FundLedgerEntry>,
    val appSettings: AppSettings?
) {
    val totalRecordsCount: Int
        get() = members.size + savings.size + collectionPeriods.size + loans.size +
            loanInstallments.size + fines.size + fundLedger.size
}

object BackupManager {

    const val BACKUP_FORMAT_VERSION = 2

    fun createFullBackupJson(
        members: List<Member>,
        savings: List<SavingsRecord>,
        collectionPeriods: List<CollectionPeriod>,
        loans: List<Loan>,
        loanInstallments: List<LoanInstallment>,
        fines: List<FineRecord>,
        ledger: List<FundLedgerEntry>,
        appSettings: AppSettings? = null
    ): String {
        val root = JSONObject()
        root.put("backupFormatVersion", BACKUP_FORMAT_VERSION)
        root.put("organization", appSettings?.orgName ?: "যাদবপুর যুব সমাজ সমিতি")
        root.put("membersCount", members.size)
        root.put("savingsRecordsCount", savings.size)
        root.put("collectionPeriodsCount", collectionPeriods.size)
        root.put("loansCount", loans.size)
        root.put("loanInstallmentsCount", loanInstallments.size)
        root.put("finesCount", fines.size)
        root.put("ledgerCount", ledger.size)
        root.put("exportDate", BengaliUtils.getCurrentDateStr())
        root.put("exportedAtMillis", System.currentTimeMillis())

        val membersArr = JSONArray()
        members.forEach { m ->
            membersArr.put(
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
        root.put("members", membersArr)

        val savingsArr = JSONArray()
        savings.forEach { s ->
            savingsArr.put(
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
        root.put("savings", savingsArr)

        val periodsArr = JSONArray()
        collectionPeriods.forEach { p ->
            periodsArr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("dateStr", p.dateStr)
                    put("monthYear", p.monthYear)
                    put("isClosed", p.isClosed)
                    put("totalSavings", p.totalSavings)
                }
            )
        }
        root.put("collectionPeriods", periodsArr)

        val loansArr = JSONArray()
        loans.forEach { l ->
            loansArr.put(
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
        root.put("loans", loansArr)

        val installmentsArr = JSONArray()
        loanInstallments.forEach { inst ->
            installmentsArr.put(
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
        root.put("loanInstallments", installmentsArr)

        val finesArr = JSONArray()
        fines.forEach { f ->
            finesArr.put(
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
        root.put("fines", finesArr)

        val ledgerArr = JSONArray()
        ledger.forEach { e ->
            ledgerArr.put(
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
        root.put("ledger", ledgerArr)

        if (appSettings != null) {
            root.put(
                "appSettings",
                JSONObject().apply {
                    put("orgName", appSettings.orgName)
                    put("subtitle", appSettings.subtitle)
                    put("currency", appSettings.currency)
                    put("currentMonthHeading", appSettings.currentMonthHeading)
                    put("interestRatePercent", appSettings.interestRatePercent)
                    put("appDownloadUrl", appSettings.appDownloadUrl)
                    put("latestVersionCode", appSettings.latestVersionCode)
                    put("latestVersionName", appSettings.latestVersionName)
                    put("updateNotes", appSettings.updateNotes)
                    put("githubRepoSlug", appSettings.githubRepoSlug)
                    put("updatedBy", appSettings.updatedBy)
                }
            )
        }

        return root.toString(2)
    }

    fun parseBackupJson(rawJson: String): Result<ParsedBackupData> {
        return try {
            val root = JSONObject(rawJson.trim())
            val hasRecognizedSection = root.has("members") ||
                root.has("savings") ||
                root.has("loans") ||
                root.has("ledger") ||
                root.has("fines") ||
                root.has("loanInstallments") ||
                root.has("organization")
            if (!hasRecognizedSection) {
                return Result.failure(IllegalArgumentException("সঠিক একতা সমিতি ব্যাকআপ JSON ফাইল নয়"))
            }

            val members = mapArray(root.optJSONArray("members")) { obj ->
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

            val savings = mapArray(root.optJSONArray("savings")) { obj ->
                SavingsRecord(
                    id = obj.optString("id"),
                    memberId = obj.optString("memberId"),
                    memberName = obj.optString("memberName"),
                    memberSerial = obj.optString("memberSerial"),
                    collectionDate = obj.optString("collectionDate"),
                    amount = obj.optDouble("amount", 0.0),
                    isPaid = obj.optBoolean("isPaid", obj.optDouble("amount", 0.0) > 0.0),
                    officerId = obj.optString("officerId"),
                    officerName = obj.optString("officerName"),
                    status = obj.optString("status", TransactionStatus.APPROVED.name),
                    createdBy = obj.optString("createdBy"),
                    approvedBy = obj.optString("approvedBy")
                )
            }

            val periods = mapArray(root.optJSONArray("collectionPeriods")) { obj ->
                CollectionPeriod(
                    id = obj.optString("id"),
                    dateStr = obj.optString("dateStr"),
                    monthYear = obj.optString("monthYear", "November 2026"),
                    isClosed = obj.optBoolean("isClosed", false),
                    totalSavings = obj.optDouble("totalSavings", 0.0)
                )
            }

            val loans = mapArray(root.optJSONArray("loans")) { obj ->
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

            val installments = mapArray(root.optJSONArray("loanInstallments")) { obj ->
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

            val fines = mapArray(root.optJSONArray("fines")) { obj ->
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

            val ledger = mapArray(root.optJSONArray("ledger")) { obj ->
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

            val appSettings = root.optJSONObject("appSettings")?.let { s ->
                AppSettings(
                    orgName = s.optString("orgName", "যাদবপুর যুব সমাজ সমিতি"),
                    subtitle = s.optString("subtitle", "সাপ্তাহিক সঞ্চয় ও ঋণ ব্যবস্থাপনা"),
                    currency = s.optString("currency", "৳"),
                    currentMonthHeading = s.optString("currentMonthHeading", "সাপ্তাহিক সঞ্চয়"),
                    interestRatePercent = s.optDouble("interestRatePercent", 20.0),
                    appDownloadUrl = s.optString("appDownloadUrl", ""),
                    latestVersionCode = s.optInt("latestVersionCode", 0),
                    latestVersionName = s.optString("latestVersionName", ""),
                    updateNotes = s.optString("updateNotes", ""),
                    githubRepoSlug = s.optString("githubRepoSlug", ""),
                    updatedBy = s.optString("updatedBy")
                )
            }

            Result.success(
                ParsedBackupData(
                    members = members,
                    savings = savings,
                    collectionPeriods = periods,
                    loans = loans,
                    loanInstallments = installments,
                    fines = fines,
                    fundLedger = ledger,
                    appSettings = appSettings
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("ব্যাকআপ ফাইল পড়া যায়নি: ${e.localizedMessage ?: "অকার্যকর JSON"}"))
        }
    }

    private inline fun <T> mapArray(arr: JSONArray?, mapper: (JSONObject) -> T): List<T> {
        if (arr == null) return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(mapper(obj))
            }
        }
    }
}
