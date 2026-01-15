package com.sustech.bojayL.glasses.communication

import android.util.Base64
import android.util.Log
import com.rokid.cxr.CXRServiceBridge
import com.rokid.cxr.Caps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 眼镜端通信桥接器
 * 
 * 封装 CXRServiceBridge，提供与手机端的通信能力
 */
class GlassesBridge {
    
    companion object {
        private const val TAG = "GlassesBridge"
    }
    
    private val cxrServiceBridge = CXRServiceBridge()
    
    // 连接状态
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
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
            _isConnected.value = true
        }
        
        override fun onDisconnected() {
            Log.d(TAG, "Disconnected")
            _isConnected.value = false
        }
        
        override fun onARTCStatus(p0: Float, p1: Boolean) {
            // 暂不使用
        }
    }
    
    // 消息回调
    private val msgCallback = object : CXRServiceBridge.MsgCallback {
        override fun onReceive(name: String?, args: Caps?, bytes: ByteArray?) {
            Log.d(TAG, "Received message: name=$name")
            when (name) {
                MessageProtocol.KEY_PHONE_RESULT -> {
                    args?.let { parseRecognitionResult(it) }
                }
                MessageProtocol.KEY_PHONE_CONFIG -> {
                    args?.let { parseConfig(it) }
                }
            }
        }
    }
    
    /**
     * 初始化通信桥接
     */
    fun init() {
        Log.d(TAG, "Initializing GlassesBridge")
        cxrServiceBridge.setStatusListener(statusListener)
        cxrServiceBridge.subscribe(MessageProtocol.KEY_SUBSCRIBE_PHONE, msgCallback)
    }
    
    /**
     * 发送识别请求
     * 
     * @param imageData 图片数据 (JPEG/WebP)
     * @param faceRect 人脸区域 [x, y, width, height]
     */
    fun sendRecognitionRequest(imageData: ByteArray, faceRect: FloatArray? = null) {
        if (!_isConnected.value) {
            Log.w(TAG, "Not connected, cannot send recognition request")
            return
        }
        
        val caps = Caps().apply {
            // 图片 Base64 编码
            write(Base64.encodeToString(imageData, Base64.NO_WRAP))
            // 时间戳
            writeInt64(System.currentTimeMillis())
            // 人脸区域 (可选)
            if (faceRect != null && faceRect.size == 4) {
                write(Caps().apply {
                    writeInt32(java.lang.Float.floatToIntBits(faceRect[0]))
                    writeInt32(java.lang.Float.floatToIntBits(faceRect[1]))
                    writeInt32(java.lang.Float.floatToIntBits(faceRect[2]))
                    writeInt32(java.lang.Float.floatToIntBits(faceRect[3]))
                })
            }
        }
        
        val result = cxrServiceBridge.sendMessage(MessageProtocol.KEY_GLASS_RECOGNIZE, caps)
        Log.d(TAG, "Sent recognition request, result=$result")
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
     */
    private fun parseRecognitionResult(caps: Caps) {
        try {
            val isKnown = caps.at(0).int == 1
            val studentId = if (caps.size() > 1) caps.at(1).string else null
            val studentName = if (caps.size() > 2) caps.at(2).string else null
            val className = if (caps.size() > 3) caps.at(3).string else null
            val confidence = if (caps.size() > 4) caps.at(4).float else 0f
            
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
     */
    private fun parseConfig(caps: Caps) {
        try {
            val threshold = if (caps.size() > 0) caps.at(0).float else 0.7f
            val interval = if (caps.size() > 1) caps.at(1).int.toLong() else 2000L
            val brightness = if (caps.size() > 2) caps.at(2).int else 80
            
            _config.value = GlassesConfig(
                recognitionThreshold = threshold,
                captureIntervalMs = interval,
                displayBrightness = brightness
            )
            
            Log.d(TAG, "Parsed config: threshold=$threshold, interval=$interval")
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
    }
}

/**
 * 眼镜配置参数
 */
data class GlassesConfig(
    val recognitionThreshold: Float = 0.7f,
    val captureIntervalMs: Long = 2000L,
    val displayBrightness: Int = 80
)
