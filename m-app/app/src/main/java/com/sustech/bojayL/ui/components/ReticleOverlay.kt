package com.sustech.bojayL.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * HUD 准心组件
 * 
 * 屏幕中央的十字准心，帮助教师确认摄像头正对位置。
 * 设计风格：
 * - 高对比度纯绿色 (#00FF00)
 * - 断开的圆环 + 十字线
 * - 可选呼吸动画
 * - 支持对准状态显示
 */
@Composable
fun ReticleOverlay(
    modifier: Modifier = Modifier,
    color: Color = HudGreen,
    animated: Boolean = true,
    showCenterDot: Boolean = false,
    isTargeted: Boolean = false,    // 是否对准了人脸
    isRecognized: Boolean = false,  // 是否已识别
    isLandscapeMode: Boolean = false  // 横屏模式
) {
    // 呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "reticle")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reticle_alpha"
    )
    
    val displayAlpha = if (animated) alpha else 1f
    
    // 根据状态选择颜色
    val displayColor = when {
        isRecognized -> HudCyan      // 已识别 - 青色
        isTargeted -> HudGreen       // 对准中 - 绿色（高亮）
        else -> color.copy(alpha = 0.5f)  // 未对准 - 半透明
    }
    
    // 准心在屏幕中心，横屏模式不需要特殊处理（中心点旋转后依然在中心）
    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        // 准心参数（对准时稍微放大）
        val scale = if (isTargeted || isRecognized) 1.2f else 1f
        val lineLength = 20.dp.toPx() * scale
        val gapFromCenter = 8.dp.toPx() * scale
        val strokeWidth = (if (isTargeted || isRecognized) 3.dp else 2.dp).toPx()
        val ringRadius = 30.dp.toPx() * scale
        val ringStrokeWidth = (if (isTargeted || isRecognized) 2.dp else 1.5.dp).toPx()
        
        val drawColor = displayColor.copy(alpha = displayAlpha)
        
        // 绘制断开的圆环（4段弧线）
        val arcSweep = 60f
        val arcGap = 30f
        
        for (i in 0..3) {
            drawArc(
                color = drawColor,
                startAngle = i * 90f + arcGap / 2,
                sweepAngle = arcSweep,
                useCenter = false,
                topLeft = Offset(centerX - ringRadius, centerY - ringRadius),
                size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = ringStrokeWidth)
            )
        }
        
        // 绘制十字线（从圆环边缘向外延伸）
        // 上
        drawLine(
            color = drawColor,
            start = Offset(centerX, centerY - gapFromCenter),
            end = Offset(centerX, centerY - gapFromCenter - lineLength),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // 下
        drawLine(
            color = drawColor,
            start = Offset(centerX, centerY + gapFromCenter),
            end = Offset(centerX, centerY + gapFromCenter + lineLength),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // 左
        drawLine(
            color = drawColor,
            start = Offset(centerX - gapFromCenter, centerY),
            end = Offset(centerX - gapFromCenter - lineLength, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // 右
        drawLine(
            color = drawColor,
            start = Offset(centerX + gapFromCenter, centerY),
            end = Offset(centerX + gapFromCenter + lineLength, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        
        // 可选：中心点
        if (showCenterDot) {
            drawCircle(
                color = drawColor,
                radius = 3.dp.toPx(),
                center = Offset(centerX, centerY)
            )
        }
    }
}

/**
 * HUD 绿色（Rokid 光波导显示屏标准色）
 */
val HudGreen = Color(0xFF00FF00)

/**
 * HUD 青色（识别成功状态）
 */
val HudCyan = Color(0xFF00FFFF)

/**
 * HUD 红色（警告/未知状态）
 */
val HudRed = Color(0xFFFF3333)

/**
 * HUD 黄色（处理中状态）
 */
val HudYellow = Color(0xFFFFFF00)

/**
 * HUD 灰色（普通检测状态）
 */
val HudGray = Color(0xFF888888)
