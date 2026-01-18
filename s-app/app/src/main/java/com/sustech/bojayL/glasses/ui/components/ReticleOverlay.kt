package com.sustech.bojayL.glasses.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.sustech.bojayL.glasses.communication.FaceState
import com.sustech.bojayL.glasses.ui.theme.*

/**
 * AR HUD 准心组件 - 极简版
 * 
 * 屏幕中央的简洁圆点准心，减少视觉干扰。
 * 
 * 状态颜色：
 * - 待机：白色半透明
 * - 检测中：白色
 * - 识别中：蓝色（脉冲动画）
 * - 识别成功：绿色
 * - 未知：黄色
 */
@Composable
fun ReticleOverlay(
    modifier: Modifier = Modifier,
    state: FaceState = FaceState.NONE,
    animated: Boolean = true
) {
    // 脉冲动画（识别中状态）
    val infiniteTransition = rememberInfiniteTransition(label = "reticle")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    // 根据状态选择颜色
    val color = when (state) {
        FaceState.NONE -> GlassWhite.copy(alpha = 0.4f)
        FaceState.DETECTING -> GlassWhite.copy(alpha = 0.7f)
        FaceState.RECOGNIZING -> GlassBlue
        FaceState.RECOGNIZED -> GlassGreen
        FaceState.UNKNOWN -> GlassYellow
    }
    
    // 基础半径
    val baseRadius = when (state) {
        FaceState.NONE -> 4.dp
        FaceState.DETECTING -> 5.dp
        else -> 6.dp
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        val radius = baseRadius.toPx() * if (state == FaceState.RECOGNIZING && animated) pulseScale else 1f
        
        // 绘制简单的圆点准心
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(centerX, centerY)
        )
    }
}

/**
 * 简化版准心（仅十字线，用于待机状态）
 */
@Composable
fun SimpleReticle(
    modifier: Modifier = Modifier,
    color: Color = GlassWhite.copy(alpha = 0.3f)
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        val lineLength = 16.dp.toPx()
        val gapFromCenter = 6.dp.toPx()
        val strokeWidth = 1.5.dp.toPx()
        
        // 简单的十字线
        // 上
        drawLine(
            color = color,
            start = Offset(centerX, centerY - gapFromCenter),
            end = Offset(centerX, centerY - gapFromCenter - lineLength),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // 下
        drawLine(
            color = color,
            start = Offset(centerX, centerY + gapFromCenter),
            end = Offset(centerX, centerY + gapFromCenter + lineLength),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // 左
        drawLine(
            color = color,
            start = Offset(centerX - gapFromCenter, centerY),
            end = Offset(centerX - gapFromCenter - lineLength, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        // 右
        drawLine(
            color = color,
            start = Offset(centerX + gapFromCenter, centerY),
            end = Offset(centerX + gapFromCenter + lineLength, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
