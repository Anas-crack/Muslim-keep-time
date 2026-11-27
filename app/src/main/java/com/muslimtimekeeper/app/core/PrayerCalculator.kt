package com.muslimtimekeeper.app.core

import android.icu.util.IslamicCalendar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

data class PrayerWindow(val name: String, val startMillis: Long, val endMillis: Long)
data class PrayerProgressInfo(val nextName: String, val prevMillis: Long, val nextMillis: Long)

object PrayerCalculator {
    val presetCities = linkedMapOf(
        "الزقازيق، مصر (Zagazig)" to doubleArrayOf(30.5877, 31.5020, 2.0),
        "القاهرة، مصر (Cairo)" to doubleArrayOf(30.0444, 31.2357, 2.0),
        "الإسكندرية، مصر (Alexandria)" to doubleArrayOf(31.2001, 29.9187, 2.0),
        "الجزائر، الجزائر (Algiers)" to doubleArrayOf(36.7538, 3.0588, 1.0),
        "وهران، الجزائر (Oran)" to doubleArrayOf(35.6971, -0.6308, 1.0),
        "الدار البيضاء، المغرب (Casablanca)" to doubleArrayOf(33.5731, -7.5898, 1.0),
        "تونس، تونس (Tunis)" to doubleArrayOf(36.8065, 10.1815, 1.0),
        "الرياض، السعودية (Riyadh)" to doubleArrayOf(24.7136, 46.6753, 3.0),
        "مكة المكرمة (Makkah)" to doubleArrayOf(21.4225, 39.8262, 3.0),
        "المدينة المنورة (Madinah)" to doubleArrayOf(24.4672, 39.6112, 3.0),
        "دبي، الإمارات (Dubai)" to doubleArrayOf(25.2048, 55.2708, 4.0),
        "عمّان، الأردن (Amman)" to doubleArrayOf(31.9454, 35.9284, 3.0),
        "القدس، فلسطين (Jerusalem)" to doubleArrayOf(31.7683, 35.2137, 2.0),
        "بغداد، العراق (Baghdad)" to doubleArrayOf(33.3152, 44.3661, 3.0),
        "إسطنبول، تركيا (Istanbul)" to doubleArrayOf(41.0082, 28.9784, 3.0),
        "جاكرتا، إندونيسيا (Jakarta)" to doubleArrayOf(-6.2088, 106.8456, 7.0),
        "كوالالمبور، ماليزيا (Kuala Lumpur)" to doubleArrayOf(3.1390, 101.6869, 8.0),
        "باريس، فرنسا (Paris)" to doubleArrayOf(48.8566, 2.3522, 1.0),
        "لندن، بريطانيا (London)" to doubleArrayOf(51.5074, -0.1278, 0.0),
        "نيويورك، أمريكا (New York)" to doubleArrayOf(40.7128, -74.0060, -5.0)
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

        // Auto-select standard Fajr/Isha angles by world region (Egypt 19.5/17.5, Gulf 18.5/18, ISNA 15/15, Algeria/MWL/Europe 18/17)
        val (fajrAng, ishaAng) = when {
            lat in 21.5..32.0 && lon in 24.5..36.0 -> Pair(19.5, 17.5) // Egypt
            lat in 12.0..32.0 && lon in 36.0..60.0 -> Pair(18.5, 18.0) // Arabian Peninsula
            lon < -30.0 -> Pair(15.0, 15.0)                            // Americas (ISNA)
            else -> Pair(18.0, 17.0)                                   // Algeria, Morocco, Tunisia, Turkey, Europe, Asia
        }

        // Pass 1: initial solar positions
        var fajr = sunAngleTime(jd, lat, fajrAng, 5.0 / 24.0, true)
        var sunrise = sunAngleTime(jd, lat, 0.833, 6.0 / 24.0, true)
        var dhuhr = midDay(jd, 12.0 / 24.0)
        var asr = asrTime(jd, lat, 1, 13.0 / 24.0)
        var maghrib = sunAngleTime(jd, lat, 0.833, 18.0 / 24.0, false)
        var isha = sunAngleTime(jd, lat, ishaAng, 18.0 / 24.0, false)

        // Pass 2: refine each prayer using its exact fractional day time
        fajr = sunAngleTime(jd, lat, fajrAng, fajr / 24.0, true) + shift
        sunrise = sunAngleTime(jd, lat, 0.833, sunrise / 24.0, true) + shift
        dhuhr = midDay(jd, dhuhr / 24.0) + shift
        asr = asrTime(jd, lat, 1, asr / 24.0) + shift
        maghrib = sunAngleTime(jd, lat, 0.833, maghrib / 24.0, false) + shift
        isha = sunAngleTime(jd, lat, ishaAng, isha / 24.0, false) + shift

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

    fun getActiveAdhan(lat: Double, lon: Double, tz: Double): PrayerWindow? {
        val now = System.currentTimeMillis()
        for ((name, cal) in calculateToday(lat, lon, tz)) {
            val start = cal.timeInMillis
            val end = start + 60_000L
            if (now in start until end) {
                return PrayerWindow(name, start, end)
            }
        }
        return null
    }

    /**
     * Prayer lock window begins at IQAMA time (Adhan + iqamaDelayMin) and lasts durationMin minutes.
     */
    fun getActivePrayer(lat: Double, lon: Double, tz: Double, durationMin: Int, iqamaDelayMin: Int = 15): PrayerWindow? {
        val now = System.currentTimeMillis()
        val iqamaOffsetMs = iqamaDelayMin.coerceIn(0, 45) * 60_000L
        val durMs = durationMin * 60_000L
        for ((name, cal) in calculateToday(lat, lon, tz)) {
            val start = cal.timeInMillis + iqamaOffsetMs
            val end = start + durMs
            if (now in start until end) {
                return PrayerWindow(name, start, end)
            }
        }
        return null
    }

    fun calculateQiblaBearing(lat: Double, lon: Double): Float {
        val kaabaLat = dtr(21.422487)
        val kaabaLon = dtr(39.826206)
        val uLat = dtr(lat)
        val dLon = kaabaLon - dtr(lon)
        val y = sin(dLon) * cos(kaabaLat)
        val x = cos(uLat) * sin(kaabaLat) - sin(uLat) * cos(kaabaLat) * cos(dLon)
        return ((rtd(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    /**
     * Returns Pair(daysUntilRamadan, currentRamadanDay).
     * If currently in Ramadan (Hijri month index 8), daysUntilRamadan = 0 and currentRamadanDay = 1..30.
     */
    fun getRamadanCountdown(offsetDays: Int = 0): Pair<Int, Int> {
        return try {
            val now = getUmmAlQuraNow(offsetDays)
            val curMonth = now.get(IslamicCalendar.MONTH) // 0=Muharram .. 8=Ramadan
            val curDay = now.get(IslamicCalendar.DAY_OF_MONTH)
            val curYear = now.get(IslamicCalendar.YEAR)
            if (curMonth == 8) return Pair(0, curDay)
            val targetYear = if (curMonth < 8) curYear else curYear + 1
            val target = IslamicCalendar().apply {
                try { calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA } catch (_: Exception) { }
                set(IslamicCalendar.YEAR, targetYear)
                set(IslamicCalendar.MONTH, 8)
                set(IslamicCalendar.DAY_OF_MONTH, 1)
                set(IslamicCalendar.HOUR_OF_DAY, 0)
                set(IslamicCalendar.MINUTE, 0)
                set(IslamicCalendar.SECOND, 0)
            }
            val diffMs = target.timeInMillis - now.timeInMillis
            val days = ((diffMs + 86_399_999L) / 86_400_000L).toInt().coerceAtLeast(1)
            Pair(days, 0)
        } catch (_: Exception) {
            Pair(0, 0)
        }
    }

    fun getPrayerProgressWindow(lat: Double, lon: Double, tz: Double, isAr: Boolean): PrayerProgressInfo {
        val now = System.currentTimeMillis()
        val todayMap = calculateToday(lat, lon, tz).entries.toList()
        for (i in todayMap.indices) {
            val (name, cal) = todayMap[i]
            if (cal.timeInMillis > now) {
                val prevMs = if (i > 0) {
                    todayMap[i - 1].value.timeInMillis
                } else {
                    val yest = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
                    calculateForDate(yest, lat, lon, tz)["Isha"]!!.timeInMillis
                }
                return PrayerProgressInfo(localizedName(name, isAr), prevMs, cal.timeInMillis)
            }
        }
        val prevIsha = todayMap.last().value.timeInMillis
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
        val tomorrowFajr = calculateForDate(tomorrow, lat, lon, tz)["Fajr"]!!.timeInMillis
        return PrayerProgressInfo(localizedName("Fajr", isAr), prevIsha, tomorrowFajr)
    }

    fun formatCountdownHms(diffMillis: Long): String {
        val totalSec = (diffMillis.coerceAtLeast(0L) / 1000L).toInt()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }

    fun getUmmAlQuraNow(offsetDays: Int = 0): IslamicCalendar {
        val hc = IslamicCalendar()
        try {
            hc.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        } catch (_: Exception) { }
        hc.timeInMillis = System.currentTimeMillis() + (offsetDays * 86_400_000L)
        return hc
    }

    fun formatHijriDate(isAr: Boolean, offsetDays: Int = 0): String {
        return try {
            val hc = getUmmAlQuraNow(offsetDays)
            val day = hc.get(IslamicCalendar.DAY_OF_MONTH)
            val month = hc.get(IslamicCalendar.MONTH)
            val year = hc.get(IslamicCalendar.YEAR)
            val monthsAr = arrayOf(
                "محرم", "صفر", "ربيع الأول", "ربيع الثاني", "جمادى الأولى", "جمادى الثاني",
                "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
            )
            val monthsEn = arrayOf(
                "Muharram", "Safar", "Rabi I", "Rabi II", "Jumada I", "Jumada II",
                "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhul Qi'dah", "Dhul Hijjah"
            )
            val mName = if (month in 0..11) (if (isAr) monthsAr[month] else monthsEn[month]) else ""
            "$day $mName $year"
        } catch (_: Exception) {
            ""
        }
    }

    fun getCurrentSkyPhase(lat: Double, lon: Double, tz: Double): String {
        val now = System.currentTimeMillis()
        val t = calculateDisplayTimes(lat, lon, tz)
        val fajr = t["Fajr"]!!.timeInMillis
        val sunrise = t["Sunrise"]!!.timeInMillis
        val dhuhr = t["Dhuhr"]!!.timeInMillis
        val asr = t["Asr"]!!.timeInMillis
        val maghrib = t["Maghrib"]!!.timeInMillis
        val isha = t["Isha"]!!.timeInMillis
        return when {
            now < fajr -> "ISHA"
            now < sunrise -> "FAJR"
            now < dhuhr -> "SUNRISE"
            now < asr -> "DHUHR"
            now < maghrib -> "ASR"
            now < isha -> "MAGHRIB"
            else -> "ISHA"
        }
    }
}
