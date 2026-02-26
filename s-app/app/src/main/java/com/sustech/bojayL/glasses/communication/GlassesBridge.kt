package com.sustech.bojayL.glasses.communication

import android.os.Build
import android.util.Log
import com.rokid.cxr.CXRServiceBridge
import com.rokid.cxr.Caps
import com.sustech.bojayL.glasses.ml.FaceTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * 眼镜端通信桥接器
 *
 * 新链路：
 * - 手机 -> 眼镜：参数配置 + 模板同步
 * - 眼镜 -> 手机：端侧识别结果 + 状态
 */
class GlassesBridge {

    companion object {
        private const val TAG = "GlassesBridge"
        private const val PAIRING_CODE_LENGTH = 6
    }

    private val cxrServiceBridge = CXRServiceBridge()

    @Volatile
    private var isSubscribed = false

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _pairingCode = MutableStateFlow(generatePairingCode())
    val pairingCode: StateFlow<String> = _pairingCode.asStateFlow()

    private val _recognitionResult = MutableStateFlow<RecognitionResult?>(null)
    val recognitionResult: StateFlow<RecognitionResult?> = _recognitionResult.asStateFlow()

    private val _config = MutableStateFlow(GlassesConfig())
    val config: StateFlow<GlassesConfig> = _config.asStateFlow()

    private val _faceTemplates = MutableStateFlow<List<FaceTemplate>>(emptyList())
    val faceTemplates: StateFlow<List<FaceTemplate>> = _faceTemplates.asStateFlow()

    private val pendingTemplateChunks = mutableMapOf<String, MutableMap<Int, String>>()
    private val expectedChunkCounts = mutableMapOf<String, Int>()

    private val statusListener = object : CXRServiceBridge.StatusListener {
        override fun onConnected(uuid: String?, type: Int) {
            Log.d(TAG, "Connected: uuid=$uuid type=$type")
            _isConnected.value = true
            _isPaired.value = true
            subscribePhoneMessages()
        }

        override fun onDisconnected() {
            Log.d(TAG, "Disconnected")
            _isConnected.value = false
            _isPaired.value = false
            _recognitionResult.value = null
            isSubscribed = false
        }

        override fun onARTCStatus(p0: Float, p1: Boolean) = Unit
    }

    private val msgCallback = object : CXRServiceBridge.MsgCallback {
        override fun onReceive(name: String?, args: Caps?, bytes: ByteArray?) {
            if (args == null) return
            Log.d(TAG, "Received message: name=$name size=${args.size()}")
            when (name) {
                MessageProtocol.KEY_PHONE_CONFIG -> parseConfig(args)
                MessageProtocol.KEY_PHONE_TEMPLATE_SYNC -> parseTemplateSync(args)
                MessageProtocol.KEY_PHONE_RESULT -> parseRecognitionResult(args) // 兼容旧版
                MessageProtocol.KEY_PHONE_VERIFY_CODE -> verifyPairingCode(args)
                MessageProtocol.KEY_SUBSCRIBE_PHONE,
                "rk_custom_key" -> parseBestEffort(args)
                else -> parseBestEffort(args)
            }
        }
    }

    fun init() {
        _isConnected.value = false
        _isPaired.value = false
        _recognitionResult.value = null
        isSubscribed = false

        cxrServiceBridge.setStatusListener(statusListener)
        subscribePhoneMessages()
        Log.d(TAG, "Bridge initialized")
    }

    private fun subscribePhoneMessages() {
        if (isSubscribed) return
        val result = cxrServiceBridge.subscribe(MessageProtocol.KEY_SUBSCRIBE_PHONE, msgCallback)
        if (result == 0) {
            isSubscribed = true
            Log.d(TAG, "Subscribed to ${MessageProtocol.KEY_SUBSCRIBE_PHONE}")
        } else {
            Log.e(TAG, "Subscribe failed: $result")
        }
    }

    fun regeneratePairingCode() {
        _pairingCode.value = generatePairingCode()
        _isPaired.value = false
    }

