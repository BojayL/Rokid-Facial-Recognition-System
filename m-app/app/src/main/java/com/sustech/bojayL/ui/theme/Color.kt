package com.sustech.bojayL.ui.theme

import androidx.compose.ui.graphics.Color

// AR 智慧课堂伴侣 - 深色主题配色方案
// 参考 PRD: 深色背景 #121212, 高亮色 #00E5FF 青色, #FFFFFF 白色

// 主背景色
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2D2D2D)

// 主色调 - 青色系
val CyanPrimary = Color(0xFF00E5FF)  // 主青色
val CyanLight = Color(0xFF6EFFFF)   // 浅青色
val CyanDark = Color(0xFF00B2CC)    // 深青色

// 辅助色
val AccentGreen = Color(0xFF00E676)  // 成功/正常状态
val AccentRed = Color(0xFFFF5252)    // 警告/异常状态
val AccentOrange = Color(0xFFFFAB40) // 提示/待处理
val AccentYellow = Color(0xFFFFD740) // 注意

// 文字颜色
val TextPrimary = Color(0xFFFFFFFF)      // 主文字 - 白色
val TextSecondary = Color(0xB3FFFFFF)    // 次要文字 - 70% 白
val TextTertiary = Color(0x80FFFFFF)     // 三级文字 - 50% 白
val TextDisabled = Color(0x4DFFFFFF)     // 禁用文字 - 30% 白

// 卡片和边框
val CardBackground = Color(0xFF1E1E1E)
val CardBorder = Color(0xFF3D3D3D)
val Divider = Color(0xFF2D2D2D)

// 设备连接状态颜色
val StatusConnected = Color(0xFF00E676)    // 已连接 - 绿色
val StatusDisconnected = Color(0xFFFF5252) // 未连接 - 红色
val StatusConnecting = Color(0xFFFFAB40)   // 连接中 - 橙色

// 考勤状态颜色
val AttendancePresent = Color(0xFF00E676)  // 出勤
val AttendanceAbsent = Color(0xFFFF5252)   // 缺勤
val AttendanceLate = Color(0xFFFFAB40)     // 迟到
