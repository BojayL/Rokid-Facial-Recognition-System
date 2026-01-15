package com.sustech.bojayL.glasses.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sustech.bojayL.glasses.ui.theme.*

/**
 * 底部提示栏
 * 
 * 位于视野底部，显示操作引导或状态提示
 * 仅在需要时出现
 */
@Composable
fun ToastBar(
    message: String?,
    modifier: Modifier = Modifier
) {
    if (message.isNullOrEmpty()) return
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = GlassWhite.copy(alpha = 0.9f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(TransparentBlack)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}

/**
 * 操作提示类型
 */
object ToastMessages {
    const val TAP_TO_CAPTURE = "单击触摸板进行识别"
    const val DOUBLE_TAP_TO_RESET = "双击触摸板重置"
    const val LONG_PRESS_FOR_MENU = "长按打开菜单"
    const val TRANSMITTING = "数据传输中..."
    const val CONNECTING = "正在连接手机..."
    const val CONNECTED = "已连接"
    const val DISCONNECTED = "连接已断开"
}
