package com.sustech.bojayL.glasses.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sustech.bojayL.glasses.communication.FaceState
import com.sustech.bojayL.glasses.ui.components.*
import com.sustech.bojayL.glasses.viewmodel.HudViewModel
import kotlinx.coroutines.delay

/**
 * AR HUD 主界面
 * 
 * 状态切换：
 * 1. 未连接时：显示配对界面 (PairingScreen)
 * 2. 已连接时：显示 AR 识别界面 (RecognitionScreen)
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
    var wasConnected by remember { mutableStateOf(false) }
    
    // 监听连接状态变化，显示连接成功动画
    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected && !wasConnected) {
            showConnectionSuccess = true
            delay(2000)  // 显示2秒
            showConnectionSuccess = false
        }
        wasConnected = uiState.isConnected
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
            // 根据连接状态显示不同界面
            AnimatedContent(
                targetState = uiState.isConnected,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "screen_transition"
            ) { isConnected ->
                if (isConnected) {
                    // 已连接：显示识别界面
                    RecognitionScreen(
                        uiState = uiState
                    )
                } else {
                    // 未连接：显示配对界面
                    PairingScreen()
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
            
            // 底部提示栏 - 始终显示（旋转后在视觉底部）
            ToastBar(
                message = uiState.toastMessage,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * 识别界面 - 已连接时显示
 * 
 * 纵向布局（旋转后）：
 * - 顶部状态栏（无背景）
 * - 中央人脸框 + 身份标签
 */
@Composable
private fun RecognitionScreen(
    uiState: com.sustech.bojayL.glasses.viewmodel.HudUiState
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(vertical = 24.dp)  // 纵向留出边距
    ) {
        // 顶部状态栏 - 无背景，纯文字
        StatusBar(
            isConnected = uiState.isConnected,
            isRecording = uiState.isRecording,
            batteryLevel = uiState.batteryLevel,
            captureMode = uiState.captureMode,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        
        // 中央 AR 层 - 仅在有识别结果时显示
        if (uiState.faceState != FaceState.NONE) {
            CentralArLayer(
                faceState = uiState.faceState,
                recognitionResult = uiState.recognitionResult,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * 中央 AR 层
 * 
 * 纵向布局：身份标签在上，人脸框居中，状态指示器在下
 */
@Composable
private fun CentralArLayer(
    faceState: com.sustech.bojayL.glasses.communication.FaceState,
    recognitionResult: com.sustech.bojayL.glasses.communication.RecognitionResult?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 身份标签（人脸框上方）
        IdentityTag(
            result = recognitionResult,
            state = faceState,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        // 人脸框 - 增大尺寸以充分利用纵向空间
        FaceFrame(
            state = faceState,
            size = 280.dp
        )
        
        // 状态指示器（人脸框下方）
        StatusIndicator(
            state = faceState,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}
