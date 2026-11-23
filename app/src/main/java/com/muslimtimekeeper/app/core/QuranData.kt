package com.muslimtimekeeper.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class SurahInfo(
    val number: Int,
    val nameAr: String,
    val nameEn: String,
    val startPage: Int,
    val ayahs: Int,
    val isMakki: Boolean
)

/**
 * Authentic King Fahd Complex Madinah Mushaf (مصحف المدينة المنورة الأصلي — 604 صفحة)
 * Complete 114-Surah index, 30-Juz index, and local-caching page loader.
 */
object QuranData {
    val surahs: List<SurahInfo> = listOf(
        SurahInfo(1, "الفاتحة", "Al-Fatihah", 1, 7, true),
        SurahInfo(2, "البقرة", "Al-Baqarah", 2, 286, false),
        SurahInfo(3, "آل عمران", "Aal-Imran", 50, 200, false),
        SurahInfo(4, "النساء", "An-Nisa", 77, 176, false),
        SurahInfo(5, "المائدة", "Al-Ma'idah", 106, 120, false),
        SurahInfo(6, "الأنعام", "Al-An'am", 128, 165, true),
        SurahInfo(7, "الأعراف", "Al-A'raf", 151, 206, true),
        SurahInfo(8, "الأنفال", "Al-Anfal", 177, 75, false),
        SurahInfo(9, "التوبة", "At-Tawbah", 187, 129, false),
        SurahInfo(10, "يونس", "Yunus", 208, 109, true),
        SurahInfo(11, "هود", "Hud", 221, 123, true),
        SurahInfo(12, "يوسف", "Yusuf", 235, 111, true),
        SurahInfo(13, "الرعد", "Ar-Ra'd", 249, 43, false),
        SurahInfo(14, "إبراهيم", "Ibrahim", 255, 52, true),
        SurahInfo(15, "الحجر", "Al-Hijr", 262, 99, true),
        SurahInfo(16, "النحل", "An-Nahl", 267, 128, true),
        SurahInfo(17, "الإسراء", "Al-Isra", 282, 111, true),
        SurahInfo(18, "الكهف", "Al-Kahf", 293, 110, true),
        SurahInfo(19, "مريم", "Maryam", 305, 98, true),
        SurahInfo(20, "طه", "Taha", 312, 135, true),
        SurahInfo(21, "الأنبياء", "Al-Anbiya", 322, 112, true),
        SurahInfo(22, "الحج", "Al-Hajj", 332, 78, false),
        SurahInfo(23, "المؤمنون", "Al-Mu'minun", 342, 118, true),
        SurahInfo(24, "النور", "An-Nur", 350, 64, false),
        SurahInfo(25, "الفرقان", "Al-Furqan", 359, 77, true),
        SurahInfo(26, "الشعراء", "Ash-Shu'ara", 367, 227, true),
        SurahInfo(27, "النمل", "An-Naml", 377, 93, true),
        SurahInfo(28, "القصص", "Al-Qasas", 385, 88, true),
        SurahInfo(29, "العنكبوت", "Al-Ankabut", 396, 69, true),
        SurahInfo(30, "الروم", "Ar-Rum", 404, 60, true),
        SurahInfo(31, "لقمان", "Luqman", 411, 34, true),
        SurahInfo(32, "السجدة", "As-Sajdah", 415, 30, true),
        SurahInfo(33, "الأحزاب", "Al-Ahzab", 418, 73, false),
        SurahInfo(34, "سبأ", "Saba", 428, 54, true),
        SurahInfo(35, "فاطر", "Fatir", 434, 45, true),
        SurahInfo(36, "يس", "Ya-Sin", 440, 83, true),
        SurahInfo(37, "الصافات", "As-Saffat", 446, 182, true),
        SurahInfo(38, "ص", "Sad", 453, 88, true),
        SurahInfo(39, "الزمر", "Az-Zumar", 458, 75, true),
        SurahInfo(40, "غافر", "Ghafir", 467, 85, true),
        SurahInfo(41, "فصلت", "Fussilat", 477, 54, true),
        SurahInfo(42, "الشورى", "Ash-Shura", 483, 53, true),
        SurahInfo(43, "الزخرف", "Az-Zukhruf", 489, 89, true),
        SurahInfo(44, "الدخان", "Ad-Dukhan", 496, 59, true),
        SurahInfo(45, "الجاثية", "Al-Jathiyah", 499, 37, true),
        SurahInfo(46, "الأحقاف", "Al-Ahqaf", 502, 35, true),
        SurahInfo(47, "محمد", "Muhammad", 507, 38, false),
        SurahInfo(48, "الفتح", "Al-Fath", 511, 29, false),
        SurahInfo(49, "الحجرات", "Al-Hujurat", 515, 18, false),
        SurahInfo(50, "ق", "Qaf", 518, 45, true),
        SurahInfo(51, "الذاريات", "Adh-Dhariyat", 520, 60, true),
        SurahInfo(52, "الطور", "At-Tur", 523, 49, true),
        SurahInfo(53, "النجم", "An-Najm", 526, 62, true),
        SurahInfo(54, "القمر", "Al-Qamar", 528, 55, true),
        SurahInfo(55, "الرحمن", "Ar-Rahman", 531, 78, false),
        SurahInfo(56, "الواقعة", "Al-Waqi'ah", 534, 96, true),
        SurahInfo(57, "الحديد", "Al-Hadid", 537, 29, false),
        SurahInfo(58, "المجادلة", "Al-Mujadila", 542, 22, false),
        SurahInfo(59, "الحشر", "Al-Hashr", 545, 24, false),
        SurahInfo(60, "الممتحنة", "Al-Mumtahanah", 549, 13, false),
        SurahInfo(61, "الصف", "As-Saf", 551, 14, false),
        SurahInfo(62, "الجمعة", "Al-Jumu'ah", 553, 11, false),
        SurahInfo(63, "المنافقون", "Al-Munafiqun", 554, 11, false),
        SurahInfo(64, "التغابن", "At-Taghabun", 556, 18, false),
        SurahInfo(65, "الطلاق", "At-Talaq", 558, 12, false),
        SurahInfo(66, "التحريم", "At-Tahrim", 560, 12, false),
        SurahInfo(67, "الملك", "Al-Mulk", 562, 30, true),
        SurahInfo(68, "القلم", "Al-Qalam", 564, 52, true),
        SurahInfo(69, "الحاقة", "Al-Haqqah", 566, 52, true),
        SurahInfo(70, "المعارج", "Al-Ma'arij", 568, 44, true),
        SurahInfo(71, "نوح", "Nuh", 570, 28, true),
        SurahInfo(72, "الجن", "Al-Jinn", 572, 28, true),
        SurahInfo(73, "المزمل", "Al-Muzzammil", 574, 20, true),
        SurahInfo(74, "المدثر", "Al-Muddaththir", 575, 56, true),
        SurahInfo(75, "القيامة", "Al-Qiyamah", 577, 40, true),
        SurahInfo(76, "الإنسان", "Al-Insan", 578, 31, false),
        SurahInfo(77, "المرسلات", "Al-Mursalat", 580, 50, true),
        SurahInfo(78, "النبأ", "An-Naba", 582, 40, true),
        SurahInfo(79, "النازعات", "An-Nazi'at", 583, 46, true),
        SurahInfo(80, "عبس", "Abasa", 585, 42, true),
        SurahInfo(81, "التكوير", "At-Takwir", 586, 29, true),
        SurahInfo(82, "الانفطار", "Al-Infitar", 587, 19, true),
        SurahInfo(83, "المطففين", "Al-Mutaffifin", 587, 36, true),
        SurahInfo(84, "الانشقاق", "Al-Inshiqaq", 589, 25, true),
        SurahInfo(85, "البروج", "Al-Buruj", 590, 22, true),
        SurahInfo(86, "الطارق", "At-Tariq", 591, 17, true),
        SurahInfo(87, "الأعلى", "Al-A'la", 591, 19, true),
        SurahInfo(88, "الغاشية", "Al-Ghashiyah", 592, 26, true),
        SurahInfo(89, "الفجر", "Al-Fajr", 593, 30, true),
        SurahInfo(90, "البلد", "Al-Balad", 594, 20, true),
        SurahInfo(91, "الشمس", "Ash-Shams", 595, 15, true),
        SurahInfo(92, "الليل", "Al-Layl", 595, 21, true),
        SurahInfo(93, "الضحى", "Ad-Duha", 596, 11, true),
        SurahInfo(94, "الشرح", "Ash-Sharh", 596, 8, true),
        SurahInfo(95, "التين", "At-Tin", 597, 8, true),
        SurahInfo(96, "العلق", "Al-Alaq", 597, 19, true),
        SurahInfo(97, "القدر", "Al-Qadr", 598, 5, true),
        SurahInfo(98, "البينة", "Al-Bayyinah", 598, 8, false),
        SurahInfo(99, "الزلزلة", "Az-Zalzalah", 599, 8, false),
        SurahInfo(100, "العاديات", "Al-Adiyat", 599, 11, true),
        SurahInfo(101, "القارعة", "Al-Qari'ah", 600, 11, true),
        SurahInfo(102, "التكاثر", "At-Takathur", 600, 8, true),
        SurahInfo(103, "العصر", "Al-Asr", 601, 3, true),
        SurahInfo(104, "الهمزة", "Al-Humazah", 601, 9, true),
        SurahInfo(105, "الفيل", "Al-Fil", 601, 5, true),
        SurahInfo(106, "قريش", "Quraysh", 602, 4, true),
        SurahInfo(107, "الماعون", "Al-Ma'un", 602, 7, true),
        SurahInfo(108, "الكوثر", "Al-Kawthar", 602, 3, true),
        SurahInfo(109, "الكافرون", "Al-Kafirun", 603, 6, true),
        SurahInfo(110, "النصر", "An-Nasr", 603, 3, false),
        SurahInfo(111, "المسد", "Al-Masad", 603, 5, true),
        SurahInfo(112, "الإخلاص", "Al-Ikhlas", 604, 4, true),
        SurahInfo(113, "الفلق", "Al-Falaq", 604, 5, true),
        SurahInfo(114, "الناس", "An-Nas", 604, 6, true)
    )

