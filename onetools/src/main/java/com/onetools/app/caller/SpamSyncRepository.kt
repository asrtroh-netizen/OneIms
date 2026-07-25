package com.onetools.app.caller

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.onetools.app.updates.HttpDownloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Clean-room spam-pack sync (Telo mast path shape, our CDN/API).
 *
 * Manifest JSON (Telo-compatible field names):
 * ```
 * {
 *   "has_update": true,
 *   "latest_version": "20260725",
 *   "download_url": "https://…/onespam.zip",
 *   "size_bytes": 12345,
 *   "checksum": "<sha256 hex>",
 *   "row_count": 1000
 * }
 * ```
 * Zip must contain one `onespam_*.db` or `mast_*.db` SQLite file.
 */
class SpamSyncRepository(private val context: Context) {
    private val _version = MutableStateFlow("")
    val versionFlow: StateFlow<String> = _version.asStateFlow()

    private val _rowCount = MutableStateFlow(0L)
    val rowCountFlow: StateFlow<Long> = _rowCount.asStateFlow()

    init {
        refreshLocalStats()
    }

    fun refreshLocalStats() {
        val db = openDbOrNull() ?: run {
            _version.value = ""
            _rowCount.value = 0L
            return
        }
        try {
            _version.value = db.metadataDao().getVersion().orEmpty()
            _rowCount.value = db.spamNumberDao().rowCount()
        } catch (e: Exception) {
            Log.w(TAG, "refreshLocalStats failed", e)
            _version.value = ""
            _rowCount.value = 0L
        } finally {
            db.close()
        }
    }

    fun openDbOrNull(): SpamOfflineDatabase? {
        val file = context.getDatabasePath(SpamOfflineDatabase.DB_FILE_NAME)
        if (!file.exists()) return null
        return Room.databaseBuilder(
            context.applicationContext,
            SpamOfflineDatabase::class.java,
            SpamOfflineDatabase.DB_FILE_NAME,
        ).build()
    }

    fun lookupLocal(phoneDigits: String): SpamNumberEntity? {
        val normalized = phoneDigits.removePrefix("86").let { NumberMatcher.digits(it) }
        if (normalized.isEmpty()) return null
        val db = openDbOrNull() ?: return null
        return try {
            db.spamNumberDao().search(normalized)
                ?: db.spamNumberDao().search(phoneDigits)
        } catch (e: Exception) {
            Log.w(TAG, "local spam lookup failed", e)
            null
        } finally {
            db.close()
        }
    }

    suspend fun checkUpdate(manifestUrl: String, currentVersion: String): SpamSyncManifest =
        withContext(Dispatchers.IO) {
            val sep = if (manifestUrl.contains('?')) '&' else '?'
            val url = buildString {
                append(manifestUrl)
                if (!manifestUrl.contains("current_version=")) {
                    append(sep)
                    append("current_version=")
                    append(java.net.URLEncoder.encode(currentVersion, Charsets.UTF_8))
                }
            }
            val text = HttpDownloads.get(url)
            SpamSyncManifest.parse(text, currentVersion)
        }

    suspend fun downloadAndInstall(
        manifest: SpamSyncManifest,
        onProgress: (Int) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        require(manifest.downloadUrl.isNotBlank()) { "download_url empty" }
        require(manifest.checksum.isNotBlank()) { "checksum empty" }
        val tempZip = File(context.cacheDir, "onespam_update.zip")
        try {
            HttpDownloads.downloadToFile(manifest.downloadUrl, tempZip) { done, total ->
                if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 99))
            }
            if (manifest.sizeBytes > 0L && tempZip.length() != manifest.sizeBytes) {
                Log.w(TAG, "size mismatch ${tempZip.length()} vs ${manifest.sizeBytes}")
                return@withContext false
            }
            val sha = sha256Hex(tempZip)
            if (!sha.equals(manifest.checksum, ignoreCase = true)) {
                Log.w(TAG, "checksum mismatch $sha vs ${manifest.checksum}")
                return@withContext false
            }
            onProgress(100)
            val extracted = extractDbFromZip(tempZip) ?: return@withContext false
            val dest = context.getDatabasePath(SpamOfflineDatabase.DB_FILE_NAME)
            dest.parentFile?.mkdirs()
            if (dest.exists()) dest.delete()
            if (!extracted.renameTo(dest)) {
                extracted.copyTo(dest, overwrite = true)
                extracted.delete()
            }
            refreshLocalStats()
            OneCallerDirectoryProvider.notifyChanged(context.contentResolver)
            true
        } catch (e: Exception) {
            Log.e(TAG, "downloadAndInstall failed", e)
            false
        } finally {
            if (tempZip.exists()) tempZip.delete()
        }
    }

    suspend fun deleteDatabase() = withContext(Dispatchers.IO) {
        val dest = context.getDatabasePath(SpamOfflineDatabase.DB_FILE_NAME)
        if (dest.exists()) dest.delete()
        refreshLocalStats()
        OneCallerDirectoryProvider.notifyChanged(context.contentResolver)
    }

    private fun extractDbFromZip(zipFile: File): File? {
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name.substringAfterLast('/')
                val ok = (name.startsWith("onespam_") || name.startsWith("mast_")) &&
                    name.endsWith(".db")
                if (!ok) continue
                val out = File(context.cacheDir, "temp_onespam.db")
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
                return out
            }
        }
        return null
    }

    companion object {
        private const val TAG = "SpamSyncRepository"

        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } >= 0) {
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

data class SpamSyncManifest(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val checksum: String,
    val rowCount: Long = 0L,
) {
    companion object {
        fun parse(raw: String, currentVersion: String): SpamSyncManifest {
            val o = JSONObject(raw)
            val latest = o.optString("latest_version").ifBlank { o.optString("version") }
            val has = if (o.has("has_update")) {
                o.getBoolean("has_update")
            } else {
                latest.isNotBlank() && latest != currentVersion
            }
            return SpamSyncManifest(
                hasUpdate = has,
                latestVersion = latest,
                downloadUrl = o.optString("download_url").ifBlank { o.optString("url") },
                sizeBytes = o.optLong("size_bytes", o.optLong("size", 0L)),
                checksum = o.optString("checksum").ifBlank { o.optString("sha256") },
                rowCount = o.optLong("row_count", 0L),
            )
        }
    }
}
