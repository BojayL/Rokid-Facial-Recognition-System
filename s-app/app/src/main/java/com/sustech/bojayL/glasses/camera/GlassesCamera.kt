package com.sustech.bojayL.glasses.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.sustech.bojayL.glasses.ml.GlassesFaceDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 眼镜端相机管理器
 * 
 * 使用 CameraX 进行图像采集，专为 Rokid 眼镜优化：
 * - 眼镜相机默认旋转90°
 * - 支持自动/手动采集模式
 * - 压缩图像用于网络传输
 */
class GlassesCamera(private val context: Context) {
    
    companion object {
        private const val TAG = "GlassesCamera"
        
        // 采集分辨率 - 使用 4K 3:4 比例以获得最高人脸检测精度
        // 注意：CameraX 会自动选择最接近的支持分辨率
        const val CAPTURE_WIDTH = 2880
        const val CAPTURE_HEIGHT = 3840
        
        // 人脸裁切后的输出尺寸（由 GlassesFaceDetector 定义）
        const val OUTPUT_FACE_SIZE = GlassesFaceDetector.OUTPUT_FACE_SIZE
        
        // 额外旋转角度（用于修正眼镜相机方向）
        const val EXTRA_ROTATION = 90f
        
        // JPEG 压缩初始质量（会动态调整以满足大小限制）
        const val JPEG_QUALITY_INITIAL = 95
        const val JPEG_QUALITY_MIN = 50
        const val JPEG_QUALITY_STEP = 5
        
        // 蓝牙传输大小限制 (40KB)
        const val MAX_IMAGE_SIZE_BYTES = 40 * 1024
        
        // 无人脸时的降级输出分辨率
        const val FALLBACK_WIDTH = 320
        const val FALLBACK_HEIGHT = 240
        const val FALLBACK_JPEG_QUALITY = 35
    }
    
    // 相机状态
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()
    
    // CameraX 组件
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    
    // 执行器
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    // 图像回调 (imageData, width, height, landmarks)
    // landmarks: 5个关键点坐标 [x1,y1,...,x5,y5]，如果没有检测到人脸则为 null
    private var imageCallback: ((ByteArray, Int, Int, FloatArray?) -> Unit)? = null
    
    // 人脸检测器是否已初始化
    private var isFaceDetectorInitialized = false
    
    // 上次采集时间（用于控制采集间隔）
    private var lastCaptureTime = 0L
    private var captureIntervalMs = 2000L  // 默认2秒
    
    // 是否自动采集
    private var autoCapture = true
    
    /**
     * 初始化相机和人脸检测器
     * 
     * @param onImageCaptured 回调函数 (imageData, width, height, landmarks)
     *        landmarks 为 null 表示没有检测到人脸（此时传输的是降级的全图）
     */
    fun initialize(lifecycleOwner: LifecycleOwner, onImageCaptured: (ByteArray, Int, Int, FloatArray?) -> Unit) {
        Log.d(TAG, "Initializing camera and face detector...")
        imageCallback = onImageCaptured
        
        // 初始化人脸检测器
        isFaceDetectorInitialized = GlassesFaceDetector.init(context, useGpu = false)
        Log.d(TAG, "Face detector initialized: $isFaceDetectorInitialized")
        
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                setupCamera(lifecycleOwner)
                _isInitialized.value = true
                Log.d(TAG, "Camera initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    /**
     * 设置相机
     */
    private fun setupCamera(lifecycleOwner: LifecycleOwner) {
        val provider = cameraProvider ?: return
        
        // 图像分析用例
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(CAPTURE_WIDTH, CAPTURE_HEIGHT))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImage(imageProxy)
                }
            }
        
        // 选择后置摄像头
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            // 解绑所有用例
            provider.unbindAll()
            
            // 绑定相机
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
            
