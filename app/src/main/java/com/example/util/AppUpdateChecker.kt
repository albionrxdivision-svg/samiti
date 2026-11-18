package com.example.util

import com.example.BuildConfig
import com.example.data.model.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val downloadUrl: String,
    val releaseNotes: String = "",
    val sourceLabel: String = "",
    val permanentDownloadUrl: String = ""
)

object AppUpdateChecker {

    const val PERMANENT_APK_ASSET_NAME = "Ekota-Samiti.apk"

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    fun extractGitHubRepoSlug(rawUrlOrSlug: String?): String? {
        val trimmed = rawUrlOrSlug?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        val plainSlugRegex = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
        if (plainSlugRegex.matches(trimmed)) {
            return trimmed.removeSuffix(".git")
        }

        val apiMatch = Regex("api\\.github\\.com/repos/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)", RegexOption.IGNORE_CASE)
            .find(trimmed)
        if (apiMatch != null) {
            return apiMatch.groupValues[1].removeSuffix(".git")
        }

        val webMatch = Regex("github\\.com/([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)", RegexOption.IGNORE_CASE)
            .find(trimmed)
        if (webMatch != null) {
            return webMatch.groupValues[1].removeSuffix(".git")
        }

        return null
    }

    fun resolveRepoSlug(
        settings: AppSettings,
        bundledRepoSlug: String = BuildConfig.GITHUB_REPO_SLUG
    ): String? {
        return extractGitHubRepoSlug(settings.githubRepoSlug)
            ?: extractGitHubRepoSlug(settings.appDownloadUrl)
            ?: extractGitHubRepoSlug(bundledRepoSlug)
    }

    /**
     * Builds the permanent, never-changing direct APK download URL for GitHub Releases (`releases/latest/download/Ekota-Samiti.apk`).
     */
    fun buildPermanentApkUrl(repoSlug: String?): String {
        val cleanSlug = extractGitHubRepoSlug(repoSlug) ?: return ""
        return "https://github.com/$cleanSlug/releases/latest/download/$PERMANENT_APK_ASSET_NAME"
    }

    /**
     * Resolves the unchanging download URL used for every update push so users never have to change links.
     */
    fun resolvePermanentApkUrl(
        settings: AppSettings,
        bundledRepoSlug: String = BuildConfig.GITHUB_REPO_SLUG
    ): String {
        val resolvedSlug = resolveRepoSlug(settings, bundledRepoSlug)
        if (!resolvedSlug.isNullOrBlank() && (settings.keepPermanentLink || settings.appDownloadUrl.isBlank())) {
            return buildPermanentApkUrl(resolvedSlug)
        }
        return settings.appDownloadUrl.trim()
    }

    /**
     * Parses a GitHub Release tag (e.g. `v1.0.25`, `1.0.25`, or `v25`) into the matching monotonic
     * versionCode (`GITHUB_RUN_NUMBER + 10` for `1.0.<runNumber>` tags).
     */
    fun parseVersionCodeFromTag(tagName: String): Int {
        val cleaned = tagName.trim().removePrefix("v").removePrefix("V")
        val dottedMatch = Regex("^1\\.0\\.(\\d+)$").find(cleaned)
        if (dottedMatch != null) {
            val runNum = dottedMatch.groupValues[1].toIntOrNull() ?: 0
            if (runNum > 0) return runNum + 10
        }
        val singleInt = cleaned.toIntOrNull()
        if (singleInt != null && singleInt > 0) return singleInt
        val trailingInt = Regex("(\\d+)$").find(cleaned)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return trailingInt ?: 0
    }

