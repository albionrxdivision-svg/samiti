package com.example.data.local

import android.content.Context
import android.os.StatFs
import com.example.util.BengaliUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StorageTierOption(
    val limitBytes: Long,
    val labelBn: String,
    val badgeBn: String,
    val subtitleBn: String
)

data class LocalBackupFileInfo(
    val fileName: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long
)

data class StorageUsageStats(
    val configuredLimitBytes: Long,
    val appUsedBytes: Long,
    val roomDbBytes: Long,
    val backupFilesBytes: Long,
    val backupCount: Int,
    val deviceAvailableBytes: Long,
    val deviceTotalBytes: Long
)

object StorageQuotaManager {

    private const val PREFS_NAME = "ekota_storage_prefs"
    private const val KEY_STORAGE_LIMIT_BYTES = "offline_storage_limit_bytes"
    private const val BACKUP_DIR_NAME = "ekota_backups"

    const val STORAGE_100_MB: Long = 100L * 1024L * 1024L
    const val STORAGE_1_GB: Long = 1L * 1024L * 1024L * 1024L
    const val STORAGE_10_GB: Long = 10L * 1024L * 1024L * 1024L
    const val STORAGE_50_GB: Long = 50L * 1024L * 1024L * 1024L
    const val STORAGE_100_GB: Long = 100L * 1024L * 1024L * 1024L

    const val MIN_STORAGE_LIMIT_BYTES: Long = 1L * 1024L * 1024L // 1 MB Firestore minimum
    const val MAX_STORAGE_LIMIT_BYTES: Long = STORAGE_100_GB
    const val DEFAULT_STORAGE_LIMIT_BYTES: Long = STORAGE_100_GB

    val availableTiers: List<StorageTierOption> = listOf(
        StorageTierOption(
            limitBytes = STORAGE_100_MB,
            labelBn = "১০০ MB",
            badgeBn = "সাধারণ",
            subtitleBn = "বেসিক অফলাইন ক্যাশ"
        ),
        StorageTierOption(
            limitBytes = STORAGE_1_GB,
            labelBn = "১ GB",
            badgeBn = "মাঝারি",
            subtitleBn = "দৈনন্দিন অফলাইন এন্ট্রি ও ইতিহাস"
        ),
        StorageTierOption(
            limitBytes = STORAGE_10_GB,
            labelBn = "১০ GB",
            badgeBn = "বড়",
            subtitleBn = "বহু বছরের হিসাব ও সঞ্চয় ব্যাকআপ"
        ),
        StorageTierOption(
            limitBytes = STORAGE_50_GB,
            labelBn = "৫০ GB",
            badgeBn = "বৃহৎ",
            subtitleBn = "দীর্ঘমেয়াদি আর্কাইভ ও অফলাইন ক্যাশ"
        ),
        StorageTierOption(
            limitBytes = STORAGE_100_GB,
            labelBn = "১০০ GB",
            badgeBn = "সর্বোচ্চ",
            subtitleBn = "সর্বোচ্চ ১০০ GB পর্যন্ত ডাটা ও অফলাইন স্টোরেজ"
        )
    )

    fun getConfiguredLimitBytes(context: Context): Long {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getLong(KEY_STORAGE_LIMIT_BYTES, DEFAULT_STORAGE_LIMIT_BYTES)
        return saved.coerceIn(MIN_STORAGE_LIMIT_BYTES, MAX_STORAGE_LIMIT_BYTES)
    }

