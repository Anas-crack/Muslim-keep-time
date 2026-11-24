package com.muslimtimekeeper.app.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

data class PrayerWindow(val name: String, val startMillis: Long, val endMillis: Long)

object PrayerCalculator {
    val presetCities = linkedMapOf(
        "Zagazig (Al-Sharqia)" to doubleArrayOf(30.5877, 31.5020, 2.0),
        "Cairo" to doubleArrayOf(30.0444, 31.2357, 2.0),
        "Alexandria" to doubleArrayOf(31.2001, 29.9187, 2.0),
        "Giza" to doubleArrayOf(30.0131, 31.2089, 2.0),
        "Mansoura" to doubleArrayOf(31.0409, 31.3785, 2.0),
        "Riyadh" to doubleArrayOf(24.7136, 46.6753, 3.0),
        "Makkah" to doubleArrayOf(21.3891, 39.8579, 3.0),
        "Dubai" to doubleArrayOf(25.2048, 55.2708, 4.0)
    )

    private fun dtr(d: Double) = d * PI / 180.0
    private fun rtd(r: Double) = r * 180.0 / PI
    private fun sinD(d: Double) = sin(dtr(d))
    private fun cosD(d: Double) = cos(dtr(d))
    private fun tanD(d: Double) = tan(dtr(d))
    private fun asinD(x: Double) = rtd(asin(x.coerceIn(-1.0, 1.0)))
    private fun acosD(x: Double) = rtd(acos(x.coerceIn(-1.0, 1.0)))
    private fun acotD(x: Double) = rtd(atan(1.0 / x))
    private fun atan2D(y: Double, x: Double) = rtd(atan2(y, x))
    private fun fixAngle(a: Double) = a - 360.0 * floor(a / 360.0)
    private fun fixHour(h: Double) = h - 24.0 * floor(h / 24.0)

