package com.sustech.bojayL.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.sustech.bojayL.data.model.Student
import com.sustech.bojayL.ui.components.FaceDetectionState
import com.sustech.bojayL.ui.components.HudFaceResult
import kotlinx.coroutines.*
import java.nio.ByteBuffer

/**
 * 相机人脸检测器
 * 
 * 封装 CameraX ImageAnalysis 和 SCRFD 检测器，
 * 实现实时视频流中的人脸检测和识别功能。
 */
class CameraFaceDetector(
    private val context: Context,
    private val onFaceDetected: (List<FaceResult>) -> Unit,
    private val onError: (String) -> Unit
) {
    
    companion object {
        private const val TAG = "CameraFaceDetector"
        
        // 默认检测间隔（毫秒）
        private const val DEFAULT_ANALYZE_INTERVAL_MS = 500L
        
        // 默认检测阈值
        private const val DEFAULT_PROB_THRESHOLD = 0.6f
        private const val DEFAULT_NMS_THRESHOLD = 0.45f
        
        // 识别稳定帧数（连续出现N帧才触发识别）
        private const val STABLE_FRAME_COUNT = 3
        
        // 相似度阈值
        private const val SIMILARITY_THRESHOLD = 0.6f
        
        // 准心对准区域占屏幕比例（以屏幕中心为基准）
        private const val RETICLE_REGION_RATIO = 0.3f
    }
    
    /**
     * 人脸检测结果
     */
    data class FaceResult(
        val rect: RectF,           // 人脸边界框（相对于图像的坐标）
        val confidence: Float,     // 置信度 0.0-1.0
        val imageWidth: Int,       // 原始图像宽度
        val imageHeight: Int,      // 原始图像高度
        val landmarks: FloatArray? = null,  // 5个关键点坐标
        val trackId: Int = -1,     // 跟踪 ID
        val state: FaceDetectionState = FaceDetectionState.DETECTING,
        val student: Student? = null  // 识别到的学生
    )
    
    private var lastAnalyzedTimestamp = 0L
    private var analyzeIntervalMs = DEFAULT_ANALYZE_INTERVAL_MS
    
    private var isInitialized = false
    private var isDetecting = false
    private var recognizerInitialized = false
    
    // 人脸跟踪状态
    private var nextTrackId = 0
    private val trackedFaces = mutableMapOf<Int, TrackedFace>()
    
    // 学生列表（用于匹配）
    private var students: List<Student> = emptyList()
    
    // HUD 结果回调
    private var onHudFaceDetected: ((List<HudFaceResult>) -> Unit)? = null
    
    // 准心对准回调（通知 UI 是否有人脸在准心区域）
    private var onTargetedFaceChanged: ((HudFaceResult?) -> Unit)? = null
    
    // 当前准心对准的人脸 trackId
    private var targetedTrackId: Int = -1
    
    // 当前图像尺寸（用于准心检测）
    private var currentImageWidth: Int = 0
    private var currentImageHeight: Int = 0
    
    // 用于手动触发识别的当前帧 bitmap
    private var currentBitmap: Bitmap? = null
    
    // 当前图像旋转角度
    private var currentRotation: Int = 0
    
    private val detectionScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * 跟踪的人脸信息
     */
    data class TrackedFace(
        val trackId: Int,
        var rect: RectF,
        var frameCount: Int = 0,
        var state: FaceDetectionState = FaceDetectionState.DETECTING,
        var student: Student? = null,
        var lastSeenTimestamp: Long = System.currentTimeMillis(),
        var landmarks: FloatArray? = null
    )
    
    /**
     * 初始化检测器
     */
    suspend fun initialize(): Boolean {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return true
        }
        
        return try {
            // 初始化 SCRFD 检测器
            val detectorSuccess = InsightfaceNcnnDetector.init(context, useGpu = true)
            isInitialized = detectorSuccess
            
            if (detectorSuccess) {
                Log.d(TAG, "SCRFD detector initialized")
                
                // 初始化 MobileFaceNet 识别器
                val recognizerSuccess = FaceRecognizer.init(context, useGpu = true)
                recognizerInitialized = recognizerSuccess
                
                if (recognizerSuccess) {
                    Log.d(TAG, "MobileFaceNet recognizer initialized")
                } else {
                    Log.w(TAG, "MobileFaceNet init failed, recognition disabled")
                }
            } else {
                onError("人脸检测器初始化失败")
            }
            detectorSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Initialization error", e)
            onError("初始化异常: ${e.message}")
            false
        }
    }
    
    /**
     * 设置学生列表（用于识别匹配）
     */
    fun setStudents(studentList: List<Student>) {
        students = studentList
        Log.d(TAG, "Students updated: ${students.size} total, ${students.count { it.isEnrolled }} enrolled")
    }
    
    /**
     * 设置 HUD 结果回调
     */
    fun setHudCallback(callback: (List<HudFaceResult>) -> Unit) {
        onHudFaceDetected = callback
    }
    
    /**
     * 设置准心对准回调
     */
    fun setTargetedFaceCallback(callback: (HudFaceResult?) -> Unit) {
        onTargetedFaceChanged = callback
    }
    
    /**
     * 设置检测间隔
     * 
     * @param intervalMs 间隔毫秒数，值越大性能越好但响应越慢
     */
    fun setAnalyzeInterval(intervalMs: Long) {
        analyzeIntervalMs = intervalMs
    }
    
    /**
     * 开始检测
     */
    fun startDetection() {
        isDetecting = true
        Log.d(TAG, "Detection started")
    }
    
    /**
     * 暂停检测
     */
    fun pauseDetection() {
        isDetecting = false
        Log.d(TAG, "Detection paused")
    }
    
    /**
     * 获取 ImageAnalysis.Analyzer
     * 用于 CameraX ImageAnalysis 用例
     */
    @OptIn(ExperimentalGetImage::class)
    fun getAnalyzer(): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy ->
            analyzeImage(imageProxy)
        }
    }
    
    /**
     * 分析图像帧
     */
    @OptIn(ExperimentalGetImage::class)
    private fun analyzeImage(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        
        // 频率控制：只在间隔时间后才执行检测
        if (currentTimestamp - lastAnalyzedTimestamp < analyzeIntervalMs) {
            imageProxy.close()
            return
        }
        
        // 检查是否正在检测
        if (!isDetecting || !isInitialized) {
            imageProxy.close()
            return
        }
        
        lastAnalyzedTimestamp = currentTimestamp
        
        // 保存旋转角度
        currentRotation = imageProxy.imageInfo.rotationDegrees
        
        // 在协程中执行检测（避免阻塞相机线程）
        detectionScope.launch {
            try {
                val bitmap = imageProxyToBitmap(imageProxy)
                if (bitmap != null) {
                    // 保存当前帧用于手动识别
                    currentBitmap?.recycle()
                    currentBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                    currentImageWidth = imageProxy.width
                    currentImageHeight = imageProxy.height
                    
                    detectFaces(bitmap, imageProxy.width, imageProxy.height, currentRotation)
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Analysis error", e)
                onError("检测异常: ${e.message}")
            } finally {
                imageProxy.close()
            }
        }
    }
    
    /**
     * 执行人脸检测（不自动识别，只检测和判断准心对准）
     * 
     * @param rotation 图像旋转角度（0, 90, 180, 270）
     */
    private suspend fun detectFaces(bitmap: Bitmap, imageWidth: Int, imageHeight: Int, rotation: Int = 0) {
        try {
            // 使用 InsightfaceNcnnDetector.detectFromBitmap
            val detectedFaces = InsightfaceNcnnDetector.detectFromBitmap(
                bitmap,
                DEFAULT_PROB_THRESHOLD,
                DEFAULT_NMS_THRESHOLD
            )
            
            if (detectedFaces == null || detectedFaces.isEmpty()) {
                clearTrackedFaces()
                targetedTrackId = -1
                onFaceDetected(emptyList())
                onHudFaceDetected?.invoke(emptyList())
                onTargetedFaceChanged?.invoke(null)
                return
            }
            
            val currentTime = System.currentTimeMillis()
            
            // 根据旋转角度计算显示尺寸（旋转后的宽高）
            val (displayWidth, displayHeight) = if (rotation == 90 || rotation == 270) {
                imageHeight.toFloat() to imageWidth.toFloat()
            } else {
                imageWidth.toFloat() to imageHeight.toFloat()
            }
            
            // 计算准心区域（屏幕中心）
            val reticleRegion = calculateReticleRegion(displayWidth, displayHeight)
            
            // 跟踪人脸
            val faces = mutableListOf<FaceResult>()
            val hudFaces = mutableListOf<HudFaceResult>()
            var newTargetedFace: HudFaceResult? = null
            var newTargetedTrackId = -1
            
            for (detectedFace in detectedFaces) {
                // 匹配或创建跟踪 ID
                val trackId = matchOrCreateTrack(detectedFace.rect, currentTime)
                val tracked = trackedFaces[trackId]!!
                tracked.rect = detectedFace.rect
                tracked.lastSeenTimestamp = currentTime
                tracked.frameCount++
                tracked.landmarks = detectedFace.landmarks
                
                // 将人脸坐标转换为显示坐标（考虑旋转）
                val rotatedRect = rotateFaceRect(
                    detectedFace.rect, 
                    imageWidth.toFloat(), 
                    imageHeight.toFloat(), 
                    rotation
                )
                
                // 检查人脸是否在准心区域内
                val isInReticle = isFaceInReticle(rotatedRect, reticleRegion)
                
                // 确定状态（不自动识别）
                val displayState = when {
                    // 已识别的保持状态
                    tracked.state == FaceDetectionState.RECOGNIZED -> FaceDetectionState.RECOGNIZED
                    tracked.state == FaceDetectionState.RECOGNIZING -> FaceDetectionState.RECOGNIZING
                    tracked.state == FaceDetectionState.UNKNOWN -> FaceDetectionState.UNKNOWN
                    // 在准心区域内且连续出现足够帧数
                    isInReticle && tracked.frameCount >= STABLE_FRAME_COUNT -> FaceDetectionState.TARGETED
                    // 其他情况都是普通检测状态
                    else -> FaceDetectionState.DETECTING
                }
                
                // 更新 tracked 状态（仅当未识别时）
                if (tracked.state != FaceDetectionState.RECOGNIZED &&
                    tracked.state != FaceDetectionState.RECOGNIZING &&
                    tracked.state != FaceDetectionState.UNKNOWN) {
                    tracked.state = displayState
                }
                
                // 创建 FaceResult（使用旋转后的坐标）
                faces.add(FaceResult(
                    rect = rotatedRect,
                    confidence = detectedFace.confidence,
                    imageWidth = displayWidth.toInt(),
                    imageHeight = displayHeight.toInt(),
                    landmarks = detectedFace.landmarks,
                    trackId = trackId,
                    state = tracked.state,
                    student = tracked.student
                ))
                
                // 创建 HudFaceResult（使用旋转后的坐标）
                val hudFace = HudFaceResult(
                    rect = android.graphics.RectF(
                        rotatedRect.left,
                        rotatedRect.top,
                        rotatedRect.right,
                        rotatedRect.bottom
                    ),
                    confidence = detectedFace.confidence,
                    state = tracked.state,
                    student = tracked.student,
                    trackId = trackId,
                    landmarks = detectedFace.landmarks,
                    isTargeted = displayState == FaceDetectionState.TARGETED,
                    imageWidth = displayWidth.toInt(),
                    imageHeight = displayHeight.toInt()
                )
                hudFaces.add(hudFace)
                
                // 记录准心对准的人脸（优先保持已有的跟踪）
                if (displayState == FaceDetectionState.TARGETED) {
                    if (newTargetedTrackId == -1 || trackId == targetedTrackId) {
                        newTargetedFace = hudFace
                        newTargetedTrackId = trackId
                    }
                }
            }
            
            // 更新准心对准状态
            targetedTrackId = newTargetedTrackId
            
            // 清理超时的跟踪
            cleanupOldTracks(currentTime)
            
            Log.d(TAG, "Detected ${faces.size} faces, targeted: ${targetedTrackId != -1}")
            onFaceDetected(faces)
            onHudFaceDetected?.invoke(hudFaces)
            onTargetedFaceChanged?.invoke(newTargetedFace)
            
        } catch (e: Exception) {
            Log.e(TAG, "Detection error", e)
            onError("检测失败: ${e.message}")
        }
    }
    
    /**
     * 计算准心区域（屏幕中心的矩形区域）
     */
    private fun calculateReticleRegion(width: Float, height: Float): RectF {
        val centerX = width / 2
        val centerY = height / 2
        val regionWidth = width * RETICLE_REGION_RATIO
        val regionHeight = height * RETICLE_REGION_RATIO
        
        return RectF(
            centerX - regionWidth / 2,
            centerY - regionHeight / 2,
            centerX + regionWidth / 2,
            centerY + regionHeight / 2
        )
    }
    
    /**
     * 检查人脸中心是否在准心区域内
     */
    private fun isFaceInReticle(faceRect: RectF, reticleRegion: RectF): Boolean {
        val faceCenterX = (faceRect.left + faceRect.right) / 2
        val faceCenterY = (faceRect.top + faceRect.bottom) / 2
        return reticleRegion.contains(faceCenterX, faceCenterY)
    }
    
    /**
     * 将人脸坐标根据图像旋转角度转换为显示坐标
     * 
     * CameraX 输出的图像可能需要旋转才能正确显示，
     * 这个函数将检测到的人脸坐标转换为旋转后的坐标。
     * 
     * @param rect 原始人脸坐标
     * @param imageWidth 原始图像宽度
     * @param imageHeight 原始图像高度
     * @param rotation 旋转角度（0, 90, 180, 270）
     * @return 旋转后的人脸坐标
     */
    private fun rotateFaceRect(
        rect: RectF,
        imageWidth: Float,
        imageHeight: Float,
        rotation: Int
    ): RectF {
        return when (rotation) {
            90 -> {
                // 顺时针旋转90度：(x, y) -> (height - y, x)
                // 原始图像 W x H，旋转后 H x W
                // 左上角 (left, top) -> (H - top, left)
                // 右下角 (right, bottom) -> (H - bottom, right)
                RectF(
                    imageHeight - rect.bottom,  // new left
                    rect.left,                   // new top
                    imageHeight - rect.top,      // new right
                    rect.right                   // new bottom
                )
            }
            180 -> {
                // 旋转180度：(x, y) -> (width - x, height - y)
                RectF(
                    imageWidth - rect.right,
                    imageHeight - rect.bottom,
                    imageWidth - rect.left,
                    imageHeight - rect.top
                )
            }
            270 -> {
                // 逆时针旋转90度（顺时针270度）：(x, y) -> (y, width - x)
                // 原始图像 W x H，旋转后 H x W
                // 左上角 (left, top) -> (top, W - left)
                // 右下角 (right, bottom) -> (bottom, W - right)
                RectF(
                    rect.top,                    // new left
                    imageWidth - rect.right,     // new top
                    rect.bottom,                 // new right
                    imageWidth - rect.left       // new bottom
                )
            }
            else -> {
                // 不旋转
                RectF(rect.left, rect.top, rect.right, rect.bottom)
            }
        }
    }
    
    /**
     * 手动触发识别（点击识别按钮时调用）
     * 
     * @return 识别结果（学生），失败返回 null
     */
    suspend fun triggerRecognition(): Student? {
        if (!recognizerInitialized || targetedTrackId == -1) {
            Log.w(TAG, "Cannot trigger recognition: recognizer=${recognizerInitialized}, targeted=${targetedTrackId}")
            return null
        }
        
        val tracked = trackedFaces[targetedTrackId]
        if (tracked == null) {
            Log.w(TAG, "Targeted face not found")
            return null
        }
        
        // 已经识别过的直接返回
        if (tracked.state == FaceDetectionState.RECOGNIZED && tracked.student != null) {
            return tracked.student
        }
        
        val bitmap = currentBitmap
        val landmarks = tracked.landmarks
        
        if (bitmap == null || landmarks == null || !FaceAlignment.isValidLandmarks(landmarks)) {
            Log.w(TAG, "No valid bitmap or landmarks for recognition")
            return null
        }
        
        // 设置为识别中状态
        tracked.state = FaceDetectionState.RECOGNIZING
        
        // 执行识别
        val matchResult = recognizeFace(bitmap, landmarks)
        
        return if (matchResult != null) {
            tracked.student = matchResult.student
            tracked.state = FaceDetectionState.RECOGNIZED
            Log.d(TAG, "Manual recognition success: ${matchResult.student.name}")
            matchResult.student
        } else {
            tracked.state = FaceDetectionState.UNKNOWN
            Log.d(TAG, "Manual recognition failed")
            null
        }
    }
    
    /**
     * 获取当前准心对准的人脸
     */
    fun getTargetedFace(): TrackedFace? {
        return if (targetedTrackId != -1) trackedFaces[targetedTrackId] else null
    }
    
    /**
     * 检查是否有准心对准的人脸
     */
    fun hasTargetedFace(): Boolean = targetedTrackId != -1
    
    /**
     * 识别人脸
     */
    private suspend fun recognizeFace(
        bitmap: Bitmap,
        landmarks: FloatArray
    ): FaceRecognizer.MatchResult? {
        return try {
            // 对齐人脸
            val alignedFace = FaceAlignment.alignFace(bitmap, landmarks)
            if (alignedFace == null) {
                Log.w(TAG, "Face alignment failed")
                return null
            }
            
            // 提取特征
            val feature = FaceRecognizer.extractFeature(alignedFace)
            alignedFace.recycle()
            
            if (feature == null) {
                Log.w(TAG, "Feature extraction failed")
                return null
            }
            
            // 匹配学生
            FaceRecognizer.matchStudent(feature, students, SIMILARITY_THRESHOLD)
        } catch (e: Exception) {
            Log.e(TAG, "Recognition error", e)
            null
        }
    }
    
    /**
     * 匹配或创建跟踪 ID（基于 IoU）
     */
    private fun matchOrCreateTrack(rect: RectF, currentTime: Long): Int {
        var bestMatch: TrackedFace? = null
        var bestIoU = 0f
        
        for (tracked in trackedFaces.values) {
            val iou = calculateIoU(rect, tracked.rect)
            if (iou > 0.3f && iou > bestIoU) {
                bestIoU = iou
                bestMatch = tracked
            }
        }
        
        return if (bestMatch != null) {
            bestMatch.trackId
        } else {
            val newId = nextTrackId++
            trackedFaces[newId] = TrackedFace(
                trackId = newId,
                rect = rect,
                lastSeenTimestamp = currentTime
            )
            newId
        }
    }
    
    /**
     * 计算 IoU (Intersection over Union)
     */
    private fun calculateIoU(rect1: RectF, rect2: RectF): Float {
        val intersectLeft = maxOf(rect1.left, rect2.left)
        val intersectTop = maxOf(rect1.top, rect2.top)
        val intersectRight = minOf(rect1.right, rect2.right)
        val intersectBottom = minOf(rect1.bottom, rect2.bottom)
        
        if (intersectLeft >= intersectRight || intersectTop >= intersectBottom) {
            return 0f
        }
        
        val intersectArea = (intersectRight - intersectLeft) * (intersectBottom - intersectTop)
        val area1 = (rect1.right - rect1.left) * (rect1.bottom - rect1.top)
        val area2 = (rect2.right - rect2.left) * (rect2.bottom - rect2.top)
        val unionArea = area1 + area2 - intersectArea
        
        return if (unionArea > 0) intersectArea / unionArea else 0f
    }
    
    /**
     * 清理超时的跟踪
     */
    private fun cleanupOldTracks(currentTime: Long) {
        val timeout = 2000L  // 2秒超时
        trackedFaces.entries.removeIf { (_, tracked) ->
            currentTime - tracked.lastSeenTimestamp > timeout
        }
    }
    
    /**
     * 清空所有跟踪
     */
    private fun clearTrackedFaces() {
        trackedFaces.clear()
    }
    
    /**
     * 将 ImageProxy 转换为 Bitmap
     */
    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        
        return try {
            // 获取 YUV 数据
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            
            val nv21 = ByteArray(ySize + uSize + vSize)
            
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            
            // 将 YUV 转换为 Bitmap
            val yuvImage = android.graphics.YuvImage(
                nv21,
                android.graphics.ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )
            
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
                100,
                out
            )
            val imageBytes = out.toByteArray()
            
            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)?.let { bitmap ->
                // 确保是 ARGB_8888 格式
                if (bitmap.config != Bitmap.Config.ARGB_8888) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false).also {
                        bitmap.recycle()
                    }
                } else {
                    bitmap
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap conversion error", e)
            null
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        isDetecting = false
        detectionScope.cancel()
        trackedFaces.clear()
        currentBitmap?.recycle()
        currentBitmap = null
        targetedTrackId = -1
        Log.d(TAG, "Camera face detector released")
    }
    
    /**
     * 重置跟踪状态（当切换摄像头等场景时调用）
     */
    fun resetTracking() {
        trackedFaces.clear()
        nextTrackId = 0
        targetedTrackId = -1
        currentBitmap?.recycle()
        currentBitmap = null
        Log.d(TAG, "Tracking reset")
    }
}
