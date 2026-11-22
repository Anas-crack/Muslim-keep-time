package com.muslimtimekeeper.app.ui

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.muslimtimekeeper.app.R
import com.muslimtimekeeper.app.core.AppRule
import com.muslimtimekeeper.app.core.PrayerCalculator
import com.muslimtimekeeper.app.core.PrefsStore
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

    // Dynamic Dark / Light Palette matching user's reference screenshots
    private fun bgCanvas(): Int = Color.parseColor(if (store.isDarkMode) "#081110" else "#F4F7FA")
    private fun bgBar(): Int = Color.parseColor(if (store.isDarkMode) "#0C1816" else "#FFFFFF")
    private fun bgCard(): Int = Color.parseColor(if (store.isDarkMode) "#111E1C" else "#FFFFFF")
    private fun borderCol(): Int = Color.parseColor(if (store.isDarkMode) "#1F3430" else "#E2E8F0")
    private fun textPrimary(): Int = Color.parseColor(if (store.isDarkMode) "#F8FAFC" else "#0F172A")
    private fun textMuted(): Int = Color.parseColor(if (store.isDarkMode) "#94A3B8" else "#64748B")
    private fun btnNeutralBg(): String = if (store.isDarkMode) "#1B2E2B" else "#E2E8F0"
    private fun btnNeutralFg(): String = if (store.isDarkMode) "#F8FAFC" else "#0F172A"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PrefsStore(this)
        ContextCompat.startForegroundService(this, Intent(this, GuardianService::class.java))
        buildShell()
        renderTab(0)
        uiHandler.post(liveClockRunnable)
        if (store.userName.isBlank()) promptUserName()
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
            textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#059669"))
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
        val labels = listOf(s("tab_home"), s("tab_apps"), s("tab_prayer"), s("tab_data"), s("tab_safety"))
        navButtons = labels.mapIndexed { idx, txt ->
            TextView(this).apply {
                text = txt; gravity = Gravity.CENTER; textSize = 11.5f; setTypeface(null, Typeface.BOLD); setPadding(dp(4), dp(8), dp(4), dp(8))
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
                tv.background = gradientBg(if (store.isDarkMode) "#133A31" else "#D1FAE5", if (store.isDarkMode) "#133A31" else "#D1FAE5", 12)
                tv.setTextColor(Color.parseColor("#059669"))
            } else {
                tv.background = null; tv.setTextColor(textMuted())
            }
        }
        val pageView = when (index) {
            0 -> buildHomeTab()
            1 -> buildAppsTab()
            2 -> buildPrayerTab()
            3 -> buildDataTab()
            else -> buildSafetyTab()
        }
        contentContainer.removeAllViews()
        pageView.alpha = 0f; pageView.translationY = 20f
        contentContainer.addView(pageView)
        pageView.animate().alpha(1f).translationY(0f).setDuration(220L).start()
    }

    private fun updateLiveTimerUi() {
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
                text = title; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#10B981")); setPadding(0, 0, 0, dp(10))
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
            background = gradientBg("#047857", "#0D9488", 22)
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
            text = if (ar) "
🏆 أكثر 5 تطبيقات استخداماً هذا الأسبوع:" else "
🏆 Top 5 Most Used Apps This Week:"
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
                list.add(AppRule(pkg, appName, lim)); store.saveAppRules(list); renderTab(1)
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
            itemCard.addView(makeBtn(s("remove"), "#DC2626", "#EF4444") {
                requireParentPin { val list = store.getAppRules(); list.removeAll { it.packageName.equals(rule.packageName, true) }; store.saveAppRules(list); renderTab(1) }
            })
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
        val swLock = Switch(this).apply { text = s("prayer_lock_sw"); isChecked = store.prayerLockEnabled; setTextColor(textPrimary()); setOnCheckedChangeListener { _, c -> requireParentPin { store.prayerLockEnabled = c } } }
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
                setOnClickListener { requireParentPin { store.prayerDurationMinutes = mins; renderTab(2) } }
            }
            durRow.addView(b)
        }
        ctrlCard.addView(durRow)

        ctrlCard.addView(makeBtn(s("test_adhan"), "#D97706", "#F59E0B") {
            try { testPlayer?.release(); testPlayer = MediaPlayer.create(this, R.raw.adhan); testPlayer?.start() } catch (_: Exception) { }
        })
        ctrlCard.addView(makeBtn(s("stop"), btnNeutralBg(), btnNeutralBg(), btnNeutralFg()) {
            testPlayer?.stop(); testPlayer?.release(); testPlayer = null
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
        val times = PrayerCalculator.calculateToday(store.latitude, store.longitude, store.timezoneOffset)
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

    // --- Tab 3: Device Internet Data Quota (GB Ring, Consumed, Remaining, %, Auto Cut-off) ---
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
                    renderTab(3)
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
                        renderTab(3)
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
                renderTab(3)
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
                else { startService(Intent(this, SafeDnsVpnService::class.java)); store.webSafetyEnabled = true; renderTab(4) }
            }
        })
        webCard.addView(makeBtn(s("disable_dns"), btnNeutralBg(), btnNeutralBg(), btnNeutralFg()) {
            requireParentPin { startService(Intent(this, SafeDnsVpnService::class.java).apply { action = "STOP_VPN" }); store.webSafetyEnabled = false; renderTab(4) }
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
            Toast.makeText(this, if (store.isArabic) "جاري البحث..." else "Checking...", Toast.LENGTH_SHORT).show()
            Thread {
                val rel = UpdateManager.checkLatestRelease(this, repo)
                uiHandler.post {
                    if (rel == null) Toast.makeText(this, if (store.isArabic) "أنت على أحدث إصدار!" else "You are up to date!", Toast.LENGTH_SHORT).show()
                    else AlertDialog.Builder(this)
                        .setTitle(if (store.isArabic) "تحديث جديد (${rel.versionTag})" else "New Update (${rel.versionTag})")
                        .setMessage(if (store.isArabic) "هل تريد تحميل وتثبيت التحديث الآن؟" else "Download and install now?")
                        .setPositiveButton(if (store.isArabic) "تحديث الآن" else "Update Now") { _, _ ->
                            Thread { UpdateManager.downloadAndInstallApk(this, rel.apkDownloadUrl) }.start()
                        }.setNegativeButton(s("cancel"), null).show()
                }
            }.start()
        })
        col.addView(updCard)

        scroll.addView(col)
        return scroll
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(liveClockRunnable); testPlayer?.release(); super.onDestroy()
    }
}
