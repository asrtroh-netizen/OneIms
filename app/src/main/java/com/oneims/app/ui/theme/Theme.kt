package com.oneims.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 全局 UI token：卡片圆角、内边距、图标尺寸、按压态颜色等统一来源。
 */
object OneImsTokens {
    val cardCornerRadius = 20.dp
    val cardPaddingHorizontal = 20.dp
    val itemSpacing = 12.dp
    val iconSize = 20.dp
    val rowMinHeight = 72.dp
    val simPillHeight = 30.dp
    val simPillDotSize = 6.dp

    @Composable
    fun pressedOverlay(): Color =
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    @Composable
    fun dividerColor(): Color =
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
}

/**
 * OneIms 的 Pixel 风格 Material 3 主题。
 *
 * Android 12+ 优先跟随系统壁纸动态取色；关闭动态色时使用克制的 Google Blue
 * 回退色板。界面层只消费语义色，不再自行拼装透明度或渐变，保证亮暗色与
 * 高对比主题下都能维持清晰的信息层级。
 */

/**
 * 全局主色：原 Google Blue 全部改为白（用户要求「全局蓝换白」）。
 * 未激活 hero 粉卡仍走 errorContainer，不属蓝色强调。
 */
private val LightColors = lightColorScheme(
    primary = Color.White,
    onPrimary = Color(0xFF1A1B20),
    primaryContainer = Color(0xFFF0F0F4),
    onPrimaryContainer = Color(0xFF1A1B20),
    secondary = Color(0xFF575E71),
    secondaryContainer = Color(0xFFE8E8EF),
    tertiary = Color(0xFF5F5E62),
    tertiaryContainer = Color(0xFFE8E8EF),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF1A1B20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color(0xFF1A1B20),
    primaryContainer = Color(0xFF2B2930),
    onPrimaryContainer = Color(0xFFE2E2E9),
    secondary = Color(0xFFC8C5D0),
    secondaryContainer = Color(0xFF3F4759),
    tertiary = Color(0xFFC8C5D0),
    tertiaryContainer = Color(0xFF48464C),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

private val OneImsShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

private val OneImsTypography = Typography().let { defaults ->
    Typography(
        displaySmall = defaults.displaySmall.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
        ),
        headlineLarge = defaults.headlineLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
        ),
        headlineMedium = defaults.headlineMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
        ),
        titleLarge = defaults.titleLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = defaults.titleMedium.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
        ),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
        labelLarge = defaults.labelLarge.copy(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
fun OneImsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OneImsTypography,
        shapes = OneImsShapes,
        content = content,
    )
}
