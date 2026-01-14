package com.sustech.bojayL.ml

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * 人脸检测器接口
 * 
 * 统一的人脸检测 API，支持多种检测器实现。
 */
interface FaceDetector {
    /**
     * 检测图片中是否包含人脸
     * 
     * @param context Android Context
     * @param imageUri 图片 URI
     * @return true 如果检测到人脸，false 如果未检测到，null 如果检测失败
     */
    suspend fun hasFace(context: Context, imageUri: Uri): Boolean?
    
    /**
     * 检测器名称，用于日志和调试
     */
    val name: String
}

/**
 * 复合人脸检测器
 * 
 * 使用多个检测器进行人脸检测，按优先级依次尝试：
 * 1. InsightFace SCRFD (NCNN) - 高精度
 * 2. Google MLKit - 回退方案
 * 
 * 当主检测器失败时，自动回退到备用检测器。
 */
object CompositeFaceDetector : FaceDetector {
    
    private const val TAG = "CompositeFaceDetector"
    
    override val name = "Composite (SCRFD + MLKit)"
    
    /**
     * 检测策略
     */
    enum class DetectionStrategy {
        SCRFD_ONLY,      // 仅使用 SCRFD
        MLKIT_ONLY,      // 仅使用 MLKit
        SCRFD_FALLBACK,  // SCRFD 优先，失败回退 MLKit (默认)
    }
    
    private var strategy = DetectionStrategy.SCRFD_FALLBACK
    
    /**
     * 设置检测策略
     */
    fun setStrategy(strategy: DetectionStrategy) {
        this.strategy = strategy
        Log.d(TAG, "Detection strategy set to: $strategy")
    }
    
    /**
     * 检测图片中是否包含人脸
     * 
     * 根据当前策略选择检测器：
     * - SCRFD_ONLY: 仅使用 SCRFD
     * - MLKIT_ONLY: 仅使用 MLKit
     * - SCRFD_FALLBACK: SCRFD 优先，失败时回退 MLKit
     */
    override suspend fun hasFace(context: Context, imageUri: Uri): Boolean? {
        return when (strategy) {
            DetectionStrategy.SCRFD_ONLY -> {
                detectWithScrfd(context, imageUri)
            }
            DetectionStrategy.MLKIT_ONLY -> {
                detectWithMlKit(context, imageUri)
            }
            DetectionStrategy.SCRFD_FALLBACK -> {
                detectWithFallback(context, imageUri)
            }
        }
    }
    
    /**
     * SCRFD 优先，MLKit 回退
     */
    private suspend fun detectWithFallback(context: Context, imageUri: Uri): Boolean? {
        Log.d(TAG, "Detecting with SCRFD (fallback to MLKit)")
        
        // 1. 尝试 SCRFD
        val scrfdResult = try {
            InsightfaceNcnnDetector.hasFace(context, imageUri)
        } catch (e: Exception) {
            Log.w(TAG, "SCRFD exception, will fallback to MLKit", e)
            null
        }
        
        // 如果 SCRFD 成功返回结果（无论是否检测到人脸）
        if (scrfdResult != null) {
            Log.d(TAG, "SCRFD result: $scrfdResult")
            return scrfdResult
        }
        
        // 2. SCRFD 失败，回退到 MLKit
        Log.d(TAG, "SCRFD failed, falling back to MLKit")
        val mlkitResult = try {
            FaceDetectorMlKit.hasFace(context, imageUri)
        } catch (e: Exception) {
            Log.e(TAG, "MLKit exception", e)
            null
        }
        
        if (mlkitResult != null) {
            Log.d(TAG, "MLKit result: $mlkitResult")
        } else {
            Log.e(TAG, "Both SCRFD and MLKit failed")
        }
        
        return mlkitResult
    }
    
    /**
     * 仅使用 SCRFD
     */
    private suspend fun detectWithScrfd(context: Context, imageUri: Uri): Boolean? {
        Log.d(TAG, "Detecting with SCRFD only")
        return try {
            InsightfaceNcnnDetector.hasFace(context, imageUri)
        } catch (e: Exception) {
            Log.e(TAG, "SCRFD exception", e)
            null
        }
    }
    
    /**
     * 仅使用 MLKit
     */
    private suspend fun detectWithMlKit(context: Context, imageUri: Uri): Boolean {
        Log.d(TAG, "Detecting with MLKit only")
        return try {
            FaceDetectorMlKit.hasFace(context, imageUri)
        } catch (e: Exception) {
            Log.e(TAG, "MLKit exception", e)
            false
        }
    }
    
    /**
     * 释放所有检测器资源
     */
    fun release() {
        try {
            InsightfaceNcnnDetector.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing SCRFD", e)
        }
    }
}
