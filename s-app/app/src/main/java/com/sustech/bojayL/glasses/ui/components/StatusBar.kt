package com.sustech.bojayL.glasses.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sustech.bojayL.glasses.communication.CaptureMode
import com.sustech.bojayL.glasses.ui.theme.*

/**
 * AR HUD 状态栏
 * 
 * 位于视野顶部，显示：
 * - 🔴 录制状态（摄像头工作中）
 * - 📶 连接状态
 * - 🔋 电量
 * - 👥 识别统计
 * 
 * 纵向布局优化：左右对称显示关键信息
 */
@Composable
fun StatusBar(
    isConnected: Boolean,
    isRecording: Boolean,
    batteryLevel: Int,
    captureMode: CaptureMode,
    recognizedCount: Int = 0,
    captureCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：录制状态 + 模式
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 录制指示器
            RecordingIndicator(isRecording = isRecording)
            
            // 当前模式
            ModeIndicator(mode = captureMode)
        }
        
        // 中间：识别统计
        RecognitionStatsIndicator(
            recognizedCount = recognizedCount,
            captureCount = captureCount
        )
        
        // 右侧：连接状态 + 电量
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 连接状态
            ConnectionIndicator(isConnected = isConnected)
            
            // 电量
            BatteryIndicator(level = batteryLevel)
        }
    }
}

/**
 * 录制状态指示器
 */
@Composable
private fun RecordingIndicator(isRecording: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 红色圆点
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = if (isRecording) GlassRed else Color.Gray,
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        )
        if (isRecording) {
            Text(
                text = "REC",
                color = GlassRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 模式指示器
 */
@Composable
private fun ModeIndicator(mode: CaptureMode) {
    val modeText = when (mode) {
        CaptureMode.AUTO -> "自动采集"
        CaptureMode.MANUAL -> "手动采集"
    }
    
    Text(
        text = modeText,
        color = GlassWhite.copy(alpha = 0.8f),
        fontSize = 14.sp
    )
}

/**
 * 连接状态指示器
 */
@Composable
private fun ConnectionIndicator(isConnected: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // WiFi 图标（简化为文字）
        Text(
            text = if (isConnected) "📶" else "📵",
            fontSize = 14.sp
        )
        Text(
            text = if (isConnected) "已连接" else "未连接",
            color = if (isConnected) GlassGreen else GlassYellow,
            fontSize = 12.sp
        )
    }
}

/**
 * 电量指示器
 */
@Composable
private fun BatteryIndicator(level: Int) {
    val color = when {
        level <= 20 -> GlassRed
        level <= 50 -> GlassYellow
        else -> GlassGreen
    }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "🔋",
            fontSize = 14.sp
        )
        Text(
            text = "$level%",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 识别统计指示器
 */
@Composable
private fun RecognitionStatsIndicator(
    recognizedCount: Int,
    captureCount: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 已识别人数
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✅",
                fontSize = 14.sp
            )
            Text(
                text = "$recognizedCount",
                color = GlassGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        // 采集次数
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📷",
                fontSize = 14.sp
            )
            Text(
                text = "$captureCount",
                color = GlassWhite.copy(alpha = 0.8f),
                fontSize = 14.sp
            )
        }
    }
}
