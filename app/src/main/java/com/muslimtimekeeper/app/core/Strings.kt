package com.muslimtimekeeper.app.core

object Strings {
    val supportedLanguages = listOf(
        Triple("ar", "العربية", "🇪🇬"),
        Triple("en", "English", "🇬🇧"),
        Triple("fr", "Français", "🇫🇷"),
        Triple("tr", "Türkçe", "🇹🇷"),
        Triple("id", "Bahasa Indonesia", "🇮🇩"),
        Triple("ur", "اردو", "🇵🇰"),
        Triple("es", "Español", "🇪🇸"),
        Triple("de", "Deutsch", "🇩🇪")
    )

    fun isRtl(lang: String): Boolean = (lang == "ar" || lang == "ur")

    fun get(ar: Boolean, key: String): String {
        return get(if (ar) "ar" else "en", key)
    }

    fun get(lang: String, key: String): String {
        val m = when (lang) {
            "ar" -> arMap
            "fr" -> frMap
            "tr" -> trMap
            "id" -> idMap
            "ur" -> urMap
            "es" -> esMap
            "de" -> deMap
            else -> enMap
        }
        return m[key] ?: enMap[key] ?: key
    }

    private val enMap = mapOf(
        "app_name" to "Muslim Time Keeper",
        "app_sub" to "حارس وقت المسلم",
        "locked" to "🔒 Locked",
        "unlocked" to "🔓 Unlocked",
        "tab_home" to "🏠\nHome",
        "tab_quran" to "📖\nQuran",
        "tab_schedule" to "📅\nTasks",
        "tab_manage" to "🛡️\nManage",
        "tab_apps" to "🎮\nApps",
        "tab_prayer" to "🕌\nPrayer",
        "tab_tasbeeh" to "📿\nAdhkar",
        "tab_data" to "📶\nData",
        "tab_safety" to "🛡️\nSafety",
        "time_left" to "TIME LEFT TODAY",
        "daily_limit" to "Daily Phone Limit",
        "used_today" to "Used Today",
        "monitored_apps" to "🎮 Monitored Apps",
        "web_safety" to "🌐 Web Safety",
        "active" to "Active",
        "protected" to "PROTECTED",
        "off" to "OFF",
        "reset_today" to "🔄  Reset Today's Counter & Unlock",
        "add_app_btn" to "➕  Choose Game / App from Phone",
        "pick_app_title" to "Select Game or App",
        "search_hint" to "Search installed apps...",
        "limit_min" to "Daily Limit (minutes)",
        "save" to "Save",
        "cancel" to "Cancel",
        "remove" to "🗑️ Remove",
        "prayer_title" to "🕌 Prayer Times & Adhan",
        "prayer_lock_sw" to "Auto-Lock Screen at Iqama Time",
        "adhan_sw" to "Play Adhan Automatically",
        "prayer_dur_label" to "Prayer Break Duration:",
        "minutes" to "min",
        "test_adhan" to "🔊 Test Adhan",
        "stop" to "⏹️ Stop",
        "test_lock" to "🕌 Test Prayer Lock Screen",
        "web_title" to "🌐 Web Safety — Block Dangerous Sites",
        "web_desc" to "Routes DNS through Cloudflare Family (1.1.1.3) & CleanBrowsing Family without root.",
        "enable_dns" to "🛡️ Enable Web Safety Filter",
        "disable_dns" to "⏹️ Disable Web Safety Filter",
        "perm_title" to "🛡️ Permissions & Anti-Uninstall",
        "perm_usage" to "1️⃣ Grant Usage Access",
        "perm_overlay" to "2️⃣ Grant Draw Over Apps",
        "perm_admin" to "3️⃣ Activate Device Admin",
        "pin_title" to "🔐 Parent PIN & Uninstall",
        "new_pin_hint" to "New Parent PIN (min 4 chars)",
        "update_pin" to "🔐 Update Parent PIN",
        "allow_uninstall" to "🔓 Allow Uninstall",
        "auth_title" to "🔐 Parent Authentication",
        "auth_msg" to "Enter Parent PIN (default 1234):",
        "wrong_pin" to "Incorrect Parent PIN!",
        "prayer_badge" to "🕌 IT IS TIME FOR PRAYER",
        "prayer_verse" to "\"Indeed, prayer has been decreed upon the believers a decree of specified times.\" (4:103)",
        "mute_adhan" to "🔇 Mute Adhan",
        "parent_unlock" to "🔓 Parent Unlock",
        "phone_locked_title" to "🔒 Phone Locked",
        "app_blocked_title" to "⛔ App Time Expired",
        "go_home" to "🏠 Go Back Home",
        "open_calls" to "📞 Emergency Calls Only"
    )

