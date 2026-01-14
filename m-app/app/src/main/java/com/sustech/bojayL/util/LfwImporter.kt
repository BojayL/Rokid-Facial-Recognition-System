package com.sustech.bojayL.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.sustech.bojayL.data.model.Student
import com.sustech.bojayL.ml.FaceAlignment
import com.sustech.bojayL.ml.FaceRecognizer
import com.sustech.bojayL.ml.InsightfaceNcnnDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * LFW 数据集批量导入器
 * 
 * 从设备存储读取 LFW 导出数据，提取人脸特征并创建学生记录。
 */
object LfwImporter {
    
    private const val TAG = "LfwImporter"
    
    /**
     * LFW 数据目录名
     */
    private const val LFW_DIR_NAME = "lfw_data"
    
    /**
     * 获取 LFW 数据目录（使用应用内部目录，无需权限）
     */
    fun getLfwDir(context: Context): java.io.File {
        // 使用内部存储目录，更可靠
        return java.io.File(context.filesDir, LFW_DIR_NAME)
    }
    
    /**
     * 获取 manifest.json 路径
     */
    fun getManifestPath(context: Context): String {
        return java.io.File(getLfwDir(context), "manifest.json").absolutePath
    }
    
    /**
     * LFW 导入清单
     */
    @Serializable
    data class LfwManifest(
        val version: Int,
        val total_persons: Int,
        val total_images: Int,
        val lfw_base_path: String? = null,
        val persons: List<LfwPerson>
    )
    
    /**
     * LFW 人员信息
     */
    @Serializable
    data class LfwPerson(
        val name: String,
        val dir: String,
        val images: List<String>
    )
    
    /**
     * 导入进度
     */
    data class ImportProgress(
        val current: Int,
        val total: Int,
        val currentName: String,
        val status: ImportStatus,
        val message: String = "",
        val students: List<Student> = emptyList()  // 导入完成时包含学生数据
    )
    
    enum class ImportStatus {
        INITIALIZING,
        PROCESSING,
        SUCCESS,
        FAILED,
        COMPLETED
    }
    
