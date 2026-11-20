package com.muslimtimekeeper.app.ui

import android.content.Intent
import android.graphics.Color
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
import com.muslimtimekeeper.app.core.PrefsStore
import com.muslimtimekeeper.app.core.Strings
import com.muslimtimekeeper.app.service.GuardianService

class LockOverlayActivity : AppCompatActivity() {
    private lateinit var store: PrefsStore
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var endMillis: Long = 0L
    private lateinit var timerTv: TextView
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
            timerTv.text = String.format("%02d:%02d", remSec / 60, remSec % 60)
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
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (isPrayer) intArrayOf(Color.parseColor("#041F17"), Color.parseColor("#0B3B2E"), Color.parseColor("#070B14"))
                else intArrayOf(Color.parseColor("#2A0C12"), Color.parseColor("#180A0E"), Color.parseColor("#070B14"))
            )
            setPadding(52, 52, 52, 52)
        }

        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(400, 400).apply { bottomMargin = 18 }
        }
        val ring = GlowingMandalaRingView(this).apply { setProgressRatio(1.0f) }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            layoutParams = FrameLayout.LayoutParams(200, 200, Gravity.CENTER)
        }
        frame.addView(ring)
        frame.addView(logo)

        val title = TextView(this).apply {
            text = when (mode) {
                "PRAYER_LOCK" -> "🕌  $reason"
                "APP_BLOCKED" -> Strings.get(ar, "app_blocked_title")
                else -> Strings.get(ar, "phone_locked_title")
            }
            textSize = 25f
            setTextColor(if (isPrayer) Color.parseColor("#F59E0B") else Color.parseColor("#F87171"))
            gravity = Gravity.CENTER
        }

        val sub = TextView(this).apply {
            text = if (isPrayer) Strings.get(ar, "prayer_verse") else reason
            textSize = 14.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 18)
        }

        timerTv = TextView(this).apply {
            text = if (isPrayer) "${store.prayerDurationMinutes}:00" else ""
            textSize = 48f
            setTextColor(Color.parseColor("#F59E0B"))
            gravity = Gravity.CENTER
        }

        val pinInput = EditText(this).apply {
            hint = Strings.get(ar, "auth_msg")
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = roundedBg("#121C30", "#0F172A", 14f, "#334155")
            setPadding(32, 26, 32, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16; bottomMargin = 16
            }
        }

        val btnUnlock = Button(this).apply {
            text = Strings.get(ar, "parent_unlock")
            background = roundedBg("#10B981", "#059669", 14f)
            setTextColor(Color.parseColor("#06281E"))
            isAllCaps = false
            textSize = 15f
            setOnClickListener {
                if (store.verifyPin(pinInput.text.toString())) {
                    stopAdhan()
                    if (isPrayer) {
                        startService(Intent(this@LockOverlayActivity, GuardianService::class.java).apply {
                            action = "ACTION_PRAYER_UNLOCKED"
                        })
                    } else if (mode == "PHONE_LOCK") {
                        store.isPhoneLockedToday = false
                        store.lockReason = ""
                        store.phoneSecondsUsedToday = maxOf(0, (store.dailyPhoneLimitMinutes * 60) - 1800)
                    }
                    finish()
                } else {
                    Toast.makeText(this@LockOverlayActivity, Strings.get(ar, "wrong_pin"), Toast.LENGTH_SHORT).show()
                }
            }
        }

        root.addView(frame)
        root.addView(title)
        root.addView(sub)
        if (isPrayer) {
            root.addView(timerTv)
            waveView = AdhanWaveView(this).apply {
                layoutParams = LinearLayout.LayoutParams(420, 72).apply { topMargin = 8; bottomMargin = 14 }
            }
            root.addView(waveView)
            val btnMute = Button(this).apply {
                text = Strings.get(ar, "mute_adhan")
                background = roundedBg("#1E293B", "#0F172A", 12f, "#334155")
                setTextColor(Color.WHITE)
                isAllCaps = false
                setOnClickListener { stopAdhan() }
            }
            root.addView(btnMute)
        }
        root.addView(pinInput)
        root.addView(btnUnlock)

        if (mode == "PHONE_LOCK" || mode == "PRAYER_LOCK") {
            val btnCalls = Button(this).apply {
                text = Strings.get(ar, "open_calls")
                background = roundedBg("#0284C7", "#0369A1", 14f)
                setTextColor(Color.WHITE)
                isAllCaps = false
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 16 }
                setOnClickListener {
                    try {
                        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(dialIntent)
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

        setContentView(root)
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
