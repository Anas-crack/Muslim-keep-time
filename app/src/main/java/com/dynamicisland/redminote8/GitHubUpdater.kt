package com.dynamicisland.redminote8

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.app.usage.UsageStatsManager
import android.graphics.Canvas
import android.hardware.camera2.CameraManager
import android.media.AudioRecordingConfiguration
import android.service.notification.NotificationListenerService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object GitHubUpdater {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun checkForUpdate(activity: Activity, repoInput: String, silentIfUpToDate: Boolean) {
        val repo = sanitizeRepo(repoInput)
        if (repo.isEmpty() || !repo.contains("/")) {
            if (!silentIfUpToDate) {
                Toast.makeText(
                    activity,
                    "اكتب اسم المستودع بصيغة: username/repo-name",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        if (!silentIfUpToDate) {
            Toast.makeText(activity, "جاري فحص التحديثات في $repo ...", Toast.LENGTH_SHORT).show()
        }

        thread {
            try {
                val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "DynamicIsland/${BuildConfig.VERSION_NAME}")
                }

                val code = conn.responseCode
                if (code != 200) {
                    mainHandler.post {
                        if (!silentIfUpToDate) {
                            val msg = if (code == 404) {
                                "لا يوجد إصدار (Release) منشور بعد في $repo"
                            } else {
                                "تعذر الاتصال بـ GitHub (رمز $code)"
                            }
                            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                    return@thread
                }

                val bodyText = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(bodyText)
                val latestTag = json.optString("tag_name", "").trim()
                val releaseNotes = json.optString("body", "تحديث جديد متاح لتطبيق Dynamic Island.").trim()
                val assets = json.optJSONArray("assets")

                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                val currentVer = BuildConfig.VERSION_NAME
                if (isNewerVersion(latestTag, currentVer)) {
                    mainHandler.post {
                        if (activity.isFinishing) return@post
                        AlertDialog.Builder(activity)
                            .setTitle("تحديث جديد متاح ($latestTag)")
                            .setMessage("الإصدار الحالي: v$currentVer\nالإصدار الجديد: $latestTag\n\n$releaseNotes")
                            .setPositiveButton("تحميل وتثبيت الآن") { _, _ ->
                                if (!apkUrl.isNullOrBlank()) {
                                    downloadAndInstallApk(activity, apkUrl, latestTag)
                                } else {
                                    val htmlUrl = json.optString("html_url", "https://github.com/$repo/releases")
                                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(htmlUrl)))
                                }
                            }
                            .setNegativeButton("لاحقًا", null)
                            .show()
                    }
                } else {
                    mainHandler.post {
                        if (!silentIfUpToDate) {
                            Toast.makeText(
                                activity,
                                "أنت تستخدم أحدث إصدار بالفعل (v$currentVer)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (!silentIfUpToDate) {
                        Toast.makeText(
                            activity,
                            "فشل التحقق من التحديث: ${e.localizedMessage ?: "تأكد من الإنترنت"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun downloadAndInstallApk(activity: Activity, apkUrl: String, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(
                activity,
                "فعّل إذن تثبيت التطبيقات من هذا المصدر ثم حاول مرة أخرى",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
            return
        }

        Toast.makeText(activity, "جاري تحميل التحديث $tag ...", Toast.LENGTH_LONG).show()

        thread {
            try {
                val dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.cacheDir
                val outFile = File(dir, "DynamicIsland-$tag.apk")
                if (outFile.exists()) outFile.delete()

                var currentUrl = apkUrl
                var hops = 0
                var conn: HttpURLConnection? = null
                while (hops < 8) {
                    val c = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 20000
                        readTimeout = 60000
                        setRequestProperty("User-Agent", "DynamicIsland/${BuildConfig.VERSION_NAME}")
                        setRequestProperty("Accept", "application/octet-stream, */*")
                    }
                    val code = c.responseCode
                    if (code in listOf(301, 302, 303, 307, 308)) {
                        val loc = c.getHeaderField("Location")
                        c.disconnect()
                        if (loc.isNullOrBlank()) throw IllegalStateException("Empty redirect")
                        currentUrl = URL(URL(currentUrl), loc).toString()
                        hops++
                    } else if (code == 200) {
                        conn = c
                        break
                    } else {
                        c.disconnect()
                        throw IllegalStateException("HTTP $code")
                    }
                }
                val activeConn = conn ?: throw IllegalStateException("Too many redirects")
                activeConn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output, bufferSize = 16384)
                        output.flush()
                        try { output.fd.sync() } catch (_: Exception) {}
                    }
                }
                activeConn.disconnect()

                // Verify the downloaded file is a real APK (starts with ZIP magic bytes "PK" and > 100 KB)
                val header = ByteArray(2)
                val readBytes = outFile.inputStream().use { it.read(header) }
                val isValidApk = outFile.length() > 100_000L && readBytes == 2 &&
                    header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
                if (!isValidApk) {
                    outFile.delete()
                    mainHandler.post {
                        Toast.makeText(activity, "جاري فتح رابط التحميل في المتصفح...", Toast.LENGTH_LONG).show()
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
                    }
                    return@thread
                }

                mainHandler.post {
                    val apkUri = FileProvider.getUriForFile(
                        activity,
                        "${activity.packageName}.provider",
                        outFile
                    )
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    activity.startActivity(installIntent)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(
                        activity,
                        "فشل تحميل التحديث: ${e.localizedMessage ?: "خطأ غير متوقع"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun sanitizeRepo(raw: String): String {
        return raw.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')
    }

    fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
        val rParts = remoteTag.trim().removePrefix("v").removePrefix("V")
            .split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val cParts = currentVersion.trim().removePrefix("v").removePrefix("V")
            .split(".").map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
