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
        
        // 采集分辨率（眼镜相机旋转90°，宽高互换）
        const val CAPTURE_WIDTH = 720
        const val CAPTURE_HEIGHT = 1280
        
        // JPEG 压缩质量
        const val JPEG_QUALITY = 75
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
            val jpegBytes = imageProxyToJpeg(imageProxy)
            
            if (jpegBytes != null) {
                lastCaptureTime = currentTime
                
                // 回调
                val rotatedWidth = imageProxy.height  // 旋转后宽高互换
                val rotatedHeight = imageProxy.width
                imageCallback?.invoke(jpegBytes, rotatedWidth, rotatedHeight)
                
                Log.d(TAG, "Image captured: ${jpegBytes.size} bytes, ${rotatedWidth}x${rotatedHeight}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process image", e)
        } finally {
            imageProxy.close()
        }
    }
    
    /**
     * ImageProxy 转 JPEG
     */
    private fun imageProxyToJpeg(imageProxy: ImageProxy): ByteArray? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer
            
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            
            val nv21 = ByteArray(ySize + uSize + vSize)
            
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            
            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )
            
            val outputStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, imageProxy.width, imageProxy.height),
                JPEG_QUALITY,
                outputStream
            )
            
            // 旋转图像（眼镜相机默认旋转90°）
            val originalBitmap = BitmapFactory.decodeByteArray(
                outputStream.toByteArray(),
                0,
                outputStream.size()
            )
            
            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            }
            
            val rotatedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0, 0,
                originalBitmap.width, originalBitmap.height,
                matrix,
                true
            )
            
            // 压缩旋转后的图像
            val rotatedOutputStream = ByteArrayOutputStream()
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, rotatedOutputStream)
            
            // 释放 Bitmap
            originalBitmap.recycle()
            rotatedBitmap.recycle()
            
            rotatedOutputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert to JPEG", e)
            null
        }
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
