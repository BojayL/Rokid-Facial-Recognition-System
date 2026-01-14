package com.sustech.bojayL.ml

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * InsightFace SCRFD 人脸检测器 (基于 NCNN)
 * 
 * 提供高精度的人脸检测能力，用于人脸录入时的质量验证。
 * 使用 SCRFD (Sample and Computation Redistribution for Face Detection) 模型。
 * 
 * 参考: https://github.com/deepinsight/insightface/tree/master/detection/scrfd
 */
object InsightfaceNcnnDetector {
    
    private const val TAG = "InsightfaceNcnn"
    
    /**
     * 模型类型
     * - 500m: 最快，精度较低
     * - 1g: 快速，精度中等
     * - 2.5g_kps: 平衡速度和精度，包含关键点检测 (推荐)
     * - 10g_kps: 高精度，较慢
     */
    private const val DEFAULT_MODEL_TYPE = "2.5g_kps"
    
    /**
     * 检测阈值
     */
    private const val DEFAULT_PROB_THRESHOLD = 0.5f
    private const val DEFAULT_NMS_THRESHOLD = 0.45f
    
    private var isNativeLoaded = false
    private var isInitialized = false
    
    /**
     * 人脸检测结果
     */
    data class FaceResult(
        val rect: RectF,           // 人脸边界框
        val confidence: Float,     // 置信度 (0.0-1.0)
        val landmarks: FloatArray? = null  // 5个关键点坐标 [x1,y1,x2,y2,...,x5,y5]
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
            result = 31 * result + (landmarks?.contentHashCode() ?: 0)
            return result
        }
    }
    
    /**
     * 初始化检测器
     * 
     * @param context Android Context
     * @param modelType 模型类型，默认 "2.5g_kps"
     * @param useGpu 是否使用 GPU 加速 (需要 Vulkan 支持)
     * @return true 如果初始化成功
     */
    suspend fun init(
        context: Context,
        modelType: String = DEFAULT_MODEL_TYPE,
        useGpu: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized && nativeIsInitialized()) {
            Log.d(TAG, "Already initialized")
            return@withContext true
        }
        
        // 加载 native 库
        if (!loadNativeLibrary()) {
            Log.e(TAG, "Failed to load native library")
            return@withContext false
        }
        
        try {
            val assetManager = context.assets
            val success = nativeInit(assetManager, modelType, useGpu)
            isInitialized = success
            if (success) {
                Log.d(TAG, "Initialized with model: $modelType, GPU: $useGpu")
            } else {
                Log.e(TAG, "Native init failed")
            }
            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "Init exception", e)
            return@withContext false
        }
    }
    
    /**
     * 检测 Bitmap 中的人脸
     * 
     * @param bitmap 图片 Bitmap
     * @param probThreshold 检测置信度阈值
     * @param nmsThreshold NMS 阈值
     * @return 检测到的人脸列表，如果失败返回 null
     */
    suspend fun detectFromBitmap(
        bitmap: Bitmap,
        probThreshold: Float = DEFAULT_PROB_THRESHOLD,
        nmsThreshold: Float = DEFAULT_NMS_THRESHOLD
    ): List<FaceResult>? = withContext(Dispatchers.IO) {
        // 确保已初始化
        if (!isInitialized || !nativeIsInitialized()) {
            Log.e(TAG, "Detector not initialized")
            return@withContext null
        }
        
        try {
            // 执行检测
            val result = nativeDetect(bitmap, probThreshold, nmsThreshold)
            
            if (result == null || result.isEmpty()) {
                Log.d(TAG, "No faces detected")
                return@withContext emptyList()
            }
            
            // 解析结果
            // 新格式: [numFaces, x, y, w, h, prob, lm1_x, lm1_y, ..., lm5_x, lm5_y, ...]
            // 每个人脸 15 个值
            val numFaces = result[0].toInt()
            if (numFaces == 0) {
                return@withContext emptyList()
            }
            
            val valuesPerFace = 15  // 5 (bbox+prob) + 10 (5 landmarks * 2)
            val faces = mutableListOf<FaceResult>()
            for (i in 0 until numFaces) {
                val offset = 1 + i * valuesPerFace
                if (offset + 14 < result.size) {
                    // 提取关键点
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
            
            Log.d(TAG, "Detected ${faces.size} faces with landmarks")
            return@withContext faces
            
        } catch (e: Exception) {
            Log.e(TAG, "Detection exception", e)
            return@withContext null
        }
    }
    
    /**
     * 检测图片中的人脸
     * 
     * @param context Android Context
     * @param imageUri 图片 URI
     * @param probThreshold 检测置信度阈值
     * @param nmsThreshold NMS 阈值
     * @return 检测到的人脸列表，如果失败返回 null
     */
    suspend fun detect(
        context: Context,
        imageUri: Uri,
        probThreshold: Float = DEFAULT_PROB_THRESHOLD,
        nmsThreshold: Float = DEFAULT_NMS_THRESHOLD
    ): List<FaceResult>? = withContext(Dispatchers.IO) {
        // 确保已初始化
        if (!isInitialized || !nativeIsInitialized()) {
            if (!init(context)) {
                Log.e(TAG, "Auto-init failed")
                return@withContext null
            }
        }
        
        try {
            // 加载图片为 Bitmap
            val bitmap = loadBitmap(context, imageUri) ?: run {
                Log.e(TAG, "Failed to load bitmap from URI: $imageUri")
                return@withContext null
            }
            
            // 执行检测
            val result = nativeDetect(bitmap, probThreshold, nmsThreshold)
            bitmap.recycle()
            
            if (result == null || result.isEmpty()) {
                Log.d(TAG, "No faces detected")
                return@withContext emptyList()
            }
            
            // 解析结果
            // 新格式: [numFaces, x, y, w, h, prob, lm1_x, lm1_y, ..., lm5_x, lm5_y, ...]
            val numFaces = result[0].toInt()
            if (numFaces == 0) {
                return@withContext emptyList()
            }
            
            val valuesPerFace = 15
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
            
            Log.d(TAG, "Detected ${faces.size} faces with landmarks")
            return@withContext faces
            
        } catch (e: Exception) {
            Log.e(TAG, "Detection exception", e)
            return@withContext null
        }
    }
    
    /**
     * 检测图片中是否有人脸
     * 
     * @param context Android Context
     * @param imageUri 图片 URI
     * @return true 如果检测到至少一个人脸，null 如果检测失败
     */
    suspend fun hasFace(context: Context, imageUri: Uri): Boolean? {
        val faces = detect(context, imageUri)
        return faces?.isNotEmpty()
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
            System.loadLibrary("scrfd_jni")
            isNativeLoaded = true
            Log.d(TAG, "Native library loaded")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
            false
        }
    }
    
    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.let { bitmap ->
                    // 确保是 ARGB_8888 格式
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
            Log.e(TAG, "Failed to load bitmap", e)
            null
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
