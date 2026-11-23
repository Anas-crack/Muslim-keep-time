package com.muslimtimekeeper.app.core

data class DhikrItem(
    val arText: String,
    val enText: String,
    val targetCount: Int
)

data class TasbeehItem(
    val titleAr: String,
    val titleEn: String,
    val textAr: String,
    val textEn: String,
    val fadlAr: String,
    val fadlEn: String,
    val target: Int
)

object AdhkarData {
    /**
     * Complete Authentic Post-Prayer Adhkar (أذكار بعد السلام من الصلاة المفروضة كاملة)
     */
    val postPrayerAdhkar: List<DhikrItem> = listOf(
        DhikrItem("أستغفرُ اللهَ العظيم", "Astaghfirullah (I seek forgiveness from Allah)", 3),
        DhikrItem("اللهم أنت السلام ومنك السلام، تباركت يا ذا الجلال والإكرام", "O Allah, You are Peace and from You comes peace. Blessed are You, O Possessor of majesty and honor.", 1),
        DhikrItem("لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير، اللهم لا مانع لما أعطيت، ولا معطي لما منعت، ولا ينفع ذا الجد منك الجد", "None has the right to be worshipped but Allah alone; O Allah, none can prevent what You bestow.", 1),
        DhikrItem("لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير، لا حول ولا قوة إلا بالله، لا إله إلا الله، ولا نعبد إلا إياه، له النعمة وله الفضل وله الثناء الحسن، لا إله إلا الله مخلصين له الدين ولو كره الكافرون", "There is no power nor might except with Allah. We worship none but Him.", 1),
        DhikrItem("سبحانَ الله", "SubhanAllah (Glory be to Allah)", 33),
        DhikrItem("الحمدُ لله", "Alhamdulillah (All praise is due to Allah)", 33),
        DhikrItem("اللهُ أكبر", "Allahu Akbar (Allah is the Greatest)", 33),
        DhikrItem("لا إله إلا الله وحده لا شريك له، له الملك وله الحمد وهو على كل شيء قدير (تمام المائة)", "None has the right to be worshipped but Allah alone (completing 100).", 1),
        DhikrItem("آية الكرسي: اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ", "Ayat al-Kursi (Surah Al-Baqarah 2:255)", 1),
        DhikrItem("سورة الإخلاص: قُلْ هُوَ اللَّهُ أَحَدٌ ۝ اللَّهُ الصَّمَدُ ۝ لَمْ يَلِدْ وَلَمْ يُولَدْ ۝ وَلَمْ يَكُن لَّهُ كُفُوًا أَحَدٌ", "Surah Al-Ikhlas", 1),
        DhikrItem("سورة الفلق: قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ۝ مِن شَرِّ مَا خَلَقَ ۝ وَمِن شَرِّ غَاسِقٍ إِذَا وَقَبَ ۝ وَمِن شَرِّ النَّفَّاثَاتِ فِي الْعُقَدِ ۝ وَمِن شَرِّ حَاسِدٍ إِذَا حَسَدَ", "Surah Al-Falaq", 1),
        DhikrItem("سورة الناس: قُلْ أَعُوذُ بِرَبِّ النَّاسِ ۝ مَلِكِ النَّاسِ ۝ إِلَٰهِ النَّاسِ ۝ مِن شَرِّ الْوَسْوَاسِ الْخَنَّاسِ ۝ الَّذِي يُوَسْوِسُ فِي صُدُورِ النَّاسِ ۝ مِنَ الْجِنَّةِ وَالنَّاسِ", "Surah An-Nas", 1),
        DhikrItem("اللهم أعنّي على ذكرك وشكرك وحسن عبادتك", "O Allah, help me to remember You, thank You, and worship You well.", 1),
        DhikrItem("اللهم إني أسألك علماً نافعاً، ورزقاً طيباً، وعملاً متقبلاً", "O Allah, I ask You for beneficial knowledge, goodly provision, and acceptable deeds.", 1)
    )

