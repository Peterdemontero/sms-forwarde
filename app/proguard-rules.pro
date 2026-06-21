# Keep WorkManager workers so they can be instantiated by class name
-keep class com.openclaw.smsforwarder.WebhookSyncWorker { *; }

# Keep JSON org classes used in PendingQueue
-keep class org.json.** { *; }
