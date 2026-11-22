package com.muslimtimekeeper.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.math.sin

/**
 * Animated Islamic Geometric Mandala + Glowing Progress Ring (60fps Hardware Accelerated)
 */
class GlowingMandalaRingView(context: Context) : View(context) {
    private var progress: Float = 0.85f
    private var rotationAngle: Float = 0f
    private var pulseFactor: Float = 0f

    private val mandalaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(48, 245, 158, 11)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E293B")
        style = Paint.Style.STROKE
        strokeWidth = 26f
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 16, 185, 129)
        style = Paint.Style.STROKE
        strokeWidth = 42f
        strokeCap = Paint.Cap.ROUND
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 26f
        strokeCap = Paint.Cap.ROUND
    }

    private val rotAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 28000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotationAngle = it.animatedValue as Float
            invalidate()
        }
    }

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener {
            pulseFactor = it.animatedValue as Float
            invalidate()
        }
    }

    fun setProgressRatio(ratio: Float) {
        progress = ratio.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rotAnimator.start()
        pulseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        rotAnimator.cancel()
        pulseAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(cx, cy) - 24f
        if (maxR <= 20f) return

        // 1. Rotating 8-point Islamic Geometric Mandala
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotationAngle)
        val sq = maxR * 1.32f
        val half = sq / 2f
        val rect = RectF(-half, -half, half, half)
        for (i in 0 until 4) {
            canvas.save()
            canvas.rotate(i * 45f)
            canvas.drawRoundRect(rect, 22f, 22f, mandalaPaint)
            canvas.restore()
        }
        canvas.drawCircle(0f, 0f, maxR * 0.96f, mandalaPaint)
        canvas.restore()

        // 2. Progress Ring Track + Glowing Arc
        val ringR = maxR * 0.80f
        val oval = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        canvas.drawCircle(cx, cy, ringR, trackPaint)

        glowPaint.alpha = (45 + 45 * pulseFactor).toInt()
        val sweep = 360f * progress
        canvas.drawArc(oval, -90f, sweep, false, glowPaint)

        arcPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(Color.parseColor("#10B981"), Color.parseColor("#34D399"), Color.parseColor("#F59E0B"), Color.parseColor("#10B981")),
            null
        )
        canvas.drawArc(oval, -90f, sweep, false, arcPaint)
    }
}

/**
 * Live Animated Audio Equalizer Bars for Adhan Playback
 */
class AdhanWaveView(context: Context) : View(context) {
    private var phase: Float = 0f
    var isPlayingWave: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    private val animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = 1200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            if (isPlayingWave) invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bars = 16
        val step = width.toFloat() / (bars + 1)
        barPaint.color = if (isPlayingWave) Color.parseColor("#F59E0B") else Color.parseColor("#334155")
        for (i in 1..bars) {
            val factor = if (isPlayingWave) {
                (0.25f + 0.7f * (0.5f + 0.5f * sin((phase + i * 0.55f).toDouble()).toFloat()))
            } else {
                0.16f
            }
            val h = height * factor
            val x = i * step
            canvas.drawLine(x, height / 2f - h / 2f, x, height / 2f + h / 2f, barPaint)
        }
    }
}
