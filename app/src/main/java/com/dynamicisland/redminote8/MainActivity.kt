package com.dynamicisland.redminote8

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.app.usage.UsageStatsManager
import android.graphics.Canvas
import android.hardware.camera2.CameraManager
import android.media.AudioRecordingConfiguration
import android.service.notification.NotificationListenerService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var settings: IslandSettings
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvNotifStatus: TextView
    private lateinit var tvServiceState: TextView
    private lateinit var switchEnable: Switch

    private val pickAvatarLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                settings = settings.copy(avatarUri = uri.toString())
                saveAndNotify()
                Toast.makeText(this, "تم تحديث صورة الملف الشخصي بجودة عالية", Toast.LENGTH_SHORT).show()
            }
        }

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = IslandConfig.load(this)

        val permsToAsk = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permsToAsk.add(Manifest.permission.POST_NOTIFICATIONS)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            permsToAsk.add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED
        ) permsToAsk.add(Manifest.permission.ANSWER_PHONE_CALLS)
        if (permsToAsk.isNotEmpty()) {
            requestPermissions(permsToAsk.toTypedArray(), 901)
        }

        val tvVersionBadge = findViewById<TextView>(R.id.tvVersionBadge)
        val tvDevVersion = findViewById<TextView>(R.id.tvDevVersion)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvNotifStatus = findViewById(R.id.tvNotifStatus)
        tvServiceState = findViewById(R.id.tvServiceState)
        switchEnable = findViewById(R.id.switchEnableIsland)

        val btnOverlayPerm = findViewById<Button>(R.id.btnOverlayPermission)
        val btnNotifPerm = findViewById<Button>(R.id.btnNotifPermission)
        val btnAppInfoPerm = findViewById<Button>(R.id.btnAppInfoRestricted)
        val btnTestIsland = findViewById<Button>(R.id.btnTestIsland)
        val btnTestAnim = findViewById<Button>(R.id.btnTestAnim)
        val btnTestDual = findViewById<Button>(R.id.btnTestDual)
        val btnTestCharge = findViewById<Button>(R.id.btnTestCharge)
        val btnTestCall = findViewById<Button>(R.id.btnTestCall)
        val btnTestPrivacy = findViewById<Button>(R.id.btnTestPrivacy)
        val btnUsagePerm = findViewById<Button>(R.id.btnUsagePermission)
        val btnPickAvatar = findViewById<Button>(R.id.btnPickAvatar)

        val rgCutout = findViewById<RadioGroup>(R.id.rgCutoutType)
        val switchCoverBar = findViewById<Switch>(R.id.switchCoverStatusBar)
        val switchHideFullscreen = findViewById<Switch>(R.id.switchHideFullscreen)
        val switchDualActivity = findViewById<Switch>(R.id.switchDualActivity)
        val switchGlassBlur = findViewById<Switch>(R.id.switchGlassBlur)
        val seekGlassOpacity = findViewById<SeekBar>(R.id.seekGlassOpacity)
        val tvGlassOpacityVal = findViewById<TextView>(R.id.tvGlassOpacityVal)
        val switchKeepNotifOpen = findViewById<Switch>(R.id.switchKeepNotifOpen)
        val switchAlwaysOn = findViewById<Switch>(R.id.switchAlwaysOn)
        val switchAutoHide = findViewById<Switch>(R.id.switchAutoHide)
        val switchStatusIcons = findViewById<Switch>(R.id.switchStatusIcons)

        val seekAutoHideSec = findViewById<SeekBar>(R.id.seekAutoHideSec)
        val tvAutoHideVal = findViewById<TextView>(R.id.tvAutoHideVal)
        val rgHidePresets = findViewById<RadioGroup>(R.id.rgHidePresets)
        val etAutoHideSec = findViewById<EditText>(R.id.etAutoHideSec)
        val btnApplyAutoHide = findViewById<Button>(R.id.btnApplyAutoHide)

        val seekNotchGap = findViewById<SeekBar>(R.id.seekNotchGap)
        val tvNotchGapVal = findViewById<TextView>(R.id.tvNotchGapVal)
        val seekBarHeight = findViewById<SeekBar>(R.id.seekBarHeight)
        val tvBarHeightVal = findViewById<TextView>(R.id.tvBarHeightVal)
        val seekTop = findViewById<SeekBar>(R.id.seekTopOffset)
        val tvTopVal = findViewById<TextView>(R.id.tvTopOffsetVal)
        val seekWidth = findViewById<SeekBar>(R.id.seekWidth)
        val tvWidthVal = findViewById<TextView>(R.id.tvWidthVal)

        val rgTheme = findViewById<RadioGroup>(R.id.rgTheme)
        val etUserName = findViewById<EditText>(R.id.etUserName)
        val switch24h = findViewById<Switch>(R.id.switch24Hour)

        val etGithubRepo = findViewById<EditText>(R.id.etGithubRepo)
        val switchAutoUpdate = findViewById<Switch>(R.id.switchAutoUpdate)
        val btnCheckUpdate = findViewById<Button>(R.id.btnCheckUpdate)

        tvVersionBadge.text = "v${BuildConfig.VERSION_NAME} • Glass Blur + Dual Pill"
        tvDevVersion.text = "تطبيق Dynamic Island — الإصدار v${BuildConfig.VERSION_NAME}"
        switchEnable.isChecked = DynamicIslandService.isRunning

        if (settings.cutoutType == "punch") rgCutout.check(R.id.rbPunchHole)
        else rgCutout.check(R.id.rbWaterdropNotch)

        switchCoverBar.isChecked = settings.coverStatusBar
        switchHideFullscreen.isChecked = settings.hideInFullscreen
        switchDualActivity.isChecked = settings.dualActivityEnabled
        switchGlassBlur.isChecked = settings.glassEffectEnabled
        seekGlassOpacity.progress = (settings.glassOpacityPercent - 40).coerceIn(0, 60)
        tvGlassOpacityVal.text = "${settings.glassOpacityPercent}%"
        switchKeepNotifOpen.isChecked = settings.keepNotifUntilRead
        switchAlwaysOn.isChecked = !settings.autoHideEnabled
        switchAutoHide.isChecked = settings.autoHideEnabled
        switchStatusIcons.isChecked = settings.showStatusIcons

        fun formatHideLabel(sec: Int): String = if (sec == 60) "دقيقة (60 ث)" else "$sec ثانية"
        seekAutoHideSec.progress = (settings.autoHideSeconds - 5).coerceIn(0, 115)
        tvAutoHideVal.text = formatHideLabel(settings.autoHideSeconds)
        etAutoHideSec.setText(settings.autoHideSeconds.toString())
        when (settings.autoHideSeconds) {
            5 -> rgHidePresets.check(R.id.rbHide5)
            15 -> rgHidePresets.check(R.id.rbHide15)
            30 -> rgHidePresets.check(R.id.rbHide30)
            45 -> rgHidePresets.check(R.id.rbHide45)
            60 -> rgHidePresets.check(R.id.rbHide60)
        }

        seekNotchGap.progress = (settings.notchGapDp - 22).coerceIn(0, 68)
        tvNotchGapVal.text = "${settings.notchGapDp} dp"
        seekBarHeight.progress = (settings.barHeightDp - 30).coerceIn(0, 28)
        tvBarHeightVal.text = "${settings.barHeightDp} dp"
        seekTop.progress = settings.topOffsetDp
        tvTopVal.text = "${settings.topOffsetDp} dp"
        seekWidth.progress = (settings.compactWidthDp - 180).coerceIn(0, 200)
        tvWidthVal.text = "${settings.compactWidthDp} dp"

        etUserName.setText(settings.userName)
        switch24h.isChecked = settings.use24Hour
        etGithubRepo.setText(settings.githubRepo)
        switchAutoUpdate.isChecked = settings.autoCheckUpdate

        when (settings.themeId) {
            "charcoal" -> rgTheme.check(R.id.rbCharcoal)
            "navy" -> rgTheme.check(R.id.rbNavy)
            "light" -> rgTheme.check(R.id.rbLight)
            else -> rgTheme.check(R.id.rbBlack)
        }

        rgCutout.setOnCheckedChangeListener { _, checkedId ->
            val isPunch = checkedId == R.id.rbPunchHole
            val defaultGap = if (isPunch) 28 else 48
            settings = settings.copy(
                cutoutType = if (isPunch) "punch" else "notch",
                notchGapDp = defaultGap
            )
            seekNotchGap.progress = (defaultGap - 22).coerceIn(0, 68)
            tvNotchGapVal.text = "$defaultGap dp"
            saveAndNotify()
        }

        switchCoverBar.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(coverStatusBar = isChecked)
            saveAndNotify()
        }

        switchHideFullscreen.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(hideInFullscreen = isChecked)
            saveAndNotify()
        }

        switchDualActivity.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(dualActivityEnabled = isChecked)
            saveAndNotify()
        }

        switchGlassBlur.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(glassEffectEnabled = isChecked)
            saveAndNotify()
        }

        seekGlassOpacity.setOnSeekBarChangeListener(simpleSeek { p ->
            val pct = (40 + p).coerceIn(40, 100)
            settings = settings.copy(glassOpacityPercent = pct)
            tvGlassOpacityVal.text = "$pct%"
            saveAndNotify()
        })

        switchKeepNotifOpen.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(keepNotifUntilRead = isChecked)
            saveAndNotify()
        }

        switchAlwaysOn.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(autoHideEnabled = !isChecked)
            if (switchAutoHide.isChecked == isChecked) switchAutoHide.isChecked = !isChecked
            saveAndNotify()
        }

        switchAutoHide.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(autoHideEnabled = isChecked)
            if (switchAlwaysOn.isChecked == isChecked) switchAlwaysOn.isChecked = !isChecked
            saveAndNotify()
        }

        rgHidePresets.setOnCheckedChangeListener { _, checkedId ->
            val sec = when (checkedId) {
                R.id.rbHide5 -> 5
                R.id.rbHide15 -> 15
                R.id.rbHide30 -> 30
                R.id.rbHide45 -> 45
                R.id.rbHide60 -> 60
                else -> settings.autoHideSeconds
            }
            settings = settings.copy(autoHideEnabled = true, autoHideSeconds = sec)
            switchAutoHide.isChecked = true
            switchAlwaysOn.isChecked = false
            seekAutoHideSec.progress = (sec - 5).coerceIn(0, 115)
            tvAutoHideVal.text = formatHideLabel(sec)
            etAutoHideSec.setText(sec.toString())
            saveAndNotify()
        }

        seekAutoHideSec.setOnSeekBarChangeListener(simpleSeek { p ->
            val sec = (5 + p).coerceIn(5, 120)
            settings = settings.copy(autoHideEnabled = true, autoHideSeconds = sec)
            switchAutoHide.isChecked = true
            switchAlwaysOn.isChecked = false
            tvAutoHideVal.text = formatHideLabel(sec)
            etAutoHideSec.setText(sec.toString())
            saveAndNotify()
        })

        btnApplyAutoHide.setOnClickListener {
            val raw = etAutoHideSec.text.toString().trim().toIntOrNull()
            if (raw == null || raw < 5) {
                Toast.makeText(this, "أقل وقت مسموح هو 5 ثواني", Toast.LENGTH_SHORT).show()
                etAutoHideSec.setText("5")
                return@setOnClickListener
            }
            val sec = raw.coerceIn(5, 300)
            settings = settings.copy(autoHideEnabled = true, autoHideSeconds = sec)
            switchAutoHide.isChecked = true
            switchAlwaysOn.isChecked = false
            seekAutoHideSec.progress = (sec - 5).coerceIn(0, 115)
            tvAutoHideVal.text = formatHideLabel(sec)
            etAutoHideSec.setText(sec.toString())
            saveAndNotify()
            Toast.makeText(this, "تم ضبط الاختفاء التلقائي على $sec ثانية", Toast.LENGTH_SHORT).show()
        }

        switchStatusIcons.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(showStatusIcons = isChecked)
            saveAndNotify()
        }

        seekNotchGap.setOnSeekBarChangeListener(simpleSeek { p ->
            val gap = 22 + p
            settings = settings.copy(notchGapDp = gap)
            tvNotchGapVal.text = "$gap dp"
            saveAndNotify()
        })

        seekBarHeight.setOnSeekBarChangeListener(simpleSeek { p ->
            val h = 30 + p
            settings = settings.copy(barHeightDp = h)
            tvBarHeightVal.text = "$h dp"
            saveAndNotify()
        })

        btnOverlayPerm.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }

        btnNotifPerm.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        btnAppInfoPerm.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            Toast.makeText(
                this,
                "اضغط الـ 3 نقاط بالأعلى ← السماح بالإعدادات المقيدة، وفعّل التشغيل التلقائي",
                Toast.LENGTH_LONG
            ).show()
        }

        btnTestIsland.setOnClickListener {
            if (!DynamicIslandService.isRunning) {
                Toast.makeText(this, "شغّل مفتاح الجزيرة العائمة أولًا بالأعلى", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendBroadcast(Intent(DynamicIslandService.ACTION_TEST_PING).setPackage(packageName))
        }

        btnTestAnim.setOnClickListener {
            if (!DynamicIslandService.isRunning) {
                Toast.makeText(this, "شغّل مفتاح الجزيرة العائمة أولًا بالأعلى", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendBroadcast(Intent(DynamicIslandService.ACTION_TEST_ANIM).setPackage(packageName))
        }

        btnTestDual.setOnClickListener {
            if (!DynamicIslandService.isRunning) {
                Toast.makeText(this, "شغّل مفتاح الجزيرة العائمة أولًا بالأعلى", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendBroadcast(Intent(DynamicIslandService.ACTION_TEST_DUAL).setPackage(packageName))
        }

        btnTestCharge.setOnClickListener {
            if (!DynamicIslandService.isRunning) {
                Toast.makeText(this, "شغّل مفتاح الجزيرة العائمة أولًا بالأعلى", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendBroadcast(Intent(DynamicIslandService.ACTION_TEST_CHARGE).setPackage(packageName))
        }

        btnTestCall.setOnClickListener {
            if (!DynamicIslandService.isRunning) {
                Toast.makeText(this, "شغّل مفتاح الجزيرة العائمة أولًا بالأعلى", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendBroadcast(Intent(DynamicIslandService.ACTION_TEST_CALL).setPackage(packageName))
        }

        btnTestPrivacy.setOnClickListener {
            if (!DynamicIslandService.isRunning) {
                Toast.makeText(this, "شغّل مفتاح الجزيرة العائمة أولًا بالأعلى", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendBroadcast(Intent(DynamicIslandService.ACTION_TEST_PRIVACY).setPackage(packageName))
        }

        btnUsagePerm.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        btnPickAvatar.setOnClickListener {
            pickAvatarLauncher.launch(arrayOf("image/*"))
        }

        switchEnable.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !Settings.canDrawOverlays(this)) {
                switchEnable.isChecked = false
                Toast.makeText(this, "فعّل إذن الظهور فوق التطبيقات أولًا", Toast.LENGTH_LONG).show()
                btnOverlayPerm.performClick()
                return@setOnCheckedChangeListener
            }
            settings = settings.copy(enabled = isChecked)
            IslandConfig.save(this, settings)
            if (isChecked) {
                try {
                    ContextCompat.startForegroundService(this, Intent(this, DynamicIslandService::class.java))
                    Handler(Looper.getMainLooper()).postDelayed({ refreshPermissionBadges() }, 450)
                } catch (e: Exception) {
                    switchEnable.isChecked = false
                    Toast.makeText(this, "تعذر التشغيل: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            } else {
                stopService(Intent(this, DynamicIslandService::class.java))
                Handler(Looper.getMainLooper()).postDelayed({ refreshPermissionBadges() }, 250)
            }
        }

        seekTop.setOnSeekBarChangeListener(simpleSeek { p ->
            settings = settings.copy(topOffsetDp = p)
            tvTopVal.text = "$p dp"
            saveAndNotify()
        })

        seekWidth.setOnSeekBarChangeListener(simpleSeek { p ->
            val w = 180 + p
            settings = settings.copy(compactWidthDp = w)
            tvWidthVal.text = "$w dp"
            saveAndNotify()
        })

        rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val tid = when (checkedId) {
                R.id.rbCharcoal -> "charcoal"
                R.id.rbNavy -> "navy"
                R.id.rbLight -> "light"
                else -> "black"
            }
            settings = settings.copy(themeId = tid)
            saveAndNotify()
        }

        switch24h.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(use24Hour = isChecked)
            saveAndNotify()
        }

        etUserName.addTextChangedListener(simpleTextWatcher { txt ->
            settings = settings.copy(userName = txt.trim().ifBlank { "أنس محمد" })
            saveAndNotify()
        })

        etGithubRepo.addTextChangedListener(simpleTextWatcher { txt ->
            settings = settings.copy(githubRepo = txt.trim())
            IslandConfig.save(this, settings)
        })

        switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            settings = settings.copy(autoCheckUpdate = isChecked)
            IslandConfig.save(this, settings)
        }

        btnCheckUpdate.setOnClickListener {
            GitHubUpdater.checkForUpdate(this, settings.githubRepo, silentIfUpToDate = false)
        }

        if (settings.autoCheckUpdate && settings.githubRepo.contains("/")) {
            GitHubUpdater.checkForUpdate(this, settings.githubRepo, silentIfUpToDate = true)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        ) {
            try {
                android.service.notification.NotificationListenerService.requestRebind(
                    android.content.ComponentName(this, IslandNotificationListener::class.java)
                )
            } catch (_: Exception) {
            }
        }
        refreshPermissionBadges()
    }

    private fun refreshPermissionBadges() {
        val overlayOk = Settings.canDrawOverlays(this)
        val notifOk = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

        tvOverlayStatus.text = if (overlayOk) "✓ مفعّل" else "✕ غير مفعّل"
        tvOverlayStatus.setTextColor(Color.parseColor(if (overlayOk) "#34D399" else "#FB7185"))

        tvNotifStatus.text = if (notifOk) "✓ مفعّل (يقرأ يوتيوب والموسيقى)" else "✕ غير مفعّل"
        tvNotifStatus.setTextColor(Color.parseColor(if (notifOk) "#34D399" else "#FBBF24"))

        switchEnable.isChecked = DynamicIslandService.isRunning
        if (DynamicIslandService.isRunning) {
            tvServiceState.text = "● الجزيرة المعتمة ظاهرة وتعمل الآن"
            tvServiceState.setTextColor(Color.parseColor("#34D399"))
        } else {
            tvServiceState.text = "● متوقفة حاليًا"
            tvServiceState.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun saveAndNotify() {
        IslandConfig.save(this, settings)
        sendBroadcast(Intent(DynamicIslandService.ACTION_RELOAD_CONFIG).setPackage(packageName))
    }

    private fun simpleSeek(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    }

    private fun simpleTextWatcher(onAfter: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            onAfter(s?.toString().orEmpty())
        }
    }
}
