package com.sustech.bojayL.rokid

import android.util.Log
import com.rokid.cxr.Caps
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.listeners.CustomCmdListener
import com.sustech.bojayL.data.model.Student
import com.sustech.bojayL.ml.SnpeFaceNetRecognizer
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Rokid 消息处理器（端侧识别版）
 */
class RokidMessageHandler {

    companion object {
        private const val TAG = "RokidMessageHandler"

        // Glass -> Phone
        const val KEY_GLASS_RESULT = "glass_result"
        const val KEY_GLASS_STATUS = "glass_status"
        const val KEY_GLASS_PAIRING_CODE = "glass_pairing_code"
        const val KEY_PAIRING_CONFIRMED = "pairing_confirmed"

        // Phone -> Glass
        const val KEY_PHONE_TEMPLATE_SYNC = "phone_template_sync"
        const val KEY_PHONE_CONFIG = "phone_config"
        const val KEY_PHONE_VERIFY_CODE = "phone_verify_code"

        private const val TEMPLATE_EMBEDDING_DIM = 256
    }

    private val _glassRecognitionResults = MutableSharedFlow<GlassRecognitionPayload>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val glassRecognitionResults: SharedFlow<GlassRecognitionPayload> = _glassRecognitionResults.asSharedFlow()

    private val _glassStatus = MutableSharedFlow<GlassStatus>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val glassStatus: SharedFlow<GlassStatus> = _glassStatus.asSharedFlow()

    private val _glassPairingCode = MutableSharedFlow<GlassPairingInfo>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val glassPairingCode: SharedFlow<GlassPairingInfo> = _glassPairingCode.asSharedFlow()

    private val _pairingConfirmed = MutableSharedFlow<PairingConfirmation>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val pairingConfirmed: SharedFlow<PairingConfirmation> = _pairingConfirmed.asSharedFlow()

    private val customCmdListener = CustomCmdListener { cmdKey, caps ->
        Log.d(TAG, "Received cmd: key=$cmdKey size=${caps?.size() ?: 0}")
        when (cmdKey) {
            KEY_GLASS_RESULT -> caps?.let { parseGlassResult(it) }
            KEY_GLASS_STATUS -> caps?.let { parseGlassStatus(it) }
            KEY_GLASS_PAIRING_CODE -> caps?.let { parseGlassPairingCode(it) }
            KEY_PAIRING_CONFIRMED -> caps?.let { parsePairingConfirmation(it) }
            else -> {
                // 兼容某些固件下 key 异常回传
                caps?.let { parseBestEffort(it) }
            }
        }
    }

    fun startListening() {
        try {
            CxrApi.getInstance().setCustomCmdListener(customCmdListener)
            Log.d(TAG, "Message listener started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listener", e)
        }
    }

    fun stopListening() {
        CxrApi.getInstance().setCustomCmdListener(null)
    }

