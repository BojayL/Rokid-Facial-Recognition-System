package com.sustech.bojayL.ui.screens.classroom

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sustech.bojayL.data.model.Student
import com.sustech.bojayL.ml.CameraFaceDetector
import com.sustech.bojayL.ui.components.*
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * 手机相机识别页面 (HUD 版)
 * 
 * 使用手机摄像头进行实时人脸检测和自动识别。
 * 集成 HUD 风格 UI：准心、断角框、信息气泡、状态栏。
 */
@Composable
fun PhoneCameraRecognitionScreen(
    students: List<Student>,
    onStudentRecognized: (Student) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClassroomViewModel? = null,
    isLandscapeMode: Boolean = false  // 横屏模式（默认关闭）
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    
    var isDetecting by remember { mutableStateOf(true) }
    var hudFaces by remember { mutableStateOf<List<HudFaceResult>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }
    var isFrontCamera by remember { mutableStateOf(false) }
    
    // 横屏模式状态
    var isLandscapeModeEnabled by remember { mutableStateOf(isLandscapeMode) }
    
    // 相机预览视图大小
    var viewWidth by remember { mutableStateOf(0f) }
    var viewHeight by remember { mutableStateOf(0f) }
    
    // 识别统计
    var recognizedCount by remember { mutableIntStateOf(0) }
    val totalStudents = students.size
    
    // 准心对准状态
    var targetedFace by remember { mutableStateOf<HudFaceResult?>(null) }
    var isRecognizing by remember { mutableStateOf(false) }
    
    // 人脸检测器
    val faceDetector = remember {
        CameraFaceDetector(
            context = context,
            onFaceDetected = { _ ->
                // 旧版回调，不再使用
            },
            onError = { error ->
                errorMessage = error
                Log.e("PhoneCameraRecognition", "Detection error: $error")
            }
        ).apply {
            // 设置 HUD 回调
            setHudCallback { faces ->
                hudFaces = faces
                
                // 更新 ViewModel
                viewModel?.updateDetectedFaces(faces)
                
                // 检查已识别的人脸（信息框跟随）
                faces.forEach { face ->
                    if (face.state == FaceDetectionState.RECOGNIZED && face.student != null) {
                        if (viewModel?.isStudentInCooldown(face.student.id) != true) {
                            onStudentRecognized(face.student)
                            recognizedCount++
                        }
                    }
                }
            }
            
            // 设置准心对准回调
            setTargetedFaceCallback { face ->
                targetedFace = face
            }
            
            // 设置学生列表
            setStudents(students)
        }
    }
    
    // 手动触发识别
    fun triggerRecognition() {
        if (isRecognizing) return
        isRecognizing = true
        
        coroutineScope.launch {
            try {
                val student = faceDetector.triggerRecognition()
                if (student != null && viewModel?.isStudentInCooldown(student.id) != true) {
                    onStudentRecognized(student)
                    recognizedCount++
                }
            } catch (e: Exception) {
                Log.e("PhoneCameraRecognition", "Recognition error", e)
                errorMessage = "识别失败: ${e.message}"
            } finally {
                isRecognizing = false
            }
        }
    }
    
    // 学生列表变化时更新
    LaunchedEffect(students) {
        faceDetector.setStudents(students)
    }
    
    // 初始化检测器
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val success = faceDetector.initialize()
            if (success) {
                faceDetector.startDetection()
            }
        }
    }
    
    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            faceDetector.release()
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // 相机预览
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        
                        // 相机预览用例
                        val preview = Preview.Builder()
                            .build()
                            .also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                        
                        // 图像分析用例
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also {
                                it.setAnalyzer(
                                    Executors.newSingleThreadExecutor(),
                                    faceDetector.getAnalyzer()
                                )
                            }
                        
                        // 绑定到生命周期
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        
                        // 获取预览尺寸
                        previewView.post {
                            viewWidth = previewView.width.toFloat()
                            viewHeight = previewView.height.toFloat()
                        }
                        
                    } catch (e: Exception) {
                        Log.e("PhoneCameraRecognition", "Camera initialization failed", e)
                        errorMessage = "相机初始化失败: ${e.message}"
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )
        
        // HUD 叠加层
        if (viewWidth > 0 && viewHeight > 0) {
            // 1. 人脸检测框（断角框样式）
            FaceDetectionOverlay(
                faces = hudFaces,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                modifier = Modifier.fillMaxSize(),
                mirrorX = isFrontCamera,
                isLandscapeMode = isLandscapeModeEnabled
            )
            
            // 2. 信息气泡（跟随已识别的人脸）
            FaceInfoBubblesOverlay(
                faces = hudFaces,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                modifier = Modifier.fillMaxSize(),
                mirrorX = isFrontCamera,
                isLandscapeMode = isLandscapeModeEnabled
            )
            
            // 3. 中心准心（根据对准状态改变颜色）
            ReticleOverlay(
                modifier = Modifier.fillMaxSize(),
                animated = isDetecting,
                isTargeted = targetedFace != null,
                isRecognized = targetedFace?.state == FaceDetectionState.RECOGNIZED,
                isLandscapeMode = isLandscapeModeEnabled
            )
        }
        
        // HUD 状态栏（顶部）
        HudStatusBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            recognizedCount = recognizedCount,
            totalStudents = totalStudents,
            isRecording = isDetecting
        )
        
        // 顶部工具栏
        HudTopBar(
            modifier = Modifier.align(Alignment.TopStart),
            onBackClick = onBackClick,
            onCameraSwitch = {
                isFrontCamera = !isFrontCamera
                cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                faceDetector.resetTracking()
            },
            isLandscapeMode = isLandscapeModeEnabled,
            onLandscapeModeToggle = {
                isLandscapeModeEnabled = !isLandscapeModeEnabled
            }
        )
        
        // 底部控制栏 (HUD 风格)
        HudBottomControlBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            isDetecting = isDetecting,
            onToggleDetection = {
                isDetecting = !isDetecting
                if (isDetecting) {
                    faceDetector.startDetection()
                } else {
                    faceDetector.pauseDetection()
                }
            },
            detectedFaceCount = hudFaces.size,
            recognizedCount = recognizedCount,
            // 准心对准时显示识别按钮
            showRecognizeButton = targetedFace != null && 
                targetedFace?.state != FaceDetectionState.RECOGNIZED &&
                targetedFace?.state != FaceDetectionState.RECOGNIZING,
            isRecognizing = isRecognizing,
            onRecognize = { triggerRecognition() }
        )
        
        // 错误提示
        errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 16.dp, end = 16.dp),
                containerColor = HudRed.copy(alpha = 0.9f),
                action = {
                    TextButton(onClick = { errorMessage = null }) {
                        Text("关闭", color = Color.White)
                    }
                }
            ) {
                Text(error, color = Color.White)
            }
        }
    }
}

