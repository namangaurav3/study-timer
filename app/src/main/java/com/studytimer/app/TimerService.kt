package com.studytimer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.Vibrator
import androidx.core.app.NotificationCompat

class TimerService : Service(), SensorEventListener {
    private val h = Handler(Looper.getMainLooper())
    private var sm: SensorManager? = null
    private var prox: Sensor? = null
    private var near = false
    private var zDown = false
    private var last = 0L
    private var ticks = 0
    private var wl: PowerManager.WakeLock? = null
    private val startTask = Runnable { setRunning(true) }
    private val tickTask = object : Runnable {
        override fun run() { doTick(); h.postDelayed(this, 1000) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        if (intent?.action == "START") start() else stop()
        return START_NOT_STICKY
    }

    private fun start() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("timer", "Study timer", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        val n = notif("Place your phone face down to start")
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        if (TimerState.armed) return
        TimerState.armed = true
        TimerState.done = false
        TimerState.running = false
        val m = getSystemService(SENSOR_SERVICE) as SensorManager
        sm = m
        prox = m.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        TimerState.hasProx = prox != null
        m.registerListener(this, m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
        prox?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "studytimer:run").apply { acquire(12 * 60 * 60 * 1000L) }
        last = SystemClock.elapsedRealtime()
        h.post(tickTask)
    }

    private fun stop() {
        h.removeCallbacks(startTask)
        h.removeCallbacks(tickTask)
        if (TimerState.armed) {
            doTick()
            TimerState.running = false
            TimerState.armed = false
            try { sm?.unregisterListener(this) } catch (e: Exception) { }
        }
        Store.flush()
        wl?.let { if (it.isHeld) it.release() }
        wl = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun evaluate() {
        val covered = (prox == null || near) && zDown
        if (covered) {
            if (!TimerState.running) { h.removeCallbacks(startTask); h.postDelayed(startTask, 500) }
        } else {
            h.removeCallbacks(startTask)
            setRunning(false)
        }
    }

    private fun setRunning(r: Boolean) {
        if (!TimerState.armed || r == TimerState.running) return
        doTick()
        TimerState.running = r
        last = SystemClock.elapsedRealtime()
        update()
    }

    private fun doTick() {
        val now = SystemClock.elapsedRealtime()
        if (TimerState.running) {
            val d = now - last
            TimerState.elapsed += d
            Store.add(d)
            val target = Store.targetMin * 60000L
            if (Store.mode == "down" && TimerState.elapsed >= target) {
                TimerState.elapsed = target
                TimerState.running = false
                TimerState.done = true
                last = now
                alert()
                stop()
                return
            }
        }
        last = now
        ticks++
        if (ticks % 10 == 0) Store.flush()
        update()
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_PROXIMITY -> near = e.values[0] < e.sensor.maximumRange
            Sensor.TYPE_ACCELEROMETER -> {
                val z = e.values[2]
                zDown = if (zDown) z < -5f else z < -7.5f
            }
        }
        evaluate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600; val m = s % 3600 / 60; val x = s % 60
        return (if (h > 0) "$h:" else "") + "%02d:%02d".format(m, x)
    }

    private fun notif(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "timer")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Study Timer")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    private fun update() {
        if (!TimerState.armed) return
        val shown = if (Store.mode == "up") TimerState.elapsed
        else (Store.targetMin * 60000L - TimerState.elapsed).coerceAtLeast(0L)
        val text = if (TimerState.running) "Studying: " + fmt(shown) else "Paused. Place the phone face down to resume."
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, notif(text))
    }

    @Suppress("DEPRECATION")
    private fun alert() {
        try { (getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(700) } catch (e: Exception) { }
        val n = NotificationCompat.Builder(this, "timer")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Time’s up")
            .setContentText("Nice work. Your session is complete.")
            .setAutoCancel(true)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(2, n)
    }

    override fun onDestroy() {
        try { sm?.unregisterListener(this) } catch (e: Exception) { }
        wl?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
