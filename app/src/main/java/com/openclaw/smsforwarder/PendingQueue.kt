package com.openclaw.smsforwarder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A lightweight offline queue backed by SharedPreferences.
 * Each entry is a pending webhook call that failed due to no network.
 * WorkManager drains this queue when connectivity returns.
 */
object PendingQueue {

    private const val PREFS = "momo_queue"
    private const val KEY   = "pending"
    private const val MAX_ATTEMPTS = 10

    data class Item(
        val id: String,
        val url: String,
        val webhookKey: String,
        val sms: String,
        val addedAt: Long,
        val attempts: Int
    )

    fun add(context: Context, url: String, webhookKey: String, sms: String): String {
        val id = "${System.currentTimeMillis()}_${sms.hashCode()}"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = load(prefs)
        // Deduplicate locally: don't add if same SMS is already queued
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).getString("sms") == sms &&
                arr.getJSONObject(i).getString("url") == url) return arr.getJSONObject(i).getString("id")
        }
        arr.put(JSONObject().apply {
            put("id", id)
            put("url", url)
            put("key", webhookKey)
            put("sms", sms)
            put("addedAt", System.currentTimeMillis())
            put("attempts", 0)
        })
        prefs.edit().putString(KEY, arr.toString()).apply()
        return id
    }

    fun remove(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = load(prefs)
        val updated = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("id") != id) updated.put(obj)
        }
        prefs.edit().putString(KEY, updated.toString()).apply()
    }

    fun incrementAttempts(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = load(prefs)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("id") == id) {
                val attempts = obj.getInt("attempts") + 1
                if (attempts >= MAX_ATTEMPTS) {
                    // Give up after MAX_ATTEMPTS — drop the entry so we don't retry forever
                    remove(context, id)
                    return
                }
                obj.put("attempts", attempts)
            }
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun getAll(context: Context): List<Item> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = load(prefs)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Item(
                id       = o.getString("id"),
                url      = o.getString("url"),
                webhookKey = o.optString("key", ""),
                sms      = o.getString("sms"),
                addedAt  = o.getLong("addedAt"),
                attempts = o.getInt("attempts")
            )
        }
    }

    fun size(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return load(prefs).length()
    }

    private fun load(prefs: android.content.SharedPreferences): JSONArray {
        val raw = prefs.getString(KEY, null) ?: return JSONArray()
        return try { JSONArray(raw) } catch (e: Exception) { JSONArray() }
    }
}
