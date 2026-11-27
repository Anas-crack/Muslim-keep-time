package com.dynamicisland.redminote8

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.app.usage.UsageStatsManager
import android.graphics.Canvas
import android.hardware.camera2.CameraManager
import android.media.AudioRecordingConfiguration
import android.service.notification.NotificationListenerService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AnticipateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class BatteryDetails(
    val pct: Int,
    val isCharging: Boolean,
    val watts: Int,
    val isFast: Boolean
)

class DynamicIslandService : Service() {

    companion object {
        const val ACTION_REFRESH = "com.dynamicisland.redminote8.ACTION_REFRESH"
        const val ACTION_RELOAD_CONFIG = "com.dynamicisland.redminote8.ACTION_RELOAD_CONFIG"
        const val ACTION_TEST_PING = "com.dynamicisland.redminote8.ACTION_TEST_PING"
        const val ACTION_TEST_ANIM = "com.dynamicisland.redminote8.ACTION_TEST_ANIM"
        const val ACTION_TEST_DUAL = "com.dynamicisland.redminote8.ACTION_TEST_DUAL"
        const val ACTION_TEST_CHARGE = "com.dynamicisland.redminote8.ACTION_TEST_CHARGE"
        const val ACTION_NEW_NOTIF = "com.dynamicisland.redminote8.ACTION_NEW_NOTIF"
        const val ACTION_CALL_UPDATE = "com.dynamicisland.redminote8.ACTION_CALL_UPDATE"
        const val ACTION_CALL_RINGING = "com.dynamicisland.redminote8.ACTION_CALL_RINGING"
        const val ACTION_TEST_CALL = "com.dynamicisland.redminote8.ACTION_TEST_CALL"
        const val ACTION_TEST_PRIVACY = "com.dynamicisland.redminote8.ACTION_TEST_PRIVACY"
        private const val CHANNEL_ID = "dynamic_island_overlay_channel"
        private const val NOTIF_ID = 8008
        @Volatile var isRunning: Boolean = false
    }

