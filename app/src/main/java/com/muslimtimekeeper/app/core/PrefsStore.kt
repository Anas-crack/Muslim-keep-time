package com.muslimtimekeeper.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppRule(
    val packageName: String,
    val displayName: String,
    val dailyLimitMinutes: Int = 60,
    var secondsUsedToday: Int = 0,
    var reopenAttempts: Int = 0
)

class PrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("mtk_prefs", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains("pin_hash")) {
            prefs.edit().putString("pin_hash", hashPin("1234")).apply()
        }
        ensureToday()
    }

    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("MTK_ANDROID_2026:$pin".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String): Boolean = hashPin(pin) == prefs.getString("pin_hash", "")
    fun updatePin(newPin: String) = prefs.edit().putString("pin_hash", hashPin(newPin)).apply()

    fun todayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun ensureToday() {
        val today = todayStr()
        val prevDate = prefs.getString("usage_date", "") ?: ""
        if (prevDate != today) {
            if (prevDate.isNotEmpty()) {
                recordDayStats(prevDate, prefs.getInt("phone_seconds_used", 0), prefs.getLong("data_used_today_bytes", 0L))
            }
            prefs.edit()
                .putString("usage_date", today)
                .putInt("phone_seconds_used", 0)
                .putLong("data_used_today_bytes", 0L)
                .putBoolean("is_phone_locked_today", false)
                .putString("lock_reason", "")
                .apply()
            val rules = getAppRules().map { it.copy(secondsUsedToday = 0, reopenAttempts = 0) }
            saveAppRules(rules)
        }
    }

    fun recordDayStats(dateKey: String, screenSec: Int, dataBytes: Long) {
        try {
            val raw = prefs.getString("daily_history_json", "{}") ?: "{}"
            val obj = JSONObject(raw)
            val entry = JSONObject().apply {
                put("sec", screenSec)
                put("bytes", dataBytes)
            }
            obj.put(dateKey, entry)
            // Keep last 21 days to keep prefs lightweight
            val keys = mutableListOf<String>()
            val it = obj.keys()
            while (it.hasNext()) keys.add(it.next())
            keys.sort()
            while (keys.size > 21) {
                obj.remove(keys.removeAt(0))
            }
            prefs.edit().putString("daily_history_json", obj.toString()).apply()
        } catch (_: Exception) { }
    }

    fun getDailyHistoryMap(): Map<String, Pair<Int, Long>> {
        val map = mutableMapOf<String, Pair<Int, Long>>()
        try {
            val raw = prefs.getString("daily_history_json", "{}") ?: "{}"
            val obj = JSONObject(raw)
            val it = obj.keys()
            while (it.hasNext()) {
                val k = it.next()
                val e = obj.optJSONObject(k) ?: continue
                map[k] = Pair(e.optInt("sec", 0), e.optLong("bytes", 0L))
            }
        } catch (_: Exception) { }
        // Always include live today stats
        map[todayStr()] = Pair(phoneSecondsUsedToday, dataUsedTodayBytes)
        return map
    }

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(v) = prefs.edit().putString("user_name", v).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean("is_dark_mode", true)
        set(v) = prefs.edit().putBoolean("is_dark_mode", v).apply()

    var isArabic: Boolean
        get() = prefs.getBoolean("is_arabic", true)
        set(v) = prefs.edit().putBoolean("is_arabic", v).apply()

    var dailyPhoneLimitMinutes: Int
        get() = prefs.getInt("daily_phone_limit_min", 180)
        set(v) = prefs.edit().putInt("daily_phone_limit_min", v).apply()

    var maxReopenAttempts: Int
        get() = prefs.getInt("max_reopen_attempts", 2)
        set(v) = prefs.edit().putInt("max_reopen_attempts", v).apply()

    var phoneSecondsUsedToday: Int
        get() { ensureToday(); return prefs.getInt("phone_seconds_used", 0) }
        set(v) = prefs.edit().putInt("phone_seconds_used", v).apply()

    var dataUsedTodayBytes: Long
        get() { ensureToday(); return prefs.getLong("data_used_today_bytes", 0L) }
        set(v) = prefs.edit().putLong("data_used_today_bytes", v).apply()

    var isPhoneLockedToday: Boolean
        get() { ensureToday(); return prefs.getBoolean("is_phone_locked_today", false) }
        set(v) = prefs.edit().putBoolean("is_phone_locked_today", v).apply()

    var lockReason: String
        get() = prefs.getString("lock_reason", "") ?: ""
        set(v) = prefs.edit().putString("lock_reason", v).apply()

    var prayerLockEnabled: Boolean
        get() = prefs.getBoolean("prayer_lock_enabled", true)
        set(v) = prefs.edit().putBoolean("prayer_lock_enabled", v).apply()

    var adhanAudioEnabled: Boolean
        get() = prefs.getBoolean("adhan_audio_enabled", true)
        set(v) = prefs.edit().putBoolean("adhan_audio_enabled", v).apply()

    var mosqueAutoSilentEnabled: Boolean
        get() = prefs.getBoolean("mosque_auto_silent", true)
        set(v) = prefs.edit().putBoolean("mosque_auto_silent", v).apply()

    var savedRingerMode: Int
        get() = prefs.getInt("saved_ringer_mode", -1)
        set(v) = prefs.edit().putInt("saved_ringer_mode", v).apply()

    var postPrayerAdhkarEnabled: Boolean
        get() = prefs.getBoolean("post_prayer_adhkar_enabled", true)
        set(v) = prefs.edit().putBoolean("post_prayer_adhkar_enabled", v).apply()

    var pendingAdhkarPrayerKey: String
        get() = prefs.getString("pending_adhkar_prayer_key", "") ?: ""
        set(v) = prefs.edit().putString("pending_adhkar_prayer_key", v).apply()

    var lastAdhkarCompletedKey: String
        get() = prefs.getString("last_adhkar_completed_key", "") ?: ""
        set(v) = prefs.edit().putString("last_adhkar_completed_key", v).apply()

    var bedtimeEnabled: Boolean
        get() = prefs.getBoolean("bedtime_enabled", false)
        set(v) = prefs.edit().putBoolean("bedtime_enabled", v).apply()

    var bedtimeStartHour: Int
        get() = prefs.getInt("bedtime_start_hour", 23)
        set(v) = prefs.edit().putInt("bedtime_start_hour", v).apply()

    var bedtimeEndHour: Int
        get() = prefs.getInt("bedtime_end_hour", 6)
        set(v) = prefs.edit().putInt("bedtime_end_hour", v).apply()

    var prayerDurationMinutes: Int
        get() = prefs.getInt("prayer_duration_min", 30)
        set(v) = prefs.edit().putInt("prayer_duration_min", v).apply()

    var cityName: String
        get() = prefs.getString("city_name", "Zagazig (Al-Sharqia)") ?: "Zagazig (Al-Sharqia)"
        set(v) = prefs.edit().putString("city_name", v).apply()

    var latitude: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("lat", java.lang.Double.doubleToRawLongBits(30.5877)))
        set(v) = prefs.edit().putLong("lat", java.lang.Double.doubleToRawLongBits(v)).apply()

    var longitude: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("lon", java.lang.Double.doubleToRawLongBits(31.5020)))
        set(v) = prefs.edit().putLong("lon", java.lang.Double.doubleToRawLongBits(v)).apply()

    var timezoneOffset: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("tz", java.lang.Double.doubleToRawLongBits(2.0)))
        set(v) = prefs.edit().putLong("tz", java.lang.Double.doubleToRawLongBits(v)).apply()

    var updateRepo: String
        get() = prefs.getString("update_repo", "Anas-crack/Muslim-keep-time") ?: "Anas-crack/Muslim-keep-time"
        set(v) = prefs.edit().putString("update_repo", v).apply()

    var dataLimitEnabled: Boolean
        get() = prefs.getBoolean("data_limit_enabled", false)
        set(v) = prefs.edit().putBoolean("data_limit_enabled", v).apply()

    var dataLimitMb: Int
        get() = prefs.getInt("data_limit_mb", 2048) // Default 2 GB
        set(v) = prefs.edit().putInt("data_limit_mb", v).apply()

    var dataUsedBytes: Long
        get() = prefs.getLong("data_used_bytes", 0L)
        set(v) = prefs.edit().putLong("data_used_bytes", v).apply()

    var lastTrafficSnapshot: Long
        get() = prefs.getLong("last_traffic_snapshot", -1L)
        set(v) = prefs.edit().putLong("last_traffic_snapshot", v).apply()

    var isInternetBlockedByQuota: Boolean
        get() = prefs.getBoolean("internet_blocked_quota", false)
        set(v) = prefs.edit().putBoolean("internet_blocked_quota", v).apply()

    var webSafetyEnabled: Boolean
        get() = prefs.getBoolean("web_safety_enabled", false)
        set(v) = prefs.edit().putBoolean("web_safety_enabled", v).apply()

    fun getAppRules(): MutableList<AppRule> {
        val raw = prefs.getString("app_rules_json", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<AppRule>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                AppRule(
                    packageName = o.getString("pkg"),
                    displayName = o.getString("name"),
                    dailyLimitMinutes = o.optInt("limit", 60),
                    secondsUsedToday = o.optInt("used", 0),
                    reopenAttempts = o.optInt("viol", 0)
                )
            )
        }
        return list
    }

    fun saveAppRules(rules: List<AppRule>) {
        val arr = JSONArray()
        for (r in rules) {
            arr.put(JSONObject().apply {
                put("pkg", r.packageName)
                put("name", r.displayName)
                put("limit", r.dailyLimitMinutes)
                put("used", r.secondsUsedToday)
                put("viol", r.reopenAttempts)
            })
        }
        prefs.edit().putString("app_rules_json", arr.toString()).apply()
    }

    fun resetAllToday() {
        prefs.edit()
            .putInt("phone_seconds_used", 0)
            .putBoolean("is_phone_locked_today", false)
            .putString("lock_reason", "")
            .putString("pending_adhkar_prayer_key", "")
            .apply()
        saveAppRules(getAppRules().map { it.copy(secondsUsedToday = 0, reopenAttempts = 0) })
    }
}