    /**
     * 下发端侧识别模板（分片发送，避免单包过大）。
     */
    fun sendFaceTemplates(students: List<Student>) {
        var skipped = 0
        val templates = students
            .filter { it.isEnrolled && !it.faceFeature.isNullOrEmpty() }
            .mapNotNull { student ->
                val feature = student.faceFeature ?: return@mapNotNull null
                val dim = student.faceFeatureDim ?: feature.size
                val model = student.faceFeatureModel

                // 严格约束：只同步 FaceNet-MobileNetV2 256d SNPE 模板，避免不同空间混用。
                val valid = dim == TEMPLATE_EMBEDDING_DIM &&
                        feature.size == TEMPLATE_EMBEDDING_DIM &&
                        model == SnpeFaceNetRecognizer.MODEL_ID
                if (!valid) {
                    skipped += 1
                    return@mapNotNull null
                }

                JSONObject().apply {
                    put("studentId", student.id)
                    put("studentName", student.name)
                    put("className", student.className)
                    put("tags", JSONArray(student.tags))
                    put("embedding", JSONArray(feature))
                    put("embeddingDim", TEMPLATE_EMBEDDING_DIM)
                    put("modelId", model)
                }
            }

        val root = JSONObject().apply {
            put("version", 1)
            put("embeddingDim", TEMPLATE_EMBEDDING_DIM)
            put("modelId", SnpeFaceNetRecognizer.MODEL_ID)
            put("templates", JSONArray(templates))
        }
        val payload = root.toString()
        val chunks = payload.chunked(3000)
        val syncId = UUID.randomUUID().toString()

        chunks.forEachIndexed { index, chunk ->
            val caps = Caps().apply {
                write(syncId)
                writeInt32(index)
                writeInt32(chunks.size)
                write(chunk)
                writeInt32(templates.size)
            }
            CxrApi.getInstance().sendCustomCmd(KEY_PHONE_TEMPLATE_SYNC, caps)
        }
        Log.i(TAG, "Template sync sent: id=$syncId templates=${templates.size} skipped=$skipped chunks=${chunks.size}")
    }

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
    }

    fun sendPairingCode(code: String) {
        val caps = Caps().apply { write(code) }
        CxrApi.getInstance().sendCustomCmd(KEY_PHONE_VERIFY_CODE, caps)
    }

    private fun parseBestEffort(caps: Caps) {
        try {
            parseGlassResult(caps)
            return
        } catch (_: Exception) {
        }
        try {
            parseGlassStatus(caps)
            return
        } catch (_: Exception) {
        }
    }

    private fun parseGlassResult(caps: Caps) {
        val isKnown = caps.at(0).int == 1
        val studentId = if (caps.size() > 1) caps.at(1).string else null
        val studentName = if (caps.size() > 2) caps.at(2).string else null
        val className = if (caps.size() > 3) caps.at(3).string else null
        val confidence = if (caps.size() > 4) {
            java.lang.Float.intBitsToFloat(caps.at(4).int)
        } else {
            0f
        }
        val tags = if (caps.size() > 5) {
            caps.at(5).string?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        } else {
            emptyList()
        }
        val timestamp = if (caps.size() > 6) caps.at(6).long else System.currentTimeMillis()
        val source = if (caps.size() > 7) caps.at(7).string ?: "edge" else "edge"

        _glassRecognitionResults.tryEmit(
            GlassRecognitionPayload(
                isKnown = isKnown,
                studentId = studentId,
                studentName = studentName,
                className = className,
                confidence = confidence,
                tags = tags,
                source = source,
                timestamp = timestamp
            )
        )
    }

    private fun parseGlassStatus(caps: Caps) {
        val battery = caps.at(0).int
        val mode = if (caps.size() > 1) caps.at(1).string else "unknown"
        val isConnected = if (caps.size() > 2) {
            try {
                caps.at(2).int == 1
            } catch (_: Exception) {
                caps.at(2).string == "true"
            }
        } else {
            true
        }
        _glassStatus.tryEmit(
            GlassStatus(
                battery = battery,
                mode = mode,
                isConnected = isConnected
            )
        )
    }

    private fun parseGlassPairingCode(caps: Caps) {
        val code = caps.at(0).string
        val deviceName = if (caps.size() > 1) caps.at(1).string else "Unknown"
        _glassPairingCode.tryEmit(
            GlassPairingInfo(
                code = code,
                deviceName = deviceName,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun parsePairingConfirmation(caps: Caps) {
        val success = caps.at(0).int == 1
        val deviceName = if (caps.size() > 1) caps.at(1).string else "Unknown"
        _pairingConfirmed.tryEmit(
            PairingConfirmation(
                success = success,
                deviceName = deviceName
            )
        )
    }
}

data class GlassRecognitionPayload(
    val isKnown: Boolean,
    val studentId: String?,
    val studentName: String?,
    val className: String?,
    val confidence: Float,
    val tags: List<String>,
    val source: String,
    val timestamp: Long
)

data class GlassPairingInfo(
    val code: String,
    val deviceName: String,
    val timestamp: Long
)

data class PairingConfirmation(
    val success: Boolean,
    val deviceName: String
)

data class GlassStatus(
    val battery: Int,
    val mode: String,
    val isConnected: Boolean
)
