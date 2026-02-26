package com.sustech.bojayL.glasses.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sustech.bojayL.glasses.camera.GlassesCamera
import com.sustech.bojayL.glasses.communication.CaptureMode
import com.sustech.bojayL.glasses.communication.FaceState
import com.sustech.bojayL.glasses.communication.GlassesBridge
import com.sustech.bojayL.glasses.communication.RecognitionResult
import com.sustech.bojayL.glasses.input.KeyType
import com.sustech.bojayL.glasses.input.SwipeDirection
import com.sustech.bojayL.glasses.ml.EdgeFaceRecognitionEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HudUiState(
    val isConnected: Boolean = false,
    val batteryLevel: Int = 80,
    val isPaired: Boolean = false,
    val faceState: FaceState = FaceState.NONE,
    val recognitionResult: RecognitionResult? = null,
    val recognizedCount: Int = 0,
    val captureCount: Int = 0,
    val captureMode: CaptureMode = CaptureMode.AUTO,
    val isRecording: Boolean = false,
    val showReticle: Boolean = true,
    val toastMessage: String? = null
)

/**
 * HUD ViewModel
 *
 * 识别链路已迁移到眼镜端：
 * Camera -> BlazeFace -> FaceAlign -> FaceNet(SNPE/NCNN fallback) -> Cosine Match
 */
class HudViewModel : ViewModel() {

    companion object {
        private const val TAG = "HudViewModel"
        private const val RESULT_DISPLAY_DURATION = 3000L
        private const val TOAST_DISPLAY_DURATION = 2000L
    }

    private val glassesBridge = GlassesBridge()
    private var glassesCamera: GlassesCamera? = null
    private var edgeEngine: EdgeFaceRecognitionEngine? = null

    private val _isCameraInitialized = MutableStateFlow(false)
    val isCameraInitialized: StateFlow<Boolean> = _isCameraInitialized.asStateFlow()

    private val _uiState = MutableStateFlow(HudUiState())
    val uiState: StateFlow<HudUiState> = _uiState.asStateFlow()

    private var resultClearJob: Job? = null
    private var toastClearJob: Job? = null
    @Volatile
    private var isFrameProcessing = false

    private val recognizedStudentIds = mutableSetOf<String>()

    init {
        glassesBridge.init()
        observeBridgeState()
        observePairingState()
    }

    private fun observeBridgeState() {
        viewModelScope.launch {
            glassesBridge.isConnected.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
                showToast(if (connected) "已连接手机" else "连接已断开")
            }
        }

        // 兼容旧链路：若手机仍回传结果，HUD 仍可展示
        viewModelScope.launch {
            glassesBridge.recognitionResult.collect { result ->
                if (result != null) {
                    handleRecognitionResult(result)
                }
            }
        }

        viewModelScope.launch {
            glassesBridge.config.collect { config ->
                val oldReticle = _uiState.value.showReticle
                _uiState.update { it.copy(showReticle = config.showReticle) }
                glassesCamera?.setCaptureInterval(config.captureIntervalMs)
                edgeEngine?.setThreshold(config.recognitionThreshold)
                if (oldReticle != config.showReticle) {
                    showToast(if (config.showReticle) "准心已开启" else "准心已关闭")
                }
            }
        }

