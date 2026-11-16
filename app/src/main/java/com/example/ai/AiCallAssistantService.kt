package com.example.ai

import android.util.Log
import com.example.data.model.FineRecord
import com.example.data.model.Loan
import com.example.data.model.LoanStatus
import com.example.data.model.Member
import com.example.data.model.SavingsRecord
import com.example.data.model.TransactionStatus
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "AiCallAssistantService"

data class AiCallTarget(
    val memberId: String,
    val memberName: String,
    val memberSerial: String,
    val mobile: String,
    val reason: String,
    val dueSummary: String,
    val callScriptBengali: String,
    val priority: Int
)

data class AiCallPlanResult(
    val summary: String,
    val targets: List<AiCallTarget>
)

class AiCallAssistantService {

    private val callPlanSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "summary" to Schema.string(description = "Overall short Bengali summary of members needing follow-up calls"),
                "targets" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "memberId" to Schema.string(description = "Member ID from input data"),
                            "memberName" to Schema.string(description = "Member full name"),
                            "memberSerial" to Schema.string(description = "Member serial number"),
                            "mobile" to Schema.string(description = "Member phone number if provided"),
                            "reason" to Schema.string(description = "Short Bengali call reason category"),
                            "dueSummary" to Schema.string(description = "Concise Bengali breakdown of dues, missed weeks, or fines"),
                            "callScriptBengali" to Schema.string(description = "Natural, polite Bengali voice script to read or speak when calling the member"),
                            "priority" to Schema.integer(description = "Urgency priority 1 (highest) to 5")
                        )
                    ),
                    description = "Prioritized call targets ordered by urgency"
                )
            )
        )
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = callPlanSchema
            }
        )
    }

    suspend fun buildCallPlan(
        orgName: String,
        members: List<Member>,
        savingsRecords: List<SavingsRecord>,
        loans: List<Loan>,
        fines: List<FineRecord>,
        customInstruction: String
    ): AiCallPlanResult {
        val prompt = buildPrompt(orgName, members, savingsRecords, loans, fines, customInstruction)
        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val rawJson = response.text?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("AI মডেল কোনো উত্তর দেয়নি। অনুগ্রহ করে আবার চেষ্টা করুন।")

            val root = JSONObject(rawJson)
            val summary = root.optString("summary", "সদস্যদের বকেয়া কল তালিকা তৈরি হয়েছে")
            val arr = root.optJSONArray("targets")
            val parsedTargets = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val memberId = obj.optString("memberId")
                        val matchedMember = members.firstOrNull { it.id == memberId }
                        val resolvedMobile = obj.optString("mobile").ifBlank { matchedMember?.mobile.orEmpty() }
                        add(
                            AiCallTarget(
                                memberId = memberId,
                                memberName = obj.optString("memberName").ifBlank { matchedMember?.name ?: "সদস্য" },
                                memberSerial = obj.optString("memberSerial").ifBlank { matchedMember?.memberSerial ?: "" },
                                mobile = resolvedMobile,
                                reason = obj.optString("reason", "সাপ্তাহিক অনুস্মারক"),
                                dueSummary = obj.optString("dueSummary", ""),
                                callScriptBengali = obj.optString("callScriptBengali", ""),
                                priority = obj.optInt("priority", i + 1)
                            )
                        )
                    }
                }
            }.sortedBy { it.priority }

            AiCallPlanResult(summary = summary, targets = parsedTargets)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini AI call planning timed out after 60s", e)
            throw IllegalStateException("AI অনুরোধ ৬০ সেকেন্ডে সম্পন্ন হয়নি। নেটওয়ার্ক যাচাই করে আবার চেষ্টা করুন।", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI call planning failed", e)
            throw e
        }
    }

    private fun buildPrompt(
        orgName: String,
        members: List<Member>,
        savingsRecords: List<SavingsRecord>,
        loans: List<Loan>,
        fines: List<FineRecord>,
        customInstruction: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine("আপনি '$orgName'-এর একজন দক্ষ, ভদ্র এবং স্পষ্টভাষী AI কল অ্যাসিস্ট্যান্ট।")
        sb.appendLine("নিচের সদস্য, সঞ্চয়, চলমান ঋণ ও জরিমানার তথ্য বিশ্লেষণ করে যাদের ফোন কল করা জরুরি তাদের একটি অগ্রাধিকার তালিকা ও বাংলা ভয়েস কল স্ক্রিপ্ট তৈরি করুন।")
        if (customInstruction.isNotBlank()) {
            sb.appendLine("অ্যাডমিনের বিশেষ নির্দেশনা: $customInstruction")
        }
        sb.appendLine()
        sb.appendLine("=== সক্রিয় সদস্য তালিকা ===")
        val activeMembers = members.filter { it.isActive }
        if (activeMembers.isEmpty()) {
            sb.appendLine("কোনো সক্রিয় সদস্য নেই।")
        } else {
            activeMembers.take(60).forEach { member ->
                val missedSavingsCount = savingsRecords.count {
                    (it.memberId == member.id || it.memberName == member.name) &&
                        it.status != TransactionStatus.REJECTED.name &&
                        (!it.isPaid || it.amount <= 0.0)
                }
                val activeLoans = loans.filter {
                    (it.memberId == member.id || it.memberName == member.name) &&
                        it.status == LoanStatus.ACTIVE.name &&
                        it.remainingLoan > 0.0
                }
                val unpaidFines = fines.filter {
                    (it.memberId == member.id || it.memberName == member.name) &&
                        it.status != TransactionStatus.REJECTED.name &&
                        !it.isPaid
                }.sumOf { it.amount }

                val loanText = if (activeLoans.isEmpty()) {
                    "কোনো চলমান ঋণ নেই"
                } else {
                    activeLoans.joinToString("; ") { l ->
                        "অবশিষ্ট: ${l.remainingLoan.toInt()} টাকা, সাপ্তাহিক কিস্তি: ${l.weeklyInstallment.toInt()} টাকা, মিসড কিস্তি: ${l.missedInstallments}টি, পরবর্তী তারিখ: ${l.nextInstallmentDate}"
                    }
                }
                sb.appendLine(
                    "- id=${member.id} | ক্রমিক=${member.memberSerial} | নাম=${member.name} | মোবাইল=${member.mobile} | এলাকা=${member.area} | সাপ্তাহিক সঞ্চয়=${member.weeklySavingsAmount.toInt()} | বাকি সঞ্চয়=${missedSavingsCount}টি | ঋণ=[$loanText] | অপরিশোধিত জরিমানা=${unpaidFines.toInt()} টাকা"
                )
            }
        }
        sb.appendLine()
        sb.appendLine("নির্দেশনা:")
        sb.appendLine("১. বকেয়া সঞ্চয়, মিসড কিস্তি, বকেয়া জরিমানা বা চলমান ঋণ থাকা সদস্যদের অগ্রাধিকার (priority 1 থেকে শুরু) দিন; মোবাইল নম্বর ইনপুট থেকে হুবহু রাখুন।")
        sb.appendLine("২. কোনো সদস্যের বকেয়া না থাকলে তবে সক্রিয় ঋণ বা অ্যাডমিনের নির্দেশনার সাথে মিল থাকলে তাদের জন্য সাপ্তাহিক অনুস্মারক স্ক্রিপ্ট লিখুন।")
        sb.appendLine("৩. callScriptBengali অবশ্যই বিনয়ী ও স্বাভাবিক কথোপকথনের বাংলায় ২-৩ বাক্যে লিখুন যেন সরাসরি ভয়েসে পড়া বা ফোনে বলা যায়।")
        return sb.toString()
    }
}
