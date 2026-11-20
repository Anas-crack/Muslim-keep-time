# 📱 Muslim Time Keeper (حارس وقت المسلم) — Android APK Project

مشروع أندرويد كامل بلغة **Kotlin + Material 3 Dark UI** مع **الشعار المرفق** و**صوت أذان مصر (`res/raw/adhan.mp3`)** وجميع ميزات الحماية:
- ⏱️ تحديد وقت استخدام الهاتف اليومي وحد لكل لعبة/تطبيق (`UsageStatsManager` + `ForegroundService`).
- 🕌 حساب مواقيت الصلاة فلكياً بدون إنترنت + شاشة قفل الصلاة الكاملة + تشغيل صوت الأذان تلقائياً.
- 🛡️ منع حذف التطبيق إلا برمز الوالدين (`DeviceAdminReceiver`) + إعادة التشغيل التلقائي بعد إقلاع الهاتف (`BOOT_COMPLETED`).
- 🌐 حجب المواقع المخلة والخطرة عبر **Family-Safe DNS VPN** (`1.1.1.3` / `185.228.168.168`) بدون روت.

---

## 🚀 طريقتان لاستخراج ملف `MuslimTimeKeeper.apk`

### الطريقة 1 (بدون تثبيت أي برامج — مجاناً عبر GitHub Actions):
1. أنشئ مستودعاً (Repository) جديداً على [GitHub](https://github.com/new).
2. ارفع محتويات هذا المجلد (بما فيه مجلد `.github/workflows/build-apk.yml`).
3. افتح تبويب **Actions** في GitHub؛ ستجد ملف **`MuslimTimeKeeper-APK`** جاهزاً للتحميل خلال دقيقتين!

### الطريقة 2 (عبر Android Studio على جهازك):
1. فك الضغط وافتح المجلد داخل **Android Studio**.
2. انتظر انتهاء مزامنة Gradle، ثم اختر من القائمة العلوية: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. ستجد الملف جاهزاً في: `app/build/outputs/apk/debug/app-debug.apk`.

*(رمز الوالدين الافتراضي: `1234`)*
