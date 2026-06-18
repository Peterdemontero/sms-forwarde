package com.openclaw.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "MoMoForwarder"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences("smsfw", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return

        val url = prefs.getString("url", "").orEmpty()
        if (url.isEmpty()) return

        val key     = prefs.getString("key", "").orEmpty()
        val filters = prefs.getString("filters", "MobileMoney").orEmpty()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        // Group by sender so multi-part SMS are concatenated before forwarding
        val bySender = LinkedHashMap<String, StringBuilder>()
        for (msg in messages) {
            val sender = msg.originatingAddress ?: "unknown"
            bySender.getOrPut(sender) { StringBuilder() }.append(msg.messageBody)
        }

        for ((sender, body) in bySender) {
            val matched = filters.isEmpty() || filters.any { sender.contains(it, ignoreCase = true) }
            if (!matched) {
                Log.d(TAG, "Skipped from: $sender")
                continue
            }
            Log.d(TAG, "Forwarding from: $sender")
            WebhookSender.send(url, key, body.toString())
            prefs.edit().putLong("last_forward", System.currentTimeMillis()).apply()
        }
    }
}
