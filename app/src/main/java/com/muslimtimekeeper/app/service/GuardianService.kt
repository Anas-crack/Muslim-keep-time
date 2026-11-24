package com.muslimtimekeeper.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.TrafficStats
import android.net.VpnService
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import com.muslimtimekeeper.app.R
import com.muslimtimekeeper.app.core.PrayerCalculator
import com.muslimtimekeeper.app.core.PrefsStore
import com.muslimtimekeeper.app.ui.LockOverlayActivity
import com.muslimtimekeeper.app.ui.MainActivity
import java.util.Calendar

class GuardianService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: PrefsStore
    private lateinit var overlay: SystemOverlayBlocker
    private var lastBlockedPkg: String? = null
    private var lastDetectedPkg: String? = null
    private var prayerUnlockedUntil: Long = 0L
    private var bedtimeUnlockedUntil: Long = 0L
    private var lastActivePrayerKey: String = ""
    private var tickCounter = 0
    private val sentAlerts = HashSet<String>()

    private val tickRunnable = object : Runnable {
        override fun run() {
            evaluateRules()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = PrefsStore(this)
        overlay = SystemOverlayBlocker(
            context = this,
            store = store,
            onPrayerUnlocked = {
                prayerUnlockedUntil = System.currentTimeMillis() + (store.prayerDurationMinutes * 60_000L)
                lastActivePrayerKey = ""
                restoreRingerMode()
            },
            onPhoneUnlocked = {
                store.isPhoneLockedToday = false
                store.lockReason = ""
                store.phoneSecondsUsedToday = maxOf(0, (store.dailyPhoneLimitMinutes * 60) - 1800)
            },
            onBedtimeUnlocked = {
                bedtimeUnlockedUntil = System.currentTimeMillis() + (60 * 60_000L) // 1 hour parent override
            },
            onAdhkarCompleted = {
                if (store.pendingAdhkarPrayerKey.isNotEmpty()) {
                    store.lastAdhkarCompletedKey = store.pendingAdhkarPrayerKey
                    store.pendingAdhkarPrayerKey = ""
                }
            }
        )
        startForeground(101, buildNotification())
        handler.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_PRAYER_UNLOCKED" -> {
                prayerUnlockedUntil = System.currentTimeMillis() + (store.prayerDurationMinutes * 60_000L)
                lastActivePrayerKey = ""
                restoreRingerMode()
                overlay.hide()
            }
            "ACTION_ADHKAR_COMPLETED" -> {
                if (store.pendingAdhkarPrayerKey.isNotEmpty()) {
                    store.lastAdhkarCompletedKey = store.pendingAdhkarPrayerKey
                    store.pendingAdhkarPrayerKey = ""
                }
                overlay.hide()
            }
            "ACTION_BEDTIME_UNLOCKED" -> {
                bedtimeUnlockedUntil = System.currentTimeMillis() + (60 * 60_000L)
                overlay.hide()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        restoreRingerMode()
        overlay.hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val chId = "mtk_guardian_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(chId, "Next Prayer Countdown & Protection", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        val ar = store.isArabic
        val nxt = PrayerCalculator.getNextPrayer(store.latitude, store.longitude, store.timezoneOffset, ar)
        val exactTime = PrayerCalculator.formatExactTime(nxt.second, ar)
        val remStr = PrayerCalculator.formatRemaining(nxt.second - System.currentTimeMillis(), ar)
        val title = if (ar) "🕌 الصلاة القادمة: ${nxt.first} ($exactTime)" else "🕌 Next Prayer: ${nxt.first} ($exactTime)"
        val body = if (ar) "⏳ متبقي $remStr على أذان ${nxt.first}" else "⏳ $remStr remaining until ${nxt.first}"

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, chId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun updatePersistentPrayerNotification() {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(101, buildNotification())
        } catch (_: Exception) { }
    }

    private fun enableMosqueSilent() {
        if (!store.mosqueAutoSilentEnabled) return
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (store.savedRingerMode == -1) {
                store.savedRingerMode = am.ringerMode
            }
            if (nm.isNotificationPolicyAccessGranted) {
                if (am.ringerMode != AudioManager.RINGER_MODE_SILENT) {
                    am.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
            } else {
                if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                    am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
            }
        } catch (_: Exception) { }
    }

    private fun restoreRingerMode() {
        val prev = store.savedRingerMode
        if (prev == -1) return
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted || prev != AudioManager.RINGER_MODE_SILENT) {
                am.ringerMode = prev
            }
        } catch (_: Exception) { }
        store.savedRingerMode = -1
    }

    private fun isInBedtimeWindow(startHour: Int, endHour: Int): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (startHour == endHour) false
        else if (startHour > endHour) (hour >= startHour || hour < endHour)
        else (hour in startHour until endHour)
    }

    private fun isAllowedCallPackage(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        val low = pkg.lowercase()
        try {
            val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (tm.defaultDialerPackage?.equals(pkg, ignoreCase = true) == true) return true
        } catch (_: Exception) { }
        return low.contains("dialer") ||
            low.contains("incallui") ||
            low.contains("telecom") ||
            low == "com.android.phone" ||
            low == "com.android.contacts" ||
            low == "com.google.android.contacts" ||
            low == "com.samsung.android.app.contacts"
    }

    private fun isScreenOn(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isInteractive
    }

    private fun getForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 10_000L, end)
        val ev = UsageEvents.Event()
        var latestPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                latestPkg = ev.packageName
            }
        }
        if (latestPkg != null) {
            lastDetectedPkg = latestPkg
            return latestPkg
        }
        if (lastDetectedPkg != null) return lastDetectedPkg
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 30_000L, end)
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun enforceLockOverlay(mode: String, reason: String, endMillis: Long = 0L) {
        if (overlay.canDrawOverlay()) {
            overlay.show(mode, reason, endMillis)
        } else {
            val i = Intent(this, LockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("MODE", mode)
                putExtra("REASON", reason)
                putExtra("END_MILLIS", endMillis)
            }
            startActivity(i)
        }
    }

    private fun sendPreAlert(key: String, msg: String) {
        val fullKey = "${store.todayStr()}_$key"
        if (sentAlerts.contains(fullKey)) return
        sentAlerts.add(fullKey)
        val chId = "mtk_alerts_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(chId, "Time & Prayer Alerts", NotificationManager.IMPORTANCE_HIGH))
        val notif = NotificationCompat.Builder(this, chId)
            .setContentTitle(if (store.isArabic) "⏰ تنبيه حارس وقت المسلم" else "⏰ Muslim Time Keeper Alert")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(key.hashCode(), notif)
    }

    private fun updateDataQuotaUsage() {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx < 0 || tx < 0) return
        val totalNow = rx + tx
        val prev = store.lastTrafficSnapshot
        store.lastTrafficSnapshot = totalNow
        if (prev in 1 until totalNow) {
            val delta = totalNow - prev
            if (delta < 500_000_000L) {
                store.dataUsedBytes = store.dataUsedBytes + delta
                store.dataUsedTodayBytes = store.dataUsedTodayBytes + delta
            }
        }

        if (!store.dataLimitEnabled) {
            if (store.isInternetBlockedByQuota) {
                store.isInternetBlockedByQuota = false
                if (store.webSafetyEnabled) startService(Intent(this, SafeDnsVpnService::class.java).apply { action = "SAFE_DNS" })
                else startService(Intent(this, SafeDnsVpnService::class.java).apply { action = "STOP_VPN" })
            }
            return
        }

        val limitBytes = store.dataLimitMb.toLong() * 1024L * 1024L
        if (limitBytes <= 0L) return
        val usedBytes = store.dataUsedBytes
        val pct = (usedBytes * 100L) / limitBytes

        if (pct in 80..94) {
            sendPreAlert("data_80", if (store.isArabic) "استهلكت 80% من باقة الإنترنت المحددة لهذا الجهاز!" else "You have used 80% of this device's internet quota!")
        } else if (pct in 95..99) {
            sendPreAlert("data_95", if (store.isArabic) "تبقّى أقل من 5% من باقة الإنترنت المحددة لهذا الجهاز!" else "Less than 5% left of this device's internet quota!")
        }

        if (usedBytes >= limitBytes) {
            if (!store.isInternetBlockedByQuota) {
                store.isInternetBlockedByQuota = true
                sendPreAlert("data_100", if (store.isArabic) "انتهت حصة الإنترنت المحددة! تم قطع الإنترنت لحين موافقة الوالدين." else "Internet data quota reached! Internet paused until parent approves.")
                if (VpnService.prepare(this) == null) {
                    startService(Intent(this, SafeDnsVpnService::class.java).apply { action = "BLOCK_ALL" })
                }
            }
        } else if (store.isInternetBlockedByQuota) {
            store.isInternetBlockedByQuota = false
            if (VpnService.prepare(this) == null) {
                if (store.webSafetyEnabled) startService(Intent(this, SafeDnsVpnService::class.java).apply { action = "SAFE_DNS" })
                else startService(Intent(this, SafeDnsVpnService::class.java).apply { action = "STOP_VPN" })
            }
        }
    }

    private fun checkProactiveReminders() {
        val ar = store.isArabic
        val nowCal = Calendar.getInstance()
        val nowMinOfDay = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

        // 1. Upcoming Prayer Reminders (5 min & 3 min before prayer)
        val nxtP = PrayerCalculator.getNextPrayer(store.latitude, store.longitude, store.timezoneOffset, ar)
        val secToPr = ((nxtP.second - System.currentTimeMillis()) / 1000L).toInt()
        if (secToPr in 181..300) {
            sendPreAlert("pr5_${nxtP.first}", if (ar) "🕌 اقتربت صلاة ${nxtP.first} — متبقي 5 دقائق! استعد للوضوء والصلاة." else "🕌 ${nxtP.first} prayer is in 5 minutes!")
        } else if (secToPr in 1..180) {
            sendPreAlert("pr3_${nxtP.first}", if (ar) "🕌 اقتربت صلاة ${nxtP.first} — متبقي 3 دقائق فقط!" else "🕌 ${nxtP.first} prayer is in 3 minutes!")
        }

        // 2. Bedtime Sleep Adhkar Reminder (10 min & 5 min before user's bedtime) with Prophetic Evidence!
        if (store.sleepAdhkarReminderEnabled) {
            val bedMinOfDay = store.bedtimeStartHour * 60 + store.bedtimeStartMinute
            var diffBed = bedMinOfDay - nowMinOfDay
            if (diffBed < -720) diffBed += 1440
            if (diffBed in 6..10) {
                sendPreAlert(
                    "sleep_adhkar_10",
                    if (ar) "🌙 اقترب ميعاد النوم (بعد 10 دقائق)! لا تنسَ أذكار النوم لأنها تقي من الشيطان؛ قال النبي ﷺ: «إذا أويت إلى فراشك فاقرأ آية الكرسي؛ فإنه لن يزال عليك من الله حافظ ولا يقربك شيطان حتى تصبح» (رواه البخاري)."
                    else "🌙 Bedtime is in 10 minutes! Don't forget Sleep Adhkar (Ayat al-Kursi & Al-Mu'awwidhat) for Allah's protection until morning (Sahih Bukhari)."
                )
            } else if (diffBed in 1..5) {
                sendPreAlert(
                    "sleep_adhkar_5",
                    if (ar) "🌙 متبقي 5 دقائق على ميعاد النوم! اقرأ آية الكرسي وآخر آيتين من البقرة والمعوذات؛ قال ﷺ: «من قرأ بالآيتين من آخر سورة البقرة في ليلة كفتاه» (متفق عليه)."
                    else "🌙 5 minutes until bedtime! Recite Ayat al-Kursi and the last two verses of Al-Baqarah — they suffice you against all harm (Bukhari & Muslim)."
                )
            }
        }

        // 3. Daily Schedule 5-Minute Pre-Alerts for lessons & appointments (respecting selected weekdays)!
        val today = store.todayStr()
        val todayDow = nowCal.get(Calendar.DAY_OF_WEEK)
        for (item in store.getScheduleItems()) {
            if (!item.enabled || item.doneDate == today) continue
            if (item.days.isNotEmpty() && todayDow !in item.days) continue
            val itemMin = item.hour * 60 + item.minute
            val diff = itemMin - nowMinOfDay
            if (diff in 1..5) {
                val tStr = String.format("%02d:%02d", item.hour, item.minute)
                sendPreAlert(
                    "sched_${item.id}",
                    if (ar) "📅 تذكير بالجدول اليومي: متبقي $diff دقائق على موعد «${item.title}» (الساعة $tStr)!"
                    else "📅 Daily Schedule Reminder: '${item.title}' starts in $diff min ($tStr)!"
                )
            }
        }
    }

    private fun evaluateRules() {
        store.ensureToday()
        if (tickCounter % 3 == 0) updateDataQuotaUsage()
        if (tickCounter % 15 == 0) checkProactiveReminders()
        if (tickCounter % 30 == 0) updatePersistentPrayerNotification()
        if (tickCounter % 60 == 0) {
            store.recordDayStats(store.todayStr(), store.phoneSecondsUsedToday, store.dataUsedTodayBytes)
        }

        // Check active prayer even when screen is off so Mosque Auto-Silent works in pocket!
        val activePrayer = if (store.prayerLockEnabled && System.currentTimeMillis() >= prayerUnlockedUntil) {
            PrayerCalculator.getActivePrayer(store.latitude, store.longitude, store.timezoneOffset, store.prayerDurationMinutes)
        } else null

        if (activePrayer != null) {
            lastActivePrayerKey = "${store.todayStr()}_${activePrayer.name}"
            enableMosqueSilent()
        } else {
            restoreRingerMode()
            if (lastActivePrayerKey.isNotEmpty()) {
                if (store.postPrayerAdhkarEnabled && store.lastAdhkarCompletedKey != lastActivePrayerKey) {
                    store.pendingAdhkarPrayerKey = lastActivePrayerKey
                    sendPreAlert(
                        "adhkar_$lastActivePrayerKey",
                        if (store.isArabic) "📿 يرجى قراءة أذكار بعد الصلاة لفتح الموبايل" else "📿 Please read Post-Prayer Adhkar to unlock your phone"
                    )
                }
                lastActivePrayerKey = ""
            }
        }

        if (!isScreenOn()) {
            tickCounter++
            return
        }

        val fg = getForegroundPackage()

        // 1. Bedtime Lock (Allows Emergency Phone Calls Only)
        if (store.bedtimeEnabled &&
            isInBedtimeWindow(store.bedtimeStartHour, store.bedtimeEndHour) &&
            System.currentTimeMillis() >= bedtimeUnlockedUntil
        ) {
            if (isAllowedCallPackage(fg)) {
                overlay.hide()
            } else {
                val windowStr = String.format("%02d:00 — %02d:00", store.bedtimeStartHour, store.bedtimeEndHour)
                val reason = if (store.isArabic)
                    "حان وقت النوم المجدول ($windowStr). المكالمات الطارئة متاحة دائماً."
                else
                    "Scheduled Bedtime ($windowStr). Emergency phone calls remain available."
                enforceLockOverlay("BEDTIME_LOCK", reason, 0L)
            }
            tickCounter++
            return
        }

        // 2. Persistent Daily Phone Lock (only when Daily Timer is enabled)
        if (store.dailyTimerEnabled && store.isPhoneLockedToday) {
            if (isAllowedCallPackage(fg)) {
                overlay.hide()
            } else {
                val reason = store.lockReason.ifEmpty {
                    if (store.isArabic) "انتهى وقت استخدام الهاتف اليوم." else "Daily phone limit reached."
                }
                enforceLockOverlay("PHONE_LOCK", reason, 0L)
            }
            tickCounter++
            return
        }

        // 3. Prayer Time Lock + Calm Tasbeeh + Adhan (Allows Emergency Phone Calls Only)
        if (activePrayer != null) {
            if (isAllowedCallPackage(fg)) {
                overlay.hide()
            } else {
                val msg = if (store.isArabic) "حان الآن موعد صلاة ${PrayerCalculator.localizedName(activePrayer.name, store.isArabic)}" else "${PrayerCalculator.localizedName(activePrayer.name, store.isArabic)} Prayer Time"
                enforceLockOverlay("PRAYER_LOCK", msg, activePrayer.endMillis)
            }
            tickCounter++
            return
        }

        // 4. Mandatory Post-Prayer Adhkar Gate (Allows Emergency Phone Calls Only)
        if (store.postPrayerAdhkarEnabled && store.pendingAdhkarPrayerKey.isNotEmpty()) {
            if (isAllowedCallPackage(fg)) {
                overlay.hide()
            } else {
                val msg = if (store.isArabic) "يرجى قراءة أذكار بعد الصلاة لفتح الموبايل" else "Please read Post-Prayer Adhkar to unlock your phone"
                enforceLockOverlay("ADHKAR_LOCK", msg, 0L)
            }
            tickCounter++
            return
        }

        // 5. Daily Phone Limit Counter (optional — only runs if user enabled Daily Timer!)
        if (store.dailyTimerEnabled) {
            val used = store.phoneSecondsUsedToday + 1
            store.phoneSecondsUsedToday = used
            val remSec = (store.dailyPhoneLimitMinutes * 60) - used
            if (remSec in 301..600) sendPreAlert("pc_10", if (store.isArabic) "متبقي 10 دقائق على انتهاء وقت الهاتف اليوم! احفظ عملك." else "10 minutes left on phone today!")
            if (remSec in 1..300) sendPreAlert("pc_5", if (store.isArabic) "متبقي 5 دقائق فقط على انتهاء وقت الهاتف اليوم!" else "Only 5 minutes left on phone today!")
            if (used >= store.dailyPhoneLimitMinutes * 60) {
                store.isPhoneLockedToday = true
                store.lockReason = if (store.isArabic) "انتهى الحد اليومي المسموح لاستخدام الهاتف." else "Daily phone screen time limit has been reached."
                enforceLockOverlay("PHONE_LOCK", store.lockReason, 0L)
                tickCounter++
                return
            }
        }

        // 6. Per-App Limits & Reopen Escalation (every 2 seconds)
        tickCounter++
        if (tickCounter % 2 != 0) return
        if (fg == null || fg == packageName) return

        val rules = store.getAppRules()
        var changed = false
        for (rule in rules) {
            if (rule.packageName.equals(fg, ignoreCase = true)) {
                val limitSec = rule.dailyLimitMinutes * 60
                if (rule.secondsUsedToday < limitSec) {
                    rule.secondsUsedToday += 2
                    changed = true
                    if (rule.secondsUsedToday >= limitSec) {
                        val msg = if (store.isArabic) "انتهى الوقت اليومي لتطبيق ${rule.displayName}." else "${rule.displayName} daily limit reached."
                        enforceLockOverlay("APP_BLOCKED", msg, 0L)
                    }
                } else {
                    if (lastBlockedPkg != fg) {
                        rule.reopenAttempts += 1
                        changed = true
                        if (rule.reopenAttempts >= store.maxReopenAttempts) {
                            store.isPhoneLockedToday = true
                            store.lockReason = if (store.isArabic) "تم قفل الهاتف لتكرار فتح ${rule.displayName} بعد انتهاء الوقت." else "Phone locked after repeated attempts to open ${rule.displayName}."
                            store.saveAppRules(rules)
                            enforceLockOverlay("PHONE_LOCK", store.lockReason, 0L)
                            return
                        }
                    }
                    val msg = if (store.isArabic)
                        "${rule.displayName} محظور اليوم (${rule.reopenAttempts}/${store.maxReopenAttempts})."
                    else
                        "${rule.displayName} is blocked today (${rule.reopenAttempts}/${store.maxReopenAttempts})."
                    enforceLockOverlay("APP_BLOCKED", msg, 0L)
                }
                lastBlockedPkg = fg
                break
            }
        }
        if (fg != lastBlockedPkg) lastBlockedPkg = null
        if (changed) store.saveAppRules(rules)
    }
}
