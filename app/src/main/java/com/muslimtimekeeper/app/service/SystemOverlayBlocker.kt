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
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.muslimtimekeeper.app.R
import com.muslimtimekeeper.app.core.AdhkarData
import com.muslimtimekeeper.app.core.PrefsStore
import com.muslimtimekeeper.app.core.Strings
import com.muslimtimekeeper.app.ui.AdhanWaveView
import com.muslimtimekeeper.app.ui.BeadTasbeehView
import java.util.Locale

/**
 * True System-Level Frosted-Glass Overlay Lock using WindowManager (TYPE_APPLICATION_OVERLAY).
 * Features:
 * - Realistic falling-bead Tasbeeh (BeadTasbeehView)
 * - Voice-recorder Adhan Sound Wave (AdhanWaveView)
 * - Built-in Arabic/English Voice Reader (TextToSpeech) for children on Post-Prayer Adhkar
 * - Emergency phone calls always accessible
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
    private var waveView: AdhanWaveView? = null
    private var tts: TextToSpeech? = null
    private var voiceEnabled: Boolean = true
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

    private fun glassCardBg(fillHex: String, strokeHex: String, radius: Float = 28f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = radius
            setStroke(2, Color.parseColor(strokeHex))
        }
    }

    private fun initTtsAndSpeak(ar: Boolean, initialText: String) {
        stopTts()
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val loc = if (ar) Locale("ar", "EG") else Locale.US
                    val res = tts?.setLanguage(loc)
                    if (ar && (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED)) {
                        tts?.setLanguage(Locale("ar"))
                    }
                    tts?.setSpeechRate(0.88f)
                    if (voiceEnabled) speakDhikr(initialText)
                }
            }
        } catch (_: Exception) { }
    }

    private fun speakDhikr(text: String) {
        if (!voiceEnabled) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dhikr_${System.currentTimeMillis()}")
        } catch (_: Exception) { }
    }

    private fun stopTts() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) { }
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
            isPrayer || isAdhkar -> intArrayOf(Color.parseColor("#CC081526"), Color.parseColor("#DD0F2438"), Color.parseColor("#EE070E1A"))
            isBedtime -> intArrayOf(Color.parseColor("#CC0F172A"), Color.parseColor("#DD1E1B4B"), Color.parseColor("#EE070B14"))
            else -> intArrayOf(Color.parseColor("#CC2A0C12"), Color.parseColor("#DD180A0E"), Color.parseColor("#EE070B14"))
        }

        val scroll = ScrollView(context).apply {
            isFillViewport = true
            layoutDirection = if (ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, bgColors)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(44, 52, 44, 52)
        }

        val logo = ImageView(context).apply {
            setImageResource(R.drawable.app_logo)
            layoutParams = LinearLayout.LayoutParams(144, 144).apply { bottomMargin = 14 }
        }

        val title = TextView(context).apply {
            text = when (mode) {
                "PRAYER_LOCK" -> "🕌  $reason"
                "ADHKAR_LOCK" -> if (ar) "📿  أذكار بعد الصلاة" else "📿  Post-Prayer Adhkar"
                "BEDTIME_LOCK" -> if (ar) "🌙  وضع النوم التلقائي" else "🌙  Bedtime Lock"
                "APP_BLOCKED" -> Strings.get(ar, "app_blocked_title")
                else -> Strings.get(ar, "phone_locked_title")
            }
            textSize = 23f
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
            textSize = 14f
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 14)
        }

        root.addView(logo)
        root.addView(title)
        root.addView(sub)

        if (isPrayer) {
            timerTv = TextView(context).apply {
                text = "${store.prayerDurationMinutes}:00"
                textSize = 40f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F59E0B"))
                gravity = Gravity.CENTER
            }
            root.addView(timerTv)

            // Live Adhan Sound Wave
            waveView = AdhanWaveView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 76).apply {
                    topMargin = 6; bottomMargin = 10
                }
            }
            root.addView(waveView)

            val btnMute = Button(context).apply {
                text = Strings.get(ar, "mute_adhan")
                setBackgroundColor(Color.parseColor("#44334155"))
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
            voiceEnabled = true

            val adhkarCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = glassCardBg("#55102A3E", "#5538BDF8", 30f)
                setPadding(26, 22, 26, 22)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 6; bottomMargin = 14
                }
            }
            val stepTv = TextView(context).apply {
                text = "${idx + 1} / ${items.size}"
                textSize = 13f
                setTextColor(Color.parseColor("#7DD3FC"))
                gravity = Gravity.CENTER
            }
            val dhikrTv = TextView(context).apply {
                text = if (ar) items[0].arText else items[0].enText
                textSize = 17.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 10, 0, 10)
            }
            val remTv = TextView(context).apply {
                text = if (ar) "المتبقي: $left" else "Remaining: $left"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F59E0B"))
                gravity = Gravity.CENTER
            }

            // Voice Controls Row (Auto-read for Kids + Replay)
            val voiceRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 8; bottomMargin = 8
                }
            }
            val btnSpeak = Button(context).apply {
                text = if (ar) "🔊 استماع للذكر" else "🔊 Listen"
                setBackgroundColor(Color.parseColor("#0284C7"))
                setTextColor(Color.WHITE)
                isAllCaps = false
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 }
                setOnClickListener {
                    voiceEnabled = true
                    speakDhikr(if (ar) items[idx].arText else items[idx].enText)
                }
            }
            val btnToggleVoice = Button(context).apply {
                text = if (ar) "🔇 كتم القراءة" else "🔇 Mute Voice"
                setBackgroundColor(Color.parseColor("#44334155"))
                setTextColor(Color.WHITE)
                isAllCaps = false
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener {
                    voiceEnabled = !voiceEnabled
                    if (!voiceEnabled) tts?.stop()
                    text = if (voiceEnabled) (if (ar) "🔇 كتم القراءة" else "🔇 Mute Voice") else (if (ar) "🔊 تفعيل القراءة" else "🔊 Unmute Voice")
                }
            }
            voiceRow.addView(btnSpeak)
            voiceRow.addView(btnToggleVoice)

            // Realistic Falling-Bead Tasbeeh for counting Adhkar!
            val beadView = BeadTasbeehView(context).apply {
                beadTheme = store.selectedBeadTheme
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 240)
            }

            fun advanceDhikr() {
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
                        return
                    }
                    left = items[idx].targetCount
                    stepTv.text = "${idx + 1} / ${items.size}"
                    val nextText = if (ar) items[idx].arText else items[idx].enText
                    dhikrTv.text = nextText
                    speakDhikr(nextText)
                }
                remTv.text = if (ar) "المتبقي: $left" else "Remaining: $left"
            }

            beadView.onBeadTapped = { advanceDhikr() }

            val hintTv = TextView(context).apply {
                text = if (ar) "اضغط على حبات السبحة لتنزيل حبة مع كل ذكر" else "Tap the prayer beads to drop a bead for each Dhikr"
                textSize = 11.5f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
            }

            adhkarCard.addView(stepTv)
            adhkarCard.addView(dhikrTv)
            adhkarCard.addView(remTv)
            adhkarCard.addView(voiceRow)
            adhkarCard.addView(beadView)
            adhkarCard.addView(hintTv)
            root.addView(adhkarCard)

            initTtsAndSpeak(ar, if (ar) items[0].arText else items[0].enText)
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
            background = glassCardBg("#55121C30", "#55475569", 22f)
            setPadding(28, 22, 28, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 8; bottomMargin = 8
            }
        }

        val btnUnlock = Button(context).apply {
            text = Strings.get(ar, "parent_unlock")
            setBackgroundColor(Color.parseColor("#0284C7"))
            setTextColor(Color.WHITE)
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
                setBackgroundColor(Color.parseColor("#0EA5E9"))
                setTextColor(Color.WHITE)
                isAllCaps = false
                textSize = 14.5f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 12
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
                setBackgroundColor(Color.parseColor("#44334155"))
                setTextColor(Color.WHITE)
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 12
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
            PixelFormat.TRANSLUCENT
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
        stopTts()
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Exception) { }
        }
        overlayView = null
        waveView = null
        currentMode = null
    }

    private fun playAdhan() {
        try {
            stopAdhan()
            mediaPlayer = MediaPlayer.create(context, R.raw.adhan)
            mediaPlayer?.start()
            waveView?.isPlayingWave = true
            mediaPlayer?.setOnCompletionListener { waveView?.isPlayingWave = false }
        } catch (_: Exception) { }
    }

    private fun stopAdhan() {
        try {
            mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null
            waveView?.isPlayingWave = false
        } catch (_: Exception) { }
    }
}