    private val arMap = mapOf(
        "app_name" to "حارس وقت المسلم",
        "app_sub" to "Muslim Time Keeper",
        "locked" to "🔒 مقفل",
        "unlocked" to "🔓 مفتوح",
        "tab_home" to "🏠\nالرئيسية",
        "tab_quran" to "📖\nالمصحف",
        "tab_schedule" to "📅\nالجدول",
        "tab_manage" to "🛡️\nالإدارة",
        "tab_apps" to "🎮\nالتطبيقات",
        "tab_prayer" to "🕌\nالصلاة",
        "tab_tasbeeh" to "📿\nالأذكار",
        "tab_data" to "📶\nالإنترنت",
        "tab_safety" to "🛡️\nالحماية",
        "time_left" to "الوقت المتبقي اليوم",
        "daily_limit" to "الحد اليومي للهاتف",
        "used_today" to "المستخدم اليوم",
        "monitored_apps" to "🎮 التطبيقات المراقبة",
        "web_safety" to "🌐 حماية الإنترنت",
        "active" to "نشط",
        "protected" to "محمي",
        "off" to "متوقف",
        "reset_today" to "🔄  تصفير عداد اليوم وفك القفل",
        "add_app_btn" to "➕  إضافة لعبة أو تطبيق من الهاتف",
        "pick_app_title" to "اختر اللعبة أو التطبيق",
        "search_hint" to "ابحث في التطبيقات المثبتة...",
        "limit_min" to "الحد اليومي (بالدقائق)",
        "save" to "حفظ",
        "cancel" to "إلغاء",
        "remove" to "🗑️ حذف",
        "prayer_title" to "🕌 مواقيت الصلاة والأذان",
        "prayer_lock_sw" to "قفل الشاشة تلقائياً عند الإقامة",
        "adhan_sw" to "تشغيل صوت الأذان تلقائياً",
        "prayer_dur_label" to "مدة استراحة الصلاة:",
        "minutes" to "دقيقة",
        "test_adhan" to "🔊 اختبار الأذان",
        "stop" to "⏹️ إيقاف",
        "test_lock" to "🕌 تجربة شاشة قفل الصلاة",
        "web_title" to "🌐 حماية الويب — منع المواقع المخلة والخطرة",
        "web_desc" to "يوجه DNS الهاتف عبر Cloudflare Family وCleanBrowsing لحجب المواقع الضارة بدون روت.",
        "enable_dns" to "🛡️ تفعيل فلتر حجب المواقع المخلة",
        "disable_dns" to "⏹️ إيقاف الفلتر",
        "perm_title" to "🛡️ الصلاحيات ومنع الحذف",
        "perm_usage" to "1️⃣ تفعيل صلاحية تتبع التطبيقات",
        "perm_overlay" to "2️⃣ تفعيل الظهور فوق التطبيقات",
        "perm_admin" to "3️⃣ تفعيل مسؤول الجهاز (منع الحذف)",
        "pin_title" to "🔐 رمز الوالدين وإلغاء التثبيت",
        "new_pin_hint" to "رمز الوالدين الجديد (4 أرقام فأكثر)",
        "update_pin" to "🔐 تحديث رمز الوالدين",
        "allow_uninstall" to "🔓 السماح بحذف التطبيق",
        "auth_title" to "🔐 صلاحية الوالدين",
        "auth_msg" to "أدخل رمز الحماية (الافتراضي 1234):",
        "wrong_pin" to "رمز الحماية غير صحيح!",
        "prayer_badge" to "🕌 حان الآن موعد الصلاة",
        "prayer_verse" to "﴿ إِنَّ الصَّلَاةَ كَانَتْ عَلَى الْمُؤْمِنِينَ كِتَابًا مَّوْقُوتًا ﴾",
        "mute_adhan" to "🔇 كتم الأذان",
        "parent_unlock" to "🔓 فتح برمز الوالدين",
        "phone_locked_title" to "🔒 الهاتف مقفل",
        "app_blocked_title" to "⛔ انتهى وقت التطبيق",
        "go_home" to "🏠 العودة للشاشة الرئيسية",
        "open_calls" to "📞 المكالمات الطارئة فقط"
    )

    private val frMap = mapOf(
        "app_name" to "Gardien du Temps Musulman",
        "locked" to "🔒 Verrouillé", "unlocked" to "🔓 Ouvert",
        "tab_home" to "🏠\nAccueil", "tab_quran" to "📖\nCoran", "tab_schedule" to "📅\nTâches",
        "tab_manage" to "🛡️\nGérer", "tab_prayer" to "🕌\nPrière", "tab_tasbeeh" to "📿\nAdhkar",
        "save" to "Enregistrer", "cancel" to "Annuler", "remove" to "🗑️ Supprimer",
        "prayer_title" to "🕌 Horaires de Prière", "prayer_lock_sw" to "Verrouiller l'écran à l'Iqama",
        "adhan_sw" to "Jouer l'Adhan automatiquement", "test_adhan" to "🔊 Tester l'Adhan",
        "stop" to "⏹️ Arrêter", "minutes" to "min"
    )

