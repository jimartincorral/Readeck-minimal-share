package io.github.jimartincorral.readeckshare

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends one link to Readeck. Waits for a network connection and retries with backoff
 * when the server can't be reached, so links shared while offline still arrive.
 */
class SaveWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE)
        val labels = inputData.getStringArray(KEY_LABELS)?.toList().orEmpty()
        val prefs = Prefs(applicationContext)
        if (!prefs.isConfigured) {
            return@withContext fail(applicationContext.getString(R.string.not_configured))
        }

        try {
            ReadeckClient(prefs.serverUrl, prefs.token).addBookmark(url, title, labels)
            notifyIfSheetGone(applicationContext.getString(R.string.saved))
            Result.success()
        } catch (e: ReadeckClient.HttpException) {
            // 5xx is likely temporary; 4xx (bad token, invalid URL…) won't fix itself.
            if (e.code >= 500 && runAttemptCount < MAX_ATTEMPTS) Result.retry()
            else fail(e.message ?: "HTTP ${e.code}")
        } catch (e: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
            else fail(applicationContext.getString(R.string.unreachable))
        }
    }

    private fun fail(message: String): Result {
        val prefix = applicationContext.getString(R.string.save_failed)
        notifyIfSheetGone("$prefix: $message")
        return Result.failure(workDataOf(KEY_ERROR to message))
    }

    // The share sheet reports results itself while it's open; once it's gone, use a toast
    // so the user still hears back without being pulled out of what they're doing.
    private fun notifyIfSheetGone(message: String) {
        if (ShareActivity.visibleWorkName == tags.firstOrNull { it.startsWith(TAG_PREFIX) }) return
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val KEY_ERROR = "error"
        private const val KEY_URL = "url"
        private const val KEY_TITLE = "title"
        private const val KEY_LABELS = "labels"
        private const val TAG_PREFIX = "save:"
        private const val MAX_ATTEMPTS = 8

        /**
         * Queues a save and returns the unique work name to observe. Sharing the same link
         * again while it's still queued doesn't create a duplicate.
         */
        fun enqueue(context: Context, link: SharedLink, labels: List<String>): String {
            val name = TAG_PREFIX + link.url
            val request = OneTimeWorkRequestBuilder<SaveWorker>()
                .setInputData(
                    workDataOf(
                        KEY_URL to link.url,
                        KEY_TITLE to link.title,
                        KEY_LABELS to labels.toTypedArray(),
                    )
                )
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag(name)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
            return name
        }
    }
}
