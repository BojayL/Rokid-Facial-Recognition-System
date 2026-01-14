package com.sustech.bojayL.ml

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.sustech.bojayL.data.model.Student
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 人脸识别器
 * 
 * 封装 MobileFaceNet 模型，提供人脸特征提取和匹配功能。
 * 
 * 特性：
 * - 512 维特征向量提取
 * - 余弦相似度计算
 * - 学生匹配（基于特征相似度）
 */
object FaceRecognizer {
    
    private const val TAG = "FaceRecognizer"
    
    /**
     * 默认模型类型
     */
    private const val DEFAULT_MODEL_TYPE = "mobilefacenet"
    
    /**
     * 默认相似度阈值（0.0-1.0）
     * 高于此阈值认为是同一个人
     */
    private const val DEFAULT_SIMILARITY_THRESHOLD = 0.7f
    
    private var isNativeLoaded = false
    private var isInitialized = false
    
    /**
     * 初始化人脸识别器
     * 
     * @param context Android Context
     * @param modelType 模型类型，默认 "mobilefacenet"
     * @param useGpu 是否使用 GPU 加速
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
     * 从对齐的人脸图像中提取特征
     * 
     * @param faceBitmap 112x112 对齐的人脸图像
     * @return 512 维特征向量，失败返回 null
     */
    suspend fun extractFeature(faceBitmap: Bitmap): FloatArray? = withContext(Dispatchers.IO) {
        // 确保已初始化
        if (!isInitialized || !nativeIsInitialized()) {
            Log.e(TAG, "Not initialized")
            return@withContext null
        }
        
        // 验证图像尺寸
        if (faceBitmap.width != 112 || faceBitmap.height != 112) {
            Log.e(TAG, "Invalid face size: ${faceBitmap.width}x${faceBitmap.height}, expected 112x112")
            return@withContext null
        }
        
        try {
            val feature = nativeExtractFeature(faceBitmap)
            // 支持 128 维或 512 维特征向量（MobileFaceNet 有不同版本）
            if (feature != null && (feature.size == 128 || feature.size == 512)) {
                Log.d(TAG, "Feature extracted successfully: ${feature.size} dimensions")
                return@withContext feature
            } else {
                Log.e(TAG, "Feature extraction failed or invalid size: ${feature?.size}")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Feature extraction exception", e)
            return@withContext null
        }
    }
    
    /**
     * 计算两个特征向量的余弦相似度
     * 
     * @param feature1 第一个特征向量 (512 floats)
     * @param feature2 第二个特征向量 (512 floats)
     * @return 相似度分数 (0.0-1.0)，失败返回 -1.0
     */
    fun cosineSimilarity(feature1: FloatArray, feature2: FloatArray): Float {
        // 支持 128 维或 512 维特征向量
        if (feature1.size != feature2.size || (feature1.size != 128 && feature1.size != 512)) {
            Log.e(TAG, "Invalid feature size: ${feature1.size}, ${feature2.size}")
            return -1.0f
        }
        
        return try {
            nativeCosineSimilarity(feature1, feature2)
        } catch (e: Exception) {
            Log.e(TAG, "Cosine similarity exception", e)
            -1.0f
        }
    }
    
    /**
     * 匹配学生（基于特征相似度）
     * 
     * @param faceFeature 待匹配的人脸特征向量
     * @param students 学生列表（必须包含人脸特征）
     * @param threshold 相似度阈值，默认 0.7
     * @return 匹配的学生，未找到返回 null
     */
    suspend fun matchStudent(
        faceFeature: FloatArray,
        students: List<Student>,
        threshold: Float = DEFAULT_SIMILARITY_THRESHOLD
    ): MatchResult? = withContext(Dispatchers.Default) {
        // 支持 128 维或 512 维特征向量
        if (faceFeature.size != 128 && faceFeature.size != 512) {
            Log.e(TAG, "Invalid feature size: ${faceFeature.size}")
            return@withContext null
        }
        
        // 过滤出已录入人脸的学生
        val enrolledStudents = students.filter { 
            it.isEnrolled && !it.faceFeature.isNullOrEmpty()
        }
        
        if (enrolledStudents.isEmpty()) {
            Log.d(TAG, "No enrolled students to match")
            return@withContext null
        }
        
        var bestMatch: Student? = null
        var bestSimilarity = 0f
        
        // 遍历所有学生，计算相似度
        for (student in enrolledStudents) {
            val studentFeature = student.faceFeature?.toFloatArray()
            // 支持 128 维或 512 维特征向量
            if (studentFeature == null || (studentFeature.size != 128 && studentFeature.size != 512)) {
                continue
            }
            
            val similarity = cosineSimilarity(faceFeature, studentFeature)
            
            if (similarity > bestSimilarity && similarity >= threshold) {
                bestSimilarity = similarity
                bestMatch = student
            }
        }
        
        if (bestMatch != null) {
            Log.d(TAG, "Matched student: ${bestMatch.name}, similarity: $bestSimilarity")
            return@withContext MatchResult(bestMatch, bestSimilarity)
        } else {
            Log.d(TAG, "No match found (best similarity: $bestSimilarity, threshold: $threshold)")
            return@withContext null
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
            System.loadLibrary("scrfd_jni")
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
    
    private external fun nativeExtractFeature(
        faceBitmap: Bitmap
    ): FloatArray?
    
    private external fun nativeCosineSimilarity(
        feature1: FloatArray,
        feature2: FloatArray
    ): Float
    
    private external fun nativeRelease()
    
    private external fun nativeIsInitialized(): Boolean
    
    /**
     * 匹配结果
     */
    data class MatchResult(
        val student: Student,
        val similarity: Float
    )
}
