package com.muslimtimekeeper.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.muslimtimekeeper.app.R
import com.muslimtimekeeper.app.core.AdhkarData
import com.muslimtimekeeper.app.core.PrefsStore
import com.muslimtimekeeper.app.core.Strings
import com.muslimtimekeeper.app.service.GuardianService
import java.util.Locale

class LockOverlayActivity : AppCompatActivity() {
    private lateinit var store: PrefsStore
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var endMillis: Long = 0L
    private var timerTv: TextView? = null
    private var waveView: AdhanWaveView? = null
    private var tts: TextToSpeech? = null
    private var voiceEnabled: Boolean = true
    private var mode: String = "PHONE_LOCK"

    private val countdownRunnable = object : Runnable {
        override fun run() {
            val remSec = ((endMillis - System.currentTimeMillis()) / 1000L).toInt()
            if (remSec <= 0) {
                stopAdhan()
                finish()
                return
            }
            timerTv?.text = String.format("%02d:%02d", remSec / 60, remSec % 60)
            handler.postDelayed(this, 500L)
        }
    }

    private fun roundedBg(startHex: String, endHex: String, radiusDp: Float, strokeHex: String? = null): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor(startHex), Color.parseColor(endHex))
        ).apply {
            cornerRadius = radiusDp * resources.displayMetrics.density
            if (strokeHex != null) setStroke(2, Color.parseColor(strokeHex))
        }
    }

    private fun initTtsAndSpeak(ar: Boolean, initialText: String) {
        stopTts()
        try {
            tts = TextToSpeech(this) { status ->
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = 55 }
                window.setBackgroundBlurRadius(50)
            } catch (_: Exception) { }
        }
        store = PrefsStore(this)
        val ar = store.isArabic
        window.decorView.layoutDirection = if (ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

        mode = intent.getStringExtra("MODE") ?: "PHONE_LOCK"
        val reason = intent.getStringExtra("REASON") ?: ""
        endMillis = intent.getLongExtra("END_MILLIS", 0L)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mode == "APP_BLOCKED") {
                    startActivity(Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                    finish()
                }
            }
        })

        val isPrayer = (mode == "PRAYER_LOCK")
        val isAdhkar = (mode == "ADHKAR_LOCK")
        val isBedtime = (mode == "BEDTIME_LOCK")

        val bgColors = when {
            isPrayer || isAdhkar -> intArrayOf(Color.parseColor("#C4081526"), Color.parseColor("#D80F2438"), Color.parseColor("#E6070E1A"))
            isBedtime -> intArrayOf(Color.parseColor("#C40F172A"), Color.parseColor("#D81E1B4B"), Color.parseColor("#E6070B14"))
            else -> intArrayOf(Color.parseColor("#C42A0C12"), Color.parseColor("#D8180A0E"), Color.parseColor("#E6070B14"))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, bgColors)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(44, 44, 44, 44)
        }

        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(240, 240).apply { bottomMargin = 12 }
        }
        val ring = GlowingMandalaRingView(this).apply { setProgressRatio(1.0f) }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            layoutParams = FrameLayout.LayoutParams(130, 130, Gravity.CENTER)
        }
        frame.addView(ring)
        frame.addView(logo)

        val title = TextView(this).apply {
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

        val sub = TextView(this).apply {
            text = when {
                isPrayer -> Strings.get(ar, "prayer_verse")
                isAdhkar -> if (ar) "يرجى قراءة أذكار بعد الصلاة لفتح الموبايل" else "Please read Post-Prayer Adhkar to unlock your phone"
                else -> reason
            }
            textSize = 14f
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 12)
        }

        root.addView(frame)
        root.addView(title)
        root.addView(sub)

        if (isPrayer) {
            timerTv = TextView(this).apply {
                text = "${store.prayerDurationMinutes}:00"
                textSize = 40f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F59E0B"))
                gravity = Gravity.CENTER
            }
            root.addView(timerTv)

            waveView = AdhanWaveView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 76).apply { topMargin = 4; bottomMargin = 10 }
            }
            root.addView(waveView)

            val btnMute = Button(this).apply {
                text = Strings.get(ar, "mute_adhan")
                background = roundedBg("#441E293B", "#440F172A", 12f, "#4464748B")
                setTextColor(Color.WHITE)
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener { stopAdhan() }
            }
            root.addView(btnMute)
        }

        if (isAdhkar) {
            val items = AdhkarData.postPrayerAdhkar
            var idx = 0
            var left = items[0].targetCount
            voiceEnabled = true

            val adhkarCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = roundedBg("#55102A3E", "#440B1B2E", 18f, "#5538BDF8")
                setPadding(24, 20, 24, 20)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 14
                }
            }
            val stepTv = TextView(this).apply {
                text = "${idx + 1} / ${items.size}"
                textSize = 13f
                setTextColor(Color.parseColor("#7DD3FC"))
                gravity = Gravity.CENTER
            }
            val dhikrTv = TextView(this).apply {
                text = if (ar) items[0].arText else items[0].enText
                textSize = 17.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 10, 0, 10)
            }
            val remTv = TextView(this).apply {
                text = if (ar) "المتبقي: $left" else "Remaining: $left"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F59E0B"))
                gravity = Gravity.CENTER
            }

            val voiceRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 8; bottomMargin = 8
                }
            }
            val btnSpeak = Button(this).apply {
                text = if (ar) "🔊 استماع للذكر" else "🔊 Listen"
                background = roundedBg("#0284C7", "#0369A1", 12f)
                setTextColor(Color.WHITE)
                isAllCaps = false
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 }
                setOnClickListener {
                    voiceEnabled = true
                    speakDhikr(if (ar) items[idx].arText else items[idx].enText)
                }
            }
            val btnToggleVoice = Button(this).apply {
                text = if (ar) "🔇 كتم القراءة" else "🔇 Mute Voice"
                background = roundedBg("#441E293B", "#440F172A", 12f, "#4464748B")
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

            val beadView = BeadTasbeehView(this).apply {
                beadTheme = store.selectedBeadTheme
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 240)
            }

            fun advanceDhikr() {
                left--
                if (left <= 0) {
                    idx++
                    if (idx >= items.size) {
                        Toast.makeText(
                            this@LockOverlayActivity,
                            if (ar) "تقبّل الله طاعتك! تم فتح الهاتف." else "May Allah accept your prayers! Phone unlocked.",
                            Toast.LENGTH_LONG
                        ).show()
                        startService(Intent(this@LockOverlayActivity, GuardianService::class.java).apply {
                            action = "ACTION_ADHKAR_COMPLETED"
                        })
                        finish()
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

            val hintTv = TextView(this).apply {
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

        val pinInput = EditText(this).apply {
            hint = Strings.get(ar, "auth_msg")
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = roundedBg("#55121C30", "#550F172A", 14f, "#55475569")
            setPadding(28, 22, 28, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 10; bottomMargin = 10
            }
        }

        val btnUnlock = Button(this).apply {
            text = Strings.get(ar, "parent_unlock")
            background = roundedBg("#0284C7", "#0369A1", 14f)
            setTextColor(Color.WHITE)
            isAllCaps = false
            textSize = 14.5f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                if (store.verifyPin(pinInput.text.toString())) {
                    stopAdhan()
                    stopTts()
                    when (mode) {
                        "PRAYER_LOCK" -> startService(Intent(this@LockOverlayActivity, GuardianService::class.java).apply { action = "ACTION_PRAYER_UNLOCKED" })
                        "ADHKAR_LOCK" -> startService(Intent(this@LockOverlayActivity, GuardianService::class.java).apply { action = "ACTION_ADHKAR_COMPLETED" })
                        "BEDTIME_LOCK" -> startService(Intent(this@LockOverlayActivity, GuardianService::class.java).apply { action = "ACTION_BEDTIME_UNLOCKED" })
                        "PHONE_LOCK" -> {
                            store.isPhoneLockedToday = false
                            store.lockReason = ""
                            store.phoneSecondsUsedToday = maxOf(0, (store.dailyPhoneLimitMinutes * 60) - 1800)
                        }
                    }
                    finish()
                } else {
                    Toast.makeText(this@LockOverlayActivity, Strings.get(ar, "wrong_pin"), Toast.LENGTH_SHORT).show()
                }
            }
        }

        root.addView(pinInput)
        root.addView(btnUnlock)

        if (mode == "PHONE_LOCK" || mode == "PRAYER_LOCK" || mode == "ADHKAR_LOCK" || mode == "BEDTIME_LOCK") {
            val btnCalls = Button(this).apply {
                text = Strings.get(ar, "open_calls")
                background = roundedBg("#0EA5E9", "#0284C7", 14f)
                setTextColor(Color.WHITE)
                isAllCaps = false
                textSize = 14.5f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 12
                }
                setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_DIAL).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                    } catch (_: Exception) { }
                }
            }
            root.addView(btnCalls)
        }

        if (mode == "APP_BLOCKED") {
            val btnHome = Button(this).apply {
                text = Strings.get(ar, "go_home")
                background = roundedBg("#441E293B", "#440F172A", 12f, "#4464748B")
                setTextColor(Color.WHITE)
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 12
                }
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                    finish()
                }
            }
            root.addView(btnHome)
        }

        scroll.addView(root)
        setContentView(scroll)
        if (isPrayer) {
            if (store.adhanAudioEnabled) playAdhan()
            if (endMillis > System.currentTimeMillis()) handler.post(countdownRunnable)
        }
    }

    private fun playAdhan() {
        try {
            stopAdhan()
            mediaPlayer = MediaPlayer.create(this, R.raw.iqama)
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

    override fun onDestroy() {
        handler.removeCallbacks(countdownRunnable); stopAdhan(); stopTts(); super.onDestroy()
    }

    private fun vibrateLockDhikr(completed: Boolean) {
        try {
            val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vib == null || !vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (completed) vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 120L, 65L, 150L), -1))
                else vib.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                if (completed) vib.vibrate(longArrayOf(0L, 120L, 65L, 150L), -1)
                else vib.vibrate(18L)
            }
        } catch (_: Exception) { }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            try { mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null; waveView?.isPlayingWave = false } catch (_: Exception) { }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
