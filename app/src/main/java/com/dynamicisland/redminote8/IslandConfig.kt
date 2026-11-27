package com.dynamicisland.redminote8

import android.content.Context
import android.graphics.Color

data class IslandSettings(
    val enabled: Boolean = false,
    val cutoutType: String = "notch",
    val coverStatusBar: Boolean = false,
    val hideInFullscreen: Boolean = true,
    val glassEffectEnabled: Boolean = true,
    val glassOpacityPercent: Int = 82,
    val dualActivityEnabled: Boolean = true,
    val autoHideEnabled: Boolean = true,
    val autoHideSeconds: Int = 30,
    val keepNotifUntilRead: Boolean = true,
    val notchGapDp: Int = 46,
    val barHeightDp: Int = 42,
    val topOffsetDp: Int = 6,
    val compactWidthDp: Int = 276,
    val cornerRadiusDp: Int = 24,
    val themeId: String = "black",
    val userName: String = "أنس محمد",
    val avatarUri: String? = null,
    val use24Hour: Boolean = false,
    val showStatusIcons: Boolean = true,
    val githubRepo: String = "",
    val autoCheckUpdate: Boolean = true
)

object IslandConfig {
    private const val PREF_NAME = "dynamic_island_prefs_v7"
    private const val LEGACY_PREF = "dynamic_island_prefs_v5"

    fun load(context: Context): IslandSettings {
        val p = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val legacy = context.getSharedPreferences(LEGACY_PREF, Context.MODE_PRIVATE)
        val hasV7 = p.contains("glassEffectEnabled")
        return IslandSettings(
            enabled = p.getBoolean("enabled", legacy.getBoolean("enabled", false)),
            cutoutType = p.getString("cutoutType", legacy.getString("cutoutType", "notch")) ?: "notch",
            coverStatusBar = if (hasV7) p.getBoolean("coverStatusBar", false) else false,
            hideInFullscreen = p.getBoolean("hideInFullscreen", true),
            glassEffectEnabled = p.getBoolean("glassEffectEnabled", true),
            glassOpacityPercent = p.getInt("glassOpacityPercent", 82).coerceIn(40, 100),
            dualActivityEnabled = p.getBoolean("dualActivityEnabled", true),
            autoHideEnabled = p.getBoolean("autoHideEnabled", true),
            autoHideSeconds = p.getInt("autoHideSeconds", 30).coerceIn(5, 300),
            keepNotifUntilRead = p.getBoolean("keepNotifUntilRead", true),
            notchGapDp = p.getInt("notchGapDp", legacy.getInt("notchGapDp", 46)),
            barHeightDp = p.getInt("barHeightDp", legacy.getInt("barHeightDp", 42)),
            topOffsetDp = if (hasV7) p.getInt("topOffsetDp", 6) else 6,
            compactWidthDp = if (hasV7) p.getInt("compactWidthDp", 276) else 276,
            cornerRadiusDp = p.getInt("cornerRadiusDp", 24),
            themeId = p.getString("themeId", legacy.getString("themeId", "black")) ?: "black",
            userName = p.getString("userName", legacy.getString("userName", "أنس محمد")) ?: "أنس محمد",
            avatarUri = p.getString("avatarUri", legacy.getString("avatarUri", null)),
            use24Hour = p.getBoolean("use24Hour", legacy.getBoolean("use24Hour", false)),
            showStatusIcons = p.getBoolean("showStatusIcons", true),
            githubRepo = p.getString("githubRepo", legacy.getString("githubRepo", "")) ?: "",
            autoCheckUpdate = p.getBoolean("autoCheckUpdate", true)
        )
    }

    fun save(context: Context, s: IslandSettings) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", s.enabled)
            .putString("cutoutType", s.cutoutType)
            .putBoolean("coverStatusBar", s.coverStatusBar)
            .putBoolean("hideInFullscreen", s.hideInFullscreen)
            .putBoolean("glassEffectEnabled", s.glassEffectEnabled)
            .putInt("glassOpacityPercent", s.glassOpacityPercent.coerceIn(40, 100))
            .putBoolean("dualActivityEnabled", s.dualActivityEnabled)
            .putBoolean("autoHideEnabled", s.autoHideEnabled)
            .putInt("autoHideSeconds", s.autoHideSeconds.coerceIn(5, 300))
            .putBoolean("keepNotifUntilRead", s.keepNotifUntilRead)
            .putInt("notchGapDp", s.notchGapDp)
            .putInt("barHeightDp", s.barHeightDp)
            .putInt("topOffsetDp", s.topOffsetDp)
            .putInt("compactWidthDp", s.compactWidthDp)
            .putInt("cornerRadiusDp", s.cornerRadiusDp)
            .putString("themeId", s.themeId)
            .putString("userName", s.userName)
            .putString("avatarUri", s.avatarUri)
            .putBoolean("use24Hour", s.use24Hour)
            .putBoolean("showStatusIcons", s.showStatusIcons)
            .putString("githubRepo", s.githubRepo)
            .putBoolean("autoCheckUpdate", s.autoCheckUpdate)
            .apply()
    }

    fun themeSolidColor(themeId: String): Int {
        return when (themeId) {
            "charcoal" -> Color.rgb(20, 25, 36)
            "navy" -> Color.rgb(10, 22, 48)
            "light" -> Color.rgb(245, 248, 252)
            else -> Color.rgb(8, 10, 15)
        }
    }

    fun themeGlassColors(themeId: String, opacityPercent: Int): IntArray {
        val alphaTop = ((opacityPercent.coerceIn(30, 100) / 100f) * 235f).toInt().coerceIn(70, 245)
        val alphaBot = (alphaTop + 18).coerceIn(85, 255)
        return when (themeId) {
            "charcoal" -> intArrayOf(Color.argb(alphaTop, 38, 48, 68), Color.argb(alphaBot, 16, 22, 34))
            "navy" -> intArrayOf(Color.argb(alphaTop, 22, 48, 96), Color.argb(alphaBot, 8, 20, 46))
            "light" -> intArrayOf(Color.argb(alphaTop, 255, 255, 255), Color.argb(alphaBot, 226, 236, 248))
            else -> intArrayOf(Color.argb(alphaTop, 28, 34, 48), Color.argb(alphaBot, 8, 10, 16))
        }
    }

    fun isLightTheme(themeId: String): Boolean = themeId == "light"
}
