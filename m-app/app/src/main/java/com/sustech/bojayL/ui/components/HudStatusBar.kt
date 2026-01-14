package com.sustech.bojayL.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * HUD 风格状态栏
 * 
 * 顶部边缘显示系统状态信息。
 * 设计风格：
 * - 极小字号
 * - 线性图标（Outlined）
 * - 高对比度纯绿色
 */
@Composable
fun HudStatusBar(
    modifier: Modifier = Modifier,
    wifiConnected: Boolean = true,
    batteryLevel: Int = 100,
    recognizedCount: Int = 0,
    totalStudents: Int = 0,
    isRecording: Boolean = false,
    color: Color = HudGreen
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 绘制边框背景（可选）
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = color.copy(alpha = 0.1f),
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：WiFi + 电量
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // WiFi 状态
                WifiIndicator(
                    connected = wifiConnected,
                    color = color
                )
                
                // 电量指示
                BatteryIndicator(
                    level = batteryLevel,
                    color = color
                )
            }
            
            // 中间：录制状态（可选）
            if (isRecording) {
                RecordingIndicator(color = HudRed)
            }
            
            // 右侧：识别统计
            RecognitionStats(
                recognized = recognizedCount,
                total = totalStudents,
                color = color
            )
        }
    }
}

/**
 * WiFi 状态指示器
 */
@Composable
private fun WifiIndicator(
    connected: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (connected) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (connected) color else color.copy(alpha = 0.5f)
        )
    }
}

/**
 * 电量指示器
 */
@Composable
private fun BatteryIndicator(
    level: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val batteryIcon = when {
        level > 80 -> Icons.Outlined.BatteryFull
        level > 50 -> Icons.Outlined.Battery5Bar
        level > 20 -> Icons.Outlined.Battery3Bar
        else -> Icons.Outlined.Battery1Bar
    }
    
    val batteryColor = when {
        level > 20 -> color
        level > 10 -> HudYellow
        else -> HudRed
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = batteryIcon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = batteryColor
        )
        Text(
            text = "$level%",
            color = batteryColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 录制状态指示器
 */
@Composable
private fun RecordingIndicator(
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 录制圆点
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(
                color = color,
                radius = size.minDimension / 2
            )
        }
        Text(
            text = "REC",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 识别统计
 */
@Composable
private fun RecognitionStats(
    recognized: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.People,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Text(
            text = "$recognized/$total",
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 精简版状态栏（仅显示核心信息）
 */
@Composable
fun HudStatusBarCompact(
    modifier: Modifier = Modifier,
    faceCount: Int = 0,
    recognizedCount: Int = 0,
    color: Color = HudGreen
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 检测人脸数
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Face,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = color
            )
            Text(
                text = "$faceCount",
                color = color,
                fontSize = 10.sp
            )
        }
        
        // 已识别数
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = HudCyan
            )
            Text(
                text = "$recognizedCount",
                color = HudCyan,
                fontSize = 10.sp
            )
        }
    }
}
