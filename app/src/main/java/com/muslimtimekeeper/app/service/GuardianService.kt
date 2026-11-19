package com.muslimtimekeeper.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.muslimtimekeeper.app.R
import com.muslimtimekeeper.app.core.PrayerCalculator
import com.muslimtimekeeper.app.core.PrefsStore
import com.muslimtimekeeper.app.ui.LockOverlayActivity

class GuardianService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: PrefsStore
    private var lastBlockedPkg: String? = null
    private var prayerUnlockedUntil: Long = 0L

    private val tickRunnable = object : Runnable {
        override fun run() {
            evaluateRules()
            handler.postDelayed(this, 5000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = PrefsStore(this)
        startForeground(101, buildNotification())
        handler.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "ACTION_PRAYER_UNLOCKED") {
            prayerUnlockedUntil = System.currentTimeMillis() + (store.prayerDurationMinutes * 60_000L)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val chId = "mtk_guardian_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(chId, "Muslim Time Keeper Protection", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, chId)
            .setContentTitle("Muslim Time Keeper Active")
            .setContentText("Monitoring screen time, apps, prayer lock & web safety")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    private fun getForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 15_000L, end)
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun evaluateRules() {
        store.ensureToday()

        // 1. Persistent lock check (survives reboot!)
        if (store.isPhoneLockedToday) {
            launchLockScreen("PHONE_LOCK", store.lockReason.ifEmpty { "Daily phone limit reached." }, 0L)
            return
        }

        // 2. Daily Phone Limit
        val used = store.phoneSecondsUsedToday + 5
        store.phoneSecondsUsedToday = used
        if (used >= store.dailyPhoneLimitMinutes * 60) {
            store.isPhoneLockedToday = true
            store.lockReason = "Daily phone screen time limit has been reached."
            lockDeviceIfAdmin()
            launchLockScreen("PHONE_LOCK", store.lockReason, 0L)
            return
        }

        // 3. Prayer Time Lock + Adhan
        if (store.prayerLockEnabled && System.currentTimeMillis() >= prayerUnlockedUntil) {
            val active = PrayerCalculator.getActivePrayer(
                store.latitude, store.longitude, store.timezoneOffset, store.prayerDurationMinutes
            )
            if (active != null) {
                launchLockScreen("PRAYER_LOCK", "${active.name} Prayer Time", active.endMillis)
                return
            }
        }

        // 4. Per-App Limits & Reopen Escalation
        val fgPkg = getForegroundPackage() ?: return
        if (fgPkg == packageName) return

        val rules = store.getAppRules()
        var changed = false
        for (rule in rules) {
            if (rule.packageName.equals(fgPkg, ignoreCase = true)) {
                val limitSec = rule.dailyLimitMinutes * 60
                if (rule.secondsUsedToday < limitSec) {
                    rule.secondsUsedToday += 5
                    changed = true
                    if (rule.secondsUsedToday >= limitSec) {
                        launchLockScreen("APP_BLOCKED", "${rule.displayName} daily limit reached.", 0L)
                    }
                } else {
                    if (lastBlockedPkg != fgPkg) {
                        rule.reopenAttempts += 1
                        changed = true
                        if (rule.reopenAttempts >= store.maxReopenAttempts) {
                            store.isPhoneLockedToday = true
                            store.lockReason = "Phone locked after repeated attempts to open ${rule.displayName}."
                            store.saveAppRules(rules)
                            lockDeviceIfAdmin()
                            launchLockScreen("PHONE_LOCK", store.lockReason, 0L)
                            return
                        }
                    }
                    launchLockScreen("APP_BLOCKED", "${rule.displayName} is blocked for today (${rule.reopenAttempts}/${store.maxReopenAttempts}).", 0L)
                }
                lastBlockedPkg = fgPkg
                break
            }
        }
        if (fgPkg != lastBlockedPkg) lastBlockedPkg = null
        if (changed) store.saveAppRules(rules)
    }

    private fun lockDeviceIfAdmin() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val comp = ComponentName(this, DeviceAdminProtectionReceiver::class.java)
            if (dpm.isAdminActive(comp)) dpm.lockNow()
        } catch (_: Exception) { }
    }

    private fun launchLockScreen(mode: String, reason: String, endMillis: Long) {
        val i = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("MODE", mode)
            putExtra("REASON", reason)
            putExtra("END_MILLIS", endMillis)
        }
        startActivity(i)
    }
}
