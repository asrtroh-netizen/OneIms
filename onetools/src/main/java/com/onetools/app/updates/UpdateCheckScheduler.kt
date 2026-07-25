package com.onetools.app.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object UpdateCheckScheduler {
    private const val UNIQUE = "onetools-update-check"

    fun resync(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = UpdateCheckPrefs(app).snapshot()
            val wm = WorkManager.getInstance(app)
            if (!prefs.enabled) {
                wm.cancelUniqueWork(UNIQUE)
                return@launch
            }
            val hours = prefs.intervalHours.toLong().coerceIn(1, 72)
            val req = PeriodicWorkRequestBuilder<UpdateCheckWorker>(hours, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            wm.enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
