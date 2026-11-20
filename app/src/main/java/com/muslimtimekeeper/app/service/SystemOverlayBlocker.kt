package com.muslimtimekeeper.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
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
import com.muslimtimekeeper.app.core.PrefsStore
import com.muslimtimekeeper.app.core.Strings

/**
 * True System-Level Overlay Lock using WindowManager (TYPE_APPLICATION_OVERLAY).
 * Works reliably on Android 10-15 (Samsung, Xiaomi, Oppo, Pixel) even when
 * background startActivity() is blocked by the OS!
 */
class SystemOverlayBlocker(
    private val context: Context,
    private val store: PrefsStore,
    private val onPrayerUnlocked: () -> Unit,
    private val onPhoneUnlocked: () -> Unit
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

    fun show(mode: String, reason: String, prayerEndMillis: Long = 0L) {
        if (!canDrawOverlay()) return
        if (overlayView != null && currentMode == mode) return
        hide()

        currentMode = mode
        endMillis = prayerEndMillis
        val ar = store.isArabic
        val isPrayer = (mode == "PRAYER_LOCK")

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutDirection = if (ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (isPrayer) intArrayOf(Color.parseColor("#041F17"), Color.parseColor("#0B3B2E"), Color.parseColor("#070B14"))
                else intArrayOf(Color.parseColor("#2A0C12"), Color.parseColor("#180A0E"), Color.parseColor("#070B14"))
            )
            setPadding(56, 72, 56, 72)
        }

        val logo = ImageView(context).apply {
            setImageResource(R.drawable.app_logo)
            layoutParams = LinearLayout.LayoutParams(210, 210).apply { bottomMargin = 22 }
        }

        val title = TextView(context).apply {
            text = when (mode) {
                "PRAYER_LOCK" -> "🕌  $reason"
                "APP_BLOCKED" -> Strings.get(ar, "app_blocked_title")
                else -> Strings.get(ar, "phone_locked_title")
            }
            textSize = 26f
            setTextColor(if (isPrayer) Color.parseColor("#F59E0B") else Color.parseColor("#F87171"))
            gravity = Gravity.CENTER
        }

        val sub = TextView(context).apply {
            text = if (isPrayer) Strings.get(ar, "prayer_verse") else reason
            textSize = 15f
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 20)
        }

        timerTv = TextView(context).apply {
            text = if (isPrayer) "${store.prayerDurationMinutes}:00" else ""
            textSize = 46f
            setTextColor(Color.parseColor("#F59E0B"))
            gravity = Gravity.CENTER
        }

        val errTv = TextView(context).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.parseColor("#F87171"))
            gravity = Gravity.CENTER
            setPadding(0, 4, 0, 8)
        }

        val pinIn = EditText(context).apply {
            hint = Strings.get(ar, "auth_msg")
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121C30"))
                cornerRadius = 24f
                setStroke(3, Color.parseColor("#334155"))
            }
            setPadding(32, 26, 32, 26)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 14; bottomMargin = 12
            }
        }

        val btnUnlock = Button(context).apply {
            text = Strings.get(ar, "parent_unlock")
            setBackgroundColor(Color.parseColor("#10B981"))
            setTextColor(Color.parseColor("#06281E"))
            isAllCaps = false
            textSize = 15f
            setOnClickListener {
                if (store.verifyPin(pinIn.text.toString())) {
                    hide()
                    if (isPrayer) onPrayerUnlocked()
                    else if (mode == "PHONE_LOCK") onPhoneUnlocked()
                } else {
                    errTv.text = Strings.get(ar, "wrong_pin")
                    pinIn.text.clear()
                }
            }
        }

        root.addView(logo)
        root.addView(title)
        root.addView(sub)
        if (isPrayer) {
            root.addView(timerTv)
            val btnMute = Button(context).apply {
                text = Strings.get(ar, "mute_adhan")
                setBackgroundColor(Color.parseColor("#334155"))
                setTextColor(Color.WHITE)
                isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 10; bottomMargin = 10
                }
                setOnClickListener { stopAdhan() }
            }
            root.addView(btnMute)
        }
        root.addView(pinIn)
        root.addView(errTv)
        root.addView(btnUnlock)

        if (mode == "PHONE_LOCK" || mode == "PRAYER_LOCK") {
            val btnCalls = Button(context).apply {
                text = Strings.get(ar, "open_calls")
                setBackgroundColor(Color.parseColor("#0284C7"))
                setTextColor(Color.WHITE)
                isAllCaps = false
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 16
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
                    topMargin = 16
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
            wm.addView(root, params)
            overlayView = root
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
