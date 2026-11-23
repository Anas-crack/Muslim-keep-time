package com.muslimtimekeeper.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.math.sin

/**
 * Animated Islamic Geometric Mandala + Glowing Progress Ring
 */
class GlowingMandalaRingView(context: Context) : View(context) {
    private var progress: Float = 1.0f
    private var rotationAngle: Float = 0f
    private var pulseFactor: Float = 0f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 22f
        color = Color.parseColor("#331E293B")
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 36f
        color = Color.parseColor("#2A10B981")
        strokeCap = Paint.Cap.ROUND
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 22f
        strokeCap = Paint.Cap.ROUND
    }

    private val mandalaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.parseColor("#3810B981")
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

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f, 0f).apply {
        duration = 3200L
        repeatCount = ValueAnimator.INFINITE
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

        val ringR = maxR * 0.80f
        val oval = RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        canvas.drawCircle(cx, cy, ringR, trackPaint)

        glowPaint.alpha = (45 + 45 * pulseFactor).toInt()
        val sweep = 360f * progress
        canvas.drawArc(oval, -90f, sweep, false, glowPaint)

        arcPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(Color.parseColor("#38BDF8"), Color.parseColor("#10B981"), Color.parseColor("#F59E0B"), Color.parseColor("#38BDF8")),
            null
        )
        canvas.drawArc(oval, -90f, sweep, false, arcPaint)
    }
}

/**
 * Voice-Recorder Style Live Sound Wave for Adhan Playback.
 * Moves organically when Adhan is playing and freezes flat immediately when stopped.
 */
class AdhanWaveView(context: Context) : View(context) {
    private var phase: Float = 0f
    var isPlayingWave: Boolean = false
        set(value) {
            field = value
            if (value) {
                if (!animator.isRunning) animator.start()
            } else {
                animator.cancel()
            }
            invalidate()
        }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
    }

    private val animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = 950L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            if (isPlayingWave) invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bars = 28
        val step = width.toFloat() / (bars + 1)
        val midY = height / 2f
        for (i in 1..bars) {
            val distCenter = 1f - (kotlin.math.abs(i - bars / 2f) / (bars / 2f))
            val factor = if (isPlayingWave) {
                val w1 = sin((phase * 1.4f + i * 0.65f).toDouble()).toFloat()
                val w2 = sin((phase * 2.3f - i * 0.42f).toDouble()).toFloat()
                (0.18f + (0.78f * (0.45f + 0.55f * distCenter)) * (0.5f + 0.25f * w1 + 0.25f * w2)).coerceIn(0.12f, 0.96f)
            } else {
                0.08f
            }
            barPaint.color = if (isPlayingWave) {
                if (i % 3 == 0) Color.parseColor("#F59E0B") else Color.parseColor("#38BDF8")
            } else {
                Color.parseColor("#475569")
            }
            val halfH = (height * factor) / 2f
            val x = i * step
            canvas.drawLine(x, midY - halfH, x, midY + halfH, barPaint)
        }
    }
}

/**
 * Interactive Realistic 3D Bead Tasbeeh (سبحة حبيبات حقيقية).
 * Every tap slides a glowing bead smoothly down the prayer string with haptic feedback!
 */
class BeadTasbeehView(context: Context) : View(context) {
    private var dropAnimFraction: Float = 1f
    private var totalDrops: Int = 0
    var onBeadTapped: (() -> Unit)? = null

    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#94A3B8")
    }

    private val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#88FFFFFF")
    }

    private val ringAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#88F59E0B")
    }

    private var dropAnimator: ValueAnimator? = null

    init {
        isClickable = true
        setOnClickListener {
            dropBead()
            onBeadTapped?.invoke()
        }
    }

    fun dropBead() {
        totalDrops++
        try { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) } catch (_: Exception) { }
        dropAnimator?.cancel()
        dropAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 230L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                dropAnimFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun drawBead(canvas: Canvas, cx: Float, cy: Float, radius: Float, isGold: Boolean) {
        val c1 = if (isGold) Color.parseColor("#FDE68A") else Color.parseColor("#7DD3FC")
        val c2 = if (isGold) Color.parseColor("#D97706") else Color.parseColor("#0284C7")
        val c3 = if (isGold) Color.parseColor("#78350F") else Color.parseColor("#0C4A6E")
        beadPaint.shader = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.3f, radius * 1.25f,
            intArrayOf(c1, c2, c3), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, beadPaint)
        canvas.drawCircle(cx, cy, radius, ringAccentPaint)
        canvas.drawCircle(cx - radius * 0.28f, cy - radius * 0.28f, radius * 0.22f, highlightPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val h = height.toFloat()
        if (h < 40f) return

        // Draw curved/vertical prayer cord
        canvas.drawLine(cx, 12f, cx, h - 12f, stringPaint)

        val beadR = min(width * 0.14f, h * 0.105f).coerceIn(14f, 28f)
        val gap = beadR * 2.25f

        // Top stack of waiting beads (3 beads)
        for (i in 0 until 3) {
            val y = 24f + i * gap
            drawBead(canvas, cx, y, beadR, (totalDrops + i) % 11 == 0)
        }

        // Falling animated bead sliding from upper zone to lower stack!
        val startY = 24f + 2 * gap
        val endY = h - 24f - 2 * gap
        val animY = startY + (endY - startY) * dropAnimFraction
        drawBead(canvas, cx, animY, beadR * 1.12f, totalDrops % 3 == 0)

        // Bottom stack of already counted beads (2 beads)
        for (i in 0 until 2) {
            val y = h - 24f - i * gap
            drawBead(canvas, cx, y, beadR, (totalDrops + i + 1) % 11 == 0)
        }
    }

    override fun onDetachedFromWindow() {
        dropAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}

/**
 * Clean Circular Progress Ring for Device Internet Data Quota (GB)
 */
class DataQuotaRingView(context: Context) : View(context) {
    private var usedRatio: Float = 0f
    private var isDark: Boolean = true

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 28f
        strokeCap = Paint.Cap.ROUND
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 28f
        strokeCap = Paint.Cap.ROUND
    }

    fun setQuota(ratio: Float, dark: Boolean) {
        usedRatio = ratio.coerceIn(0f, 1f)
        isDark = dark
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - 26f
        if (r <= 10f) return
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)

        trackPaint.color = if (isDark) Color.parseColor("#551E293B") else Color.parseColor("#66CBD5E1")
        canvas.drawCircle(cx, cy, r, trackPaint)

        val arcColor = when {
            usedRatio >= 0.95f -> Color.parseColor("#EF4444")
            usedRatio >= 0.75f -> Color.parseColor("#F59E0B")
            else -> Color.parseColor("#38BDF8")
        }
        arcPaint.color = arcColor
        val sweep = 360f * usedRatio
        if (sweep > 0.5f) {
            canvas.drawArc(oval, -90f, sweep, false, arcPaint)
        }
    }
}