    fun broadcastPairingCode() {
        if (!_isConnected.value) return
        val caps = Caps().apply {
            write(_pairingCode.value)
            write(Build.MODEL)
        }
        cxrServiceBridge.sendMessage(MessageProtocol.KEY_GLASS_PAIRING_CODE, caps)
    }

    fun sendStatusSync(battery: Int, mode: String) {
        val caps = Caps().apply {
            writeInt32(battery)
            write(mode)
            write(_isConnected.value)
        }
        cxrServiceBridge.sendMessage(MessageProtocol.KEY_GLASS_STATUS, caps)
    }

    /**
     * 眼镜端识别结果上报（端侧识别）。
     */
    fun sendEdgeRecognitionResult(result: RecognitionResult) {
        if (!_isConnected.value) {
            Log.w(TAG, "Not connected, skip result upload")
            return
        }
        val caps = Caps().apply {
            writeInt32(if (result.isKnown) 1 else 0)
            write(result.studentId ?: "")
            write(result.studentName ?: "")
            write(result.className ?: "")
            writeInt32(java.lang.Float.floatToIntBits(result.confidence))
            write(result.tags.joinToString(","))
            writeInt64(result.timestamp)
            write(result.source)
        }

        val code = cxrServiceBridge.sendMessage(MessageProtocol.KEY_GLASS_RESULT, caps)
        Log.d(TAG, "Send edge result code=$code known=${result.isKnown} name=${result.studentName}")
    }

    fun clearResult() {
        _recognitionResult.value = null
    }

    fun release() {
        cxrServiceBridge.setStatusListener(null)
        _isPaired.value = false
        isSubscribed = false
        pendingTemplateChunks.clear()
        expectedChunkCounts.clear()
    }

    fun getPairingStatus(): PairingStatus {
        return PairingStatus(
            code = _pairingCode.value,
            isPaired = _isPaired.value,
            isConnected = _isConnected.value,
            deviceName = Build.MODEL
        )
    }

