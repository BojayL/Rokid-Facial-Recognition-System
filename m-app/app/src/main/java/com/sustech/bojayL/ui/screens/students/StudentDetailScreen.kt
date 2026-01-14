package com.sustech.bojayL.ui.screens.students

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sustech.bojayL.data.model.*
import com.sustech.bojayL.ui.components.StudentAvatar
import com.sustech.bojayL.ui.theme.*

/**
 * 学生详情页面
 * 
 * 根据 PRD 要求 STU-01：
 * 展示眼镜无法显示的详细信息：
 * - 基础信息：高清证件照、家长联系方式
 * - 学业信息：历史成绩折线图、偏科分析
 * - 行为记录：本学期迟到次数、课堂互动得分
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    student: Student,
    onBackClick: () -> Unit = {},
    onEnrollFace: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("学生档案") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // 头部信息卡片
            StudentHeaderCard(
                student = student,
                onEnrollFace = onEnrollFace
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 基础信息
            InfoSection(
                title = "基础信息",
                icon = Icons.Default.Person
            ) {
                InfoRow(label = "学号", value = student.studentId)
                InfoRow(label = "班级", value = student.className)
                InfoRow(label = "年级", value = student.grade)
                student.parentContact?.let {
                    InfoRow(label = "家长联系方式", value = it)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 学业信息
            student.academicInfo?.let { academic ->
                InfoSection(
                    title = "学业信息",
                    icon = Icons.Default.School
                ) {
                    InfoRow(label = "平均分", value = "${academic.averageScore} 分")
                    InfoRow(label = "年级排名", value = "第 ${academic.ranking} 名")
                    
                    // 各科成绩分析
                    if (academic.subjectAnalysis.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "各科成绩",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        academic.subjectAnalysis.forEach { (subject, score) ->
                            SubjectScoreBar(
                                subject = subject,
                                score = score
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // 行为记录
            student.behaviorRecord?.let { behavior ->
                InfoSection(
                    title = "行为记录",
                    icon = Icons.Default.Assessment
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BehaviorStatItem(
                            label = "迟到",
                            value = "${behavior.lateCount}次",
                            color = if (behavior.lateCount > 3) AccentRed else TextPrimary
                        )
                        BehaviorStatItem(
                            label = "缺勤",
                            value = "${behavior.absentCount}次",
                            color = if (behavior.absentCount > 2) AccentRed else TextPrimary
                        )
                        BehaviorStatItem(
                            label = "互动得分",
                            value = "${behavior.interactionScore.toInt()}分",
                            color = CyanPrimary
                        )
                        BehaviorStatItem(
                            label = "互动次数",
                            value = "${behavior.interactionCount}次",
                            color = AccentGreen
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // 人脸录入状态
            InfoSection(
                title = "人脸识别",
                icon = Icons.Default.Face
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (student.isEnrolled) "已录入人脸特征" else "未录入人脸特征",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (student.isEnrolled) AccentGreen else AccentOrange
                        )
                        Text(
                            text = if (student.isEnrolled) "可以通过眼镜识别" else "请先录入人脸以启用识别",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    
                    Button(
                        onClick = onEnrollFace,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = DarkBackground
                        )
                    ) {
                        Text(if (student.isEnrolled) "更新" else "录入")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 学生头部卡片
 */
@Composable
private fun StudentHeaderCard(
    student: Student,
    onEnrollFace: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 头像
            StudentAvatar(
                avatarUrl = student.avatarUrl,
                size = 96.dp,
                isHighlighted = student.isEnrolled
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 姓名
            Text(
                text = student.name,
                style = MaterialTheme.typography.headlineMedium,
                color = CyanPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // 班级和学号
            Text(
                text = "${student.className} · ${student.studentId}",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            
            // 标签
            if (student.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    student.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyanPrimary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 信息区块
 */
@Composable
private fun InfoSection(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            content()
        }
    }
}

/**
 * 信息行
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}

/**
 * 科目成绩条
 */
@Composable
private fun SubjectScoreBar(
    subject: String,
    score: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = subject,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(48.dp)
        )
        
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(DarkSurfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(score / 100f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            score >= 80 -> AccentGreen
                            score >= 60 -> AccentOrange
                            else -> AccentRed
                        }
                    )
            )
        }
        
        Text(
            text = "${score.toInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            modifier = Modifier.width(36.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

/**
 * 行为统计项
 */
@Composable
private fun BehaviorStatItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
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
private fun StudentDetailScreenPreview() {
    MobileappTheme {
        StudentDetailScreen(
            student = Student(
                id = "s1",
                studentId = "2021001",
                name = "张三",
                className = "高三(2)班",
                grade = "高三",
                parentContact = "138****8888",
                isEnrolled = true,
                tags = listOf("班长", "优秀"),
                academicInfo = AcademicInfo(
                    averageScore = 85.5f,
                    ranking = 12,
                    subjectAnalysis = mapOf(
                        "语文" to 88f,
                        "数学" to 92f,
                        "英语" to 78f,
                        "物理" to 85f
                    )
                ),
                behaviorRecord = BehaviorRecord(
                    lateCount = 2,
                    absentCount = 0,
                    interactionScore = 88f,
                    interactionCount = 15
                )
            )
        )
    }
}
