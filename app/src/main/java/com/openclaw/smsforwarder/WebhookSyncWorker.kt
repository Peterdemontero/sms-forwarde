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
        private const val TAG         = "MoMoForwarder"
        private const val WORK_NAME   = "momo_webhook_sync"
        private const val PERIOD_NAME = "momo_webhook_sync_periodic"

        // Called when a send fails — one-time retry with exponential back-off.
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

        // Called once at app start. Runs every 15 minutes in the background
        // even when the app is closed. This is the permanent safety net:
        // if the server was down and all one-time retries expired, the next
        // 15-minute tick delivers the stuck SMS automatically — no one needs
        // to open the app, no SMS is permanently lost.
        fun schedulePeriodicSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<WebhookSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIOD_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
