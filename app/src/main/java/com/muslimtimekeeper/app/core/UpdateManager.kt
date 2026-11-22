package com.muslimtimekeeper.app.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val versionTag: String,
    val apkDownloadUrl: String,
    val releaseNotes: String
)

object UpdateManager {
    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    private fun isNewerVersion(remoteTag: String, current: String): Boolean {
        val rParts = remoteTag.trimStart('v', 'V').split(".").map { it.toIntOrNull() ?: 0 }
        val cParts = current.trimStart('v', 'V').split(".").map { it.toIntOrNull() ?: 0 }
        val len = maxOf(rParts.size, cParts.size)
        for (i in 0 until len) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun checkLatestRelease(context: Context, ownerRepo: String): ReleaseInfo? {
        return try {
            val cleanRepo = ownerRepo.trim()
                .removePrefix("https://github.com/")
                .removePrefix("http://github.com/")
                .trim('/')
            if (!cleanRepo.contains("/")) return null
            val url = URL("https://api.github.com/repos/$cleanRepo/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MuslimTimeKeeper-Android")
                connectTimeout = 12000
                readTimeout = 15000
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "")
            val current = getCurrentVersionName(context)
            if (tag.isEmpty() || !isNewerVersion(tag, current)) return null

            val assets = json.optJSONArray("assets") ?: return null
            var apkUrl = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url", "")
                    break
                }
            }
            if (apkUrl.isEmpty()) return null
            ReleaseInfo(tag, apkUrl, json.optString("body", ""))
        } catch (_: Exception) {
            null
        }
    }

    fun downloadAndInstallApk(context: Context, apkUrl: String): Boolean {
        return try {
            val outFile = File(context.cacheDir, "mtk_update_latest.apk")
            if (outFile.exists()) outFile.delete()
            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "MuslimTimeKeeper-Android")
                connectTimeout = 20000
                readTimeout = 90000
            }
            if (conn.responseCode !in 200..299) return false
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            if (!outFile.exists() || outFile.length() < 100_000L) return false
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