    fun compareVersionNames(a: String, b: String): Int {
        val partsA = a.trim().removePrefix("v").removePrefix("V").split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val partsB = b.trim().removePrefix("v").removePrefix("V").split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val va = partsA.getOrElse(i) { 0 }
            val vb = partsB.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    fun isUpdateNewer(
        currentVersionCode: Int,
        currentVersionName: String,
        remoteVersionCode: Int,
        remoteVersionName: String
    ): Boolean {
        if (remoteVersionCode > 0 && remoteVersionCode > currentVersionCode) {
            return true
        }
        if (remoteVersionCode in 1..currentVersionCode) {
            return false
        }
        return remoteVersionName.isNotBlank() && compareVersionNames(remoteVersionName, currentVersionName) > 0
    }

    fun evaluateSettingsUpdate(
        settings: AppSettings,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): AppUpdateInfo? {
        val rawUrl = settings.appDownloadUrl.trim()
        val effectiveUrl = if (settings.keepPermanentLink) {
            resolvePermanentApkUrl(settings, bundledRepoSlug = "").ifBlank { rawUrl }
        } else {
            rawUrl
        }
        if (effectiveUrl.isBlank()) return null
        val newer = isUpdateNewer(
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName,
            remoteVersionCode = settings.latestVersionCode,
            remoteVersionName = settings.latestVersionName
        )
        if (!newer) return null
        return AppUpdateInfo(
            latestVersionCode = settings.latestVersionCode.coerceAtLeast(currentVersionCode + 1),
            latestVersionName = settings.latestVersionName.ifBlank { "নতুন সংস্করণ" },
            downloadUrl = effectiveUrl,
            releaseNotes = settings.updateNotes,
            sourceLabel = "সমিতি সার্ভার",
            permanentDownloadUrl = effectiveUrl
        )
    }

    fun parseGitHubReleaseJson(
        jsonString: String,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        currentVersionName: String = BuildConfig.VERSION_NAME
    ): AppUpdateInfo? {
        return try {
            val obj = JSONObject(jsonString)
            if (obj.optBoolean("draft", false) || obj.optBoolean("prerelease", false)) {
                return null
            }
            val tagName = obj.optString("tag_name", "").trim()
            val htmlUrl = obj.optString("html_url", "").trim()
            val bodyNotes = obj.optString("body", "").trim()
            val assets = obj.optJSONArray("assets")
            var apkDownloadUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    val url = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                        apkDownloadUrl = url
                        break
                    }
                }
            }
            val finalUrl = apkDownloadUrl.ifBlank { htmlUrl }
            if (finalUrl.isBlank()) return null

            val parsedCode = parseVersionCodeFromTag(tagName)
            val displayVerName = tagName.removePrefix("v").removePrefix("V").ifBlank { tagName }
            if (!isUpdateNewer(currentVersionCode, currentVersionName, parsedCode, displayVerName)) {
                return null
            }

            val repoSlug = extractGitHubRepoSlug(finalUrl) ?: extractGitHubRepoSlug(htmlUrl)
            val permanentUrl = buildPermanentApkUrl(repoSlug)

            AppUpdateInfo(
                latestVersionCode = parsedCode,
                latestVersionName = displayVerName,
                downloadUrl = finalUrl,
                releaseNotes = bodyNotes,
                sourceLabel = "GitHub Release",
                permanentDownloadUrl = permanentUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun checkForAvailableUpdate(
        settings: AppSettings,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
        currentVersionName: String = BuildConfig.VERSION_NAME,
        bundledRepoSlug: String = BuildConfig.GITHUB_REPO_SLUG
    ): AppUpdateInfo? {
        val settingsUpdate = evaluateSettingsUpdate(settings, currentVersionCode, currentVersionName)
        val resolvedSlug = resolveRepoSlug(settings, bundledRepoSlug)

        if (resolvedSlug.isNullOrBlank()) {
            return settingsUpdate
        }

        val permanentUrl = buildPermanentApkUrl(resolvedSlug)
        val rawGithubUpdate = fetchLatestGitHubRelease(
            repoSlug = resolvedSlug,
            currentVersionCode = currentVersionCode,
            currentVersionName = currentVersionName
        )
        val githubUpdate = rawGithubUpdate?.let { info ->
            if (settings.keepPermanentLink && permanentUrl.isNotBlank()) {
                info.copy(
                    downloadUrl = permanentUrl,
                    permanentDownloadUrl = permanentUrl
                )
            } else {
                info.copy(
                    permanentDownloadUrl = permanentUrl.ifBlank { info.permanentDownloadUrl }
                )
            }
        }

        return when {
            githubUpdate != null && settingsUpdate != null -> {
                if (githubUpdate.latestVersionCode >= settingsUpdate.latestVersionCode) githubUpdate else settingsUpdate
            }
            else -> githubUpdate ?: settingsUpdate
        }
    }

    private suspend fun fetchLatestGitHubRelease(
        repoSlug: String,
        currentVersionCode: Int,
        currentVersionName: String
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repoSlug/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "EkotaSamiti-Android")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parseGitHubReleaseJson(body, currentVersionCode, currentVersionName)
            }
        } catch (_: Exception) {
            null
        }
    }
}
