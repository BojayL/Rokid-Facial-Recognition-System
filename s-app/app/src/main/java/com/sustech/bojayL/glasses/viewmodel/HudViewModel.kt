package com.sustech.bojayL.glasses.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sustech.bojayL.glasses.camera.GlassesCamera
import com.sustech.bojayL.glasses.communication.*
import com.sustech.bojayL.glasses.input.KeyType
import com.sustech.bojayL.glasses.input.SwipeDirection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * HUD 界面状态
 */
data class HudUiState(
    // 连接状态
    val isConnected: Boolean = false,
    val batteryLevel: Int = 80,
    
    // 配对状态（连接后自动配对）
    val isPaired: Boolean = false,
    
    // 识别状态
    val faceState: FaceState = FaceState.NONE,
    val recognitionResult: RecognitionResult? = null,
    
    // 统计信息
    val recognizedCount: Int = 0,      // 本次会话已识别人数
    val captureCount: Int = 0,         // 本次会话采集次数
    
    // 模式
    val captureMode: CaptureMode = CaptureMode.AUTO,
    val isRecording: Boolean = false,
    
    // 显示设置
    val showReticle: Boolean = true,  // 是否显示准心
    
    // 提示消息
    val toastMessage: String? = null
)

/**
 * HUD ViewModel
 * 
 * 管理眼镜端 HUD 界面的所有状态
 */
class HudViewModel : ViewModel() {
    
    companion object {
        private const val TAG = "HudViewModel"
        private const val RESULT_DISPLAY_DURATION = 3000L  // 识别结果显示时长
        private const val TOAST_DISPLAY_DURATION = 2000L  // 提示消息显示时长
    }
    
    // 通信桥接器
    private val glassesBridge = GlassesBridge()
    
    // 相机管理器（需要通过 initCamera 初始化）
    private var glassesCamera: GlassesCamera? = null
    
    // 相机是否已初始化
    private val _isCameraInitialized = MutableStateFlow(false)
    val isCameraInitialized: StateFlow<Boolean> = _isCameraInitialized.asStateFlow()
    
    // UI 状态
    private val _uiState = MutableStateFlow(HudUiState())
    val uiState: StateFlow<HudUiState> = _uiState.asStateFlow()
    
    // 定时任务
    private var resultClearJob: Job? = null
    private var toastClearJob: Job? = null
    
    init {
        glassesBridge.init()
        observeBridgeState()
        observePairingState()
    }
    
    /**
     * 监听通信桥接状态
     */
    private fun observeBridgeState() {
        // 监听连接状态
        viewModelScope.launch {
            glassesBridge.isConnected.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
                if (connected) {
                    showToast("已连接手机")
                } else {
                    showToast("连接已断开")
                }
            }
        }
        
        // 监听识别结果
        viewModelScope.launch {
            glassesBridge.recognitionResult.collect { result ->
                if (result != null) {
                    handleRecognitionResult(result)
                }
            }
        }
        
