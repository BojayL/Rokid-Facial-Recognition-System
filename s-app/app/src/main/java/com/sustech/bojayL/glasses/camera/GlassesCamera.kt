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
        
        // 采集分辨率（降低分辨率以适应 SDK 消息大小限制）
        // 原始 1280x720 太大，降低到 640x360
        const val CAPTURE_WIDTH = 360
        const val CAPTURE_HEIGHT = 640
        
        // 网络传输用的输出分辨率（进一步缩小，保持 4:3 横向比例）
        const val OUTPUT_WIDTH = 320
        const val OUTPUT_HEIGHT = 240
        
        // 额外旋转角度（用于修正眼镜相机方向）
        const val EXTRA_ROTATION = 90f
        
        // JPEG 压缩质量（降低以减小数据大小）
        const val JPEG_QUALITY = 35
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
    
    // 图像回调
    private var imageCallback: ((ByteArray, Int, Int) -> Unit)? = null
    
    // 上次采集时间（用于控制采集间隔）
    private var lastCaptureTime = 0L
    private var captureIntervalMs = 2000L  // 默认2秒
    
    // 是否自动采集
    private var autoCapture = true
    
    /**
     * 初始化相机
     */
    fun initialize(lifecycleOwner: LifecycleOwner, onImageCaptured: (ByteArray, Int, Int) -> Unit) {
        Log.d(TAG, "Initializing camera...")
        imageCallback = onImageCaptured
        
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
     * 处理图像
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
            // 转换为 JPEG
            val result = imageProxyToJpeg(imageProxy)
            
            if (result != null) {
                lastCaptureTime = currentTime
                
                // 回调（使用实际输出尺寸）
                imageCallback?.invoke(result.data, result.width, result.height)
                
                Log.d(TAG, "Image captured: ${result.data.size} bytes, ${result.width}x${result.height}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image", e)
        } finally {
            imageProxy.close()
        }
    }
    
    /**
     * 图像转换结果
     */
    private data class ImageResult(
        val data: ByteArray,
        val width: Int,
        val height: Int
    )
    
    /**
     * ImageProxy 转 JPEG
     * 
     * 注意：为了适应 Rokid SDK 的消息大小限制（约 64KB），
     * 需要大幅压缩图像。目标是将 Base64 编码后的数据控制在 50KB 以内。
     */
    private fun imageProxyToJpeg(imageProxy: ImageProxy): ImageResult? {
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
            
            // 第一步：压缩为 JPEG
            val outputStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, imageProxy.width, imageProxy.height),
                60,  // 中等质量
                outputStream
            )
            
            // 第二步：解码为 Bitmap
            val originalBitmap = BitmapFactory.decodeByteArray(
                outputStream.toByteArray(),
                0,
                outputStream.size()
            )
            
            // 第三步：旋转图像
            // CameraX 的 rotationDegrees 是传感器方向，再加上额外旋转以修正眼镜显示方向
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
            
            // 第四步：缩放到输出尺寸，保持宽高比
            // 根据旋转后的图像方向确定目标尺寸
            val targetWidth: Int
            val targetHeight: Int
            if (rotatedBitmap.width > rotatedBitmap.height) {
                // 横向图像
                targetWidth = OUTPUT_WIDTH
                targetHeight = OUTPUT_HEIGHT
            } else {
                // 竖向图像
                targetWidth = OUTPUT_HEIGHT
                targetHeight = OUTPUT_WIDTH
            }
            
            val scaledBitmap = Bitmap.createScaledBitmap(
                rotatedBitmap,
                targetWidth,
                targetHeight,
                true
            )
            
            // 第五步：压缩为低质量 JPEG
            val finalOutputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, finalOutputStream)
            
            // 释放 Bitmap
            originalBitmap.recycle()
            rotatedBitmap.recycle()
            scaledBitmap.recycle()
            
            val resultData = finalOutputStream.toByteArray()
            Log.d(TAG, "Compressed image: ${resultData.size} bytes (${targetWidth}x${targetHeight}, quality=$JPEG_QUALITY)")
            
            ImageResult(resultData, targetWidth, targetHeight)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert to JPEG", e)
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
        Log.d(TAG, "Releasing camera")
        stopCapture()
        
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind camera", e)
        }
        
        cameraExecutor.shutdown()
        _isInitialized.value = false
    }
}
