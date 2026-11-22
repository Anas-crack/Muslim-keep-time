package com.muslimtimekeeper.app.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.muslimtimekeeper.app.R
import com.muslimtimekeeper.app.core.AdhkarData
import com.muslimtimekeeper.app.core.PrefsStore
import com.muslimtimekeeper.app.core.Strings
import com.muslimtimekeeper.app.service.GuardianService

class LockOverlayActivity : AppCompatActivity() {
    private lateinit var store: PrefsStore
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var endMillis: Long = 0L
    private var timerTv: TextView? = null
    private var waveView: AdhanWaveView? = null
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
            if (strokeHex != null) setStroke(3, Color.parseColor(strokeHex))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            isPrayer || isAdhkar -> intArrayOf(Color.parseColor("#041F17"), Color.parseColor("#0B3B2E"), Color.parseColor("#070B14"))
            isBedtime -> intArrayOf(Color.parseColor("#0F172A"), Color.parseColor("#1E1B4B"), Color.parseColor("#070B14"))
            else -> intArrayOf(Color.parseColor("#2A0C12"), Color.parseColor("#180A0E"), Color.parseColor("#070B14"))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, bgColors)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(280, 280).apply { bottomMargin = 14 }
        }
        val ring = GlowingMandalaRingView(this).apply { setProgressRatio(1.0f) }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            layoutParams = FrameLayout.LayoutParams(150, 150, Gravity.CENTER)
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

        val sub = TextView(this).apply {
            text = when {
                isPrayer -> Strings.get(ar, "prayer_verse")
                isAdhkar -> if (ar) "يرجى قراءة أذكار بعد الصلاة لفتح الموبايل" else "Please read Post-Prayer Adhkar to unlock your phone"
                else -> reason
            }
            textSize = 14f
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 14)
        }

        root.addView(frame)
        root.addView(title)
        root.addView(sub)

        if (isPrayer) {
            timerTv = TextView(this).apply {
                text = "${store.prayerDurationMinutes}:00"
                textSize = 42f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F59E0B"))
                gravity = Gravity.CENTER
            }
            root.addView(timerTv)

            waveView = AdhanWaveView(this).apply {
                layoutParams = LinearLayout.LayoutParams(360, 56).apply { topMargin = 4; bottomMargin = 10 }
            }
            root.addView(waveView)

            // Calm Electronic Tasbeeh
            val phrases = if (ar) AdhkarData.tasbeehPhrasesAr else AdhkarData.tasbeehPhrasesEn
            var phraseIdx = 0
            var tasbeehCount = 0

            val tasbeehCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = roundedBg("#0D2E25", "#09211B", 16f, "#10B981")
                setPadding(26, 20, 26, 20)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 12
                }
            }
            val tasbeehLbl = TextView(this).apply {
                text = if (ar) "📿 سبحة إلكترونية هادئة" else "📿 Calm Tasbeeh"
                textSize = 12f
                setTextColor(Color.parseColor("#6EE7B7"))
                gravity = Gravity.CENTER
            }
            val phraseTv = TextView(this).apply {
                text = phrases[0]
                textSize = 17.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 6, 0, 4)
            }
            val countTv = TextView(this).apply {
                text = "0"
                textSize = 24f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F59E0B"))
                gravity = Gravity.CENTER
            }
            val rowBtns = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 8
                }
            }
            val btnTap = Button(this).apply {
                text = if (ar) "سبّح (+1)" else "Tap (+1)"
                background = roundedBg("#10B981", "#059669", 12f)
                setTextColor(Color.parseColor("#06281E"))
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f).apply { marginEnd = 8 }
                setOnClickListener {
                    tasbeehCount++
                    countTv.text = "$tasbeehCount"
                }
            }
            val btnNextPhrase = Button(this).apply {
                text = if (ar) "ذكر آخر ↻" else "Next ↻"
                background = roundedBg("#1E293B", "#0F172A", 12f, "#334155")
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

            val btnMute = Button(this).apply {
                text = Strings.get(ar, "mute_adhan")
                background = roundedBg("#1E293B", "#0F172A", 12f, "#334155")
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

            val adhkarCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = roundedBg("#0D2E25", "#09211B", 18f, "#10B981")
                setPadding(28, 24, 28, 24)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = 14
                }
            }
            val stepTv = TextView(this).apply {
                text = "${idx + 1} / ${items.size}"
                textSize = 13f
                setTextColor(Color.parseColor("#6EE7B7"))
                gravity = Gravity.CENTER
            }
            val dhikrTv = TextView(this).apply {
                text = if (ar) items[0].arText else items[0].enText
                textSize = 18.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 12, 0, 12)
            }
            val remTv = TextView(this).apply {
                text = if (ar) "المتبقي: $left" else "Remaining: $left"
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F59E0B"))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 12)
            }
            val btnRead = Button(this).apply {
                text = if (ar) "📿 اضغط بعد القراءة / التسبيح" else "📿 Tap After Reading"
                background = roundedBg("#10B981", "#059669", 14f)
                setTextColor(Color.parseColor("#06281E"))
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setOnClickListener {
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

        val pinInput = EditText(this).apply {
            hint = Strings.get(ar, "auth_msg")
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = roundedBg("#121C30", "#0F172A", 14f, "#334155")
            setPadding(28, 22, 28, 22)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12; bottomMargin = 12
            }
        }

        val btnUnlock = Button(this).apply {
            text = Strings.get(ar, "parent_unlock")
            background = roundedBg("#10B981", "#059669", 14f)
            setTextColor(Color.parseColor("#06281E"))
            isAllCaps = false
            textSize = 14.5f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                if (store.verifyPin(pinInput.text.toString())) {
                    stopAdhan()
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
                background = roundedBg("#0284C7", "#0369A1", 14f)
                setTextColor(Color.WHITE)
                isAllCaps = false
                textSize = 14.5f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 14
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
                background = roundedBg("#1E293B", "#0F172A", 12f, "#334155")
                setTextColor(Color.WHITE)
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 14
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
            mediaPlayer = MediaPlayer.create(this, R.raw.adhan)
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
        handler.removeCallbacks(countdownRunnable); stopAdhan(); super.onDestroy()
    }
}
