package com.muslimtimekeeper.app.core

data class DhikrItem(
    val arText: String,
    val enText: String,
    val targetCount: Int
)

object AdhkarData {
    val postPrayerAdhkar: List<DhikrItem> = listOf(
        DhikrItem(
            "أستغفرُ اللهَ العظيم",
            "Astaghfirullah (I seek forgiveness from Allah)",
            3
        ),
        DhikrItem(
            "اللهم أنت السلام ومنك السلام، تباركت يا ذا الجلال والإكرام",
            "O Allah, You are Peace and from You comes peace. Blessed are You, O Possessor of majesty and honor.",
            1
        ),
        DhikrItem(
            "سبحانَ الله",
            "SubhanAllah (Glory be to Allah)",
            33
        ),
        DhikrItem(
            "الحمدُ لله",
            "Alhamdulillah (All praise is due to Allah)",
            33
        ),
        DhikrItem(
            "اللهُ أكبر",
            "Allahu Akbar (Allah is the Greatest)",
            33
        ),
        DhikrItem(
            "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير",
            "None has the right to be worshipped but Allah alone, without partner; to Him belongs all sovereignty and praise, and He is over all things omnipotent.",
            1
        ),
        DhikrItem(
            "آية الكرسي: ﴿ اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ... ﴾",
            "Recite Ayat al-Kursi (Surah Al-Baqarah 2:255)",
            1
        )
    )

    val tasbeehPhrasesAr: List<String> = listOf(
        "سبحان الله",
        "الحمد لله",
        "لا إله إلا الله",
        "الله أكبر",
        "أستغفر الله وأتوب إليه",
        "اللهم صلِّ وسلِّم على نبينا محمد"
    )

    val tasbeehPhrasesEn: List<String> = listOf(
        "SubhanAllah",
        "Alhamdulillah",
        "La ilaha illallah",
        "Allahu Akbar",
        "Astaghfirullah",
        "Allahumma salli ala Muhammad"
    )
}
