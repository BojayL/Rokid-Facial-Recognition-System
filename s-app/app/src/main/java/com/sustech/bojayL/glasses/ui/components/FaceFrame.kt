package com.sustech.bojayL.glasses.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sustech.bojayL.glasses.communication.FaceState
import com.sustech.bojayL.glasses.ui.theme.*

/**
 * L 型人脸框组件
 * 
 * 根据 UI 设计方案，使用 L 型四角框而非全封闭框，减少视觉压迫感
 * 
 * 颜色状态：
 * - 白色虚线：待机/寻找中
 * - 蓝色实线：锁定/识别中
 * - 绿色实线：识别成功
 * - 黄色实线：未知人员
 */
@Composable
fun FaceFrame(
    state: FaceState,
    size: Dp = 200.dp,
    modifier: Modifier = Modifier
) {
    val color = when (state) {
        FaceState.NONE -> GlassWhite.copy(alpha = 0.5f)
        FaceState.DETECTING -> GlassWhite
        FaceState.RECOGNIZING -> GlassBlue
        FaceState.RECOGNIZED -> GlassGreen
        FaceState.UNKNOWN -> GlassYellow
    }
    
    val isDashed = state == FaceState.DETECTING || state == FaceState.NONE
    val strokeWidth = if (state == FaceState.NONE) 2f else 4f
    
    Canvas(modifier = modifier.size(size)) {
        val frameSize = this.size.minDimension
        val cornerLength = frameSize * 0.25f  // L 型边长
        
        val stroke = Stroke(
            width = strokeWidth,
            pathEffect = if (isDashed) {
                PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
            } else null
        )
        
        // 左上角 L
        drawLine(
            color = color,
            start = Offset(0f, cornerLength),
            end = Offset(0f, 0f),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(cornerLength, 0f),
            strokeWidth = strokeWidth
        )
        
        // 右上角 L
        drawLine(
            color = color,
            start = Offset(frameSize - cornerLength, 0f),
            end = Offset(frameSize, 0f),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = color,
            start = Offset(frameSize, 0f),
            end = Offset(frameSize, cornerLength),
            strokeWidth = strokeWidth
        )
        
        // 右下角 L
        drawLine(
            color = color,
            start = Offset(frameSize, frameSize - cornerLength),
            end = Offset(frameSize, frameSize),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = color,
            start = Offset(frameSize, frameSize),
            end = Offset(frameSize - cornerLength, frameSize),
            strokeWidth = strokeWidth
        )
        
        // 左下角 L
        drawLine(
            color = color,
            start = Offset(cornerLength, frameSize),
            end = Offset(0f, frameSize),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = color,
            start = Offset(0f, frameSize),
            end = Offset(0f, frameSize - cornerLength),
            strokeWidth = strokeWidth
        )
    }
}

/**
 * 带动画的人脸框（可选扩展）
 */
@Composable
fun AnimatedFaceFrame(
    state: FaceState,
    size: Dp = 200.dp,
    modifier: Modifier = Modifier
) {
    // TODO: 添加呼吸动画效果
    FaceFrame(state = state, size = size, modifier = modifier)
}
