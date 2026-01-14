package com.sustech.bojayL.data.model

/**
 * 识别结果
 * 
 * 根据 PRD 要求 CLS-02：
 * 采用"卡片流"形式，实时从 WebSocket 接收眼镜识别到的学生 ID
 */
data class RecognitionResult(
    val id: String,                          // 识别记录唯一标识
    val studentId: String?,                  // 学生 ID（识别成功时有值）
    val student: Student? = null,            // 关联的学生信息
    val sessionId: String,                   // 所属会话 ID
    val status: RecognitionStatus,           // 识别状态
    val confidence: Float = 0f,              // 识别置信度 0-1
    val timestamp: Long,                     // 识别时间戳
    val faceImageUrl: String? = null,        // 抓拍的人脸图片 URL
    val attendanceStatus: AttendanceStatus = AttendanceStatus.UNKNOWN, // 考勤状态
    val isHighlighted: Boolean = false       // 是否高亮显示（最新识别）
)

/**
 * 识别状态
 */
enum class RecognitionStatus {
    SUCCESS,       // 识别成功
    FAILED,        // 识别失败（人脸质量不佳等）
    UNKNOWN,       // 未知人员（不在底库中）
    PROCESSING     // 识别中
}

/**
 * 识别事件
 * 用于 WebSocket 消息解析
 */
data class RecognitionEvent(
    val type: RecognitionEventType,
    val payload: RecognitionResult? = null,
    val error: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 识别事件类型
 */
enum class RecognitionEventType {
    FACE_DETECTED,      // 检测到人脸
    RECOGNITION_START,  // 开始识别
    RECOGNITION_END,    // 识别完成
    ERROR               // 错误
}

/**
 * 快捷操作类型
 * 
 * 根据 PRD 要求 CLS-04：
 * 在识别卡片上提供快捷操作按钮
 */
enum class QuickAction(val label: String) {
    QUESTION("提问"),     // 记录一次课堂互动
    ABNORMAL("异常")      // 标记状态（如睡觉、玩手机）
}
