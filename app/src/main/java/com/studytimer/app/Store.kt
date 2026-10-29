package com.studytimer.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object TimerState {
    @Volatile var armed = false
    @Volatile var running = false
    @Volatile var done = false
    @Volatile var elapsed = 0L
    @Volatile var hasProx = true
}

object Store {
    private lateinit var sp: SharedPreferences
    private var days = JSONObject()

    fun init(c: Context) {
        if (::sp.isInitialized) return
        sp = c.applicationContext.getSharedPreferences("st", Context.MODE_PRIVATE)
        days = try { JSONObject(sp.getString("days", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
    }

    var mode: String
        get() = sp.getString("mode", "up") ?: "up"
        set(v) { sp.edit().putString("mode", v).apply() }
    var targetMin: Int
        get() = sp.getInt("target", 25)
        set(v) { sp.edit().putInt("target", v).apply() }
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) { sp.edit().putInt("theme", v).apply() }

    fun key(cal: Calendar = Calendar.getInstance()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

    @Synchronized fun add(ms: Long) { val k = key(); days.put(k, days.optLong(k, 0L) + ms) }
    @Synchronized fun flush() { sp.edit().putString("days", days.toString()).apply() }
    @Synchronized fun get(k: String): Long = days.optLong(k, 0L)

    fun todayIndex(): Int = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7

    fun week(): LongArray {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_MONTH, -todayIndex())
        return LongArray(7) { val v = get(key(c)); c.add(Calendar.DAY_OF_MONTH, 1); v }
    }
}
