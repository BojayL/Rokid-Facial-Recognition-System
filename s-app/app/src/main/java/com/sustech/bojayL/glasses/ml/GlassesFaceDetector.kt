package com.sustech.bojayL.glasses.ml

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * 眼镜端人脸检测器 (基于 SCRFD + NCNN)
 * 
 * 用于在眼镜端进行人脸检测和裁切，
 * 仅传输裁切后的人脸区域到手机端进行识别。
 */
object GlassesFaceDetector {
    
    private const val TAG = "GlassesFaceDetector"
    
    /**
     * 模型类型：2.5g_kps 平衡速度和精度，包含关键点检测
     */
    private const val MODEL_TYPE = "2.5g_kps"
    
    /**
     * 检测阈值
     */
    private const val DEFAULT_PROB_THRESHOLD = 0.5f
    private const val DEFAULT_NMS_THRESHOLD = 0.45f
    
    /**
     * 裁切边距比例（扩展人脸框的比例）
     */
    private const val CROP_MARGIN_RATIO = 0.3f
    
    /**
     * 输出人脸尺寸
     */
    const val OUTPUT_FACE_SIZE = 160
    
    private var isNativeLoaded = false
    private var isInitialized = false
    
    /**
     * 人脸检测结果
     */
    data class FaceResult(
        val rect: RectF,               // 人脸边界框（原图坐标）
        val confidence: Float,         // 置信度 (0.0-1.0)
        val landmarks: FloatArray      // 5个关键点坐标 [x1,y1,x2,y2,...,x5,y5]（原图坐标）
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FaceResult) return false
            return rect == other.rect && confidence == other.confidence &&
                    landmarks.contentEquals(other.landmarks)
        }
        
        override fun hashCode(): Int {
            var result = rect.hashCode()
            result = 31 * result + confidence.hashCode()
            result = 31 * result + landmarks.contentHashCode()
            return result
        }
    }
    
    /**
     * 裁切后的人脸数据
     */
    data class CroppedFace(
        val bitmap: Bitmap,            // 裁切后的人脸图像 (OUTPUT_FACE_SIZE x OUTPUT_FACE_SIZE)
        val landmarks: FloatArray,     // 转换后的关键点坐标（相对于裁切图像）
        val confidence: Float          // 检测置信度
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CroppedFace) return false
            return bitmap == other.bitmap && confidence == other.confidence &&
                    landmarks.contentEquals(other.landmarks)
        }
        
        override fun hashCode(): Int {
            var result = bitmap.hashCode()
            result = 31 * result + confidence.hashCode()
            result = 31 * result + landmarks.contentHashCode()
            return result
        }
    }
    
    /**
     * 初始化检测器
     * 
     * @param context Android Context
     * @param useGpu 是否使用 GPU 加速
     * @return true 如果初始化成功
     */
    fun init(context: Context, useGpu: Boolean = false): Boolean {
        if (isInitialized && nativeIsInitialized()) {
            Log.d(TAG, "Already initialized")
            return true
        }
        
        // 加载 native 库
        if (!loadNativeLibrary()) {
            Log.e(TAG, "Failed to load native library")
            return false
        }
        
        return try {
            val assetManager = context.assets
            val success = nativeInit(assetManager, MODEL_TYPE, useGpu)
            isInitialized = success
            if (success) {
                Log.d(TAG, "Initialized with model: $MODEL_TYPE, GPU: $useGpu")
            } else {
                Log.e(TAG, "Native init failed")
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Init exception", e)
            false
        }
    }
    
    /**
     * 检测 Bitmap 中的人脸
     * 
     * @param bitmap 输入图片
     * @param probThreshold 检测置信度阈值
     * @param nmsThreshold NMS 阈值
     * @return 检测到的人脸列表
     */
    fun detect(
        bitmap: Bitmap,
        probThreshold: Float = DEFAULT_PROB_THRESHOLD,
        nmsThreshold: Float = DEFAULT_NMS_THRESHOLD
    ): List<FaceResult> {
        if (!isInitialized || !nativeIsInitialized()) {
            Log.e(TAG, "Detector not initialized")
            return emptyList()
        }
        
        return try {
            val result = nativeDetect(bitmap, probThreshold, nmsThreshold)
            
            if (result == null || result.isEmpty()) {
                Log.d(TAG, "No faces detected")
                return emptyList()
            }
            
            // 解析结果
            val numFaces = result[0].toInt()
            if (numFaces == 0) {
                return emptyList()
            }
            
            val valuesPerFace = 15  // 5 (bbox+prob) + 10 (5 landmarks * 2)
            val faces = mutableListOf<FaceResult>()
            
            for (i in 0 until numFaces) {
                val offset = 1 + i * valuesPerFace
                if (offset + 14 < result.size) {
                    val landmarks = FloatArray(10) { idx ->
                        result[offset + 5 + idx]
                    }
                    
                    faces.add(FaceResult(
                        rect = RectF(
                            result[offset],      // x
                            result[offset + 1],  // y
                            result[offset] + result[offset + 2],  // x + width
                            result[offset + 1] + result[offset + 3]  // y + height
                        ),
                        confidence = result[offset + 4],
                        landmarks = landmarks
                    ))
                }
            }
            
            Log.d(TAG, "Detected ${faces.size} faces")
            faces
            
        } catch (e: Exception) {
            Log.e(TAG, "Detection exception", e)
            emptyList()
        }
    }
    
    /**
     * 检测并裁切人脸
     * 
     * 执行人脸检测后，裁切最大置信度的人脸区域，
     * 并将关键点坐标转换为相对于裁切区域的坐标。
     * 
     * @param bitmap 输入图片
     * @return 裁切后的人脸数据，如果没有检测到人脸返回 null
     */
    fun detectAndCrop(bitmap: Bitmap): CroppedFace? {
        val faces = detect(bitmap)
        if (faces.isEmpty()) {
            return null
        }
        
        // 取置信度最高的人脸
        val bestFace = faces.maxByOrNull { it.confidence } ?: return null
        
        return cropFace(bitmap, bestFace)
    }
    
    /**
     * 裁切人脸区域
     * 
     * @param bitmap 原图
     * @param face 人脸检测结果
     * @return 裁切后的人脸数据
     */
    fun cropFace(bitmap: Bitmap, face: FaceResult): CroppedFace? {
        try {
            val imgWidth = bitmap.width
            val imgHeight = bitmap.height
            
            // 计算扩展后的裁切区域
            val faceWidth = face.rect.width()
            val faceHeight = face.rect.height()
            val margin = max(faceWidth, faceHeight) * CROP_MARGIN_RATIO
            
            // 使用正方形裁切区域（以人脸中心为中心）
            val centerX = face.rect.centerX()
            val centerY = face.rect.centerY()
            val halfSize = max(faceWidth, faceHeight) / 2 + margin
            
            // 裁切边界（限制在图像范围内）
            val cropLeft = max(0f, centerX - halfSize)
            val cropTop = max(0f, centerY - halfSize)
            val cropRight = min(imgWidth.toFloat(), centerX + halfSize)
            val cropBottom = min(imgHeight.toFloat(), centerY + halfSize)
            
            val cropWidth = cropRight - cropLeft
            val cropHeight = cropBottom - cropTop
            
            if (cropWidth <= 0 || cropHeight <= 0) {
                Log.w(TAG, "Invalid crop region")
                return null
            }
            
            // 裁切并缩放到输出尺寸
            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                cropLeft.toInt(),
                cropTop.toInt(),
                cropWidth.toInt(),
                cropHeight.toInt()
            )
            
            val scaledBitmap = Bitmap.createScaledBitmap(
                croppedBitmap,
                OUTPUT_FACE_SIZE,
                OUTPUT_FACE_SIZE,
                true
            )
            
            // 如果不是同一个对象，回收中间结果
            if (croppedBitmap !== scaledBitmap) {
                croppedBitmap.recycle()
            }
            
            // 转换关键点坐标：从原图坐标转换为裁切图像坐标
            val scaleX = OUTPUT_FACE_SIZE / cropWidth
            val scaleY = OUTPUT_FACE_SIZE / cropHeight
            
            val transformedLandmarks = FloatArray(10)
            for (i in 0 until 5) {
                val origX = face.landmarks[i * 2]
                val origY = face.landmarks[i * 2 + 1]
                
                // 转换坐标
                transformedLandmarks[i * 2] = (origX - cropLeft) * scaleX
                transformedLandmarks[i * 2 + 1] = (origY - cropTop) * scaleY
            }
            
            Log.d(TAG, "Face cropped: ${OUTPUT_FACE_SIZE}x${OUTPUT_FACE_SIZE}, " +
                    "crop region: (${cropLeft.toInt()},${cropTop.toInt()})-(${cropRight.toInt()},${cropBottom.toInt()})")
            
            return CroppedFace(
                bitmap = scaledBitmap,
                landmarks = transformedLandmarks,
                confidence = face.confidence
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop face", e)
            return null
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        if (isInitialized) {
            try {
                nativeRelease()
            } catch (e: Exception) {
                Log.e(TAG, "Release exception", e)
            }
            isInitialized = false
        }
    }
    
    /**
     * 检查是否已初始化
     */
    fun isReady(): Boolean {
        return isInitialized && isNativeLoaded && nativeIsInitialized()
    }
    
    // ========== Private Methods ==========
    
    private fun loadNativeLibrary(): Boolean {
        if (isNativeLoaded) return true
        
        return try {
            System.loadLibrary("scrfd_glasses_jni")
            isNativeLoaded = true
            Log.d(TAG, "Native library loaded")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
            false
        }
    }
    
    // ========== Native Methods ==========
    
    private external fun nativeInit(
        assetManager: AssetManager,
        modelType: String,
        useGpu: Boolean
    ): Boolean
    
    private external fun nativeDetect(
        bitmap: Bitmap,
        probThreshold: Float,
        nmsThreshold: Float
    ): FloatArray?
    
    private external fun nativeRelease()
    
    private external fun nativeIsInitialized(): Boolean
}
