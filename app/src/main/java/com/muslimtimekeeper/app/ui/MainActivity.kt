package com.muslimtimekeeper.app.ui

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.muslimtimekeeper.app.R
import com.muslimtimekeeper.app.core.AdhkarData
import com.muslimtimekeeper.app.core.AppRule
import com.muslimtimekeeper.app.core.PrayerCalculator
import com.muslimtimekeeper.app.core.PrefsStore
import com.muslimtimekeeper.app.core.QuranData
import com.muslimtimekeeper.app.core.ScheduleItem
import com.muslimtimekeeper.app.core.Strings
import com.muslimtimekeeper.app.core.UpdateManager
import com.muslimtimekeeper.app.service.DeviceAdminProtectionReceiver
import com.muslimtimekeeper.app.service.GuardianService
import com.muslimtimekeeper.app.service.SafeDnsVpnService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var store: PrefsStore
    private var parentUnlocked = false
    private var currentTab = 0
    private var testPlayer: MediaPlayer? = null
    private var prayerWaveView: AdhanWaveView? = null
    private var tasbeehTts: TextToSpeech? = null
    private var tasbeehSessionCount = 0
    private var adhkarCategory = 0 // 0=Daily, 1=Morning, 2=Evening, 3=Sleep, 4=Waking
    private var adhkarCatIdx = 0
    private var manageSubTab = 0 // 0=Apps, 1=Data, 2=Safety
    private var mushafDownloading = false
    private var mushafDownloadCancelled = false
    private var showFadlInTasbeeh = true
    private lateinit var contentContainer: FrameLayout
    private lateinit var navButtons: List<TextView>
    private lateinit var lockStatusChip: TextView

    private var liveTimerTv: TextView? = null
    private var livePctTv: TextView? = null
    private var liveBar: ProgressBar? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private val liveClockRunnable = object : Runnable {
        override fun run() {
            if (currentTab == 0) updateLiveTimerUi()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            startService(Intent(this, SafeDnsVpnService::class.java))
            store.webSafetyEnabled = true
            renderTab(currentTab)
        }
    }

    // Translucent Frosted Glass (Glassmorphism Wallpaper Blur) Palette — Dark & Light Glass
    private fun bgCanvas(): Int = Color.parseColor(if (store.isDarkMode) "#7807111F" else "#96EBF3F9")
    private fun bgBar(): Int = Color.parseColor(if (store.isDarkMode) "#99091526" else "#B8FFFFFF")
    private fun bgCard(): Int = Color.parseColor(if (store.isDarkMode) "#6E0F2036" else "#99FFFFFF")
    private fun borderCol(): Int = Color.parseColor(if (store.isDarkMode) "#447DD3FC" else "#6694A3B8")
    private fun textPrimary(): Int = Color.parseColor(if (store.isDarkMode) "#F8FAFC" else "#0F172A")
    private fun textMuted(): Int = Color.parseColor(if (store.isDarkMode) "#CBD5E1" else "#475569")
    private fun btnNeutralBg(): String = if (store.isDarkMode) "#661E293B" else "#88E2E8F0"
    private fun btnNeutralFg(): String = if (store.isDarkMode) "#F8FAFC" else "#0F172A"
    private fun accentCol(): Int = Color.parseColor(if (store.isDarkMode) "#38BDF8" else "#0284C7")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = 60 }
                window.setBackgroundBlurRadius(55)
            } catch (_: Exception) { }
        }
        store = PrefsStore(this)
        ContextCompat.startForegroundService(this, Intent(this, GuardianService::class.java))
        buildShell()
        renderTab(0)
        uiHandler.post(liveClockRunnable)
        if (store.userName.isBlank()) promptUserName()
    }

    private var autoUpdateCheckedOnce = false

    private fun vibrateDhikr(completed: Boolean) {
        try {
            val vib: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vib == null || !vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (completed) {
                    // Distinct double-pulse vibration whenever any Dhikr finishes (even target == 1)
                    vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 120L, 65L, 150L), -1))
                } else {
                    vib.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
                if (completed) vib.vibrate(longArrayOf(0L, 120L, 65L, 150L), -1)
                else vib.vibrate(18L)
            }
        } catch (_: Exception) { }
    }

    private fun launchApkUpdate(apkUrl: String) {
        val ar = store.isArabic
        if (UpdateManager.needsInstallPermission(this)) {
            Toast.makeText(
                this,
                if (ar) "يرجى تفعيل إذن (تثبيت التطبيقات غير المعروفة) لـ حارس وقت المسلم ثم الضغط على تحديث مرة أخرى"
                else "Please allow 'Install unknown apps' for Muslim Time Keeper, then tap Update again",
                Toast.LENGTH_LONG
            ).show()
            UpdateManager.openInstallPermissionSettings(this)
            return
        }
        Toast.makeText(
            this,
            if (ar) "جاري تحميل التحديث الآن، يرجى الانتظار..." else "Downloading update, please wait...",
            Toast.LENGTH_LONG
        ).show()
        Thread {
            val (ok, err) = UpdateManager.downloadAndInstallApk(this, apkUrl)
            uiHandler.post {
                if (!ok) {
                    Toast.makeText(
                        this,
                        if (ar) "تعذر تحميل التحديث: ${err ?: "خطأ في الشبكة"}" else "Update failed: ${err ?: "Network error"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    private fun autoCheckUpdateOnStart() {
        if (autoUpdateCheckedOnce) return
        autoUpdateCheckedOnce = true
        val repo = store.updateRepo.ifBlank { "Anas-crack/Muslim-keep-time" }
        Thread {
            val res = UpdateManager.checkLatestReleaseDetailed(this, repo)
            val rel = res.release ?: return@Thread
            uiHandler.post {
                if (isFinishing || isDestroyed) return@post
                val ar = store.isArabic
                AlertDialog.Builder(this)
                    .setTitle(if (ar) "🚀 تحديث جديد متاح (${rel.versionTag})" else "🚀 New Update Available (${rel.versionTag})")
                    .setMessage(
                        if (ar) "يتوفر إصدار أحدث من حارس وقت المسلم (${rel.versionTag}).\nهل تريد تحميله وتثبيته الآن؟"
                        else "A newer version (${rel.versionTag}) is available.\nDownload and install now?"
                    )
                    .setPositiveButton(if (ar) "تحديث الآن" else "Update Now") { _, _ ->
                        launchApkUpdate(rel.apkDownloadUrl)
                    }
                    .setNegativeButton(if (ar) "لاحقاً" else "Later", null)
                    .show()
            }
        }.start()
    }

    private fun promptUserName() {
        val input = EditText(this).apply {
            hint = if (store.isArabic) "اكتب اسمك (مثال: أنس)" else "Enter your name (e.g. Anas)"
        }
        AlertDialog.Builder(this)
            .setTitle(if (store.isArabic) "مرحباً بك في حارس وقت المسلم" else "Welcome to Muslim Time Keeper")
            .setMessage(if (store.isArabic) "اكتب اسمك لنرحب بك في كل مرة تفتح فيها التطبيق:" else "Enter your name so we can greet you:")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(s("save")) { _, _ ->
                store.userName = input.text.toString().trim().ifEmpty { if (store.isArabic) "أنس" else "Anas" }
                renderTab(0)
            }
            .show()
    }

    private fun s(key: String): String = Strings.get(store.isArabic, key)
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun cardBg(borderHex: String? = null): GradientDrawable = GradientDrawable().apply {
        setColor(bgCard())
        cornerRadius = dp(20).toFloat()
        setStroke(dp(1), if (borderHex != null) Color.parseColor(borderHex) else borderCol())
    }

    private fun gradientBg(c1: String, c2: String, radius: Int = 20): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor(c1), Color.parseColor(c2))
    ).apply { cornerRadius = dp(radius).toFloat() }

    private fun requireParentPin(onSuccess: () -> Unit) {
        if (parentUnlocked) { onSuccess(); return }
        val input = EditText(this).apply {
            hint = s("auth_msg")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle(s("auth_title"))
            .setMessage(s("auth_msg"))
            .setView(input)
            .setPositiveButton(s("save")) { _, _ ->
                if (store.verifyPin(input.text.toString())) {
                    parentUnlocked = true; updateLockChip(); onSuccess()
                } else {
                    Toast.makeText(this, s("wrong_pin"), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(s("cancel"), null)
            .show()
    }

    private fun updateLockChip() {
        lockStatusChip.text = if (parentUnlocked) s("unlocked") else s("locked")
        lockStatusChip.background = if (parentUnlocked) gradientBg("#059669", "#10B981", 12) else gradientBg(btnNeutralBg(), btnNeutralBg(), 12)
        lockStatusChip.setTextColor(if (parentUnlocked) Color.WHITE else Color.parseColor(btnNeutralFg()))
    }

    private fun buildShell() {
        window.decorView.layoutDirection = if (store.isArabic) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        window.statusBarColor = bgBar()
        window.navigationBarColor = bgBar()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgCanvas()) }

        // Top Bar: Logo + Title + Theme Toggle (🌙/☀️) + Language Toggle (EN/عربي) + Lock Chip
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(bgBar()); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) }
        }
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBox.addView(TextView(this).apply { text = s("app_name"); textSize = 15.5f; setTypeface(null, Typeface.BOLD); setTextColor(textPrimary()) })
        titleBox.addView(TextView(this).apply {
            text = if (store.isArabic) "أهلاً ${store.userName.ifBlank { "" }}" else "Hi ${store.userName.ifBlank { "" }}"
            textSize = 11.5f; setTextColor(textMuted())
        })

        val themeBtn = TextView(this).apply {
            text = if (store.isDarkMode) "☀️" else "🌙"
            textSize = 14f; background = gradientBg(btnNeutralBg(), btnNeutralBg(), 10); setPadding(dp(9), dp(6), dp(9), dp(6))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) }
            setOnClickListener { store.isDarkMode = !store.isDarkMode; buildShell(); renderTab(currentTab) }
        }

        val langBtn = TextView(this).apply {
            text = if (store.isArabic) "EN" else "عربي"
            textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(accentCol())
            background = gradientBg(btnNeutralBg(), btnNeutralBg(), 10); setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) }
            setOnClickListener { store.isArabic = !store.isArabic; buildShell(); renderTab(currentTab) }
        }

        lockStatusChip = TextView(this).apply {
            textSize = 11.5f; setTypeface(null, Typeface.BOLD); setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { if (parentUnlocked) { parentUnlocked = false; updateLockChip() } else requireParentPin { } }
        }
        updateLockChip()
        topBar.addView(logo); topBar.addView(titleBox); topBar.addView(themeBtn); topBar.addView(langBtn); topBar.addView(lockStatusChip)

        contentContainer = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }

        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(bgBar()); setPadding(dp(8), dp(8), dp(8), dp(10))
        }
        val labels = listOf(s("tab_home"), s("tab_quran"), s("tab_tasbeeh"), s("tab_schedule"), s("tab_prayer"), s("tab_manage"))
        navButtons = labels.mapIndexed { idx, txt ->
            TextView(this).apply {
                text = txt; gravity = Gravity.CENTER; textSize = 10.5f; setTypeface(null, Typeface.BOLD); setPadding(dp(2), dp(8), dp(2), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4); marginEnd = dp(4) }
                setOnClickListener { renderTab(idx) }
            }.also { navBar.addView(it) }
        }

        root.addView(topBar); root.addView(contentContainer); root.addView(navBar)
        setContentView(root)
    }

    private fun renderTab(index: Int) {
        currentTab = index
        liveTimerTv = null; livePctTv = null; liveBar = null
        navButtons.forEachIndexed { i, tv ->
            if (i == index) {
                tv.background = gradientBg(if (store.isDarkMode) "#4438BDF8" else "#550284C7", if (store.isDarkMode) "#4438BDF8" else "#550284C7", 12)
                tv.setTextColor(accentCol())
            } else {
                tv.background = null; tv.setTextColor(textMuted())
            }
        }
        val pageView = when (index) {
            0 -> buildHomeTab()
            1 -> buildQuranTab()
            2 -> buildTasbeehTab()
            3 -> buildScheduleTab()
            4 -> buildPrayerTab()
            else -> buildManageTab()
        }
        contentContainer.removeAllViews()
        pageView.alpha = 0f; pageView.translationY = 20f
        contentContainer.addView(pageView)
        pageView.animate().alpha(1f).translationY(0f).setDuration(220L).start()
    }

    private fun updateLiveTimerUi() {
        if (!store.dailyTimerEnabled) {
            liveTimerTv?.text = if (store.isArabic) "مغلق" else "OFF"
            livePctTv?.text = if (store.isArabic) "مغلق" else "OFF"
            liveBar?.progress = 0
            return
        }
        val totalSec = maxOf(1, store.dailyPhoneLimitMinutes * 60)
        val remSec = maxOf(0, totalSec - store.phoneSecondsUsedToday)
        liveTimerTv?.text = String.format("%02d:%02d:%02d", remSec / 3600, (remSec % 3600) / 60, remSec % 60)
        val pct = ((store.phoneSecondsUsedToday * 100.0) / totalSec).toInt().coerceIn(0, 100)
        livePctTv?.text = "$pct%"
        liveBar?.progress = pct
    }

    private fun makeCard(title: String? = null, borderHex: String? = null): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = cardBg(borderHex); setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
            if (title != null) addView(TextView(this@MainActivity).apply {
                text = title; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(accentCol()); setPadding(0, 0, 0, dp(10))
            })
        }
    }

    private fun makeBtn(label: String, c1: String, c2: String, fgHex: String = "#FFFFFF", onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label; background = gradientBg(c1, c2, 12); setTextColor(Color.parseColor(fgHex)); isAllCaps = false; textSize = 13.5f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(10) }
            setOnClickListener { onClick() }
        }
    }

    // --- Tab 0: Emerald Hero Card (Matching User's Reference Screenshots — No Pomodoro!) ---
    private fun buildHomeTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }

        // 1. Emerald Hero Greeting Card
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradientBg(if (store.isDarkMode) "#880F2942" else "#B80284C7", if (store.isDarkMode) "#780C4A6E" else "#A80369A1", 22).apply { setStroke(dp(1), Color.parseColor("#557DD3FC")) }
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
        }
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greetWord = if (store.isArabic) (if (hour < 12) "صباح الخير" else "مساء الخير") else (if (hour < 12) "Good Morning" else "Good Evening")
        val displayName = store.userName.ifBlank { if (store.isArabic) "أنس" else "Anas" }

        hero.addView(TextView(this).apply { text = "☀️  $greetWord"; textSize = 13f; setTextColor(Color.parseColor("#D1FAE5")) })
        hero.addView(TextView(this).apply {
            text = "$displayName 👋"; textSize = 24f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            setPadding(0, dp(2), 0, dp(2))
        })
        val dateFmt = SimpleDateFormat("EEEE • dd MMMM", if (store.isArabic) Locale("ar") else Locale.US)
        hero.addView(TextView(this).apply { text = dateFmt.format(Date()); textSize = 12f; setTextColor(Color.parseColor("#A7F3D0")); setPadding(0, 0, 0, dp(14)) })

        // 4 Mini Translucent Stat Pills inside Emerald Hero (just like reference image!)
        val nextP = PrayerCalculator.getNextPrayer(store.latitude, store.longitude, store.timezoneOffset, store.isArabic)
        val timeFmt = SimpleDateFormat("hh:mm", Locale.US)

        val pillsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun addMiniPill(valText: String, lblText: String, captureTimer: Boolean = false) {
            val pill = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = GradientDrawable().apply { setColor(Color.argb(46, 255, 255, 255)); cornerRadius = dp(14).toFloat() }
                setPadding(dp(6), dp(10), dp(6), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
            }
            val vTv = TextView(this).apply {
                text = valText; textSize = if (captureTimer) 13.5f else 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); gravity = Gravity.CENTER
            }
            if (captureTimer) liveTimerTv = vTv
            pill.addView(vTv)
            pill.addView(TextView(this).apply { text = lblText; textSize = 10f; setTextColor(Color.parseColor("#D1FAE5")); gravity = Gravity.CENTER })
            pillsRow.addView(pill)
        }
        addMiniPill("03:00:00", if (store.isArabic) "المتبقي" else "Left", true)
        addMiniPill("${nextP.first}\n${timeFmt.format(Date(nextP.second))}", if (store.isArabic) "الصلاة" else "Prayer")
        addMiniPill("${store.getAppRules().size}", if (store.isArabic) "تطبيقات" else "Apps")
        addMiniPill(if (store.webSafetyEnabled) "✓" else "✕", if (store.isArabic) "الويب" else "Web")
        hero.addView(pillsRow)
        col.addView(hero)

        // 2. Daily Screen Time Progress Card (Clean modern style)
        val progCard = makeCard()
        val swDailyTimer = Switch(this).apply {
            text = if (store.isArabic) "⏱️ تفعيل مؤقت الوقت اليومي (اختياري للكبار)" else "⏱️ Enable Daily Phone Timer (Optional)"
            isChecked = store.dailyTimerEnabled
            setTextColor(textPrimary())
            setPadding(0, 0, 0, dp(8))
            setOnCheckedChangeListener { _, checked ->
                requireParentPin {
                    store.dailyTimerEnabled = checked
                    if (!checked) {
                        store.isPhoneLockedToday = false
                        store.lockReason = ""
                    }
                    renderTab(0)
                }
            }
        }
        progCard.addView(swDailyTimer)
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topRow.addView(TextView(this).apply {
            text = "⏱️  ${s("daily_limit")} (${store.dailyPhoneLimitMinutes} ${s("minutes")})"
            textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(textPrimary())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        livePctTv = TextView(this).apply { text = "0%"; textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#10B981")) }
        topRow.addView(livePctTv)
        progCard.addView(topRow)

        liveBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14)).apply { topMargin = dp(12); bottomMargin = dp(14) }
        }
        progCard.addView(liveBar)

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (delta in listOf(-30, -15, 15, 30)) {
            val b = Button(this).apply {
                text = if (delta > 0) "+${delta}m" else "${delta}m"
                background = gradientBg(btnNeutralBg(), btnNeutralBg(), 10)
                setTextColor(Color.parseColor(btnNeutralFg())); isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
                setOnClickListener {
                    requireParentPin {
                        store.dailyPhoneLimitMinutes = (store.dailyPhoneLimitMinutes + delta).coerceIn(15, 720)
                        renderTab(0)
                    }
                }
            }
            btnRow.addView(b)
        }
        progCard.addView(btnRow)
        col.addView(progCard)

        // 3. Bedtime Lock Card (Automatic Overnight Sleep Schedule)
        val bedCard = makeCard(if (store.isArabic) "🌙 وضع النوم التلقائي (Bedtime Lock)" else "🌙 Bedtime Lock (Auto Sleep)", "#818CF8")
        val swBed = Switch(this).apply {
            text = if (store.isArabic) "قفل الهاتف تلقائياً وقت النوم (المكالمات الطارئة متاحة)" else "Auto-lock phone during bedtime (Emergency calls allowed)"
            isChecked = store.bedtimeEnabled
            setTextColor(textPrimary())
            setOnCheckedChangeListener { _, checked ->
                requireParentPin { store.bedtimeEnabled = checked; renderTab(0) }
            }
        }
        bedCard.addView(swBed)

        val bedTimeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(6)) }
        fun addHourCtrl(label: String, hourVal: Int, onChange: (Int) -> Unit) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = gradientBg(btnNeutralBg(), btnNeutralBg(), 12)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4); marginEnd = dp(4) }
            }
            box.addView(TextView(this).apply { text = label; textSize = 11f; setTextColor(textMuted()); gravity = Gravity.CENTER })
            box.addView(TextView(this).apply { text = String.format(Locale.US, "%02d:00", hourVal); textSize = 17f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#818CF8")); gravity = Gravity.CENTER })
            val bRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            val bMinus = Button(this).apply {
                text = "-"; isAllCaps = false; textSize = 13f
                background = gradientBg("#1E293B", "#1E293B", 8); setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).apply { marginEnd = dp(4) }
                setOnClickListener { requireParentPin { onChange((hourVal + 23) % 24); renderTab(0) } }
            }
            val bPlus = Button(this).apply {
                text = "+"; isAllCaps = false; textSize = 13f
                background = gradientBg("#1E293B", "#1E293B", 8); setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f)
                setOnClickListener { requireParentPin { onChange((hourVal + 1) % 24); renderTab(0) } }
            }
            bRow.addView(bMinus); bRow.addView(bPlus)
            box.addView(bRow)
            bedTimeRow.addView(box)
        }
        addHourCtrl(if (store.isArabic) "بداية النوم" else "Sleep Start", store.bedtimeStartHour) { store.bedtimeStartHour = it }
        addHourCtrl(if (store.isArabic) "الاستيقاظ" else "Wake Up", store.bedtimeEndHour) { store.bedtimeEndHour = it }
        bedCard.addView(bedTimeRow)

        val btnSleepTime = makeBtn(
            if (store.isArabic) "⏰ تحديد ميعاد النوم بدقة (${String.format(Locale.US, "%02d:%02d", store.bedtimeStartHour, store.bedtimeStartMinute)})"
            else "⏰ Set Exact Bedtime (${String.format(Locale.US, "%02d:%02d", store.bedtimeStartHour, store.bedtimeStartMinute)})",
            btnNeutralBg(), btnNeutralBg(), btnNeutralFg()
        ) {
            TimePickerDialog(this, { _, h, m ->
                store.bedtimeStartHour = h
                store.bedtimeStartMinute = m
                renderTab(0)
            }, store.bedtimeStartHour, store.bedtimeStartMinute, false).show()
        }
        bedCard.addView(btnSleepTime)

        val swSleepAdhkar = Switch(this).apply {
            text = if (store.isArabic) "📿 تذكير بأذكار النوم والدلائل قبل الميعاد بـ 10 و5 دقائق" else "📿 Remind Sleep Adhkar & Evidence 10m & 5m before bedtime"
            isChecked = store.sleepAdhkarReminderEnabled
            setTextColor(textPrimary())
            setPadding(0, dp(8), 0, 0)
            setOnCheckedChangeListener { _, checked -> store.sleepAdhkarReminderEnabled = checked }
        }
        bedCard.addView(swSleepAdhkar)
        col.addView(bedCard)

        // 4. Weekly Report Quick Card
        val repCard = makeCard(if (store.isArabic) "📊 التقرير الأسبوعي السريع" else "📊 Quick Weekly Report", "#10B981")
        repCard.addView(TextView(this).apply {
            text = if (store.isArabic) "اعرض أكثر التطبيقات استهلاكاً للوقت والإنترنت هذا الأسبوع مع مقارنة بالأسبوع السابق." else "View top apps by screen time and internet usage this week vs. last week."
            textSize = 12.5f; setTextColor(textMuted())
        })
        repCard.addView(makeBtn(if (store.isArabic) "📈 فتح التقرير الأسبوعي" else "📈 Open Weekly Report", "#059669", "#10B981") {
            showWeeklyReportDialog()
        })
        col.addView(repCard)

        // 3. Quick Reset Card
        col.addView(makeBtn(s("reset_today"), btnNeutralBg(), btnNeutralBg(), btnNeutralFg()) {
            requireParentPin { store.resetAllToday(); renderTab(0) }
        })

        updateLiveTimerUi()
        scroll.addView(col)
        return scroll
    }

    private fun showWeeklyReportDialog() {
        val ar = store.isArabic
        val dayMs = 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // Aggregate screen time for this week (last 7d) vs previous week (days 8..14)
        val thisWeekMap = mutableMapOf<String, Long>()
        var thisWeekMs = 0L
        var prevWeekMs = 0L
        try {
            val curStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 7L * dayMs, now) ?: emptyList()
            for (u in curStats) {
                if (u.totalTimeInForeground > 0L && u.packageName != packageName) {
                    thisWeekMap[u.packageName] = (thisWeekMap[u.packageName] ?: 0L) + u.totalTimeInForeground
                    thisWeekMs += u.totalTimeInForeground
                }
            }
            val prevStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 14L * dayMs, now - 7L * dayMs) ?: emptyList()
            for (u in prevStats) {
                if (u.totalTimeInForeground > 0L && u.packageName != packageName) {
                    prevWeekMs += u.totalTimeInForeground
                }
            }
        } catch (_: Exception) { }

        // Aggregate daily history data (Internet bytes) for this week vs previous week
        val hist = store.getDailyHistoryMap()
        val fmtDay = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        var thisWeekDataBytes = 0L
        var prevWeekDataBytes = 0L
        for (d in 0 until 7) {
            val kCur = fmtDay.format(Date(now - d * dayMs))
            val kPrev = fmtDay.format(Date(now - (d + 7) * dayMs))
            thisWeekDataBytes += hist[kCur]?.second ?: 0L
            prevWeekDataBytes += hist[kPrev]?.second ?: 0L
        }
        if (thisWeekDataBytes == 0L) thisWeekDataBytes = store.dataUsedBytes

        val thisMin = (thisWeekMs / 60_000L).toInt()
        val prevMin = (prevWeekMs / 60_000L).toInt()
        val diffTimeStr = if (prevMin > 0) {
            val pct = ((thisMin - prevMin) * 100) / prevMin
            if (pct >= 0) "▲ +$pct%" else "▼ $pct%"
        } else {
            if (ar) "أول أسبوع" else "First week"
        }
        val diffDataStr = if (prevWeekDataBytes > 0L) {
            val pct = (((thisWeekDataBytes - prevWeekDataBytes) * 100L) / prevWeekDataBytes).toInt()
            if (pct >= 0) "▲ +$pct%" else "▼ $pct%"
        } else {
            if (ar) "أول أسبوع" else "First week"
        }

        val pm = packageManager
        val topApps = thisWeekMap.entries.sortedByDescending { it.value }.take(5)

        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (ar) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }

        fun addSummaryLine(title: String, value: String, badge: String, colHex: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = gradientBg(btnNeutralBg(), btnNeutralBg(), 12)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
            }
            val left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            left.addView(TextView(this).apply { text = title; textSize = 12f; setTextColor(textMuted()) })
            left.addView(TextView(this).apply { text = value; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor(colHex)) })
            row.addView(left)
            row.addView(TextView(this).apply { text = badge; textSize = 12.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#F59E0B")) })
            box.addView(row)
        }

        val hThis = thisMin / 60; val mThis = thisMin % 60
        addSummaryLine(
            if (ar) "⏱️ إجمالي وقت الشاشة (آخر 7 أيام)" else "⏱️ Total Screen Time (Last 7 Days)",
            if (ar) "${hThis}س ${mThis}د" else "${hThis}h ${mThis}m",
            diffTimeStr,
            "#10B981"
        )
        addSummaryLine(
            if (ar) "📶 إجمالي استهلاك الإنترنت (آخر 7 أيام)" else "📶 Total Internet Usage (Last 7 Days)",
            formatDataMb(thisWeekDataBytes),
            diffDataStr,
            "#0284C7"
        )

        box.addView(TextView(this).apply {
            text = if (ar) "\n🏆 أكثر 5 تطبيقات استخداماً هذا الأسبوع:" else "\n🏆 Top 5 Most Used Apps This Week:"
            textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(textPrimary())
            setPadding(0, dp(4), 0, dp(8))
        })

        if (topApps.isEmpty()) {
            box.addView(TextView(this).apply {
                text = if (ar) "لا توجد بيانات كافية بعد — تأكد من تفعيل صلاحية تتبع التطبيقات (Usage Access)." else "No usage data yet — make sure Usage Access permission is granted."
                textSize = 12.5f; setTextColor(textMuted())
            })
        } else {
            val maxMs = maxOf(1L, topApps.first().value)
            for ((pkg, ms) in topApps) {
                val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
                val mins = (ms / 60_000L).toInt()
                val hrs = mins / 60; val remM = mins % 60
                val timeStr = if (hrs > 0) "${hrs}h ${remM}m" else "${remM}m"

                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(TextView(this).apply { text = appLabel; textSize = 13.5f; setTypeface(null, Typeface.BOLD); setTextColor(textPrimary()); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                row.addView(TextView(this).apply { text = timeStr; textSize = 13f; setTextColor(Color.parseColor("#10B981")) })
                box.addView(row)

                val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = ((ms * 100L) / maxMs).toInt().coerceIn(5, 100)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)).apply { topMargin = dp(3); bottomMargin = dp(10) }
                }
                box.addView(bar)
            }
        }

        scroll.addView(box)
        AlertDialog.Builder(this)
            .setTitle(if (ar) "📊 التقرير الأسبوعي السريع" else "📊 Quick Weekly Report")
            .setView(scroll)
            .setPositiveButton(if (ar) "إغلاق" else "Close", null)
            .show()
    }

    // --- Tab 1: Apps Picker ---
    private fun showInstalledAppPicker() {
        val pm = packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != packageName }
            .map { Pair(pm.getApplicationLabel(it).toString(), it.packageName) }
            .sortedBy { it.first.lowercase() }

        val dialogLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
        val searchIn = EditText(this).apply { hint = s("search_hint") }
        val listView = ListView(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360)) }
        dialogLayout.addView(searchIn); dialogLayout.addView(listView)

        val dlg = AlertDialog.Builder(this).setTitle(s("pick_app_title")).setView(dialogLayout).setNegativeButton(s("cancel"), null).create()
        fun refreshList(q: String) {
            val filtered = if (q.isEmpty()) installed else installed.filter { it.first.contains(q, true) || it.second.contains(q, true) }
            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filtered.map { it.first })
            listView.setOnItemClickListener { _, _, pos, _ -> dlg.dismiss(); askAppLimitDialog(filtered[pos].first, filtered[pos].second) }
        }
        refreshList("")
        searchIn.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { refreshList(s?.toString() ?: "") }
            override fun afterTextChanged(s: Editable?) {}
        })
        dlg.show()
    }

    private fun askAppLimitDialog(appName: String, pkg: String) {
        val limIn = EditText(this).apply { setText("60"); inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle("$appName — ${s("limit_min")}").setView(limIn)
            .setPositiveButton(s("save")) { _, _ ->
                val lim = (limIn.text.toString().toIntOrNull() ?: 60).coerceIn(1, 720)
                val list = store.getAppRules(); list.removeAll { it.packageName.equals(pkg, true) }
                list.add(AppRule(pkg, appName, lim)); store.saveAppRules(list); run { manageSubTab = 0; renderTab(5) }
            }.setNegativeButton(s("cancel"), null).show()
    }

    private fun buildAppsTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        col.addView(makeBtn(s("add_app_btn"), "#059669", "#10B981") { requireParentPin { showInstalledAppPicker() } })
        col.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(14)) })

        for (rule in store.getAppRules()) {
            val itemCard = makeCard()
            itemCard.addView(TextView(this).apply { text = "${rule.displayName}  (${rule.dailyLimitMinutes} ${s("minutes")})"; textSize = 15.5f; setTypeface(null, Typeface.BOLD); setTextColor(textPrimary()) })
            itemCard.addView(TextView(this).apply { text = "${s("used_today")}: ${rule.secondsUsedToday / 60}m  •  ${rule.packageName}"; textSize = 12f; setTextColor(textMuted()) })

            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
            }
            val btnEdit = Button(this).apply {
                text = if (store.isArabic) "✏️ تعديل الوقت" else "✏️ Edit Time"
                background = gradientBg("#0284C7", "#0EA5E9", 12)
                setTextColor(Color.WHITE); isAllCaps = false; textSize = 13f; setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1.2f).apply { marginEnd = dp(8) }
                setOnClickListener {
                    requireParentPin {
                        val limIn = EditText(this@MainActivity).apply {
                            setText(rule.dailyLimitMinutes.toString())
                            inputType = InputType.TYPE_CLASS_NUMBER
                            setSelection(text.length)
                        }
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle(if (store.isArabic) "تعديل وقت ${rule.displayName} (بالدقائق)" else "Edit ${rule.displayName} Limit (min)")
                            .setMessage(if (store.isArabic) "اكتب الوقت الجديد مباشرة (مثلاً 120) بدون حذف المؤقت:" else "Enter the new daily limit in minutes directly:")
                            .setView(limIn)
                            .setPositiveButton(s("save")) { _, _ ->
                                val newLim = (limIn.text.toString().toIntOrNull() ?: rule.dailyLimitMinutes).coerceIn(1, 1440)
                                val list = store.getAppRules()
                                for (i in list.indices) {
                                    if (list[i].packageName.equals(rule.packageName, true)) {
                                        list[i] = list[i].copy(dailyLimitMinutes = newLim, reopenAttempts = 0)
                                        break
                                    }
                                }
                                store.saveAppRules(list)
                                Toast.makeText(this@MainActivity, if (store.isArabic) "تم تعديل الوقت إلى $newLim دقيقة!" else "Updated limit to $newLim min!", Toast.LENGTH_SHORT).show()
                                run { manageSubTab = 0; renderTab(5) }
                            }
                            .setNegativeButton(s("cancel"), null)
                            .show()
                    }
                }
            }
            val btnRemove = Button(this).apply {
                text = s("remove")
                background = gradientBg("#DC2626", "#EF4444", 12)
                setTextColor(Color.WHITE); isAllCaps = false; textSize = 13f; setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
                setOnClickListener {
                    requireParentPin { val list = store.getAppRules(); list.removeAll { it.packageName.equals(rule.packageName, true) }; store.saveAppRules(list); run { manageSubTab = 0; renderTab(5) } }
                }
            }
            actionRow.addView(btnEdit); actionRow.addView(btnRemove)
            itemCard.addView(actionRow)
            col.addView(itemCard)
        }
        scroll.addView(col)
        return scroll
    }

    // --- Tab 2: Prayer & Adhan (Localized Prayer Names!) ---
    private fun buildPrayerTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }

        val ctrlCard = makeCard(s("prayer_title"), "#10B981")
        val swLock = Switch(this).apply {
            text = s("prayer_lock_sw")
            isChecked = store.prayerLockEnabled
            setTextColor(textPrimary())
            setOnClickListener {
                val wantOn = isChecked
                if (wantOn) {
                    isChecked = false
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(if (store.isArabic) "⚠️ تنبيه مهم قبل تفعيل قفل الصلاة" else "⚠️ Important Warning Before Enabling")
                        .setMessage(if (store.isArabic) "عند تفعيل هذا الخيار، سيتم قفل الهاتف وقت الصلاة ولن تستطيع فتح الموبايل إلا برمز الوالدين (أو بعد انتهاء مدة الصلاة). المكالمات الطارئة فقط ستبقى متاحة.\n\nيرجى التأكد من قرارك قبل التفعيل." else "When enabled, your phone will lock automatically at prayer time and can only be unlocked with the Parent PIN (or when the prayer break ends). Emergency calls remain available.\n\nPlease confirm your decision.")
                        .setPositiveButton(if (store.isArabic) "تأكيد التفعيل" else "Confirm & Enable") { _, _ ->
                            requireParentPin {
                                store.prayerLockEnabled = true
                                isChecked = true
                            }
                        }
                        .setNegativeButton(s("cancel"), null)
                        .show()
                } else {
                    isChecked = true
                    requireParentPin {
                        store.prayerLockEnabled = false
                        isChecked = false
                    }
                }
            }
        }
        val swAdhan = Switch(this).apply { text = s("adhan_sw"); isChecked = store.adhanAudioEnabled; setTextColor(textPrimary()); setOnCheckedChangeListener { _, c -> requireParentPin { store.adhanAudioEnabled = c } } }
        val swSilent = Switch(this).apply {
            text = if (store.isArabic) "🔇 وضع الصمت التلقائي للمسجد (كتم الرنين وإعادته)" else "🔇 Mosque Auto-Silent (Mute & restore ringer)"
            isChecked = store.mosqueAutoSilentEnabled
            setTextColor(textPrimary())
            setOnCheckedChangeListener { _, c -> requireParentPin { store.mosqueAutoSilentEnabled = c } }
        }
        val swAdhkar = Switch(this).apply {
            text = if (store.isArabic) "📿 إلزام أذكار بعد الصلاة لفتح الموبايل" else "📿 Require Post-Prayer Adhkar to unlock phone"
            isChecked = store.postPrayerAdhkarEnabled
            setTextColor(textPrimary())
            setOnCheckedChangeListener { _, c -> requireParentPin { store.postPrayerAdhkarEnabled = c } }
        }
        ctrlCard.addView(swLock); ctrlCard.addView(swAdhan); ctrlCard.addView(swSilent); ctrlCard.addView(swAdhkar)

        ctrlCard.addView(TextView(this).apply { text = "\n${s("prayer_dur_label")} ${store.prayerDurationMinutes} ${s("minutes")}"; setTextColor(Color.parseColor("#10B981")); setTypeface(null, Typeface.BOLD) })
        val durRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(4)) }
        for (mins in listOf(15, 30, 45, 60)) {
            val sel = (store.prayerDurationMinutes == mins)
            val b = Button(this).apply {
                text = "${mins}m"
                background = if (sel) gradientBg("#059669", "#10B981", 10) else gradientBg(btnNeutralBg(), btnNeutralBg(), 10)
                setTextColor(if (sel) Color.WHITE else Color.parseColor(btnNeutralFg()))
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
                setOnClickListener { requireParentPin { store.prayerDurationMinutes = mins; renderTab(4) } }
            }
            durRow.addView(b)
        }
        ctrlCard.addView(durRow)

        prayerWaveView = AdhanWaveView(this).apply {
            isPlayingWave = (testPlayer?.isPlaying == true)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(10); bottomMargin = dp(4)
            }
        }
        ctrlCard.addView(prayerWaveView)
        ctrlCard.addView(makeBtn(s("test_adhan"), "#D97706", "#F59E0B") {
            try {
                testPlayer?.stop(); testPlayer?.release()
                testPlayer = MediaPlayer.create(this, R.raw.adhan)
                testPlayer?.setOnCompletionListener { prayerWaveView?.isPlayingWave = false }
                testPlayer?.start()
                prayerWaveView?.isPlayingWave = true
            } catch (_: Exception) { }
        })
        ctrlCard.addView(makeBtn(s("stop"), btnNeutralBg(), btnNeutralBg(), btnNeutralFg()) {
            try { testPlayer?.stop(); testPlayer?.release() } catch (_: Exception) { }
            testPlayer = null
            prayerWaveView?.isPlayingWave = false
        })
        ctrlCard.addView(makeBtn(s("test_lock"), "#0D9488", "#0284C7") {
            requireParentPin {
                startActivity(Intent(this, LockOverlayActivity::class.java).apply {
                    putExtra("MODE", "PRAYER_LOCK")
                    putExtra("REASON", if (store.isArabic) "صلاة العصر (تجربة)" else "Asr (Test)")
                    putExtra("END_MILLIS", System.currentTimeMillis() + store.prayerDurationMinutes * 60_000L)
                })
            }
        })
        ctrlCard.addView(makeBtn(if (store.isArabic) "📿 تجربة شاشة أذكار بعد الصلاة" else "📿 Test Post-Prayer Adhkar Screen", "#059669", "#10B981") {
            startActivity(Intent(this, LockOverlayActivity::class.java).apply {
                putExtra("MODE", "ADHKAR_LOCK")
                putExtra("REASON", if (store.isArabic) "يرجى قراءة أذكار بعد الصلاة لفتح الموبايل" else "Please read Post-Prayer Adhkar to unlock your phone")
            })
        })
        col.addView(ctrlCard)

        val fmt = SimpleDateFormat("hh:mm a", Locale.US)
        val times = PrayerCalculator.calculateDisplayTimes(store.latitude, store.longitude, store.timezoneOffset)
        for ((enName, cal) in times) {
            val rowCard = makeCard()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply { text = PrayerCalculator.localizedName(enName, store.isArabic); textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(textPrimary()); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(TextView(this).apply { text = fmt.format(cal.time); textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#10B981")) })
            rowCard.addView(row); col.addView(rowCard)
        }
        scroll.addView(col)
        return scroll
    }

    private fun speakTasbeeh(text: String) {
        try {
            if (tasbeehTts == null) {
                tasbeehTts = TextToSpeech(this) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val loc = if (store.isArabic) Locale("ar", "EG") else Locale.US
                        val res = tasbeehTts?.setLanguage(loc)
                        if (store.isArabic && (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED)) {
                            tasbeehTts?.setLanguage(Locale("ar"))
                        }
                        tasbeehTts?.setSpeechRate(0.90f)
                        tasbeehTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tasbeeh_tts")
                    }
                }
            } else {
                tasbeehTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tasbeeh_tts")
            }
        } catch (_: Exception) { }
    }

    // --- Tab 1: Authentic King Fahd Madinah Mushaf (مصحف المدينة المنورة — 604 صفحة) ---
    private fun buildQuranTab(): View {
        val ar = store.isArabic
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(24)) }

        val curPage = store.lastMushafPage.coerceIn(1, 604)
        val surah = QuranData.getSurahForPage(curPage)
        val juz = QuranData.getJuzForPage(curPage)

        // 1. Mushaf Header Bar (Surah Index + Juz Jump + Page Jump + Night Mode)
        val barCard = makeCard(if (ar) "📖 مصحف المدينة المنورة الأصلي" else "📖 Al-Mushaf Al-Madani")
        barCard.addView(TextView(this).apply {
            text = if (ar) "سورة ${surah.nameAr}  •  الجزء $juz  •  صفحة $curPage / 604"
            else "Surah ${surah.nameEn}  •  Juz $juz  •  Page $curPage / 604"
            textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(accentCol()); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        val toolsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun addTool(lbl: String, onClick: () -> Unit) {
            val b = Button(this).apply {
                text = lbl; isAllCaps = false; textSize = 11.5f; setTypeface(null, Typeface.BOLD)
                background = gradientBg(btnNeutralBg(), btnNeutralBg(), 10)
                setTextColor(Color.parseColor(btnNeutralFg()))
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
                setOnClickListener { onClick() }
            }
            toolsRow.addView(b)
        }

        addTool(if (ar) "📚 السور" else "📚 Surahs") {
            val dlgCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
            val searchIn = EditText(this).apply { hint = if (ar) "ابحث باسم السورة..." else "Search Surah..." }
            val lv = ListView(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(380)) }
            dlgCol.addView(searchIn); dlgCol.addView(lv)
            val dlg = AlertDialog.Builder(this).setTitle(if (ar) "فهرس السور (114 سورة)" else "Surah Index (114)").setView(dlgCol).setNegativeButton(s("cancel"), null).create()
            fun fill(q: String) {
                val list = if (q.isBlank()) QuranData.surahs else QuranData.surahs.filter { it.nameAr.contains(q) || it.nameEn.contains(q, true) || it.number.toString() == q }
                lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list.map {
                    if (ar) "${it.number}. سورة ${it.nameAr}  (ص ${it.startPage} • ${it.ayahs} آية)" else "${it.number}. ${it.nameEn} (p.${it.startPage})"
                })
                lv.setOnItemClickListener { _, _, pos, _ ->
                    store.lastMushafPage = list[pos].startPage
                    dlg.dismiss()
                    renderTab(1)
                }
            }
            fill("")
            searchIn.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { fill(s?.toString() ?: "") }
                override fun afterTextChanged(s: Editable?) {}
            })
            dlg.show()
        }

        addTool(if (ar) "📑 الأجزاء" else "📑 Juz") {
            val items = (1..30).map { if (ar) "الجزء $it  (صفحة ${QuranData.juzStartPages[it - 1]})" else "Juz $it (p.${QuranData.juzStartPages[it - 1]})" }.toTypedArray()
            AlertDialog.Builder(this).setTitle(if (ar) "اختر الجزء" else "Select Juz").setItems(items) { _, which ->
                store.lastMushafPage = QuranData.juzStartPages[which]
                renderTab(1)
            }.show()
        }

        addTool(if (ar) "🔢 صفحة" else "🔢 Page") {
            val pin = EditText(this).apply { setText(curPage.toString()); inputType = InputType.TYPE_CLASS_NUMBER }
            AlertDialog.Builder(this).setTitle(if (ar) "انتقال إلى صفحة (1 - 604)" else "Go to Page (1 - 604)").setView(pin)
                .setPositiveButton(s("save")) { _, _ ->
                    val p = (pin.text.toString().toIntOrNull() ?: curPage).coerceIn(1, 604)
                    store.lastMushafPage = p
                    renderTab(1)
                }.setNegativeButton(s("cancel"), null).show()
        }

        addTool(if (store.mushafNightMode) (if (ar) "☀️ نهار" else "☀️ Day") else (if (ar) "🌙 ليل" else "🌙 Night")) {
            store.mushafNightMode = !store.mushafNightMode
            renderTab(1)
        }
        barCard.addView(toolsRow)
        col.addView(barCard)

        // 1B. Full Offline Mushaf Downloader Card (0% -> 100% Resumable!)
        val cachedNow = QuranData.countCachedPages(this)
        val dlCard = makeCard()
        if (cachedNow >= 604) {
            dlCard.addView(TextView(this).apply {
                text = if (ar) "✅ المصحف الشريف محمّل بالكامل (604/604) ويعمل أوفلاين بدون إنترنت!"
                else "✅ Full Madinah Mushaf downloaded (604/604) — Ready 100% Offline!"
                textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#10B981")); gravity = Gravity.CENTER
            })
        } else {
            val initPct = (cachedNow * 100) / 604
            val dlStatusTv = TextView(this).apply {
                text = if (ar) "📥 تحميل المصحف للعمل بدون نت: $cachedNow / 604 صفحة ($initPct%)"
                else "📥 Offline Mushaf Download: $cachedNow / 604 pages ($initPct%)"
                textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(textPrimary())
            }
            val dlBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = initPct
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)).apply {
                    topMargin = dp(8); bottomMargin = dp(8)
                }
            }
            lateinit var dlBtn: Button
            dlBtn = makeBtn(
                if (mushafDownloading) (if (ar) "⏹️ إيقاف التحميل مؤقتاً" else "⏹️ Pause Download")
                else if (cachedNow > 0) (if (ar) "⬇️ استكمال تحميل المصحف كاملاً ($initPct%)" else "⬇️ Resume Full Mushaf Download ($initPct%)")
                else (if (ar) "⬇️ تحميل المصحف كاملاً مرة واحدة (604 صفحة)" else "⬇️ Download Full Mushaf Once (604 Pages)"),
                if (mushafDownloading) "#D97706" else "#059669",
                if (mushafDownloading) "#F59E0B" else "#10B981"
            ) {
                if (mushafDownloading) {
                    mushafDownloadCancelled = true
                    mushafDownloading = false
                    dlBtn.text = if (ar) "⬇️ استكمال تحميل المصحف كاملاً" else "⬇️ Resume Full Mushaf Download"
                    Toast.makeText(this, if (ar) "تم إيقاف التحميل مؤقتاً — يمكنك الاستكمال لاحقاً!" else "Download paused — you can resume anytime!", Toast.LENGTH_SHORT).show()
                } else {
                    mushafDownloading = true
                    mushafDownloadCancelled = false
                    dlBtn.text = if (ar) "⏹️ إيقاف التحميل مؤقتاً" else "⏹️ Pause Download"
                    dlBtn.background = gradientBg("#D97706", "#F59E0B", 12)
                    Thread {
                        val completed = QuranData.downloadAllPages(
                            context = this,
                            isCancelled = { mushafDownloadCancelled },
                            onProgress = { done, total ->
                                val pct = (done * 100) / total
                                uiHandler.post {
                                    dlBar.progress = pct
                                    dlStatusTv.text = if (ar) "📥 جاري التحميل: $done / $total صفحة ($pct%)" else "📥 Downloading: $done / $total pages ($pct%)"
                                }
                            }
                        )
                        mushafDownloading = false
                        uiHandler.post {
                            if (completed) {
                                Toast.makeText(this, if (ar) "ما شاء الله! اكتمل تحميل المصحف كاملاً (100%) ويعمل الآن بدون إنترنت!" else "Full Mushaf downloaded (100%) — Now works offline!", Toast.LENGTH_LONG).show()
                                if (currentTab == 1) renderTab(1)
                            } else if (!mushafDownloadCancelled) {
                                dlBtn.text = if (ar) "⬇️ استكمال تحميل المصحف كاملاً" else "⬇️ Resume Download"
                                dlBtn.background = gradientBg("#059669", "#10B981", 12)
                            }
                        }
                    }.start()
                }
            }
            dlCard.addView(dlStatusTv)
            dlCard.addView(dlBar)
            dlCard.addView(dlBtn)
        }
        col.addView(dlCard)

        // 2. Authentic Madinah Mushaf Page Viewer Card (Offline Cached!)
        val pageCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = gradientBg(
                if (store.mushafNightMode) "#101622" else "#FCF8EC",
                if (store.mushafNightMode) "#0B101A" else "#F7EED7",
                18
            ).apply { setStroke(dp(1), Color.parseColor("#B48A3E")) }
            setPadding(dp(8), dp(10), dp(8), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        val statusTv = TextView(this).apply {
            text = if (ar) "جاري تحميل صفحة $curPage من مصحف المدينة..." else "Loading Madinah Mushaf page $curPage..."
            textSize = 13f; setTextColor(if (store.mushafNightMode) Color.parseColor("#CBD5E1") else Color.parseColor("#475569"))
            gravity = Gravity.CENTER; setPadding(dp(8), dp(20), dp(8), dp(20))
        }
        val pageImg = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
            if (store.mushafNightMode) {
                val inv = ColorMatrix(floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                ))
                colorFilter = ColorMatrixColorFilter(inv)
            }
        }
        pageCard.addView(statusTv)
        pageCard.addView(pageImg)
        col.addView(pageCard)

        fun applyMushafResult(res: com.muslimtimekeeper.app.core.MushafPageResult) {
            if (res.bitmap != null) {
                pageImg.setImageBitmap(res.bitmap)
                pageImg.visibility = View.VISIBLE
                statusTv.visibility = View.GONE
            } else if (!res.fallbackText.isNullOrEmpty()) {
                statusTv.text = res.fallbackText
                statusTv.textSize = 20f
                statusTv.setLineSpacing(dp(10).toFloat(), 1.15f)
                statusTv.setTextColor(if (store.mushafNightMode) Color.parseColor("#F8FAFC") else Color.parseColor("#1E293B"))
                statusTv.gravity = Gravity.CENTER
                statusTv.setPadding(dp(12), dp(14), dp(12), dp(14))
            } else {
                statusTv.text = if (ar) "تعذّر تحميل الصفحة — تأكد من اتصال الإنترنت لأول مرة (يتم حفظ كل صفحة تلقائياً للقراءة بدون إنترنت لاحقاً)."
                else "Could not load page — check internet connection for first-time download (pages are cached offline automatically)."
            }
        }

        val targetPage = curPage
        val instant = QuranData.getCachedPageInstant(this, targetPage)
        if (instant != null) {
            applyMushafResult(instant)
        } else {
            Thread {
                val res = QuranData.loadMushafPage(this, targetPage)
                uiHandler.post {
                    if (store.lastMushafPage == targetPage) applyMushafResult(res)
                }
            }.start()
        }

        // 3. Previous / Next Page Navigation Row
        val navRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnPrev = Button(this).apply {
            text = if (ar) "◀ الصفحة السابقة" else "◀ Prev Page"
            isAllCaps = false; textSize = 13.5f; setTypeface(null, Typeface.BOLD)
            background = gradientBg(btnNeutralBg(), btnNeutralBg(), 12); setTextColor(Color.parseColor(btnNeutralFg()))
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(6) }
            setOnClickListener { if (curPage > 1) { store.lastMushafPage = curPage - 1; renderTab(1) } }
        }
        val btnNext = Button(this).apply {
            text = if (ar) "الصفحة التالية ▶" else "Next Page ▶"
            isAllCaps = false; textSize = 13.5f; setTypeface(null, Typeface.BOLD)
            background = gradientBg("#059669", "#10B981", 12); setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(6) }
            setOnClickListener { if (curPage < 604) { store.lastMushafPage = curPage + 1; renderTab(1) } }
        }
        navRow.addView(btnPrev); navRow.addView(btnNext)
        col.addView(navRow)

        scroll.addView(col)
        return scroll
    }

    // --- Tab 2: Categorized Adhkar & Electronic Tasbeeh (Daily / Morning / Evening / Sleep / Waking) ---
    private fun buildTasbeehTab(): View {
        val ar = store.isArabic
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(24)) }

        // 1. Category Selector Bar (5 Categories!)
        val catCard = makeCard(if (ar) "📿 الأذكار والتسبيح" else "📿 Adhkar & Tasbeeh")
        val catNames = if (ar) listOf("📿 يومية", "☀️ الصباح (28)", "🌇 المساء (28)", "🌙 النوم (12)", "🌅 الاستيقاظ (5)")
        else listOf("📿 Daily", "☀️ Morning (28)", "🌇 Evening (28)", "🌙 Sleep (12)", "🌅 Waking (5)")

        fun makeCatBtn(idx: Int): Button {
            val sel = (adhkarCategory == idx)
            return Button(this).apply {
                text = catNames[idx]; isAllCaps = false; textSize = 11.5f; setTypeface(null, Typeface.BOLD)
                background = if (sel) gradientBg("#059669", "#10B981", 10) else gradientBg(btnNeutralBg(), btnNeutralBg(), 10)
                setTextColor(if (sel) Color.WHITE else Color.parseColor(btnNeutralFg()))
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
                setOnClickListener {
                    adhkarCategory = idx
                    adhkarCatIdx = 0
                    tasbeehSessionCount = 0
                    renderTab(2)
                }
            }
        }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(6)) }
        row1.addView(makeCatBtn(0)); row1.addView(makeCatBtn(1)); row1.addView(makeCatBtn(2))
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(4)) }
        row2.addView(makeCatBtn(3)); row2.addView(makeCatBtn(4))
        catCard.addView(row1)
        catCard.addView(row2)

        val list = when (adhkarCategory) {
            1 -> AdhkarData.morningAdhkar
            2 -> AdhkarData.eveningAdhkar
            3 -> AdhkarData.sleepAdhkar
            4 -> AdhkarData.wakingAdhkar
            else -> AdhkarData.tasbeehLibrary
        }
        val selIdx = if (adhkarCategory == 0) store.selectedTasbeehIndex.coerceIn(0, list.lastIndex) else adhkarCatIdx.coerceIn(0, list.lastIndex)
        val item = list[selIdx]

        catCard.addView(makeBtn(
            if (ar) "📖 اختيار الذكر (${selIdx + 1} من ${list.size}): ${item.titleAr} ▼"
            else "📖 Select Dhikr (${selIdx + 1}/${list.size}): ${item.titleEn} ▼",
            "#0284C7", "#0369A1"
        ) {
            val titles = list.map { if (ar) "${it.titleAr}  (${it.target})" else "${it.titleEn}  (${it.target})" }.toTypedArray()
            AlertDialog.Builder(this)
                .setTitle(catNames[adhkarCategory])
                .setItems(titles) { _, which ->
                    if (adhkarCategory == 0) store.selectedTasbeehIndex = which else adhkarCatIdx = which
                    tasbeehSessionCount = 0
                    renderTab(2)
                }
                .setNegativeButton(s("cancel"), null)
                .show()
        })

        // Bead color theme pills
        val themeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(2)) }
        val themeLabels = if (ar) listOf("⚪ لؤلؤي", "🔵 سماوي", "🟢 زمردي", "🟡 ذهبي") else listOf("⚪ Pearl", "🔵 Blue", "🟢 Green", "🟡 Gold")
        themeLabels.forEachIndexed { tIdx, lbl ->
            val sel = (store.selectedBeadTheme == tIdx)
            val pill = Button(this).apply {
                text = lbl; isAllCaps = false; textSize = 11.5f
                background = if (sel) gradientBg("#0284C7", "#0369A1", 10) else gradientBg(btnNeutralBg(), btnNeutralBg(), 10)
                setTextColor(if (sel) Color.WHITE else Color.parseColor(btnNeutralFg()))
                layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
                setOnClickListener { store.selectedBeadTheme = tIdx; renderTab(2) }
            }
            themeRow.addView(pill)
        }
        catCard.addView(themeRow)
        col.addView(catCard)

        // 2. Dhikr Text + Prophetic Virtue / Evidence (الدليل والفضل)
        val dhikrCard = makeCard()
        dhikrCard.addView(TextView(this).apply {
            text = if (ar) item.textAr else item.textEn
            textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(textPrimary()); gravity = Gravity.CENTER
            setPadding(dp(4), dp(4), dp(4), dp(10))
        })
        val fadlTv = TextView(this).apply {
            text = if (ar) item.fadlAr else item.fadlEn
            textSize = 13f; setTextColor(accentCol()); gravity = Gravity.CENTER
            visibility = if (showFadlInTasbeeh) View.VISIBLE else View.GONE
            setPadding(dp(6), dp(8), dp(6), dp(4))
        }
        val fadlToggle = TextView(this).apply {
            text = if (ar) (if (showFadlInTasbeeh) "فضل الذكر والدليل ▲" else "فضل الذكر والدليل ▼") else (if (showFadlInTasbeeh) "Virtue & Evidence ▲" else "Virtue & Evidence ▼")
            textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(accentCol()); gravity = Gravity.CENTER
            background = gradientBg(btnNeutralBg(), btnNeutralBg(), 12); setPadding(dp(14), dp(5), dp(14), dp(5))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL }
            setOnClickListener {
                showFadlInTasbeeh = !showFadlInTasbeeh
                fadlTv.visibility = if (showFadlInTasbeeh) View.VISIBLE else View.GONE
                text = if (ar) (if (showFadlInTasbeeh) "فضل الذكر والدليل ▲" else "فضل الذكر والدليل ▼") else (if (showFadlInTasbeeh) "Virtue & Evidence ▲" else "Virtue & Evidence ▼")
            }
        }
        dhikrCard.addView(fadlToggle); dhikrCard.addView(fadlTv)
        col.addView(dhikrCard)

        // 3. Diagonal Curved 3D Pearl Bead Tasbeeh + Auto-Advance for Fixed Adhkar!
        val beadCard = makeCard()
        val countTv = TextView(this).apply {
            text = "$tasbeehSessionCount / ${item.target}"
            textSize = 36f; setTypeface(null, Typeface.BOLD); setTextColor(accentCol()); gravity = Gravity.CENTER
        }
        val totalTv = TextView(this).apply {
            text = if (ar) "إجمالي تسبيحاتك: ${store.totalTasbeehCount}  •  اضغط على السبحة لتنزيل حبة" else "Total: ${store.totalTasbeehCount}  •  Tap beads to slide one"
            textSize = 11.5f; setTextColor(textMuted()); gravity = Gravity.CENTER; setPadding(0, dp(2), 0, dp(6))
        }

        val beadView = BeadTasbeehView(this).apply {
            beadTheme = store.selectedBeadTheme
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170))
            onBeadTapped = {
                tasbeehSessionCount++
                store.totalTasbeehCount = store.totalTasbeehCount + 1
                countTv.text = "$tasbeehSessionCount / ${item.target}"
                totalTv.text = if (ar) "إجمالي تسبيحاتك: ${store.totalTasbeehCount}" else "Total: ${store.totalTasbeehCount}"
                if (tasbeehSessionCount >= item.target) {
                    vibrateDhikr(true)
                    if (adhkarCategory > 0 && selIdx < list.lastIndex) {
                        adhkarCatIdx = selIdx + 1
                        tasbeehSessionCount = 0
                        renderTab(2)
                    } else {
                        if (adhkarCategory == 0) tasbeehSessionCount = 0
                        Toast.makeText(
                            this@MainActivity,
                            if (ar) "ما شاء الله! أتممت الذكر بنجاح" else "MashaAllah! Completed!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    vibrateDhikr(false)
                }
            }
        }

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(2)) }
        val btnReset = Button(this).apply {
            text = if (ar) "↻ إعادة" else "↻ Reset"
            isAllCaps = false; textSize = 13f
            background = gradientBg(btnNeutralBg(), btnNeutralBg(), 12); setTextColor(Color.parseColor(btnNeutralFg()))
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) }
            setOnClickListener { tasbeehSessionCount = 0; countTv.text = "0 / ${item.target}" }
        }
        val btnListen = Button(this).apply {
            text = if (ar) "🔊 استماع" else "🔊 Listen"
            isAllCaps = false; textSize = 13f
            background = gradientBg("#0284C7", "#0369A1", 12); setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) }
            setOnClickListener { speakTasbeeh(if (ar) item.textAr else item.textEn) }
        }
        val btnNextDhikr = Button(this).apply {
            text = if (ar) "التالي ◀" else "Next ▶"
            isAllCaps = false; textSize = 13f
            background = gradientBg("#059669", "#10B981", 12); setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            setOnClickListener {
                val nxt = (selIdx + 1) % list.size
                if (adhkarCategory == 0) store.selectedTasbeehIndex = nxt else adhkarCatIdx = nxt
                tasbeehSessionCount = 0
                renderTab(2)
            }
        }
        actionRow.addView(btnReset); actionRow.addView(btnListen); actionRow.addView(btnNextDhikr)
        beadCard.addView(beadView); beadCard.addView(countTv); beadCard.addView(totalTv); beadCard.addView(actionRow)
        col.addView(beadCard)

        scroll.addView(col)
        return scroll
    }

    // --- Tab 3: Daily Schedule Tab (جدول الدروس والمواعيد + تذكير قبلها بـ 5 دقائق) ---
    private fun buildScheduleTab(): View {
        val ar = store.isArabic
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }

        val hdrCard = makeCard(if (ar) "📅 الجدول اليومي (الدروس والمواعيد)" else "📅 Daily Schedule (Lessons & Tasks)")
        hdrCard.addView(TextView(this).apply {
            text = if (ar) "أضف جدول دروسك ومواعيدك اليومية، وسيقوم الهاتف بإرسال إشعار تذكير تلقائي قبل كل موعد بـ 5 دقائق."
            else "Add your daily lessons and appointments; your phone will automatically notify you 5 minutes before each one."
            textSize = 12.5f; setTextColor(textMuted())
        })
        hdrCard.addView(makeBtn(if (ar) "➕ إضافة درس أو موعد جديد" else "➕ Add Lesson / Appointment", "#059669", "#10B981") {
            var pickedH = 16
            var pickedM = 0
            val dlgBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12)) }
            val titleIn = EditText(this).apply { hint = if (ar) "اسم الدرس أو الموعد (مثلاً: درس الرياضيات)" else "Lesson or task title" }
            val timeBtn = Button(this).apply {
                text = if (ar) "⏰ اختر الوقت: 04:00 م" else "⏰ Pick Time: 16:00"
                isAllCaps = false
                setOnClickListener {
                    TimePickerDialog(this@MainActivity, { _, h, m ->
                        pickedH = h; pickedM = m
                        text = String.format(Locale.US, "⏰ %02d:%02d", h, m)
                    }, pickedH, pickedM, false).show()
                }
            }
            dlgBox.addView(titleIn); dlgBox.addView(timeBtn)
            AlertDialog.Builder(this)
                .setTitle(if (ar) "موعد جديد في الجدول اليومي" else "New Schedule Item")
                .setView(dlgBox)
                .setPositiveButton(s("save")) { _, _ ->
                    val t = titleIn.text.toString().trim()
                    if (t.isNotEmpty()) {
                        val items = store.getScheduleItems()
                        items.add(ScheduleItem(System.currentTimeMillis().toString(), t, pickedH, pickedM, true, ""))
                        store.saveScheduleItems(items)
                        renderTab(3)
                    }
                }
                .setNegativeButton(s("cancel"), null)
                .show()
        })
        col.addView(hdrCard)

        val today = store.todayStr()
        val items = store.getScheduleItems()
        if (items.isEmpty()) {
            val emptyCard = makeCard()
            emptyCard.addView(TextView(this).apply {
                text = if (ar) "جدولك اليومي فارغ حالياً — اضغط الزر أعلاه لإضافة أول درس أو موعد!" else "Your daily schedule is empty — tap above to add your first lesson or task!"
                textSize = 13.5f; setTextColor(textMuted()); gravity = Gravity.CENTER; setPadding(dp(8), dp(18), dp(8), dp(18))
            })
            col.addView(emptyCard)
        } else {
            for (item in items) {
                val isDone = (item.doneDate == today)
                val itemCard = makeCard()
                val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val timeBadge = TextView(this).apply {
                    text = String.format(Locale.US, "⏰ %02d:%02d", item.hour, item.minute)
                    textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
                    background = gradientBg(if (isDone) "#059669" else "#0284C7", if (isDone) "#10B981" else "#0EA5E9", 10)
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) }
                }
                val titleTv = TextView(this).apply {
                    text = if (isDone) "✅ ${item.title}" else item.title
                    textSize = 15.5f; setTypeface(null, Typeface.BOLD); setTextColor(if (isDone) Color.parseColor("#10B981") else textPrimary())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                top.addView(timeBadge); top.addView(titleTv)
                itemCard.addView(top)

                val rowBtns = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
                }
                val btnDone = Button(this).apply {
                    text = if (isDone) (if (ar) "تراجع" else "Undo") else (if (ar) "✓ تم الإنجاز" else "✓ Mark Done")
                    isAllCaps = false; textSize = 12.5f
                    background = gradientBg(btnNeutralBg(), btnNeutralBg(), 10); setTextColor(Color.parseColor(btnNeutralFg()))
                    layoutParams = LinearLayout.LayoutParams(0, dp(38), 1.2f).apply { marginEnd = dp(8) }
                    setOnClickListener {
                        val updated = store.getScheduleItems().map { if (it.id == item.id) it.copy(doneDate = if (isDone) "" else today) else it }
                        store.saveScheduleItems(updated); renderTab(3)
                    }
                }
                val btnDel = Button(this).apply {
                    text = s("remove"); isAllCaps = false; textSize = 12.5f
                    background = gradientBg("#DC2626", "#EF4444", 10); setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
                    setOnClickListener {
                        val updated = store.getScheduleItems().filter { it.id != item.id }
                        store.saveScheduleItems(updated); renderTab(3)
                    }
                }
                rowBtns.addView(btnDone); rowBtns.addView(btnDel)
                itemCard.addView(rowBtns)
                col.addView(itemCard)
            }
        }
        scroll.addView(col)
        return scroll
    }

    // --- Tab 5: Management Hub (Apps + Internet Data Quota + Safety & OTA) ---
    private fun buildManageTab(): View {
        val ar = store.isArabic
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val subBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(4))
        }
        val subLabels = if (ar) listOf("🎮 التطبيقات", "📶 الإنترنت", "🛡️ الحماية والتحديث") else listOf("🎮 Apps", "📶 Data", "🛡️ Safety & OTA")
        subLabels.forEachIndexed { idx, lbl ->
            val sel = (manageSubTab == idx)
            val b = Button(this).apply {
                text = lbl; isAllCaps = false; textSize = 12f; setTypeface(null, Typeface.BOLD)
                background = if (sel) gradientBg("#0284C7", "#0EA5E9", 12) else gradientBg(btnNeutralBg(), btnNeutralBg(), 12)
                setTextColor(if (sel) Color.WHITE else Color.parseColor(btnNeutralFg()))
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
                setOnClickListener { manageSubTab = idx; renderTab(5) }
            }
            subBar.addView(b)
        }
        wrapper.addView(subBar)

        val content = when (manageSubTab) {
            0 -> buildAppsTab()
            1 -> buildDataTab()
            else -> buildSafetyTab()
        }
        content.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        wrapper.addView(content)
        return wrapper
    }

    // --- Tab 4: Device Internet Data Quota (GB Ring, Consumed, Remaining, %, Auto Cut-off) ---
    private fun formatDataMb(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) String.format(Locale.US, "%.2f GB", mb / 1024.0)
        else String.format(Locale.US, "%.0f MB", mb)
    }

    private fun buildDataTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }

        val limitBytes = maxOf(1L, store.dataLimitMb.toLong() * 1024L * 1024L)
        val usedBytes = store.dataUsedBytes
        val remBytes = maxOf(0L, limitBytes - usedBytes)
        val ratio = (usedBytes.toDouble() / limitBytes.toDouble()).toFloat().coerceIn(0f, 1f)
        val pct = (ratio * 100f).toInt()

        // 1. Main Circular Quota Gauge Card
        val gaugeCard = makeCard(if (store.isArabic) "📶 استهلاك الإنترنت لهذا الجهاز" else "📶 Device Internet Data Quota", "#10B981")

        val ringFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(210), dp(210)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(6); bottomMargin = dp(14)
            }
        }
        val ring = DataQuotaRingView(this).apply { setQuota(ratio, store.isDarkMode) }
        val centerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        centerBox.addView(TextView(this).apply {
            text = "$pct%"
            textSize = 32f; setTypeface(null, Typeface.BOLD)
            setTextColor(if (pct >= 95) Color.parseColor("#EF4444") else if (pct >= 75) Color.parseColor("#F59E0B") else Color.parseColor("#10B981"))
        })
        centerBox.addView(TextView(this).apply {
            text = if (store.isArabic) "نسبة الاستهلاك" else "USED"
            textSize = 11.5f; setTextColor(textMuted())
        })
        ringFrame.addView(ring); ringFrame.addView(centerBox)
        gaugeCard.addView(ringFrame)

        // 3 Stat Pills: Consumed | Remaining | Total Quota
        val statsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun addStat(lbl: String, valStr: String, colHex: String) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = gradientBg(btnNeutralBg(), btnNeutralBg(), 14)
                setPadding(dp(8), dp(10), dp(8), dp(10))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4); marginEnd = dp(4) }
            }
            box.addView(TextView(this).apply { text = valStr; textSize = 14.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor(colHex)); gravity = Gravity.CENTER })
            box.addView(TextView(this).apply { text = lbl; textSize = 10.5f; setTextColor(textMuted()); gravity = Gravity.CENTER })
            statsRow.addView(box)
        }
        addStat(if (store.isArabic) "المستهلك" else "Used", formatDataMb(usedBytes), "#F59E0B")
        addStat(if (store.isArabic) "المتبقي" else "Remaining", formatDataMb(remBytes), "#10B981")
        addStat(if (store.isArabic) "الباقة" else "Limit", formatDataMb(limitBytes), btnNeutralFg())
        gaugeCard.addView(statsRow)
        col.addView(gaugeCard)

        // 2. Enable Data Cut-off + Adjust GB Quota Card
        val ctrlCard = makeCard(if (store.isArabic) "⚙️ التحكم في الباقة وقطع الإنترنت" else "⚙️ Quota Limit & Auto Cut-Off")
        val swQuota = Switch(this).apply {
            text = if (store.isArabic) "قطع الإنترنت تلقائياً عند انتهاء الجيجات (يتطلب موافقة الوالدين)" else "Block internet automatically when quota ends (Parent PIN)"
            isChecked = store.dataLimitEnabled
            setTextColor(textPrimary())
            setOnCheckedChangeListener { _, checked ->
                requireParentPin {
                    store.dataLimitEnabled = checked
                    if (checked) {
                        val prep = VpnService.prepare(this@MainActivity)
                        if (prep != null) vpnLauncher.launch(prep)
                    } else if (store.isInternetBlockedByQuota) {
                        store.isInternetBlockedByQuota = false
                        startService(Intent(this@MainActivity, SafeDnsVpnService::class.java).apply { action = if (store.webSafetyEnabled) "SAFE_DNS" else "STOP_VPN" })
                    }
                    run { manageSubTab = 1; renderTab(5) }
                }
            }
        }
        ctrlCard.addView(swQuota)

        ctrlCard.addView(TextView(this).apply {
            text = if (store.isArabic) "\nتعديل حد الباقة المسموح لهذا الجهاز:" else "\nAdjust allowed data quota:"
            setTextColor(textMuted()); textSize = 12.5f
        })
        val adjRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(6)) }
        for (pair in listOf(Pair("-500MB", -512), Pair("+500MB", 512), Pair("+1GB", 1024), Pair("+5GB", 5120))) {
            val b = Button(this).apply {
                text = pair.first
                background = gradientBg(btnNeutralBg(), btnNeutralBg(), 10)
                setTextColor(Color.parseColor(btnNeutralFg())); isAllCaps = false; textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
                setOnClickListener {
                    requireParentPin {
                        store.dataLimitMb = (store.dataLimitMb + pair.second).coerceIn(100, 512000)
                        if (store.dataUsedBytes < store.dataLimitMb.toLong() * 1024L * 1024L && store.isInternetBlockedByQuota) {
                            store.isInternetBlockedByQuota = false
                            startService(Intent(this@MainActivity, SafeDnsVpnService::class.java).apply { action = if (store.webSafetyEnabled) "SAFE_DNS" else "STOP_VPN" })
                        }
                        run { manageSubTab = 1; renderTab(5) }
                    }
                }
            }
            adjRow.addView(b)
        }
        ctrlCard.addView(adjRow)

        ctrlCard.addView(makeBtn(if (store.isArabic) "🔄 تصفير عداد الاستهلاك وفتح الإنترنت" else "🔄 Reset Data Counter & Restore Internet", "#059669", "#10B981") {
            requireParentPin {
                store.dataUsedBytes = 0L
                store.isInternetBlockedByQuota = false
                startService(Intent(this, SafeDnsVpnService::class.java).apply { action = if (store.webSafetyEnabled) "SAFE_DNS" else "STOP_VPN" })
                Toast.makeText(this, if (store.isArabic) "تم تصفير استهلاك الإنترنت!" else "Data usage reset!", Toast.LENGTH_SHORT).show()
                run { manageSubTab = 1; renderTab(5) }
            }
        })
        col.addView(ctrlCard)

        scroll.addView(col)
        return scroll
    }

    // --- Tab 3: Safety, Permissions & In-App OTA Update ---
    private fun buildSafetyTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }

        val webCard = makeCard(s("web_title"), "#10B981")
        webCard.addView(TextView(this).apply { text = s("web_desc"); setTextColor(textMuted()); textSize = 13.5f })
        webCard.addView(makeBtn(s("enable_dns"), "#059669", "#10B981") {
            requireParentPin {
                val prep = VpnService.prepare(this)
                if (prep != null) vpnLauncher.launch(prep)
                else { startService(Intent(this, SafeDnsVpnService::class.java)); store.webSafetyEnabled = true; renderTab(5) }
            }
        })
        webCard.addView(makeBtn(s("disable_dns"), btnNeutralBg(), btnNeutralBg(), btnNeutralFg()) {
            requireParentPin { startService(Intent(this, SafeDnsVpnService::class.java).apply { action = "STOP_VPN" }); store.webSafetyEnabled = false; renderTab(5) }
        })
        col.addView(webCard)

        val permCard = makeCard(s("perm_title"), "#F59E0B")
        permCard.addView(makeBtn(s("perm_usage"), btnNeutralBg(), btnNeutralBg(), btnNeutralFg()) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        permCard.addView(makeBtn(s("perm_overlay"), btnNeutralBg(), btnNeutralBg(), btnNeutralFg()) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })
        permCard.addView(makeBtn(s("perm_admin"), "#059669", "#10B981") {
            val comp = ComponentName(this, DeviceAdminProtectionReceiver::class.java)
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply { putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp) })
        })
        permCard.addView(makeBtn(if (store.isArabic) "4️⃣ صلاحية عدم الإزعاج (للوضع الصامت في المسجد)" else "4️⃣ Grant Do Not Disturb Access (Mosque Auto-Silent)", btnNeutralBg(), btnNeutralBg(), btnNeutralFg()) {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) } catch (_: Exception) { }
        })
        col.addView(permCard)

        val pinCard = makeCard(s("pin_title"))
        val newPinIn = EditText(this).apply { hint = s("new_pin_hint"); setTextColor(textPrimary()); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        pinCard.addView(newPinIn)
        pinCard.addView(makeBtn(s("update_pin"), "#D97706", "#F59E0B") {
            requireParentPin { val np = newPinIn.text.toString().trim(); if (np.length >= 4) { store.updatePin(np); newPinIn.text.clear(); Toast.makeText(this, "OK!", Toast.LENGTH_SHORT).show() } }
        })
        pinCard.addView(makeBtn(s("allow_uninstall"), "#DC2626", "#EF4444") {
            requireParentPin {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val comp = ComponentName(this, DeviceAdminProtectionReceiver::class.java)
                if (dpm.isAdminActive(comp)) dpm.removeActiveAdmin(comp)
            }
        })
        col.addView(pinCard)

        // OTA Update Card
        val updCard = makeCard(if (store.isArabic) "🔄 تحديث التطبيق تلقائياً (OTA)" else "🔄 In-App Auto-Update (OTA)", "#0284C7")
        val repoIn = EditText(this).apply { hint = "Anas-crack/Muslim-keep-time"; setTextColor(textPrimary()); setText(store.updateRepo) }
        updCard.addView(repoIn)
        updCard.addView(TextView(this).apply { text = (if (store.isArabic) "الإصدار الحالي: v" else "Current Version: v") + UpdateManager.getCurrentVersionName(this@MainActivity); setTextColor(textMuted()); textSize = 12.5f })
        updCard.addView(makeBtn(if (store.isArabic) "🚀 التحقق من التحديثات وتنزيلها" else "🚀 Check & Install Update", "#0284C7", "#0369A1") {
            val repo = repoIn.text.toString().trim(); store.updateRepo = repo
            if (!repo.contains("/")) return@makeBtn
            AlertDialog.Builder(this)
                .setTitle(if (store.isArabic) "📋 إرشادات مهمة قبل تحديث التطبيق" else "📋 Important Pre-Update Instructions")
                .setMessage(if (store.isArabic) "يرجى قبل تحديث التطبيق الدخول إلى مدير الملفات ← قسم ملفات APK (أو التنزيلات) وحذف أي ملف APK متبقٍ خاص بالتطبيق من إصدارات سابقة أو حديثة، حتى يتم التثبيت بنجاح بدون خطأ تحليل الحزمة.\n\nهل قمت بحذف الملفات القديمة وتريد المتابعة الآن؟" else "Before updating, please open your File Manager -> APK files (or Downloads) and delete any old or previous APK files for this app so the update installs smoothly without package parsing errors.\n\nProceed with checking for updates now?")
                .setPositiveButton(if (store.isArabic) "نعم، متابعة التحديث" else "Yes, Continue") { _, _ ->
                    Toast.makeText(this, if (store.isArabic) "جاري البحث..." else "Checking...", Toast.LENGTH_SHORT).show()
                    Thread {
                        val res = UpdateManager.checkLatestReleaseDetailed(this, repo)
                        uiHandler.post {
                            val rel = res.release
                            if (rel == null) {
                                val msg = if (res.error != null) {
                                    if (store.isArabic) "تعذر التحقق من التحديث: ${res.error}" else "Update check failed: ${res.error}"
                                } else {
                                    if (store.isArabic) "أنت على أحدث إصدار!" else "You are up to date!"
                                }
                                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                            } else AlertDialog.Builder(this)
                                .setTitle(if (store.isArabic) "تحديث جديد (${rel.versionTag})" else "New Update (${rel.versionTag})")
                                .setMessage(if (store.isArabic) "هل تريد تحميل وتثبيت التحديث الآن؟" else "Download and install now?")
                                .setPositiveButton(if (store.isArabic) "تحديث الآن" else "Update Now") { _, _ ->
                                    launchApkUpdate(rel.apkDownloadUrl)
                                }.setNegativeButton(s("cancel"), null).show()
                        }
                    }.start()
                }
                .setNegativeButton(s("cancel"), null)
                .show()
        })
        col.addView(updCard)

        scroll.addView(col)
        return scroll
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(liveClockRunnable); testPlayer?.release(); try { tasbeehTts?.stop(); tasbeehTts?.shutdown() } catch (_: Exception) { }; super.onDestroy()
    }
}
