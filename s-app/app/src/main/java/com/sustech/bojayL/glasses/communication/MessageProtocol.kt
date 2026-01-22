package com.sustech.bojayL.glasses.communication

/**
 * 眼镜端与手机端通信协议
 * 
 * 消息格式使用 Rokid SDK 的 Caps 序列化
 * 注意：SDK 没有 writeFloat()，使用 writeInt32(Float.floatToIntBits()) 代替
 */
object MessageProtocol {
    
    // ========== 消息 Key 定义 ==========
    
    /** 眼镜发送: 识别请求 */
    const val KEY_GLASS_RECOGNIZE = "glass_recognize"
    
    /** 眼镜发送: 状态同步 */
    const val KEY_GLASS_STATUS = "glass_status"
    
    /** 眼镜发送: 配对码广播 */
    const val KEY_GLASS_PAIRING_CODE = "glass_pairing_code"
    
    /** 手机发送: 识别结果 */
    const val KEY_PHONE_RESULT = "phone_result"
    
    /** 手机发送: 参数配置 */
    const val KEY_PHONE_CONFIG = "phone_config"
    
    /** 手机发送: 验证配对码 */
    const val KEY_PHONE_VERIFY_CODE = "phone_verify_code"
    
    /** 配对确认 */
    const val KEY_PAIRING_CONFIRMED = "pairing_confirmed"
    
    /** 订阅手机端消息的 Key */
    // 尝试使用 rk_custom_key，因为手机端发送的是这个 key
    const val KEY_SUBSCRIBE_PHONE = "rk_custom_key"
    
    // ========== 消息字段定义 ==========
    
    // 识别请求字段
    const val FIELD_IMAGE_BASE64 = "imageBase64"
    const val FIELD_TIMESTAMP = "timestamp"
    const val FIELD_FACE_RECT = "faceRect"  // x,y,w,h
    const val FIELD_LANDMARKS = "landmarks"  // 人脸关键点 [x1,y1,...,x5,y5]
    
    // 识别结果字段
    const val FIELD_STUDENT_ID = "studentId"
    const val FIELD_STUDENT_NAME = "name"
    const val FIELD_CONFIDENCE = "confidence"
    const val FIELD_CLASS_NAME = "className"
    const val FIELD_TAGS = "tags"
    const val FIELD_IS_KNOWN = "isKnown"
    
    // 状态同步字段
    const val FIELD_BATTERY = "battery"
    const val FIELD_MODE = "mode"
    const val FIELD_IS_CONNECTED = "isConnected"
    
    // 参数配置字段
    const val FIELD_THRESHOLD = "threshold"
    const val FIELD_INTERVAL = "interval"
    const val FIELD_BRIGHTNESS = "brightness"
    
    // 配对码字段
    const val FIELD_PAIRING_CODE = "pairingCode"
    const val FIELD_DEVICE_NAME = "deviceName"
}

/**
 * 识别结果数据
 */
data class RecognitionResult(
    val studentId: String?,
    val studentName: String?,
    val className: String?,
    val confidence: Float,
    val tags: List<String>,
    val isKnown: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 人脸检测状态
 */
enum class FaceState {
    /** 无人脸 */
    NONE,
    /** 检测到人脸，等待识别 */
    DETECTING,
    /** 识别中 */
    RECOGNIZING,
    /** 识别成功 */
    RECOGNIZED,
    /** 未知人员 */
    UNKNOWN
}

/**
 * 采集模式
 */
enum class CaptureMode {
    /** 自动连续采集 */
    AUTO,
    /** 手动触发采集 */
    MANUAL
}
