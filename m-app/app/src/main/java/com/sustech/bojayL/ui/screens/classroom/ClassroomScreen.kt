package com.sustech.bojayL.ui.screens.classroom

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sustech.bojayL.data.model.*
import com.sustech.bojayL.ui.components.*
import com.sustech.bojayL.ui.theme.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.google.accompanist.permissions.isGranted

/**
 * 课堂页面 - 核心主页
 * 
 * 根据 PRD 要求：
 * - 展示实时识别流、当前课程状态
 * - 课程会话控制（开始/结束上课）
 * - 识别结果流（卡片流形式）
 */
@OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
@Composable
fun ClassroomScreen(
    deviceState: DeviceState,
    viewModel: ClassroomViewModel,
    students: List<Student>,
    cameraPermissionState: com.google.accompanist.permissions.PermissionState,
    onDeviceClick: () -> Unit = {},
    onStudentClick: (Student) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    
    // 当有新识别结果时，自动滚动到顶部
    LaunchedEffect(uiState.recognitionResults.firstOrNull()?.id) {
        if (uiState.recognitionResults.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部设备连接状态栏
        ConnectionStatusBar(
            deviceState = deviceState,
            onClick = onDeviceClick
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 课程信息卡片
        SessionInfoCard(
            className = uiState.session.className,
            courseName = uiState.session.courseName,
            status = uiState.session.status,
            recognizedCount = uiState.session.recognizedCount,
            totalStudents = uiState.session.totalStudents
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 工作模式选择器
        WorkModeSelector(
            currentMode = uiState.workMode,
            onModeSelected = { mode ->
                viewModel.setWorkMode(mode)
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 开始/结束上课按钮
        SessionControlButton(
            sessionStatus = uiState.session.status,
            workMode = uiState.workMode,
            deviceState = deviceState,
            onStartSession = {
                when (uiState.workMode) {
                    WorkMode.GLASSES -> viewModel.startSession()
                    WorkMode.PHONE_CAMERA -> {
                        // 检查相机权限
                        if (cameraPermissionState.status.isGranted) {
                            viewModel.startPhoneCameraSession()
                        } else {
                            // 请求权限
                            cameraPermissionState.launchPermissionRequest()
                        }
                    }
                }
            },
            onEndSession = { viewModel.endSession() }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 识别结果流标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "识别结果",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            // 自动弹窗开关
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "识别即弹窗",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Switch(
                    checked = uiState.autoPopupEnabled,
                    onCheckedChange = { viewModel.toggleAutoPopup() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanPrimary,
                        checkedTrackColor = CyanPrimary.copy(alpha = 0.5f)
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 识别结果列表
        if (uiState.recognitionResults.isEmpty()) {
            // 空状态
            EmptyRecognitionState(
                sessionStatus = uiState.session.status,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = uiState.recognitionResults,
                    key = { it.id }
                ) { result ->
                    result.student?.let { student ->
                        StudentCard(
                            student = student,
                            attendanceStatus = result.attendanceStatus,
                            isHighlighted = result.isHighlighted,
                            showQuickActions = uiState.session.status == SessionStatus.ACTIVE,
                            onCardClick = { 
                                onStudentClick(student)
                            },
                            onQuickAction = { action ->
                                viewModel.handleQuickAction(result.id, action)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 空识别状态
 */
@Composable
fun EmptyRecognitionState(
    sessionStatus: SessionStatus,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = when (sessionStatus) {
                    SessionStatus.IDLE -> "点击「开始上课」开始识别"
                    SessionStatus.ACTIVE -> "等待眼镜识别学生..."
                    SessionStatus.PAUSED -> "课堂已暂停"
                    SessionStatus.ENDED -> "本次课程已结束"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = TextTertiary
            )
            
            if (sessionStatus == SessionStatus.ACTIVE) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = CyanPrimary,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

/**
 * 工作模式选择器
 */
@Composable
private fun WorkModeSelector(
    currentMode: WorkMode,
    onModeSelected: (WorkMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WorkMode.entries.forEach { mode ->
            val isSelected = mode == currentMode
            
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onModeSelected(mode) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) CyanPrimary.copy(alpha = 0.2f) else DarkSurface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = if (isSelected) CyanPrimary else Color.Transparent
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (mode) {
                            WorkMode.GLASSES -> Icons.Default.Videocam
                            WorkMode.PHONE_CAMERA -> Icons.Default.Smartphone
                        },
                        contentDescription = mode.label,
                        tint = if (isSelected) CyanPrimary else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) CyanPrimary else TextSecondary
                    )
                }
            }
        }
    }
}