    fun setConfiguredLimitBytes(context: Context, requestedBytes: Long): Long {
        val clamped = requestedBytes.coerceIn(MIN_STORAGE_LIMIT_BYTES, MAX_STORAGE_LIMIT_BYTES)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_STORAGE_LIMIT_BYTES, clamped).apply()
        return clamped
    }

    fun getBackupDirectory(context: Context): File {
        val dir = File(context.applicationContext.filesDir, BACKUP_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getStorageUsageStats(context: Context): StorageUsageStats {
        val appContext = context.applicationContext
        val configuredLimit = getConfiguredLimitBytes(appContext)
        val filesBytes = calculateFolderSize(appContext.filesDir)
        val cacheBytes = calculateFolderSize(appContext.cacheDir)
        val dbDir = appContext.getDatabasePath("ekota_offline_sync.db").parentFile
        val databasesBytes = calculateFolderSize(dbDir)

        val roomDbFiles = listOf(
            appContext.getDatabasePath("ekota_offline_sync.db"),
            appContext.getDatabasePath("ekota_offline_sync.db-wal"),
            appContext.getDatabasePath("ekota_offline_sync.db-shm")
        )
        val roomDbBytes = roomDbFiles.sumOf { if (it.exists()) it.length() else 0L }

        val backups = listBackupArchives(appContext)
        val backupBytes = backups.sumOf { it.sizeBytes }

        val (availableBytes, totalBytes) = try {
            val stat = StatFs(appContext.filesDir.absolutePath)
            stat.availableBytes to stat.totalBytes
        } catch (_: Exception) {
            0L to 0L
        }

        return StorageUsageStats(
            configuredLimitBytes = configuredLimit,
            appUsedBytes = filesBytes + cacheBytes + databasesBytes,
            roomDbBytes = roomDbBytes,
            backupFilesBytes = backupBytes,
            backupCount = backups.size,
            deviceAvailableBytes = availableBytes,
            deviceTotalBytes = totalBytes
        )
    }

    fun saveBackupToLocalArchive(
        context: Context,
        jsonContent: String,
        prefix: String = "ekota_backup"
    ): Result<LocalBackupFileInfo> {
        return try {
            val appContext = context.applicationContext
            val bytes = jsonContent.toByteArray(Charsets.UTF_8)
            val stats = getStorageUsageStats(appContext)
            if (stats.appUsedBytes + bytes.size > stats.configuredLimitBytes) {
                return Result.failure(
                    IllegalStateException(
                        "নির্ধারিত ${formatBytesBn(stats.configuredLimitBytes)} স্টোরেজ সীমা পূর্ণ হয়েছে"
                    )
                )
            }
            if (stats.deviceAvailableBytes > 0L && bytes.size > stats.deviceAvailableBytes) {
                return Result.failure(
                    IllegalStateException("ডিভাইসে পর্যাপ্ত খালি জায়গা নেই")
                )
            }
            val dir = getBackupDirectory(appContext)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeFile = File(dir, "${prefix}_$stamp.json")
            safeFile.writeText(jsonContent, Charsets.UTF_8)
            Result.success(
                LocalBackupFileInfo(
                    fileName = safeFile.name,
                    sizeBytes = safeFile.length(),
                    lastModifiedMillis = safeFile.lastModified()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listBackupArchives(context: Context): List<LocalBackupFileInfo> {
        val dir = File(context.applicationContext.filesDir, BACKUP_DIR_NAME)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                LocalBackupFileInfo(
                    fileName = file.name,
                    sizeBytes = file.length(),
                    lastModifiedMillis = file.lastModified()
                )
            }
            ?: emptyList()
    }

    fun readBackupArchiveContent(context: Context, fileName: String): String? {
        val dir = File(context.applicationContext.filesDir, BACKUP_DIR_NAME)
        val target = File(dir, File(fileName).name)
        return if (target.exists() && target.isFile) {
            target.readText(Charsets.UTF_8)
        } else {
            null
        }
    }

    fun deleteBackupArchive(context: Context, fileName: String): Boolean {
        val dir = File(context.applicationContext.filesDir, BACKUP_DIR_NAME)
        val target = File(dir, File(fileName).name)
        return target.exists() && target.delete()
    }

    fun formatBytesBn(bytes: Long): String {
        if (bytes <= 0L) return "০ B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val formatted = when {
            bytes >= gb -> {
                val valGb = bytes / gb
                if (valGb >= 10.0 || valGb % 1.0 == 0.0) {
                    "${valGb.toLong()} GB"
                } else {
                    String.format(Locale.US, "%.1f GB", valGb)
                }
            }
            bytes >= mb -> {
                val valMb = bytes / mb
                if (valMb >= 10.0 || valMb % 1.0 == 0.0) {
                    "${valMb.toLong()} MB"
                } else {
                    String.format(Locale.US, "%.1f MB", valMb)
                }
            }
            bytes >= kb -> "${(bytes / kb).toLong().coerceAtLeast(1L)} KB"
            else -> "$bytes B"
        }
        return BengaliUtils.toBengaliDigits(formatted)
    }

    private fun calculateFolderSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        val children = dir.listFiles() ?: return 0L
        var total = 0L
        for (child in children) {
            total += if (child.isDirectory) calculateFolderSize(child) else child.length()
        }
        return total
    }
}
