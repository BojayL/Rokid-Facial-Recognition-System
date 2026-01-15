package com.sustech.bojayL.glasses.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sustech.bojayL.glasses.ui.theme.*

/**
 * 配对界面
 * 
 * AR眼镜启动时显示，等待手机端连接
 * 设计要点：
 * - 透明背景，仅显示必要信息
 * - 居中显示配对提示
 * - 显示设备标识供手机端识别
 * - 纵向布局优化：充分利用屏幕高度
 */
@Composable
fun PairingScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        // 配对信息卡片 - 半透明背景，充分利用纵向空间
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)  // 占据 85% 宽度
                .clip(RoundedCornerShape(20.dp))
                .background(TransparentBlack)
                .border(2.dp, GlassBlue, RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 标题 - 增大字号
            Text(
                text = "AR 智慧课堂",
                color = GlassWhite,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 状态图标 - 增大
            Text(
                text = "📱",
                fontSize = 64.sp
            )
            
            // 提示文字 - 增大字号
            Text(
                text = "等待手机连接...",
                color = GlassBlue,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 设备信息
            DeviceInfoSection()
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 操作提示 - 增大字号
            Text(
                text = "请在手机端打开 AR 智慧课堂\n点击「设备」→「扫描设备」",
                color = GlassWhite.copy(alpha = 0.7f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }
    }
}

/**
 * 设备信息区域 - 纵向布局优化
 */
@Composable
private fun DeviceInfoSection() {
    val deviceName = Build.MODEL
    val deviceId = Build.SERIAL.takeLast(6).ifEmpty { "ROKID" }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x30FFFFFF))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "设备名称",
            color = GlassWhite.copy(alpha = 0.6f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = deviceName,
            color = GlassGreen,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 连接成功提示 - 纵向布局优化
 */
@Composable
fun ConnectionSuccessOverlay(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(TransparentBlack)
                .padding(horizontal = 48.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "✓",
                color = GlassGreen,
                fontSize = 80.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "连接成功",
                color = GlassGreen,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