    private val trMap = mapOf(
        "app_name" to "Müslüman Zaman Koruyucu",
        "locked" to "🔒 Kilitli", "unlocked" to "🔓 Açık",
        "tab_home" to "🏠\nAna", "tab_quran" to "📖\nKur'an", "tab_schedule" to "📅\nGörev",
        "tab_manage" to "🛡️\nYönet", "tab_prayer" to "🕌\nNamaz", "tab_tasbeeh" to "📿\nZikir",
        "save" to "Kaydet", "cancel" to "İptal", "remove" to "🗑️ Sil",
        "prayer_title" to "🕌 Namaz Vakitleri", "prayer_lock_sw" to "Kamet Vaktinde Ekranı Kilitle",
        "adhan_sw" to "Ezanı Otomatik Çal", "test_adhan" to "🔊 Ezanı Test Et",
        "stop" to "⏹️ Durdur", "minutes" to "dk"
    )

    private val idMap = mapOf(
        "app_name" to "Penjaga Waktu Muslim",
        "locked" to "🔒 Terkunci", "unlocked" to "🔓 Terbuka",
        "tab_home" to "🏠\nBeranda", "tab_quran" to "📖\nQuran", "tab_schedule" to "📅\nJadwal",
        "tab_manage" to "🛡️\nKelola", "tab_prayer" to "🕌\nSholat", "tab_tasbeeh" to "📿\nDzikir",
        "save" to "Simpan", "cancel" to "Batal", "remove" to "🗑️ Hapus",
        "prayer_title" to "🕌 Waktu Sholat & Adzan", "prayer_lock_sw" to "Kunci Layar saat Iqamah",
        "adhan_sw" to "Putar Adzan Otomatis", "test_adhan" to "🔊 Tes Adzan",
        "stop" to "⏹️ Stop", "minutes" to "mnt"
    )

    private val urMap = mapOf(
        "app_name" to "مسلم ٹائم کیپر",
        "locked" to "🔒 مقفل", "unlocked" to "🔓 کھلا",
        "tab_home" to "🏠\nہوم", "tab_quran" to "📖\nقرآن", "tab_schedule" to "📅\nشیڈول",
        "tab_manage" to "🛡️\nانتظام", "tab_prayer" to "🕌\nنماز", "tab_tasbeeh" to "📿\nاذکار",
        "save" to "محفوظ", "cancel" to "منسوخ", "remove" to "🗑️ حذف",
        "prayer_title" to "🕌 اوقاتِ نماز", "prayer_lock_sw" to "اقامت پر اسکرین لاک کریں",
        "adhan_sw" to "اذان خودکار چلائیں", "test_adhan" to "🔊 اذان چیک کریں",
        "stop" to "⏹️ روکیں", "minutes" to "منٹ"
    )

    private val esMap = mapOf(
        "app_name" to "Guardián del Tiempo Musulmán",
        "locked" to "🔒 Bloqueado", "unlocked" to "🔓 Abierto",
        "tab_home" to "🏠\nInicio", "tab_quran" to "📖\nCorán", "tab_schedule" to "📅\nTareas",
        "tab_manage" to "🛡️\nGestión", "tab_prayer" to "🕌\nOración", "tab_tasbeeh" to "📿\nAdhkar",
        "save" to "Guardar", "cancel" to "Cancelar", "remove" to "🗑️ Eliminar",
        "prayer_title" to "🕌 Horarios de Oración", "prayer_lock_sw" to "Bloquear pantalla en Iqama",
        "adhan_sw" to "Reproducir Adhan automáticamente", "test_adhan" to "🔊 Probar Adhan",
        "stop" to "⏹️ Detener", "minutes" to "min"
    )

    private val deMap = mapOf(
        "app_name" to "Muslim Zeitwächter",
        "locked" to "🔒 Gesperrt", "unlocked" to "🔓 Offen",
        "tab_home" to "🏠\nStart", "tab_quran" to "📖\nKoran", "tab_schedule" to "📅\nPlan",
        "tab_manage" to "🛡️\nVerw.", "tab_prayer" to "🕌\nGebet", "tab_tasbeeh" to "📿\nDhikr",
        "save" to "Speichern", "cancel" to "Abbrechen", "remove" to "🗑️ Löschen",
        "prayer_title" to "🕌 Gebetszeiten", "prayer_lock_sw" to "Bildschirm bei Iqama sperren",
        "adhan_sw" to "Adhan automatisch abspielen", "test_adhan" to "🔊 Adhan testen",
        "stop" to "⏹️ Stopp", "minutes" to "Min"
    )
}
