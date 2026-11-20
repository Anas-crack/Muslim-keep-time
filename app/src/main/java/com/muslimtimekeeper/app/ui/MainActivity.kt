package com.muslimtimekeeper.app.ui

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var store: PrefsStore
    private var parentUnlocked = false
    private var currentTab = 0
    private var testPlayer: MediaPlayer? = null
    private lateinit var contentContainer: FrameLayout
    private lateinit var navButtons: List<TextView>
    private lateinit var lockStatusChip: TextView

    // Live timer references on Home tab
    private var liveTimerTv: TextView? = null
    private var liveRingView: GlowingMandalaRingView? = null
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PrefsStore(this)
        ContextCompat.startForegroundService(this, Intent(this, GuardianService::class.java))
        buildShell()
        renderTab(0)
        uiHandler.post(liveClockRunnable)
    }

    private fun s(key: String): String = Strings.get(store.isArabic, key)
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun cardBg(borderHex: String = "#26354D"): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.parseColor("#16233A"), Color.parseColor("#0F172A"))
    ).apply { cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.parseColor(borderHex)) }

    private fun pillBg(c1: String, c2: String, radius: Int = 14): GradientDrawable = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
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
        lockStatusChip.background = if (parentUnlocked) pillBg("#10B981", "#059669", 12) else pillBg("#1E293B", "#1E293B", 12)
        lockStatusChip.setTextColor(if (parentUnlocked) Color.parseColor("#06281E") else Color.WHITE)
    }

    private fun buildShell() {
        window.decorView.layoutDirection = if (store.isArabic) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#070B14")) }

        // Top Bar with Language Toggle (AR / EN) + Lock Chip
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0B1120")); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) }
        }
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBox.addView(TextView(this).apply { text = s("app_name"); textSize = 16.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
        titleBox.addView(TextView(this).apply { text = s("app_sub"); textSize = 11.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#F59E0B")) })

        val langBtn = TextView(this).apply {
            text = if (store.isArabic) "EN" else "عربي"
            textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#F59E0B"))
            background = pillBg("#1E293B", "#1E293B", 10); setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) }
            setOnClickListener {
                store.isArabic = !store.isArabic
                buildShell()
                renderTab(currentTab)
            }
        }

        lockStatusChip = TextView(this).apply {
            textSize = 12f; setTypeface(null, Typeface.BOLD); setPadding(dp(11), dp(6), dp(11), dp(6))
            setOnClickListener {
                if (parentUnlocked) { parentUnlocked = false; updateLockChip() } else requireParentPin { }
            }
        }
        updateLockChip()
        topBar.addView(logo); topBar.addView(titleBox); topBar.addView(langBtn); topBar.addView(lockStatusChip)

        contentContainer = FrameLayout(this).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f) }

        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.parseColor("#0F172A")); setPadding(dp(8), dp(8), dp(8), dp(10))
        }
        val labels = listOf(s("tab_home"), s("tab_apps"), s("tab_prayer"), s("tab_safety"))
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
        liveTimerTv = null; liveRingView = null
        navButtons.forEachIndexed { i, tv ->
            if (i == index) { tv.background = pillBg("#133A31", "#133A31", 12); tv.setTextColor(Color.parseColor("#34D399")) }
            else { tv.background = null; tv.setTextColor(Color.parseColor("#94A3B8")) }
        }
        val pageView = when (index) {
            0 -> buildHomeTab()
            1 -> buildAppsTab()
            2 -> buildPrayerTab()
            else -> buildSafetyTab()
        }
        contentContainer.removeAllViews()
        pageView.alpha = 0f; pageView.translationY = 24f
        contentContainer.addView(pageView)
        pageView.animate().alpha(1f).translationY(0f).setDuration(240L).start()
    }

    private fun updateLiveTimerUi() {
        val totalSec = maxOf(1, store.dailyPhoneLimitMinutes * 60)
        val remSec = maxOf(0, totalSec - store.phoneSecondsUsedToday)
        val h = remSec / 3600; val m = (remSec % 3600) / 60; val sec = remSec % 60
        liveTimerTv?.text = String.format("%02d:%02d:%02d", h, m, sec)
        liveRingView?.setProgressRatio(remSec.toFloat() / totalSec.toFloat())
    }

    private fun makeCard(title: String? = null, borderHex: String = "#26354D"): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = cardBg(borderHex); setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) }
            if (title != null) addView(TextView(this@MainActivity).apply {
                text = title; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#F59E0B")); setPadding(0, 0, 0, dp(10))
            })
        }
    }

    private fun makeBtn(label: String, c1: String, c2: String, fgHex: String = "#FFFFFF", onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label; background = pillBg(c1, c2, 12); setTextColor(Color.parseColor(fgHex)); isAllCaps = false; textSize = 13.5f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(10) }
            setOnClickListener { onClick() }
        }
    }

    // --- Tab 0: Live Second-by-Second Phone Timer + Daily Limit Control ---
    private fun buildHomeTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(24)) }

        val heroFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(235), dp(235)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(14) }
        }
        val ringView = GlowingMandalaRingView(this).also { liveRingView = it }
        val centerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        centerBox.addView(ImageView(this).apply { setImageResource(R.drawable.app_logo); layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply { bottomMargin = dp(4) } })
        val timerTv = TextView(this).apply { textSize = 24f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) }.also { liveTimerTv = it }
        centerBox.addView(timerTv)
        centerBox.addView(TextView(this).apply { text = s("time_left"); textSize = 10f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#34D399")) })
        heroFrame.addView(ringView); heroFrame.addView(centerBox)
        col.addView(heroFrame)
        updateLiveTimerUi()

        // Daily Phone Limit Adjuster Card
        val limCard = makeCard("⏱️  ${s("daily_limit")}: ${store.dailyPhoneLimitMinutes} ${s("minutes")}", "#10B981")
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (delta in listOf(-30, -15, 15, 30)) {
            val lbl = if (delta > 0) "+${delta}m" else "${delta}m"
            val b = Button(this).apply {
                text = lbl; background = pillBg("#1E293B", "#334155", 10); setTextColor(Color.WHITE); isAllCaps = false
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
                setOnClickListener {
                    requireParentPin {
                        store.dailyPhoneLimitMinutes = (store.dailyPhoneLimitMinutes + delta).coerceIn(15, 720)
                        renderTab(0)
                    }
                }
            }
            btnRow.addView(b)
        }
        limCard.addView(btnRow)
        col.addView(limCard)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val c1 = makeCard(borderHex = "#10B981").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8); bottomMargin = dp(14) }
            addView(TextView(this@MainActivity).apply { text = s("monitored_apps"); textSize = 12f; setTextColor(Color.parseColor("#94A3B8")) })
            addView(TextView(this@MainActivity).apply { text = "${store.getAppRules().size} ${s("active")}"; textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
        }
        val c2 = makeCard(borderHex = if (store.webSafetyEnabled) "#10B981" else "#F59E0B").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8); bottomMargin = dp(14) }
            addView(TextView(this@MainActivity).apply { text = s("web_safety"); textSize = 12f; setTextColor(Color.parseColor("#94A3B8")) })
            addView(TextView(this@MainActivity).apply {
                text = if (store.webSafetyEnabled) s("protected") else s("off")
                textSize = 17f; setTypeface(null, Typeface.BOLD)
                setTextColor(if (store.webSafetyEnabled) Color.parseColor("#10B981") else Color.parseColor("#F59E0B"))
            })
        }
        row.addView(c1); row.addView(c2)
        col.addView(row)

        col.addView(makeBtn(s("reset_today"), "#1E293B", "#334155") {
            requireParentPin { store.resetAllToday(); renderTab(0) }
        })

        scroll.addView(col)
        return scroll
    }

    // --- Tab 1: Installed Apps Picker (Tap + to choose any Game/App directly!) ---
    private fun showInstalledAppPicker() {
        val pm = packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null && it.packageName != packageName }
            .map { Triple(pm.getApplicationLabel(it).toString(), it.packageName, it.loadIcon(pm)) }
            .sortedBy { it.first.lowercase() }

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14))
            setBackgroundColor(Color.parseColor("#0F172A"))
        }
        val searchIn = EditText(this).apply {
            hint = s("search_hint"); setHintTextColor(Color.parseColor("#64748B")); setTextColor(Color.WHITE)
        }
        val listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360))
        }
        dialogLayout.addView(searchIn); dialogLayout.addView(listView)

        val dlg = AlertDialog.Builder(this).setTitle(s("pick_app_title")).setView(dialogLayout).setNegativeButton(s("cancel"), null).create()

        fun refreshList(query: String) {
            val filtered = if (query.isEmpty()) installed else installed.filter { it.first.contains(query, true) || it.second.contains(query, true) }
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, filtered.map { it.first })
            listView.adapter = adapter
            listView.setOnItemClickListener { _, _, pos, _ ->
                val chosen = filtered[pos]
                dlg.dismiss()
                askAppLimitDialog(chosen.first, chosen.second)
            }
        }
        refreshList("")
        searchIn.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshList(s?.toString() ?: "") }
            override fun afterTextChanged(s: Editable?) {}
        })
        dlg.show()
    }

    private fun askAppLimitDialog(appName: String, pkg: String) {
        val limIn = EditText(this).apply {
            setText("60"); inputType = InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle("$appName — ${s("limit_min")}")
            .setView(limIn)
            .setPositiveButton(s("save")) { _, _ ->
                val lim = (limIn.text.toString().toIntOrNull() ?: 60).coerceIn(1, 720)
                val list = store.getAppRules()
                list.removeAll { it.packageName.equals(pkg, ignoreCase = true) }
                list.add(AppRule(pkg, appName, lim))
                store.saveAppRules(list)
                renderTab(1)
            }
            .setNegativeButton(s("cancel"), null)
            .show()
    }

    private fun buildAppsTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(24)) }

        col.addView(makeBtn(s("add_app_btn"), "#10B981", "#059669", "#06281E") {
            requireParentPin { showInstalledAppPicker() }
        })
        col.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(14)) })

        for (rule in store.getAppRules()) {
            val itemCard = makeCard()
            itemCard.addView(TextView(this).apply { text = "${rule.displayName}  (${rule.dailyLimitMinutes} ${s("minutes")})"; textSize = 15.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
            itemCard.addView(TextView(this).apply { text = "${s("used_today")}: ${rule.secondsUsedToday / 60}m  •  ${rule.packageName}"; textSize = 12f; setTextColor(Color.parseColor("#94A3B8")) })
            itemCard.addView(makeBtn(s("remove"), "#991B1B", "#EF4444") {
                requireParentPin {
                    val list = store.getAppRules()
                    list.removeAll { it.packageName.equals(rule.packageName, ignoreCase = true) }
                    store.saveAppRules(list); renderTab(1)
                }
            })
            col.addView(itemCard)
        }
        scroll.addView(col)
        return scroll
    }

    // --- Tab 2: Prayer Times + Mosque Distance Break Duration (15/30/45/60m) + Adhan ---
    private fun buildPrayerTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(24)) }

        val ctrlCard = makeCard(s("prayer_title"), "#F59E0B")
        val wave = AdhanWaveView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)).apply { bottomMargin = dp(8) }
        }
        ctrlCard.addView(wave)

        val swLock = Switch(this).apply {
            text = s("prayer_lock_sw"); isChecked = store.prayerLockEnabled; setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked -> requireParentPin { store.prayerLockEnabled = checked } }
        }
        val swAdhan = Switch(this).apply {
            text = s("adhan_sw"); isChecked = store.adhanAudioEnabled; setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked -> requireParentPin { store.adhanAudioEnabled = checked } }
        }
        ctrlCard.addView(swLock); ctrlCard.addView(swAdhan)

        ctrlCard.addView(TextView(this).apply {
            text = "\n${s("prayer_dur_label")}  ${store.prayerDurationMinutes} ${s("minutes")}"
            setTextColor(Color.parseColor("#34D399")); setTypeface(null, Typeface.BOLD)
        })
        val durRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(4)) }
        for (mins in listOf(15, 30, 45, 60)) {
            val selected = (store.prayerDurationMinutes == mins)
            val b = Button(this).apply {
                text = "${mins}m"
                background = if (selected) pillBg("#10B981", "#059669", 10) else pillBg("#1E293B", "#334155", 10)
                setTextColor(if (selected) Color.parseColor("#06281E") else Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(3); marginEnd = dp(3) }
                setOnClickListener {
                    requireParentPin { store.prayerDurationMinutes = mins; renderTab(2) }
                }
            }
            durRow.addView(b)
        }
        ctrlCard.addView(durRow)

        ctrlCard.addView(makeBtn(s("test_adhan"), "#F59E0B", "#D97706", "#1F1300") {
            try {
                testPlayer?.release(); testPlayer = MediaPlayer.create(this, R.raw.adhan); testPlayer?.start()
                wave.isPlayingWave = true; testPlayer?.setOnCompletionListener { wave.isPlayingWave = false }
            } catch (_: Exception) { }
        })
        ctrlCard.addView(makeBtn(s("stop"), "#1E293B", "#334155") {
            testPlayer?.stop(); testPlayer?.release(); testPlayer = null; wave.isPlayingWave = false
        })
        ctrlCard.addView(makeBtn(s("test_lock"), "#0E7490", "#0284C7") {
            requireParentPin {
                startActivity(Intent(this, LockOverlayActivity::class.java).apply {
                    putExtra("MODE", "PRAYER_LOCK")
                    putExtra("REASON", if (store.isArabic) "صلاة العصر (تجربة)" else "Asr (Test)")
                    putExtra("END_MILLIS", System.currentTimeMillis() + store.prayerDurationMinutes * 60_000L)
                })
            }
        })
        col.addView(ctrlCard)

        val fmt = SimpleDateFormat("hh:mm a", Locale.US)
        val times = PrayerCalculator.calculateToday(store.latitude, store.longitude, store.timezoneOffset)
        for ((name, cal) in times) {
            val rowCard = makeCard()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(TextView(this).apply { text = name; textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            row.addView(TextView(this).apply { text = fmt.format(cal.time); textSize = 16f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#F59E0B")) })
            rowCard.addView(row)
            col.addView(rowCard)
        }
        scroll.addView(col)
        return scroll
    }

    // --- Tab 3: Web Safety & Anti-Uninstall ---
    private fun buildSafetyTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(24)) }

        val webCard = makeCard(s("web_title"), "#10B981")
        webCard.addView(TextView(this).apply { text = s("web_desc"); setTextColor(Color.parseColor("#CBD5E1")); textSize = 13.5f })
        webCard.addView(makeBtn(s("enable_dns"), "#10B981", "#059669", "#06281E") {
            requireParentPin {
                val prep = VpnService.prepare(this)
                if (prep != null) vpnLauncher.launch(prep)
                else { startService(Intent(this, SafeDnsVpnService::class.java)); store.webSafetyEnabled = true; renderTab(3) }
            }
        })
        webCard.addView(makeBtn(s("disable_dns"), "#1E293B", "#334155") {
            requireParentPin {
                startService(Intent(this, SafeDnsVpnService::class.java).apply { action = "STOP_VPN" })
                store.webSafetyEnabled = false; renderTab(3)
            }
        })
        col.addView(webCard)

        val permCard = makeCard(s("perm_title"), "#F59E0B")
        permCard.addView(makeBtn(s("perm_usage"), "#1E293B", "#334155") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        permCard.addView(makeBtn(s("perm_overlay"), "#1E293B", "#334155") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })
        permCard.addView(makeBtn(s("perm_admin"), "#10B981", "#059669", "#06281E") {
            val comp = ComponentName(this, DeviceAdminProtectionReceiver::class.java)
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply { putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp) })
        })
        col.addView(permCard)

        val pinCard = makeCard(s("pin_title"))
        val newPinIn = EditText(this).apply { hint = s("new_pin_hint"); setHintTextColor(Color.parseColor("#64748B")); setTextColor(Color.WHITE); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        pinCard.addView(newPinIn)
        pinCard.addView(makeBtn(s("update_pin"), "#F59E0B", "#D97706", "#1F1300") {
            requireParentPin {
                val np = newPinIn.text.toString().trim()
                if (np.length >= 4) { store.updatePin(np); newPinIn.text.clear(); Toast.makeText(this, "OK!", Toast.LENGTH_SHORT).show() }
            }
        })
        pinCard.addView(makeBtn(s("allow_uninstall"), "#991B1B", "#EF4444") {
            requireParentPin {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val comp = ComponentName(this, DeviceAdminProtectionReceiver::class.java)
                if (dpm.isAdminActive(comp)) dpm.removeActiveAdmin(comp)
            }
        })
        col.addView(pinCard)

        val updTitle = if (store.isArabic) "🔄 تحديث التطبيق تلقائياً (OTA)" else "🔄 In-App Auto-Update (OTA)"
        val updCard = makeCard(updTitle, "#38BDF8")
        val repoIn = EditText(this).apply {
            hint = if (store.isArabic) "اسم المستودع (مثال: Anas-crack/Muslim-keeper)" else "GitHub Repo (e.g. Anas-crack/Muslim-keeper)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            setText(store.updateRepo)
        }
        updCard.addView(repoIn)
        val curVer = UpdateManager.getCurrentVersionName(this)
        updCard.addView(TextView(this).apply {
            text = (if (store.isArabic) "الإصدار الحالي: v" else "Current Version: v") + curVer
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12.5f
        })
        val checkLbl = if (store.isArabic) "🚀  التحقق من التحديثات وتنزيلها" else "🚀  Check & Install Update"
        updCard.addView(makeBtn(checkLbl, "#0284C7", "#0369A1") {
            val repo = repoIn.text.toString().trim()
            store.updateRepo = repo
            if (!repo.contains("/")) {
                Toast.makeText(this, if (store.isArabic) "أدخل اسم المستودع بصيغة owner/repo" else "Enter repo as owner/repo", Toast.LENGTH_SHORT).show()
                return@makeBtn
            }
            Toast.makeText(this, if (store.isArabic) "جاري البحث عن تحديث..." else "Checking for updates...", Toast.LENGTH_SHORT).show()
            Thread {
                val rel = UpdateManager.checkLatestRelease(this, repo)
                uiHandler.post {
                    if (rel == null) {
                        Toast.makeText(this, if (store.isArabic) "أنت على أحدث إصدار بالفعل!" else "You are on the latest version!", Toast.LENGTH_SHORT).show()
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle(if (store.isArabic) "تحديث جديد متاح (${rel.versionTag})" else "New Update Available (${rel.versionTag})")
                            .setMessage(rel.releaseNotes.ifEmpty { if (store.isArabic) "هل تريد تحميل وتثبيت التحديث الآن؟" else "Download and install update now?" })
                            .setPositiveButton(if (store.isArabic) "تحديث الآن" else "Update Now") { _, _ ->
                                Toast.makeText(this, if (store.isArabic) "جاري تحميل التحديث..." else "Downloading APK...", Toast.LENGTH_SHORT).show()
                                Thread {
                                    val ok = UpdateManager.downloadAndInstallApk(this, rel.apkDownloadUrl)
                                    if (!ok) uiHandler.post {
                                        Toast.makeText(this, if (store.isArabic) "فشل تحميل التحديث" else "Update download failed", Toast.LENGTH_SHORT).show()
                                    }
                                }.start()
                            }
                            .setNegativeButton(s("cancel"), null)
                            .show()
                    }
                }
            }.start()
        })
        col.addView(updCard)

        scroll.addView(col)
        return scroll
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(liveClockRunnable)
        testPlayer?.release()
        super.onDestroy()
    }
}
