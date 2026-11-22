package com.muslimtimekeeper.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.muslimtimekeeper.app.R
import com.muslimtimekeeper.app.core.AdhkarData
import com.muslimtimekeeper.app.core.PrefsStore
import com.muslimtimekeeper.app.core.Strings

/**
 * True System-Level Overlay Lock using WindowManager (TYPE_APPLICATION_OVERLAY).
 * Supports PRAYER_LOCK (with calm electronic Tasbeeh), ADHKAR_LOCK (mandatory post-prayer Adhkar),
 * BEDTIME_LOCK (overnight sleep lock), PHONE_LOCK, and APP_BLOCKED.
 * Emergency phone calls remain accessible in all full-phone lock modes.
 */
class SystemOverlayBlocker(
    private val context: Context,
    private val store: PrefsStore,
    private val onPrayerUnlocked: () -> Unit,
    private val onPhoneUnlocked: () -> Unit,
    private val onBedtimeUnlocked: () -> Unit,
    private val onAdhkarCompleted: () -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var currentMode: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var timerTv: TextView? = null
    private var endMillis: Long = 0L

    val isShowing: Boolean get() = overlayView != null

    private val countdownRunnable = object : Runnable {
        override fun run() {
            val rem = ((endMillis - System.currentTimeMillis()) / 1000L).toInt()
            if (rem <= 0) {
                hide()
                return
            }
            timerTv?.text = String.format("%02d:%02d", rem / 60, rem % 60)
            handler.postDelayed(this, 500L)
        }
    }

    fun canDrawOverlay(): Boolean = Settings.canDrawOverlays(context)

    private fun cardBg(fillHex: String, strokeHex: String, radius: Float = 28f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = radius
            setStroke(3, Color.parseColor(strokeHex))
        }
    }

    fun show(mode: String, reason: String, prayerEndMillis: Long = 0L) {
        if (!canDrawOverlay()) return
        if (overlayView != null && currentMode == mode) return
        hide()

        currentMode = mode
        endMillis = prayerEndMillis
        val ar = store.isArabic
        val isPrayer = (mode == "PRAYER_LOCK")
        val isAdhkar = (mode == "ADHKAR_LOCK")
        val isBedtime = (mode == "BEDTIME_LOCK")

        val bgColors = when {
            isPrayer || isAdhkar -> intArrayOf(Color.parseColor("#041F17"), Color.parseColor("#0B3B2E"), Color.parseColor("#070B14"))
            isBedtime -> intArrayOf(Color.parseColor("#0F172A"), Color.parseColor("#1E1B4B"), Color.parseColor("#070B14"))
            else -> intArrayOf(Color.parseColor("#2A0C12"), Color.parseColor("#180A0E"), Color.parseColor("#070B14"))
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            layoutDirection = if (ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, bgColors)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 56, 48, 56)
        }

        val logo = ImageView(context).apply {
            setImageResource(R.drawable.app_logo)
            layoutParams = LinearLayout.LayoutParams(160, 160).apply { bottomMargin = 16 }
        }

        val title = TextView(context).apply {
            text = when (mode) {
                "PRAYER_LOCK" -> "🕌  $reason"
                "ADHKAR_LOCK" -> if (ar) "📿  أذكار بعد الصلاة" else "📿  Post-Prayer Adhkar"
                "BEDTIME_LOCK" -> if (ar) "🌙  وضع النوم التلقائي" else "🌙  Bedtime Lock"
                "APP_BLOCKED" -> Strings.get(ar, "app_blocked_title")
                else -> Strings.get(ar, "phone_locked_title")
            }
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(
                when {
                    isPrayer || isAdhkar -> Color.parseColor("#F59E0B")
                    isBedtime -> Color.parseColor("#818CF8")
                    else -> Color.parseColor("#F87171")
                }
            )
            gravity = Gravity.CENTER
        }

        val sub = TextView(context).apply {
            text = when {
                isPrayer -> Strings.get(ar, "prayer_verse")
                isAdhkar -> if (ar) "يرجى قراءة أذكار بعد الصلاة لفتح الموبايل" else "Please read Post-Prayer Adhkar to unlock your phone"
                else -> reason
            }
            textSize = 14.5f
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 16)
        }

        root.addView(logo)
        root.addView(title)
        root.addView(sub)

        if (isPrayer) {
            timerTv = TextView(context).apply {
                text = "${store.prayerDurationMinutes}:00"
                textSize = 42f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F59E0B"))
                gravity = Gravity.CENTER
            }
            root.addView(timerTv)

            // Calm Electronic Tasbeeh card during prayer break
            val phrases = if (ar) AdhkarData.tasbeehPhrasesAr else AdhkarData.tasbeehPhrasesEn
            var phraseIdx = 0
            var tasbeehCount = 0

            val tasbeehCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = cardBg("#0D2E25", "#10B981", 28f)
                setPadding(28, 22, 28, 22)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 14; bottomMargin = 14
                }
            }
            val tasbeehLbl = TextView(context).apply {
                text = if (ar) "📿 سبحة إلكترونية هادئة" else "📿 Calm Tasbeeh"
                textSize = 12f
                setTextColor(Color.parseColor("#6EE7B7"))
                gravity = Gravity.CENTER
            }
            val phraseTv = TextView(context).apply {
                text = phrases[0]
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 6)
            }
            val countTv = TextView(context).apply {
                text = "0"
                textSize = 26f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F59E0B"))
                gravity = Gravity.CENTER
            }
            val rowBtns = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 10
                }
            }
            val btnTap = Button(context).apply {
                text = if (ar) "سبّح (+1)" else "Tap (+1)"
                setBackgroundColor(Color.parseColor("#10B981"))
                setTextColor(Color.parseColor("#06281E"))
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f).apply { marginEnd = 8 }
                setOnClickListener {
                    tasbeehCount++
                    countTv.text = "$tasbeehCount"
                }
            }
            val btnNextPhrase = Button(context).apply {
                text = if (ar) "ذكر آخر ↻" else "Next ↻"
                setBackgroundColor(Color.parseColor("#1E293B"))
                setTextColor(Color.WHITE)
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    phraseIdx = (phraseIdx + 1) % phrases.size
                    tasbeehCount = 0
                    phraseTv.text = phrases[phraseIdx]
                    countTv.text = "0"
                }
            }
            rowBtns.addView(btnTap)
            rowBtns.addView(btnNextPhrase)
            tasbeehCard.addView(tasbeehLbl)
            tasbeehCard.addView(phraseTv)
            tasbeehCard.addView(countTv)
            tasbeehCard.addView(rowBtns)
            root.addView(tasbeehCard)

            val btnMute = Button(context).apply {
                text = Strings.get(ar, "mute_adhan")
                setBackgroundColor(Color.parseColor("#334155"))
                setTextColor(Color.WHITE)
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 10
                }
                setOnClickListener { stopAdhan() }
            }
            root.addView(btnMute)
        }

        if (isAdhkar) {
            val items = AdhkarData.postPrayerAdhkar
            var idx = 0
            var left = items[0].targetCount

            val adhkarCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = cardBg("#0D2E25", "#10B981", 30f)
                setPadding(30, 26, 30, 26)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 8; bottomMargin = 16
                }
            }
            val stepTv = TextView(context).apply {
                text = "${idx + 1} / ${items.size}"
                textSize = 13f
                setTextColor(Color.parseColor("#6EE7B7"))
                gravity = Gravity.CENTER
            }
            val dhikrTv = TextView(context).apply {
                text = if (ar) items[0].arText else items[0].enText
                textSize = 19f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 14, 0, 14)
            }
            val remTv = TextView(context).apply {
                text = if (ar) "المتبقي: $left" else "Remaining: $left"
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F59E0B"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 14)
            }
            val btnRead = Button(context).apply {
                text = if (ar) "📿 اضغط بعد القراءة / التسبيح" else "📿 Tap After Reading"
                setBackgroundColor(Color.parseColor("#10B981"))
                setTextColor(Color.parseColor("#06281E"))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
                    left--
                    if (left <= 0) {
                        idx++
                        if (idx >= items.size) {
                            Toast.makeText(
                                context,
                                if (ar) "تقبّل الله طاعتك! تم فتح الهاتف." else "May Allah accept your prayers! Phone unlocked.",
                                Toast.LENGTH_LONG
                            ).show()
                            hide()
                            onAdhkarCompleted()
                            return@setOnClickListener
                        }
                        left = items[idx].targetCount
                        stepTv.text = "${idx + 1} / ${items.size}"
                        dhikrTv.text = if (ar) items[idx].arText else items[idx].enText
                    }
                    remTv.text = if (ar) "المتبقي: $left" else "Remaining: $left"
                }
            }
            adhkarCard.addView(stepTv)
            adhkarCard.addView(dhikrTv)
            adhkarCard.addView(remTv)
            adhkarCard.addView(btnRead)
            root.addView(adhkarCard)
        }

        val errTv = TextView(context).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.parseColor("#F87171"))
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 6)
        }

        val pinIn = EditText(context).apply {
            hint = Strings.get(ar, "auth_msg")
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = cardBg("#121C30", "#334155", 22f)
            setPadding(28, 22, 28, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8; bottomMargin = 8
            }
        }

        val btnUnlock = Button(context).apply {
            text = Strings.get(ar, "parent_unlock")
            setBackgroundColor(Color.parseColor("#10B981"))
            setTextColor(Color.parseColor("#06281E"))
            isAllCaps = false
            textSize = 14.5f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                if (store.verifyPin(pinIn.text.toString())) {
                    hide()
                    when (mode) {
                        "PRAYER_LOCK" -> onPrayerUnlocked()
                        "ADHKAR_LOCK" -> onAdhkarCompleted()
                        "BEDTIME_LOCK" -> onBedtimeUnlocked()
                        "PHONE_LOCK" -> onPhoneUnlocked()
                    }
                } else {
                    errTv.text = Strings.get(ar, "wrong_pin")
                    pinIn.text.clear()
                }
            }
        }

        root.addView(pinIn)
        root.addView(errTv)
        root.addView(btnUnlock)

        if (mode == "PHONE_LOCK" || mode == "PRAYER_LOCK" || mode == "ADHKAR_LOCK" || mode == "BEDTIME_LOCK") {
            val btnCalls = Button(context).apply {
                text = Strings.get(ar, "open_calls")
                setBackgroundColor(Color.parseColor("#0284C7"))
                setTextColor(Color.WHITE)
                isAllCaps = false
                textSize = 14.5f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 14
                }
                setOnClickListener {
                    hide()
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (_: Exception) { }
                }
            }
            root.addView(btnCalls)
        } else if (mode == "APP_BLOCKED") {
            val btnHome = Button(context).apply {
                text = Strings.get(ar, "go_home")
                setBackgroundColor(Color.parseColor("#334155"))
                setTextColor(Color.WHITE)
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 14
                }
                setOnClickListener {
                    hide()
                    context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
            root.addView(btnHome)
        }

        scroll.addView(root)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.OPAQUE
        )

        try {
            wm.addView(scroll, params)
            overlayView = scroll
            if (isPrayer) {
                if (store.adhanAudioEnabled) playAdhan()
                if (endMillis > System.currentTimeMillis()) handler.post(countdownRunnable)
            }
        } catch (_: Exception) {
            overlayView = null
        }
    }

    fun hide() {
        handler.removeCallbacks(countdownRunnable)
        stopAdhan()
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Exception) { }
        }
        overlayView = null
        currentMode = null
    }

    private fun playAdhan() {
        try {
            stopAdhan()
            mediaPlayer = MediaPlayer.create(context, R.raw.adhan)
            mediaPlayer?.start()
        } catch (_: Exception) { }
    }

    private fun stopAdhan() {
        try {
            mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null
        } catch (_: Exception) { }
    }
}
