package com.sustech.bojayL.glasses.communication

import android.os.Build
import android.util.Base64
import android.util.Log
import com.rokid.cxr.CXRServiceBridge
import com.rokid.cxr.Caps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * 眼镜端通信桥接器
 * 
 * 封装 CXRServiceBridge，提供与手机端的通信能力
 * 包括配对码生成和验证功能
 */
class GlassesBridge {
    
    companion object {
        private const val TAG = "GlassesBridge"
        private const val PAIRING_CODE_LENGTH = 6
    }
    
    private val cxrServiceBridge = CXRServiceBridge()
    
    // 连接状态
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // 配对状态
    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()
    
    // 配对码
    private val _pairingCode = MutableStateFlow(generatePairingCode())
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()
    
    // 最新识别结果
    private val _recognitionResult = MutableStateFlow<RecognitionResult?>(null)
    val recognitionResult: StateFlow<RecognitionResult?> = _recognitionResult.asStateFlow()
    
    // 配置参数
    private val _config = MutableStateFlow(GlassesConfig())
    val config: StateFlow<GlassesConfig> = _config.asStateFlow()
    
    // 连接状态监听
    private val statusListener = object : CXRServiceBridge.StatusListener {
        override fun onConnected(uuid: String?, type: Int) {
            Log.d(TAG, "Connected: uuid=$uuid, type=$type")
            // 连接成功后设置状态
            _isConnected.value = true
            // 连接后直接设为已配对，无需配对码验证
            _isPaired.value = true
            Log.d(TAG, "Auto-paired on connection, isConnected=${_isConnected.value}, isPaired=${_isPaired.value}")
            
            // 连接成功后订阅消息（关键：必须在连接后订阅才能收到手机端消息）
            subscribePhoneMessages()
        }
        
        override fun onDisconnected() {
            Log.d(TAG, "Disconnected")
            // 断开连接时重置状态
            _isConnected.value = false
            _isPaired.value = false
            // 清除识别结果
            _recognitionResult.value = null
            Log.d(TAG, "States reset: isConnected=${_isConnected.value}, isPaired=${_isPaired.value}")
        }
        
        override fun onARTCStatus(p0: Float, p1: Boolean) {
            // 暂不使用
        }
    }
    
    
    // 消息回调
    private val msgCallback = object : CXRServiceBridge.MsgCallback {
        override fun onReceive(name: String?, args: Caps?, bytes: ByteArray?) {
            Log.d(TAG, "===== Received message from phone =====")
            Log.d(TAG, "name='$name', args size=${args?.size() ?: 0}, bytes size=${bytes?.size ?: 0}")
            Log.d(TAG, "Expected KEY_PHONE_RESULT='${MessageProtocol.KEY_PHONE_RESULT}'")
            
            when (name) {
                MessageProtocol.KEY_PHONE_RESULT -> {
                    Log.d(TAG, "Matched KEY_PHONE_RESULT, parsing recognition result...")
                    args?.let { parseRecognitionResult(it) }
                }
                MessageProtocol.KEY_PHONE_CONFIG -> {
                    Log.d(TAG, "Matched KEY_PHONE_CONFIG, parsing config...")
                    args?.let { parseConfig(it) }
                }
                MessageProtocol.KEY_PHONE_VERIFY_CODE -> {
                    Log.d(TAG, "Matched KEY_PHONE_VERIFY_CODE, verifying...")
                    args?.let { verifyPairingCode(it) }
                }
                else -> {
                    Log.w(TAG, "Unknown message name: '$name', not handled")
                }
            }
        }
    }
    
    /**
     * 初始化通信桥接
     */
    fun init() {
        Log.d(TAG, "Initializing GlassesBridge")
        
        // 重置状态确保干净的初始状态
        _isConnected.value = false
        _isPaired.value = false
        _recognitionResult.value = null
        
        cxrServiceBridge.setStatusListener(statusListener)
        
        // 订阅手机端消息
        subscribePhoneMessages()
        
        Log.d(TAG, "GlassesBridge initialized, waiting for connection")
    }
    
