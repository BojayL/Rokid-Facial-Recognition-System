package com.sustech.bojayL.rokid

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.sustech.bojayL.data.model.Student
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import java.util.UUID
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Rokid 服务管理器
 * 
 * 整合 RokidManager 和 RokidMessageHandler，提供统一的接口：
 * - 蓝牙扫描和连接
 * - 与眼镜端的消息通信
 * - 识别请求处理
 */
class RokidService(private val context: Context) {
    
    companion object {
        private const val TAG = "RokidService"
    }
    
    // 协程作用域
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 蓝牙连接管理
    val rokidManager = RokidManager(context)
    
    // 消息处理器
    val messageHandler = RokidMessageHandler()
    
    // 是否已初始化
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    // 综合连接状态
    val connectionState: StateFlow<RokidConnectionState> = rokidManager.connectionState
    
    // 扫描到的设备
    val scannedDevices: StateFlow<List<ScannedDevice>> = rokidManager.scannedDevices
    
    // 是否正在扫描
    val isScanning: StateFlow<Boolean> = rokidManager.isScanning
    
    // 眼镜信息
    val glassInfo: StateFlow<GlassInfo?> = rokidManager.glassInfo
    
    // 识别请求回调
    private var onRecognitionRequest: ((RecognitionRequest) -> Unit)? = null
    
    // 识别结果流（用于手机端UI显示）
    private val _recognitionResults = MutableSharedFlow<GlassRecognitionResult>()
    val recognitionResults: SharedFlow<GlassRecognitionResult> = _recognitionResults.asSharedFlow()
    
    // 配对状态
    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()
    
    // 眼镜配对信息
    private val _glassPairingInfo = MutableStateFlow<GlassPairingInfo?>(null)
    val glassPairingInfo: StateFlow<GlassPairingInfo?> = _glassPairingInfo.asStateFlow()
    
    // 最新眼镜帧（调试用）
    private val _latestGlassesFrame = MutableStateFlow<GlassesFrameData?>(null)
    val latestGlassesFrame: StateFlow<GlassesFrameData?> = _latestGlassesFrame.asStateFlow()
    
    // 协程管理（避免重复创建）
    private var connectionObserverJob: Job? = null
    private var messageListenerJob: Job? = null
    private var isListening = false
    
    /**
     * 初始化服务
     * 
     * @param snResourceId SN 认证文件资源 ID（可选，如果有的话）
     */
    fun initialize(snResourceId: Int? = null) {
        Log.d(TAG, "Initializing RokidService")
        
        // 加载 SN 文件（如果提供）
        snResourceId?.let {
            rokidManager.initSnFile(it)
        }
        
        _isInitialized.value = true
        Log.d(TAG, "RokidService initialized")
    }
    
    /**
     * 开始监听眼镜消息
     */
    fun startListening() {
        if (connectionState.value.status != ConnectionStatus.CONNECTED) {
            Log.w(TAG, "Cannot start listening: not connected")
            return
        }
        
        // 避免重复启动监听器
        if (isListening) {
            Log.d(TAG, "Already listening, skip")
            return
        }
        
        Log.d(TAG, "Starting to listen for glass messages")
        isListening = true
        messageHandler.startListening()
        
        // 取消之前的监听协程
        messageListenerJob?.cancel()
        
        // 启动新的监听协程
        messageListenerJob = serviceScope.launch {
            // 监听识别请求
            launch {
                messageHandler.recognitionRequests.collect { request ->
                    Log.d(TAG, "Received recognition request from glasses, bitmap: ${request.bitmap.width}x${request.bitmap.height}")
                    
                    // 更新最新帧（调试预览用）
                    _latestGlassesFrame.value = GlassesFrameData(
                        bitmap = request.bitmap.copy(Bitmap.Config.ARGB_8888, false),
                        timestamp = request.timestamp
                    )
                    
                    onRecognitionRequest?.invoke(request)
                }
            }
            
            // 监听眼镜状态
            launch {
                messageHandler.glassStatus.collect { status ->
                    Log.d(TAG, "Glass status: battery=${status.battery}, mode=${status.mode}")
                }
            }
            
            // 监听眼镜配对码
            launch {
                messageHandler.glassPairingCode.collect { info ->
                    Log.d(TAG, "Glass pairing code: ${info.code} from ${info.deviceName}")
                    _glassPairingInfo.value = info
                }
            }
            
            // 监听配对确认
            launch {
                messageHandler.pairingConfirmed.collect { confirmation ->
                    Log.d(TAG, "Pairing confirmed: ${confirmation.success}")
                    _isPaired.value = confirmation.success
                }
            }
        }
    }
    
    /**
     * 停止监听
     */
    fun stopListening() {
        Log.d(TAG, "Stopping glass message listener")
        isListening = false
        messageListenerJob?.cancel()
        messageListenerJob = null
        messageHandler.stopListening()
    }
    