    private fun parseBestEffort(caps: Caps) {
        try {
            if (caps.size() >= 4) {
                // 优先按模板分片解析
                val possibleSyncId = caps.at(0).string
                val possibleChunk = caps.at(1).int
                val possibleTotal = caps.at(2).int
                val possiblePayload = caps.at(3).string
                if (!possibleSyncId.isNullOrEmpty() &&
                    possibleChunk >= 0 &&
                    possibleTotal > 0 &&
                    !possiblePayload.isNullOrEmpty()
                ) {
                    parseTemplateSync(caps)
                    return
                }
            }
        } catch (_: Exception) {
            // ignore and continue
        }

        try {
            parseRecognitionResult(caps)
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun parseRecognitionResult(caps: Caps) {
        try {
            val isKnown = caps.at(0).int == 1
            val studentId = if (caps.size() > 1) caps.at(1).string else null
            val studentName = if (caps.size() > 2) caps.at(2).string else null
            val className = if (caps.size() > 3) caps.at(3).string else null
            val confidenceBits = if (caps.size() > 4) caps.at(4).int else 0
            val confidence = java.lang.Float.intBitsToFloat(confidenceBits)
            val tags = if (caps.size() > 5) {
                caps.at(5).string?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            } else {
                emptyList()
            }
            val timestamp = if (caps.size() > 6) caps.at(6).long else System.currentTimeMillis()
            val source = if (caps.size() > 7) caps.at(7).string ?: "phone" else "phone"

            _recognitionResult.value = RecognitionResult(
                studentId = studentId,
                studentName = studentName,
                className = className,
                confidence = confidence,
                tags = tags,
                isKnown = isKnown,
                source = source,
                timestamp = timestamp
            )
        } catch (e: Exception) {
            Log.w(TAG, "Parse recognition result failed", e)
        }
    }

    private fun parseTemplateSync(caps: Caps) {
        try {
            val syncId = caps.at(0).string ?: return
            val chunkIndex = caps.at(1).int
            val totalChunks = caps.at(2).int
            val payload = caps.at(3).string ?: return
            val templateCount = if (caps.size() > 4) caps.at(4).int else -1

            val chunkMap = pendingTemplateChunks.getOrPut(syncId) { mutableMapOf() }
            chunkMap[chunkIndex] = payload
            expectedChunkCounts[syncId] = totalChunks

            Log.d(TAG, "Template chunk: syncId=$syncId $chunkIndex/$totalChunks")

            if (chunkMap.size >= totalChunks) {
                val combined = StringBuilder()
                for (i in 0 until totalChunks) {
                    combined.append(chunkMap[i] ?: "")
                }
                val templates = parseTemplatePayload(combined.toString())
                _faceTemplates.value = templates
                Log.i(
                    TAG,
                    "Template sync completed: syncId=$syncId parsed=${templates.size} expected=$templateCount"
                )
                pendingTemplateChunks.remove(syncId)
                expectedChunkCounts.remove(syncId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse template sync", e)
        }
    }

    private fun parseTemplatePayload(json: String): List<FaceTemplate> {
        val root = JSONObject(json)
        val rootDim = root.optInt("embeddingDim", 256)
        val rootModelId = root.optString("modelId", "").ifBlank { null }
        val templates = root.optJSONArray("templates") ?: JSONArray()
        val result = ArrayList<FaceTemplate>(templates.length())
        for (i in 0 until templates.length()) {
            val obj = templates.optJSONObject(i) ?: continue
            val studentId = obj.optString("studentId", "")
            if (studentId.isBlank()) continue
            val studentName = obj.optString("studentName", "")
            val className = obj.optString("className", "")
            val tagsArray = obj.optJSONArray("tags") ?: JSONArray()
            val tags = mutableListOf<String>()
            for (t in 0 until tagsArray.length()) {
                tags += tagsArray.optString(t, "")
            }
            val embArray = obj.optJSONArray("embedding") ?: JSONArray()
            val embedding = FloatArray(embArray.length()) { idx ->
                embArray.optDouble(idx, 0.0).toFloat()
            }
            val dim = obj.optInt("embeddingDim", rootDim)
            val modelId = obj.optString("modelId", rootModelId ?: "").ifBlank { rootModelId }
            if (dim != 256 || embedding.size != 256) {
                Log.w(TAG, "Skip template $studentId due to dim mismatch: dim=$dim size=${embedding.size}")
                continue
            }
            result += FaceTemplate(
                studentId = studentId,
                studentName = studentName,
                className = className,
                tags = tags,
                modelId = modelId,
                embedding = embedding
            )
        }
        return result
    }

    private fun parseConfig(caps: Caps) {
        try {
            val thresholdBits = if (caps.size() > 0) caps.at(0).int else java.lang.Float.floatToIntBits(0.7f)
            val threshold = java.lang.Float.intBitsToFloat(thresholdBits)
            val interval = if (caps.size() > 1) caps.at(1).int.toLong() else 2000L
            val brightness = if (caps.size() > 2) caps.at(2).int else 80
            val showReticle = if (caps.size() > 3) caps.at(3).int == 1 else true
            _config.value = GlassesConfig(
                recognitionThreshold = threshold,
                captureIntervalMs = interval,
                displayBrightness = brightness,
                showReticle = showReticle
            )
            Log.d(TAG, "Config updated: threshold=$threshold interval=$interval showReticle=$showReticle")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse config", e)
        }
    }

    private fun verifyPairingCode(caps: Caps) {
        try {
            val receivedCode = caps.at(0).string
            val success = receivedCode == _pairingCode.value
            _isPaired.value = success
            val resp = Caps().apply {
                writeInt32(if (success) 1 else 0)
                write(Build.MODEL)
            }
            cxrServiceBridge.sendMessage(MessageProtocol.KEY_PAIRING_CONFIRMED, resp)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify pairing code", e)
        }
    }

    private fun generatePairingCode(): String {
        return (1..PAIRING_CODE_LENGTH)
            .map { Random.nextInt(0, 10) }
            .joinToString("")
    }
}

data class PairingStatus(
    val code: String,
    val isPaired: Boolean,
    val isConnected: Boolean,
    val deviceName: String
)

data class GlassesConfig(
    val recognitionThreshold: Float = 0.7f,
    val captureIntervalMs: Long = 2000L,
    val displayBrightness: Int = 80,
    val showReticle: Boolean = true
)