    val juzStartPages: IntArray = intArrayOf(
        1, 22, 42, 62, 82, 102, 121, 142, 162, 182,
        201, 222, 242, 262, 282, 302, 322, 342, 362, 382,
        402, 422, 442, 462, 482, 502, 522, 542, 562, 582
    )

    fun getSurahForPage(page: Int): SurahInfo {
        var matched = surahs.first()
        for (s in surahs) {
            if (s.startPage <= page) matched = s else break
        }
        return matched
    }

    fun getJuzForPage(page: Int): Int {
        var juz = 1
        for (i in juzStartPages.indices) {
            if (juzStartPages[i] <= page) juz = i + 1 else break
        }
        return juz
    }

    /**
     * Loads authentic Madinah Mushaf page (1..604) with permanent local disk caching so viewed pages work offline!
     */
    fun loadMushafPage(context: Context, pageNum: Int): Bitmap? {
        val p = pageNum.coerceIn(1, 604)
        val cacheDir = File(context.filesDir, "mushaf_madani")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val file = File(cacheDir, String.format("page%03d.png", p))
        if (file.exists() && file.length() > 5000L) {
            return BitmapFactory.decodeFile(file.absolutePath)
        }

        val padded = String.format("%03d", p)
        val urls = listOf(
            "https://android.quran.com/data/width_1024/page$padded.png",
            "https://cdn.islamic.network/quran/images/high-resolution/page$p.png"
        )
        for (u in urls) {
            try {
                val conn = (URL(u).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "MuslimTimeKeeper-Android")
                }
                if (conn.responseCode == 200) {
                    val bytes = conn.inputStream.readBytes()
                    if (bytes.size > 5000) {
                        FileOutputStream(file).use { it.write(bytes) }
                        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                }
            } catch (_: Exception) { }
        }
        return null
    }
}
