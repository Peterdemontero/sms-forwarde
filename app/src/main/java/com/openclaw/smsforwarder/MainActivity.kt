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
    private lateinit var queueText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("smsfw", MODE_PRIVATE)
        setContentView(buildUI())
        loadSettings()
        requestSmsPermissions()
        // Flush any SMS that failed while the app was closed
        WebhookSyncWorker.enqueue(this)
    }

    override fun onResume() {
        super.onResume()
        refreshLastForward()
        refreshQueueBadge()
        WebhookSyncWorker.enqueue(this)
    }

    // ── UI ───────────────────────────────────────────────────────────────

    private fun buildUI(): View {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#0F172A"))

        val root = vbox()
        root.setPadding(dp(20), dp(24), dp(20), dp(40))
        scroll.addView(root)

        // Header
        root.addView(text("📱 MoMo SMS Forwarder", 21f, Color.WHITE, bold = true))
        root.addView(text("Forwards MoMo SMS to your OpenClaw agent", 13f, Color.parseColor("#64748B")))
        root.addView(gap(20))

        // Last forward badge
        lastFwdText = text("No messages forwarded yet", 12f, Color.parseColor("#22C55E"))
        root.addView(lastFwdText)

        // Offline queue badge
        queueText = text("", 12f, Color.parseColor("#F59E0B"))
        root.addView(queueText)
        root.addView(gap(16))

        // Enable toggle card
        val toggleCard = card()
        val toggleRow = hbox()

        val toggleLabels = vbox()
        toggleLabels.setLayoutParams(LinearLayout.LayoutParams(0, -2, 1f))
        toggleLabels.addView(text("Forwarding Active", 15f, Color.WHITE, bold = true))
        toggleLabels.addView(text("Turn on to start forwarding SMS", 12f, Color.parseColor("#64748B")))
        toggleRow.addView(toggleLabels)

        enableSwitch = SwitchCompat(this)
        toggleRow.addView(enableSwitch)
        toggleCard.addView(toggleRow)
        root.addView(toggleCard)
        root.addView(gap(12))

        // Webhook URL card
        val urlCard = card()
        urlCard.addView(fieldLabel("WEBHOOK URL"))
        urlInput = input("https://xxxx.ngrok-free.app/webhook/sms/your-agent-id")
        urlCard.addView(urlInput)
        root.addView(urlCard)
        root.addView(gap(12))

        // Webhook key card
        val keyCard = card()
        keyCard.addView(fieldLabel("WEBHOOK KEY  (leave blank if not set)"))
        keyInput = input("your-secret-key")
        keyCard.addView(keyInput)
        root.addView(keyCard)
        root.addView(gap(12))

        // Filters card
        val filterCard = card()
        filterCard.addView(fieldLabel("SENDER ID FILTERS"))
        filterCard.addView(text("Forward SMS only from these sender IDs", 12f, Color.parseColor("#64748B")))
        filterCard.addView(gap(10))

        // Quick-add preset buttons for common Ghana MoMo providers
        filterCard.addView(fieldLabel("QUICK ADD"))
        val presetRow1 = hbox()
        val presetRow2 = hbox()
        listOf(
            "MobileMoney" to "#1E3A5F",  // MTN MoMo
            "1016"        to "#1E3A5F",  // MTN shortcode
            "VodaCash"    to "#2D1B69",  // Vodafone Cash
            "AirtelTigo"  to "#2D1B69",  // AirtelTigo Money
            "AT Money"    to "#1A3D2B",  // AirtelTigo alt
            "GhIPSS"      to "#1A3D2B",  // GhIPSS transfers
        ).forEachIndexed { i, (label, color) ->
            val btn = Button(this)
            btn.text = "+ $label"
            btn.textSize = 11f
            btn.setTextColor(Color.parseColor("#CBD5E1"))
            btn.setBackgroundColor(Color.parseColor(color))
            val lp = LinearLayout.LayoutParams(0, -2, 1f)
            lp.marginEnd = dp(4)
            btn.setLayoutParams(lp)
            btn.setPadding(dp(4), dp(6), dp(4), dp(6))
            btn.setOnClickListener { addChip(label) }
            if (i < 3) presetRow1.addView(btn) else presetRow2.addView(btn)
        }
        filterCard.addView(presetRow1)
        filterCard.addView(gap(4))
        filterCard.addView(presetRow2)
        filterCard.addView(gap(10))

        // Active filter chips
        filterCard.addView(fieldLabel("ACTIVE FILTERS"))
        filtersContainer = vbox()
        filterCard.addView(filtersContainer)
        filterCard.addView(gap(8))

        // Custom sender ID input
        val addRow = hbox()
        filterInput = input("Custom sender ID...")
        filterInput.setLayoutParams(LinearLayout.LayoutParams(0, -2, 1f))
        addRow.addView(filterInput)
        addRow.addView(gap(8))

        val addBtn = Button(this)
        addBtn.text = "+  Add"
        addBtn.setTextColor(Color.WHITE)
        addBtn.setBackgroundColor(Color.parseColor("#6366F1"))
        addBtn.setPadding(dp(14), 0, dp(14), 0)
        addBtn.setOnClickListener {
            val t = filterInput.text.toString().trim()
            if (t.isNotEmpty()) { addChip(t); filterInput.setText("") }
        }
        addRow.addView(addBtn)
        filterCard.addView(addRow)
        root.addView(filterCard)
        root.addView(gap(24))

        // Save button
        val saveBtn = Button(this)
        saveBtn.text = "💾   Save Settings"
        saveBtn.textSize = 15f
        saveBtn.setTextColor(Color.WHITE)
        saveBtn.setBackgroundColor(Color.parseColor("#22C55E"))
        saveBtn.setPadding(0, dp(14), 0, dp(14))
        saveBtn.setLayoutParams(wrapWidth(bottom = 10))
        saveBtn.setOnClickListener { save() }
        root.addView(saveBtn)

        // Test button
        val testBtn = Button(this)
        testBtn.text = "🧪   Send Test"
        testBtn.textSize = 15f
        testBtn.setTextColor(Color.parseColor("#A5B4FC"))
        testBtn.setBackgroundColor(Color.parseColor("#1E1B4B"))
        testBtn.setPadding(0, dp(14), 0, dp(14))
        testBtn.setLayoutParams(wrapWidth(bottom = 10))
        testBtn.setOnClickListener { sendTest() }
        root.addView(testBtn)

        statusText = text("", 13f, Color.parseColor("#64748B"))
        root.addView(statusText)

        return scroll
    }

    private fun addChip(label: String) {
        val chipRow = hbox()
        chipRow.tag = label
        chipRow.setBackgroundColor(Color.parseColor("#1E293B"))
        chipRow.setPadding(dp(12), dp(8), dp(8), dp(8))
        val chipParams = LinearLayout.LayoutParams(-1, -2)
        chipParams.bottomMargin = dp(6)
        chipRow.setLayoutParams(chipParams)

        val chipText = TextView(this)
        chipText.text = "✓  $label"
        chipText.textSize = 14f
        chipText.setTextColor(Color.parseColor("#22C55E"))
        chipText.setLayoutParams(LinearLayout.LayoutParams(0, -2, 1f))
        chipRow.addView(chipText)

        val chipBtn = Button(this)
        chipBtn.text = "✕"
        chipBtn.textSize = 13f
        chipBtn.setTextColor(Color.parseColor("#EF4444"))
        chipBtn.setBackgroundColor(Color.TRANSPARENT)
        chipBtn.setOnClickListener { filtersContainer.removeView(chipRow) }
        chipRow.addView(chipBtn)

        filtersContainer.addView(chipRow)
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
            "Payment received for GHS 5.00 from TEST ACCOUNT  Current Balance: GHS 105.00 . " +
            "Available Balance: GHS 105.00. Reference: TEST ACCOUNT,0244123456,1 from VODAFONE. " +
            "Transaction ID: TEST000000. TRANSACTION FEE: 0.00"
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
        lastFwdText.text = "Last forwarded: " + when {
            diff < 60    -> "${diff}s ago"
            diff < 3600  -> "${diff / 60}m ago"
            diff < 86400 -> "${diff / 3600}h ago"
            else -> SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(last))
        }
    }

    private fun refreshQueueBadge() {
        val n = PendingQueue.size(this)
        queueText.text = if (n > 0) "⏳ $n SMS queued — will retry when online" else ""
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

    private fun vbox(): LinearLayout {
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.setLayoutParams(LinearLayout.LayoutParams(-1, -2))
        return ll
    }

    private fun hbox(): LinearLayout {
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.HORIZONTAL
        ll.gravity = Gravity.CENTER_VERTICAL
        ll.setLayoutParams(LinearLayout.LayoutParams(-1, -2))
        return ll
    }

    private fun card(): LinearLayout {
        val ll = vbox()
        ll.setBackgroundColor(Color.parseColor("#1E293B"))
        ll.setPadding(dp(16), dp(14), dp(16), dp(14))
        return ll
    }

    private fun gap(sizeDp: Int): View {
        val v = View(this)
        v.setLayoutParams(LinearLayout.LayoutParams(-1, dp(sizeDp)))
        return v
    }

    private fun text(str: String, size: Float, color: Int, bold: Boolean = false): TextView {
        val tv = TextView(this)
        tv.text = str
        tv.textSize = size
        tv.setTextColor(color)
        if (bold) tv.setTypeface(null, Typeface.BOLD)
        tv.setLayoutParams(LinearLayout.LayoutParams(-1, -2))
        return tv
    }

    private fun fieldLabel(str: String): TextView {
        val tv = text(str, 10f, Color.parseColor("#64748B"))
        tv.setTypeface(null, Typeface.BOLD)
        tv.letterSpacing = 0.1f
        val p = LinearLayout.LayoutParams(-1, -2)
        p.bottomMargin = dp(6)
        tv.setLayoutParams(p)
        return tv
    }

    private fun input(hint: String): EditText {
        val et = EditText(this)
        et.hint = hint
        et.setHintTextColor(Color.parseColor("#334155"))
        et.setTextColor(Color.parseColor("#E2E8F0"))
        et.setBackgroundColor(Color.parseColor("#0F172A"))
        et.setPadding(dp(10), dp(10), dp(10), dp(10))
        et.textSize = 13f
        et.setLayoutParams(LinearLayout.LayoutParams(-1, -2))
        return et
    }

    private fun wrapWidth(bottom: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(-1, -2)
        p.bottomMargin = dp(bottom)
        return p
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
