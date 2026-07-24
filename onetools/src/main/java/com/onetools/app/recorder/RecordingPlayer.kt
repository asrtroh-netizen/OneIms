package com.onetools.app.recorder

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import androidx.core.content.FileProvider
import java.io.File

object RecordingPlayer {
    private var player: MediaPlayer? = null

    fun play(context: Context, file: File): Result<Unit> = runCatching {
        stop()
        val mp = MediaPlayer()
        mp.setDataSource(file.absolutePath)
        mp.setOnCompletionListener { stop() }
        mp.prepare()
        mp.start()
        player = mp
    }

    fun stop() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
