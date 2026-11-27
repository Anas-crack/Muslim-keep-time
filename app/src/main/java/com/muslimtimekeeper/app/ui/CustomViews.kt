package com.muslimtimekeeper.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
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
        color = Color.parseColor("#2A38BDF8")
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
        color = Color.parseColor("#3838BDF8")
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
            val distCenter = 1f - (abs(i - bars / 2f) / (bars / 2f))
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
 * Realistic Curved-Arc Diagonal Pearl Bead Tasbeeh (سبحة حبيبات حقيقية على قوس مائل).
 * Matches user's reference screenshot: distinct glossy 3D pearls along a curved cord
 * sweeping from upper-left to lower-right with a clear gap where ONE bead slides down per tap!
 */
class BeadTasbeehView(context: Context) : View(context) {
    private var slideFraction: Float = 1f
    private var isAnimating: Boolean = false
    var beadTheme: Int = 0 // 0=Pearl White, 1=Sapphire Blue, 2=Emerald Green, 3=Royal Gold
        set(value) {
            field = value
            invalidate()
        }
    var onBeadTapped: (() -> Unit)? = null

    private val cordPath = Path()
    private val cordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        color = Color.parseColor("#CC1E293B")
        strokeCap = Paint.Cap.ROUND
    }
    private val cordHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#55CBD5E1")
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#38000000")
    }
    private val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#B8FFFFFF")
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#55FFFFFF")
    }

    private var animator: ValueAnimator? = null

    init {
        isClickable = true
        setOnClickListener {
            dropBead()
            onBeadTapped?.invoke()
        }
    }

    fun dropBead() {
        try { performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) } catch (_: Exception) { }
        animator?.cancel()
        isAnimating = true
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                slideFraction = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun quadPoint(t: Float, x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float): Pair<Float, Float> {
        val u = 1f - t
        val x = u * u * x0 + 2f * u * t * cx + t * t * x1
        val y = u * u * y0 + 2f * u * t * cy + t * t * y1
        return Pair(x, y)
    }

    private fun drawPearl(canvas: Canvas, x: Float, y: Float, r: Float) {
        // Soft drop shadow
        canvas.drawCircle(x + r * 0.10f, y + r * 0.18f, r * 0.98f, shadowPaint)

        // 3D glossy radial shading
        val colors = when (beadTheme) {
            1 -> intArrayOf(Color.parseColor("#BAE6FD"), Color.parseColor("#0284C7"), Color.parseColor("#082F49"))
            2 -> intArrayOf(Color.parseColor("#A7F3D0"), Color.parseColor("#059669"), Color.parseColor("#064E3B"))
            3 -> intArrayOf(Color.parseColor("#FEF08A"), Color.parseColor("#D97706"), Color.parseColor("#78350F"))
            else -> intArrayOf(Color.parseColor("#FFFFFF"), Color.parseColor("#E8E2D9"), Color.parseColor("#B6ACA0"))
        }
        beadPaint.shader = RadialGradient(
            x - r * 0.30f, y - r * 0.32f, r * 1.35f,
            colors, floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x, y, r, beadPaint)
        canvas.drawCircle(x, y, r, rimPaint)
        // Specular pearl shine
        canvas.drawCircle(x - r * 0.32f, y - r * 0.32f, r * 0.24f, glossPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 60f || h < 60f) return

        // Sweeping diagonal curve from upper-left to lower-right (like user's reference photo)
        val x0 = -20f
        val y0 = h * 0.24f
        val cx = w * 0.48f
        val cy = h * 0.62f
        val x1 = w + 20f
        val y1 = h * 0.80f

        cordPath.reset()
        cordPath.moveTo(x0, y0)
        cordPath.quadTo(cx, cy, x1, y1)
        canvas.drawPath(cordPath, cordPaint)
        canvas.drawPath(cordPath, cordHighlightPaint)

        val r = min(w * 0.068f, h * 0.22f).coerceIn(22f, 44f)

        // Upper-left resting pearls (4 distinct pearls, spaced cleanly along t = 0.05 .. 0.44)
        val leftSlots = floatArrayOf(0.05f, 0.18f, 0.31f, 0.44f)
        // Lower-right counted pearls (2 distinct pearls along t = 0.82, 0.95)
        val rightSlots = floatArrayOf(0.82f, 0.95f)

        val shift = if (isAnimating) slideFraction else 1f
        val step = 0.13f

        // Incoming bead at far upper-left entering as queue shifts forward
        val pIn = quadPoint(-0.08f + step * shift, x0, y0, cx, cy, x1, y1)
        drawPearl(canvas, pIn.first, pIn.second, r)

        // Left pearls shifting gently one slot forward
        for (i in 0 until leftSlots.size - 1) {
            val t = leftSlots[i] + step * shift
            val pt = quadPoint(t, x0, y0, cx, cy, x1, y1)
            drawPearl(canvas, pt.first, pt.second, r)
        }

        // The active falling bead gliding across the open cord gap (0.44 -> 0.82)!
        val activeT = 0.44f + (0.82f - 0.44f) * shift
        val pActive = quadPoint(activeT, x0, y0, cx, cy, x1, y1)
        drawPearl(canvas, pActive.first, pActive.second, r * 1.04f)

        // Lower-right pearls shifting gently out to the right
        for (tBase in rightSlots) {
            val t = tBase + step * shift
            if (t <= 1.08f) {
                val pt = quadPoint(t, x0, y0, cx, cy, x1, y1)
                drawPearl(canvas, pt.first, pt.second, r)
            }
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
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
