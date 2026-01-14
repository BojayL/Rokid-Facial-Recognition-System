package com.sustech.bojayL.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 人脸对齐工具
 * 
 * 根据人脸关键点（5个点：双眼、鼻尖、嘴角）进行仿射变换，
 * 将人脸对齐到标准姿态（112x112）。
 * 
 * 使用 InsightFace arcface 标准模板进行对齐，确保与 MobileFaceNet 模型兼容。
 */
object FaceAlignment {
    
    private const val TAG = "FaceAlignment"
    
    /**
     * 标准人脸尺寸
     */
    private const val FACE_SIZE = 112
    
    /**
     * InsightFace arcface 标准人脸关键点位置（112x112 图像）
     * 顺序：左眼、右眼、鼻尖、左嘴角、右嘴角
     * 
     * 这些坐标是 InsightFace 模型训练时使用的标准模板，
     * 必须精确匹配以获得最佳识别效果。
     */
    private val STANDARD_LANDMARKS = floatArrayOf(
        38.2946f, 51.6963f,  // 左眼
        73.5318f, 51.5014f,  // 右眼
        56.0252f, 71.7366f,  // 鼻尖
        41.5493f, 92.3655f,  // 左嘴角
        70.7299f, 92.2041f   // 右嘴角
    )
    
