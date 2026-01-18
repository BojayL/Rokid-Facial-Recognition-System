package com.sustech.bojayL.rokid

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.rokid.cxr.Caps
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.listeners.CustomCmdListener
import com.sustech.bojayL.data.model.Student
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Rokid 消息处理器
 * 
 * 处理与眼镜端的自定义消息通信
 */
class RokidMessageHandler {
    
    companion object {
        private const val TAG = "RokidMessageHandler"
        
        // 消息 Key 定义 (与眼镜端一致)
        const val KEY_GLASS_RECOGNIZE = "glass_recognize"
        const val KEY_GLASS_STATUS = "glass_status"
        const val KEY_GLASS_PAIRING_CODE = "glass_pairing_code"
        const val KEY_PHONE_RESULT = "phone_result"
        const val KEY_PHONE_CONFIG = "phone_config"
        const val KEY_PHONE_VERIFY_CODE = "phone_verify_code"
        const val KEY_PAIRING_CONFIRMED = "pairing_confirmed"
        const val KEY_SUBSCRIBE_GLASS = "rk_custom_key"
    }
    
    // 识别请求流（添加缓冲区避免丢失消息）
    private val _recognitionRequests = MutableSharedFlow<RecognitionRequest>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val recognitionRequests: SharedFlow<RecognitionRequest> = _recognitionRequests.asSharedFlow()
    
    // 眼镜状态流
    private val _glassStatus = MutableSharedFlow<GlassStatus>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val glassStatus: SharedFlow<GlassStatus> = _glassStatus.asSharedFlow()
    
    // 配对码流
    private val _glassPairingCode = MutableSharedFlow<GlassPairingInfo>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val glassPairingCode: SharedFlow<GlassPairingInfo> = _glassPairingCode.asSharedFlow()
    
    // 配对确认流
    private val _pairingConfirmed = MutableSharedFlow<PairingConfirmation>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val pairingConfirmed: SharedFlow<PairingConfirmation> = _pairingConfirmed.asSharedFlow()
    
    // 自定义消息监听器
    private val customCmdListener = CustomCmdListener { cmdKey, caps ->
        Log.d(TAG, "===== Received custom cmd: cmdKey='$cmdKey', caps size=${caps?.size() ?: 0} =====")
        
        when (cmdKey) {
            KEY_GLASS_RECOGNIZE -> {
                Log.d(TAG, "Handling KEY_GLASS_RECOGNIZE")
                caps?.let { parseRecognitionRequest(it) }
            }
            KEY_GLASS_STATUS -> {
                Log.d(TAG, "Handling KEY_GLASS_STATUS")
                caps?.let { parseGlassStatus(it) }
            }
            KEY_GLASS_PAIRING_CODE -> {
                Log.d(TAG, "Handling KEY_GLASS_PAIRING_CODE")
                caps?.let { parseGlassPairingCode(it) }
            }
            KEY_PAIRING_CONFIRMED -> {
                Log.d(TAG, "Handling KEY_PAIRING_CONFIRMED")
                caps?.let { parsePairingConfirmation(it) }
            }
            else -> {
                Log.w(TAG, "Unknown cmdKey: '$cmdKey'")
            }
        }
    }
    
    /**
     * 开始监听消息
     */
    fun startListening() {
        Log.d(TAG, "===== Starting message listener =====")
        try {
            CxrApi.getInstance().setCustomCmdListener(customCmdListener)
            Log.d(TAG, "setCustomCmdListener called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set custom cmd listener", e)
        }
    }
    
    /**
     * 停止监听消息
     */
    fun stopListening() {
        Log.d(TAG, "Stopping message listener")
        CxrApi.getInstance().setCustomCmdListener(null)
    }
    
    /**
     * 发送识别结果
     * 
     * 注意：Rokid SDK 的 Caps 没有 writeFloat() 方法，
     * 需要用 writeInt32(Float.floatToIntBits(value)) 代替
     */
    fun sendRecognitionResult(
        isKnown: Boolean,
        student: Student?,
        confidence: Float
    ) {
        Log.d(TAG, "===== sendRecognitionResult called =====")
        Log.d(TAG, "isKnown=$isKnown, student=${student?.name}, confidence=$confidence")
        
        try {
            val caps = Caps().apply {
                // isKnown (int: 1=known, 0=unknown)
                writeInt32(if (isKnown) 1 else 0)
                
                // studentId
                write(student?.studentId ?: "")
                
                // name
                write(student?.name ?: "")
                
                // className
                write(student?.className ?: "")
                
                // confidence (使用 floatToIntBits 编码)
                writeInt32(java.lang.Float.floatToIntBits(confidence))
                
                // tags (comma-separated)
                write(student?.tags?.joinToString(",") ?: "")
            }
            
            Log.d(TAG, "Caps created with ${caps.size()} fields, sending to glasses...")
            CxrApi.getInstance().sendCustomCmd(KEY_PHONE_RESULT, caps)
            Log.d(TAG, "sendCustomCmd called successfully for KEY_PHONE_RESULT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send recognition result", e)
        }
    }
    
