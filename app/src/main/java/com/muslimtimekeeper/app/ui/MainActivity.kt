package com.muslimtimekeeper.app.ui

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
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

    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            startService(Intent(this, SafeDnsVpnService::class.java))
            store.webSafetyEnabled = true
            Toast.makeText(this, "Web Safety DNS Filter Activated!", Toast.LENGTH_SHORT).show()
            renderTab(currentTab)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = PrefsStore(this)
        ContextCompat.startForegroundService(this, Intent(this, GuardianService::class.java))
        buildShell()
        renderTab(0)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun cardBg(borderHex: String = "#26354D"): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#16233A"), Color.parseColor("#0F172A"))
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.parseColor(borderHex))
        }
    }

    private fun pillBg(startHex: String, endHex: String, radius: Int = 14): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor(startHex), Color.parseColor(endHex))
        ).apply {
            cornerRadius = dp(radius).toFloat()
        }
    }

    private fun requireParentPin(onSuccess: () -> Unit) {
        if (parentUnlocked) { onSuccess(); return }
        val input = EditText(this).apply {
            hint = "Default PIN: 1234"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        AlertDialog.Builder(this)
            .setTitle("🔐 Parent Authentication")
            .setMessage("Enter Parent PIN to modify settings:")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                if (store.verifyPin(input.text.toString())) {
                    parentUnlocked = true
                    updateLockChip()
                    onSuccess()
                } else {
                    Toast.makeText(this, "Incorrect Parent PIN!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateLockChip() {
        lockStatusChip.text = if (parentUnlocked) "🔓 Unlocked" else "🔒 Locked"
        lockStatusChip.background = if (parentUnlocked) pillBg("#10B981", "#059669", 12) else pillBg("#1E293B", "#1E293B", 12)
        lockStatusChip.setTextColor(if (parentUnlocked) Color.parseColor("#06281E") else Color.WHITE)
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#070B14"))
        }

        // Top App Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0B1120"))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) }
        }
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleBox.addView(TextView(this).apply {
            text = "Muslim Time Keeper"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        titleBox.addView(TextView(this).apply {
            text = "حارس وقت المسلم"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F59E0B"))
        })

        lockStatusChip = TextView(this).apply {
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setOnClickListener {
                if (parentUnlocked) { parentUnlocked = false; updateLockChip() }
                else requireParentPin { }
            }
        }
        updateLockChip()
        topBar.addView(logo)
        topBar.addView(titleBox)
        topBar.addView(lockStatusChip)

        // Content Host
        contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        // Modern Bottom Navigation Bar
        val navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(dp(8), dp(8), dp(8), dp(10))
        }
        val labels = listOf("📊\nHome", "🎮\nApps", "🕌\nPrayer", "🛡️\nSafety")
        navButtons = labels.mapIndexed { idx, txt ->
            TextView(this).apply {
                text = txt
                gravity = Gravity.CENTER
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(4), dp(8), dp(4), dp(8))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(4); marginEnd = dp(4)
                }
                setOnClickListener { renderTab(idx) }
            }.also { navBar.addView(it) }
        }

        root.addView(topBar)
        root.addView(contentContainer)
        root.addView(navBar)
        setContentView(root)
    }

    private fun renderTab(index: Int) {
        currentTab = index
        navButtons.forEachIndexed { i, tv ->
            if (i == index) {
                tv.background = pillBg("#133A31", "#133A31", 12)
                tv.setTextColor(Color.parseColor("#34D399"))
            } else {
                tv.background = null
                tv.setTextColor(Color.parseColor("#94A3B8"))
            }
        }

        val pageView = when (index) {
            0 -> buildHomeTab()
            1 -> buildAppsTab()
            2 -> buildPrayerTab()
            else -> buildSafetyTab()
        }

        contentContainer.removeAllViews()
        pageView.alpha = 0f
        pageView.translationY = 24f
        contentContainer.addView(pageView)
        pageView.animate().alpha(1f).translationY(0f).setDuration(260L).start()
    }

    private fun makeCard(title: String? = null, borderHex: String = "#26354D"): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg(borderHex)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
            if (title != null) {
                addView(TextView(this@MainActivity).apply {
                    text = title
                    textSize = 15.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#F59E0B"))
                    setPadding(0, 0, 0, dp(10))
                })
            }
        }
    }

    private fun makeBtn(label: String, c1: String, c2: String, fgHex: String = "#FFFFFF", onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            background = pillBg(c1, c2, 12)
            setTextColor(Color.parseColor(fgHex))
            isAllCaps = false
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply {
                topMargin = dp(10)
            }
            setOnClickListener { onClick() }
        }
    }

    // --- Tab 0: Animated Dashboard ---
    private fun buildHomeTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }

        val totalSec = maxOf(1, store.dailyPhoneLimitMinutes * 60)
        val remSec = maxOf(0, totalSec - store.phoneSecondsUsedToday)
        val ratio = remSec.toFloat() / totalSec.toFloat()

        // Hero Glowing Mandala Ring
        val heroFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(235), dp(235)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16)
            }
        }
        val ringView = GlowingMandalaRingView(this).apply { setProgressRatio(ratio) }
        val centerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        centerBox.addView(ImageView(this).apply {
            setImageResource(R.drawable.app_logo)
            layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)).apply { bottomMargin = dp(4) }
        })
        centerBox.addView(TextView(this).apply {
            text = String.format("%02d:%02d", remSec / 3600, (remSec % 3600) / 60)
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        centerBox.addView(TextView(this).apply {
            text = "TIME LEFT TODAY"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#34D399"))
        })
        heroFrame.addView(ringView)
        heroFrame.addView(centerBox)
        col.addView(heroFrame)

        // Two KPI Cards side-by-side
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val c1 = makeCard(borderHex = "#10B981").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8); bottomMargin = dp(14) }
            addView(TextView(this@MainActivity).apply { text = "🎮 Monitored Apps"; textSize = 12f; setTextColor(Color.parseColor("#94A3B8")) })
            addView(TextView(this@MainActivity).apply { text = "${store.getAppRules().size} Active"; textSize = 19f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
        }
        val c2 = makeCard(borderHex = if (store.webSafetyEnabled) "#10B981" else "#F59E0B").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8); bottomMargin = dp(14) }
            addView(TextView(this@MainActivity).apply { text = "🌐 Web Safety"; textSize = 12f; setTextColor(Color.parseColor("#94A3B8")) })
            addView(TextView(this@MainActivity).apply {
                text = if (store.webSafetyEnabled) "PROTECTED" else "OFF"
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (store.webSafetyEnabled) Color.parseColor("#10B981") else Color.parseColor("#F59E0B"))
            })
        }
        row.addView(c1); row.addView(c2)
        col.addView(row)

        val quickCard = makeCard("⚡ Quick Controls")
        quickCard.addView(makeBtn("🔄  Reset Today's Counters & Unlock", "#1E293B", "#334155") {
            requireParentPin {
                store.resetAllToday()
                Toast.makeText(this, "All daily counters reset!", Toast.LENGTH_SHORT).show()
                renderTab(0)
            }
        })
        col.addView(quickCard)

        scroll.addView(col)
        return scroll
    }

    // --- Tab 1: Apps & Games ---
    private fun buildAppsTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(24)) }

        val addCard = makeCard("🎮 Add App / Game Time Limit", "#10B981")
        val pkgIn = EditText(this).apply { hint = "Package (e.g. com.roblox.client)"; setHintTextColor(Color.parseColor("#64748B")); setTextColor(Color.WHITE) }
        val nameIn = EditText(this).apply { hint = "Display Name (e.g. Roblox)"; setHintTextColor(Color.parseColor("#64748B")); setTextColor(Color.WHITE) }
        val limIn = EditText(this).apply { hint = "Daily Limit in Minutes (e.g. 60)"; setHintTextColor(Color.parseColor("#64748B")); setTextColor(Color.WHITE); inputType = InputType.TYPE_CLASS_NUMBER }
        addCard.addView(pkgIn); addCard.addView(nameIn); addCard.addView(limIn)
        addCard.addView(makeBtn("➕  Save Rule", "#10B981", "#059669", "#06281E") {
            requireParentPin {
                val pkg = pkgIn.text.toString().trim()
                val name = nameIn.text.toString().trim().ifEmpty { pkg }
                val lim = limIn.text.toString().toIntOrNull() ?: 60
                if (pkg.isNotEmpty()) {
                    val list = store.getAppRules()
                    list.removeAll { it.packageName.equals(pkg, ignoreCase = true) }
                    list.add(AppRule(pkg, name, lim))
                    store.saveAppRules(list)
                    renderTab(1)
                }
            }
        })
        col.addView(addCard)

        for (rule in store.getAppRules()) {
            val itemCard = makeCard()
            itemCard.addView(TextView(this).apply { text = "${rule.displayName}  (${rule.dailyLimitMinutes} min/day)"; textSize = 15.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE) })
            itemCard.addView(TextView(this).apply { text = "${rule.packageName}  •  Used: ${rule.secondsUsedToday / 60}m  •  Violations: ${rule.reopenAttempts}"; textSize = 12.5f; setTextColor(Color.parseColor("#94A3B8")) })
            itemCard.addView(makeBtn("🗑️  Remove Rule", "#991B1B", "#EF4444") {
                requireParentPin {
                    val list = store.getAppRules()
                    list.removeAll { it.packageName.equals(rule.packageName, ignoreCase = true) }
                    store.saveAppRules(list)
                    renderTab(1)
                }
            })
            col.addView(itemCard)
        }

        scroll.addView(col)
        return scroll
    }

    // --- Tab 2: Prayer & Egypt Adhan ---
    private fun buildPrayerTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(24)) }

        val ctrlCard = makeCard("🕌 Prayer Times & Egypt Adhan", "#F59E0B")
        val wave = AdhanWaveView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)).apply { bottomMargin = dp(8) }
        }
        ctrlCard.addView(wave)
        ctrlCard.addView(TextView(this).apply { text = "📍 Active City: ${store.cityName}"; setTextColor(Color.parseColor("#34D399")); setTypeface(null, Typeface.BOLD) })
        ctrlCard.addView(makeBtn("🔊  Test Built-in Egypt Adhan", "#F59E0B", "#D97706", "#1F1300") {
            try {
                testPlayer?.release()
                testPlayer = MediaPlayer.create(this, R.raw.adhan)
                testPlayer?.start()
                wave.isPlayingWave = true
                testPlayer?.setOnCompletionListener { wave.isPlayingWave = false }
            } catch (_: Exception) { }
        })
        ctrlCard.addView(makeBtn("⏹️  Stop Adhan Audio", "#1E293B", "#334155") {
            testPlayer?.stop(); testPlayer?.release(); testPlayer = null; wave.isPlayingWave = false
        })
        ctrlCard.addView(makeBtn("🕌  Test Animated Prayer Lock Screen", "#0E7490", "#0284C7") {
            requireParentPin {
                startActivity(Intent(this, LockOverlayActivity::class.java).apply {
                    putExtra("MODE", "PRAYER_LOCK")
                    putExtra("REASON", "Asr (Test)")
                    putExtra("END_MILLIS", System.currentTimeMillis() + 60_000L)
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

    // --- Tab 3: Web Safety & Permissions ---
    private fun buildSafetyTab(): View {
        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(24)) }

        val webCard = makeCard("🌐 Web Safety — Block Adult & Dangerous Sites", "#10B981")
        webCard.addView(TextView(this).apply {
            text = "Routes DNS through Cloudflare Family (1.1.1.3) & CleanBrowsing Family to block adult, explicit, and malware sites across all browsers without root."
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 13.5f
        })
        webCard.addView(makeBtn("🛡️  Enable Web Safety Filter", "#10B981", "#059669", "#06281E") {
            requireParentPin {
                val prep = VpnService.prepare(this)
                if (prep != null) vpnLauncher.launch(prep)
                else {
                    startService(Intent(this, SafeDnsVpnService::class.java))
                    store.webSafetyEnabled = true
                    renderTab(3)
                }
            }
        })
        webCard.addView(makeBtn("⏹️  Disable Web Safety Filter", "#1E293B", "#334155") {
            requireParentPin {
                startService(Intent(this, SafeDnsVpnService::class.java).apply { action = "STOP_VPN" })
                store.webSafetyEnabled = false
                renderTab(3)
            }
        })
        col.addView(webCard)

        val permCard = makeCard("🛡️ System Permissions & Anti-Uninstall", "#F59E0B")
        permCard.addView(makeBtn("1️⃣  Grant Usage Access (Track Apps)", "#1E293B", "#334155") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        permCard.addView(makeBtn("2️⃣  Grant Draw Over Apps (Lock Screen)", "#1E293B", "#334155") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        permCard.addView(makeBtn("3️⃣  Activate Device Admin (Prevent Uninstall)", "#10B981", "#059669", "#06281E") {
            val comp = ComponentName(this, DeviceAdminProtectionReceiver::class.java)
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevents child from uninstalling Muslim Time Keeper.")
            })
        })
        col.addView(permCard)

        val pinCard = makeCard("🔐 Parent PIN & Uninstall")
        val newPinIn = EditText(this).apply {
            hint = "New Parent PIN (min 4 chars)"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        pinCard.addView(newPinIn)
        pinCard.addView(makeBtn("🔐  Update Parent PIN", "#F59E0B", "#D97706", "#1F1300") {
            requireParentPin {
                val np = newPinIn.text.toString().trim()
                if (np.length >= 4) {
                    store.updatePin(np); newPinIn.text.clear()
                    Toast.makeText(this, "Parent PIN updated!", Toast.LENGTH_SHORT).show()
                }
            }
        })
        pinCard.addView(makeBtn("🔓  Allow Uninstall (Deactivate Admin)", "#991B1B", "#EF4444") {
            requireParentPin {
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val comp = ComponentName(this, DeviceAdminProtectionReceiver::class.java)
                if (dpm.isAdminActive(comp)) dpm.removeActiveAdmin(comp)
                Toast.makeText(this, "Device Admin removed. You can now uninstall the app.", Toast.LENGTH_LONG).show()
            }
        })
        col.addView(pinCard)

        scroll.addView(col)
        return scroll
    }

    override fun onDestroy() {
        testPlayer?.release()
        super.onDestroy()
    }
}
