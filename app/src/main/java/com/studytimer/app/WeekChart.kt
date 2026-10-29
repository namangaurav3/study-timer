package com.studytimer.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class WeekChart(ctx: Context, private val fg: Int, private val mut: Int, private val acc: Int, private val dim: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density
    private val labels = arrayOf("M", "T", "W", "T", "F", "S", "S")

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (150 * d).toInt())
    }

    private fun short(ms: Long): String {
        val m = Math.round(ms / 60000.0).toInt()
        return if (m >= 60) "${m / 60}h${m % 60}" else "${m}m"
    }

    override fun onDraw(c: Canvas) {
        val w = Store.week()
        val ti = Store.todayIndex()
        val mx = maxOf(w.max(), 3600000L).toFloat()
        val gap = 8 * d
        val bw = (width - gap * 6) / 7
        val top = 18 * d
        val bottom = height - 22 * d
        p.textAlign = Paint.Align.CENTER
        p.textSize = 11 * d
        for (i in 0..6) {
            val x = i * (bw + gap)
            val hgt = maxOf(3 * d, w[i] / mx * (bottom - top))
            p.color = if (i == ti) acc else dim
            c.drawRoundRect(RectF(x, bottom - hgt, x + bw, bottom), 6 * d, 6 * d, p)
            p.color = if (i == ti) fg else mut
            c.drawText(labels[i], x + bw / 2, height - 5 * d, p)
            if (w[i] > 0) { p.color = mut; c.drawText(short(w[i]), x + bw / 2, bottom - hgt - 4 * d, p) }
        }
    }
}