    private fun julianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2.0 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun sunPos(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinD(g) + 0.020 * sinD(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = atan2D(cosD(e) * sinD(l), cosD(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = asinD(sinD(e) * sinD(l))
        return Pair(decl, eqt)
    }

    private fun midDay(jd: Double, t: Double): Double = fixHour(12.0 - sunPos(jd + t).second)

    private fun sunAngleTime(jd: Double, lat: Double, angle: Double, t: Double, ccw: Boolean): Double {
        val decl = sunPos(jd + t).first
        val noon = midDay(jd, t)
        val num = -sinD(angle) - sinD(decl) * sinD(lat)
        val den = cosD(decl) * cosD(lat)
        val v = acosD((num / den).coerceIn(-1.0, 1.0)) / 15.0
        return noon + if (ccw) -v else v
    }

    private fun asrTime(jd: Double, lat: Double, factor: Int, t: Double): Double {
        val decl = sunPos(jd + t).first
        val angle = -acotD(factor + tanD(abs(lat - decl)))
        return sunAngleTime(jd, lat, angle, t, false)
    }

    // Automatically honors Egypt Daylight Saving Time (UTC+2 <-> UTC+3) using live system offset when in same zone
    private fun effectiveTz(cal: Calendar, baseTz: Double): Double {
        val deviceTz = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3_600_000.0
        return if (abs(deviceTz - baseTz) <= 1.5) deviceTz else baseTz
    }

    private fun hoursToCal(base: Calendar, hours: Double): Calendar {
        val totalMinutes = floor(fixHour(hours) * 60.0 + 0.5).toInt()
        val h = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        return (base.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    /**
     * Two-pass iterative astronomical calculation (Egyptian General Authority of Survey:
     * Fajr 19.5°, Isha 17.5°, Sunrise/Maghrib 0.833°, Shafi'i Asr).
     */
    private fun computeHoursForDate(cal: Calendar, lat: Double, lon: Double, tz: Double): Map<String, Double> {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val jd = julianDate(y, m, d) - lon / (15.0 * 24.0)
        val shift = effectiveTz(cal, tz) - lon / 15.0

        // Pass 1: initial solar positions
        var fajr = sunAngleTime(jd, lat, 19.5, 5.0 / 24.0, true)
        var sunrise = sunAngleTime(jd, lat, 0.833, 6.0 / 24.0, true)
        var dhuhr = midDay(jd, 12.0 / 24.0)
        var asr = asrTime(jd, lat, 1, 13.0 / 24.0)
        var maghrib = sunAngleTime(jd, lat, 0.833, 18.0 / 24.0, false)
        var isha = sunAngleTime(jd, lat, 17.5, 18.0 / 24.0, false)

        // Pass 2: refine each prayer using its exact fractional day time
        fajr = sunAngleTime(jd, lat, 19.5, fajr / 24.0, true) + shift
        sunrise = sunAngleTime(jd, lat, 0.833, sunrise / 24.0, true) + shift
        dhuhr = midDay(jd, dhuhr / 24.0) + shift
        asr = asrTime(jd, lat, 1, asr / 24.0) + shift
        maghrib = sunAngleTime(jd, lat, 0.833, maghrib / 24.0, false) + shift
        isha = sunAngleTime(jd, lat, 17.5, isha / 24.0, false) + shift

        return mapOf(
            "Fajr" to fajr,
            "Sunrise" to sunrise,
            "Duha" to (sunrise + 15.0 / 60.0),
            "Dhuhr" to dhuhr,
            "Asr" to asr,
            "Maghrib" to maghrib,
            "Isha" to isha
        )
    }

    fun calculateForDate(cal: Calendar, lat: Double, lon: Double, tz: Double): LinkedHashMap<String, Calendar> {
        val hrs = computeHoursForDate(cal, lat, lon, tz)
        val map = linkedMapOf<String, Calendar>()
        for (k in listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")) {
            map[k] = hoursToCal(cal, hrs[k]!!)
        }
        return map
    }

    /**
     * Five obligatory prayers for today (used for Adhan & Prayer Lock)
     */
    fun calculateToday(lat: Double, lon: Double, tz: Double): LinkedHashMap<String, Calendar> {
        return calculateForDate(Calendar.getInstance(), lat, lon, tz)
    }

    /**
     * Complete daily schedule including Sunrise (الشروق) & Duha (صلاة الضحى = Sunrise + 15m)
     */
    fun calculateDisplayTimes(lat: Double, lon: Double, tz: Double): LinkedHashMap<String, Calendar> {
        val cal = Calendar.getInstance()
        val hrs = computeHoursForDate(cal, lat, lon, tz)
        val map = linkedMapOf<String, Calendar>()
        for (k in listOf("Fajr", "Sunrise", "Duha", "Dhuhr", "Asr", "Maghrib", "Isha")) {
            map[k] = hoursToCal(cal, hrs[k]!!)
        }
        return map
    }

    fun localizedName(enName: String, isAr: Boolean): String {
        if (!isAr) return enName
        return when (enName) {
            "Fajr" -> "الفجر"
            "Sunrise" -> "الشروق"
            "Duha" -> "صلاة الضحى"
            "Dhuhr" -> "الظهر"
            "Asr" -> "العصر"
            "Maghrib" -> "المغرب"
            "Isha" -> "العشاء"
            else -> enName
        }
    }

    fun getNextPrayer(lat: Double, lon: Double, tz: Double, isAr: Boolean): Pair<String, Long> {
        val now = System.currentTimeMillis()
        for ((name, cal) in calculateToday(lat, lon, tz)) {
            if (cal.timeInMillis > now) return Pair(localizedName(name, isAr), cal.timeInMillis)
        }
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
        val tomorrowFajr = calculateForDate(tomorrow, lat, lon, tz)["Fajr"]!!.timeInMillis
        return Pair(localizedName("Fajr", isAr), tomorrowFajr)
    }

    fun formatRemaining(diffMillis: Long, isAr: Boolean): String {
        val totalMin = ((diffMillis.coerceAtLeast(0L) + 59_999L) / 60_000L).toInt()
        val h = totalMin / 60
        val m = totalMin % 60
        return if (isAr) {
            when {
                totalMin <= 1 -> "أقل من دقيقة"
                h == 0 -> "$m دقيقة"
                m == 0 -> "$h ساعة"
                else -> "$h س $m د"
            }
        } else {
            when {
                totalMin <= 1 -> "< 1 min"
                h == 0 -> "${m}m"
                m == 0 -> "${h}h"
                else -> "${h}h ${m}m"
            }
        }
    }

    fun formatExactTime(millis: Long, isAr: Boolean): String {
        val raw = SimpleDateFormat("hh:mm a", Locale.US).format(Date(millis))
        return if (isAr) raw.replace("AM", "ص").replace("PM", "م") else raw
    }

    fun getActivePrayer(lat: Double, lon: Double, tz: Double, durationMin: Int): PrayerWindow? {
        val now = System.currentTimeMillis()
        val durMs = durationMin * 60_000L
        for ((name, cal) in calculateToday(lat, lon, tz)) {
            val start = cal.timeInMillis
            val end = start + durMs
            if (now in start until end) {
                return PrayerWindow(name, start, end)
            }
        }
        return null
    }
}
