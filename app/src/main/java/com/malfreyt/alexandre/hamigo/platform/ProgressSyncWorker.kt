package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.malfreyt.alexandre.hamigo.Progress
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Durable hourly refresh. Android may defer it for battery, Doze, or a missing connection. */
class ProgressSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val progress = Progress(applicationContext)
        val sync = GitHubSync(applicationContext)
        if (!progress.prefs.getBoolean("autoSync", true) || !sync.tokens.hasToken()) return Result.success()
        return try {
            sync.synchronize(progress)
            refreshFriends(progress, sync)
            FriendInboxCoordinator(applicationContext,progress,sync).refresh()
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (e: SocialException) {
            // An expired authorization requires the user's reconnection, not repeated background attempts.
            if (e.httpStatus in listOf(401, 403, 404, 422) && e.message?.contains("limite") != true) Result.failure()
            else if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (_: Exception) { Result.failure() }
    }

    private suspend fun refreshFriends(progress: Progress, sync: GitHubSync) {
        val before = readFriends(progress)
        val refreshed = linkedMapOf<String, GitHubFriendProfile>()
        for (entry in before) {
            val gist = entry.optString("gist")
            if (gist.isBlank()) continue
            try { refreshed[gist] = sync.readProfile(gist) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* A temporarily unreachable friend retains its previous card. */ }
        }
        synchronized(Progress.CLOUD_LOCK) {
            // Reload after the network calls: never restore a friend removed while the worker was running.
            val latest = readFriends(progress)
            val merged = latest.map { entry ->
                val update = refreshed[entry.optString("gist")]
                if (update == null) entry else {
                    val previousTime = runCatching { Instant.parse(entry.getJSONObject("progress").getString("updatedAt")) }.getOrNull()
                    val updateTime = runCatching { Instant.parse(update.progress.updatedAt) }.getOrNull()
                    JSONObject(entry.toString()).also { updated ->
                        if (!(previousTime != null && updateTime != null && previousTime.isAfter(updateTime)))
                            updated.put("progress", JSONObject(update.progress.toJson()))
                        update.identity?.let { updated.put("githubIdentity",it.toJson())
                            .put("githubIdentityCheckedAt",System.currentTimeMillis()) }
                    }
                }
            }
            progress.prefs.edit().putString("friends", JSONArray(merged).toString())
                .putLong("friendsSyncedAt", System.currentTimeMillis()).apply()
        }
    }

    private fun readFriends(progress: Progress): List<JSONObject> = runCatching {
        val array = CloudProgress.activeFriends(progress.friendRecords())
        (0 until minOf(array.length(), 30)).map { array.getJSONObject(it) }
    }.getOrDefault(emptyList())
}

object ProgressSyncScheduler {
    const val PERIODIC_NAME = "hamigo-github-hourly"
    const val PENDING_NAME = "hamigo-github-pending"
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true).build()

    fun schedule(context: Context) {
        val progress = Progress(context.applicationContext)
        if (!progress.prefs.getBoolean("autoSync", true) || !SecureTokenStore(context).hasToken()) {
            cancel(context); return
        }
        val request = PeriodicWorkRequestBuilder<ProgressSyncWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
            .setConstraints(constraints).setInitialDelay(1, TimeUnit.HOURS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Debounced answer/save events persist even if the app closes before the connection returns. */
    fun enqueue(context: Context, delaySeconds: Long = 8) {
        val progress = Progress(context.applicationContext)
        if (!progress.prefs.getBoolean("autoSync", true) || !SecureTokenStore(context).hasToken()) return
        val request = OneTimeWorkRequestBuilder<ProgressSyncWorker>().setConstraints(constraints)
            .setInitialDelay(delaySeconds.coerceIn(0, 60), TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(PENDING_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PERIODIC_NAME)
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(PENDING_NAME)
    }
}
