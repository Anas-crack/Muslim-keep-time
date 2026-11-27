package com.muslimtimekeeper.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated Prayer Sky & Mosque Hero View:
 * Dynamically shifts sky colors, sun/moon position, twinkling stars, drifting clouds,
 * and gliding birds for FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, and ISHA.
 */
class MosqueSkyView(context: Context) : View(context) {
    var skyPhase: String = "DHUHR" // FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA
    var nextPrayerLabel: String = "الظهر بعد"
    var countdownHms: String = "00:00:00"
    var locationLabel: String = "📍 الزقازيق، مصر"

    private var animPhase: Float = 0f
    private var lastFrameMs: Long = 0L

    private val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val celPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val birdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val hillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mosquePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val domePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val timerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val locPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val starCoords = arrayOf(
        0.10f to 0.14f, 0.22f to 0.09f, 0.34f to 0.18f, 0.48f to 0.08f,
        0.62f to 0.15f, 0.76f to 0.10f, 0.88f to 0.19f, 0.16f to 0.28f,
        0.30f to 0.30f, 0.70f to 0.26f, 0.84f to 0.32f, 0.54f to 0.22f
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = System.currentTimeMillis()
        if (lastFrameMs == 0L) lastFrameMs = now
        val dt = ((now - lastFrameMs).coerceIn(0L, 60L)) / 1000f
        lastFrameMs = now
        animPhase = (animPhase + dt) % 3600f

        val isNight = (skyPhase == "ISHA" || skyPhase == "MAGHRIB" || skyPhase == "FAJR")
        val skyColors = when (skyPhase) {
            "FAJR" -> intArrayOf(Color.parseColor("#141E46"), Color.parseColor("#3B3B7A"), Color.parseColor("#E69A8D"))
            "SUNRISE" -> intArrayOf(Color.parseColor("#1D4ED8"), Color.parseColor("#60A5FA"), Color.parseColor("#FDE68A"))
            "DHUHR" -> intArrayOf(Color.parseColor("#0EA5E9"), Color.parseColor("#7DD3FC"), Color.parseColor("#E0F2FE"))
            "ASR" -> intArrayOf(Color.parseColor("#1E3A8A"), Color.parseColor("#EA580C"), Color.parseColor("#FDBA74"))
            "MAGHRIB" -> intArrayOf(Color.parseColor("#090D1F"), Color.parseColor("#3B1D4A"), Color.parseColor("#C2410C"))
            else -> intArrayOf(Color.parseColor("#030712"), Color.parseColor("#0B1633"), Color.parseColor("#172554"))
        }

        val clipPath = Path().apply { addRoundRect(RectF(0f, 0f, w, h), 36f, 36f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clipPath)

        // 1. Dynamic Sky Gradient
        skyPaint.shader = LinearGradient(0f, 0f, 0f, h, skyColors, floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, skyPaint)

        // 2. Twinkling Stars (Fajr, Maghrib, Isha)
        if (isNight) {
            val baseAlpha = when (skyPhase) {
                "ISHA" -> 235
                "MAGHRIB" -> 165
                else -> 125
            }
            for (i in starCoords.indices) {
                val (sx, sy) = starCoords[i]
                val twinkle = (0.45f + 0.55f * sin(animPhase * 2.2f + i * 1.3f))
                starPaint.alpha = (baseAlpha * twinkle).toInt().coerceIn(30, 255)
                canvas.drawCircle(w * sx, h * sy, if (i % 3 == 0) 3.2f else 2.1f, starPaint)
            }
        }

        // 3. Sun or Crescent Moon with Animated Glow
        val pulse = 1f + 0.06f * sin(animPhase * 1.8f)
        val cxCel = when (skyPhase) {
            "FAJR" -> w * 0.80f
            "SUNRISE" -> w * 0.82f
            "DHUHR" -> w * 0.84f
            "ASR" -> w * 0.66f // Setting behind the mosque dome!
            "MAGHRIB" -> w * 0.22f
            else -> w * 0.82f
        }
        val cyCel = when (skyPhase) {
            "FAJR" -> h * 0.24f
            "SUNRISE" -> h * 0.34f
            "DHUHR" -> h * 0.20f
            "ASR" -> h * 0.58f // Low golden-red sun behind mosque!
            "MAGHRIB" -> h * 0.24f
            else -> h * 0.20f
        }
        val celR = if (skyPhase == "ASR") w * 0.10f else w * 0.065f
        val celColor = when (skyPhase) {
            "ASR" -> Color.parseColor("#FF5722")
            "SUNRISE" -> Color.parseColor("#FDE047")
            "DHUHR" -> Color.parseColor("#FFFBEB")
            else -> Color.parseColor("#FEF08A")
        }
        glowPaint.shader = RadialGradient(
            cxCel, cyCel, celR * 2.4f * pulse,
            intArrayOf(Color.argb(140, Color.red(celColor), Color.green(celColor), Color.blue(celColor)), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cxCel, cyCel, celR * 2.4f * pulse, glowPaint)
        celPaint.color = celColor
        canvas.drawCircle(cxCel, cyCel, celR, celPaint)
        if (skyPhase == "ISHA" || skyPhase == "MAGHRIB" || skyPhase == "FAJR") {
            // Cut crescent moon shadow
            celPaint.color = skyColors[0]
            canvas.drawCircle(cxCel - celR * 0.38f, cyCel - celR * 0.14f, celR * 0.86f, celPaint)
        }

        // 4. Gently Drifting Clouds & Gliding Birds (Daytime / Sunrise / Asr)
        if (skyPhase == "DHUHR" || skyPhase == "SUNRISE" || skyPhase == "ASR") {
            cloudPaint.color = if (skyPhase == "ASR") Color.argb(75, 255, 214, 170) else Color.argb(115, 255, 255, 255)
            val drift1 = ((animPhase * 12f) % (w + 180f)) - 90f
            val drift2 = ((animPhase * 7f + w * 0.55f) % (w + 180f)) - 90f
            drawCloud(canvas, drift1, h * 0.22f, 32f)
            drawCloud(canvas, drift2, h * 0.34f, 24f)

            birdPaint.color = if (skyPhase == "ASR") Color.parseColor("#431407") else Color.parseColor("#1E293B")
            for (b in 0..4) {
                val bx = ((w * 0.35f + b * 24f + animPhase * (14f + b * 1.5f)) % (w + 60f)) - 30f
                val by = h * (0.46f + (b % 3) * 0.035f) + sin(animPhase * 5f + b) * 4f
                val flap = sin(animPhase * 8f + b * 1.1f) * 5f
                canvas.drawLine(bx - 7f, by - flap, bx, by, birdPaint)
                canvas.drawLine(bx, by, bx + 7f, by - flap, birdPaint)
            }
        }

        // 5. Soft Layered Hills Behind Mosque
        hillPaint.color = when (skyPhase) {
            "ISHA" -> Color.parseColor("#0A1529")
            "MAGHRIB" -> Color.parseColor("#2A1530")
            "ASR" -> Color.parseColor("#7C2D12")
            "FAJR" -> Color.parseColor("#282C54")
            else -> Color.parseColor("#86B87A")
        }
        val hillPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.78f)
            quadTo(w * 0.25f, h * 0.66f, w * 0.52f, h * 0.77f)
            quadTo(w * 0.80f, h * 0.86f, w, h * 0.70f)
            lineTo(w, h)
            close()
        }
        canvas.drawPath(hillPath, hillPaint)

        // 6. Architectural Mosque Silhouette (Dome + Twin Minarets + Arches)
        val wallColor = when (skyPhase) {
            "ISHA" -> Color.parseColor("#111C33")
            "MAGHRIB" -> Color.parseColor("#1F162E")
            "ASR" -> Color.parseColor("#3B1F2B")
            "FAJR" -> Color.parseColor("#252B4F")
            else -> Color.parseColor("#F8FAFC")
        }
        val domeColor = when (skyPhase) {
            "ISHA", "MAGHRIB" -> Color.parseColor("#D97706")
            "ASR" -> Color.parseColor("#EA580C")
            "FAJR" -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#F59E0B")
        }
        val winColor = if (isNight) Color.parseColor("#FDE047") else Color.parseColor("#0284C7")

        mosquePaint.color = wallColor
        domePaint.color = domeColor
        windowPaint.color = winColor

        val baseTop = h * 0.74f
        // Main mosque body
        canvas.drawRect(w * 0.14f, baseTop, w * 0.86f, h, mosquePaint)
        // Center tall entrance gate
        canvas.drawRect(w * 0.36f, h * 0.65f, w * 0.64f, h, mosquePaint)
        // Main golden dome
        val domeRect = RectF(w * 0.52f, h * 0.56f, w * 0.78f, h * 0.80f)
        canvas.drawArc(domeRect, 180f, 180f, true, domePaint)
        // Secondary dome
        val dome2 = RectF(w * 0.25f, h * 0.62f, w * 0.45f, h * 0.78f)
        canvas.drawArc(dome2, 180f, 180f, true, domePaint)
        // Left & Right Minarets
        canvas.drawRect(w * 0.17f, h * 0.54f, w * 0.22f, h, mosquePaint)
        canvas.drawArc(RectF(w * 0.165f, h * 0.50f, w * 0.225f, h * 0.57f), 180f, 180f, true, domePaint)
        canvas.drawRect(w * 0.78f, h * 0.52f, w * 0.83f, h, mosquePaint)
        canvas.drawArc(RectF(w * 0.775f, h * 0.48f, w * 0.835f, h * 0.55f), 180f, 180f, true, domePaint)

        // Glowing arched windows & main door
        canvas.drawRoundRect(RectF(w * 0.46f, h * 0.76f, w * 0.54f, h), 24f, 24f, windowPaint)
        for (wx in floatArrayOf(0.26f, 0.32f, 0.68f, 0.74f)) {
            canvas.drawRoundRect(RectF(w * wx, h * 0.80f, w * (wx + 0.032f), h * 0.90f), 12f, 12f, windowPaint)
        }

        // 7. Crisp Overlay Typography (Next Prayer Label + Giant HH:MM:SS Countdown + City)
        val txtColor = if (skyPhase == "DHUHR" || skyPhase == "SUNRISE") Color.parseColor("#0F172A") else Color.WHITE
        val subColor = if (skyPhase == "DHUHR" || skyPhase == "SUNRISE") Color.parseColor("#0369A1") else Color.parseColor("#FDE68A")

        labelPaint.color = subColor
        labelPaint.textSize = h * 0.095f
        canvas.drawText(nextPrayerLabel, w * 0.5f, h * 0.19f, labelPaint)

        timerPaint.color = txtColor
        timerPaint.textSize = h * 0.22f
        canvas.drawText(countdownHms, w * 0.5f, h * 0.39f, timerPaint)

        locPaint.color = txtColor
        locPaint.textSize = h * 0.075f
        canvas.drawText(locationLabel, w * 0.5f, h * 0.49f, locPaint)

        canvas.restore()
        postInvalidateOnAnimation()
    }

    private fun drawCloud(canvas: Canvas, x: Float, y: Float, r: Float) {
        canvas.drawCircle(x, y, r, cloudPaint)
        canvas.drawCircle(x + r * 0.9f, y - r * 0.2f, r * 0.85f, cloudPaint)
        canvas.drawCircle(x + r * 1.75f, y + r * 0.05f, r * 0.72f, cloudPaint)
    }
}
