package com.sustech.bojayL.ui.screens.students

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.sustech.bojayL.data.model.Student
import com.sustech.bojayL.ml.FaceAlignment
import com.sustech.bojayL.ml.FaceRecognizer
import com.sustech.bojayL.ml.InsightfaceNcnnDetector
import com.sustech.bojayL.ui.theme.*
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * 人脸录入方式
 */
enum class EnrollmentMethod {
    CAMERA,   // 现场拍摄
    GALLERY,  // 相册导入
    REMOTE    // 远程采集
}

/**
 * 人脸录入结果
 */
data class EnrollmentResult(
    val imageUri: Uri,
    val faceFeature: List<Float>?  // 512维特征向量
)

/**
 * 人脸录入页面
 * 
 * 根据 PRD 要求 STU-02：
 * 支持多种方式更新学生人脸底库，以提高识别准确率：
 * - 方式 A (现场拍摄)：调用手机摄像头拍摄学生正脸，具备人脸居中引导框
 * - 方式 B (相册导入)：从手机相册选择照片，支持手势缩放与移动，提供正方形/圆形裁剪框
 * - 方式 C (远程采集)：点击"远程采集"，触发眼镜端抓拍当前画面并上传更新
 * 
 * 修改：现在在录入时提取并保存 512 维人脸特征向量
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FaceEnrollmentScreen(
    student: Student,
    onBackClick: () -> Unit,
    onEnrollmentComplete: (Uri?) -> Unit,
    onEnrollmentCompleteWithFeature: ((EnrollmentResult) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedMethod by remember { mutableStateOf<EnrollmentMethod?>(null) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showCropScreen by remember { mutableStateOf(false) }
    
    // 相机权限
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    // 相册选择器
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            showCropScreen = true
        }
    }
    
    // 如果显示裁剪页面
    if (showCropScreen && selectedImageUri != null) {
        ImageCropScreen(
            imageUri = selectedImageUri!!,
            onCropComplete = { croppedUri ->
                capturedImageUri = croppedUri
                showCropScreen = false
                selectedImageUri = null
            },
            onCancel = {
                showCropScreen = false
                selectedImageUri = null
            }
        )
        return
    }
    
    // 如果已经选择了拍照方式
    if (selectedMethod == EnrollmentMethod.CAMERA && cameraPermissionState.status.isGranted) {
        CameraCaptureScreen(
            student = student,
            onCapture = { uri ->
                capturedImageUri = uri
                selectedMethod = null
            },
            onCancel = {
                selectedMethod = null
            }
        )
        return
    }
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("人脸录入") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 学生信息
            StudentInfoHeader(student = student)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 当前人脸预览
            FacePreviewCard(
                currentImageUri = capturedImageUri,
                studentAvatarUrl = student.avatarUrl
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 录入方式选择
            Text(
                text = "选择录入方式",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 方式 A: 现场拍摄
            EnrollmentMethodCard(
                icon = Icons.Default.CameraAlt,
                title = "现场拍摄",
                description = "使用手机摄像头拍摄学生正脸",
                onClick = {
                    if (cameraPermissionState.status.isGranted) {
                        selectedMethod = EnrollmentMethod.CAMERA
                    } else {
                        cameraPermissionState.launchPermissionRequest()
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 方式 B: 相册导入
            EnrollmentMethodCard(
                icon = Icons.Default.PhotoLibrary,
                title = "相册导入",
                description = "从手机相册选择照片并裁剪",
                onClick = {
                    galleryLauncher.launch("image/*")
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 方式 C: 远程采集
            EnrollmentMethodCard(
                icon = Icons.Default.Visibility,
                title = "远程采集",
                description = "通过眼镜抓拍当前画面",
                onClick = {
                    selectedMethod = EnrollmentMethod.REMOTE
                    // TODO: 发送远程采集指令给眼镜
                }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 确认按钮
            if (capturedImageUri != null) {
                var isProcessing by remember { mutableStateOf(false) }
                
                Button(
                    onClick = {
                        if (!isProcessing) {
                            isProcessing = true
                            coroutineScope.launch {
                                // 提取人脸特征
                                val feature = extractFaceFeature(context, capturedImageUri!!)
                                isProcessing = false
                                
                                if (onEnrollmentCompleteWithFeature != null) {
                                    onEnrollmentCompleteWithFeature(EnrollmentResult(
                                        imageUri = capturedImageUri!!,
                                        faceFeature = feature
                                    ))
                                } else {
                                    onEnrollmentComplete(capturedImageUri)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = DarkBackground
                    ),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = DarkBackground,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "提取特征中...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "确认录入",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // 权限请求提示
            if (!cameraPermissionState.status.isGranted && cameraPermissionState.status.shouldShowRationale) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "需要相机权限才能进行现场拍摄",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentOrange,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 学生信息头部
 */
