package com.muslimtimekeeper.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class QiblaCompassView(context: Context) : View(context) {
    var azimuthDeg: Float = 0f
        set(value) {
            field = value
            invalidate()
        }
    var qiblaBearingDeg: Float = 136.5f
        set(value) {
            field = value
            invalidate()
        }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#38BDF8")
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0F172A")
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        color = Color.parseColor("#64748B")
    }
    private val dirPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        color = Color.WHITE
    }
    private val qiblaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#F59E0B")
    }
    private val alignedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#10B981")
    }

    fun isAlignedToQibla(): Boolean {
        val diff = ((qiblaBearingDeg - azimuthDeg + 540f) % 360f) - 180f
        return abs(diff) <= 8f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - 24f
        if (r <= 20f) return

        val aligned = isAlignedToQibla()
        ringPaint.color = if (aligned) Color.parseColor("#10B981") else Color.parseColor("#38BDF8")

        canvas.drawCircle(cx, cy, r, bgPaint)
        canvas.drawCircle(cx, cy, r, ringPaint)

        // Draw rotating compass dial (-azimuthDeg)
        canvas.save()
        canvas.rotate(-azimuthDeg, cx, cy)
        for (deg in 0 until 360 step 15) {
            val rad = Math.toRadians(deg.toDouble())
            val inner = if (deg % 90 == 0) r - 24f else r - 14f
            canvas.drawLine(
                cx + (inner * sin(rad)).toFloat(),
                cy - (inner * cos(rad)).toFloat(),
                cx + (r * sin(rad)).toFloat(),
                cy - (r * cos(rad)).toFloat(),
                tickPaint
            )
        }
        dirPaint.color = Color.parseColor("#EF4444")
        canvas.drawText("N", cx, cy - r + 58f, dirPaint)
        dirPaint.color = Color.parseColor("#CBD5E1")
        canvas.drawText("S", cx, cy + r - 32f, dirPaint)
        canvas.drawText("E", cx + r - 44f, cy + 12f, dirPaint)
        canvas.drawText("W", cx - r + 44f, cy + 12f, dirPaint)
        canvas.restore()

        // Draw Qibla Kaaba pointer (qiblaBearingDeg - azimuthDeg)
        canvas.save()
        canvas.rotate(qiblaBearingDeg - azimuthDeg, cx, cy)
        val arrow = Path().apply {
            moveTo(cx, cy - r + 26f)
            lineTo(cx - 22f, cy + 18f)
            lineTo(cx, cy + 2f)
            lineTo(cx + 22f, cy + 18f)
            close()
        }
        canvas.drawPath(arrow, if (aligned) alignedPaint else qiblaPaint)
        canvas.restore()

        // Center dot
        canvas.drawCircle(cx, cy, 10f, if (aligned) alignedPaint else qiblaPaint)
    }
}
