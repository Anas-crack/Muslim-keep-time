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
import kotlin.math.sin

class MosqueSkyView(context: Context) : View(context) {
    var skyPhase: String = "DHUHR" // FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA
    var nextPrayerLabel: String = "الظهر بعد"
    var countdownHms: String = "00:00:00"
    var locationLabel: String = "📍 الزقازيق، مصر"

    private var animPhase: Float = 0f
    private var lastFrameMs: Long = 0L

    private val pSky = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pCel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val pCloud = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBird = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.6f
        strokeCap = Paint.Cap.ROUND
    }
    private val pDune = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pBush = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pWall = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pShade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pTile = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pOrange = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pTrim = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pWin = Paint(Paint.ANTI_ALIAS_FLAG)

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
        0.08f to 0.14f, 0.18f to 0.08f, 0.28f to 0.20f, 0.42f to 0.09f,
        0.58f to 0.11f, 0.72f to 0.16f, 0.85f to 0.09f, 0.92f to 0.22f,
        0.14f to 0.30f, 0.36f to 0.32f, 0.66f to 0.28f, 0.82f to 0.34f
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

        val isNight = (skyPhase == "ISHA" || skyPhase == "FAJR")
        val isSunset = (skyPhase == "MAGHRIB")
        val skyColors = when (skyPhase) {
            "FAJR" -> intArrayOf(Color.parseColor("#0F172A"), Color.parseColor("#312E81"), Color.parseColor("#F9A8D4"))
            "SUNRISE" -> intArrayOf(Color.parseColor("#2563EB"), Color.parseColor("#7DD3FC"), Color.parseColor("#FEF08A"))
            "DHUHR" -> intArrayOf(Color.parseColor("#7DD3FC"), Color.parseColor("#BAE6FD"), Color.parseColor("#FEF9C3"))
            "ASR" -> intArrayOf(Color.parseColor("#3B82F6"), Color.parseColor("#93C5FD"), Color.parseColor("#FDBA74"))
            "MAGHRIB" -> intArrayOf(Color.parseColor("#450A0A"), Color.parseColor("#B91C1C"), Color.parseColor("#F97316"))
            else -> intArrayOf(Color.parseColor("#020617"), Color.parseColor("#0F172A"), Color.parseColor("#1E293B"))
        }

        val clipPath = Path().apply { addRoundRect(RectF(0f, 0f, w, h), 36f, 36f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clipPath)

        // 1. Dynamic Sky Gradient
        pSky.shader = LinearGradient(0f, 0f, 0f, h, skyColors, floatArrayOf(0f, 0.56f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, pSky)

        // 2. Twinkling Stars (Isha & Fajr)
        if (isNight || isSunset) {
            val maxA = when (skyPhase) {
                "ISHA" -> 235
                "FAJR" -> 165
                else -> 90
            }
            for (i in starCoords.indices) {
                val (sx, sy) = starCoords[i]
                val twinkle = 0.45f + 0.55f * sin(animPhase * 2.4f + i * 1.4f)
                pStar.alpha = (maxA * twinkle).toInt().coerceIn(20, 255)
                canvas.drawCircle(w * sx, h * sy, if (i % 3 == 0) 3.0f else 2.0f, pStar)
            }
        }

        // 3. Sun or Crescent Moon Position by Prayer Phase
        //    DHUHR -> Center, ASR -> Right, MAGHRIB -> Red sunset low, ISHA -> Crescent Moon
        val pulse = 1f + 0.05f * sin(animPhase * 2f)
        val cxCel = when (skyPhase) {
            "FAJR" -> w * 0.18f
            "SUNRISE" -> w * 0.85f
            "DHUHR" -> w * 0.50f
            "ASR" -> w * 0.80f
            "MAGHRIB" -> w * 0.72f
            else -> w * 0.82f
        }
        val cyCel = when (skyPhase) {
            "FAJR" -> h * 0.22f
            "SUNRISE" -> h * 0.32f
            "DHUHR" -> h * 0.16f
            "ASR" -> h * 0.28f
            "MAGHRIB" -> h * 0.52f
            else -> h * 0.18f
        }
        val celR = if (isSunset) w * 0.085f else w * 0.06f
        val celColor = when (skyPhase) {
            "MAGHRIB" -> Color.parseColor("#EF4444")
            "ASR" -> Color.parseColor("#FBBF24")
            "DHUHR" -> Color.parseColor("#FFFBEB")
            "SUNRISE" -> Color.parseColor("#FDE047")
            else -> Color.parseColor("#FEF08A")
        }
        pGlow.shader = RadialGradient(
            cxCel, cyCel, celR * 2.5f * pulse,
            intArrayOf(Color.argb(145, Color.red(celColor), Color.green(celColor), Color.blue(celColor)), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cxCel, cyCel, celR * 2.5f * pulse, pGlow)
        pCel.color = celColor
        canvas.drawCircle(cxCel, cyCel, celR, pCel)
        if (isNight) {
            // Crescent cutout
            pCel.color = skyColors[0]
            canvas.drawCircle(cxCel - celR * 0.38f, cyCel - celR * 0.14f, celR * 0.86f, pCel)
        }

        // 4. Soft Drifting Clouds & Flying Birds (like the reference image!)
        if (!isNight) {
            pCloud.color = if (isSunset) Color.argb(70, 254, 202, 202) else Color.argb(110, 255, 255, 255)
            val d1 = ((animPhase * 10f) % (w + 180f)) - 90f
            val d2 = ((animPhase * 6f + w * 0.55f) % (w + 180f)) - 90f
            drawCloud(canvas, d1, h * 0.20f, 26f)
            drawCloud(canvas, d2, h * 0.30f, 20f)
        }
        pBird.color = if (isNight) Color.parseColor("#94A3B8") else Color.parseColor("#1E293B")
        val birdOffsets = arrayOf(
            0.34f to 0.45f, 0.37f to 0.47f, 0.41f to 0.43f, 0.45f to 0.46f,
            0.48f to 0.41f, 0.51f to 0.45f, 0.54f to 0.43f, 0.57f to 0.47f
        )
        for (i in birdOffsets.indices) {
            val bx = ((w * birdOffsets[i].first + animPhase * 8f) % (w + 40f)) - 20f
            val by = h * birdOffsets[i].second + sin(animPhase * 4.5f + i) * 3f
            val flap = sin(animPhase * 7f + i * 1.2f) * 3.5f
            canvas.drawLine(bx - 5f, by - flap, bx, by, pBird)
            canvas.drawLine(bx, by, bx + 5f, by - flap, pBird)
        }

        // 5. Desert Sand Dunes & Lush Green Hills (Matching Reference Image!)
        pDune.color = when (skyPhase) {
            "ISHA" -> Color.parseColor("#1E293B")
            "MAGHRIB" -> Color.parseColor("#7C2D12")
            "ASR" -> Color.parseColor("#D6BC8B")
            else -> Color.parseColor("#EAD7A7")
        }
        val dunePath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.64f)
            quadTo(w * 0.22f, h * 0.56f, w * 0.48f, h * 0.67f)
            quadTo(w * 0.78f, h * 0.56f, w, h * 0.63f)
            lineTo(w, h)
            close()
        }
        canvas.drawPath(dunePath, pDune)

        pBush.color = when (skyPhase) {
            "ISHA" -> Color.parseColor("#064E3B")
            "MAGHRIB" -> Color.parseColor("#365314")
            "ASR" -> Color.parseColor("#4D7C0F")
            else -> Color.parseColor("#65A30D")
        }
        // Green foliage hills behind mosque wings
        canvas.drawCircle(w * 0.06f, h * 0.80f, h * 0.14f, pBush)
        canvas.drawCircle(w * 0.44f, h * 0.73f, h * 0.11f, pBush)
        canvas.drawCircle(w * 0.52f, h * 0.74f, h * 0.09f, pBush)
        canvas.drawCircle(w * 0.94f, h * 0.81f, h * 0.12f, pBush)

        // 6. Architectural Mosque (Faithful to Reference Screenshot!)
        val wallLight = when (skyPhase) {
            "ISHA" -> Color.parseColor("#CBD5E1")
            "MAGHRIB" -> Color.parseColor("#F1D5C9")
            "ASR" -> Color.parseColor("#F8EFE6")
            else -> Color.parseColor("#FFFFFF")
        }
        val wallSlate = when (skyPhase) {
            "ISHA" -> Color.parseColor("#334155")
            "MAGHRIB" -> Color.parseColor("#64748B")
            else -> Color.parseColor("#CBD5E1")
        }
        val tileBlue = when (skyPhase) {
            "ISHA" -> Color.parseColor("#1D4ED8")
            else -> Color.parseColor("#3B6CB7")
        }
        val orangeAccent = Color.parseColor("#F97316")
        val trimDark = Color.parseColor("#475569")
        val litWin = if (isNight || isSunset) Color.parseColor("#FDE047") else Color.parseColor("#F97316")

        pWall.color = wallLight
        pShade.color = wallSlate
        pTile.color = tileBlue
        pOrange.color = orangeAccent
        pTrim.color = trimDark
        pWin.color = litWin

        val groundY = h * 0.94f
        canvas.drawRect(0f, groundY, w, h, pTrim)

        // Main Orange Dome (Right-Center behind secondary Iwan)
        val domeRect = RectF(w * 0.57f, h * 0.50f, w * 0.83f, h * 0.78f)
        canvas.drawArc(domeRect, 180f, 180f, true, pOrange)
        // Dome spire
        canvas.drawLine(w * 0.70f, h * 0.45f, w * 0.70f, h * 0.51f, pTrim.apply { strokeWidth = 3f })
        canvas.drawCircle(w * 0.70f, h * 0.45f, 3.5f, pOrange)
        // Dome drum with tiny arched windows
        canvas.drawRect(w * 0.59f, h * 0.64f, w * 0.81f, h * 0.70f, pWall)
        for (i in 0..4) {
            val wx = w * (0.61f + i * 0.04f)
            canvas.drawRoundRect(RectF(wx, h * 0.65f, wx + w * 0.018f, h * 0.695f), 8f, 8f, pTile)
        }

        // Secondary Center-Right Iwan Portal
        canvas.drawRect(w * 0.53f, h * 0.63f, w * 0.71f, groundY, pWall)
        canvas.drawRect(w * 0.545f, h * 0.645f, w * 0.695f, h * 0.79f, pTile)
        canvas.drawRoundRect(RectF(w * 0.56f, h * 0.665f, w * 0.68f, h * 0.80f), 26f, 26f, pWall)

        // Left Wing
        canvas.drawRect(w * 0.07f, h * 0.77f, w * 0.22f, groundY, pWall)
        canvas.drawRect(w * 0.07f, h * 0.76f, w * 0.22f, h * 0.775f, pOrange)
        for (wx in floatArrayOf(0.095f, 0.16f)) {
            canvas.drawRoundRect(RectF(w * wx, h * 0.81f, w * (wx + 0.04f), groundY), 14f, 14f, pShade)
            canvas.drawRoundRect(RectF(w * (wx + 0.009f), h * 0.83f, w * (wx + 0.031f), groundY - 2f), 10f, 10f, pWin)
        }

        // Grand Left-Center Iwan Portal (Signature Blue Tile Frame + Tall Arch + Orange Door)
        canvas.drawRect(w * 0.21f, h * 0.57f, w * 0.40f, groundY, pWall)
        canvas.drawRect(w * 0.21f, h * 0.56f, w * 0.40f, h * 0.575f, pTrim)
        canvas.drawRect(w * 0.23f, h * 0.59f, w * 0.38f, groundY, pTile)
        canvas.drawRoundRect(RectF(w * 0.25f, h * 0.66f, w * 0.36f, groundY), 38f, 38f, pWall)
        canvas.drawRoundRect(RectF(w * 0.28f, h * 0.79f, w * 0.33f, groundY), 20f, 20f, pOrange)

        // Center Arcade Wing
        canvas.drawRect(w * 0.40f, h * 0.77f, w * 0.65f, groundY, pWall)
        canvas.drawRect(w * 0.40f, h * 0.76f, w * 0.65f, h * 0.775f, pOrange)
        for (i in 0..3) {
            val wx = w * (0.425f + i * 0.055f)
            canvas.drawRoundRect(RectF(wx, h * 0.81f, wx + w * 0.038f, groundY), 14f, 14f, pShade)
            canvas.drawRoundRect(RectF(wx + w * 0.008f, h * 0.83f, wx + w * 0.030f, groundY - 2f), 10f, 10f, pWin)
        }

        // Right Two-Story Wing (Slate & Tall Narrow Windows)
        canvas.drawRect(w * 0.65f, h * 0.73f, w * 0.91f, groundY, pShade)
        canvas.drawRect(w * 0.65f, h * 0.72f, w * 0.91f, h * 0.735f, pTrim)
        for (i in 0..5) {
            val wx = w * (0.675f + i * 0.037f)
            canvas.drawRoundRect(RectF(wx, h * 0.79f, wx + w * 0.022f, h * 0.90f), 10f, 10f, pWall)
            canvas.drawRoundRect(RectF(wx + 3f, h * 0.805f, wx + w * 0.022f - 3f, h * 0.89f), 8f, 8f, pWin)
        }

        // Left Minaret
        drawMinaret(canvas, w * 0.135f, h * 0.51f, w * 0.036f, groundY)
        // Right Minaret (Taller!)
        drawMinaret(canvas, w * 0.915f, h * 0.45f, w * 0.040f, groundY)

        // 7. Crisp Overlay Typography (Next/Selected Prayer + Giant HH:MM:SS + City)
        val mainTextColor = if (isNight || isSunset) Color.WHITE else Color.parseColor("#0F172A")
        val subTextColor = if (isNight || isSunset) Color.parseColor("#FDE68A") else Color.parseColor("#0369A1")

        labelPaint.color = subTextColor
        labelPaint.textSize = h * 0.092f
        canvas.drawText(nextPrayerLabel, w * 0.5f, h * 0.17f, labelPaint)

        timerPaint.color = mainTextColor
        timerPaint.textSize = h * 0.21f
        canvas.drawText(countdownHms, w * 0.5f, h * 0.36f, timerPaint)

        locPaint.color = mainTextColor
        locPaint.textSize = h * 0.072f
        canvas.drawText(locationLabel, w * 0.5f, h * 0.46f, locPaint)

        canvas.restore()
        postInvalidateOnAnimation()
    }

    private fun drawMinaret(canvas: Canvas, cx: Float, topY: Float, halfW: Float, groundY: Float) {
        // Shaft
        canvas.drawRect(cx - halfW * 0.72f, topY + 26f, cx + halfW * 0.72f, groundY, pWall)
        // Balcony
        canvas.drawRect(cx - halfW * 1.15f, topY + 34f, cx + halfW * 1.15f, topY + 42f, pTrim)
        canvas.drawRect(cx - halfW * 1.15f, topY + 72f, cx + halfW * 1.15f, topY + 79f, pTrim)
        // Lantern & Orange Dome
        canvas.drawRect(cx - halfW * 0.55f, topY + 14f, cx + halfW * 0.55f, topY + 34f, pWall)
        canvas.drawArc(RectF(cx - halfW * 0.75f, topY, cx + halfW * 0.75f, topY + 24f), 180f, 180f, true, pOrange)
        canvas.drawLine(cx, topY - 12f, cx, topY + 2f, pTrim)
        canvas.drawCircle(cx, topY - 12f, 2.5f, pOrange)
    }

    private fun drawCloud(canvas: Canvas, x: Float, y: Float, r: Float) {
        canvas.drawCircle(x, y, r, pCloud)
        canvas.drawCircle(x + r * 0.9f, y - r * 0.2f, r * 0.85f, pCloud)
        canvas.drawCircle(x + r * 1.75f, y + r * 0.05f, r * 0.72f, pCloud)
    }
}
