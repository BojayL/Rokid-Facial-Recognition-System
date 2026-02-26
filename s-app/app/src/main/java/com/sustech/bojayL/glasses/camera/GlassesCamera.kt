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
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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
 * 眼镜相机管理器（仅负责采帧，不做识别）。
 */
class GlassesCamera(private val context: Context) {

    companion object {
        private const val TAG = "GlassesCamera"

        // 端侧识别使用中高分辨率，兼顾性能
        const val CAPTURE_WIDTH = 1920
        const val CAPTURE_HEIGHT = 2560

        // 修正眼镜相机方向
        const val EXTRA_ROTATION = 90f
    }

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var camera: Camera? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var frameCallback: ((Bitmap) -> Unit)? = null
    private var lastCaptureTime = 0L
    private var captureIntervalMs = 2000L
    private var autoCapture = true
    private var pendingManualCapture = false

    fun initialize(lifecycleOwner: LifecycleOwner, onFrameCaptured: (Bitmap) -> Unit) {
        Log.d(TAG, "Initializing camera...")
        frameCallback = onFrameCaptured

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                cameraProvider = future.get()
                setupCamera(lifecycleOwner)
                _isInitialized.value = true
                Log.d(TAG, "Camera initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun setupCamera(lifecycleOwner: LifecycleOwner) {
        val provider = cameraProvider ?: return

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(CAPTURE_WIDTH, CAPTURE_HEIGHT))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(cameraExecutor) { image ->
                    processImage(image)
                }
            }

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                imageAnalysis
            )
            Log.d(TAG, "Camera bound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera", e)
        }
    }

    private fun processImage(imageProxy: ImageProxy) {
        if (!_isCapturing.value) {
            imageProxy.close()
            return
        }

        val now = System.currentTimeMillis()
        if (autoCapture) {
            if (captureIntervalMs > 0 && now - lastCaptureTime < captureIntervalMs) {
                imageProxy.close()
                return
            }
            lastCaptureTime = now
        } else if (!pendingManualCapture) {
            imageProxy.close()
            return
        } else {
            pendingManualCapture = false
        }

        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                frameCallback?.invoke(bitmap)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process frame", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val nv21 = yuv420888ToNv21(imageProxy)
            val yuvImage = YuvImage(
                nv21,
                ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )
            val output = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, imageProxy.width, imageProxy.height),
                88,
                output
            )
            val original = BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size())
                ?: return null

            val totalRotation = imageProxy.imageInfo.rotationDegrees.toFloat() + EXTRA_ROTATION
            val matrix = Matrix().apply { postRotate(totalRotation) }

            val rotated = Bitmap.createBitmap(
                original,
                0,
                0,
                original.width,
                original.height,
                matrix,
                true
            )
            if (original !== rotated) {
                original.recycle()
            }
            rotated
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert image", e)
            null
        }
    }

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

        val nv21 = ByteArray(width * height * 3 / 2)
        var pos = 0

        if (yRowStride == width) {
            yBuffer.position(0)
            yBuffer.get(nv21, 0, width * height)
            pos = width * height
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        }

        val uvHeight = height / 2
        val uvWidth = width / 2

        if (uvPixelStride == 2) {
            for (row in 0 until uvHeight) {
                val rowStart = row * uvRowStride
                for (col in 0 until uvWidth) {
                    val index = rowStart + col * uvPixelStride
                    vBuffer.position(index)
                    nv21[pos++] = vBuffer.get()
                    uBuffer.position(index)
                    nv21[pos++] = uBuffer.get()
                }
            }
        } else {
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val index = row * uvRowStride + col * uvPixelStride
                    vBuffer.position(index)
                    nv21[pos++] = vBuffer.get()
                    uBuffer.position(index)
                    nv21[pos++] = uBuffer.get()
                }
            }
        }
        return nv21
    }

    fun startCapture() {
        Log.d(TAG, "Start capture, auto=$autoCapture interval=$captureIntervalMs")
        _isCapturing.value = true
    }

    fun stopCapture() {
        Log.d(TAG, "Stop capture")
        _isCapturing.value = false
        pendingManualCapture = false
    }

    fun captureOnce() {
        if (!_isInitialized.value) {
            Log.w(TAG, "Camera not initialized")
            return
        }
        pendingManualCapture = true
    }

    fun setCaptureInterval(intervalMs: Long) {
        captureIntervalMs = intervalMs
    }

    fun setAutoCapture(auto: Boolean) {
        autoCapture = auto
        if (auto) {
            pendingManualCapture = false
        }
    }

    fun release() {
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
