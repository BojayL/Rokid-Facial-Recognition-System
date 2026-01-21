package com.sustech.bojayL.ui.screens.classroom

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sustech.bojayL.data.model.*
import com.sustech.bojayL.ui.components.HudFaceResult
import com.sustech.bojayL.ui.components.FaceDetectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 课堂页面状态
 */
data class ClassroomUiState(
    val session: ClassSession = ClassSession(
        sessionId = "",
        className = "高三(2)班",
        courseName = "高等数学",
        teacherId = "teacher_001",
        totalStudents = 42
    ),
    val recognitionResults: List<RecognitionResult> = emptyList(),
    val autoPopupEnabled: Boolean = false,
    val workMode: WorkMode = WorkMode.GLASSES,  // 工作模式
    val isPhoneCameraActive: Boolean = false,    // 手机相机是否激活
    val isLoading: Boolean = false,
    val error: String? = null,
    // HUD 相关状态
    val detectedFaces: List<HudFaceResult> = emptyList(),  // 当前检测到的人脸
    val recognizedStudentIds: Set<String> = emptySet()     // 本次会话已识别的学生ID
)

/**
 * 课堂页面 ViewModel
 * 
 * 负责管理课堂会话状态、识别结果流和 HUD 状态
 */
class ClassroomViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "ClassroomViewModel"
    }
    
    private val _uiState = MutableStateFlow(ClassroomUiState())
    val uiState: StateFlow<ClassroomUiState> = _uiState.asStateFlow()
    
    // 最近识别的学生ID集合（用于去重）
    private val recentlyRecognizedStudents = mutableSetOf<String>()
    private val recognitionCooldownMs = 30_000L // 30秒冷却时间
    
    // 已识别学生的时间戳（用于冷却计时）
    private val recognitionTimestamps = mutableMapOf<String, Long>()
    
    /**
     * 开始上课
     * 生成 Session ID 并推送给眼镜
     */
    fun startSession() {
        val sessionId = UUID.randomUUID().toString()
        
        _uiState.update { state ->
            state.copy(
                session = state.session.copy(
                    sessionId = sessionId,
                    status = SessionStatus.ACTIVE,
                    startTime = System.currentTimeMillis(),
                    recognizedCount = 0,
                    attendanceStats = AttendanceStats()
                ),
                recognitionResults = emptyList(),
                recognizedStudentIds = emptySet()  // 清空已识别学生集合
            )
        }
        
        // 清空去重集合
        recentlyRecognizedStudents.clear()
        recognitionTimestamps.clear()
        
        Log.d(TAG, "Session started: $sessionId")
        
        // TODO: 通过 WebSocket 推送 Session ID 给眼镜
        // websocketService.sendSessionStart(sessionId)
        
        // 注意：移除模拟识别结果，实际应从眼镜或手机相机接收
        // simulateRecognitionResults()
    }
    
    /**
     * 结束上课
     */
    fun endSession() {
        _uiState.update { state ->
            state.copy(
                session = state.session.copy(
                    status = SessionStatus.ENDED,
                    endTime = System.currentTimeMillis()
                )
            )
        }
        
        // TODO: 通知眼镜结束会话
        // websocketService.sendSessionEnd()
    }
    
    /**
     * 切换自动弹窗开关
     */
    fun toggleAutoPopup() {
        _uiState.update { state ->
            state.copy(autoPopupEnabled = !state.autoPopupEnabled)
        }
    }
    
    /**
     * 处理快捷操作
     */
    fun handleQuickAction(resultId: String, action: QuickAction) {
        when (action) {
            QuickAction.QUESTION -> recordInteraction(resultId)
            QuickAction.ABNORMAL -> markAbnormal(resultId)
        }
    }
    
    /**
     * 记录课堂互动
     */
    private fun recordInteraction(resultId: String) {
        val result = _uiState.value.recognitionResults.find { it.id == resultId } ?: return
        val studentId = result.studentId ?: return
        
        // TODO: 发送互动记录到后端
        // apiService.recordInteraction(studentId, sessionId, InteractionType.QUESTION)
    }
    
    /**
     * 标记异常
     */
    private fun markAbnormal(resultId: String) {
        val result = _uiState.value.recognitionResults.find { it.id == resultId } ?: return
        val studentId = result.studentId ?: return
        
        // TODO: 显示异常类型选择对话框并记录
        // 暂时记录为 OTHER 类型
    }
    
    /**
     * 接收识别结果（从 WebSocket）
     */
    fun onRecognitionResult(result: RecognitionResult) {
        val studentId = result.studentId ?: return
        
        // 去重：检查该学生是否已经签到过
        if (_uiState.value.recognizedStudentIds.contains(studentId)) {
            Log.d(TAG, "Student $studentId already recognized in this session, skipping")
            return
        }
        
        _uiState.update { state ->
            // 将旧的高亮状态移除
            val updatedResults = state.recognitionResults.map { 
                it.copy(isHighlighted = false)
            }
            
            // 新结果添加到列表顶部并高亮
            val newResult = result.copy(isHighlighted = true)
            
            state.copy(
                recognitionResults = listOf(newResult) + updatedResults,
                recognizedStudentIds = state.recognizedStudentIds + studentId,
                session = state.session.copy(
                    recognizedCount = (state.recognizedStudentIds.size + 1).coerceAtMost(state.session.totalStudents)
                )
            )
        }
        
        Log.d(TAG, "Recognition result added: studentId=$studentId, total recognized=${_uiState.value.recognizedStudentIds.size}")
    }
    
    /**
     * 模拟识别结果（用于开发测试）
     * 注意：此方法已废弃，实际识别应通过眼镜或手机相机
     */
    @Deprecated("Use real recognition from glasses or phone camera")
    private fun simulateRecognitionResults() {
        // 此方法已禁用，避免自动模拟签到
        Log.d(TAG, "simulateRecognitionResults is deprecated and disabled")
    }
    
    // ========== 手机相机模式相关方法 ==========
    
    /**
     * 切换工作模式
     */
    fun setWorkMode(mode: WorkMode) {
        _uiState.update { state ->
            state.copy(
                workMode = mode,
                isPhoneCameraActive = false,
                detectedFaces = emptyList()
            )
        }
    }
    
    /**
     * 开始手机相机会话
     */
    fun startPhoneCameraSession() {
        val sessionId = UUID.randomUUID().toString()
        
        _uiState.update { state ->
            state.copy(
                session = state.session.copy(
                    sessionId = sessionId,
                    status = SessionStatus.ACTIVE,
                    startTime = System.currentTimeMillis(),
                    recognizedCount = 0,
                    attendanceStats = AttendanceStats()
                ),
                recognitionResults = emptyList(),
                isPhoneCameraActive = true,
                detectedFaces = emptyList(),
                recognizedStudentIds = emptySet()
            )
        }
        
        // 清空去重集合
        recentlyRecognizedStudents.clear()
        recognitionTimestamps.clear()
        Log.d(TAG, "Phone camera session started: $sessionId")
    }
    
    /**
     * 关闭手机相机
     */
    fun closePhoneCamera() {
        _uiState.update { state ->
            state.copy(
                isPhoneCameraActive = false,
                detectedFaces = emptyList()
            )
        }
        Log.d(TAG, "Phone camera closed")
    }
    
    /**
     * 手机模式识别学生
     * 包含去重逻辑
     */
    fun recognizeStudent(student: Student) {
        // 检查是否在冷却时间内
        if (recentlyRecognizedStudents.contains(student.id)) {
            Log.d(TAG, "Student ${student.name} is in cooldown, skipping")
            return
        }
        
        // 生成识别结果
        val result = RecognitionResult(
            id = UUID.randomUUID().toString(),
            studentId = student.id,
            student = student,
            sessionId = _uiState.value.session.sessionId,
            status = RecognitionStatus.SUCCESS,
            confidence = 0.95f,
            timestamp = System.currentTimeMillis(),
            attendanceStatus = AttendanceStatus.PRESENT
        )
        
        // 添加到结果流
        onRecognitionResult(result)
        
        // 加入去重集合和已识别集合
        recentlyRecognizedStudents.add(student.id)
        recognitionTimestamps[student.id] = System.currentTimeMillis()
        
        _uiState.update { state ->
            state.copy(
                recognizedStudentIds = state.recognizedStudentIds + student.id
            )
        }
        
        Log.d(TAG, "Recognized: ${student.name}")
        
        // 延迟移除（冷却时间后）
        viewModelScope.launch {
            kotlinx.coroutines.delay(recognitionCooldownMs)
            recentlyRecognizedStudents.remove(student.id)
            recognitionTimestamps.remove(student.id)
            Log.d(TAG, "Student ${student.name} cooldown expired")
        }
    }
    
    /**
     * 更新检测到的人脸列表 (HUD 使用)
     */
    fun updateDetectedFaces(faces: List<HudFaceResult>) {
        _uiState.update { state ->
            state.copy(detectedFaces = faces)
        }
        
        // 自动识别已匹配的学生
        faces.forEach { face ->
            if (face.state == FaceDetectionState.RECOGNIZED && face.student != null) {
                recognizeStudent(face.student)
            }
        }
    }
    
    /**
     * 检查学生是否在冷却中
     */
    fun isStudentInCooldown(studentId: String): Boolean {
        return recentlyRecognizedStudents.contains(studentId)
    }
    
    /**
     * 获取当前检测的人脸数
     */
    fun getDetectedFaceCount(): Int = _uiState.value.detectedFaces.size
    
    /**
     * 获取已识别的学生数
     */
    fun getRecognizedCount(): Int = _uiState.value.recognizedStudentIds.size
}
