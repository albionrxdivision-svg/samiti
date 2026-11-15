package com.example.util

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.UserRole

interface CredentialKeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getStringSet(key: String): Set<String>
    fun putStringSet(key: String, value: Set<String>)
}

class SharedPrefsCredentialStore(private val prefs: SharedPreferences) : CredentialKeyValueStore {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getStringSet(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    override fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }
}

class InMemoryCredentialStore : CredentialKeyValueStore {
    private val strings = mutableMapOf<String, String>()
    private val sets = mutableMapOf<String, Set<String>>()

    override fun getString(key: String): String? = strings[key]

    override fun putString(key: String, value: String) {
        strings[key] = value
    }

    override fun getStringSet(key: String): Set<String> = sets[key]?.toSet() ?: emptySet()

    override fun putStringSet(key: String, value: Set<String>) {
        sets[key] = value.toSet()
    }
}

sealed class PasswordChangeResult {
    data object Success : PasswordChangeResult()
    data class Error(val message: String) : PasswordChangeResult()
}

class PasswordManager(private val store: CredentialKeyValueStore) {

    companion object {
        const val CREDENTIALS_PREFS_NAME = "ekota_credentials_v1"
        const val DEFAULT_ADMIN_USERNAME = "Nitai"
        const val DEFAULT_ADMIN_PASSWORD = "Nemai"
        const val DEFAULT_OFFICER_PASSWORD = "123"

        private const val KEY_ADMIN_PASSWORD = "admin_password"
        private const val KEY_ADMIN_CUSTOMIZED = "admin_password_customized"
        private const val KEY_DEFAULT_OFFICER_PASSWORD = "default_officer_password"
        private const val KEY_OFFICER_PASSWORD_PREFIX = "officer_pw_"
        private const val KEY_KNOWN_OFFICERS = "known_officers"

        fun forContext(context: Context): PasswordManager {
            val prefs = context.getSharedPreferences(CREDENTIALS_PREFS_NAME, Context.MODE_PRIVATE)
            return PasswordManager(SharedPrefsCredentialStore(prefs))
        }
    }

    private fun normalizeOfficerKey(username: String): String =
        username.trim().lowercase()

    fun isAdminPasswordCustomized(): Boolean =
        store.getString(KEY_ADMIN_CUSTOMIZED) == "true"

    fun getAdminPassword(): String =
        store.getString(KEY_ADMIN_PASSWORD) ?: DEFAULT_ADMIN_PASSWORD

    fun getDefaultOfficerPassword(): String =
        store.getString(KEY_DEFAULT_OFFICER_PASSWORD) ?: DEFAULT_OFFICER_PASSWORD

    fun hasCustomOfficerPassword(username: String): Boolean {
        val key = normalizeOfficerKey(username)
        if (key.isEmpty()) return false
        return store.getString("$KEY_OFFICER_PASSWORD_PREFIX$key") != null
    }

    fun getOfficerPassword(username: String): String {
        val key = normalizeOfficerKey(username)
        if (key.isNotEmpty()) {
            val custom = store.getString("$KEY_OFFICER_PASSWORD_PREFIX$key")
            if (custom != null) return custom
        }
        return getDefaultOfficerPassword()
    }