/**
 * HUD 风格顶部工具栏
 */
@Composable
private fun HudTopBar(
    onBackClick: () -> Unit,
    onCameraSwitch: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscapeMode: Boolean = false,
    onLandscapeModeToggle: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回按钮
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(
                Icons.Outlined.ArrowBack,
                contentDescription = "返回",
                tint = HudGreen
            )
        }
        
        // 右侧按钮组
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 横屏模式切换按钮
            IconButton(
                onClick = onLandscapeModeToggle,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (isLandscapeMode) HudGreen.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f),
                        CircleShape
                    )
            ) {
                Icon(
                    if (isLandscapeMode) Icons.Outlined.ScreenRotation else Icons.Outlined.StayCurrentPortrait,
                    contentDescription = "横屏模式",
                    tint = if (isLandscapeMode) HudGreen else HudGreen.copy(alpha = 0.7f)
                )
            }
            
            // 切换摄像头按钮
            IconButton(
                onClick = onCameraSwitch,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    Icons.Outlined.Cameraswitch,
                    contentDescription = "切换摄像头",
                    tint = HudGreen
                )
            }
        }
    }
}

/**
 * HUD 风格底部控制栏
 */
@Composable
private fun HudBottomControlBar(
    isDetecting: Boolean,
    onToggleDetection: () -> Unit,
    detectedFaceCount: Int,
    recognizedCount: Int,
    modifier: Modifier = Modifier,
    showRecognizeButton: Boolean = false,
    isRecognizing: Boolean = false,
    onRecognize: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 状态信息行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 检测人数（数人头）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (detectedFaceCount > 0) HudGreen else HudGreen.copy(alpha = 0.5f)
                )
                Text(
                    text = "$detectedFaceCount 人",
                    color = HudGreen,
                    fontSize = 12.sp
                )
            }
            
            // 分隔线
            Text("|", color = HudGreen.copy(alpha = 0.3f), fontSize = 12.sp)
            
            // 已识别数
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = HudCyan
                )
                Text(
                    text = "$recognizedCount 已识别",
                    color = HudCyan,
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 按钮行
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 暂停/继续按钮
            IconButton(
                onClick = onToggleDetection,
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = if (isDetecting) HudGreen.copy(alpha = 0.2f) else HudYellow.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isDetecting) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (isDetecting) "暂停" else "继续",
                    modifier = Modifier.size(28.dp),
                    tint = if (isDetecting) HudGreen else HudYellow
                )
            }
            
            // 识别按钮（仅在准心对准时显示）
            if (showRecognizeButton) {
                Button(
                    onClick = onRecognize,
                    enabled = !isRecognizing,
                    modifier = Modifier
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudGreen,
                        contentColor = Color.Black,
                        disabledContainerColor = HudYellow.copy(alpha = 0.5f),
                        disabledContentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    if (isRecognizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("识别中...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PersonSearch,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("识别", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
