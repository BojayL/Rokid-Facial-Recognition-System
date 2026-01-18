package com.sustech.bojayL.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sustech.bojayL.ui.theme.*

/**
 * 眼镜预览卡片
 * 
 * 显示眼镜端相机实时画面，用于调试
 * 功能：
 * - 显示最新一帧图像
 * - 绘制准心位置
 * - 显示帧信息（尺寸、时间戳）
 */
@Composable
fun GlassesPreviewCard(
    latestFrame: Bitmap?,
    frameTimestamp: Long = 0L,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "眼镜预览 (调试)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 预览区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)  // 眼镜相机横屏比例
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (latestFrame != null) {
                    // 显示图像
                    Image(
                        bitmap = latestFrame.asImageBitmap(),
                        contentDescription = "眼镜画面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    
                    // 绘制准心叠加层
                    ReticleOverlayDebug(
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // 等待画面
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            tint = HudGray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "等待眼镜画面...",
                            color = HudGray,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            // 帧信息
            if (latestFrame != null) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "尺寸: ${latestFrame.width}×${latestFrame.height}",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    
                    val timeDiff = System.currentTimeMillis() - frameTimestamp
                    Text(
                        text = if (timeDiff < 1000) "刚刚" else "${timeDiff / 1000}秒前",
                        color = if (timeDiff < 3000) HudGreen else HudYellow,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * 调试用准心叠加层
 * 
 * 在预览画面上绘制准心，帮助确认对准位置
 */
@Composable
private fun ReticleOverlayDebug(
    modifier: Modifier = Modifier
) {
    // 呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "debug_reticle")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "debug_reticle_alpha"
    )
    
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        // 准心参数
        val lineLength = 30.dp.toPx()
        val gapFromCenter = 12.dp.toPx()
        val ringRadius = 50.dp.toPx()
        val strokeWidth = 2.dp.toPx()
        val ringStrokeWidth = 1.5.dp.toPx()
        
        val color = HudGreen.copy(alpha = alpha)
        
        // 绘制断开的圆环（4段弧线）
        val arcSweep = 50f
        val arcGap = 40f
        
        for (i in 0..3) {
            drawArc(
                color = color,
                startAngle = i * 90f + arcGap / 2,
                sweepAngle = arcSweep,
                useCenter = false,
                topLeft = Offset(centerX - ringRadius, centerY - ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = ringStrokeWidth)
            )
        }
        
        // 绘制十字线
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
        
        // 中心点
        drawCircle(
            color = color,
            radius = 3.dp.toPx(),
            center = Offset(centerX, centerY)
        )
    }
}