    fun getKnownOfficers(): List<String> {
        val saved = store.getStringSet(KEY_KNOWN_OFFICERS)
        return (listOf("মাঠ কর্মকর্তা ১") + saved)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(DEFAULT_ADMIN_USERNAME, ignoreCase = true) }
            .distinct()
    }

    fun recordOfficerKnown(username: String) {
        val clean = username.trim()
        if (clean.isEmpty() || clean.equals(DEFAULT_ADMIN_USERNAME, ignoreCase = true)) return
        val existing = store.getStringSet(KEY_KNOWN_OFFICERS).toMutableSet()
        existing.add(clean)
        store.putStringSet(KEY_KNOWN_OFFICERS, existing)
    }

    fun verifyAdminLogin(username: String, password: String): Boolean {
        if (!username.trim().equals(DEFAULT_ADMIN_USERNAME, ignoreCase = true)) return false
        val saved = getAdminPassword()
        return if (!isAdminPasswordCustomized()) {
            password.trim().equals(saved, ignoreCase = true)
        } else {
            password == saved
        }
    }

    fun verifyOfficerLogin(username: String, password: String): Boolean {
        val cleanUser = username.trim()
        if (cleanUser.isEmpty() || cleanUser.equals(DEFAULT_ADMIN_USERNAME, ignoreCase = true)) {
            return false
        }
        val expected = getOfficerPassword(cleanUser)
        return password == expected
    }

    fun changeOwnPassword(
        role: UserRole,
        officerUsername: String,
        currentPassword: String,
        newPassword: String,
        confirmPassword: String
    ): PasswordChangeResult {
        val cleanNew = newPassword.trim()
        val cleanConfirm = confirmPassword.trim()
        val cleanCurrent = currentPassword.trim()

        if (cleanCurrent.isEmpty()) {
            return PasswordChangeResult.Error("বর্তমান পাসওয়ার্ড লিখুন")
        }
        if (cleanNew.length < 3) {
            return PasswordChangeResult.Error("নতুন পাসওয়ার্ড কমপক্ষে ৩ অক্ষরের হতে হবে")
        }
        if (cleanNew != cleanConfirm) {
            return PasswordChangeResult.Error("নতুন পাসওয়ার্ড ও নিশ্চিত পাসওয়ার্ড মেলেনি")
        }

        if (role == UserRole.ADMIN) {
            val validCurrent = if (!isAdminPasswordCustomized()) {
                cleanCurrent.equals(getAdminPassword(), ignoreCase = true)
            } else {
                cleanCurrent == getAdminPassword()
            }
            if (!validCurrent) {
                return PasswordChangeResult.Error("অ্যাডমিনের বর্তমান পাসওয়ার্ড সঠিক নয়")
            }
            if (cleanNew == getAdminPassword()) {
                return PasswordChangeResult.Error("নতুন পাসওয়ার্ড পূর্বের পাসওয়ার্ড থেকে আলাদা হতে হবে")
            }
            store.putString(KEY_ADMIN_PASSWORD, cleanNew)
            store.putString(KEY_ADMIN_CUSTOMIZED, "true")
            return PasswordChangeResult.Success
        } else {
            val cleanOfficer = officerUsername.trim()
            if (cleanOfficer.isEmpty()) {
                return PasswordChangeResult.Error("মাঠ কর্মকর্তার আইডি পাওয়া যায়নি")
            }
            val currentExpected = getOfficerPassword(cleanOfficer)
            if (cleanCurrent != currentExpected) {
                return PasswordChangeResult.Error("বর্তমান পাসওয়ার্ড সঠিক নয়")
            }
            if (cleanNew == currentExpected) {
                return PasswordChangeResult.Error("নতুন পাসওয়ার্ড পূর্বের পাসওয়ার্ড থেকে আলাদা হতে হবে")
            }
            store.putString("$KEY_OFFICER_PASSWORD_PREFIX${normalizeOfficerKey(cleanOfficer)}", cleanNew)
            recordOfficerKnown(cleanOfficer)
            return PasswordChangeResult.Success
        }
    }

    fun adminSetOfficerPassword(
        officerUsername: String,
        newPassword: String,
        confirmPassword: String,
        setAsDefaultForNewOfficers: Boolean = false
    ): PasswordChangeResult {
        val cleanNew = newPassword.trim()
        val cleanConfirm = confirmPassword.trim()

        if (cleanNew.length < 3) {
            return PasswordChangeResult.Error("নতুন পাসওয়ার্ড কমপক্ষে ৩ অক্ষরের হতে হবে")
        }
        if (cleanNew != cleanConfirm) {
            return PasswordChangeResult.Error("নতুন পাসওয়ার্ড ও নিশ্চিত পাসওয়ার্ড মেলেনি")
        }

        if (setAsDefaultForNewOfficers) {
            store.putString(KEY_DEFAULT_OFFICER_PASSWORD, cleanNew)
            return PasswordChangeResult.Success
        }

        val cleanOfficer = officerUsername.trim()
        if (cleanOfficer.isEmpty()) {
            return PasswordChangeResult.Error("মাঠ কর্মকর্তার নাম বা আইডি লিখুন")
        }
        if (cleanOfficer.equals(DEFAULT_ADMIN_USERNAME, ignoreCase = true)) {
            return PasswordChangeResult.Error("এটি অ্যাডমিনের আইডি। অ্যাডমিন পাসওয়ার্ড পরিবর্তন অপশন ব্যবহার করুন")
        }

        store.putString("$KEY_OFFICER_PASSWORD_PREFIX${normalizeOfficerKey(cleanOfficer)}", cleanNew)
        recordOfficerKnown(cleanOfficer)
        return PasswordChangeResult.Success
    }
}
