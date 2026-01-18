package com.sustech.bojayL.glasses.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sustech.bojayL.glasses.communication.CaptureMode
import com.sustech.bojayL.glasses.communication.FaceState
import com.sustech.bojayL.glasses.communication.RecognitionResult
import com.sustech.bojayL.glasses.ui.components.*
import com.sustech.bojayL.glasses.ui.theme.*
import com.sustech.bojayL.glasses.viewmodel.HudUiState
import com.sustech.bojayL.glasses.viewmodel.HudViewModel
import kotlinx.coroutines.delay

/**
 * AR HUD 主界面
 * 
 * 状态切换：
 * 1. 未连接时：显示配对界面 (PairingScreen) - 显示配对码
 * 2. 已连接但未配对：显示配对界面 - 等待输入配对码
 * 3. 已配对：显示 AR 识别界面 (RecognitionScreen)
 * 
 * AR眼镜设计要点：
 * - 全透明背景
 * - 仅显示必要的UI元素
 * - UI 逆时针旋转 90 度以适配眼镜物理屏幕方向
 */
@Composable
fun HudScreen(
    viewModel: HudViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // 连接成功动画状态
    var showConnectionSuccess by remember { mutableStateOf(false) }
    // 使用 rememberSaveable 防止配置变化时丢失状态
    var previousPairedState by remember { mutableStateOf(false) }
    
    // 监听配对状态变化，显示配对成功动画
    // 仅在从未配对变为已配对时触发
    LaunchedEffect(uiState.isPaired) {
        if (uiState.isPaired && !previousPairedState) {
            // 从未配对 -> 已配对，显示连接成功动画
            showConnectionSuccess = true
            delay(2000)  // 显示2秒
            showConnectionSuccess = false
        }
        // 更新前一次状态
        previousPairedState = uiState.isPaired
    }
    
    // 使用 RotatedLayout 将整个 UI 逆时针旋转 90 度
    // 适配 Rokid AR 眼镜的物理屏幕方向
    RotatedLayout(
        modifier = modifier.background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
            // 根据配对状态显示不同界面
            AnimatedContent(
                targetState = uiState.isPaired,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "screen_transition"
            ) { isPaired ->
                if (isPaired) {
                    // 已配对：显示识别界面
                    RecognitionScreen(
                        uiState = uiState
                    )
                } else {
                    // 未配对：显示等待连接界面
                    PairingScreen(
                        isConnected = uiState.isConnected,
                        isPaired = uiState.isPaired
                    )
                }
            }
            
            // 连接成功提示覆盖层
            AnimatedVisibility(
                visible = showConnectionSuccess,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ConnectionSuccessOverlay()
            }
            
            // 底部提示栏 - 显示操作提示或状态信息
            ToastBar(
                message = uiState.toastMessage ?: getContextualHint(uiState),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * 根据当前状态获取上下文相关的操作提示
 */
private fun getContextualHint(uiState: HudUiState): String? {
    // 如果有识别结果，不显示提示
    if (uiState.faceState == FaceState.RECOGNIZED || uiState.faceState == FaceState.UNKNOWN) {
        return null
    }
    
    // 识别中状态
    if (uiState.faceState == FaceState.RECOGNIZING) {
        return "识别中..."
    }
    
    // 根据模式显示不同提示
    return when {
        !uiState.isRecording -> "单击开始采集"
        uiState.captureMode == CaptureMode.MANUAL -> "单击触摸板进行识别"
        uiState.captureMode == CaptureMode.AUTO -> "自动采集中..."
        else -> null
    }
}

/**
 * 识别界面 - 已连接时显示
 * 
 * 极简AR布局，避免与视线重叠：
 * - 中央：简洁圆点准心
 * - 左下角：信息卡片（识别结果）
 * - 右下角：人数统计
 */
@Composable
private fun RecognitionScreen(
    uiState: HudUiState
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
    ) {
        // 中央准心（根据配置显示/隐藏）
        if (uiState.showReticle) {
            ReticleOverlay(
                state = uiState.faceState,
                animated = uiState.isRecording,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // 左下角：信息卡片
        AnimatedVisibility(
            visible = uiState.faceState != FaceState.NONE && uiState.faceState != FaceState.DETECTING,
            enter = fadeIn() + slideInHorizontally { -it },
            exit = fadeOut() + slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            CompactIdentityCard(
                result = uiState.recognitionResult,
                state = uiState.faceState
            )
        }
        
        // 右下角：人数统计
        CompactStatsIndicator(
            recognizedCount = uiState.recognizedCount,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

/**
 * 紧凑型身份卡片 - 左下角显示
 */
@Composable
private fun CompactIdentityCard(
    result: RecognitionResult?,
    state: FaceState
) {
    val backgroundColor = Color.Black.copy(alpha = 0.6f)
    val borderColor = when (state) {
        FaceState.RECOGNIZED -> GlassGreen
        FaceState.UNKNOWN -> GlassYellow
        FaceState.RECOGNIZING -> GlassBlue
        else -> Color.Transparent
    }
    
    Row(
        modifier = Modifier
            .background(backgroundColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 状态图标
        val icon = when (state) {
            FaceState.RECOGNIZING -> "⏳"
            FaceState.RECOGNIZED -> "✓"
            FaceState.UNKNOWN -> "?"
            else -> ""
        }
        Text(
            text = icon,
            color = borderColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        // 内容
        when (state) {
            FaceState.RECOGNIZING -> {
                Text(
                    text = "识别中",
                    color = GlassBlue,
                    fontSize = 16.sp
                )
            }
            FaceState.RECOGNIZED -> {
                Column {
                    Text(
                        text = result?.studentName ?: "未知",
                        color = GlassGreen,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!result?.className.isNullOrEmpty()) {
                        Text(
                            text = result?.className ?: "",
                            color = GlassWhite.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            FaceState.UNKNOWN -> {
                Text(
                    text = "未知人员",
                    color = GlassYellow,
                    fontSize = 16.sp
                )
            }
            else -> {}
        }
    }
}

/**
 * 紧凑型统计指示器 - 右下角显示
 */
@Composable
private fun CompactStatsIndicator(
    recognizedCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "👥",
            fontSize = 14.sp
        )
        Text(
            text = "$recognizedCount",
            color = GlassGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
