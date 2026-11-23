package com.muslimtimekeeper.app.core

data class DhikrItem(
    val arText: String,
    val enText: String,
    val targetCount: Int
)

object AdhkarData {
    /**
     * Complete Authentic Post-Prayer Adhkar (أذكار بعد السلام من الصلاة المفروضة كاملة)
     */
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
            "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير، اللهم لا مانع لما أعطيت، ولا معطي لما منعت، ولا ينفع ذا الجد منك الجد",
            "None has the right to be worshipped but Allah alone; O Allah, none can prevent what You have willed to bestow and none can bestow what You have willed to prevent.",
            1
        ),
        DhikrItem(
            "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير، لا حول ولا قوة إلا بالله، لا إله إلا الله، ولا نعبد إلا إياه، له النعمة وله الفضل وله الثناء الحسن، لا إله إلا الله مخلصين له الدين ولو كره الكافرون",
            "There is no power nor might except with Allah. We worship none but Him; to Him belong all blessings, favors, and good praise.",
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
            "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير (تمام المائة)",
            "None has the right to be worshipped but Allah alone, without partner; to Him belongs all sovereignty and praise (completing 100).",
            1
        ),
        DhikrItem(
            "آية الكرسي: اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ",
            "Ayat al-Kursi (Surah Al-Baqarah 2:255): Allah! There is no deity except Him, the Ever-Living, the Sustainer of all existence...",
            1
        ),
        DhikrItem(
            "سورة الإخلاص: قُلْ هُوَ اللَّهُ أَحَدٌ ۝ اللَّهُ الصَّمَدُ ۝ لَمْ يَلِدْ وَلَمْ يُولَدْ ۝ وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ",
            "Surah Al-Ikhlas: Say, He is Allah, the One; Allah, the Eternal Refuge...",
            1
        ),
        DhikrItem(
            "سورة الفلق: قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ۝ مِن شَرِّ مَا خَلَقَ ۝ وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ ۝ وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ ۝ وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ",
            "Surah Al-Falaq: Say, I seek refuge in the Lord of daybreak...",
            1
        ),
        DhikrItem(
            "سورة الناس: قُلْ أَعُوذُ بِرَبِّ النَّاسِ ۝ مَلِكِ النَّاسِ ۝ إِلَٰهِ النَّاسِ ۝ مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ ۝ الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ ۝ مِنَ الْجِنَّةِ وَالنَّاسِ",
            "Surah An-Nas: Say, I seek refuge in the Lord of mankind...",
            1
        ),
        DhikrItem(
            "اللهم أعنّي على ذكرك وشكرك وحسن عبادتك",
            "O Allah, help me to remember You, to give thanks to You, and to worship You in the best of manners.",
            1
        ),
        DhikrItem(
            "اللهم إني أسألك علماً نافعاً، ورزقاً طيباً، وعملاً متقبلاً",
            "O Allah, I ask You for beneficial knowledge, goodly provision, and acceptable deeds.",
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
