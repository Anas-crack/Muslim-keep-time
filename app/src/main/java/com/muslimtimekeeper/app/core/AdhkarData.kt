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

    /**
     * Authentic Morning Adhkar (أذكار الصباح)
     */
    val morningAdhkar: List<TasbeehItem> = listOf(
        TasbeehItem(
            "آية الكرسي", "Ayat al-Kursi",
            "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ",
            "Ayat al-Kursi (Surah Al-Baqarah 2:255)",
            "الدليل: من قالها حين يصبح أُجير من الجن حتى يمسي (رواه الحاكم وصححه الألباني).",
            "Evidence: Protects you from Jinn until evening.",
            1
        ),
        TasbeehItem(
            "المعوذات (الإخلاص والفلق والناس)", "Al-Ikhlas, Al-Falaq & An-Nas",
            "قُلْ هُوَ اللَّهُ أَحَدٌ ... / قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ... / قُلْ أَعُوذُ بِرَبِّ النَّاسِ ...",
            "Recite Surah Al-Ikhlas, Al-Falaq, and An-Nas (3 times each)",
            "الدليل: قال النبي ﷺ: «قل هو الله أحد والمعوذتين حين تمسي وحين تصبح ثلاث مرات تكفيك من كل شيء» (أبو داود والترمذي).",
            "Evidence: Suffices you against every harm (Abu Dawud & Tirmidhi).",
            3
        ),
        TasbeehItem(
            "أصبحنا وأصبح الملك لله", "Asbahna wa Asbahal-Mulk",
            "أَصْبَحْنَا وَأَصْبَحَ الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذَا الْيَوْمِ وَخَيْرَ مَا بَعْدَهُ، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذَا الْيَوْمِ وَشَرِّ مَا بَعْدَهُ",
            "We have reached the morning and at this very time unto Allah belongs all sovereignty...",
            "الدليل: كان النبي ﷺ يقوله كل صباح (رواه مسلم).",
            "Evidence: Recited every morning by the Prophet ﷺ (Sahih Muslim).",
            1
        ),
        TasbeehItem(
            "سيد الاستغفار", "Sayyidul Istighfar",
            "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ",
            "O Allah, You are my Lord, none has the right to be worshipped except You...",
            "الدليل: من قالها موقناً بها حين يصبح فمات من يومه دخل الجنة (رواه البخاري).",
            "Evidence: Whoever says it with conviction in the morning and passes away that day enters Paradise (Bukhari).",
            1
        ),
        TasbeehItem(
            "بسم الله الذي لا يضر مع اسمه شيء", "Bismillah Protection",
            "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ",
            "In the name of Allah, with whose name nothing on earth or in heaven can cause harm.",
            "الدليل: من قالها ثلاثاً إذا أصبح لم تصبه فجأة بلاء حتى يمسي (أبو داود والترمذي).",
            "Evidence: Nothing will harm you until evening (Abu Dawud & Tirmidhi).",
            3
        ),
        TasbeehItem(
            "اللهم بك أصبحنا", "Allahumma Bika Asbahna",
            "اللَّهُمَّ بِكَ أَصْبَحْنَا، وَبِكَ أَمْسَيْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ وَإِلَيْكَ النُّشُورُ",
            "O Allah, by Your leave we have reached the morning and by Your leave we reach the evening...",
            "الدليل: رواه الترمذي وصححه الألباني.",
            "Evidence: Narrated by At-Tirmidhi.",
            1
        ),
        TasbeehItem(
            "حسبي الله لا إله إلا هو", "HasbiyAllahu",
            "حَسْبِيَ اللَّهُ لَا إِلَهَ إِلَّا هُوَ عَلَيْهِ تَوَكَّلْتُ وَهُوَ رَبُّ الْعَرْشِ الْعَظِيمِ",
            "Allah is sufficient for me; on Him I rely.",
            "الدليل: من قالها حين يصبح سبع مرات كفاه الله ما أهمه من أمر الدنيا والآخرة (أبو داود).",
            "Evidence: Suffices for all worldly and hereafter concerns (Abu Dawud).",
            7
        ),
        TasbeehItem(
            "رضيت بالله رباً", "Radeetu Billahi",
            "رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ ﷺ نَبِيًّا",
            "I am pleased with Allah as my Lord, Islam as my religion, and Muhammad ﷺ as my Prophet.",
            "الدليل: كان حقاً على الله أن يرضيه يوم القيامة (أبو داود).",
            "Evidence: Allah promises to please them on the Day of Judgment (Abu Dawud).",
            3
        )
    )

    /**
     * Authentic Evening Adhkar (أذكار المساء)
     */
    val eveningAdhkar: List<TasbeehItem> = listOf(
        TasbeehItem(
            "آية الكرسي", "Ayat al-Kursi",
            "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ",
            "Ayat al-Kursi (Surah Al-Baqarah 2:255)",
            "الدليل: من قالها حين يمسي أُجير من الجن حتى يصبح (رواه الحاكم).",
            "Evidence: Protects you from Jinn until morning.",
            1
        ),
        TasbeehItem(
            "المعوذات ثلاثاً", "Al-Mu'awwidhat (3x)",
            "قُلْ هُوَ اللَّهُ أَحَدٌ ... / قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ ... / قُلْ أَعُوذُ بِرَبِّ النَّاسِ ...",
            "Recite Surah Al-Ikhlas, Al-Falaq, and An-Nas (3 times each)",
            "الدليل: تكفيك من كل شيء حتى تصبح (أبو داود والترمذي).",
            "Evidence: Suffices you against every harm until morning.",
            3
        ),
        TasbeehItem(
            "أمسينا وأمسى الملك لله", "Amsayna wa Amsal-Mulk",
            "أَمْسَيْنَا وَأَمْسَى الْمُلْكُ لِلَّهِ، وَالْحَمْدُ لِلَّهِ، لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، رَبِّ أَسْأَلُكَ خَيْرَ مَا فِي هَذِهِ اللَّيْلَةِ وَخَيْرَ مَا بَعْدَهَا، وَأَعُوذُ بِكَ مِنْ شَرِّ مَا فِي هَذِهِ اللَّيْلَةِ وَشَرِّ مَا بَعْدَهَا",
            "We have reached the evening and unto Allah belongs all sovereignty...",
            "الدليل: كان النبي ﷺ يقوله كل مساء (رواه مسلم).",
            "Evidence: Recited every evening by the Prophet ﷺ (Sahih Muslim).",
            1
        ),
        TasbeehItem(
            "سيد الاستغفار", "Sayyidul Istighfar",
            "اللَّهُمَّ أَنْتَ رَبِّي لَا إِلَهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلَى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ",
            "O Allah, You are my Lord, none has the right to be worshipped except You...",
            "الدليل: من قالها موقناً بها حين يمسي فمات من ليلته دخل الجنة (رواه البخاري).",
            "Evidence: Entering Paradise if one passes away that night (Bukhari).",
            1
        ),
        TasbeehItem(
            "أعوذ بكلمات الله التامات", "Protection from Harm",
            "أَعُوذُ بِكَلِمَاتِ اللَّهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ",
            "I seek refuge in the perfect words of Allah from the evil of what He created.",
            "الدليل: من قالها حين يمسي ثلاثاً لم يضره شيء تلك الليلة (رواه مسلم).",
            "Evidence: Nothing will harm you that night (Sahih Muslim).",
            3
        ),
        TasbeehItem(
            "بسم الله الذي لا يضر مع اسمه شيء", "Bismillah Protection",
            "بِسْمِ اللَّهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ",
            "In the name of Allah, with whose name nothing can cause harm.",
            "الدليل: من قالها ثلاثاً حين يمسي لم يضره شيء حتى يصبح (أبو داود).",
            "Evidence: Protects from sudden affliction until morning (Abu Dawud).",
            3
        ),
        TasbeehItem(
            "اللهم بك أمسينا", "Allahumma Bika Amsayna",
            "اللَّهُمَّ بِكَ أَمْسَيْنَا، وَبِكَ أَصْبَحْنَا، وَبِكَ نَحْيَا، وَبِكَ نَمُوتُ وَإِلَيْكَ الْمَصِيرُ",
            "O Allah, by Your leave we have reached the evening and unto You is our return.",
            "الدليل: رواه الترمذي وصححه الألباني.",
            "Evidence: Narrated by At-Tirmidhi.",
            1
        )
    )

    /**
     * Authentic Sleep Adhkar (أذكار النوم — مع الدلائل النبوية)
     */
    val sleepAdhkar: List<TasbeehItem> = listOf(
        TasbeehItem(
            "آية الكرسي قبل النوم", "Ayat al-Kursi Before Sleep",
            "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ ۚ لَا تَأْخُذُهُ سِنَةٌ وَلَا نَوْمٌ ۚ لَّهُ مَا فِي السَّمَاوَاتِ وَمَا فِي الْأَرْضِ ۗ مَن ذَا الَّذِي يَشْفَعُ عِندَهُ إِلَّا بِإِذْنِهِ ۚ يَعْلَمُ مَا بَيْنَ أَيْدِيهِمْ وَمَا خَلْفَهُمْ ۖ وَلَا يُحِيطُونَ بِشَيْءٍ مِّنْ عِلْمِهِ إِلَّا بِمَا شَاءَ ۚ وَسِعَ كُرْسِيُّهُ السَّمَاوَاتِ وَالْأَرْضَ ۖ وَلَا يَئُودُهُ حِفْظُهُمَا ۚ وَهُوَ الْعَلِيُّ الْعَظِيمُ",
            "Recite Ayat al-Kursi (Surah Al-Baqarah 2:255)",
            "الدليل: قال النبي ﷺ: «إذا أويت إلى فراشك فاقرأ آية الكرسي؛ فإنه لن يزال عليك من الله حافظ ولا يقربك شيطان حتى تصبح» (رواه البخاري).",
            "Evidence: A guardian from Allah will remain over you and no devil will come near you until morning (Sahih Bukhari).",
            1
        ),
        TasbeehItem(
            "آخر آيتين من سورة البقرة", "Last 2 Verses of Al-Baqarah",
            "آمَنَ الرَّسُولُ بِمَا أُنزِلَ إِلَيْهِ مِن رَّبِّهِ وَالْمُؤْمِنُونَ ۚ كُلٌّ آمَنَ بِاللَّهِ وَمَلَائِكَتِهِ وَكُتُبِهِ وَرُسُلِهِ ... (الآيتان 285-286 من سورة البقرة)",
            "Recite the last two verses of Surah Al-Baqarah (2:285-286)",
            "الدليل: قال النبي ﷺ: «من قرأ بالآيتين من آخر سورة البقرة في ليلة كفتاه» (متفق عليه — أي كفتاه من كل شر وآفة وشيطان).",
            "Evidence: Whoever recites them at night, they will suffice him against all harm (Bukhari & Muslim).",
            1
        ),
        TasbeehItem(
            "النفث بالمعوذات في الكفين", "Al-Ikhlas, Al-Falaq & An-Nas",
            "يجمع كفيه ثم ينفث فيهما ويقرأ: ﴿قُلْ هُوَ اللَّهُ أَحَدٌ﴾ و﴿قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ﴾ و﴿قُلْ أَعُوذُ بِرَبِّ النَّاسِ﴾ ثم يمسح بهما ما استطاع من جسده",
            "Cup hands, blow gently, recite Al-Ikhlas, Al-Falaq & An-Nas, and wipe over the body (3x)",
            "الدليل: كان النبي ﷺ يفعل ذلك كل ليلة عند النوم ثلاث مرات لحفظ الجسد من الشرور والعين والشيطان (رواه البخاري).",
            "Evidence: Sunnah of the Prophet ﷺ every night for complete protection (Sahih Bukhari).",
            3
        ),
        TasbeehItem(
            "باسمك ربي وضعت جنبي", "Bismika Rabbi Wada'tu Janbi",
            "بِاسْمِكَ رَبِّي وَضَعْتُ جَنْبِي، وَبِكَ أَرْفَعُهُ، فَإِنْ أَمْسَكْتَ نَفْسِي فَارْحَمْهَا، وَإِنْ أَرْسَلْتَهَا فَاحْفَظْهَا بِمَا تَحْفَظُ بِهِ عِبَادَكَ الصَّالِحِينَ",
            "In Your name my Lord I lie down, and in Your name I rise...",
            "الدليل: أوصى به النبي ﷺ عند الاضطجاع للنوم (متفق عليه).",
            "Evidence: Recommended by the Prophet ﷺ when lying down (Bukhari & Muslim).",
            1
        ),
        TasbeehItem(
            "تسبيح النوم (سبحان الله 33)", "SubhanAllah (33x)",
            "سُبْحَانَ اللَّهِ",
            "SubhanAllah (33 times)",
            "الدليل: علّمه النبي ﷺ لفاطمة وعلي رضي الله عنهما عند النوم وقال: «ذلك خير لكما من خادم» (متفق عليه).",
            "Evidence: Better for you than a servant — gives strength and barakah (Bukhari & Muslim).",
            33
        ),
        TasbeehItem(
            "تحميد النوم (الحمد لله 33)", "Alhamdulillah (33x)",
            "الْحَمْدُ لِلَّهِ",
            "Alhamdulillah (33 times)",
            "الدليل: من تسبيح النوم النبوي الذي يبعث النشاط والبركة (متفق عليه).",
            "Evidence: Part of the Prophetic bedtime Tasbeeh (Bukhari & Muslim).",
            33
        ),
        TasbeehItem(
            "تكبير النوم (الله أكبر 34)", "Allahu Akbar (34x)",
            "اللَّهُ أَكْبَرُ",
            "Allahu Akbar (34 times)",
            "الدليل: تمام المائة قبل النوم كما علّم النبي ﷺ ابنته فاطمة رضي الله عنها (متفق عليه).",
            "Evidence: Completes 100 before sleep (Bukhari & Muslim).",
            34
        ),
        TasbeehItem(
            "باسمك اللهم أموت وأحيا", "Bismika Allahumma Amutu wa Ahya",
            "بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا",
            "In Your name O Allah, I die and I live.",
            "الدليل: كان النبي ﷺ إذا أخذ مضجعه قالها (رواه البخاري).",
            "Evidence: The Prophet's ﷺ words when going to sleep (Sahih Bukhari).",
            1
        )
    )

    /**
     * Authentic Waking Up Adhkar (أذكار الاستيقاظ من النوم)
     */
    val wakingAdhkar: List<TasbeehItem> = listOf(
        TasbeehItem(
            "الحمد لله الذي أحيانا", "Praise Upon Waking",
            "الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ",
            "All praise is for Allah who gave us life after having taken it from us and unto Him is the resurrection.",
            "الدليل: كان النبي ﷺ يقولها فور استيقاظه من النوم (رواه البخاري).",
            "Evidence: Recited by the Prophet ﷺ immediately upon waking (Sahih Bukhari).",
            1
        ),
        TasbeehItem(
            "الحمد لله الذي عافاني في جسدي", "Gratitude for Health",
            "الْحَمْدُ لِلَّهِ الَّذِي عَافَانِي فِي جَسَدِي، وَرَدَّ عَلَيَّ رُوحِي، وَأَذِنَ لِي بِذِكْرِهِ",
            "All praise is for Allah who restored my health, returned my soul, and permitted me to remember Him.",
            "الدليل: رواه الترمذي وحسنه الألباني.",
            "Evidence: Narrated by At-Tirmidhi.",
            1
        ),
        TasbeehItem(
            "دعاء الاستيقاظ ليلاً (إجابة الدعاء)", "Waking at Night Dua",
            "لَا إِلَهَ إِلَّا اللَّهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلَى كُلِّ شَيْءٍ قَدِيرٌ، الْحَمْدُ لِلَّهِ، وَسُبْحَانَ اللَّهِ، وَلَا إِلَهَ إِلَّا اللَّهُ، وَاللَّهُ أَكْبَرُ، وَلَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ، اللَّهُمَّ اغْفِرْ لِي",
            "None is worthy of worship except Allah alone... O Allah forgive me.",
            "الدليل: من تعارّ من الليل فقالها ثم دعا استُجيب له، فإن توضأ وصلى قُبلت صلاته (رواه البخاري).",
            "Evidence: Whoever says this upon waking at night and supplicates will be answered (Sahih Bukhari).",
            1
        )
    )
}
