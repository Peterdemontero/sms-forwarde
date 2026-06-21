package com.openclaw.smsforwarder

import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object WebhookSender {
    // Exposed so SmsReceiver can reuse this thread for goAsync() work
    val executor = Executors.newSingleThreadExecutor()
    private const val TAG = "MoMoForwarder"

    /** Async — fire-and-forget with optional result callback (used by test button). */
    fun send(webhookUrl: String, key: String, smsBody: String, onResult: ((Boolean) -> Unit)? = null) {
        executor.execute {
            onResult?.invoke(sendSync(webhookUrl, key, smsBody))
        }
    }

    /** Synchronous — call from a background thread (used by SmsReceiver + WorkManager). */
    fun sendSync(webhookUrl: String, key: String, smsBody: String): Boolean {
        return try {
            val json = buildJson(smsBody, key)
            val conn = URL(webhookUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.outputStream.use { OutputStreamWriter(it).use { w -> w.write(json) } }
            val code = conn.responseCode
            Log.d(TAG, "Response $code — ${smsBody.take(50)}")
            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
            false
        }
    }

    private fun buildJson(sms: String, key: String): String {
        return """{"sms":${escape(sms)},"webhook_key":${escape(key)}}"""
    }

    private fun escape(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"'  -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}
