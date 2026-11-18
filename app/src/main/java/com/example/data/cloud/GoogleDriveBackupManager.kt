package com.example.data.cloud

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class GoogleDriveConnectionState(
    val isConnected: Boolean = false,
    val accountEmail: String = "",
    val accountName: String = "",
    val folderId: String = "",
    val lastBackupTimeMillis: Long = 0L,
    val lastBackupFileName: String = "",
    val lastBackupFileId: String = "",
    val autoBackupEnabled: Boolean = false
)

data class DriveBackupFile(
    val id: String,
    val name: String,
    val modifiedTime: String = "",
    val sizeBytes: Long = 0L
)

sealed interface DriveAuthOutcome {
    data class Authorized(val accessToken: String) : DriveAuthOutcome
    data class NeedsResolution(val pendingIntent: PendingIntent) : DriveAuthOutcome
    data class Failed(val message: String) : DriveAuthOutcome
}

object GoogleDriveBackupManager {

    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val BACKUP_FOLDER_NAME = "EkotaSamitiBackups"

    private const val PREFS_NAME = "ekota_google_drive_storage"
    private const val KEY_CONNECTED = "drive_connected"
    private const val KEY_EMAIL = "drive_email"
    private const val KEY_NAME = "drive_name"
    private const val KEY_FOLDER_ID = "drive_folder_id"
    private const val KEY_LAST_BACKUP_TIME = "drive_last_backup_time"
    private const val KEY_LAST_BACKUP_FILE = "drive_last_backup_file"
    private const val KEY_LAST_BACKUP_FILE_ID = "drive_last_backup_file_id"
    private const val KEY_AUTO_BACKUP = "drive_auto_backup"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    fun loadConnectionState(context: Context): GoogleDriveConnectionState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return GoogleDriveConnectionState(
            isConnected = prefs.getBoolean(KEY_CONNECTED, false),
            accountEmail = prefs.getString(KEY_EMAIL, "") ?: "",
            accountName = prefs.getString(KEY_NAME, "") ?: "",
            folderId = prefs.getString(KEY_FOLDER_ID, "") ?: "",
            lastBackupTimeMillis = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L),
            lastBackupFileName = prefs.getString(KEY_LAST_BACKUP_FILE, "") ?: "",
            lastBackupFileId = prefs.getString(KEY_LAST_BACKUP_FILE_ID, "") ?: "",
            autoBackupEnabled = prefs.getBoolean(KEY_AUTO_BACKUP, false)
        )
    }

    fun saveConnectionState(context: Context, state: GoogleDriveConnectionState) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CONNECTED, state.isConnected)
            .putString(KEY_EMAIL, state.accountEmail)
            .putString(KEY_NAME, state.accountName)
            .putString(KEY_FOLDER_ID, state.folderId)
            .putLong(KEY_LAST_BACKUP_TIME, state.lastBackupTimeMillis)
            .putString(KEY_LAST_BACKUP_FILE, state.lastBackupFileName)
            .putString(KEY_LAST_BACKUP_FILE_ID, state.lastBackupFileId)
            .putBoolean(KEY_AUTO_BACKUP, state.autoBackupEnabled)
            .commit()
    }

    fun setAutoBackupEnabled(context: Context, enabled: Boolean): GoogleDriveConnectionState {
        val updated = loadConnectionState(context).copy(autoBackupEnabled = enabled)
        saveConnectionState(context, updated)
        return updated
    }

    fun disconnect(context: Context): GoogleDriveConnectionState {
        val disconnected = GoogleDriveConnectionState(isConnected = false)
        saveConnectionState(context, disconnected)
        return disconnected
    }

    suspend fun requestDriveAuthorization(activity: Activity): DriveAuthOutcome {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(activity).authorize(request).await()
            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) {
                    DriveAuthOutcome.NeedsResolution(pendingIntent)
                } else {
                    DriveAuthOutcome.Failed("Google Drive অনুমোদনের পর্দা চালু করা যায়নি")
                }
            } else {
                val token = result.accessToken
                if (!token.isNullOrBlank()) {
                    DriveAuthOutcome.Authorized(token)
                } else {
                    DriveAuthOutcome.Failed("Google Drive অ্যাক্সেস টোকেন পাওয়া যায়নি")
                }
            }
        } catch (e: ApiException) {
            DriveAuthOutcome.Failed(
                "Google Drive অনুমোদন ব্যর্থ হয়েছে (কোড: ${e.statusCode})। ফোনে Google অ্যাকাউন্ট লগইন আছে কিনা দেখুন অথবা নিচের 'ফাইল/ড্রাইভ পিকার' ব্যবহার করুন।"
            )
        } catch (e: Exception) {
            DriveAuthOutcome.Failed(
                e.localizedMessage ?: "Google Drive সংযোগ করা যায়নি"
            )
        }
    }

    fun extractAuthorizationFromIntent(activity: Activity, data: Intent?): DriveAuthOutcome {
        if (data == null) {
            return DriveAuthOutcome.Failed("Google Drive অনুমোদন বাতিল করা হয়েছে")
        }
        return try {
            val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
            val token = result.accessToken
            if (!token.isNullOrBlank()) {
                DriveAuthOutcome.Authorized(token)
            } else {
                DriveAuthOutcome.Failed("Google Drive অ্যাক্সেস টোকেন পাওয়া যায়নি")
            }
        } catch (e: ApiException) {
            DriveAuthOutcome.Failed("Google Drive অনুমোদন ব্যর্থ (কোড: ${e.statusCode})")
        } catch (e: Exception) {
            DriveAuthOutcome.Failed(e.localizedMessage ?: "Google Drive অনুমোদন ব্যর্থ হয়েছে")
        }
    }

    suspend fun syncConnectedAccountInfo(
        context: Context,
        accessToken: String
    ): Result<GoogleDriveConnectionState> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=user(displayName,emailAddress)")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Google Drive তথ্য আনা যায়নি (${resp.code})")
                }
                val userObj = JSONObject(bodyStr).optJSONObject("user")
                val email = userObj?.optString("emailAddress").orEmpty()
                val name = userObj?.optString("displayName").orEmpty()
                val current = loadConnectionState(context)
                val updated = current.copy(
                    isConnected = true,
                    accountEmail = email.ifBlank { current.accountEmail },
                    accountName = name.ifBlank { current.accountName }
                )
                saveConnectionState(context, updated)
                updated
            }
        }
    }

    suspend fun ensureBackupFolder(
        context: Context,
        accessToken: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedFolderId = loadConnectionState(context).folderId
            if (cachedFolderId.isNotBlank()) {
                val verifyReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/${URLEncoder.encode(cachedFolderId, "UTF-8")}?fields=id,trashed")
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                val isValid = httpClient.newCall(verifyReq).execute().use { resp ->
                    if (!resp.isSuccessful) false
                    else {
                        val obj = JSONObject(resp.body?.string().orEmpty())
                        !obj.optBoolean("trashed", false) && obj.optString("id").isNotBlank()
                    }
                }
                if (isValid) return@runCatching cachedFolderId
            }

            val query = URLEncoder.encode(
                "name='$BACKUP_FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false",
                "UTF-8"
            )
            val searchReq = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name)&spaces=drive")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val existingId = httpClient.newCall(searchReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Google Drive ফোল্ডার খোঁজা যায়নি (${resp.code})")
                }
                val files = JSONObject(resp.body?.string().orEmpty()).optJSONArray("files")
                if (files != null && files.length() > 0) files.getJSONObject(0).optString("id") else ""
            }

            val finalFolderId = if (existingId.isNotBlank()) {
                existingId
            } else {
                val metaJson = JSONObject().apply {
                    put("name", BACKUP_FOLDER_NAME)
                    put("mimeType", "application/vnd.google-apps.folder")
                }.toString()
                val createReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?fields=id,name")
                    .header("Authorization", "Bearer $accessToken")
                    .post(metaJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(createReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("Google Drive ফোল্ডার তৈরি করা যায়নি (${resp.code})")
                    }
                    JSONObject(resp.body?.string().orEmpty()).getString("id")
                }
            }

            val current = loadConnectionState(context)
            saveConnectionState(context, current.copy(isConnected = true, folderId = finalFolderId))
            finalFolderId
        }
    }

    suspend fun uploadBackupJson(
        context: Context,
        accessToken: String,
        jsonContent: String,
        fileName: String = defaultBackupFileName()
    ): Result<DriveBackupFile> = withContext(Dispatchers.IO) {
        runCatching {
            syncConnectedAccountInfo(context, accessToken)
            val folderId = ensureBackupFolder(context, accessToken).getOrThrow()

            val boundary = "ekota_drive_${System.currentTimeMillis()}"
            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("mimeType", "application/json")
                put("parents", JSONArray(listOf(folderId)))
            }.toString()

            val multipartBodyStr = buildString {
                append("--").append(boundary).append("\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(metadataJson).append("\r\n")
                append("--").append(boundary).append("\r\n")
                append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                append(jsonContent).append("\r\n")
                append("--").append(boundary).append("--")
            }

            val uploadReq = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,modifiedTime,size")
                .header("Authorization", "Bearer $accessToken")
                .post(
                    multipartBodyStr.toRequestBody(
                        "multipart/related; boundary=$boundary".toMediaType()
                    )
                )
                .build()

            httpClient.newCall(uploadReq).execute().use { resp ->
                val respStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Google Drive আপলোড ব্যর্থ (${resp.code})")
                }
                val obj = JSONObject(respStr)
                val uploaded = DriveBackupFile(
                    id = obj.getString("id"),
                    name = obj.optString("name", fileName),
                    modifiedTime = obj.optString("modifiedTime"),
                    sizeBytes = obj.optString("size").toLongOrNull()
                        ?: jsonContent.toByteArray(Charsets.UTF_8).size.toLong()
                )
                val current = loadConnectionState(context)
                saveConnectionState(
                    context,
                    current.copy(
                        isConnected = true,
                        folderId = folderId,
                        lastBackupTimeMillis = System.currentTimeMillis(),
                        lastBackupFileName = uploaded.name,
                        lastBackupFileId = uploaded.id
                    )
                )
                uploaded
            }
        }
    }

    suspend fun listBackups(
        context: Context,
        accessToken: String
    ): Result<List<DriveBackupFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val folderId = ensureBackupFolder(context, accessToken).getOrThrow()
            val query = URLEncoder.encode(
                "'$folderId' in parents and trashed=false",
                "UTF-8"
            )
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files?q=$query&orderBy=modifiedTime%20desc&fields=files(id,name,modifiedTime,size)&pageSize=20")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val respStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Google Drive ফাইল তালিকা আনা যায়নি (${resp.code})")
                }
                val arr = JSONObject(respStr).optJSONArray("files") ?: JSONArray()
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(
                            DriveBackupFile(
                                id = obj.optString("id"),
                                name = obj.optString("name"),
                                modifiedTime = obj.optString("modifiedTime"),
                                sizeBytes = obj.optString("size").toLongOrNull() ?: 0L
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun downloadBackupContent(
        accessToken: String,
        fileId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/${URLEncoder.encode(fileId, "UTF-8")}?alt=media")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(req).execute().use { resp ->
                val content = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || content.isBlank()) {
                    throw IllegalStateException("Google Drive থেকে ব্যাকআপ ডাউনলোড করা যায়নি (${resp.code})")
                }
                content
            }
        }
    }

    fun defaultBackupFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "ekota_samiti_backup_$stamp.json"
    }

    fun formatTimestamp(millis: Long): String {
        if (millis <= 0L) return "এখনো ব্যাকআপ নেওয়া হয়নি"
        return SimpleDateFormat("dd/MM/yy hh:mm a", Locale.US).format(Date(millis))
    }
}
