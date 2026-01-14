package com.sustech.bojayL.data.model

import kotlinx.serialization.Serializable

/**
 * 学生数据模型
 * 
 * 根据 PRD 要求，支持：
 * - 基础信息：证件照、家长联系方式
 * - 学业信息：历史成绩、偏科分析
 * - 行为记录：迟到次数、课堂互动得分
 */
@Serializable
data class Student(
    val id: String,                    // 学生唯一标识
    val studentId: String,             // 学号
    val name: String,                  // 姓名
    val className: String,             // 班级名称
    val grade: String,                 // 年级
    val avatarUrl: String? = null,     // 头像 URL
    val photoUrl: String? = null,      // 证件照 URL
    val parentContact: String? = null, // 家长联系方式
    val faceFeatureId: String? = null, // 人脸特征 ID（用于识别）
    val faceFeature: List<Float>? = null, // 512维人脸特征向量（MobileFaceNet）
    val isEnrolled: Boolean = false,   // 是否已录入人脸
    val tags: List<String> = emptyList(), // 标签（如：优秀、重点关注等）
    val academicInfo: AcademicInfo? = null,
    val behaviorRecord: BehaviorRecord? = null
)

/**
 * 学业信息
 */
@Serializable
data class AcademicInfo(
    val averageScore: Float = 0f,           // 平均分
    val ranking: Int = 0,                   // 年级排名
    val recentScores: List<ScoreRecord> = emptyList(), // 最近成绩记录
    val subjectAnalysis: Map<String, Float> = emptyMap() // 各科成绩分析
)

/**
 * 成绩记录
 */
@Serializable
data class ScoreRecord(
    val examName: String,      // 考试名称
    val subject: String,       // 科目
    val score: Float,          // 分数
    val fullScore: Float,      // 满分
    val date: Long             // 考试日期时间戳
)

/**
 * 行为记录
 */
@Serializable
data class BehaviorRecord(
    val lateCount: Int = 0,              // 本学期迟到次数
    val absentCount: Int = 0,            // 本学期缺勤次数
    val interactionScore: Float = 0f,    // 课堂互动得分
    val interactionCount: Int = 0,       // 课堂互动次数
    val abnormalRecords: List<AbnormalRecord> = emptyList() // 异常记录
)

/**
 * 异常记录（如睡觉、玩手机等）
 */
@Serializable
data class AbnormalRecord(
    val type: AbnormalType,
    val description: String,
    val timestamp: Long,
    val sessionId: String
)

/**
 * 异常类型
 */
enum class AbnormalType {
    SLEEPING,      // 睡觉
    PHONE_USAGE,   // 玩手机
    ABSENT,        // 缺勤
    LATE,          // 迟到
    OTHER          // 其他
}

/**
 * 考勤状态
 */
enum class AttendanceStatus {
    UNKNOWN,    // 未知
    PRESENT,    // 出勤
    ABSENT,     // 缺勤
    LATE,       // 迟到
    LEAVE       // 请假
}

/**
 * List<Float> 转 FloatArray 扩展函数
 */
fun List<Float>.toFloatArray(): FloatArray {
    return FloatArray(this.size) { this[it] }
}