    /**
     * 发送配对码验证
     */
    fun verifyPairingCode(code: String) {
        if (connectionState.value.status != ConnectionStatus.CONNECTED) {
            Log.w(TAG, "Cannot verify pairing code: not connected")
            return
        }
        
        Log.d(TAG, "Verifying pairing code: $code")
        messageHandler.sendPairingCode(code)
    }
    
    /**
     * 重置配对状态
     */
    fun resetPairingState() {
        _isPaired.value = false
        _glassPairingInfo.value = null
    }
    
    /**
     * 设置识别请求回调
     */
    fun setOnRecognitionRequest(callback: (RecognitionRequest) -> Unit) {
        onRecognitionRequest = callback
    }
    
    /**
     * 发送识别结果到眼镜，并发布到结果流
     */
    fun sendRecognitionResult(
        isKnown: Boolean,
        student: Student?,
        confidence: Float,
        sessionId: String = ""
    ) {
        Log.d(TAG, "===== sendRecognitionResult called =====")
        Log.d(TAG, "Connection status: ${connectionState.value.status}")
        Log.d(TAG, "isKnown=$isKnown, student=${student?.name}, confidence=$confidence")
        
        if (connectionState.value.status != ConnectionStatus.CONNECTED) {
            Log.w(TAG, "Cannot send result: not connected (status=${connectionState.value.status})")
            return
        }
        
        Log.d(TAG, "Connection OK, sending to glasses via messageHandler...")
        // 发送到眼镜
        messageHandler.sendRecognitionResult(isKnown, student, confidence)
        Log.d(TAG, "messageHandler.sendRecognitionResult called")
        
        // 发布到结果流（供手机端UI使用）
        val result = GlassRecognitionResult(
            id = UUID.randomUUID().toString(),
            isKnown = isKnown,
            student = student,
            confidence = confidence,
            sessionId = sessionId,
            timestamp = System.currentTimeMillis()
        )
        
        serviceScope.launch {
            _recognitionResults.emit(result)
        }
        
        Log.d(TAG, "Sent recognition result: isKnown=$isKnown, name=${student?.name}")
    }
    
    /**
     * 发送配置到眼镜
     * 
     * @param showReticle 是否显示准心
     */
    fun sendConfig(
        threshold: Float,
        intervalMs: Long,
        brightness: Int,
        showReticle: Boolean = true
    ) {
        if (connectionState.value.status != ConnectionStatus.CONNECTED) {
            Log.w(TAG, "Cannot send config: not connected")
            return
        }
        
        messageHandler.sendConfig(threshold, intervalMs, brightness, showReticle)
        Log.d(TAG, "Sent config: threshold=$threshold, interval=$intervalMs, showReticle=$showReticle")
    }
    
    // ========== 蓝牙操作委托 ==========
    
    /**
     * 开始扫描
     */
    fun startScan() {
        Log.d(TAG, "Starting BLE scan")
        rokidManager.startScan()
    }
    
    /**
     * 停止扫描
     */
    fun stopScan() {
        Log.d(TAG, "Stopping BLE scan")
        rokidManager.stopScan()
    }
    
    /**
     * 连接设备
     */
    fun connect(device: ScannedDevice) {
        Log.d(TAG, "Connecting to device: ${device.name}")
        
        // 取消之前的连接观察者
        connectionObserverJob?.cancel()
        
        rokidManager.connect(device)
        
        // 监听连接状态变化，连接成功后开始监听消息
        connectionObserverJob = serviceScope.launch {
            connectionState.collect { state ->
                when (state.status) {
                    ConnectionStatus.CONNECTED -> {
                        Log.d(TAG, "Connected, starting message listener")
                        startListening()
                        // 连接后自动设为已配对，无需配对码验证
                        _isPaired.value = true
                        Log.d(TAG, "Auto-paired on connection")
                    }
                    ConnectionStatus.DISCONNECTED -> {
                        stopListening()
                        _isPaired.value = false
                        _latestGlassesFrame.value = null
                    }
                    else -> { /* CONNECTING, ERROR */ }
                }
            }
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting")
        stopListening()
        rokidManager.disconnect()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        Log.d(TAG, "Releasing RokidService")
        disconnect()
        connectionObserverJob?.cancel()
        connectionObserverJob = null
        _isInitialized.value = false
    }
}

/**
 * 眼镜端识别结果（用于手机端UI显示）
 */
data class GlassRecognitionResult(
    val id: String,
    val isKnown: Boolean,
    val student: Student?,
    val confidence: Float,
    val sessionId: String,
    val timestamp: Long
)

/**
 * 眼镜相机帧数据（调试预览用）
 */
data class GlassesFrameData(
    val bitmap: Bitmap,
    val timestamp: Long
)