    /**
     * Rich Library of 16 Authentic Adhkar & Tasbeehat with Virtues (فضل الذكر)
     */
    val tasbeehLibrary: List<TasbeehItem> = listOf(
        TasbeehItem(
            "سبحان الله", "SubhanAllah",
            "سُبْحَانَ اللَّهِ", "SubhanAllah (Glory be to Allah)",
            "قال النبي ﷺ: «أَيَعْجِزُ أَحَدُكُمْ أَنْ يَكْسِبَ كُلَّ يَوْمٍ أَلْفَ حَسَنَةٍ؟ يُسَبِّحُ مِائَةَ تَسْبِيحَةٍ، فَيُكْتَبُ لَهُ أَلْفُ حَسَنَةٍ أَوْ يُحَطُّ عَنْهُ أَلْفُ خَطِيئَةٍ» (رواه مسلم).",
            "Saying SubhanAllah 100 times earns 1,000 good deeds or erases 1,000 sins (Sahih Muslim).",
            33
        ),
        TasbeehItem(
            "الحمد لله", "Alhamdulillah",
            "الْحَمْدُ لِلَّهِ", "Alhamdulillah (All praise is due to Allah)",
            "قال النبي ﷺ: «الطُّهُورُ شَطْرُ الإِيمَانِ، وَالْحَمْدُ لِلَّهِ تَمْلَأُ الْمِيزَانَ» (رواه مسلم).",
            "Alhamdulillah fills the scale of good deeds (Sahih Muslim).",
            33
        ),
        TasbeehItem(
            "الله أكبر", "Allahu Akbar",
            "اللَّهُ أَكْبَرُ", "Allahu Akbar (Allah is the Greatest)",
            "من أحب الكلام إلى الله، وسبب لمغفرة الذنوب ورفع الدرجات وزيادة الطمأنينة.",
            "Among the most beloved words to Allah, bringing peace and elevated rank.",
            33
        ),
        TasbeehItem(
            "لا إله إلا الله وحده لا شريك له", "La ilaha illallah",
            "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ",
            "None has the right to be worshipped but Allah alone, without partner; to Him belongs all sovereignty and praise.",
            "من قالها في يوم مائة مرة كانت له عدل عشر رقاب، وكُتبت له مائة حسنة، ومُحيت عنه مائة سيئة، وكانت له حرزاً من الشيطان (متفق عليه).",
            "Saying this 100 times a day equals freeing 10 slaves, writes 100 good deeds, erases 100 sins, and protects from Shaytan.",
            100
        ),
        TasbeehItem(
            "سبحان الله وبحمده", "SubhanAllahi wa bihamdihi",
            "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", "Glory and praise be to Allah",
            "قال النبي ﷺ: «مَنْ قَالَ سُبْحَانَ اللَّهِ وَبِحَمْدِهِ فِي يَوْمٍ مِائَةَ مَرَّةٍ حُطَّتْ خَطَايَاهُ وَإِنْ كَانَتْ مِثْلَ زَبَدِ الْبَحْرِ» (متفق عليه).",
            "Whoever says this 100 times a day has their sins forgiven even if like the foam of the sea.",
            100
        ),
        TasbeehItem(
            "سبحان الله وبحمده سبحان الله العظيم", "SubhanAllah Al-Azim",
            "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ، سُبْحَانَ اللَّهِ الْعَظِيمِ",
            "Glory and praise be to Allah, Glory be to Allah the Supreme",
            "قال النبي ﷺ: «كَلِمَتَانِ خَفِيفَتَانِ عَلَى اللِّسَانِ، ثَقِيلَتَانِ فِي الْمِيزَانِ، حَبِيبَتَانِ إِلَى الرَّحْمَنِ» (متفق عليه).",
            "Two phrases light on the tongue, heavy on the scale, beloved to the Most Merciful.",
            33
        ),
        TasbeehItem(
            "أستغفر الله العظيم وأتوب إليه", "Istighfar",
            "أَسْتَغْفِرُ اللَّهَ الْعَظِيمَ وَأَتُوبُ إِلَيْهِ",
            "I seek forgiveness from Allah the Almighty and repent to Him",
            "الاستغفار سبب لتفريج الهموم، وجلب الرزق، ومغفرة الذنوب، وكان النبي ﷺ يستغفر في اليوم أكثر من سبعين مرة.",
            "Seeking forgiveness brings relief from worries, provision, and forgiveness.",
            100
        ),
        TasbeehItem(
            "الصلاة على النبي ﷺ", "Salawat on Prophet",
            "اللَّهُمَّ صَلِّ وَسَلِّمْ وَبَارِكْ عَلَى نَبِيِّنَا مُحَمَّدٍ",
            "O Allah, send peace and blessings upon our Prophet Muhammad",
            "قال النبي ﷺ: «مَنْ صَلَّى عَلَيَّ صَلَاةً وَاحِدَةً صَلَّى اللَّهُ عَلَيْهِ بِهَا عَشْرًا» (رواه مسلم).",
            "Whoever sends one blessing upon the Prophet, Allah sends ten upon them (Sahih Muslim).",
            100
        ),
        TasbeehItem(
            "لا حول ولا قوة إلا بالله", "Hawqalah",
            "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ",
            "There is no power nor might except with Allah",
            "قال النبي ﷺ: «يَا عَبْدَ اللَّهِ بْنَ قَيْسٍ، أَلَا أَدُلُّكَ عَلَى كَنْزٍ مِنْ كُنُوزِ الْجَنَّةِ؟ لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ» (متفق عليه).",
            "A treasure from the treasures of Paradise (Bukhari & Muslim).",
            33
        ),
        TasbeehItem(
            "كلمات المغفرة (حديث علي رضي الله عنه)", "Words of Forgiveness",
            "لَا إِلَهَ إِلَّا اللَّهُ الْعَلِيُّ الْعَظِيمُ، لَا إِلَهَ إِلَّا اللَّهُ الْحَلِيمُ الْكَرِيمُ، سُبْحَانَ اللَّهِ رَبِّ السَّمَاوَاتِ السَّبْعِ وَرَبِّ الْعَرْشِ الْعَظِيمِ، وَالْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
            "None is worthy of worship except Allah the Most High, the Supreme, the Forbearing, the Generous; Glory be to Allah Lord of the seven heavens and the Mighty Throne.",
            "عن علي بن أبي طالب رضي الله عنه قال: قال لي رسول الله ﷺ: «يَا عَلِيُّ، أَلَا أُعَلِّمُكَ كَلِمَاتٍ إِذَا قُلْتَهُنَّ غُفِرَ لَكَ مَعَ أَنَّهُ مَغْفُورٌ لَكَ» (مسند أحمد وابن حبان).",
            "Taught by the Prophet ﷺ to Ali (may Allah be pleased with him) as words of immense forgiveness.",
            100
        ),
        TasbeehItem(
            "دعوة ذي النون (يونس عليه السلام)", "Dua of Yunus",
            "لَا إِلَهَ إِلَّا أَنْتَ سُبْحَانَكَ إِنِّي كُنْتُ مِنَ الظَّالِمِينَ",
            "There is no deity except You; exalted are You. Indeed, I have been of the wrongdoers.",
            "قال النبي ﷺ: «فَإِنَّهُ لَمْ يَدْعُ بِهَا رَجُلٌ مُسْلِمٌ فِي شَيْءٍ قَطُّ إِلَّا اسْتَجَابَ اللَّهُ لَهُ» (رواه الترمذي).",
            "No Muslim supplicates with it for anything except that Allah responds to them (Tirmidhi).",
            33
        ),
        TasbeehItem(
            "الباقيات الصالحات", "Al-Baqiyat As-Salihat",
            "سُبْحَانَ اللَّهِ، وَالْحَمْدُ لِلَّهِ، وَلَا إِلَهَ إِلَّا اللَّهُ، وَاللَّهُ أَكْبَرُ",
            "Glory be to Allah, praise be to Allah, none is worthy of worship but Allah, Allah is the Greatest",
            "قال النبي ﷺ: «أَحَبُّ الْكَلَامِ إِلَى اللَّهِ أَرْبَعٌ: سُبْحَانَ اللَّهِ، وَالْحَمْدُ لِلَّهِ، وَلَا إِلَهَ إِلَّا اللَّهُ، وَاللَّهُ أَكْبَرُ» (رواه مسلم).",
            "The four most beloved words to Allah (Sahih Muslim).",
            33
        ),
        TasbeehItem(
            "حسبي الله لا إله إلا هو", "HasbiyAllahu",
            "حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ",
            "Allah is sufficient for me; there is no deity except Him. On Him I rely, and He is the Lord of the Great Throne.",
            "من قالها حين يصبح وحين يمسي سبع مرات كفاه الله ما أهمه من أمر الدنيا والآخرة.",
            "Saying it 7 times morning and evening suffices for all worldly and hereafter worries.",
            7
        ),
        TasbeehItem(
            "يا حي يا قيوم برحمتك أستغيث", "Ya Hayyu Ya Qayyum",
            "يَا حَيُّ يَا قَيُّومُ بِرَحْمَتِكَ أَسْتَغِيثُ، أَصْلِحْ لِي شَأْنِي كُلَّهُ وَلَا تَكِلْنِي إِلَى نَفْسِي طَرْفَةَ عَيْنٍ",
            "O Ever-Living, O Sustainer, in Your mercy I seek relief; rectify all my affairs and do not leave me to myself for the blink of an eye.",
            "من جوامع الدعاء النبوي لطلب التوفيق والعون الإلهي في كل شؤون الحياة.",
            "A comprehensive prophetic supplication for divine guidance and ease in all affairs.",
            3
        ),
        TasbeehItem(
            "رضيت بالله رباً", "Radeetu Billahi",
            "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ ﷺ نَبِيًّا وَرَسُولًا",
            "I am pleased with Allah as my Lord, Islam as my religion, and Muhammad ﷺ as my Prophet.",
            "قال النبي ﷺ: «مَنْ قَالَ رَضِيتُ بِاللَّهِ رَبًّا وَبِالْإِسْلَامِ دِينًا وَبِمُحَمَّدٍ نَبِيًّا وَجَبَتْ لَهُ الْجَنَّةُ» (أبو داود).",
            "Whoever says this is promised Paradise (Abu Dawud).",
            3
        ),
        TasbeehItem(
            "اللهم إنك عفو تحب العفو فاعف عني", "Dua of Forgiveness",
            "اللَّهُمَّ إِنَّكَ عَفُوٌّ تُحِبُّ الْعَفْوَ فَاعْفُ عَنِّي",
            "O Allah, You are Forgiving and love forgiveness, so forgive me.",
            "أعظم دعاء علّمه النبي ﷺ للسيدة عائشة رضي الله عنها، يجمع خيري الدنيا والآخرة.",
            "The supplication taught by the Prophet ﷺ to Aisha (may Allah be pleased with her).",
            33
        )
    )
}