    /**
     * 订阅手机端消息
     * 
     * 注意：必须在连接建立后调用才能生效
     */
    private fun subscribePhoneMessages() {
        val result = cxrServiceBridge.subscribe(MessageProtocol.KEY_SUBSCRIBE_PHONE, msgCallback)
        Log.d(TAG, "Subscribe to phone messages: key=${MessageProtocol.KEY_SUBSCRIBE_PHONE}, result=$result")
        
        if (result != 0) {
            Log.e(TAG, "Failed to subscribe! error code=$result")
        } else {
            Log.d(TAG, "Successfully subscribed to phone messages")
        }
    }
    
    /**
     * 生成配对码
     */
    private fun generatePairingCode(): String {
        return (1..PAIRING_CODE_LENGTH)
            .map { Random.nextInt(0, 10) }
            .joinToString("")
    }
    
    /**
     * 重新生成配对码
     */
    fun regeneratePairingCode() {
        _pairingCode.value = generatePairingCode()
        _isPaired.value = false
        Log.d(TAG, "Generated new pairing code: ${_pairingCode.value}")
    }
    
    /**
     * 广播配对码
     * 连接后周期性发送，让手机端知道配对码
     */
    fun broadcastPairingCode() {
        if (!_isConnected.value) {
            Log.w(TAG, "Not connected, cannot broadcast pairing code")
            return
        }
        
        val deviceName = Build.MODEL
        
        val caps = Caps().apply {
            write(_pairingCode.value)
            write(deviceName)
        }
        
        val result = cxrServiceBridge.sendMessage(MessageProtocol.KEY_GLASS_PAIRING_CODE, caps)
        Log.d(TAG, "Broadcast pairing code: ${_pairingCode.value}, result=$result")
    }
    