    /**
     * 发送配置参数
     * 
     * 注意：使用 floatToIntBits 编码浮点数
     * @param showReticle 是否显示准心
     */
    fun sendConfig(
        threshold: Float,
        intervalMs: Long,
        brightness: Int,
        showReticle: Boolean = true
    ) {
        val caps = Caps().apply {
            writeInt32(java.lang.Float.floatToIntBits(threshold))
            writeInt32(intervalMs.toInt())
            writeInt32(brightness)
            writeInt32(if (showReticle) 1 else 0)
        }
        
        CxrApi.getInstance().sendCustomCmd(KEY_PHONE_CONFIG, caps)
        Log.d(TAG, "Sent config: threshold=$threshold, interval=$intervalMs, showReticle=$showReticle")
    }
    
    /**
     * 解析识别请求
     */
    private fun parseRecognitionRequest(caps: Caps) {
        try {
            Log.d(TAG, "parseRecognitionRequest: caps.size()=${caps.size()}")
            
            // 图片 Base64
            val imageBase64 = caps.at(0).string
            Log.d(TAG, "Base64 string length: ${imageBase64?.length ?: 0}")
            
            if (imageBase64.isNullOrEmpty()) {
                Log.e(TAG, "imageBase64 is null or empty!")
                return
            }
            
            // 时间戳（尝试多种方式读取）
            val timestamp = try {
                if (caps.size() > 1) caps.at(1).long else System.currentTimeMillis()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read timestamp as long, using current time", e)
                System.currentTimeMillis()
            }
            
            // 解码图片
            val imageData = Base64.decode(imageBase64, Base64.NO_WRAP)
            Log.d(TAG, "Decoded image data size: ${imageData.size} bytes")
            
            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            
            if (bitmap != null) {
                val request = RecognitionRequest(
                    bitmap = bitmap,
                    timestamp = timestamp
                )
                
                // 发送到流
                val emitResult = _recognitionRequests.tryEmit(request)
                Log.d(TAG, "Parsed recognition request, image size: ${bitmap.width}x${bitmap.height}, emit result: $emitResult")
            } else {
                Log.e(TAG, "Failed to decode image from ${imageData.size} bytes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse recognition request", e)
        }
    }
    
    /**
     * 解析眼镜状态
     */
    private fun parseGlassStatus(caps: Caps) {
        try {
            val battery = caps.at(0).int
            val mode = if (caps.size() > 1) caps.at(1).string else "unknown"
            val isConnected = if (caps.size() > 2) caps.at(2).int == 1 else true
            
            val status = GlassStatus(
                battery = battery,
                mode = mode,
                isConnected = isConnected
            )
            
            _glassStatus.tryEmit(status)
            Log.d(TAG, "Parsed glass status: battery=$battery, mode=$mode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse glass status", e)
        }
    }
    
    /**
     * 解析眼镜配对码
     */
    private fun parseGlassPairingCode(caps: Caps) {
        try {
            val code = caps.at(0).string
            val deviceName = if (caps.size() > 1) caps.at(1).string else "Unknown"
            
            val info = GlassPairingInfo(
                code = code,
                deviceName = deviceName,
                timestamp = System.currentTimeMillis()
            )
            
            _glassPairingCode.tryEmit(info)
            Log.d(TAG, "Received pairing code: $code from $deviceName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse pairing code", e)
        }
    }
    
    /**
     * 解析配对确认
     */
    private fun parsePairingConfirmation(caps: Caps) {
        try {
            val success = caps.at(0).int == 1
            val deviceName = if (caps.size() > 1) caps.at(1).string else "Unknown"
            
            val confirmation = PairingConfirmation(
                success = success,
                deviceName = deviceName
            )
            
            _pairingConfirmed.tryEmit(confirmation)
            Log.d(TAG, "Pairing confirmation: success=$success, device=$deviceName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse pairing confirmation", e)
        }
    }
    
    /**
     * 发送配对码验证
     */
    fun sendPairingCode(code: String) {
        val caps = Caps().apply {
            write(code)
        }
        
        CxrApi.getInstance().sendCustomCmd(KEY_PHONE_VERIFY_CODE, caps)
        Log.d(TAG, "Sent pairing code: $code")
    }
}

/**
 * 眼镜配对信息
 */
data class GlassPairingInfo(
    val code: String,
    val deviceName: String,
    val timestamp: Long
)

/**
 * 配对确认
 */
data class PairingConfirmation(
    val success: Boolean,
    val deviceName: String
)

/**
 * 识别请求数据
 */
data class RecognitionRequest(
    val bitmap: Bitmap,
    val timestamp: Long
)

/**
 * 眼镜状态数据
 */
data class GlassStatus(
    val battery: Int,
    val mode: String,
    val isConnected: Boolean
)
