package com.sustech.bojayL.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * AR 智慧课堂伴侣 - 深色主题
 * 
 * 设计原则（参考 PRD）：
 * 1. 暗黑模式：保持与 AR 眼镜的科技感一致，适应课堂投影环境（暗光）
 * 2. 大卡片与易读性：字号比普通 APP 大 20%
 * 3. 关键信息高亮：姓名、分数使用 #00E5FF 青色 / #FFFFFF 白色
 */

// 深色主题配色方案
private val ARClassroomDarkColorScheme = darkColorScheme(
    // 主色调 - 青色系
    primary = CyanPrimary,
    onPrimary = Color.Black,
    primaryContainer = CyanDark,
    onPrimaryContainer = TextPrimary,
    
    // 次要色
    secondary = CyanLight,
    onSecondary = Color.Black,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    
    // 强调色
    tertiary = AccentGreen,
    onTertiary = Color.Black,
    tertiaryContainer = DarkSurfaceVariant,
    onTertiaryContainer = TextPrimary,
    
    // 错误色
    error = AccentRed,
    onError = Color.Black,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    
    // 背景色
    background = DarkBackground,
    onBackground = TextPrimary,
    
    // 表面色
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    
    // 轮廓和分割线
    outline = CardBorder,
    outlineVariant = Divider,
    
    // 其他
    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = CyanDark,
    surfaceTint = CyanPrimary
)

@Composable
fun MobileappTheme(
    content: @Composable () -> Unit
) {
    // 强制使用深色主题，不跟随系统设置
    MaterialTheme(
        colorScheme = ARClassroomDarkColorScheme,
        typography = Typography,
        content = content
    )
}
