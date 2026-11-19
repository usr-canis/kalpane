package dev.kalpane

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.kalpane.sync.GitHubSync
import dev.kalpane.sync.SyncException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Auto push: pulls and pushes all notebooks to origin every few hours on unmetered networks. */
class AutoPushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val services = Services.get(applicationContext)
        if (services.repo == null || !services.prefs.getBoolean("autoBackup", false)) return Result.success()
        // Never pull under an open page; try again later.
        if (services.editing) return Result.retry()
        return try {
            withContext(Dispatchers.IO) {
                services.lock.withLock { services.runGit(GitHubSync.Mode.Push, "Auto push from Kalpane") { true } }
            }
            Result.success()
        } catch (e: SyncException) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "kalpane-auto-push"

        fun schedule(context: Context, on: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!on) { manager.cancelUniqueWork(NAME); return }
            val request = PeriodicWorkRequestBuilder<AutoPushWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
