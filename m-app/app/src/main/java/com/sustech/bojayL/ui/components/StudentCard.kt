package com.sustech.bojayL.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sustech.bojayL.data.model.AttendanceStatus
import com.sustech.bojayL.data.model.QuickAction
import com.sustech.bojayL.data.model.Student
import com.sustech.bojayL.ui.theme.*

/**
 * 学生信息卡片 - 大卡片设计
 * 
 * 根据 PRD 要求：
 * - 大卡片与易读性：字号比普通 APP 大 20%
 * - 核心信息卡片化，摒弃复杂的列表线
 * - 卡片展示：头像(大图)、姓名、学号、最近一次考勤状态
 */
@Composable
fun StudentCard(
    student: Student,
    attendanceStatus: AttendanceStatus = AttendanceStatus.UNKNOWN,
    isHighlighted: Boolean = false,
    showQuickActions: Boolean = true,
    onCardClick: () -> Unit = {},
    onQuickAction: (QuickAction) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .then(
                if (isHighlighted) {
                    Modifier.border(
                        width = 2.dp,
                        color = CyanPrimary,
                        shape = RoundedCornerShape(16.dp)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) DarkSurfaceVariant else CardBackground
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHighlighted) 8.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像（大图）
            StudentAvatar(
                avatarUrl = student.avatarUrl,
                size = 72.dp,
                isHighlighted = isHighlighted
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 学生信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // 姓名 - 高亮显示
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighlighted) CyanPrimary else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 学号和班级
                Text(
                    text = "${student.className} · ${student.studentId}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 考勤状态标签
                AttendanceStatusChip(status = attendanceStatus)
            }
            
            // 快捷操作按钮
            if (showQuickActions) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 提问按钮
                    QuickActionButton(
                        action = QuickAction.QUESTION,
                        onClick = { onQuickAction(QuickAction.QUESTION) }
                    )
                    // 异常按钮
                    QuickActionButton(
                        action = QuickAction.ABNORMAL,
                        onClick = { onQuickAction(QuickAction.ABNORMAL) }
                    )
                }
            }
        }
    }
}

/**
 * 学生头像组件
 */
@Composable
fun StudentAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 64.dp,
    isHighlighted: Boolean = false
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(DarkSurfaceVariant)
            .then(
                if (isHighlighted) {
                    Modifier.border(2.dp, CyanPrimary, CircleShape)
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "学生头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "默认头像",
                modifier = Modifier.size(size * 0.6f),
                tint = TextTertiary
            )
        }
    }
}

/**
 * 考勤状态标签
 */
@Composable
fun AttendanceStatusChip(
    status: AttendanceStatus,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, text) = when (status) {
        AttendanceStatus.PRESENT -> Triple(
            AttendancePresent.copy(alpha = 0.2f),
            AttendancePresent,
            "出勤"
        )
        AttendanceStatus.ABSENT -> Triple(
            AttendanceAbsent.copy(alpha = 0.2f),
            AttendanceAbsent,
            "缺勤"
        )
        AttendanceStatus.LATE -> Triple(
            AttendanceLate.copy(alpha = 0.2f),
            AttendanceLate,
            "迟到"
        )
        AttendanceStatus.LEAVE -> Triple(
            AccentYellow.copy(alpha = 0.2f),
            AccentYellow,
            "请假"
        )
        AttendanceStatus.UNKNOWN -> Triple(
            TextTertiary.copy(alpha = 0.2f),
            TextTertiary,
            "未知"
        )
    }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 快捷操作按钮
 * 
 * 根据 PRD 要求 CLS-04：
 * - [提问]: 记录一次课堂互动
 * - [异常]: 标记状态（如睡觉、玩手机）
 */
@Composable
fun QuickActionButton(
    action: QuickAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, backgroundColor, contentColor) = when (action) {
        QuickAction.QUESTION -> Triple(
            Icons.Default.QuestionAnswer,
            CyanPrimary.copy(alpha = 0.2f),
            CyanPrimary
        )
        QuickAction.ABNORMAL -> Triple(
            Icons.Default.Warning,
            AccentOrange.copy(alpha = 0.2f),
            AccentOrange
        )
    }
    
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(44.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = action.label,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 简化版学生卡片 - 用于列表显示
 */
@Composable
fun StudentListItem(
    student: Student,
    isRecognized: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = DarkBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            StudentAvatar(
                avatarUrl = student.avatarUrl,
                size = 48.dp
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${student.className} · ${student.studentId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            // 识别状态
            if (isRecognized) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AccentGreen.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "已识别",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentGreen
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun StudentCardPreview() {
    MobileappTheme {
        StudentCard(
            student = Student(
                id = "1",
                studentId = "2021001",
                name = "张三",
                className = "高三(2)班",
                grade = "高三"
            ),
            attendanceStatus = AttendanceStatus.PRESENT,
            isHighlighted = true,
            modifier = Modifier.padding(16.dp)
        )
    }
}
