package com.muslimtimekeeper.app.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
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

data class UpdateCheckResult(
    val release: ReleaseInfo?,
    val error: String? = null
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

    private fun extractVersionNums(tag: String): List<Int> {
        val m = Regex("\\d+(?:\\.\\d+)*").find(tag) ?: return listOf(0)
        return m.value.split(".").map { it.toIntOrNull() ?: 0 }
    }

    private fun isNewerVersion(remoteTag: String, current: String): Boolean {
        val rParts = extractVersionNums(remoteTag)
        val cParts = extractVersionNums(current)
        val len = maxOf(rParts.size, cParts.size)
        for (i in 0 until len) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseReleaseJson(json: JSONObject, currentVersion: String): ReleaseInfo? {
        val tag = json.optString("tag_name", "").ifEmpty { json.optString("name", "") }
        if (tag.isEmpty() || !isNewerVersion(tag, currentVersion)) return null
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
        return ReleaseInfo(tag, apkUrl, json.optString("body", ""))
    }

    private fun fetchJsonText(urlStr: String): Pair<Int, String?> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MuslimTimeKeeper-Android/2.0.4")
                connectTimeout = 15000
                readTimeout = 20000
            }
            val code = conn.responseCode
            val txt = if (code in 200..299) conn.inputStream.bufferedReader().readText() else null
            Pair(code, txt)
        } catch (e: Exception) {
            Pair(-1, e.message)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) { }
        }
    }

    fun checkLatestReleaseDetailed(context: Context, ownerRepo: String): UpdateCheckResult {
        val cleanRepo = ownerRepo.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .trim('/')
        if (!cleanRepo.contains("/")) return UpdateCheckResult(null, "Invalid repository name")
        val current = getCurrentVersionName(context)

        // 1. Try /releases/latest
        val (code1, body1) = fetchJsonText("https://api.github.com/repos/$cleanRepo/releases/latest")
        if (code1 == 200 && body1 != null) {
            try {
                val rel = parseReleaseJson(JSONObject(body1), current)
                if (rel != null) return UpdateCheckResult(rel, null)
            } catch (_: Exception) { }
        }

        // 2. Fallback to /releases list (catches pre-releases or multiple releases!)
        val (code2, body2) = fetchJsonText("https://api.github.com/repos/$cleanRepo/releases?per_page=10")
        if (code2 == 200 && body2 != null) {
            try {
                val arr = JSONArray(body2)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.optBoolean("draft", false)) continue
                    val rel = parseReleaseJson(obj, current)
                    if (rel != null) return UpdateCheckResult(rel, null)
                }
                return UpdateCheckResult(null, null) // Truly up-to-date
            } catch (e: Exception) {
                return UpdateCheckResult(null, "Parse error: ${e.message}")
            }
        }
        return UpdateCheckResult(null, if (code1 == -1) "No internet connection" else "GitHub HTTP $code1 / $code2")
    }

    fun checkLatestRelease(context: Context, ownerRepo: String): ReleaseInfo? {
        return checkLatestReleaseDetailed(context, ownerRepo).release
    }

    private fun openWithRedirects(startUrl: String): HttpURLConnection {
        var currentUrl = startUrl
        for (hop in 0..6) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "MuslimTimeKeeper-Android/2.0.4")
                setRequestProperty("Accept", "application/octet-stream, */*")
                connectTimeout = 20000
                readTimeout = 120000
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) throw Exception("Empty redirect")
                currentUrl = URL(URL(currentUrl), loc).toString()
                continue
            }
            return conn
        }
        throw Exception("Too many redirects")
    }

    fun needsInstallPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
        }
    }

    fun downloadAndInstallApk(context: Context, apkUrl: String): Pair<Boolean, String?> {
        return try {
            val outFile = File(context.cacheDir, "mtk_update_latest.apk")
            if (outFile.exists()) outFile.delete()
            val conn = openWithRedirects(apkUrl)
            if (conn.responseCode !in 200..299) {
                val c = conn.responseCode
                conn.disconnect()
                return Pair(false, "HTTP $c")
            }
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
            conn.disconnect()
            if (!outFile.exists() || outFile.length() < 100_000L) {
                return Pair(false, "Downloaded file too small")
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            Pair(true, null)
        } catch (e: Exception) {
            Pair(false, e.message ?: "Install error")
        }
    }
}
