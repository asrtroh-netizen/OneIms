package com.onetools.app.updates

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onetools.app.updates.VersionCompare.UpdateState

/**
 * Background catalog check for One 自主更新中心.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = UpdateCheckPrefs(applicationContext).snapshot()
        if (!prefs.enabled) return Result.success()

        val repo = UpdateCatalogRepository(applicationContext)
        repo.ensureSeeded()
        val apps = repo.snapshot().filter { it.trackUpdates }
        if (apps.isEmpty()) return Result.success()

        val bearer = MembershipTokenStore(applicationContext).getToken()
        val abis = Build.SUPPORTED_ABIS.toList()
        val outdated = ArrayList<String>()

        for (app in apps) {
            val asset = UpdateFetcher.latestAsset(
                app,
                abis,
                applicationContext,
                bearer,
            ).getOrNull() ?: continue
            val installed = InstalledVersions.versionName(applicationContext, app.packageName)
            if (VersionCompare.state(installed, asset.tag) == UpdateState.UPDATE_AVAILABLE) {
                outdated += app.title
            }
        }

        if (outdated.isNotEmpty()) {
            UpdateCheckNotifier.notifyUpdates(applicationContext, outdated)
        }
        return Result.success()
    }
}
