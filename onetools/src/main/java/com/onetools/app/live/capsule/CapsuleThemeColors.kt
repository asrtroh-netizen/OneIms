package com.onetools.app.live.capsule

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * 动态色：壁纸取色压暗后作胶囊底，失败则回退固定深色。
 * 干净室借鉴 MT/OneCapsule dynamicColor 开关心智。
 */
object CapsuleThemeColors {
    private const val FALLBACK_PILL = 0xF2000000.toInt()
    private const val FALLBACK_CARD = 0xF214161C.toInt()
    private const val FALLBACK_STROKE = 0x22FFFFFF

    fun pillFill(context: Context, dynamicEnabled: Boolean): Int {
        if (!dynamicEnabled) return FALLBACK_PILL
        val primary = wallpaperPrimary(context) ?: return FALLBACK_PILL
        val darkened = ColorUtils.blendARGB(primary, Color.BLACK, 0.72f)
        return ColorUtils.setAlphaComponent(darkened, 0xF2)
    }

    fun cardFill(context: Context, dynamicEnabled: Boolean): Int {
        if (!dynamicEnabled) return FALLBACK_CARD
        val primary = wallpaperPrimary(context) ?: return FALLBACK_CARD
        val darkened = ColorUtils.blendARGB(primary, Color.BLACK, 0.78f)
        return ColorUtils.setAlphaComponent(darkened, 0xF2)
    }

    fun stroke(dynamicEnabled: Boolean, accent: Int): Int {
        if (!dynamicEnabled) return FALLBACK_STROKE
        return ColorUtils.setAlphaComponent(accent, 0x55)
    }

    private fun wallpaperPrimary(context: Context): Int? {
        return runCatching {
            val colors = WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            colors?.primaryColor?.toArgb()
        }.getOrNull()
    }
}
