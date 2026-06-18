package com.openclaw.smsforwarder

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var urlInput: EditText
    private lateinit var keyInput: EditText
    private lateinit var enableSwitch: SwitchCompat
    private lateinit var filtersContainer: LinearLayout
    private lateinit var filterInput: EditText
    private lateinit var statusText: TextView
    private lateinit var lastFwdText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("smsfw", MODE_PRIVATE)
        setContentView(buildUI())
        loadSettings()
        requestSmsPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshLastForward()
    }

    // ── UI ───────────────────────────────────────────────────────────────

    private fun buildUI(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
        }
        val root = column().apply {
            setPadding(dp(20), dp(24), dp(20), dp(40))
        }
        scroll.addView(root)

        // Header
        root.addView(textView("📱 MoMo SMS Forwarder", 21f, Color.WHITE, bold = true))
        root.addView(textView("Forwards MoMo SMS to your OpenClaw agent", 13f, Color.parseColor("#64748B")))
        root.addView(gap(20))

        // Last forward status pill
        lastFwdText = textView("No messages forwarded yet", 12f, Color.parseColor("#22C55E"))
        root.addView(lastFwdText)
        root.addView(gap(16))

        // Enable card
        val card0 = card()
        val row0 = row()
        val col0 = column().apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        col0.addView(textView("Forwarding Active", 15f, Color.WHITE, bold = true))
        col0.addView(textView("Turn on to start forwarding SMS", 12f, Color.parseColor("#64748B")))
        row0.addView(col0)
        enableSwitch = SwitchCompat(this)
        row0.addView(enableSwitch)
        card0.addView(row0)
        root.addView(card0)
        root.addView(gap(12))

        // URL card
        val card1 = card()
        card1.addView(fieldLabel("WEBHOOK URL"))
        urlInput = input("https://xxxx.ngrok-free.app/webhook/sms/your-agent-id")
        card1.addView(urlInput)
        root.addView(card1)
        root.addView(gap(12))

        // Key card
        val card2 = card()
        card2.addView(fieldLabel("WEBHOOK KEY  (leave blank if not set)"))
        keyInput = input("your-secret-key")
        card2.addView(keyInput)
        root.addView(card2)
        root.addView(gap(12))

        // Filters card
        val card3 = card()
        card3.addView(fieldLabel("SENDER FILTERS"))
        card3.addView(textView("Only forward SMS from these senders", 12f, Color.parseColor("#64748B")))
        card3.addView(gap(10))
        filtersContainer = column()
        card3.addView(filtersContainer)
        card3.addView(gap(8))

        val addRow = row()
        filterInput = input("e.g. MobileMoney, 1016, MTN").apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        addRow.addView(filterInput)
        addRow.addView(gap(8))
        addRow.addView(Button(this).apply {
            text = "+  Add"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#6366F1"))
            setPadding(dp(14), 0, dp(14), 0)
            setOnClickListener {
                val t = filterInput.text.toString().trim()
                if (t.isNotEmpty()) { addChip(t); filterInput.setText("") }
            }
        })
        card3.addView(addRow)
        root.addView(card3)
        root.addView(gap(24))

        // Save button
        root.addView(Button(this).apply {
            text = "💾   Save Settings"
            textSize = 15f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#22C55E"))
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = fullWidth(bottom = 10)
            setOnClickListener { save() }
        })

        // Test button
        root.addView(Button(this).apply {
            text = "🧪   Send Test"
            textSize = 15f
            setTextColor(Color.parseColor("#A5B4FC"))
            setBackgroundColor(Color.parseColor("#1E1B4B"))
            setPadding(0, dp(14), 0, dp(14))
            layoutParams = fullWidth(bottom = 10)
            setOnClickListener { sendTest() }
        })

        statusText = textView("", 13f, Color.parseColor("#64748B"))
        root.addView(statusText)

        return scroll
    }

    private fun addChip(text: String) {
        val row = row().apply {
            tag = text
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(dp(12), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(6)
            }
        }
        row.addView(textView("✓  $text", 14f, Color.parseColor("#22C55E")).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        row.addView(Button(this).apply {
            text = "✕"
            textSize = 13f
            setTextColor(Color.parseColor("#EF4444"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { filtersContainer.removeView(row) }
        })
        filtersContainer.addView(row)
    }

    private fun getFilters() = (0 until filtersContainer.childCount)
        .mapNotNull { filtersContainer.getChildAt(it).tag as? String }

    // ── Logic ─────────────────────────────────────────────────────────────

    private fun loadSettings() {
        urlInput.setText(prefs.getString("url", ""))
        keyInput.setText(prefs.getString("key", ""))
        enableSwitch.isChecked = prefs.getBoolean("enabled", false)
        filtersContainer.removeAllViews()
        val saved = prefs.getString("filters", "MobileMoney") ?: "MobileMoney"
        saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { addChip(it) }
    }

    private fun save() {
        prefs.edit()
            .putString("url", urlInput.text.toString().trim())
            .putString("key", keyInput.text.toString().trim())
            .putString("filters", getFilters().joinToString(","))
            .putBoolean("enabled", enableSwitch.isChecked)
            .apply()
        status("✓ Settings saved", "#22C55E")
    }

    private fun sendTest() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) { status("⚠ Enter a webhook URL first", "#EF4444"); return }
        val key = keyInput.text.toString().trim()
        status("Sending test...", "#64748B")
        WebhookSender.send(
            url, key,
            "You have received GHS 5.00 from 0244123456 MobileMoney on 18-06-2025 at 10:30 AM. " +
            "New balance: GHS 105.00. Financial Transaction Id TEST123456."
        ) { ok ->
            runOnUiThread {
                if (ok) status("✓ Test sent — check your SMS Logs tab", "#22C55E")
                else    status("✗ Failed — check URL and key are correct", "#EF4444")
            }
        }
    }

    private fun refreshLastForward() {
        val last = prefs.getLong("last_forward", 0L)
        if (last == 0L) return
        val diff = (System.currentTimeMillis() - last) / 1000
        val label = when {
            diff < 60    -> "${diff}s ago"
            diff < 3600  -> "${diff / 60}m ago"
            diff < 86400 -> "${diff / 3600}h ago"
            else -> SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(last))
        }
        lastFwdText.text = "Last forwarded: $label"
    }

    private fun status(msg: String, hex: String) {
        statusText.text = msg
        statusText.setTextColor(Color.parseColor(hex))
    }

    private fun requestSmsPermissions() {
        val needed = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty())
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
    }

    // ── View helpers ──────────────────────────────────────────────────────

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun card() = column().apply {
        setBackgroundColor(Color.parseColor("#1E293B"))
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    private fun gap(sizeDp: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(-1, dp(sizeDp))
    }

    private fun textView(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }

    private fun fieldLabel(text: String) = textView(text, 10f, Color.parseColor("#64748B")).apply {
        setTypeface(null, Typeface.BOLD)
        letterSpacing = 0.1f
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) }
    }

    private fun input(hint: String) = EditText(this).apply {
        this.hint = hint
        setHintTextColor(Color.parseColor("#334155"))
        setTextColor(Color.parseColor("#E2E8F0"))
        setBackgroundColor(Color.parseColor("#0F172A"))
        setPadding(dp(10), dp(10), dp(10), dp(10))
        textSize = 13f
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun fullWidth(bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = dp(bottom)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
