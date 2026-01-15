package com.sustech.bojayL.glasses.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// AR 眼镜暗色主题 - 高对比度
private val GlassesDarkColorScheme = darkColorScheme(
    primary = GlassGreen,
    secondary = GlassBlue,
    tertiary = GlassYellow,
    background = Color.Transparent,
    surface = TransparentBlack,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = GlassWhite,
    onSurface = GlassWhite
)

/**
 * AR 眼镜端主题
 * 
 * 专为 Rokid AR 眼镜优化：
 * - 暗色背景，减少光线干扰
 * - 高对比度颜色
 * - 大字号，易于阅读
 */
@Composable
fun GlassesAppTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GlassesDarkColorScheme,
        typography = GlassesTypography,
        content = content
    )
}
