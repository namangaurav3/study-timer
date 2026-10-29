package com.studytimer.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var timeTv: TextView
    private lateinit var statusTv: TextView
    private lateinit var goBtn: TextView
    private lateinit var todayTv: TextView
    private lateinit var cmpTv: TextView
    private lateinit var chart: WeekChart

    // bg, fg, muted, accent, card
    private val themes = arrayOf(
        arrayOf("#0f1624", "#e8edf7", "#8b97b0", "#6ea8ff", "#18223a"),
        arrayOf("#12201a", "#e6f1e3", "#8aa592", "#9be37a", "#1b3025"),
        arrayOf("#241633", "#f3e9fb", "#a58fc0", "#ff8fb1", "#331f47"),
        arrayOf("#f2f1ec", "#1d2430", "#6b7280", "#2f6fed", "#e4e2da"),
        arrayOf("#1c1210", "#f8e9e1", "#b49488", "#ff9a3c", "#2c1c18")
    )
    private val presets = intArrayOf(25, 45, 60, 90)

    private fun c(i: Int) = Color.parseColor(themes[Store.theme][i])
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val loop = object : Runnable {
        override fun run() { refresh(); h.postDelayed(this, 250) }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Store.init(this)
        build()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() { super.onResume(); h.post(loop) }
    override fun onPause() { super.onPause(); h.removeCallbacks(loop) }

    private fun pill(t: String, bg: Int, fg: Int, onClick: () -> Unit): TextView = TextView(this).apply {
        text = t; setTextColor(fg); textSize = 16f; gravity = Gravity.CENTER
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(bg) }
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun row(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
    }

    private fun LinearLayout.addW(v: View) {
        addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6); marginStart = dp(6) })
    }

    private fun send(action: String) {
        val i = Intent(this, TimerService::class.java).setAction(action)
        if (action == "START" && Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun setMode(m: String) {
        if (TimerState.armed) return
        Store.mode = m; TimerState.elapsed = 0; TimerState.done = false; build()
    }

    private fun build() {
        window.statusBarColor = c(0); window.navigationBarColor = c(0)
        val sv = ScrollView(this).apply { setBackgroundColor(c(0)) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(28))
        }
        sv.addView(col)

        val up = Store.mode == "up"
        val modes = row()
        modes.addW(pill("Count up", if (up) c(3) else c(4), if (up) c(0) else c(1)) { setMode("up") })
        modes.addW(pill("Count down", if (!up) c(3) else c(4), if (!up) c(0) else c(1)) { setMode("down") })
        col.addView(modes)

        if (!up) {
            val pr = row()
            for (m in presets) {
                val sel = Store.targetMin == m
                pr.addW(pill("$m", if (sel) c(3) else c(4), if (sel) c(0) else c(1)) {
                    if (!TimerState.armed) { Store.targetMin = m; TimerState.elapsed = 0; TimerState.done = false; build() }
                })
            }
            col.addView(pr)
            val adj = row()
            adj.addW(pill("-5 min", c(4), c(1)) {
                if (!TimerState.armed) { Store.targetMin = (Store.targetMin - 5).coerceIn(1, 600); TimerState.elapsed = 0; build() }
            })
            adj.addW(pill("+5 min", c(4), c(1)) {
                if (!TimerState.armed) { Store.targetMin = (Store.targetMin + 5).coerceIn(1, 600); TimerState.elapsed = 0; build() }
            })
            col.addView(adj)
        }

        timeTv = TextView(this).apply {
            textSize = 64f; setTextColor(c(1)); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; fontFeatureSettings = "tnum"
            setPadding(0, dp(28), 0, dp(4))
        }
        col.addView(timeTv)
        statusTv = TextView(this).apply { textSize = 15f; setTextColor(c(2)); gravity = Gravity.CENTER }
        col.addView(statusTv)

        val ctl = row()
        goBtn = pill("Start session", c(3), c(0)) { send(if (TimerState.armed) "STOP" else "START") }
        goBtn.textSize = 18f; goBtn.typeface = Typeface.DEFAULT_BOLD
        ctl.addView(goBtn, LinearLayout.LayoutParams(0, -2, 2f).apply { marginEnd = dp(6) })
        ctl.addView(pill("Reset", c(4), c(1)) {
            if (!TimerState.armed) { TimerState.elapsed = 0; TimerState.done = false }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(ctl)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(c(4)) }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) }
        }
        todayTv = TextView(this).apply { textSize = 22f; setTextColor(c(1)); typeface = Typeface.DEFAULT_BOLD }
        cmpTv = TextView(this).apply { textSize = 14f; setTextColor(c(2)); setPadding(0, dp(2), 0, dp(10)) }
        val dim = Color.argb(110, Color.red(c(2)), Color.green(c(2)), Color.blue(c(2)))
        chart = WeekChart(this, c(1), c(2), c(3), dim)
        card.addView(todayTv); card.addView(cmpTv); card.addView(chart)
        col.addView(card)

        val th = row()
        th.gravity = Gravity.CENTER
        for (i in themes.indices) {
            val dot = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(themes[i][3]))
                    setStroke(dp(3), if (i == Store.theme) c(1) else c(4))
                }
                setOnClickListener { Store.theme = i; build() }
            }
            th.addView(dot, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginStart = dp(8); marginEnd = dp(8) })
        }
        col.addView(th)

        setContentView(sv)
        refresh()
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        val hh = s / 3600; val m = s % 3600 / 60; val x = s % 60
        return (if (hh > 0) "$hh:" else "") + "%02d:%02d".format(m, x)
    }

    private fun fmtD(ms: Long): String {
        val m = Math.round(ms / 60000.0).toInt()
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    private fun refresh() {
        val shown = if (Store.mode == "up") TimerState.elapsed
        else (Store.targetMin * 60000L - TimerState.elapsed).coerceAtLeast(0L)
        timeTv.text = fmt(shown)
        statusTv.text = when {
            TimerState.done -> "Done. Reset to start another round."
            !TimerState.armed -> "Start a session, then lay your phone screen down."
            TimerState.running -> "Face down, running. Pick the phone up to pause."
            else -> "Paused. Lay the phone screen down to resume."
        } + if (TimerState.armed && !TimerState.hasProx) "\nNo proximity sensor found, using tilt only." else ""
        goBtn.text = if (TimerState.armed) "End session" else "Start session"

        val w = Store.week(); val ti = Store.todayIndex(); val tot = w[ti]
        val os = w.filterIndexed { i, v -> i != ti && v > 0 }
        todayTv.text = fmtD(tot) + " today"
        cmpTv.text = if (os.isEmpty()) "Study on another day to start comparing."
        else if (tot > os.max()) "Your best day this week."
        else { val d = tot - os.average(); fmtD(abs(d).toLong()) + (if (d >= 0) " above" else " below") + " your average of the other days." }
        chart.invalidate()
    }
}