    /**
     * 导入结果
     */
    data class ImportResult(
        val success: Boolean,
        val students: List<Student>,
        val successCount: Int,
        val failedCount: Int,
        val message: String
    )
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    /**
     * 从清单文件导入 LFW 数据
     * 
     * @param context Android Context
     * @param manifestPath 清单文件路径 (manifest.json)
     * @return 导入进度 Flow
     */
    fun importFromManifest(
        context: Context,
        manifestPath: String
    ): Flow<ImportProgress> = flow {
        emit(ImportProgress(0, 0, "", ImportStatus.INITIALIZING, "正在初始化..."))
        
        try {
            // 读取清单文件
            val manifestFile = File(manifestPath)
            if (!manifestFile.exists()) {
                emit(ImportProgress(0, 0, "", ImportStatus.FAILED, "清单文件不存在: $manifestPath"))
                return@flow
            }
            
            val manifestContent = manifestFile.readText()
            val manifest = json.decodeFromString<LfwManifest>(manifestContent)
            
            Log.d(TAG, "Loaded manifest: ${manifest.total_persons} persons, ${manifest.total_images} images")
            
            // 获取图片目录
            val imagesDir = manifestFile.parentFile?.resolve("images")
            if (imagesDir == null || !imagesDir.exists()) {
                emit(ImportProgress(0, 0, "", ImportStatus.FAILED, "图片目录不存在"))
                return@flow
            }
            
            // 初始化检测器和识别器
            emit(ImportProgress(0, manifest.total_persons, "", ImportStatus.INITIALIZING, "正在初始化人脸检测器..."))
            
            withContext(Dispatchers.IO) {
                if (!InsightfaceNcnnDetector.isReady()) {
                    InsightfaceNcnnDetector.init(context)
                }
                if (!FaceRecognizer.isReady()) {
                    FaceRecognizer.init(context)
                }
            }
            
            val students = mutableListOf<Student>()
            var successCount = 0
            var failedCount = 0
            
            // 遍历每个人
            manifest.persons.forEachIndexed { index, person ->
                emit(ImportProgress(
                    current = index + 1,
                    total = manifest.total_persons,
                    currentName = person.name,
                    status = ImportStatus.PROCESSING,
                    message = "正在处理: ${person.name}"
                ))
                
                try {
                    val student = processPersonWithContext(context, person, imagesDir)
                    if (student != null) {
                        students.add(student)
                        successCount++
                        Log.d(TAG, "Successfully imported: ${person.name}")
                    } else {
                        failedCount++
                        Log.w(TAG, "Failed to extract feature for: ${person.name}")
                    }
                } catch (e: Exception) {
                    failedCount++
                    Log.e(TAG, "Error processing ${person.name}", e)
                }
            }
            
            emit(ImportProgress(
                current = manifest.total_persons,
                total = manifest.total_persons,
                currentName = "",
                status = ImportStatus.COMPLETED,
                message = "导入完成: 成功 $successCount, 失败 $failedCount",
                students = students.toList()  // 返回导入的学生数据
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            emit(ImportProgress(0, 0, "", ImportStatus.FAILED, "导入失败: ${e.message}"))
        }
    }
    
    /**
     * 处理单个人员（带 Context）
     */
    private suspend fun processPersonWithContext(
        context: Context,
        person: LfwPerson,
        imagesDir: File
    ): Student? = withContext(Dispatchers.IO) {
        processPerson(context, person, imagesDir)
    }
    
    /**
     * 处理单个人员
     */
    private suspend fun processPerson(
        context: Context,
        person: LfwPerson,
        imagesDir: File
    ): Student? {
        val personDir = imagesDir.resolve(person.dir)
        if (!personDir.exists()) {
            Log.w(TAG, "Person directory not found: ${person.dir}")
            return null
        }
        
        // 尝试从每张图片提取特征，取第一个成功的
        var faceFeature: List<Float>? = null
        var primaryImagePath: String? = null
        
        for (imageName in person.images) {
            val imageFile = personDir.resolve(imageName)
            if (!imageFile.exists()) continue
            
            // 记录第一张图片作为头像
            if (primaryImagePath == null) {
                primaryImagePath = imageFile.absolutePath
            }
            
            val feature = extractFeatureFromFile(context, imageFile)
            if (feature != null) {
                faceFeature = feature
                primaryImagePath = imageFile.absolutePath
                break
            }
        }
        
        // 即使没有特征也创建学生记录（只要有图片）
        if (primaryImagePath == null) {
            return null
        }
        
        // 创建学生记录
        return Student(
            id = "lfw_${UUID.randomUUID().toString().take(8)}",
            studentId = "LFW${person.dir.hashCode().toString().take(5).padStart(5, '0')}",
            name = person.name,
            className = "LFW测试班",
            grade = "测试",
            avatarUrl = "file://$primaryImagePath",
            photoUrl = "file://$primaryImagePath",
            faceFeature = faceFeature,
            isEnrolled = faceFeature != null,  // 有特征才算已录入
            tags = listOf("LFW测试数据")
        )
    }
    
    /**
     * 从图片文件提取人脸特征
     */
    private suspend fun extractFeatureFromFile(
        context: Context,
        imageFile: File
    ): List<Float>? = withContext(Dispatchers.IO) {
        try {
            // 加载图片
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
                ?: return@withContext null
            
            // 检测人脸
            val faces = InsightfaceNcnnDetector.detectFromBitmap(bitmap)
            if (faces.isNullOrEmpty()) {
                bitmap.recycle()
                return@withContext null
            }
            
            val face = faces.first()
            val landmarks = face.landmarks
            
            if (landmarks == null || !FaceAlignment.isValidLandmarks(landmarks)) {
                bitmap.recycle()
                return@withContext null
            }
            
            // 对齐人脸
            val alignedFace = FaceAlignment.alignFace(bitmap, landmarks)
            bitmap.recycle()
            
            if (alignedFace == null) {
                return@withContext null
            }
            
            // 提取特征
            val feature = FaceRecognizer.extractFeature(alignedFace)
            alignedFace.recycle()
            
            feature?.toList()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting feature from ${imageFile.name}", e)
            null
        }
    }
    
    /**
     * 同步导入（返回最终结果）
     */
    suspend fun importSync(
        context: Context,
        manifestPath: String
    ): ImportResult = withContext(Dispatchers.IO) {
        val students = mutableListOf<Student>()
        var successCount = 0
        var failedCount = 0
        var lastMessage = ""
        
        try {
            val manifestFile = File(manifestPath)
            if (!manifestFile.exists()) {
                return@withContext ImportResult(false, emptyList(), 0, 0, "清单文件不存在")
            }
            
            val manifest = json.decodeFromString<LfwManifest>(manifestFile.readText())
            val imagesDir = manifestFile.parentFile?.resolve("images")
                ?: return@withContext ImportResult(false, emptyList(), 0, 0, "图片目录不存在")
            
            // 初始化
            if (!InsightfaceNcnnDetector.isReady()) {
                InsightfaceNcnnDetector.init(context)
            }
            if (!FaceRecognizer.isReady()) {
                FaceRecognizer.init(context)
            }
            
            for (person in manifest.persons) {
                try {
                    val student = processPerson(context, person, imagesDir)
                    if (student != null) {
                        students.add(student)
                        successCount++
                    } else {
                        failedCount++
                    }
                } catch (e: Exception) {
                    failedCount++
                }
            }
            
            lastMessage = "导入完成: 成功 $successCount, 失败 $failedCount"
            ImportResult(true, students, successCount, failedCount, lastMessage)
            
        } catch (e: Exception) {
            ImportResult(false, emptyList(), 0, 0, "导入失败: ${e.message}")
        }
    }
}