            Log.d(TAG, "Camera bound to lifecycle")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera", e)
        }
    }
    
    /**
     * 处理图像：检测人脸并裁切
     */
    private fun processImage(imageProxy: ImageProxy) {
        if (!_isCapturing.value) {
            imageProxy.close()
            return
        }
        
        val currentTime = System.currentTimeMillis()
        
        // 检查采集间隔（自动模式）
        if (autoCapture && currentTime - lastCaptureTime < captureIntervalMs) {
            imageProxy.close()
            return
        }
        
        try {
            // 转换为 Bitmap
            val bitmap = imageProxyToBitmap(imageProxy)
            
            if (bitmap != null) {
                lastCaptureTime = currentTime
                
                // 尝试检测并裁切人脸
                val result = processWithFaceDetection(bitmap)
                
                if (result != null) {
                    imageCallback?.invoke(result.data, result.width, result.height, result.landmarks)
                    Log.d(TAG, "Image captured: ${result.data.size} bytes, ${result.width}x${result.height}, " +
                            "hasLandmarks=${result.landmarks != null}")
                }
                
                // 回收 Bitmap
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image", e)
        } finally {
            imageProxy.close()
        }
    }
    
    /**
     * 带人脸检测的图像处理
     * 
     * 如果检测到人脸，返回裁切后的人脸图像和关键点；
     * 否则返回降级压缩的全图（无关键点）。
     */
    private fun processWithFaceDetection(bitmap: Bitmap): ImageResult? {
        if (isFaceDetectorInitialized) {
            // 尝试检测并裁切人脸
            val croppedFace = GlassesFaceDetector.detectAndCrop(bitmap)
            
            if (croppedFace != null) {
                // 成功检测到人脸，使用动态压缩以保持在40KB限制内
                val compressedData = compressWithSizeLimit(
                    croppedFace.bitmap,
                    MAX_IMAGE_SIZE_BYTES,
                    JPEG_QUALITY_INITIAL,
                    JPEG_QUALITY_MIN,
                    JPEG_QUALITY_STEP
                )
                
                val result = ImageResult(
                    data = compressedData,
                    width = croppedFace.bitmap.width,
                    height = croppedFace.bitmap.height,
                    landmarks = croppedFace.landmarks
                )
                
                // 回收裁切的 Bitmap
                if (!croppedFace.bitmap.isRecycled) {
                    croppedFace.bitmap.recycle()
                }
                
                Log.d(TAG, "Face detected and cropped: ${result.data.size} bytes")
                return result
            }
        }
        
        // 未检测到人脸或检测器未初始化，返回降级压缩的全图
        Log.d(TAG, "No face detected, falling back to full image")
        return fallbackFullImage(bitmap)
    }
    
    /**
     * 动态压缩图像以满足大小限制
     * 
     * 从高质量开始压缩，如果超出限制则逐步降低质量直到满足要求。
     * 
     * @param bitmap 待压缩的 Bitmap
     * @param maxSizeBytes 最大允许字节数
     * @param initialQuality 初始压缩质量 (0-100)
     * @param minQuality 最低压缩质量
     * @param qualityStep 每次降低的质量步长
     * @return 压缩后的字节数组
     */
    private fun compressWithSizeLimit(
        bitmap: Bitmap,
        maxSizeBytes: Int,
        initialQuality: Int,
        minQuality: Int,
        qualityStep: Int
    ): ByteArray {
        var quality = initialQuality
        var outputStream = ByteArrayOutputStream()
        
        while (quality >= minQuality) {
            outputStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            
            val size = outputStream.size()
            if (size <= maxSizeBytes) {
                Log.d(TAG, "Compressed to $size bytes at quality $quality")
                return outputStream.toByteArray()
            }
            
            Log.d(TAG, "Size $size bytes exceeds limit at quality $quality, reducing...")
            quality -= qualityStep
        }
        
        // 最低质量仍超限，返回最后一次压缩结果
        Log.w(TAG, "Could not compress to target size, final size: ${outputStream.size()} bytes")
        return outputStream.toByteArray()
    }
    
    /**
     * 降级方案：压缩全图传输
     */
    private fun fallbackFullImage(bitmap: Bitmap): ImageResult {
        // 缩放到降级尺寸
        val scaledBitmap = Bitmap.createScaledBitmap(
            bitmap,
            FALLBACK_WIDTH,
            FALLBACK_HEIGHT,
            true
        )
        
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, FALLBACK_JPEG_QUALITY, outputStream)
        
        val result = ImageResult(
            data = outputStream.toByteArray(),
            width = FALLBACK_WIDTH,
            height = FALLBACK_HEIGHT,
            landmarks = null  // 无关键点表示是全图
        )
        
        if (scaledBitmap !== bitmap && !scaledBitmap.isRecycled) {
            scaledBitmap.recycle()
        }
        
        return result
    }
    
    /**
     * 图像转换结果
     */
    private data class ImageResult(
        val data: ByteArray,
        val width: Int,
        val height: Int,
        val landmarks: FloatArray?  // 5个关键点坐标，如果是全图则为 null
    )
    
    /**
     * ImageProxy 转 Bitmap
     * 
     * 保持较高分辨率用于人脸检测，检测后再裁切人脸区域。
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            // 正确处理 YUV_420_888 到 NV21 的转换
            val nv21 = yuv420888ToNv21(imageProxy)
            
            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )
            
            // 压缩为 JPEG（使用较高质量以保留人脸细节）
            val outputStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, imageProxy.width, imageProxy.height),
                80,  // 较高质量用于人脸检测
                outputStream
            )
            
            // 解码为 Bitmap
            val originalBitmap = BitmapFactory.decodeByteArray(
                outputStream.toByteArray(),
                0,
                outputStream.size()
            )
            
            // 旋转图像以修正眼镜相机方向
            val totalRotation = imageProxy.imageInfo.rotationDegrees.toFloat() + EXTRA_ROTATION
            val matrix = Matrix().apply {
                postRotate(totalRotation)
            }
            
            val rotatedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0, 0,
                originalBitmap.width, originalBitmap.height,
                matrix,
                true
            )
            
            // 释放原始 Bitmap
            if (originalBitmap !== rotatedBitmap) {
                originalBitmap.recycle()
            }
            
            Log.d(TAG, "Bitmap created: ${rotatedBitmap.width}x${rotatedBitmap.height}")
            rotatedBitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert to Bitmap", e)
            null
        }
    }
    
    /**
     * 正确地将 YUV_420_888 转换为 NV21
     * 
     * YUV_420_888 是 Android Camera2/CameraX 的通用格式，但内存布局因设备而异：
     * - 有些设备 U/V 平面是交错的（pixelStride=2），即已经是 NV12/NV21 格式
     * - 有些设备 U/V 平面是分离的（pixelStride=1），即 I420 格式
     * 
     * NV21 格式: YYYYYYYY VUVU...
     */
    private fun yuv420888ToNv21(imageProxy: ImageProxy): ByteArray {
        val width = imageProxy.width
        val height = imageProxy.height
        
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]
        
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        
        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride
        
        // NV21 格式: Y 数据 + VU 交错数据
        val nv21 = ByteArray(width * height * 3 / 2)
        
        // 复制 Y 平面
        var pos = 0
        if (yRowStride == width) {
            // 如果行步长等于宽度，可以直接复制
            yBuffer.position(0)
            yBuffer.get(nv21, 0, width * height)
            pos = width * height
        } else {
            // 需要逐行复制，跳过 padding
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }
        
        // 复制 UV 数据（NV21 是 VU 交错）
        val uvHeight = height / 2
        val uvWidth = width / 2
        
        if (uvPixelStride == 2) {
            // 已经是交错格式，但需要确认是 NV21（VU）还是 NV12（UV）
            // 在大多数设备上，vBuffer 和 uBuffer 共享内存，偏移 1 字节
            // 检查 V 平面的起始位置是否在 U 平面之前（NV21）或之后（NV12）
            // 通过检查两个 buffer 的起始地址关系
            
            // 直接从 V 平面读取交错数据（因为 NV21 是 VUVUVU...）
            for (row in 0 until uvHeight) {
                val rowStart = row * uvRowStride
                for (col in 0 until uvWidth) {
                    val vIndex = rowStart + col * uvPixelStride
                    val uIndex = rowStart + col * uvPixelStride
                    
                    // NV21: V 在前，U 在后
                    vBuffer.position(vIndex)
                    nv21[pos++] = vBuffer.get()
                    uBuffer.position(uIndex)
                    nv21[pos++] = uBuffer.get()
                }
            }
        } else {
            // 分离的 U/V 平面（I420 格式），需要交错成 NV21
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val uvIndex = row * uvRowStride + col * uvPixelStride
                    
                    // NV21: V 在前，U 在后
                    vBuffer.position(uvIndex)
                    nv21[pos++] = vBuffer.get()
                    uBuffer.position(uvIndex)
                    nv21[pos++] = uBuffer.get()
                }
            }
        }
        
        return nv21
    }
    
    /**
     * 开始采集
     */
    fun startCapture() {
        Log.d(TAG, "Starting capture (auto=$autoCapture, interval=${captureIntervalMs}ms)")
        _isCapturing.value = true
    }
    
    /**
     * 停止采集
     */
    fun stopCapture() {
        Log.d(TAG, "Stopping capture")
        _isCapturing.value = false
    }
    
    /**
     * 手动触发一次采集
     */
    fun captureOnce() {
        if (!_isInitialized.value) {
            Log.w(TAG, "Camera not initialized")
            return
        }
        
        // 临时设置为非自动模式，强制采集
        lastCaptureTime = 0
        Log.d(TAG, "Manual capture triggered")
    }
    
    /**
     * 设置采集间隔
     */
    fun setCaptureInterval(intervalMs: Long) {
        captureIntervalMs = intervalMs
        Log.d(TAG, "Capture interval set to ${intervalMs}ms")
    }
    
    /**
     * 设置自动采集模式
     */
    fun setAutoCapture(auto: Boolean) {
        autoCapture = auto
        Log.d(TAG, "Auto capture set to $auto")
    }
    
    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "Releasing camera and face detector")
        stopCapture()
        
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind camera", e)
        }
        
        // 释放人脸检测器
        GlassesFaceDetector.release()
        isFaceDetectorInitialized = false
        
        cameraExecutor.shutdown()
        _isInitialized.value = false
    }
}
