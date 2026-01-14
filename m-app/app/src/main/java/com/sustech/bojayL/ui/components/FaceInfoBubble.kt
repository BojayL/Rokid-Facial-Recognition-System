package com.sustech.bojayL.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sustech.bojayL.data.model.AttendanceStatus
import com.sustech.bojayL.data.model.Student

/**
 * HUD 风格信息气泡组件
 * 
 * 跟随人脸框下方显示学生信息。
 * 设计风格：
 * - 镂空边框设计（纯绿色线框，无填充背景）
 * - 姓名大号加粗
 * - 班级 + 状态图标小号
 */
@Composable
fun FaceInfoBubble(
    student: Student,
    attendanceStatus: AttendanceStatus = AttendanceStatus.PRESENT,
    x: Float,  // 气泡中心 X 坐标
    y: Float,  // 气泡顶部 Y 坐标
    viewWidth: Float,
    viewHeight: Float = 0f,
    modifier: Modifier = Modifier,
    color: Color = HudCyan,
    animated: Boolean = true,
    isLandscapeMode: Boolean = false  // 横屏模式（组件旋转90度）
) {
    val density = LocalDensity.current
    
    // 入场动画
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(student.id) {
        isVisible = true
    }
    
    // 气泡尺寸
    val bubbleWidth = 180.dp
    val bubbleHeight = 70.dp
    val arrowHeight = 10.dp
    
    // 计算气泡位置（确保不超出屏幕）
    val bubbleWidthPx = with(density) { bubbleWidth.toPx() }
    val offsetX = (x - bubbleWidthPx / 2).coerceIn(
        16f,
        viewWidth - bubbleWidthPx - 16f
    )
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)) + scaleIn(
            initialScale = 0.8f,
            animationSpec = tween(200, easing = EaseOutBack)
        ),
        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f),
        modifier = modifier
            .offset {
                IntOffset(offsetX.toInt(), y.toInt())
            }
            .then(
                if (isLandscapeMode) {
                    // 横屏模式：绕自身中心旋转90度
                    Modifier.graphicsLayer {
                        rotationZ = 90f
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 向上的箭头
            Canvas(
                modifier = Modifier
                    .width(20.dp)
                    .height(arrowHeight)
            ) {
                val path = Path().apply {
                    moveTo(size.width / 2, 0f)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            
            // 信息卡片（镂空边框）
            Box(
                modifier = Modifier
                    .width(bubbleWidth)
                    .height(bubbleHeight)
            ) {
                // 绘制镂空边框
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // 绘制圆角矩形边框
                    drawRoundRect(
                        color = color,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    
                    // 绘制分隔线
                    val dividerY = size.height * 0.55f
                    drawLine(
                        color = color.copy(alpha = 0.5f),
                        start = Offset(12.dp.toPx(), dividerY),
                        end = Offset(size.width - 12.dp.toPx(), dividerY),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                
                // 内容
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 顶部：图标 + 姓名
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Face,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = color
                        )
                        Text(
                            text = student.name,
                            color = color,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    
                    // 底部：班级 + 状态
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = student.className,
                            color = color.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        
                        // 状态指示器
                        StatusIndicator(
                            status = attendanceStatus,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

/**
 * 状态指示器
 */
@Composable
private fun StatusIndicator(
    status: AttendanceStatus,
    color: Color,
    modifier: Modifier = Modifier
) {
    val (icon, text, statusColor) = when (status) {
        AttendanceStatus.PRESENT -> Triple(Icons.Outlined.CheckCircle, "正常", HudGreen)
        AttendanceStatus.LATE -> Triple(Icons.Outlined.Schedule, "迟到", HudYellow)
        AttendanceStatus.ABSENT -> Triple(Icons.Outlined.Cancel, "缺勤", HudRed)
        AttendanceStatus.LEAVE -> Triple(Icons.Outlined.EventBusy, "请假", HudYellow)
        AttendanceStatus.UNKNOWN -> Triple(Icons.Outlined.Help, "未知", color)
    }
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = statusColor
        )
        Text(
            text = text,
            color = statusColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 多人脸信息气泡叠加层
 */
@Composable
fun FaceInfoBubblesOverlay(
    faces: List<HudFaceResult>,
    viewWidth: Float,
    viewHeight: Float,
    modifier: Modifier = Modifier,
    mirrorX: Boolean = false,
    isLandscapeMode: Boolean = false  // 横屏模式
) {
    Box(modifier = modifier.fillMaxSize()) {
        faces.forEach { face ->
            if (face.state == FaceDetectionState.RECOGNIZED && face.student != null) {
                // 计算缩放比例（从图像坐标到视图坐标）
                val scaleX = if (face.imageWidth > 0) viewWidth / face.imageWidth else 1f
                val scaleY = if (face.imageHeight > 0) viewHeight / face.imageHeight else 1f
                
                // 计算气泡位置（人脸框下方）
                val faceRect = face.rect
                val scaledCenterX = (faceRect.left + faceRect.right) / 2 * scaleX
                val scaledBottom = faceRect.bottom * scaleY
                
                val centerX = if (mirrorX) {
                    viewWidth - scaledCenterX
                } else {
                    scaledCenterX
                }
                val topY = scaledBottom + 8f
                
                FaceInfoBubble(
                    student = face.student,
                    attendanceStatus = AttendanceStatus.PRESENT,  // 可从 face 获取
                    x = centerX,
                    y = topY,
                    viewWidth = viewWidth,
                    viewHeight = viewHeight,
                    isLandscapeMode = isLandscapeMode
                )
            }
        }
    }
}

/**
 * 简化版信息标签（仅显示姓名）
 */
@Composable
fun FaceNameTag(
    name: String,
    x: Float,
    y: Float,
    color: Color = HudCyan,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.offset {
            IntOffset(x.toInt() - 50, y.toInt())
        }
    ) {
        Canvas(
            modifier = Modifier
                .width(100.dp)
                .height(28.dp)
        ) {
            // 绘制边框
            drawRoundRect(
                color = color,
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        
        Text(
            text = name,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