@Composable
private fun StudentInfoHeader(
    student: Student,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 头像
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DarkSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (student.avatarUrl != null) {
                AsyncImage(
                    model = student.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = TextTertiary
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(
                text = student.name,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${student.className} · ${student.studentId}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

/**
 * 人脸预览卡片
 */
@Composable
private fun FacePreviewCard(
    currentImageUri: Uri?,
    studentAvatarUrl: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .size(200.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (currentImageUri != null) {
                AsyncImage(
                    model = currentImageUri,
                    contentDescription = "已采集的人脸",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                
                // 成功标记
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AccentGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = TextTertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "待采集人脸",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

/**
 * 录入方式卡片
 */
@Composable
private fun EnrollmentMethodCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyanPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary
            )
        }
    }
}

/**
 * 相机拍摄页面
 */
@Composable
fun CameraCaptureScreen(
    student: Student,
    onCapture: (Uri) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    
    Box(modifier = modifier.fillMaxSize()) {
        // 相机预览
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    
                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        Log.e("CameraCapture", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )
        
        // 人脸引导框
        FaceGuideOverlay(
            modifier = Modifier.fillMaxSize()
        )
        
        // 顶部返回按钮
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(DarkBackground.copy(alpha = 0.6f))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = TextPrimary
            )
        }
        
        // 学生信息
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = DarkBackground.copy(alpha = 0.7f)
        ) {
            Text(
                text = "正在为 ${student.name} 采集人脸",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
        
        // 底部提示和拍摄按钮
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "请将人脸置于引导框内",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 拍摄按钮
            IconButton(
                onClick = {
                    if (!isCapturing) {
                        isCapturing = true
                        takePhoto(
                            context = context,
                            imageCapture = imageCapture,
                            onSuccess = { uri ->
                                isCapturing = false
                                onCapture(uri)
                            },
                            onError = {
                                isCapturing = false
                            }
                        )
                    }
                },
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (isCapturing) TextTertiary else CyanPrimary)
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = DarkBackground,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "拍照",
                        tint = DarkBackground,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

/**
 * 人脸引导框覆盖层
 */
@Composable
fun FaceGuideOverlay(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // 椭圆引导框参数
        val ovalWidth = canvasWidth * 0.65f
        val ovalHeight = ovalWidth * 1.3f
        val ovalLeft = (canvasWidth - ovalWidth) / 2
        val ovalTop = (canvasHeight - ovalHeight) / 2 - 50.dp.toPx()
        
        // 绘制半透明遮罩
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            size = size
        )
        
        // 清除椭圆区域（透明）
        drawOval(
            color = Color.Transparent,
            topLeft = Offset(ovalLeft, ovalTop),
            size = Size(ovalWidth, ovalHeight),
            blendMode = BlendMode.Clear
        )
        
        // 绘制椭圆边框
        drawOval(
            color = CyanPrimary,
            topLeft = Offset(ovalLeft, ovalTop),
            size = Size(ovalWidth, ovalHeight),
            style = Stroke(width = 3.dp.toPx())
        )
        
        // 四角标记
        val cornerLength = 30.dp.toPx()
        val strokeWidth = 4.dp.toPx()
        val cornerColor = CyanPrimary
        
        // 左上角
        drawLine(
            color = cornerColor,
            start = Offset(ovalLeft - 10, ovalTop + ovalHeight * 0.1f),
            end = Offset(ovalLeft - 10, ovalTop + ovalHeight * 0.1f + cornerLength),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = cornerColor,
            start = Offset(ovalLeft + ovalWidth * 0.1f, ovalTop - 10),
            end = Offset(ovalLeft + ovalWidth * 0.1f + cornerLength, ovalTop - 10),
            strokeWidth = strokeWidth
        )
        
        // 右上角
        drawLine(
            color = cornerColor,
            start = Offset(ovalLeft + ovalWidth + 10, ovalTop + ovalHeight * 0.1f),
            end = Offset(ovalLeft + ovalWidth + 10, ovalTop + ovalHeight * 0.1f + cornerLength),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = cornerColor,
            start = Offset(ovalLeft + ovalWidth * 0.9f - cornerLength, ovalTop - 10),
            end = Offset(ovalLeft + ovalWidth * 0.9f, ovalTop - 10),
            strokeWidth = strokeWidth
        )
        
        // 左下角
        drawLine(
            color = cornerColor,
            start = Offset(ovalLeft - 10, ovalTop + ovalHeight * 0.9f - cornerLength),
            end = Offset(ovalLeft - 10, ovalTop + ovalHeight * 0.9f),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = cornerColor,
            start = Offset(ovalLeft + ovalWidth * 0.1f, ovalTop + ovalHeight + 10),
            end = Offset(ovalLeft + ovalWidth * 0.1f + cornerLength, ovalTop + ovalHeight + 10),
            strokeWidth = strokeWidth
        )
        
        // 右下角
        drawLine(
            color = cornerColor,
            start = Offset(ovalLeft + ovalWidth + 10, ovalTop + ovalHeight * 0.9f - cornerLength),
            end = Offset(ovalLeft + ovalWidth + 10, ovalTop + ovalHeight * 0.9f),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = cornerColor,
            start = Offset(ovalLeft + ovalWidth * 0.9f - cornerLength, ovalTop + ovalHeight + 10),
            end = Offset(ovalLeft + ovalWidth * 0.9f, ovalTop + ovalHeight + 10),
            strokeWidth = strokeWidth
        )
    }
}

/**
 * 图片裁剪页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropScreen(
    imageUri: Uri,
    onCropComplete: (Uri) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // 变换状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isCircleCrop by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize(0, 0)) }
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("裁剪照片") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "取消"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            isProcessing = true
                            // 执行真实裁剪 + 人脸校验（先用 MLKit，后续替换为 InsightFace-NCNN）
                            scope.launch {
                            try {
                                    val cropped = com.sustech.bojayL.util.ImageCrop.cropImage(
                                        context = context,
                                        imageUri = imageUri,
                                        containerWidth = containerSize.width,
                                        containerHeight = containerSize.height,
                                        scale = scale,
                                        offsetX = offsetX,
                                        offsetY = offsetY,
                                        isCircle = isCircleCrop
                                    )
                                    // 使用复合检测器：SCRFD 优先，失败回退 MLKit
                                    val hasFace = com.sustech.bojayL.ml.CompositeFaceDetector.hasFace(context, cropped)
                                    when (hasFace) {
                                        true -> onCropComplete(cropped)
                                        false -> Toast.makeText(context, "未检测到人脸，请调整裁剪区域后重试", Toast.LENGTH_SHORT).show()
                                        null -> Toast.makeText(context, "人脸检测失败，请重试", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "裁剪失败：${e.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isProcessing = false
                                }
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "完成",
                                color = CyanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 图片裁剪区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { containerSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 3f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // 图片
                AsyncImage(
                    model = imageUri,
                    contentDescription = "待裁剪图片",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentScale = ContentScale.Fit
                )
                
                // 裁剪框覆盖层
                CropOverlay(
                    isCircle = isCircleCrop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // 底部工具栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "双指缩放和移动图片，将人脸置于裁剪框内",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 裁剪形状选择
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FilterChip(
                            selected = isCircleCrop,
                            onClick = { isCircleCrop = true },
                            label = { Text("圆形") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Circle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = CyanPrimary,
                                selectedLeadingIconColor = CyanPrimary
                            )
                        )
                        
                        FilterChip(
                            selected = !isCircleCrop,
                            onClick = { isCircleCrop = false },
                            label = { Text("正方形") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CropSquare,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = CyanPrimary,
                                selectedLeadingIconColor = CyanPrimary
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 重置按钮
                    TextButton(
                        onClick = {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重置")
                    }
                }
            }
        }
    }
}

/**
 * 裁剪覆盖层
 */
@Composable
fun CropOverlay(
    isCircle: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        
        // 裁剪框大小
        val cropSize = minOf(canvasWidth, canvasHeight) * 0.7f
        val cropLeft = (canvasWidth - cropSize) / 2
        val cropTop = (canvasHeight - cropSize) / 2
        
        // 绘制半透明遮罩
        drawRect(
            color = Color.Black.copy(alpha = 0.6f),
            size = size
        )
        
        // 清除裁剪区域
        if (isCircle) {
            drawCircle(
                color = Color.Transparent,
                radius = cropSize / 2,
                center = Offset(canvasWidth / 2, canvasHeight / 2),
                blendMode = BlendMode.Clear
            )
            // 绘制圆形边框
            drawCircle(
                color = CyanPrimary,
                radius = cropSize / 2,
                center = Offset(canvasWidth / 2, canvasHeight / 2),
                style = Stroke(width = 2.dp.toPx())
            )
        } else {
            drawRect(
                color = Color.Transparent,
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropSize, cropSize),
                blendMode = BlendMode.Clear
            )
            // 绘制方形边框
            drawRect(
                color = CyanPrimary,
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropSize, cropSize),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

/**
 * 拍照
 */
private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    onSuccess: (Uri) -> Unit,
    onError: () -> Unit
) {
    val imageCapture = imageCapture ?: return
    
    val photoFile = File(
        context.cacheDir,
        "face_${System.currentTimeMillis()}.jpg"
    )
    
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                onSuccess(savedUri)
            }
            
            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraCapture", "Photo capture failed: ${exception.message}", exception)
                onError()
            }
        }
    )
}

