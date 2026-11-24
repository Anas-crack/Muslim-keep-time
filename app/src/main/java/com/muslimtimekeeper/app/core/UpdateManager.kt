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
    val error: String? = null,
    val latestRemoteTag: String? = null
)

object UpdateManager {
    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    private fun extractVersionNums(tag: String): List<Int> {
        val m = Regex("\d+(?:\.\d+)*").find(tag) ?: return listOf(0)
        return m.value.split(".").map { it.toIntOrNull() ?: 0 }
    }

    private fun compareVersions(a: String, b: String): Int {
        val aParts = extractVersionNums(a)
        val bParts = extractVersionNums(b)
        val len = maxOf(aParts.size, bParts.size)
        for (i in 0 until len) {
            val av = aParts.getOrElse(i) { 0 }
            val bv = bParts.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun extractCandidate(json: JSONObject): ReleaseInfo? {
        if (json.optBoolean("draft", false)) return null
        val tagName = json.optString("tag_name", "").trim()
        val relName = json.optString("name", "").trim()
        val bestTag = when {
            tagName.isNotEmpty() && extractVersionNums(tagName).any { it > 0 } -> tagName
            relName.isNotEmpty() && extractVersionNums(relName).any { it > 0 } -> relName
            else -> tagName.ifEmpty { relName }
        }
        if (bestTag.isEmpty()) return null
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
        return ReleaseInfo(bestTag, apkUrl, json.optString("body", ""))
    }

    private fun fetchJsonText(urlStr: String): Pair<Int, String?> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "MuslimTimeKeeper-Android/3.0")
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
        val candidates = mutableListOf<ReleaseInfo>()

        // Fetch full releases list (catches all releases & pre-releases regardless of latest flag)
        val (codeList, bodyList) = fetchJsonText("https://api.github.com/repos/$cleanRepo/releases?per_page=20")
        if (codeList == 200 && bodyList != null) {
            try {
                val arr = JSONArray(bodyList)
                for (i in 0 until arr.length()) {
                    extractCandidate(arr.getJSONObject(i))?.let { candidates.add(it) }
                }
            } catch (_: Exception) { }
        }

        // Also check /releases/latest as backup
        if (candidates.isEmpty()) {
            val (code1, body1) = fetchJsonText("https://api.github.com/repos/$cleanRepo/releases/latest")
            if (code1 == 200 && body1 != null) {
                try {
                    extractCandidate(JSONObject(body1))?.let { candidates.add(it) }
                } catch (_: Exception) { }
            } else if (codeList != 200) {
                return UpdateCheckResult(null, if (codeList == -1) "No internet connection" else "GitHub HTTP $codeList")
            }
        }

        if (candidates.isEmpty()) {
            return UpdateCheckResult(null, null, null)
        }

        // Pick the highest semantic version among all valid APK releases
        val highest = candidates.maxWithOrNull { a, b -> compareVersions(a.versionTag, b.versionTag) }!!
        return if (compareVersions(highest.versionTag, current) > 0) {
            UpdateCheckResult(highest, null, highest.versionTag)
        } else {
            UpdateCheckResult(null, null, highest.versionTag)
        }
    }

    fun checkLatestRelease(context: Context, ownerRepo: String): ReleaseInfo? {
        return checkLatestReleaseDetailed(context, ownerRepo).release
    }

    private fun openWithRedirects(startUrl: String): HttpURLConnection {
        var currentUrl = startUrl
        for (hop in 0..6) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", "MuslimTimeKeeper-Android/3.0")
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
