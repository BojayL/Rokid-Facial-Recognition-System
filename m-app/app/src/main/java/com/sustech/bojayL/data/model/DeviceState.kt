package com.sustech.bojayL.data.model

/**
 * 设备连接状态
 * 
 * 根据 PRD 要求 DEV-01：
 * 首页顶部常驻显示眼镜连接状态（未连接/USB连接/蓝牙连接）及眼镜电量图标
 */
data class DeviceState(
    val connectionType: ConnectionType = ConnectionType.DISCONNECTED,
    val batteryLevel: Int = 0,              // 电量百分比 0-100
    val isCharging: Boolean = false,        // 是否正在充电
    val deviceName: String? = null,         // 设备名称
    val deviceId: String? = null,           // 设备唯一标识
    val firmwareVersion: String? = null,    // 固件版本
    val lastConnectedTime: Long? = null,    // 上次连接时间
    val signalStrength: Int = 0,            // 信号强度（蓝牙模式）
    val sdkParams: SdkParams = SdkParams()  // SDK 参数配置
)

/**
 * 连接类型
 */
enum class ConnectionType {
    DISCONNECTED,  // 未连接
    USB,           // USB 直连
    BLUETOOTH,     // 蓝牙连接
    WIFI           // WiFi 连接
}

/**
 * SDK 参数配置
 * 
 * 根据 PRD 要求 DEV-04：
 * 允许调节 SDK 参数并实时下发给眼镜，
 */
data class SdkParams(
    val recognitionThreshold: RecognitionThreshold = RecognitionThreshold.MEDIUM,
    val captureInterval: CaptureInterval = CaptureInterval.CONTINUOUS,
    val displayBrightness: Int = 80,  // 显示亮度 0-100
    val showReticle: Boolean = true   // 是否显示准心
)

/**
 * 识别阈值
 * 低(0.8)/中(0.9)/高(0.95)
 */
enum class RecognitionThreshold(val value: Float, val label: String) {
    LOW(0.8f, "低"),
    MEDIUM(0.9f, "中"),
    HIGH(0.95f, "高")
}

/**
 * 采集间隔
 * 连续/手动/5秒一次
 */
enum class CaptureInterval(val label: String, val intervalMs: Long) {
    CONTINUOUS("连续", 0),
    MANUAL("手动", -1),
    FIVE_SECONDS("5秒", 5000)
}

/**
 * 配对信息
 * 
 * 根据 PRD 要求 DEV-02：
 * 生成动态二维码（包含 WiFi SSID/PWD, Server IP, Token）
 */
data class PairingInfo(
    val wifiSsid: String,
    val wifiPassword: String,
    val serverIp: String,
    val serverPort: Int,
    val token: String,
    val bluetoothMac: String? = null,
    val expiresAt: Long  // 过期时间戳
)