        viewModelScope.launch {
            glassesBridge.faceTemplates.collect { templates ->
                edgeEngine?.updateTemplates(templates)
                if (templates.isNotEmpty()) {
                    showToast("模板已同步 ${templates.size} 人")
                }
            }
        }
    }

    private fun observePairingState() {
        viewModelScope.launch {
            glassesBridge.isPaired.collect { paired ->
                val wasPaired = _uiState.value.isPaired
                _uiState.update { it.copy(isPaired = paired) }
                if (paired) {
                    showToast("已就绪")
                    if (!wasPaired && _isCameraInitialized.value && !_uiState.value.isRecording) {
                        startCapture()
                    }
                }
            }
        }
    }

    private fun handleRecognitionResult(result: RecognitionResult) {
        val newState = if (result.isKnown) FaceState.RECOGNIZED else FaceState.UNKNOWN
        val currentStudentId = result.studentId
        val shouldIncreaseCount = result.isKnown &&
                !currentStudentId.isNullOrBlank() &&
                recognizedStudentIds.add(currentStudentId)

        _uiState.update {
            it.copy(
                faceState = newState,
                recognitionResult = result,
                recognizedCount = if (shouldIncreaseCount) it.recognizedCount + 1 else it.recognizedCount
            )
        }

        resultClearJob?.cancel()
        resultClearJob = viewModelScope.launch {
            delay(RESULT_DISPLAY_DURATION)
            clearRecognitionResult()
        }
    }

    fun onKeyEvent(keyType: KeyType) {
        when (keyType) {
            KeyType.CLICK -> {
                if (_uiState.value.captureMode == CaptureMode.MANUAL) {
                    triggerCapture()
                } else {
                    toggleCapture()
                }
            }
            KeyType.DOUBLE_CLICK -> toggleReticle()
            KeyType.LONG_PRESS -> toggleCaptureMode()
            KeyType.AI_START -> startCapture()
            else -> Unit
        }
    }

    private fun toggleCapture() {
        if (_uiState.value.isRecording) stopCapture() else startCapture()
    }

    fun onSwipe(direction: SwipeDirection) {
        Log.d(TAG, "Swipe: $direction")
    }

    private fun toggleReticle() {
        val newValue = !_uiState.value.showReticle
        _uiState.update { it.copy(showReticle = newValue) }
        showToast(if (newValue) "准心已开启" else "准心已关闭")
    }

    fun initCamera(context: Context, lifecycleOwner: LifecycleOwner) {
        if (glassesCamera != null) {
            Log.d(TAG, "Camera already initialized")
            return
        }

        edgeEngine = EdgeFaceRecognitionEngine(context)
        viewModelScope.launch {
            val ok = edgeEngine?.initialize() == true
            Log.i(TAG, "Edge engine initialized: $ok")
        }

        glassesCamera = GlassesCamera(context).apply {
            initialize(lifecycleOwner) { bitmap ->
                onFrameCaptured(bitmap)
            }
        }

        viewModelScope.launch {
            glassesCamera?.isInitialized?.collect { initialized ->
                val wasInitialized = _isCameraInitialized.value
                _isCameraInitialized.value = initialized
                if (initialized) {
                    showToast("相机已就绪")
                    if (!wasInitialized && _uiState.value.isPaired && !_uiState.value.isRecording) {
                        startCapture()
                    }
                }
            }
        }
    }

    private fun onFrameCaptured(frame: Bitmap) {
        if (isFrameProcessing) {
            recycleQuietly(frame)
            return
        }
        isFrameProcessing = true

        _uiState.update {
            it.copy(
                faceState = FaceState.RECOGNIZING,
                captureCount = it.captureCount + 1
            )
        }

        viewModelScope.launch {
            try {
                val engine = edgeEngine
                if (engine == null) {
                    updateFaceDetectionState(false)
                    return@launch
                }

                val result = engine.recognize(frame)
                if (!result.faceDetected) {
                    updateFaceDetectionState(false)
                    return@launch
                }

                val recognition = result.recognition
                if (recognition != null) {
                    handleRecognitionResult(recognition)
                    // 同步端侧结果到手机端 UI
                    glassesBridge.sendEdgeRecognitionResult(recognition)
                } else {
                    updateFaceDetectionState(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Frame recognition failed", e)
                updateFaceDetectionState(false)
            } finally {
                recycleQuietly(frame)
                isFrameProcessing = false
            }
        }
    }

    fun startCapture() {
        val camera = glassesCamera ?: run {
            showToast("相机未初始化")
            return
        }
        if (!_isCameraInitialized.value) {
            showToast("相机正在初始化...")
            return
        }

        camera.setAutoCapture(_uiState.value.captureMode == CaptureMode.AUTO)
        camera.startCapture()
        _uiState.update { it.copy(isRecording = true) }
        showToast(if (_uiState.value.captureMode == CaptureMode.AUTO) "自动采集已开始" else "手动采集模式")
    }

    fun stopCapture() {
        glassesCamera?.stopCapture()
        _uiState.update { it.copy(isRecording = false) }
        showToast("采集已停止")
    }

    fun triggerCapture() {
        val camera = glassesCamera ?: run {
            showToast("相机未初始化")
            return
        }
        if (!_isCameraInitialized.value) {
            showToast("相机正在初始化...")
            return
        }
        camera.captureOnce()
        showToast("正在识别...")
    }

    fun setCaptureInterval(intervalMs: Long) {
        glassesCamera?.setCaptureInterval(intervalMs)
    }

    fun updateFaceDetectionState(detected: Boolean) {
        if (_uiState.value.faceState == FaceState.RECOGNIZED ||
            _uiState.value.faceState == FaceState.UNKNOWN
        ) {
            return
        }
        _uiState.update {
            it.copy(faceState = if (detected) FaceState.DETECTING else FaceState.NONE)
        }
    }

    private fun toggleCaptureMode() {
        val newMode = when (_uiState.value.captureMode) {
            CaptureMode.AUTO -> CaptureMode.MANUAL
            CaptureMode.MANUAL -> CaptureMode.AUTO
        }
        _uiState.update { it.copy(captureMode = newMode) }
        glassesCamera?.setAutoCapture(newMode == CaptureMode.AUTO)
        showToast(if (newMode == CaptureMode.AUTO) "自动采集模式" else "手动采集模式")
    }

    fun resetState() {
        resultClearJob?.cancel()
        glassesBridge.clearResult()
        recognizedStudentIds.clear()
        _uiState.update {
            it.copy(
                faceState = FaceState.NONE,
                recognitionResult = null,
                recognizedCount = 0
            )
        }
        showToast("已重置")
    }

    private fun clearRecognitionResult() {
        glassesBridge.clearResult()
        _uiState.update {
            it.copy(
                faceState = FaceState.NONE,
                recognitionResult = null
            )
        }
    }

    private fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
        toastClearJob?.cancel()
        toastClearJob = viewModelScope.launch {
            delay(TOAST_DISPLAY_DURATION)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    fun setRecording(recording: Boolean) {
        _uiState.update { it.copy(isRecording = recording) }
    }

    fun updateBattery(level: Int) {
        _uiState.update { it.copy(batteryLevel = level) }
    }

    fun getBridge(): GlassesBridge = glassesBridge

    fun releaseCamera() {
        glassesCamera?.release()
        glassesCamera = null
        edgeEngine?.release()
        edgeEngine = null
        _isCameraInitialized.value = false
    }

    override fun onCleared() {
        super.onCleared()
        releaseCamera()
        glassesBridge.release()
    }

    private fun recycleQuietly(bitmap: Bitmap) {
        try {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        } catch (_: Exception) {
        }
    }
}