/**
 * 从图片中提取人脸特征
 * 
 * @param context Android Context
 * @param imageUri 图片 URI
 * @return 512维特征向量，失败返回 null
 */
private suspend fun extractFaceFeature(
    context: Context,
    imageUri: Uri
): List<Float>? = withContext(Dispatchers.IO) {
    try {
        // 初始化检测器和识别器
        if (!InsightfaceNcnnDetector.isReady()) {
            InsightfaceNcnnDetector.init(context)
        }
        if (!FaceRecognizer.isReady()) {
            FaceRecognizer.init(context)
        }
        
        // 加载图片
        val bitmap = loadBitmapFromUri(context, imageUri) ?: run {
            Log.e("FaceEnrollment", "Failed to load bitmap")
            return@withContext null
        }
        
        // 检测人脸
        val faces = InsightfaceNcnnDetector.detectFromBitmap(bitmap)
        if (faces.isNullOrEmpty()) {
            Log.w("FaceEnrollment", "No face detected")
            bitmap.recycle()
            return@withContext null
        }
        
        val face = faces.first()
        val landmarks = face.landmarks
        
        if (landmarks == null || !FaceAlignment.isValidLandmarks(landmarks)) {
            Log.w("FaceEnrollment", "Invalid landmarks")
            bitmap.recycle()
            return@withContext null
        }
        
        // 对齐人脸
        val alignedFace = FaceAlignment.alignFace(bitmap, landmarks)
        bitmap.recycle()
        
        if (alignedFace == null) {
            Log.w("FaceEnrollment", "Face alignment failed")
            return@withContext null
        }
        
        // 提取特征
        val feature = FaceRecognizer.extractFeature(alignedFace)
        alignedFace.recycle()
        
        if (feature == null) {
            Log.w("FaceEnrollment", "Feature extraction failed")
            return@withContext null
        }
        
        Log.d("FaceEnrollment", "Feature extracted: ${feature.size} dimensions")
        return@withContext feature.toList()
        
    } catch (e: Exception) {
        Log.e("FaceEnrollment", "Feature extraction error", e)
        return@withContext null
    }
}

/**
 * 从 URI 加载 Bitmap
 */
private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream)?.let { bitmap ->
                if (bitmap.config != Bitmap.Config.ARGB_8888) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false).also {
                        bitmap.recycle()
                    }
                } else {
                    bitmap
                }
            }
        }
    } catch (e: Exception) {
        Log.e("FaceEnrollment", "Failed to load bitmap from URI", e)
        null
    }
}