    /**
     * 验证配对码
     */
    private fun verifyPairingCode(caps: Caps) {
        try {
            val receivedCode = caps.at(0).string
            Log.d(TAG, "Verifying pairing code: received=$receivedCode, expected=${_pairingCode.value}")
            
            if (receivedCode == _pairingCode.value) {
                _isPaired.value = true
                sendPairingConfirmation(true)
                Log.d(TAG, "Pairing code verified successfully!")
            } else {
                sendPairingConfirmation(false)
                Log.w(TAG, "Pairing code mismatch")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify pairing code", e)
        }
    }
    
    /**
     * 发送配对确认
     */
    private fun sendPairingConfirmation(success: Boolean) {
        val caps = Caps().apply {
            writeInt32(if (success) 1 else 0)
            write(Build.MODEL)
        }
        
        cxrServiceBridge.sendMessage(MessageProtocol.KEY_PAIRING_CONFIRMED, caps)
    }
    
    /**
     * 发送识别请求
     * 
     * @param imageData 图片数据 (JPEG)
     * @param landmarks 人脸关键点 [x1,y1,...,x5,y5]，如果是全图则为 null
     */
    fun sendRecognitionRequest(imageData: ByteArray, landmarks: FloatArray? = null) {
        Log.d(TAG, "sendRecognitionRequest called, imageData size=${imageData.size}, " +
                "hasLandmarks=${landmarks != null}, isConnected=${_isConnected.value}")
        
        if (!_isConnected.value) {
            Log.w(TAG, "Not connected, cannot send recognition request")
            return
        }
        
        try {
            // Base64 编码图片
            val base64Image = Base64.encodeToString(imageData, Base64.NO_WRAP)
            Log.d(TAG, "Base64 encoded image size: ${base64Image.length} chars")
            
            val caps = Caps().apply {
                // 图片 Base64 编码
                write(base64Image)
                // 时间戳（使用 Int64）
                writeInt64(System.currentTimeMillis())
                // 是否有关键点（1=有，0=无/全图）
                writeInt32(if (landmarks != null) 1 else 0)
                // 关键点坐标（如果有）
                if (landmarks != null && landmarks.size == 10) {
                    for (value in landmarks) {
                        writeInt32(java.lang.Float.floatToIntBits(value))
                    }
                }
            }
            
            val result = cxrServiceBridge.sendMessage(MessageProtocol.KEY_GLASS_RECOGNIZE, caps)
            Log.d(TAG, "sendMessage result: $result (key=${MessageProtocol.KEY_GLASS_RECOGNIZE})")
            
            if (result != 0) {
                Log.e(TAG, "Failed to send message! result=$result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in sendRecognitionRequest", e)
        }
    }
    
    /**
     * 发送状态同步
     */
    fun sendStatusSync(battery: Int, mode: String) {
        val caps = Caps().apply {
            writeInt32(battery)
            write(mode)
            write(_isConnected.value)
        }
        
        val result = cxrServiceBridge.sendMessage(MessageProtocol.KEY_GLASS_STATUS, caps)
        Log.d(TAG, "Sent status sync, result=$result")
    }
    
    /**
     * 解析识别结果
     * 
     * 注意：confidence 是通过 floatToIntBits 编码的，需要用 intBitsToFloat 解码
     */
    private fun parseRecognitionResult(caps: Caps) {
        try {
            val isKnown = caps.at(0).int == 1
            val studentId = if (caps.size() > 1) caps.at(1).string else null
            val studentName = if (caps.size() > 2) caps.at(2).string else null
            val className = if (caps.size() > 3) caps.at(3).string else null
            // confidence 是通过 floatToIntBits 编码的
            val confidenceInt = if (caps.size() > 4) caps.at(4).int else 0
            val confidence = java.lang.Float.intBitsToFloat(confidenceInt)
            
            // 解析标签
            val tags = mutableListOf<String>()
            if (caps.size() > 5) {
                val tagsStr = caps.at(5).string
                if (!tagsStr.isNullOrEmpty()) {
                    tags.addAll(tagsStr.split(","))
                }
            }
            
            _recognitionResult.value = RecognitionResult(
                studentId = studentId,
                studentName = studentName,
                className = className,
                confidence = confidence,
                tags = tags,
                isKnown = isKnown
            )
            
            Log.d(TAG, "Parsed result: isKnown=$isKnown, name=$studentName, confidence=$confidence")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse recognition result", e)
        }
    }
    
    /**
     * 解析配置参数
     * 
     * 注意：threshold 是通过 floatToIntBits 编码的
     */
    private fun parseConfig(caps: Caps) {
        try {
            // threshold 是通过 floatToIntBits 编码的
            val thresholdInt = if (caps.size() > 0) caps.at(0).int else java.lang.Float.floatToIntBits(0.7f)
            val threshold = java.lang.Float.intBitsToFloat(thresholdInt)
            val interval = if (caps.size() > 1) caps.at(1).int.toLong() else 2000L
            val brightness = if (caps.size() > 2) caps.at(2).int else 80
            // showReticle: 1=显示, 0=隐藏
            val showReticle = if (caps.size() > 3) caps.at(3).int == 1 else true
            
            _config.value = GlassesConfig(
                recognitionThreshold = threshold,
                captureIntervalMs = interval,
                displayBrightness = brightness,
                showReticle = showReticle
            )
            
            Log.d(TAG, "Parsed config: threshold=$threshold, interval=$interval, showReticle=$showReticle")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse config", e)
        }
    }
    
    /**
     * 清除当前识别结果
     */
    fun clearResult() {
        _recognitionResult.value = null
    }
    
    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "Releasing GlassesBridge")
        cxrServiceBridge.setStatusListener(null)
        _isPaired.value = false
    }
    
    /**
     * 获取配对状态摘要
     */
    fun getPairingStatus(): PairingStatus {
        return PairingStatus(
            code = _pairingCode.value,
            isPaired = _isPaired.value,
            isConnected = _isConnected.value,
            deviceName = Build.MODEL
        )
    }
}

/**
 * 配对状态
 */
data class PairingStatus(
    val code: String,
    val isPaired: Boolean,
    val isConnected: Boolean,
    val deviceName: String
)

/**
 * 眼镜配置参数
 */
data class GlassesConfig(
    val recognitionThreshold: Float = 0.7f,
    val captureIntervalMs: Long = 2000L,
    val displayBrightness: Int = 80,
    val showReticle: Boolean = true  // 是否显示准心
)
