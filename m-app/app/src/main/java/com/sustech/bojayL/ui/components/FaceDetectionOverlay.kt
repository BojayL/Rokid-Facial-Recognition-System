package com.sustech.bojayL.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.sustech.bojayL.data.model.Student

/**
 * 人脸检测状态
 */
enum class FaceDetectionState {
    DETECTING,      // 检测中 - 普通人脸（灰色，仅计数）
    TARGETED,       // 准心对准 - 等待识别（绿色高亮）
    RECOGNIZING,    // 识别中（黄色 + 呼吸动画）
    RECOGNIZED,     // 已识别（青色）
    UNKNOWN         // 未知人员（红色）
}

/**
 * HUD 风格人脸检测结果
 */
data class HudFaceResult(
    val rect: android.graphics.RectF,  // 人脸边界框（图像坐标）
    val confidence: Float,              // 置信度
    val state: FaceDetectionState,      // 检测状态
    val student: Student? = null,       // 识别到的学生
    val trackId: Int = -1,              // 跟踪 ID
    val landmarks: FloatArray? = null,  // 关键点
    val isTargeted: Boolean = false,    // 是否被准心对准
    val imageWidth: Int = 0,            // 原始图像宽度（用于坐标缩放）
    val imageHeight: Int = 0            // 原始图像高度
)

/**
 * HUD 风格人脸检测框绘制组件
 * 
 * 在相机预览上叠加绘制检测到的人脸边界框。
 * 设计风格：
 * - 断角框（四个角的 L 型线条）
 * - 状态颜色编码
 * - 识别中呼吸闪烁动画
 */
@Composable
fun FaceDetectionOverlay(
    faces: List<HudFaceResult>,
    viewWidth: Float,
    viewHeight: Float,
    modifier: Modifier = Modifier,
    mirrorX: Boolean = false,  // 前置摄像头镜像
    isLandscapeMode: Boolean = false  // 横屏模式（组件旋转90度）
) {
    // 呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "face_box")
    val breathingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        if (faces.isEmpty()) return@Canvas
        
        faces.forEach { face ->
            // 计算缩放比例（从图像坐标到视图坐标）
            val scaleX = if (face.imageWidth > 0) viewWidth / face.imageWidth else 1f
            val scaleY = if (face.imageHeight > 0) viewHeight / face.imageHeight else 1f
            
            // 缩放并镜像处理
            val rect = if (mirrorX) {
                Rect(
                    left = viewWidth - face.rect.right * scaleX,
                    top = face.rect.top * scaleY,
                    right = viewWidth - face.rect.left * scaleX,
                    bottom = face.rect.bottom * scaleY
                )
            } else {
                Rect(
                    left = face.rect.left * scaleX,
                    top = face.rect.top * scaleY,
                    right = face.rect.right * scaleX,
                    bottom = face.rect.bottom * scaleY
                )
            }
            
            // 根据状态选择颜色
            val baseColor = when (face.state) {
                FaceDetectionState.DETECTING -> HudGray  // 普通人脸用灰色
                FaceDetectionState.TARGETED -> HudGreen  // 准心对准用绿色
                FaceDetectionState.RECOGNIZING -> HudYellow
                FaceDetectionState.RECOGNIZED -> HudCyan
                FaceDetectionState.UNKNOWN -> HudRed
            }
            
            // 普通检测状态的人脸只显示简化标记（不显示断角框）
            if (face.state == FaceDetectionState.DETECTING) {
                // 只在人脸中心绘制一个小圆点表示检测到
                val centerX = (rect.left + rect.right) / 2
                val centerY = (rect.top + rect.bottom) / 2
                drawCircle(
                    color = baseColor.copy(alpha = 0.6f),
                    radius = 6.dp.toPx(),
                    center = Offset(centerX, centerY)
                )
                return@forEach  // 跳过断角框绘制
            }
            
            // 识别中状态使用呼吸动画
            val color = if (face.state == FaceDetectionState.RECOGNIZING) {
                baseColor.copy(alpha = breathingAlpha)
            } else {
                baseColor
            }
            
            // 断角框参数
            val cornerLength = minOf(rect.width, rect.height) * 0.25f
            val strokeWidth = 3.dp.toPx()
            
            // 人脸框中心点
            val centerX = (rect.left + rect.right) / 2
            val centerY = (rect.top + rect.bottom) / 2
            
            // 横屏模式：绕人脸中心旋转90度绘制
            if (isLandscapeMode) {
                drawContext.canvas.nativeCanvas.save()
                drawContext.canvas.nativeCanvas.rotate(90f, centerX, centerY)
            }
            
            // 绘制四个角的 L 型线条
            // 左上角
            drawLine(
                color = color,
                start = Offset(rect.left, rect.top),
                end = Offset(rect.left + cornerLength, rect.top),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(rect.left, rect.top),
                end = Offset(rect.left, rect.top + cornerLength),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            
            // 右上角
            drawLine(
                color = color,
                start = Offset(rect.right, rect.top),
                end = Offset(rect.right - cornerLength, rect.top),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(rect.right, rect.top),
                end = Offset(rect.right, rect.top + cornerLength),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            
            // 左下角
            drawLine(
                color = color,
                start = Offset(rect.left, rect.bottom),
                end = Offset(rect.left + cornerLength, rect.bottom),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(rect.left, rect.bottom),
                end = Offset(rect.left, rect.bottom - cornerLength),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            
            // 右下角
            drawLine(
                color = color,
                start = Offset(rect.right, rect.bottom),
                end = Offset(rect.right - cornerLength, rect.bottom),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(rect.right, rect.bottom),
                end = Offset(rect.right, rect.bottom - cornerLength),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            
            // 绘制状态文本（仅在准心对准或识别中状态时显示）
            if (face.state != FaceDetectionState.RECOGNIZED && face.state != FaceDetectionState.DETECTING) {
                val statusText = when (face.state) {
                    FaceDetectionState.TARGETED -> "对准"
                    FaceDetectionState.RECOGNIZING -> "识别中..."
                    FaceDetectionState.UNKNOWN -> "未知"
                    else -> ""
                }
                
                val textPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.argb(
                        (color.alpha * 255).toInt(),
                        (color.red * 255).toInt(),
                        (color.green * 255).toInt(),
                        (color.blue * 255).toInt()
                    )
                    textSize = 32f
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.DEFAULT,
                        android.graphics.Typeface.BOLD
                    )
                }
                
                drawContext.canvas.nativeCanvas.drawText(
                    statusText,
                    rect.left,
                    rect.top - 12f,
                    textPaint
                )
            }
            
            // 横屏模式：恢复画布状态
            if (isLandscapeMode) {
                drawContext.canvas.nativeCanvas.restore()
            }
        }
    }
}

/**
 * 兼容旧版 CameraFaceDetector.FaceResult 的重载
 */
@Composable
fun FaceDetectionOverlay(
    faces: List<com.sustech.bojayL.ml.CameraFaceDetector.FaceResult>,
    viewWidth: Float,
    viewHeight: Float,
    modifier: Modifier = Modifier
) {
    // 转换为 HudFaceResult
    val hudFaces = faces.map { face ->
        HudFaceResult(
            rect = android.graphics.RectF(
                face.rect.left * viewWidth / face.imageWidth,
                face.rect.top * viewHeight / face.imageHeight,
                face.rect.right * viewWidth / face.imageWidth,
                face.rect.bottom * viewHeight / face.imageHeight
            ),
            confidence = face.confidence,
            state = FaceDetectionState.DETECTING
        )
    }
    
    FaceDetectionOverlay(
        faces = hudFaces,
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        modifier = modifier
    )
}
