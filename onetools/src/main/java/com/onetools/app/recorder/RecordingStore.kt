package com.onetools.app.recorder

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecorderConsent {
    private const val PREF = "one_call_recorder"
    private const val KEY = "legal_ok"

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setAccepted(context: Context, ok: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, ok)
            .apply()
    }
}

object RecordingStore {
    fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), "call_recordings").also { it.mkdirs() }

    fun newFile(context: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir(context), "call_$stamp.wav")
    }

    fun list(context: Context): List<File> =
        dir(context).listFiles()?.filter { it.isFile && it.extension.equals("wav", true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
}
