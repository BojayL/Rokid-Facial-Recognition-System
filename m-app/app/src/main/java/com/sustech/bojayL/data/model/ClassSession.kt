package com.sustech.bojayL.data.model

/**
 * 课堂会话
 * 
 * 根据 PRD 要求 CLS-01：
 * 点击"开始"后，生成 Session ID 推送给眼镜，标记识别记录归属
 */
data class ClassSession(
    val sessionId: String,                // 会话唯一标识
    val className: String,                // 班级名称
    val courseName: String,               // 课程名称
    val teacherId: String,                // 教师 ID
    val status: SessionStatus = SessionStatus.IDLE,
    val startTime: Long? = null,          // 开始时间戳
    val endTime: Long? = null,            // 结束时间戳
    val totalStudents: Int = 0,           // 班级总人数
    val recognizedCount: Int = 0,         // 已识别人数
    val attendanceStats: AttendanceStats = AttendanceStats()
)

/**
 * 会话状态
 */
enum class SessionStatus {
    IDLE,       // 空闲（未开始）
    ACTIVE,     // 进行中
    PAUSED,     // 暂停
    ENDED       // 已结束
}

/**
 * 考勤统计
 */
data class AttendanceStats(
    val presentCount: Int = 0,   // 出勤人数
    val absentCount: Int = 0,    // 缺勤人数
    val lateCount: Int = 0,      // 迟到人数
    val leaveCount: Int = 0      // 请假人数
)

/**
 * 课堂互动记录
 */
data class InteractionRecord(
    val id: String,
    val studentId: String,
    val sessionId: String,
    val type: InteractionType,
    val timestamp: Long,
    val note: String? = null
)

/**
 * 互动类型
 */
enum class InteractionType {
    QUESTION,      // 提问回答
    DISCUSSION,    // 课堂讨论
    PRESENTATION,  // 展示
    OTHER          // 其他
}
