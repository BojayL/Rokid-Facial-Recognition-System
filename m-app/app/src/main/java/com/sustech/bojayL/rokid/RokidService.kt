package com.sustech.bojayL.rokid

import android.content.Context
import android.util.Log
import com.sustech.bojayL.data.model.Student
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Rokid 服务（端侧识别模式）
 *
 * 手机端职责：
 * - 设备连接
 * - 模板同步 / 参数下发
 * - 展示眼镜上报识别结果
 */
class RokidService(private val context: Context) {

    companion object {
        private const val TAG = "RokidService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val rokidManager = RokidManager(context)
    val messageHandler = RokidMessageHandler()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    val connectionState: StateFlow<RokidConnectionState> = rokidManager.connectionState
    val scannedDevices: StateFlow<List<ScannedDevice>> = rokidManager.scannedDevices
    val isScanning: StateFlow<Boolean> = rokidManager.isScanning
    val glassInfo: StateFlow<GlassInfo?> = rokidManager.glassInfo

    private val _recognitionResults = MutableSharedFlow<GlassRecognitionResult>()
    val recognitionResults: SharedFlow<GlassRecognitionResult> = _recognitionResults.asSharedFlow()

    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _glassPairingInfo = MutableStateFlow<GlassPairingInfo?>(null)
    val glassPairingInfo: StateFlow<GlassPairingInfo?> = _glassPairingInfo.asStateFlow()

    private var connectionObserverJob: Job? = null
    private var messageListenerJob: Job? = null
    private var isListening = false

    fun initialize(snResourceId: Int? = null) {
        snResourceId?.let { rokidManager.initSnFile(it) }
        _isInitialized.value = true
    }

    fun startListening() {
        if (connectionState.value.status != ConnectionStatus.CONNECTED) {
            Log.w(TAG, "Cannot start listening: disconnected")
            return
        }
        if (isListening) return

        isListening = true
        messageHandler.startListening()
        messageListenerJob?.cancel()

        messageListenerJob = serviceScope.launch {
            launch {
                messageHandler.glassRecognitionResults.collect { payload ->
                    _recognitionResults.emit(
                        GlassRecognitionResult(
                            id = UUID.randomUUID().toString(),
                            isKnown = payload.isKnown,
                            studentId = payload.studentId,
                            studentName = payload.studentName,
                            className = payload.className,
                            tags = payload.tags,
                            confidence = payload.confidence,
                            source = payload.source,
                            timestamp = payload.timestamp
                        )
                    )
                }
            }

            launch {
                messageHandler.glassStatus.collect { status ->
                    Log.d(TAG, "Glass status: battery=${status.battery}, mode=${status.mode}")
                }
            }

            launch {
                messageHandler.glassPairingCode.collect { info ->
                    _glassPairingInfo.value = info
                }
            }

            launch {
                messageHandler.pairingConfirmed.collect { confirmation ->
                    _isPaired.value = confirmation.success
                }
            }
        }
    }

    fun stopListening() {
        isListening = false
        messageListenerJob?.cancel()
        messageListenerJob = null
        messageHandler.stopListening()
    }

    fun verifyPairingCode(code: String) {
        if (connectionState.value.status != ConnectionStatus.CONNECTED) return
        messageHandler.sendPairingCode(code)
    }

    fun resetPairingState() {
        _isPaired.value = false
        _glassPairingInfo.value = null
    }

    fun sendConfig(
        threshold: Float,
        intervalMs: Long,
        brightness: Int,
        showReticle: Boolean = true
    ) {
        if (connectionState.value.status != ConnectionStatus.CONNECTED) {
            Log.w(TAG, "Cannot send config: disconnected")
            return
        }
        messageHandler.sendConfig(threshold, intervalMs, brightness, showReticle)
    }

    fun syncFaceTemplates(students: List<Student>) {
        if (connectionState.value.status != ConnectionStatus.CONNECTED) {
            Log.d(TAG, "Skip template sync: disconnected")
            return
        }
        messageHandler.sendFaceTemplates(students)
    }

    fun startScan() {
        rokidManager.startScan()
    }

    fun stopScan() {
        rokidManager.stopScan()
    }

    fun connect(device: ScannedDevice) {
        connectionObserverJob?.cancel()
        rokidManager.connect(device)

        connectionObserverJob = serviceScope.launch {
            connectionState.collect { state ->
                when (state.status) {
                    ConnectionStatus.CONNECTED -> {
                        startListening()
                        _isPaired.value = true
                    }
                    ConnectionStatus.DISCONNECTED -> {
                        stopListening()
                        _isPaired.value = false
                    }
                    else -> Unit
                }
            }
        }
    }

    fun disconnect() {
        stopListening()
        rokidManager.disconnect()
    }

    fun release() {
        disconnect()
        connectionObserverJob?.cancel()
        connectionObserverJob = null
        _isInitialized.value = false
    }
}

data class GlassRecognitionResult(
    val id: String,
    val isKnown: Boolean,
    val studentId: String?,
    val studentName: String?,
    val className: String?,
    val tags: List<String>,
    val confidence: Float,
    val source: String,
    val timestamp: Long
)
