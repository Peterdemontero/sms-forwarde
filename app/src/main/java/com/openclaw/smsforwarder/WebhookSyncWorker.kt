package com.openclaw.smsforwarder

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class WebhookSyncWorker(
    private val ctx: Context,
    params: WorkerParameters
) : Worker(ctx, params) {

    override fun doWork(): Result {
        val pending = PendingQueue.getAll(ctx)
        if (pending.isEmpty()) return Result.success()

        Log.d(TAG, "Syncing ${pending.size} pending webhook(s)")
        var anyFailed = false

        for (item in pending) {
            val ok = WebhookSender.sendSync(item.url, item.webhookKey, item.sms)
            if (ok) {
                PendingQueue.remove(ctx, item.id)
                Log.d(TAG, "Flushed queued SMS id=${item.id}")
            } else {
                PendingQueue.incrementAttempts(ctx, item.id)
                anyFailed = true
            }
        }

        return if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "MoMoForwarder"
        private const val WORK_NAME = "momo_webhook_sync"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WebhookSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