        // 监听配置变更
        viewModelScope.launch {
            glassesBridge.config.collect { config ->
                Log.d(TAG, "Config updated: showReticle=${config.showReticle}")
                // 直接应用准心显示配置
                val oldValue = _uiState.value.showReticle
                _uiState.update { it.copy(showReticle = config.showReticle) }
                if (oldValue != config.showReticle) {
                    showToast(if (config.showReticle) "准心已开启" else "准心已关闭")
                }
            }
        }
    }
    
    /**
     * 监听配对状态（连接后自动配对）
     */
    private fun observePairingState() {
        viewModelScope.launch {
            glassesBridge.isPaired.collect { paired ->
                val wasPaired = _uiState.value.isPaired
                _uiState.update { it.copy(isPaired = paired) }
                if (paired) {
                    showToast("已就绪")
                    // 配对成功后自动开始采集（如果相机已初始化）
                    if (!wasPaired && _isCameraInitialized.value && !_uiState.value.isRecording) {
                        startCapture()
                    }
                }
            }
        }
    }
    
    /**
     * 处理识别结果
     */
    private fun handleRecognitionResult(result: RecognitionResult) {
        Log.d(TAG, "Received recognition result: ${result.studentName}, known=${result.isKnown}")
        
        val newState = if (result.isKnown) FaceState.RECOGNIZED else FaceState.UNKNOWN
        
        _uiState.update {
            it.copy(
                faceState = newState,
                recognitionResult = result,
                // 识别成功时增加计数
                recognizedCount = if (result.isKnown) it.recognizedCount + 1 else it.recognizedCount
            )
        }
        
        // 设置自动清除定时器
        resultClearJob?.cancel()
        resultClearJob = viewModelScope.launch {
            delay(RESULT_DISPLAY_DURATION)
            clearRecognitionResult()
        }
    }
    
    /**
     * 处理按键事件
     */
    fun onKeyEvent(keyType: KeyType) {
        Log.d(TAG, "Key event: $keyType")
        
        when (keyType) {
            KeyType.CLICK -> {
                // 单击：
                // - 手动模式下触发识别
                // - 自动模式下切换采集状态
                if (_uiState.value.captureMode == CaptureMode.MANUAL) {
                    triggerCapture()
                } else {
                    toggleCapture()
                }
            }
            KeyType.DOUBLE_CLICK -> {
                // 双击：切换准心显示
                toggleReticle()
            }
            KeyType.LONG_PRESS -> {
                // 长按：切换模式
                toggleCaptureMode()
            }
            KeyType.AI_START -> {
                // AI按键：开始采集
                startCapture()
            }
            else -> {}
        }
    }
    
    /**
     * 切换采集状态
     */
    private fun toggleCapture() {
        if (_uiState.value.isRecording) {
            stopCapture()
        } else {
            startCapture()
        }
    }
    
    /**
     * 处理滑动事件
     */
    fun onSwipe(direction: SwipeDirection) {
        Log.d(TAG, "Swipe: $direction")
        // 可用于切换显示信息页等功能
    }
    
    /**
     * 切换准心显示
     */
    private fun toggleReticle() {
        val newValue = !_uiState.value.showReticle
        _uiState.update { it.copy(showReticle = newValue) }
        showToast(if (newValue) "准心已开启" else "准心已关闭")
    }
    
    /**
     * 初始化相机
     * 
     * @param context Android 上下文
     * @param lifecycleOwner 生命周期所有者
     */
    fun initCamera(context: Context, lifecycleOwner: LifecycleOwner) {
        if (glassesCamera != null) {
            Log.d(TAG, "Camera already initialized")
            return
        }
        
        Log.d(TAG, "Initializing camera...")
        glassesCamera = GlassesCamera(context).apply {
            initialize(lifecycleOwner) { imageData, width, height ->
                onImageCaptured(imageData, width, height)
            }
        }
        
        // 监听相机初始化状态
        viewModelScope.launch {
            glassesCamera?.isInitialized?.collect { initialized ->
                val wasInitialized = _isCameraInitialized.value
                _isCameraInitialized.value = initialized
                if (initialized) {
                    showToast("相机已就绪")
                    // 相机就绪后，如果已配对且未开始采集，自动开始
                    if (!wasInitialized && _uiState.value.isPaired && !_uiState.value.isRecording) {
                        startCapture()
                    }
                }
            }
        }
    }
    
    /**
     * 处理采集到的图像
     */
    private fun onImageCaptured(imageData: ByteArray, width: Int, height: Int) {
        Log.d(TAG, "Image captured: ${imageData.size} bytes, ${width}x${height}")
        
        // 直接检查 glassesBridge 的连接状态（避免竞态条件）
        if (!glassesBridge.isConnected.value) {
            Log.w(TAG, "Not connected (bridge.isConnected=false), cannot send image")
            return
        }
        
        // 更新状态为识别中，增加采集计数
        _uiState.update {
            it.copy(
                faceState = FaceState.RECOGNIZING,
                captureCount = it.captureCount + 1
            )
        }
        
        // 发送识别请求
        Log.d(TAG, "Sending recognition request to phone...")
        glassesBridge.sendRecognitionRequest(imageData)
    }
    
    /**
     * 开始采集
     */
    fun startCapture() {
        val camera = glassesCamera
        if (camera == null) {
            showToast("相机未初始化")
            return
        }
        
        if (!_isCameraInitialized.value) {
            showToast("相机正在初始化...")
            return
        }
        
        // 根据当前模式设置相机
        camera.setAutoCapture(_uiState.value.captureMode == CaptureMode.AUTO)
        camera.startCapture()
        
        _uiState.update {
            it.copy(isRecording = true)
        }
        
        showToast(if (_uiState.value.captureMode == CaptureMode.AUTO) "自动采集已开始" else "手动采集模式")
    }
    
    /**
     * 停止采集
     */
    fun stopCapture() {
        glassesCamera?.stopCapture()
        
        _uiState.update {
            it.copy(isRecording = false)
        }
        
        showToast("采集已停止")
    }
    
    /**
     * 触发手动抓拍（用于手动模式）
     */
    fun triggerCapture() {
        val camera = glassesCamera
        if (camera == null) {
            showToast("相机未初始化")
            return
        }
        
        if (!_isCameraInitialized.value) {
            showToast("相机正在初始化...")
            return
        }
        
        if (!_uiState.value.isConnected) {
            showToast("未连接手机")
            return
        }
        
        // 手动触发一次采集
        camera.captureOnce()
        showToast("正在识别...")
    }
    
    /**
     * 设置采集间隔
     */
    fun setCaptureInterval(intervalMs: Long) {
        glassesCamera?.setCaptureInterval(intervalMs)
    }
    
    /**
     * 更新人脸检测状态
     */
    fun updateFaceDetectionState(detected: Boolean) {
        if (_uiState.value.faceState == FaceState.RECOGNIZED ||
            _uiState.value.faceState == FaceState.UNKNOWN) {
            return  // 保持识别结果显示
        }
        
        _uiState.update {
            it.copy(faceState = if (detected) FaceState.DETECTING else FaceState.NONE)
        }
    }
    
    /**
     * 切换采集模式
     */
    private fun toggleCaptureMode() {
        val newMode = when (_uiState.value.captureMode) {
            CaptureMode.AUTO -> CaptureMode.MANUAL
            CaptureMode.MANUAL -> CaptureMode.AUTO
        }
        
        _uiState.update { it.copy(captureMode = newMode) }
        
        // 更新相机模式
        glassesCamera?.setAutoCapture(newMode == CaptureMode.AUTO)
        
        showToast(if (newMode == CaptureMode.AUTO) "自动采集模式" else "手动采集模式")
    }
    
    /**
     * 重置状态
     */
    fun resetState() {
        resultClearJob?.cancel()
        glassesBridge.clearResult()
        
        _uiState.update {
            it.copy(
                faceState = FaceState.NONE,
                recognitionResult = null
            )
        }
        
        showToast("已重置")
    }
    
    /**
     * 清除识别结果
     */
    private fun clearRecognitionResult() {
        glassesBridge.clearResult()
        _uiState.update {
            it.copy(
                faceState = FaceState.NONE,
                recognitionResult = null
            )
        }
    }
    
    /**
     * 显示提示消息
     */
    private fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
        
        toastClearJob?.cancel()
        toastClearJob = viewModelScope.launch {
            delay(TOAST_DISPLAY_DURATION)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }
    
    /**
     * 更新录制状态
     */
    fun setRecording(recording: Boolean) {
        _uiState.update { it.copy(isRecording = recording) }
    }
    
    /**
     * 更新电量
     */
    fun updateBattery(level: Int) {
        _uiState.update { it.copy(batteryLevel = level) }
    }
    
    /**
     * 获取通信桥接器（供 Activity 使用）
     */
    fun getBridge(): GlassesBridge = glassesBridge
    
    /**
     * 释放相机资源
     */
    fun releaseCamera() {
        glassesCamera?.release()
        glassesCamera = null
        _isCameraInitialized.value = false
    }
    
    override fun onCleared() {
        super.onCleared()
        releaseCamera()
        glassesBridge.release()
    }
}
