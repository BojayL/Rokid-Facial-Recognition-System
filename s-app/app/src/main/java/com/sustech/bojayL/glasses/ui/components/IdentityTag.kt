package com.sustech.bojayL.glasses.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sustech.bojayL.glasses.communication.FaceState
import com.sustech.bojayL.glasses.communication.RecognitionResult
import com.sustech.bojayL.glasses.ui.theme.*

/**
 * 身份标签组件
 * 
 * 显示在人脸框上方，包含：
 * - 姓名 + 置信度
 * - 班级信息
 * 
 * 设计要点：
 * - 半透明黑底白字
 * - 仅显示两行最关键信息
 * - 3 秒后自动淡出（由外部控制）
 */
@Composable
fun IdentityTag(
    result: RecognitionResult?,
    state: FaceState,
    modifier: Modifier = Modifier
) {
    if (state == FaceState.NONE || state == FaceState.DETECTING) {
        return  // 无标签显示
    }
    
    val backgroundColor = TransparentBlack
    val borderColor = when (state) {
        FaceState.RECOGNIZED -> GlassGreen
        FaceState.UNKNOWN -> GlassYellow
        FaceState.RECOGNIZING -> GlassBlue
        else -> Color.Transparent
    }
    
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            FaceState.RECOGNIZING -> {
                // 识别中状态
                RecognizingContent()
            }
            FaceState.RECOGNIZED -> {
                // 识别成功
                if (result != null) {
                    RecognizedContent(result = result)
                }
            }
            FaceState.UNKNOWN -> {
                // 未知人员
                UnknownContent()
            }
            else -> {}
        }
    }
}

/**
 * 识别中内容
 */
@Composable
private fun RecognizingContent() {
    Text(
        text = "识别中...",
        color = GlassBlue,
        fontSize = 20.sp,
        fontWeight = FontWeight.Medium
    )
}

/**
 * 识别成功内容
 */
@Composable
private fun RecognizedContent(result: RecognitionResult) {
    // 姓名 + 置信度
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = result.studentName ?: "未知",
            color = GlassGreen,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        // 置信度
        Text(
            text = "(${(result.confidence * 100).toInt()}%)",
            color = GlassWhite.copy(alpha = 0.7f),
            fontSize = 16.sp
        )
    }
    
    // 班级信息
    if (!result.className.isNullOrEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "班级：${result.className}",
            color = GlassWhite.copy(alpha = 0.8f),
            fontSize = 16.sp
        )
    }
}

/**
 * 未知人员内容
 */
@Composable
private fun UnknownContent() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "⚠",
            fontSize = 20.sp
        )
        Text(
            text = "未知人员",
            color = GlassYellow,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
        )
    }
    
    Spacer(modifier = Modifier.height(4.dp))
    
    Text(
        text = "请在手机端完善信息",
        color = GlassWhite.copy(alpha = 0.6f),
        fontSize = 14.sp
    )
}

/**
 * 状态指示器（用于人脸框右侧）
 */
@Composable
fun StatusIndicator(
    state: FaceState,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (state) {
        FaceState.RECOGNIZING -> "⏳" to GlassBlue
        FaceState.RECOGNIZED -> "✔️" to GlassGreen
        FaceState.UNKNOWN -> "❓" to GlassYellow
        else -> return
    }
    
    Text(
        text = icon,
        fontSize = 24.sp,
        modifier = modifier
    )
}
