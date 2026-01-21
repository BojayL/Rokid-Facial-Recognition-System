package com.sustech.bojayL.rokid

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.sustech.bojayL.data.model.Student
import com.sustech.bojayL.data.model.toFloatArray
import com.sustech.bojayL.ml.FaceAlignment
import com.sustech.bojayL.ml.FaceRecognizer
import com.sustech.bojayL.ml.InsightfaceNcnnDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 识别请求处理器
 * 
 * 接收来自眼镜端的图像，执行人脸检测和识别，返回结果
 */
class RecognitionProcessor(private val context: Context) {
    
    companion object {
        private const val TAG = "RecognitionProcessor"
        private const val DEFAULT_THRESHOLD = 0.7f
    }
    
    // 使用单例组件
    private val faceDetector = InsightfaceNcnnDetector
    private val faceAlignment = FaceAlignment
    private val faceRecognizer = FaceRecognizer
    
    // 是否已初始化
    private var isInitialized = false
    
    // 识别阈值
    private var threshold = DEFAULT_THRESHOLD
    
    // 学生列表（用于匹配）
    private var students: List<Student> = emptyList()
    
    /**
     * 初始化处理器
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return@withContext
        }
        
        Log.d(TAG, "Initializing RecognitionProcessor...")
        
        try {
            // 初始化人脸检测器与识别器（挂起函数）
            val detOk = faceDetector.init(context)
            val recOk = faceRecognizer.init(context)
            
            isInitialized = detOk && recOk
            Log.d(TAG, "RecognitionProcessor initialized: det=$detOk, rec=$recOk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RecognitionProcessor", e)
        }
    }
    
    /**
     * 更新学生列表
     */
    fun updateStudents(studentList: List<Student>) {
        students = studentList.filter { it.isEnrolled && it.faceFeature != null }
        Log.d(TAG, "Updated students: ${students.size} enrolled")
    }
    
    /**
     * 设置识别阈值
     */
    fun setThreshold(value: Float) {
        threshold = value
        Log.d(TAG, "Threshold set to $value")
    }
    
    /**
     * 处理识别请求
     * 
     * @param bitmap 输入图像（可能是裁切后的人脸或全图）
     * @param landmarks 人脸关键点，如果为 null 则需要进行检测
     * @return 识别结果
     */
    suspend fun process(bitmap: Bitmap, landmarks: FloatArray? = null): ProcessResult = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.w(TAG, "Not initialized")
            return@withContext ProcessResult(
                isKnown = false,
                student = null,
                confidence = 0f,
                errorMessage = "处理器未初始化"
            )
        }
        
        // 检查 bitmap 是否有效
        if (bitmap.isRecycled) {
            Log.w(TAG, "Bitmap already recycled")
            return@withContext ProcessResult(
                isKnown = false,
                student = null,
                confidence = 0f,
                errorMessage = "图像已被回收"
            )
        }
        
        try {
            Log.d(TAG, "Processing image: ${bitmap.width}x${bitmap.height}, hasLandmarks=${landmarks != null}")
            
            // 确定用于对齐的关键点
            val alignmentLandmarks: FloatArray
            
            if (landmarks != null && landmarks.size == 10) {
                // 使用眼镜端传过来的关键点，跳过检测
                Log.d(TAG, "Using provided landmarks from glasses")
                alignmentLandmarks = landmarks
            } else {
                // 需要进行人脸检测（降级模式或旧版协议）
                Log.d(TAG, "No landmarks provided, performing face detection")
                
                val detectionResults = faceDetector.detectFromBitmap(bitmap)
                
                if (detectionResults.isNullOrEmpty()) {
                    Log.d(TAG, "No face detected")
                    return@withContext ProcessResult(
                        isKnown = false,
                        student = null,
                        confidence = 0f,
                        errorMessage = "未检测到人脸"
                    )
                }
                
                val bestFace = detectionResults.maxByOrNull { it.confidence }!!
                Log.d(TAG, "Face detected: confidence=${bestFace.confidence}")
                
                if (bestFace.landmarks == null || bestFace.landmarks.isEmpty()) {
                    Log.w(TAG, "No landmarks detected")
                    return@withContext ProcessResult(
                        isKnown = false,
                        student = null,
                        confidence = 0f,
                        errorMessage = "无法获取关键点"
                    )
                }
                
                alignmentLandmarks = bestFace.landmarks
            }
            
            // 检查 bitmap 是否仍然有效
            if (bitmap.isRecycled) {
                Log.w(TAG, "Bitmap recycled during detection")
                return@withContext ProcessResult(
                    isKnown = false,
                    student = null,
                    confidence = 0f,
                    errorMessage = "图像在处理中被回收"
                )
            }
            
            // 人脸对齐
            val alignedFace = faceAlignment.alignFace(bitmap, alignmentLandmarks)
            if (alignedFace == null) {
                Log.w(TAG, "Face alignment failed")
                return@withContext ProcessResult(
                    isKnown = false,
                    student = null,
                    confidence = 0f,
                    errorMessage = "人脸对齐失败"
                )
            }
            
            // 特征提取
            val feature = faceRecognizer.extractFeature(alignedFace)
            if (!alignedFace.isRecycled) {
                alignedFace.recycle()
            }
            
            if (feature == null) {
                Log.w(TAG, "Feature extraction failed")
                return@withContext ProcessResult(
                    isKnown = false,
                    student = null,
                    confidence = 0f,
                    errorMessage = "特征提取失败"
                )
            }
            
            Log.d(TAG, "Feature extracted: ${feature.size} dimensions")
            
            // 特征匹配
            var bestMatch: Student? = null
            var bestSimilarity = 0f
            
            for (student in students) {
                val studentFeature = student.faceFeature?.toFloatArray() ?: continue
                val similarity = faceRecognizer.cosineSimilarity(feature, studentFeature)
                
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity
                    bestMatch = student
                }
            }
            
            Log.d(TAG, "Best match: ${bestMatch?.name}, similarity=$bestSimilarity, threshold=$threshold")
            
            // 判断是否匹配
            return@withContext if (bestSimilarity >= threshold && bestMatch != null) {
                ProcessResult(
                    isKnown = true,
                    student = bestMatch,
                    confidence = bestSimilarity,
                    errorMessage = null
                )
            } else {
                ProcessResult(
                    isKnown = false,
                    student = null,
                    confidence = bestSimilarity,
                    errorMessage = null
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            return@withContext ProcessResult(
                isKnown = false,
                student = null,
                confidence = 0f,
                errorMessage = e.message ?: "处理失败"
            )
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "Releasing RecognitionProcessor")
        faceDetector.release()
        faceRecognizer.release()
        isInitialized = false
    }
}

/**
 * 处理结果
 */
data class ProcessResult(
    val isKnown: Boolean,
    val student: Student?,
    val confidence: Float,
    val errorMessage: String?
)
