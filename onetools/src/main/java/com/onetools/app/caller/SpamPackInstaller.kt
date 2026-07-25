package com.onetools.app.caller

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Build / install a local spam pack without relying on Telo's cloud.
 * Used for OneBlock JSON → onespam.db (and optional zip for CDN upload).
 */
object SpamPackInstaller {
    private const val TAG = "SpamPackInstaller"

    suspend fun installFromBlocklistJson(
        context: Context,
        json: String,
        version: String = "local-${System.currentTimeMillis() / 1000}",
    ): Int = withContext(Dispatchers.IO) {
        val rules = BlocklistFormat.parse(json)
        val rows = rules
            .filter { it.mode == CallMatchMode.EXACT && it.kind == CallRuleKind.BLOCK }
            .map {
                Triple(
                    NumberMatcher.digits(it.pattern).removePrefix("86"),
                    it.tag.ifBlank { "骚扰电话" },
                    "oneblock",
                )
            }
            .filter { it.first.length >= 5 }
            .distinctBy { it.first }
        installRows(context, rows, version)
    }

    suspend fun installRows(
        context: Context,
        rows: List<Triple<String, String, String>>,
        version: String,
    ): Int = withContext(Dispatchers.IO) {
        val dest = context.getDatabasePath(SpamOfflineDatabase.DB_FILE_NAME)
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()
        // Also clear Room sidecar files
        File(dest.path + "-wal").delete()
        File(dest.path + "-shm").delete()

        val db = SQLiteDatabase.openOrCreateDatabase(dest, null)
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS spam_numbers (
                  phone_number TEXT PRIMARY KEY NOT NULL,
                  tag TEXT NOT NULL,
                  source TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS metadata (
                  key TEXT PRIMARY KEY NOT NULL,
                  value TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.beginTransaction()
            try {
                val insert = db.compileStatement(
                    "INSERT OR REPLACE INTO spam_numbers(phone_number, tag, source) VALUES(?,?,?)",
                )
                for ((phone, tag, source) in rows) {
                    insert.clearBindings()
                    insert.bindString(1, phone)
                    insert.bindString(2, tag)
                    insert.bindString(3, source)
                    insert.executeInsert()
                }
                db.execSQL(
                    "INSERT OR REPLACE INTO metadata(key, value) VALUES('version', ?)",
                    arrayOf(version),
                )
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
        OneCallerDirectoryProvider.notifyChanged(context.contentResolver)
        SpamSyncRepository(context).refreshLocalStats()
        Log.i(TAG, "installed ${rows.size} spam rows version=$version")
        rows.size
    }

    /** Create a CDN-ready zip (onespam_{version}.db inside). */
    fun zipDatabase(dbFile: File, version: String, zipOut: File) {
        ZipOutputStream(FileOutputStream(zipOut)).use { zos ->
            zos.putNextEntry(ZipEntry("onespam_$version.db"))
            dbFile.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }
}
