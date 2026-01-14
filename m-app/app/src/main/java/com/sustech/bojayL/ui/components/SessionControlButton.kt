package com.sustech.bojayL.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sustech.bojayL.data.model.*
import com.sustech.bojayL.ui.theme.*

/**
 * 课程会话控制按钮
 * 
 * 根据 PRD 要求 CLS-01：
 * 包含"开始上课"和"结束上课"按钮
 * 点击"开始"后，生成 Session ID 推送给眼镜
 */
@Composable
fun SessionControlButton(
    sessionStatus: SessionStatus,
    onStartSession: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier,
    workMode: WorkMode = WorkMode.GLASSES,
    deviceState: DeviceState? = null
) {
    val isActive = sessionStatus == SessionStatus.ACTIVE
    
    // 判断是否可以开始
    val canStart = when (workMode) {
        WorkMode.GLASSES -> deviceState?.connectionType != ConnectionType.DISCONNECTED
        WorkMode.PHONE_CAMERA -> true  // 手机模式始终可用
    }
    
    val enabled = if (isActive) true else canStart
    
    // 动画效果
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) AccentRed else CyanPrimary,
        label = "background"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.95f,
        label = "scale"
    )
    
    Button(
        onClick = {
            if (isActive) onEndSession() else onStartSession()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .scale(scale),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = Color.Black,
            disabledContainerColor = backgroundColor.copy(alpha = 0.5f),
            disabledContentColor = Color.Black.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = if (isActive) "结束上课" else "开始上课",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 紧凑版会话状态指示器
 */
@Composable
fun SessionStatusIndicator(
    status: SessionStatus,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (status) {
        SessionStatus.IDLE -> Pair(TextTertiary, "未开始")
        SessionStatus.ACTIVE -> Pair(AccentGreen, "进行中")
        SessionStatus.PAUSED -> Pair(AccentOrange, "已暂停")
        SessionStatus.ENDED -> Pair(TextTertiary, "已结束")
    }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 状态点
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .padding(0.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = color
                ) {}
            }
            
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 课程信息卡片
 */
@Composable
fun SessionInfoCard(
    className: String,
    courseName: String,
    status: SessionStatus,
    recognizedCount: Int,
    totalStudents: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 顶部：课程名称和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = courseName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = className,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                
                SessionStatusIndicator(status = status)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 统计信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "已识别",
                    value = recognizedCount.toString(),
                    color = CyanPrimary
                )
                StatItem(
                    label = "总人数",
                    value = totalStudents.toString(),
                    color = TextPrimary
                )
                StatItem(
                    label = "识别率",
                    value = if (totalStudents > 0) {
                        "${(recognizedCount * 100 / totalStudents)}%"
                    } else "0%",
                    color = AccentGreen
                )
            }
        }
    }
}

/**
 * 统计项
 */
@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun SessionControlButtonPreview() {
    MobileappTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SessionControlButton(
                sessionStatus = SessionStatus.IDLE,
                onStartSession = {},
                onEndSession = {}
            )
            
            SessionControlButton(
                sessionStatus = SessionStatus.ACTIVE,
                onStartSession = {},
                onEndSession = {}
            )
            
            SessionInfoCard(
                className = "高三(2)班",
                courseName = "高等数学",
                status = SessionStatus.ACTIVE,
                recognizedCount = 35,
                totalStudents = 42
            )
        }
    }
}
