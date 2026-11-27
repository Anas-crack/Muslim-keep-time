package com.dynamicisland.redminote8

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class ChargingVortexBatteryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var realBatteryPct: Int = 50
    private var vortexAngle: Float = 0f
    private var fillProgress: Float = 0f
    private var flashAlpha: Float = 0f
    private var animating: Boolean = false

    private val vortexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val spiralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(235, 226, 252, 240)
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(235, 226, 252, 240)
    }
    private val bgFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(140, 10, 24, 18)
    }
    private val greenFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val outerRect = RectF()
    private val innerSpiralRect = RectF()
    private val battBodyRect = RectF()
    private val battCapRect = RectF()
    private val fillRect = RectF()

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!animating || visibility != VISIBLE) return
            vortexAngle = (vortexAngle + 8.5f) % 360f
            if (realBatteryPct >= 100) {
                fillProgress = 1f
                if (flashAlpha > 0f) flashAlpha = (flashAlpha - 0.04f).coerceAtLeast(0f)
            } else {
                fillProgress += 0.016f
                if (fillProgress >= 1.0f) {
                    // Reached top -> flash emerald ring & reset to 0% to swirl and fill again!
                    fillProgress = 0f
                    flashAlpha = 1.0f
                } else if (flashAlpha > 0f) {
                    flashAlpha = (flashAlpha - 0.05f).coerceAtLeast(0f)
                }
            }
            invalidate()
            postDelayed(this, 24L)
        }
    }

    fun setBatteryPercent(pct: Int) {
        realBatteryPct = pct.coerceIn(0, 100)
        if (realBatteryPct >= 100) fillProgress = 1f
    }

    fun startVortex() {
        if (animating) return
        animating = true
        removeCallbacks(frameRunnable)
        post(frameRunnable)
    }

    fun stopVortex() {
        animating = false
        removeCallbacks(frameRunnable)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && animating) {
            removeCallbacks(frameRunnable)
            post(frameRunnable)
        } else if (visibility != VISIBLE) {
            removeCallbacks(frameRunnable)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frameRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val minDim = minOf(w, h)
        val strokeW = (minDim * 0.085f).coerceAtLeast(2.2f)

        // 1. Swirling green vortex rings around the battery
        val rOuter = minDim * 0.44f
        outerRect.set(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter)
        vortexPaint.strokeWidth = strokeW
        vortexPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(
                Color.argb(15, 16, 185, 129),
                Color.argb(190, 52, 211, 153),
                Color.argb(255, 110, 231, 183),
                Color.argb(40, 16, 185, 129)
            ),
            floatArrayOf(0f, 0.45f, 0.85f, 1f)
        )
        canvas.save()
        canvas.rotate(vortexAngle, cx, cy)
        canvas.drawArc(outerRect, 0f, 300f, false, vortexPaint)
        canvas.restore()

        // Inner counter-swirl cyclone trail
        val rInner = minDim * 0.36f
        innerSpiralRect.set(cx - rInner, cy - rInner, cx + rInner, cy + rInner)
        spiralPaint.strokeWidth = (strokeW * 0.65f).coerceAtLeast(1.5f)
        spiralPaint.color = Color.argb(165, 52, 211, 153)
        canvas.save()
        canvas.rotate(-vortexAngle * 1.45f, cx, cy)
        canvas.drawArc(innerSpiralRect, 20f, 130f, false, spiralPaint)
        canvas.drawArc(innerSpiralRect, 200f, 110f, false, spiralPaint)
        canvas.restore()

        // Flash burst when cycle resets to 0%
        if (flashAlpha > 0.02f) {
            flashPaint.strokeWidth = strokeW * 1.2f
            flashPaint.color = Color.argb((flashAlpha * 230).toInt().coerceIn(0, 255), 110, 231, 183)
            canvas.drawCircle(cx, cy, rOuter, flashPaint)
        }

        // 2. Centered vertical battery body + cap
        val battW = minDim * 0.34f
        val battH = minDim * 0.50f
        val capW = battW * 0.44f
        val capH = (minDim * 0.055f).coerceAtLeast(2f)
        val bodyTop = cy - battH / 2f + capH * 0.4f
        val bodyBot = bodyTop + battH
        val bodyLeft = cx - battW / 2f
        val bodyRight = cx + battW / 2f
        val cornerR = (battW * 0.22f).coerceAtLeast(2.5f)

        battCapRect.set(cx - capW / 2f, bodyTop - capH - 0.5f, cx + capW / 2f, bodyTop)
        canvas.drawRoundRect(battCapRect, capH / 2f, capH / 2f, capPaint)

        battBodyRect.set(bodyLeft, bodyTop, bodyRight, bodyBot)
        canvas.drawRoundRect(battBodyRect, cornerR, cornerR, bgFillPaint)

        // 3. Smoothly rising green battery level (fills step-by-step then resets at 100%)
        val pad = (minDim * 0.032f).coerceAtLeast(1.5f)
        val innerMaxH = (battH - pad * 2f).coerceAtLeast(1f)
        val curFillH = innerMaxH * fillProgress.coerceIn(0f, 1f)
        if (curFillH > 0.8f) {
            fillRect.set(
                bodyLeft + pad,
                bodyBot - pad - curFillH,
                bodyRight - pad,
                bodyBot - pad
            )
            greenFillPaint.shader = LinearGradient(
                fillRect.left, fillRect.bottom, fillRect.left, fillRect.top,
                intArrayOf(Color.rgb(5, 150, 105), Color.rgb(52, 211, 153), Color.rgb(167, 243, 208)),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(fillRect, cornerR * 0.6f, cornerR * 0.6f, greenFillPaint)
        }

        // 4. Crisp battery glass outline
        outlinePaint.strokeWidth = (minDim * 0.045f).coerceAtLeast(1.4f)
        canvas.drawRoundRect(battBodyRect, cornerR, cornerR, outlinePaint)

        // 5. Orbiting spark dot on the vortex tip
        val rad = Math.toRadians((vortexAngle + 295f).toDouble())
        val dotX = cx + (rOuter * cos(rad)).toFloat()
        val dotY = cy + (rOuter * sin(rad)).toFloat()
        canvas.drawCircle(dotX, dotY, (strokeW * 0.55f).coerceAtLeast(1.8f), capPaint)
    }
}