    /**
     * 人脸关键点（5个点）
     * 
     * @property landmarks 关键点坐标数组 [x1, y1, x2, y2, ..., x5, y5]
     */
    data class FaceLandmarks(
        val landmarks: FloatArray
    ) {
        init {
            require(landmarks.size == 10) { "Landmarks must contain 5 points (10 values)" }
        }
        
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FaceLandmarks) return false
            return landmarks.contentEquals(other.landmarks)
        }
        
        override fun hashCode(): Int {
            return landmarks.contentHashCode()
        }
    }
    
    /**
     * 根据5个关键点对齐人脸
     * 
     * 使用相似变换（旋转、缩放、平移）将人脸关键点对齐到标准模板位置，
     * 生成 112x112 的对齐人脸图像。
     * 
     * @param bitmap 原始图像
     * @param landmarks 5个关键点坐标 [x1,y1,x2,y2,x3,y3,x4,y4,x5,y5]
     * @return 对齐后的 112x112 人脸图像，失败返回 null
     */
    fun alignFace(bitmap: Bitmap, landmarks: FloatArray): Bitmap? {
        if (landmarks.size != 10) {
            Log.e(TAG, "Invalid landmarks size: ${landmarks.size}, expected 10")
            return null
        }
        
        try {
            // 计算从源关键点到标准关键点的正向变换矩阵
            // canvas.drawBitmap 会自动计算逆变换进行采样
            val forwardMatrix = computeSimilarityTransform(landmarks, STANDARD_LANDMARKS)
            
            if (forwardMatrix == null) {
                Log.e(TAG, "Failed to compute transform matrix")
                return null
            }
            
            // 创建 112x112 输出 Bitmap
            val alignedFace = Bitmap.createBitmap(FACE_SIZE, FACE_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(alignedFace)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            
            // 应用正向变换绘制源图像
            // Android 内部会计算 matrix 的逆来确定采样位置
            canvas.drawBitmap(bitmap, forwardMatrix, paint)
            
            return alignedFace
            
        } catch (e: Exception) {
            Log.e(TAG, "Face alignment failed", e)
            return null
        }
    }
    
    /**
     * 简单的人脸对齐（仅使用双眼）
     * 
     * 如果5个关键点不可用，可以使用此方法仅基于双眼进行对齐。
     * 
     * @param bitmap 原始图像
     * @param leftEye 左眼坐标 (x, y)
     * @param rightEye 右眼坐标 (x, y)
     * @return 对齐后的 112x112 人脸图像
     */
    fun alignFaceSimple(
        bitmap: Bitmap,
        leftEye: Pair<Float, Float>,
        rightEye: Pair<Float, Float>
    ): Bitmap {
        // 目标：左眼 (38.29, 51.70)，右眼 (73.53, 51.50)
        // 目标双眼中心点
        val dstEyesCenterX = (STANDARD_LANDMARKS[0] + STANDARD_LANDMARKS[2]) / 2  // 55.91
        val dstEyesCenterY = (STANDARD_LANDMARKS[1] + STANDARD_LANDMARKS[3]) / 2  // 51.60
        val dstEyeDistance = sqrt(
            (STANDARD_LANDMARKS[2] - STANDARD_LANDMARKS[0]) * (STANDARD_LANDMARKS[2] - STANDARD_LANDMARKS[0]) +
            (STANDARD_LANDMARKS[3] - STANDARD_LANDMARKS[1]) * (STANDARD_LANDMARKS[3] - STANDARD_LANDMARKS[1])
        )  // ~35.24
        
        // 源双眼信息
        val srcEyesCenterX = (leftEye.first + rightEye.first) / 2
        val srcEyesCenterY = (leftEye.second + rightEye.second) / 2
        val dY = rightEye.second - leftEye.second
        val dX = rightEye.first - leftEye.first
        val srcEyeDistance = sqrt(dX * dX + dY * dY)
        val angle = Math.toDegrees(atan2(dY.toDouble(), dX.toDouble())).toFloat()
        
        // 缩放比例
        val scale = dstEyeDistance / srcEyeDistance
        
        // 构建变换：先旋转对齐，再缩放，最后平移到正确位置
        val matrix = Matrix()
        // 1. 旋转使双眼水平（绕源双眼中心点）
        matrix.postRotate(-angle, srcEyesCenterX, srcEyesCenterY)
        // 2. 缩放（绕源双眼中心点）
        matrix.postScale(scale, scale, srcEyesCenterX, srcEyesCenterY)
        // 3. 平移使源双眼中心对齐到目标位置
        // 注意：经过步骤1和2后，srcEyesCenter仍在原位置（因为是绕它旋转和缩放）
        matrix.postTranslate(
            dstEyesCenterX - srcEyesCenterX,
            dstEyesCenterY - srcEyesCenterY
        )
        
        // 创建输出 Bitmap 并绘制
        val alignedFace = Bitmap.createBitmap(FACE_SIZE, FACE_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(alignedFace)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)
        
        return alignedFace
    }
    
    /**
     * 计算相似变换矩阵（正向：src → dst）
     * 
     * 使用最小二乘法估计从源关键点到目标关键点的相似变换（旋转+缩放+平移）。
     * 
     * @param srcLandmarks 源关键点 (10 values: x1,y1,x2,y2,...,x5,y5)
     * @param dstLandmarks 目标关键点 (10 values)
     * @return 正向变换矩阵，失败返回 null
     */
    private fun computeSimilarityTransform(
        srcLandmarks: FloatArray,
        dstLandmarks: FloatArray
    ): Matrix? {
        // 使用所有5个点计算最优相似变换
        // 相似变换形式: [a, -b, tx; b, a, ty]
        // 其中 scale = sqrt(a^2 + b^2), angle = atan2(b, a)
        
        val n = 5  // 点数
        
        // 计算源点和目标点的中心
        var srcCenterX = 0f
        var srcCenterY = 0f
        var dstCenterX = 0f
        var dstCenterY = 0f
        
        for (i in 0 until n) {
            srcCenterX += srcLandmarks[i * 2]
            srcCenterY += srcLandmarks[i * 2 + 1]
            dstCenterX += dstLandmarks[i * 2]
            dstCenterY += dstLandmarks[i * 2 + 1]
        }
        srcCenterX /= n
        srcCenterY /= n
        dstCenterX /= n
        dstCenterY /= n
        
        // 去中心化的坐标
        val srcX = FloatArray(n)
        val srcY = FloatArray(n)
        val dstX = FloatArray(n)
        val dstY = FloatArray(n)
        
        for (i in 0 until n) {
            srcX[i] = srcLandmarks[i * 2] - srcCenterX
            srcY[i] = srcLandmarks[i * 2 + 1] - srcCenterY
            dstX[i] = dstLandmarks[i * 2] - dstCenterX
            dstY[i] = dstLandmarks[i * 2 + 1] - dstCenterY
        }
        
        // 计算最优相似变换参数 (最小二乘法)
        // 目标: 找到 a, b 使得 sum((a*srcX - b*srcY - dstX)^2 + (b*srcX + a*srcY - dstY)^2) 最小
        // 解: a = (sum(srcX*dstX + srcY*dstY)) / (sum(srcX^2 + srcY^2))
        //     b = (sum(srcX*dstY - srcY*dstX)) / (sum(srcX^2 + srcY^2))
        
        var sumSrcSq = 0f
        var sumANum = 0f
        var sumBNum = 0f
        
        for (i in 0 until n) {
            sumSrcSq += srcX[i] * srcX[i] + srcY[i] * srcY[i]
            sumANum += srcX[i] * dstX[i] + srcY[i] * dstY[i]
            sumBNum += srcX[i] * dstY[i] - srcY[i] * dstX[i]
        }
        
        if (sumSrcSq < 1e-10f) {
            Log.e(TAG, "Degenerate source landmarks")
            return null
        }
        
        val a = sumANum / sumSrcSq
        val b = sumBNum / sumSrcSq
        
        // 计算平移 (考虑旋转和缩放后的中心点偏移)
        val tx = dstCenterX - (a * srcCenterX - b * srcCenterY)
        val ty = dstCenterY - (b * srcCenterX + a * srcCenterY)
        
        // 正向变换矩阵: src -> dst
        // | a  -b  tx |
        // | b   a  ty |
        // | 0   0   1 |
        
        val det = a * a + b * b
        if (det < 1e-10f) {
            Log.e(TAG, "Transform determinant too small")
            return null
        }
        
        // 构建 Android Matrix (3x3)
        // 注意：Android Matrix 使用行优先顺序
        val matrix = Matrix()
        val values = floatArrayOf(
            a, -b, tx,
            b, a, ty,
            0f, 0f, 1f
        )
        matrix.setValues(values)
        
        Log.d(TAG, "Transform: scale=${sqrt(det)}, angle=${Math.toDegrees(atan2(b.toDouble(), a.toDouble()))}")
        
        return matrix
    }
    
    /**
     * 从检测结果中提取关键点
     * 
     * @param detectionResult SCRFD 检测结果 (包含关键点数据)
     * @param faceIndex 人脸索引（如果有多个人脸）
     * @return 关键点坐标，失败返回 null
     */
    fun extractLandmarks(detectionResult: FloatArray, faceIndex: Int = 0): FloatArray? {
        if (detectionResult.isEmpty()) {
            return null
        }
        
        val numFaces = detectionResult[0].toInt()
        if (faceIndex >= numFaces) {
            Log.e(TAG, "Face index $faceIndex out of range (total: $numFaces)")
            return null
        }
        
        // 每个人脸: x, y, w, h, prob, lm1_x, lm1_y, ..., lm5_x, lm5_y (共15个值)
        val valuesPerFace = 15
        val offset = 1 + faceIndex * valuesPerFace + 5  // 跳过 bbox 和 prob
        
        if (offset + 10 > detectionResult.size) {
            Log.e(TAG, "Insufficient data for landmarks")
            return null
        }
        
        return detectionResult.copyOfRange(offset, offset + 10)
    }
    
    /**
     * 验证关键点是否有效
     * 
     * @param landmarks 关键点坐标
     * @return true 如果关键点有效
     */
    fun isValidLandmarks(landmarks: FloatArray?): Boolean {
        if (landmarks == null || landmarks.size != 10) {
            return false
        }
        
        // 检查是否有无效值（NaN 或无穷大）
        for (value in landmarks) {
            if (value.isNaN() || value.isInfinite()) {
                return false
            }
        }
        
        // 检查是否有非零值（全零表示检测失败）
        val hasNonZero = landmarks.any { it != 0f }
        
        return hasNonZero
    }
}