    private lateinit var windowManager: WindowManager
    private var rootView: View? = null
    private var fullscreenProbeView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var settings: IslandSettings = IslandSettings()
    private var isExpanded: Boolean = false
    private var isIslandHidden: Boolean = false
    private var hiddenByFullscreen: Boolean = false
    private var fullscreenStreak: Int = 0
    private var wasFallbackAudioPlaying: Boolean = false
    private var mediaPausedSince: Long = 0L
    private var lastSeenNotifVersion: Long = 0L
    private var isCameraActive: Boolean = false
    private var isMicActive: Boolean = false
    private var privacyDemoUntil: Long = 0L
    private val activeCameraIds = mutableSetOf<String>()
    private val appIconCache = HashMap<String, Bitmap>()
    private var cameraManager: CameraManager? = null
    private var audioManagerRef: AudioManager? = null

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            activeCameraIds.add(cameraId)
            val was = isCameraActive
            isCameraActive = true
            if (!was) {
                handler.post {
                    hiddenByFullscreen = false
                    wakeUpIsland(forceAnim = true)
                    updateIslandUi()
                }
            }
        }
        override fun onCameraAvailable(cameraId: String) {
            activeCameraIds.remove(cameraId)
            isCameraActive = activeCameraIds.isNotEmpty()
            handler.post { updateIslandUi() }
        }
    }

    private val micCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            val nowMic = !configs.isNullOrEmpty()
            val was = isMicActive
            isMicActive = nowMic
            if (nowMic && !was) {
                handler.post {
                    hiddenByFullscreen = false
                    wakeUpIsland(forceAnim = true)
                    updateIslandUi()
                }
            } else {
                handler.post { updateIslandUi() }
            }
        }
    }
    private var lastActivityTime: Long = System.currentTimeMillis()
    private var wasCharging: Boolean = false
    private var chargingPopupUntil: Long = 0L
    private var dualDemoUntil: Long = 0L
    private var satelliteMode: String = ""
    private var satellitePackage: String = ""
    private var satelliteVisible: Boolean = false

    private var cachedAvatarUri: String? = null
    private var cachedAvatarBitmap: Bitmap? = null

    private val handler = Handler(Looper.getMainLooper())
    private val clockTicker = object : Runnable {
        override fun run() {
            if (IslandLiveStore.notifVersion != lastSeenNotifVersion && IslandLiveStore.notifActive) {
                lastSeenNotifVersion = IslandLiveStore.notifVersion
                hiddenByFullscreen = false
                wakeUpIsland(forceAnim = true)
                setExpanded(true)
            }
            pollActiveMediaSessions()
            checkSystemCallState()
            checkChargingTransition()
            evaluateFullscreenAndAutoHide()
            updateIslandUi()
            handler.postDelayed(this, 650L)
        }
    }

    private val appReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_RELOAD_CONFIG -> {
                    settings = IslandConfig.load(this@DynamicIslandService)
                    cachedAvatarUri = null
                    hiddenByFullscreen = false
                    wakeUpIsland(forceAnim = false)
                    applyWindowPosition()
                    updateIslandUi()
                }
                ACTION_TEST_ANIM -> {
                    hideIslandAnimated()
                    handler.postDelayed({ wakeUpIsland(forceAnim = true) }, 700L)
                }
                ACTION_TEST_DUAL -> {
                    dualDemoUntil = System.currentTimeMillis() + 12000L
                    IslandLiveStore.notifActive = true
                    IslandLiveStore.notifPackage = packageName
                    IslandLiveStore.notifAppName = "نشاط مزدوج"
                    IslandLiveStore.notifTitle = "الجزيرة المزدوجة نشطة الآن!"
                    IslandLiveStore.notifText = "اضغط على الدائرة الجانبية للتبديل بين التطبيقين"
                    IslandLiveStore.notifTimestamp = System.currentTimeMillis()
                    setExpanded(false)
                    wakeUpIsland(forceAnim = true)
                    updateIslandUi()
                }
                ACTION_TEST_CHARGE -> {
                    triggerChargingPopup(forceDemo = true)
                }
                ACTION_NEW_NOTIF -> {
                    hiddenByFullscreen = false
                    wakeUpIsland(forceAnim = true)
                    setExpanded(true)
                    updateIslandUi()
                }
                ACTION_CALL_RINGING -> {
                    hiddenByFullscreen = false
                    wakeUpIsland(forceAnim = true)
                    setExpanded(true)
                    updateIslandUi()
                }
                ACTION_CALL_UPDATE -> {
                    hiddenByFullscreen = false
                    wakeUpIsland(forceAnim = true)
                    updateIslandUi()
                }
                ACTION_TEST_PRIVACY -> {
                    privacyDemoUntil = System.currentTimeMillis() + 8000L
                    hiddenByFullscreen = false
                    wakeUpIsland(forceAnim = true)
                    updateIslandUi()
                }
                ACTION_TEST_CALL -> {
                    IslandLiveStore.callActive = true
                    IslandLiveStore.callIsRinging = true
                    IslandLiveStore.callCaller = "أنس محمد (مكالمة واردة)"
                    IslandLiveStore.callPackage = packageName
                    IslandLiveStore.callStartTime = System.currentTimeMillis()
                    IslandLiveStore.callContentIntent = null
                    IslandLiveStore.callEndAction = null
            IslandLiveStore.callAnswerAction = null
            IslandLiveStore.callIsRinging = false
                    hiddenByFullscreen = false
                    wakeUpIsland(forceAnim = true)
                    setExpanded(true)
                    updateIslandUi()
                }
                ACTION_TEST_PING -> {
                    IslandLiveStore.notifActive = true
                    IslandLiveStore.notifPackage = packageName
                    IslandLiveStore.notifAppName = "Dynamic Island"
                    IslandLiveStore.notifTitle = "رسالة تجريبية • أنس محمد"
                    IslandLiveStore.notifText = "رسالة صوتية 🎙️ (0:14) — يمكنك القراءة أو الاستماع مباشرة"
                    IslandLiveStore.notifIsVoice = true
                    IslandLiveStore.notifContentIntent = null
                    IslandLiveStore.notifPlayAction = null
                    IslandLiveStore.notifTimestamp = System.currentTimeMillis()
                    wakeUpIsland(forceAnim = true)
                    setExpanded(true)
                    updateIslandUi()
                }
                ACTION_REFRESH -> {
                    wakeUpIsland(forceAnim = false)
                    checkChargingTransition()
                    updateIslandUi()
                }
            }
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    wasCharging = true
                    triggerChargingPopup(forceDemo = false)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    wasCharging = false
                    chargingPopupUntil = 0L
                    updateIslandUi()
                }
                TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                    val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    @Suppress("DEPRECATION")
                    val num = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    when (stateStr) {
                        TelephonyManager.EXTRA_STATE_RINGING -> {
                            IslandLiveStore.callActive = true
                            IslandLiveStore.callIsRinging = true
                            if (!num.isNullOrBlank()) IslandLiveStore.callCaller = num
                            else if (IslandLiveStore.callCaller.isBlank()) IslandLiveStore.callCaller = "مكالمة واردة"
                            hiddenByFullscreen = false
                            wakeUpIsland(forceAnim = true)
                            setExpanded(true)
                            updateIslandUi()
                        }
                        TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                            IslandLiveStore.callActive = true
                            if (IslandLiveStore.callIsRinging || IslandLiveStore.callStartTime == 0L) {
                                IslandLiveStore.callIsRinging = false
                                IslandLiveStore.callStartTime = System.currentTimeMillis()
                            }
                            hiddenByFullscreen = false
                            wakeUpIsland(forceAnim = true)
                            updateIslandUi()
                        }
                        TelephonyManager.EXTRA_STATE_IDLE -> {
                            IslandLiveStore.callActive = false
                            IslandLiveStore.callIsRinging = false
                            IslandLiveStore.callNotifKey = ""
                            IslandLiveStore.callAnswerAction = null
                            IslandLiveStore.callEndAction = null
                            IslandLiveStore.callContentIntent = null
                            setExpanded(false)
                            updateIslandUi()
                        }
                    }
                }
                Intent.ACTION_BATTERY_CHANGED,
                BluetoothAdapter.ACTION_STATE_CHANGED,
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    checkChargingTransition()
                    updateIslandUi()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        evaluateFullscreenAndAutoHide()
    }

    override fun onCreate() {
        super.onCreate()
        settings = IslandConfig.load(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        wasCharging = getBatteryDetails().isCharging
        lastActivityTime = System.currentTimeMillis()

        try {
            val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIF_ID, buildForegroundNotification(), fgType)
        } catch (_: Exception) {
            try {
                startForeground(NOTIF_ID, buildForegroundNotification())
            } catch (_: Exception) {
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            isRunning = false
            Toast.makeText(this, "فعّل إذن الظهور فوق التطبيقات أولًا", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        attachFullscreenProbe()
        if (!attachOverlayView()) {
            isRunning = false
            stopSelf()
            return
        }

        isRunning = true

        val appFilter = IntentFilter().apply {
            addAction(ACTION_REFRESH)
            addAction(ACTION_RELOAD_CONFIG)
            addAction(ACTION_TEST_PING)
            addAction(ACTION_TEST_ANIM)
            addAction(ACTION_TEST_DUAL)
            addAction(ACTION_TEST_CHARGE)
            addAction(ACTION_NEW_NOTIF)
            addAction(ACTION_CALL_UPDATE)
            addAction(ACTION_CALL_RINGING)
            addAction(ACTION_TEST_CALL)
            addAction(ACTION_TEST_PRIVACY)
        }
        val sysFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appReceiver, appFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(systemReceiver, sysFilter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(appReceiver, appFilter)
            @Suppress("DEPRECATION")
            registerReceiver(systemReceiver, sysFilter)
        }

        registerPrivacySensors()
        tryRebindNotificationListener()
        lastSeenNotifVersion = IslandLiveStore.notifVersion
        wakeUpIsland(forceAnim = true)
        handler.post(clockTicker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        settings = IslandConfig.load(this)
        if (rootView == null && Settings.canDrawOverlays(this)) {
            attachFullscreenProbe()
            if (attachOverlayView()) {
                isRunning = true
            }
        }
        wakeUpIsland(forceAnim = false)
        applyWindowPosition()
        pollActiveMediaSessions()
        updateIslandUi()
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(clockTicker)
        unregisterPrivacySensors()
        try { unregisterReceiver(appReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(systemReceiver) } catch (_: Exception) {}
        rootView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        fullscreenProbeView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        rootView = null
        fullscreenProbeView = null
        super.onDestroy()
    }

    // 1px invisible edge probe WITHOUT FLAG_LAYOUT_IN_SCREEN:
    // Normal apps place it below status bar (loc[1] > 0); Fullscreen/Immersive apps shift it to loc[1] == 0!
    private fun attachFullscreenProbe() {
        if (fullscreenProbeView != null) return
        try {
            val probe = View(this)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val lp = WindowManager.LayoutParams(
                1,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            windowManager.addView(probe, lp)
            fullscreenProbeView = probe
        } catch (_: Exception) {
            fullscreenProbeView = null
        }
    }

    private fun isAppInFullscreen(): Boolean {
        if (!settings.hideInFullscreen) {
            fullscreenStreak = 0
            return false
        }
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            fullscreenStreak = 3
            return true
        }
        val probe = fullscreenProbeView ?: return false
        if (probe.height <= 0 || !probe.isAttachedToWindow) return false
        val loc = IntArray(2)
        probe.getLocationOnScreen(loc)
        val screenH = resources.displayMetrics.heightPixels
        val rawFull = (loc[1] == 0 && probe.height >= screenH - dpToPx(24))
        if (rawFull) fullscreenStreak++ else fullscreenStreak = 0
        return fullscreenStreak >= 2
    }

    // Juicy iPhone-style Elastic Pop-In Animation
    private fun wakeUpIsland(forceAnim: Boolean) {
        if (hiddenByFullscreen && !forceAnim) return
        lastActivityTime = System.currentTimeMillis()
        val container = rootView?.findViewById<View>(R.id.islandContainer) ?: return
        if (isIslandHidden || forceAnim) {
            isIslandHidden = false
            container.animate().cancel()
            container.visibility = View.VISIBLE
            container.alpha = 0f
            container.translationY = -dpToPx(38).toFloat()
            container.scaleX = 0.34f
            container.scaleY = 0.45f
            container.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(460L)
                .setInterpolator(OvershootInterpolator(1.35f))
                .start()
        }
    }

    // Juicy iPhone-style Elastic Pop-Out Animation
    private fun hideIslandAnimated() {
        val container = rootView?.findViewById<View>(R.id.islandContainer) ?: return
        if (isIslandHidden) return
        isIslandHidden = true
        isExpanded = false
        container.animate().cancel()
        container.animate()
            .alpha(0f)
            .translationY(-dpToPx(40).toFloat())
            .scaleX(0.30f)
            .scaleY(0.42f)
            .setDuration(340L)
            .setInterpolator(AnticipateInterpolator(1.2f))
            .withEndAction {
                if (isIslandHidden) container.visibility = View.GONE
            }
            .start()
    }

    private fun evaluateFullscreenAndAutoHide() {
        if (isAppInFullscreen()) {
            if (!hiddenByFullscreen) {
                hiddenByFullscreen = true
                hideIslandAnimated()
            }
            return
        } else if (hiddenByFullscreen) {
            hiddenByFullscreen = false
            wakeUpIsland(forceAnim = true)
            return
        }

        if (!settings.autoHideEnabled || settings.autoHideSeconds <= 0) {
            if (isIslandHidden) wakeUpIsland(forceAnim = true)
            return
        }

        val now = System.currentTimeMillis()
        val hasRecentNotif = IslandLiveStore.notifActive &&
            (settings.keepNotifUntilRead || (now - IslandLiveStore.notifTimestamp < 7000L))
        val hasCharging = now < chargingPopupUntil
        val hasDualDemo = now < dualDemoUntil
        val hasPrivacy = isCameraActive || isMicActive || (now < privacyDemoUntil)
        val isBusy = isExpanded || IslandLiveStore.callActive || IslandLiveStore.mediaPlaying || hasRecentNotif || hasCharging || hasDualDemo || hasPrivacy

        if (isBusy) {
            wakeUpIsland(forceAnim = false)
        } else {
            val waitMs = settings.autoHideSeconds.coerceIn(5, 300) * 1000L
            if (!isIslandHidden && (now - lastActivityTime >= waitMs)) {
                hideIslandAnimated()
            }
        }
    }

    private fun triggerChargingPopup(forceDemo: Boolean) {
        chargingPopupUntil = System.currentTimeMillis() + 7000L
        if (!hiddenByFullscreen || forceDemo) {
            hiddenByFullscreen = false
            wakeUpIsland(forceAnim = true)
            setExpanded(true)
            updateIslandUi()
            handler.postDelayed({
                if (System.currentTimeMillis() >= chargingPopupUntil && isExpanded) {
                    setExpanded(false)
                }
            }, 7100L)
        }
    }

    private fun registerPrivacySensors() {
        try {
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            cameraManager?.registerAvailabilityCallback(cameraCallback, handler)
        } catch (_: Exception) {
        }
        try {
            audioManagerRef = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManagerRef?.registerAudioRecordingCallback(micCallback, handler)
            isMicActive = !(audioManagerRef?.activeRecordingConfigurations.isNullOrEmpty())
        } catch (_: Exception) {
        }
    }

    private fun unregisterPrivacySensors() {
        try { cameraManager?.unregisterAvailabilityCallback(cameraCallback) } catch (_: Exception) {}
        try { audioManagerRef?.unregisterAudioRecordingCallback(micCallback) } catch (_: Exception) {}
    }

    private fun tryRebindNotificationListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                if (NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)) {
                    NotificationListenerService.requestRebind(
                        ComponentName(this, IslandNotificationListener::class.java)
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun getForegroundAppPackage(): String {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return ""
            val end = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 60_000L, end)
            if (stats.isNullOrEmpty()) return ""
            val recent = stats
                .filter { it.packageName != packageName && !it.packageName.contains("launcher") && !it.packageName.contains("systemui") }
                .maxByOrNull { it.lastTimeUsed }
            recent?.packageName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun isBluetoothOn(): Boolean {
        return try {
            val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bm?.adapter?.isEnabled == true
        } catch (_: Exception) {
            false
        }
    }

    private fun isSilentOrVibrate(): Boolean {
        return try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode != AudioManager.RINGER_MODE_NORMAL
        } catch (_: Exception) {
            false
        }
    }

    private fun checkChargingTransition() {
        val batt = getBatteryDetails()
        if (batt.isCharging && !wasCharging) {
            wasCharging = true
            triggerChargingPopup(forceDemo = false)
        } else if (!batt.isCharging && wasCharging) {
            wasCharging = false
            chargingPopupUntil = 0L
        }
    }

    private fun checkSystemCallState() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val isRingingMode = am.mode == AudioManager.MODE_RINGTONE
            val inVoiceCall = am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION
            if (isRingingMode) {
                val newlyRinging = !IslandLiveStore.callActive || !IslandLiveStore.callIsRinging
                IslandLiveStore.callActive = true
                IslandLiveStore.callIsRinging = true
                if (IslandLiveStore.callCaller.isBlank()) IslandLiveStore.callCaller = "مكالمة واردة"
                if (newlyRinging) {
                    hiddenByFullscreen = false
                    wakeUpIsland(forceAnim = true)
                    setExpanded(true)
                }
            } else if (inVoiceCall) {
                if (!IslandLiveStore.callActive || IslandLiveStore.callIsRinging) {
                    IslandLiveStore.callActive = true
                    IslandLiveStore.callIsRinging = false
                    if (IslandLiveStore.callCaller.isBlank() || IslandLiveStore.callCaller == "مكالمة واردة") {
                        IslandLiveStore.callCaller = "مكالمة جارية"
                    }
                    IslandLiveStore.callStartTime = System.currentTimeMillis()
                    hiddenByFullscreen = false
                    wakeUpIsland(forceAnim = true)
                }
            } else if (IslandLiveStore.callActive &&
                IslandLiveStore.callNotifKey.isBlank() && IslandLiveStore.callPackage != packageName
            ) {
                IslandLiveStore.callActive = false
                IslandLiveStore.callIsRinging = false
            }
        } catch (_: Exception) {
        }
    }

    private fun formatCallDuration(startMs: Long): String {
        val secTotal = ((System.currentTimeMillis() - startMs).coerceAtLeast(0L) / 1000L)
        val h = secTotal / 3600L
        val m = (secTotal % 3600L) / 60L
        val s = secTotal % 60L
        return if (h > 0) String.format(Locale.ENGLISH, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ENGLISH, "%02d:%02d", m, s)
    }

    private fun handleMediaControl(action: String) {
        wakeUpIsland(forceAnim = false)
        pollActiveMediaSessions()
        val ctrl = IslandLiveStore.activeController

        when (action) {
            "prev" -> {
                var handled = false
                try {
                    if (ctrl != null) { ctrl.transportControls.skipToPrevious(); handled = true }
                } catch (_: Exception) {}
                if (!handled) sendUniversalMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            "next" -> {
                var handled = false
                try {
                    if (ctrl != null) { ctrl.transportControls.skipToNext(); handled = true }
                } catch (_: Exception) {}
                if (!handled) sendUniversalMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            "toggle" -> {
                val wantPause = IslandLiveStore.mediaPlaying
                var handled = false
                try {
                    if (ctrl != null) {
                        if (wantPause) ctrl.transportControls.pause()
                        else ctrl.transportControls.play()
                        handled = true
                    }
                } catch (_: Exception) {}
                if (!handled) {
                    sendUniversalMediaKey(if (wantPause) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY)
                    sendUniversalMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                }
                IslandLiveStore.mediaPlaying = !wantPause
                updateIslandUi()
            }
        }
    }

    private fun sendUniversalMediaKey(keyCode: Int) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val now = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        } catch (_: Exception) {
        }
    }

    private fun clearMediaStore() {
        IslandLiveStore.activeController = null
        IslandLiveStore.mediaActive = false
        IslandLiveStore.mediaPlaying = false
        IslandLiveStore.mediaTitle = ""
        IslandLiveStore.mediaArtist = ""
        IslandLiveStore.mediaPackage = ""
        IslandLiveStore.mediaArt = null
        IslandLiveStore.secondaryMediaActive = false
        IslandLiveStore.secondaryMediaPackage = ""
        IslandLiveStore.secondaryMediaTitle = ""
        mediaPausedSince = 0L
    }

    private fun pollActiveMediaSessions() {
        val now = System.currentTimeMillis()
        val hasNotifPerm = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        if (hasNotifPerm) {
            try {
                val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val comp = ComponentName(this, IslandNotificationListener::class.java)
                val sessions = msm.getActiveSessions(comp)
                if (!sessions.isNullOrEmpty()) {
                    val amCheck = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val playingSession = sessions.firstOrNull {
                        val st = it.playbackState?.state
                        st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
                    }
                    val best = playingSession ?: if (!amCheck.isMusicActive) {
                        sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
                    } else {
                        null
                    }

                    if (best != null) {
                        val st = best.playbackState?.state ?: PlaybackState.STATE_NONE
                        val isNowPlaying = (st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING)

                        // Auto-clear stale PAUSED music sessions after 12 seconds so closed music apps NEVER stay stuck!
                        if (!isNowPlaying) {
                            if (mediaPausedSince == 0L) mediaPausedSince = now
                            if (now - mediaPausedSince >= 12_000L) {
                                clearMediaStore()
                                return
                            }
                        } else {
                            mediaPausedSince = 0L
                        }

                        val meta = best.metadata
                        val pkg = best.packageName ?: ""
                        val appLabel = getFriendlyAppName(pkg)
                        val wasPlaying = IslandLiveStore.mediaPlaying
                        wasFallbackAudioPlaying = false
                        IslandLiveStore.activeController = best
                        IslandLiveStore.mediaActive = true
                        IslandLiveStore.mediaPlaying = isNowPlaying
                        if (pkg.isNotBlank()) {
                            IslandLiveStore.mediaPackage = pkg
                            IslandLiveStore.lastNotifMediaPackage = pkg
                        }
                        IslandLiveStore.mediaTitle = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                            ?.takeIf { it.isNotBlank() } ?: appLabel
                        IslandLiveStore.mediaArtist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                            ?.takeIf { it.isNotBlank() }
                            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.takeIf { it.isNotBlank() }
                            ?: appLabel
                        IslandLiveStore.mediaArt = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)

                        val second = sessions.firstOrNull {
                            val sst = it.playbackState?.state
                            it.packageName != pkg && !it.packageName.isNullOrBlank() &&
                                (sst == PlaybackState.STATE_PLAYING || sst == PlaybackState.STATE_BUFFERING)
                        }
                        if (second != null) {
                            IslandLiveStore.secondaryMediaActive = true
                            IslandLiveStore.secondaryMediaPackage = second.packageName ?: ""
                            IslandLiveStore.secondaryMediaTitle = second.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                                ?.takeIf { it.isNotBlank() } ?: getFriendlyAppName(second.packageName)
                        } else {
                            IslandLiveStore.secondaryMediaActive = false
                            IslandLiveStore.secondaryMediaPackage = ""
                            IslandLiveStore.secondaryMediaTitle = ""
                        }

                        if (!wasPlaying && isNowPlaying && !hiddenByFullscreen && isIslandHidden) {
                            wakeUpIsland(forceAnim = true)
                        }
                        return
                    }
                }
            } catch (_: Exception) {
            }
        }

        // No active/valid MediaSession found -> check AudioManager, else CLEAR completely so nothing stays stuck!
        IslandLiveStore.secondaryMediaActive = false
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.isMusicActive) {
                mediaPausedSince = 0L
                IslandLiveStore.activeController = null
                IslandLiveStore.mediaActive = true
                IslandLiveStore.mediaPlaying = true
                val fgPkg = getForegroundAppPackage()
                val fallbackPkg = when {
                    fgPkg.isNotBlank() -> fgPkg
                    IslandLiveStore.lastNotifMediaPackage.isNotBlank() -> IslandLiveStore.lastNotifMediaPackage
                    else -> IslandLiveStore.mediaPackage
                }
                if (fallbackPkg.isNotBlank()) {
                    IslandLiveStore.mediaPackage = fallbackPkg
                    IslandLiveStore.mediaTitle = getFriendlyAppName(fallbackPkg)
                } else {
                    IslandLiveStore.mediaTitle = "فيديو / صوت قيد التشغيل"
                }
                IslandLiveStore.mediaArtist = "اضغط للتحكم بالتشغيل"
                if (!wasFallbackAudioPlaying) {
                    wasFallbackAudioPlaying = true
                    if (!hiddenByFullscreen && isIslandHidden) wakeUpIsland(forceAnim = true)
                }
            } else {
                wasFallbackAudioPlaying = false
                clearMediaStore()
            }
        } catch (_: Exception) {
            clearMediaStore()
        }
    }

    private fun getFriendlyAppName(pkg: String?): String {
        if (pkg.isNullOrBlank()) return "وسائط"
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    private fun buildForegroundNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Dynamic Island",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_island_notif)
            .setContentTitle("Dynamic Island (v${BuildConfig.VERSION_NAME})")
            .setContentText("الجزيرة العائمة تعمل الآن")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun setExpanded(expand: Boolean) {
        if (isExpanded == expand) return
        isExpanded = expand
        lastActivityTime = System.currentTimeMillis()
        val container = rootView?.findViewById<ViewGroup>(R.id.islandContainer)
        if (container != null) {
            val transition = AutoTransition().apply { duration = 220 }
            TransitionManager.beginDelayedTransition(container, transition)
        }
        applyWindowPosition()
        updateIslandUi()
    }

    private fun attachOverlayView(): Boolean {
        return try {
            val themedCtx = ContextThemeWrapper(this, R.style.Theme_DynamicIsland)
            val view = LayoutInflater.from(themedCtx).inflate(R.layout.layout_dynamic_island, null)
            rootView = view

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS



            val lp = WindowManager.LayoutParams(
                if (settings.coverStatusBar) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = if (settings.coverStatusBar) 0 else dpToPx(settings.topOffsetDp)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }

            }

            layoutParams = lp
            windowManager.addView(view, lp)

            clipRounded(view.findViewById(R.id.ivCompactIcon), 999f)
            clipRounded(view.findViewById(R.id.ivExpandedIcon), dpToPx(16).toFloat())
            clipRounded(view.findViewById(R.id.ivSatelliteIcon), 999f)

            view.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE && isExpanded) {
                    if (IslandLiveStore.notifActive && settings.keepNotifUntilRead) {
                        IslandLiveStore.notifActive = false
                    }
                    setExpanded(false)
                    return@setOnTouchListener true
                }
                false
            }

            val pill = view.findViewById<View>(R.id.islandPill)
            pill.setOnClickListener {
                lastActivityTime = System.currentTimeMillis()
                setExpanded(!isExpanded)
            }

            val satBubble = view.findViewById<View>(R.id.satelliteBubble)
            satBubble.setOnClickListener {
                lastActivityTime = System.currentTimeMillis()
                when (satelliteMode) {
                    "charge" -> triggerChargingPopup(forceDemo = true)
                    "app", "notif", "media2" -> {
                        if (satellitePackage.isNotBlank() && satellitePackage != packageName) {
                            try {
                                val launch = packageManager.getLaunchIntentForPackage(satellitePackage)
                                if (launch != null) {
                                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(launch)
                                } else {
                                    setExpanded(true)
                                }
                            } catch (_: Exception) {
                                setExpanded(true)
                            }
                        } else {
                            setExpanded(true)
                        }
                    }
                    else -> setExpanded(true)
                }
            }

            view.findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { handleMediaControl("prev") }
            view.findViewById<ImageButton>(R.id.btnPlayPause).setOnClickListener { handleMediaControl("toggle") }
            view.findViewById<ImageButton>(R.id.btnNext).setOnClickListener { handleMediaControl("next") }
            view.findViewById<TextView>(R.id.btnNotifListen).setOnClickListener {
                val playAct = IslandLiveStore.notifPlayAction
                val openAct = IslandLiveStore.notifContentIntent
                var triggered = false
                try {
                    if (playAct != null) {
                        playAct.send()
                        triggered = true
                    } else if (openAct != null) {
                        openAct.send()
                        triggered = true
                    }
                } catch (_: Exception) {
                }
                if (!triggered && IslandLiveStore.notifPackage.isNotBlank()) {
                    try {
                        val launch = packageManager.getLaunchIntentForPackage(IslandLiveStore.notifPackage)
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launch)
                        }
                    } catch (_: Exception) {
                    }
                }
                IslandLiveStore.notifActive = false
                setExpanded(false)
                updateIslandUi()
            }
            view.findViewById<TextView>(R.id.btnNotifOpen).setOnClickListener {
                val openAct = IslandLiveStore.notifContentIntent
                var opened = false
                try {
                    if (openAct != null) {
                        openAct.send()
                        opened = true
                    }
                } catch (_: Exception) {
                }
                if (!opened && IslandLiveStore.notifPackage.isNotBlank()) {
                    try {
                        val launch = packageManager.getLaunchIntentForPackage(IslandLiveStore.notifPackage)
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launch)
                        }
                    } catch (_: Exception) {
                    }
                }
                IslandLiveStore.notifActive = false
                setExpanded(false)
                updateIslandUi()
            }
            view.findViewById<TextView>(R.id.btnNotifDismiss).setOnClickListener {
                IslandLiveStore.notifActive = false
                setExpanded(false)
                updateIslandUi()
            }
            view.findViewById<TextView>(R.id.btnCallOpen).setOnClickListener {
                if (IslandLiveStore.callIsRinging) {
                    var answered = false
                    try {
                        IslandLiveStore.callAnswerAction?.let {
                            it.send()
                            answered = true
                        }
                    } catch (_: Exception) {
                    }
                    if (!answered && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                            @Suppress("DEPRECATION")
                            tm.acceptRingingCall()
                            answered = true
                        } catch (_: Exception) {
                        }
                    }
                    IslandLiveStore.callIsRinging = false
                    IslandLiveStore.callStartTime = System.currentTimeMillis()
                    if (IslandLiveStore.callCaller.contains("واردة")) {
                        IslandLiveStore.callCaller = IslandLiveStore.callCaller.replace("واردة", "جارية")
                    }
                    updateIslandUi()
                    return@setOnClickListener
                }
                var opened = false
                try {
                    IslandLiveStore.callContentIntent?.let {
                        it.send()
                        opened = true
                    }
                } catch (_: Exception) {
                }
                if (!opened && IslandLiveStore.callPackage.isNotBlank() && IslandLiveStore.callPackage != packageName) {
                    try {
                        val launch = packageManager.getLaunchIntentForPackage(IslandLiveStore.callPackage)
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launch)
                            opened = true
                        }
                    } catch (_: Exception) {
                    }
                }
                if (!opened) {
                    try {
                        val dial = Intent(Intent.ACTION_DIAL).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        startActivity(dial)
                    } catch (_: Exception) {
                    }
                }
                setExpanded(false)
            }
            view.findViewById<TextView>(R.id.btnCallEnd).setOnClickListener {
                var ended = false
                try {
                    IslandLiveStore.callEndAction?.let {
                        it.send()
                        ended = true
                    }
                } catch (_: Exception) {
                }
                if (!ended && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                        @Suppress("DEPRECATION")
                        tm.endCall()
                        ended = true
                    } catch (_: Exception) {
                    }
                }
                IslandLiveStore.callActive = false
                IslandLiveStore.callNotifKey = ""
                IslandLiveStore.callContentIntent = null
                IslandLiveStore.callEndAction = null
            IslandLiveStore.callAnswerAction = null
            IslandLiveStore.callIsRinging = false
                setExpanded(false)
                updateIslandUi()
            }
            view.findViewById<TextView>(R.id.btnOpenSettings).setOnClickListener {
                setExpanded(false)
                val i = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(i)
            }
            true
        } catch (e: Exception) {
            rootView = null
            Toast.makeText(
                this,
                "خطأ في إظهار الجزيرة: ${e.javaClass.simpleName}: ${e.localizedMessage ?: ""}",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    private fun clipRounded(v: View, radiusPx: Float) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val r = if (radiusPx > 500f) view.width / 2f else radiusPx
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        v.clipToOutline = true
    }

    private fun applyWindowPosition() {
        val v = rootView ?: return
        val lp = layoutParams ?: return
        lp.width = if (settings.coverStatusBar && !isExpanded) {
            WindowManager.LayoutParams.MATCH_PARENT
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }
        lp.y = if (settings.coverStatusBar && !isExpanded) 0 else dpToPx(settings.topOffsetDp)

        try {
            windowManager.updateViewLayout(v, lp)
        } catch (_: Exception) {
        }
    }

    private fun getNetworkStatus(): Pair<Boolean, Boolean> {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return Pair(false, false)
            val caps = cm.getNetworkCapabilities(net) ?: return Pair(false, false)
            Pair(
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            )
        } catch (_: Exception) {
            Pair(false, false)
        }
    }

    // Triple-redundant Xiaomi/MIUI & Universal Fast Charging Detection + Real Live Wattage
    private fun getBatteryDetails(): BatteryDetails {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val sticky = try {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }

        val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pctFromIntent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val pct = if (pctFromIntent in 0..100) pctFromIntent
        else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)

        val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = bm.isCharging ||
            plugged != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            (status == BatteryManager.BATTERY_STATUS_FULL && plugged != 0)

        val voltageMv = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val rawCurrent = try {
            abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
        } catch (_: Exception) {
            0
        }
        val currentMa = if (rawCurrent > 10000) rawCurrent / 1000.0 else rawCurrent.toDouble()
        val watts = if (charging && voltageMv > 0 && currentMa > 100.0) {
            ((voltageMv / 1000.0) * (currentMa / 1000.0)).toInt().coerceIn(1, 120)
        } else {
            0
        }
        val isFast = charging && (watts >= 12 || plugged == BatteryManager.BATTERY_PLUGGED_AC)
        return BatteryDetails(pct, charging, watts, isFast)
    }

    // Crisp anti-aliased center-square high-res bitmap decoder (ZERO pixelation / blur!)
    private fun getCrispAvatarBitmap(): Bitmap? {
        val uriStr = settings.avatarUri
        if (uriStr.isNullOrBlank()) return null
        if (uriStr == cachedAvatarUri && cachedAvatarBitmap != null) return cachedAvatarBitmap
        return try {
            val uri = Uri.parse(uriStr)
            val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, optsBounds) }
            val maxDim = maxOf(optsBounds.outWidth, optsBounds.outHeight)
            var sample = 1
            while (maxDim / sample > 512) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            val side = minOf(raw.width, raw.height)
            val x = (raw.width - side) / 2
            val y = (raw.height - side) / 2
            val cropped = Bitmap.createBitmap(raw, x, y, side, side)
            val crisp = Bitmap.createScaledBitmap(cropped, 256, 256, true)
            cachedAvatarUri = uriStr
            cachedAvatarBitmap = crisp
            crisp
        } catch (_: Exception) {
            null
        }
    }

    private fun buildSurfaceDrawable(isCircle: Boolean, strokeColor: Int, isCoverBar: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            if (isCircle) {
                cornerRadius = dpToPx(99).toFloat()
            } else if (isCoverBar) {
                val r = dpToPx(14).toFloat()
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            } else {
                cornerRadius = dpToPx(settings.cornerRadiusDp).toFloat()
            }
            if (settings.glassEffectEnabled) {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = IslandConfig.themeGlassColors(settings.themeId, settings.glassOpacityPercent)
            } else {
                setColor(IslandConfig.themeSolidColor(settings.themeId))
            }
            setStroke(dpToPx(1), strokeColor)
        }
    }

    private fun updateIslandUi() {
        val view = rootView ?: return
        val pill = view.findViewById<LinearLayout>(R.id.islandPill)
        val satelliteBubble = view.findViewById<FrameLayout>(R.id.satelliteBubble)
        val ivSatelliteIcon = view.findViewById<ImageView>(R.id.ivSatelliteIcon)
        val compactRow = view.findViewById<LinearLayout>(R.id.compactRow)
        val expandedBox = view.findViewById<LinearLayout>(R.id.expandedBox)
        val mediaControls = view.findViewById<LinearLayout>(R.id.mediaControlsRow)
        val notifActionsRow = view.findViewById<LinearLayout>(R.id.notifActionsRow)
        val btnNotifListen = view.findViewById<TextView>(R.id.btnNotifListen)
        val callActionsRow = view.findViewById<LinearLayout>(R.id.callActionsRow)
        val compactVortex = view.findViewById<ChargingVortexBatteryView>(R.id.compactVortexBattery)
        val expandedVortex = view.findViewById<ChargingVortexBatteryView>(R.id.expandedVortexBattery)
        val notchCenterGap = view.findViewById<Space>(R.id.notchCenterGap)

        val ivCamActive = view.findViewById<ImageView>(R.id.ivCamActive)
        val ivMicActive = view.findViewById<ImageView>(R.id.ivMicActive)
        val ivBluetooth = view.findViewById<ImageView>(R.id.ivBluetooth)
        val ivSilent = view.findViewById<ImageView>(R.id.ivSilent)
        val ivWifi = view.findViewById<ImageView>(R.id.ivWifi)
        val ivSignal = view.findViewById<ImageView>(R.id.ivSignal)
        val tvBatteryLeft = view.findViewById<TextView>(R.id.tvBatteryLeft)
        val tvCompactLeftStatus = view.findViewById<TextView>(R.id.tvCompactLeftStatus)
        val tvCompactRightName = view.findViewById<TextView>(R.id.tvCompactRightName)
        val ivCompactIcon = view.findViewById<ImageView>(R.id.ivCompactIcon)

        val ivExpandedIcon = view.findViewById<ImageView>(R.id.ivExpandedIcon)
        val tvExpandedTitle = view.findViewById<TextView>(R.id.tvExpandedTitle)
        val tvExpandedSub = view.findViewById<TextView>(R.id.tvExpandedSub)
        val tvExpandedMeta = view.findViewById<TextView>(R.id.tvExpandedMeta)
        val btnPlayPause = view.findViewById<ImageButton>(R.id.btnPlayPause)

        val (hasWifi, hasCell) = getNetworkStatus()
        val batt = getBatteryDetails()
        val batteryPct = batt.pct
        val isCharging = batt.isCharging
        val btOn = isBluetoothOn()
        val silentOn = isSilentOrVibrate()
        val nowMs = System.currentTimeMillis()
        val isChargingAlert = nowMs < chargingPopupUntil
        val isDualDemo = nowMs < dualDemoUntil

        val isLight = IslandConfig.isLightTheme(settings.themeId)
        val strokeColor = when {
            IslandLiveStore.callActive || isChargingAlert || isCharging -> Color.argb(200, 52, 211, 153)
            settings.glassEffectEnabled && isLight -> Color.argb(150, 255, 255, 255)
            settings.glassEffectEnabled -> Color.argb(95, 255, 255, 255)
            isLight -> Color.rgb(203, 213, 225)
            else -> Color.rgb(30, 41, 59)
        }
        val primaryText = if (isLight) Color.rgb(15, 23, 42) else Color.WHITE
        val mutedText = if (isLight) Color.rgb(71, 85, 105) else Color.rgb(190, 204, 224)

        val isCoverBar = settings.coverStatusBar && !isExpanded
        pill.background = buildSurfaceDrawable(isCircle = false, strokeColor = strokeColor, isCoverBar = isCoverBar)

        val showNotif = IslandLiveStore.notifActive &&
            (settings.keepNotifUntilRead || (nowMs - IslandLiveStore.notifTimestamp < 7000L))

        // Determine Dual-Activity Satellite Bubble (iPhone Split Pill + Detached Circle)
        var wantSat = false
        var satMode = ""
        var satPkg = ""
        var satStroke = strokeColor
        if (settings.dualActivityEnabled && !isExpanded && !isCoverBar) {
            when {
                isDualDemo -> {
                    wantSat = true
                    satMode = "charge"
                    satStroke = Color.argb(210, 52, 211, 153)
                    ivSatelliteIcon.setImageResource(R.drawable.ic_charging_bolt)
                    ivSatelliteIcon.clearColorFilter()
                }
                IslandLiveStore.mediaActive && showNotif -> {
                    wantSat = true
                    satMode = "media2"
                    satPkg = IslandLiveStore.mediaPackage
                    val mIcon = loadAppIconBitmap(IslandLiveStore.mediaPackage)
                    if (mIcon != null) ivSatelliteIcon.setImageBitmap(mIcon)
                    else ivSatelliteIcon.setImageResource(R.drawable.app_logo)
                    ivSatelliteIcon.clearColorFilter()
                }
                IslandLiveStore.mediaActive && isCharging -> {
                    wantSat = true
                    satMode = "charge"
                    satStroke = Color.argb(210, 52, 211, 153)
                    ivSatelliteIcon.setImageResource(R.drawable.ic_charging_bolt)
                    ivSatelliteIcon.clearColorFilter()
                }
                IslandLiveStore.mediaActive && IslandLiveStore.secondaryMediaActive -> {
                    wantSat = true
                    satMode = "media2"
                    satPkg = IslandLiveStore.secondaryMediaPackage
                    val sIcon = loadAppIconBitmap(satPkg)
                    if (sIcon != null) ivSatelliteIcon.setImageBitmap(sIcon)
                    else ivSatelliteIcon.setImageResource(R.drawable.app_logo)
                    ivSatelliteIcon.clearColorFilter()
                }
                showNotif && isCharging -> {
                    wantSat = true
                    satMode = "charge"
                    satStroke = Color.argb(210, 52, 211, 153)
                    ivSatelliteIcon.setImageResource(R.drawable.ic_charging_bolt)
                    ivSatelliteIcon.clearColorFilter()
                }
                IslandLiveStore.mediaActive && btOn -> {
                    wantSat = true
                    satMode = "bt"
                    satStroke = Color.argb(180, 96, 165, 250)
                    ivSatelliteIcon.setImageResource(R.drawable.ic_bluetooth_mini)
                    ivSatelliteIcon.setColorFilter(Color.rgb(96, 165, 250))
                }
            }
        }
        satelliteMode = satMode
        satellitePackage = satPkg
        if (wantSat) {
            satelliteBubble.background = buildSurfaceDrawable(isCircle = true, strokeColor = satStroke, isCoverBar = false)
            if (!satelliteVisible) {
                satelliteVisible = true
                satelliteBubble.visibility = View.VISIBLE
                satelliteBubble.scaleX = 0.25f
                satelliteBubble.scaleY = 0.25f
                satelliteBubble.alpha = 0f
                satelliteBubble.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(360L)
                    .setInterpolator(OvershootInterpolator(1.4f))
                    .start()
            }
        } else if (satelliteVisible) {
            satelliteVisible = false
            satelliteBubble.animate().cancel()
            satelliteBubble.visibility = View.GONE
        }

        val maxCompactW = settings.compactWidthDp.coerceIn(160, 380)
        val targetWidth = when {
            isExpanded -> dpToPx(340)
            isCoverBar -> ViewGroup.LayoutParams.MATCH_PARENT
            wantSat -> dpToPx((maxCompactW - 48).coerceAtLeast(145))
            else -> dpToPx(maxCompactW)
        }
        val targetMinHeight = if (isCoverBar) {
            dpToPx(settings.barHeightDp.coerceIn(30, 58))
        } else {
            0
        }
        val pParams = pill.layoutParams
        if (pParams.width != targetWidth) {
            pParams.width = targetWidth
            pill.layoutParams = pParams
        }
        pill.minimumHeight = targetMinHeight

        val pad = if (isCoverBar) 0 else dpToPx(4)
        view.setPadding(pad, if (isCoverBar) 0 else pad, pad, pad)

        val gapDp = settings.notchGapDp.coerceIn(22, 90)
        val gapPx = dpToPx(gapDp)
        if (notchCenterGap.layoutParams.width != gapPx) {
            val gp = notchCenterGap.layoutParams
            gp.width = gapPx
            notchCenterGap.layoutParams = gp
        }

        tvCompactLeftStatus.setTextColor(primaryText)
        tvCompactRightName.setTextColor(primaryText)
        tvExpandedTitle.setTextColor(primaryText)
        tvExpandedSub.setTextColor(mutedText)
        tvExpandedMeta.setTextColor(if (isChargingAlert || isCharging) Color.rgb(52, 211, 153) else mutedText)

        val camLive = isCameraActive || (nowMs < privacyDemoUntil)
        val micLive = isMicActive || (nowMs < privacyDemoUntil)
        ivCamActive.visibility = if (camLive) View.VISIBLE else View.GONE
        ivMicActive.visibility = if (micLive) View.VISIBLE else View.GONE
        val showMiniStatus = settings.showStatusIcons && (settings.coverStatusBar || settings.compactWidthDp >= 250)
        ivBluetooth.visibility = if (showMiniStatus && btOn) View.VISIBLE else View.GONE
        ivBluetooth.setColorFilter(Color.rgb(96, 165, 250))

        ivSilent.visibility = if (showMiniStatus && silentOn) View.VISIBLE else View.GONE
        ivSilent.setColorFilter(Color.rgb(251, 191, 36))

        ivWifi.visibility = if (showMiniStatus && hasWifi) View.VISIBLE else View.GONE
        ivWifi.setColorFilter(if (isLight) Color.rgb(2, 132, 199) else Color.rgb(56, 189, 248))

        ivSignal.visibility = if (showMiniStatus && (hasCell || !hasWifi)) View.VISIBLE else View.GONE
        ivSignal.setColorFilter(if (hasCell) (if (isLight) Color.rgb(15, 23, 42) else Color.WHITE) else Color.rgb(100, 116, 139))

        tvBatteryLeft.visibility = if (showMiniStatus) View.VISIBLE else View.GONE
        tvBatteryLeft.text = if (isCharging) "⚡$batteryPct%" else "$batteryPct%"
        tvBatteryLeft.setTextColor(
            when {
                isCharging -> Color.rgb(52, 211, 153)
                batteryPct <= 20 -> Color.rgb(251, 113, 133)
                else -> primaryText
            }
        )

        val now = Date()
        val timeFmt = SimpleDateFormat(if (settings.use24Hour) "HH:mm" else "hh:mm", Locale.ENGLISH)
        val fullTimeFmt = SimpleDateFormat(if (settings.use24Hour) "HH:mm" else "hh:mm a", Locale("ar"))
        val dateFmt = SimpleDateFormat("EEEE، d MMMM", Locale("ar"))
        val wattTag = if (batt.watts > 0) " (${batt.watts}W)" else ""
        val statusSummary = buildString {
            append(if (hasWifi) "Wi-Fi" else if (hasCell) "بيانات" else "بدون نت")
            if (camLive) append(" • 📷 كاميرا نشطة")
            if (micLive) append(" • 🎙️ مايك نشط")
            if (btOn) append(" • بلوتوث")
            if (silentOn) append(" • صامت")
            append(" • ${if (isCharging) "⚡" else ""}$batteryPct%$wattTag")
        }

        compactRow.visibility = if (isExpanded) View.GONE else View.VISIBLE
        expandedBox.visibility = if (isExpanded) View.VISIBLE else View.GONE

        fun showVortex(show: Boolean) {
            if (show) {
                compactVortex.setBatteryPercent(batteryPct)
                expandedVortex.setBatteryPercent(batteryPct)
                compactVortex.visibility = View.VISIBLE
                expandedVortex.visibility = View.VISIBLE
                compactVortex.startVortex()
                expandedVortex.startVortex()
                ivCompactIcon.visibility = View.GONE
                ivExpandedIcon.visibility = View.GONE
            } else {
                compactVortex.stopVortex()
                expandedVortex.stopVortex()
                compactVortex.visibility = View.GONE
                expandedVortex.visibility = View.GONE
                ivCompactIcon.visibility = View.VISIBLE
                ivExpandedIcon.visibility = View.VISIBLE
            }
        }

        when {
            IslandLiveStore.callActive -> {
                showVortex(false)
                val btnCallOpenView = view.findViewById<TextView>(R.id.btnCallOpen)
                val btnCallEndView = view.findViewById<TextView>(R.id.btnCallEnd)
                val callAppIcon = loadAppIconBitmap(IslandLiveStore.callPackage)
                if (callAppIcon != null && IslandLiveStore.callPackage != packageName) {
                    ivCompactIcon.setImageBitmap(callAppIcon)
                    ivExpandedIcon.setImageBitmap(callAppIcon)
                } else {
                    ivCompactIcon.setImageResource(R.drawable.ic_call_live)
                    ivExpandedIcon.setImageResource(R.drawable.ic_call_live)
                }
                if (IslandLiveStore.callIsRinging) {
                    tvCompactRightName.text = IslandLiveStore.callCaller.ifBlank { "مكالمة واردة" }
                    tvCompactLeftStatus.text = "📞 يرن الآن"
                    tvCompactLeftStatus.setTextColor(Color.rgb(52, 211, 153))
                    tvExpandedTitle.text = IslandLiveStore.callCaller.ifBlank { "مكالمة واردة" }
                    tvExpandedSub.text = "مكالمة واردة — اضغط للرد أو الرفض"
                    tvExpandedSub.setTextColor(Color.rgb(52, 211, 153))
                    tvExpandedMeta.text = "يرن الآن 📞 • $statusSummary"
                    btnCallOpenView.text = "📞 رد على المكالمة"
                    btnCallEndView.text = "📵 رفض"
                } else {
                    val callTimer = formatCallDuration(IslandLiveStore.callStartTime)
                    tvCompactRightName.text = IslandLiveStore.callCaller.ifBlank { "مكالمة جارية" }
                    tvCompactLeftStatus.text = "📞 $callTimer"
                    tvCompactLeftStatus.setTextColor(Color.rgb(52, 211, 153))
                    tvExpandedTitle.text = IslandLiveStore.callCaller.ifBlank { "مكالمة جارية" }
                    tvExpandedSub.text = "مدة المكالمة: $callTimer"
                    tvExpandedSub.setTextColor(Color.rgb(52, 211, 153))
                    tvExpandedMeta.text = "مكالمة نشطة • $statusSummary"
                    btnCallOpenView.text = "📞 فتح المكالمة"
                    btnCallEndView.text = "📵 إنهاء المكالمة"
                }
                mediaControls.visibility = View.GONE
                notifActionsRow.visibility = View.GONE
                callActionsRow.visibility = View.VISIBLE
            }

            isChargingAlert -> {
                showVortex(true)
                val chargeTitle = when {
                    batt.watts >= 12 -> "شحن فائق سريع ⚡ ${batt.watts}W"
                    batt.watts > 0 -> "جاري الشحن ⚡ ${batt.watts}W"
                    batt.isFast -> "جاري الشحن السريع ⚡"
                    else -> "جاري الشحن ⚡"
                }
                tvCompactRightName.text = if (batt.watts > 0) "شحن ${batt.watts}W" else "جاري الشحن"
                tvCompactLeftStatus.text = "$batteryPct%"
                tvCompactLeftStatus.setTextColor(Color.rgb(52, 211, 153))
                tvExpandedTitle.text = chargeTitle
                tvExpandedSub.text = if (batteryPct >= 100) "اكتمل الشحن 100% ✅" else "نسبة البطارية: $batteryPct% — جاري التعبئة"
                tvExpandedMeta.text = statusSummary
                mediaControls.visibility = View.GONE
                notifActionsRow.visibility = View.GONE
                callActionsRow.visibility = View.GONE
            }

            showNotif -> {
                showVortex(false)
                callActionsRow.visibility = View.GONE
                val appIcon = loadAppIconBitmap(IslandLiveStore.notifPackage)
                val largeBmp = IslandLiveStore.notifLargeIcon
                if (appIcon != null) {
                    ivCompactIcon.setImageBitmap(appIcon)
                } else if (largeBmp != null) {
                    ivCompactIcon.setImageBitmap(largeBmp)
                } else {
                    ivCompactIcon.setImageResource(R.drawable.app_logo)
                }
                if (largeBmp != null) {
                    ivExpandedIcon.setImageBitmap(largeBmp)
                } else if (appIcon != null) {
                    ivExpandedIcon.setImageBitmap(appIcon)
                } else {
                    ivExpandedIcon.setImageResource(R.drawable.app_logo)
                }
                tvCompactRightName.text = IslandLiveStore.notifAppName
                tvCompactLeftStatus.text = if (IslandLiveStore.notifIsVoice) "🎙️ فويس" else "رسالة"
                tvExpandedTitle.text = IslandLiveStore.notifTitle.ifBlank { IslandLiveStore.notifAppName }
                tvExpandedSub.text = IslandLiveStore.notifText
                tvExpandedMeta.text = if (IslandLiveStore.notifIsVoice) {
                    "${IslandLiveStore.notifAppName} • رسالة صوتية 🎙️"
                } else {
                    "${IslandLiveStore.notifAppName} • $statusSummary"
                }
                mediaControls.visibility = View.GONE
                notifActionsRow.visibility = View.VISIBLE
                btnNotifListen.visibility = if (IslandLiveStore.notifIsVoice) View.VISIBLE else View.GONE
            }

            IslandLiveStore.mediaActive -> {
                showVortex(false)
                callActionsRow.visibility = View.GONE
                val appIcon = loadAppIconBitmap(IslandLiveStore.mediaPackage)
                val art = IslandLiveStore.mediaArt
                if (appIcon != null) ivCompactIcon.setImageBitmap(appIcon)
                else if (art != null) ivCompactIcon.setImageBitmap(art)
                else ivCompactIcon.setImageResource(R.drawable.ic_media_wave)

                if (art != null) ivExpandedIcon.setImageBitmap(art)
                else if (appIcon != null) ivExpandedIcon.setImageBitmap(appIcon)
                else ivExpandedIcon.setImageResource(R.drawable.ic_media_wave)

                tvCompactRightName.text = IslandLiveStore.mediaTitle
                tvCompactLeftStatus.text = if (IslandLiveStore.mediaPlaying) "♪" else "⏸"
                tvExpandedTitle.text = IslandLiveStore.mediaTitle
                tvExpandedSub.text = IslandLiveStore.mediaArtist
                tvExpandedMeta.text = "${if (IslandLiveStore.mediaPlaying) "يعمل الآن" else "متوقف"} • $statusSummary"
                btnPlayPause.setImageResource(
                    if (IslandLiveStore.mediaPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
                mediaControls.visibility = View.VISIBLE
                notifActionsRow.visibility = View.GONE
            }

            else -> {
                callActionsRow.visibility = View.GONE
                if (isCharging) {
                    showVortex(true)
                    tvCompactRightName.text = if (batt.watts > 0) "شحن ${batt.watts}W" else "شحن $batteryPct%"
                } else {
                    showVortex(false)
                    bindCrispAvatar(ivCompactIcon)
                    bindCrispAvatar(ivExpandedIcon)
                    tvCompactRightName.text = settings.userName
                }
                tvCompactLeftStatus.text = timeFmt.format(now)
                tvExpandedTitle.text = "${settings.userName} • ${fullTimeFmt.format(now)}"
                tvExpandedSub.text = if (isCharging) "جاري الشحن ($batteryPct%) • ${dateFmt.format(now)}" else dateFmt.format(now)
                tvExpandedMeta.text = statusSummary
                mediaControls.visibility = View.GONE
                notifActionsRow.visibility = View.GONE
            }
        }
    }

    private fun bindCrispAvatar(iv: ImageView) {
        val bmp = getCrispAvatarBitmap()
        if (bmp != null) {
            iv.setImageBitmap(bmp)
        } else {
            iv.setImageResource(R.drawable.app_logo)
        }
    }

    private fun loadAppIconBitmap(pkg: String): Bitmap? {
        if (pkg.isBlank()) return null
        appIconCache[pkg]?.let { return it }
        return try {
            val d = packageManager.getApplicationIcon(pkg)
            val size = 128
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            d.setBounds(0, 0, size, size)
            d.draw(canvas)
            appIconCache[pkg] = bmp
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}
