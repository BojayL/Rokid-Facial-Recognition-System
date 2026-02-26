package com.sustech.bojayL.glasses.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.sustech.bojayL.glasses.communication.RecognitionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 眼镜端识别引擎
 *
 * 主链路：
 * - 检测：BlazeFace（ML Kit）
 * - 特征：FaceNet-MobileNetV2 256d（SNPE INT8，若不可用回退 NCNN）
 * - 比对：余弦相似度
 */
class EdgeFaceRecognitionEngine(
    private val context: Context
) {

    companion object {
        private const val TAG = "EdgeFaceRecEngine"
        private const val DEFAULT_THRESHOLD = 0.7f
    }

    data class EngineResult(
        val faceDetected: Boolean,
        val recognition: RecognitionResult? = null
    )

    private val blazeFaceDetector = BlazeFaceDetector()
    private val snpeRecognizer = SnpeFaceNetRecognizer()

    private var snpeReady = false
    private var ncnnReady = false
    private var scrfdReady = false
    private var threshold = DEFAULT_THRESHOLD

    @Volatile
    private var templates: List<FaceTemplate> = emptyList()

    suspend fun initialize(): Boolean = withContext(Dispatchers.Default) {
        snpeReady = snpeRecognizer.init(context)
        ncnnReady = NcnnMobileFaceRecognizer.init(context, useGpu = false)
        scrfdReady = GlassesFaceDetector.init(context, useGpu = false)
        Log.i(TAG, "Engine init done: snpe=$snpeReady, ncnn=$ncnnReady, scrfd=$scrfdReady")
        snpeReady || ncnnReady
    }

    fun setThreshold(value: Float) {
        threshold = value.coerceIn(0f, 1f)
    }

    fun updateTemplates(newTemplates: List<FaceTemplate>) {
        templates = newTemplates
            .mapNotNull { template ->
                if (template.embedding.size != EmbeddingUtils.TARGET_DIMENSION) {
                    Log.w(
                        TAG,
                        "Skip template ${template.studentId}: embeddingDim=${template.embedding.size}"
                    )
                    return@mapNotNull null
                }
                if (!template.modelId.isNullOrBlank() &&
                    template.modelId != SnpeFaceNetRecognizer.MODEL_ID
                ) {
                    Log.w(
                        TAG,
                        "Skip template ${template.studentId}: modelId=${template.modelId}"
                    )
                    return@mapNotNull null
                }
                template.copy(embedding = EmbeddingUtils.l2Normalize(template.embedding.copyOf()))
            }
        Log.i(TAG, "Templates updated: ${templates.size}")
    }

    suspend fun recognize(frameBitmap: Bitmap): EngineResult = withContext(Dispatchers.Default) {
        val detection = detectFace(frameBitmap)
        if (detection == null) {
            return@withContext EngineResult(faceDetected = false, recognition = null)
        }

        val aligned = FaceAligner.align(frameBitmap, detection.landmarks)
            ?: return@withContext EngineResult(faceDetected = true, recognition = unknownResult(0f, "align_failed"))

        try {
            val (embedding, source) = extractEmbedding(aligned)
                ?: return@withContext EngineResult(faceDetected = true, recognition = unknownResult(0f, "feature_failed"))

            val match = matchTemplate(embedding)
            if (match == null) {
                return@withContext EngineResult(
                    faceDetected = true,
                    recognition = unknownResult(0f, source)
                )
            }

            val (template, score) = match
            val recognized = score >= threshold
            val result = if (recognized) {
                RecognitionResult(
                    studentId = template.studentId,
                    studentName = template.studentName,
                    className = template.className,
                    confidence = score,
                    tags = template.tags,
                    isKnown = true,
                    source = source
                )
            } else {
                unknownResult(score, source)
            }
            EngineResult(faceDetected = true, recognition = result)
        } finally {
            if (!aligned.isRecycled) {
                aligned.recycle()
            }
        }
    }

    fun release() {
        blazeFaceDetector.close()
        snpeRecognizer.release()
        NcnnMobileFaceRecognizer.release()
        GlassesFaceDetector.release()
        snpeReady = false
        ncnnReady = false
        scrfdReady = false
    }

    private suspend fun detectFace(bitmap: Bitmap): BlazeFaceDetector.Detection? {
        val blaze = blazeFaceDetector.detect(bitmap)
        if (blaze != null) {
            return blaze
        }

        if (!scrfdReady) return null
        val scrfdFaces = GlassesFaceDetector.detect(bitmap)
        val best = scrfdFaces.maxByOrNull { it.confidence } ?: return null
        return BlazeFaceDetector.Detection(
            rect = RectF(best.rect),
            confidence = best.confidence,
            landmarks = best.landmarks
        )
    }

    private suspend fun extractEmbedding(aligned: Bitmap): Pair<FloatArray, String>? {
        if (snpeReady) {
            val snpeFeature = snpeRecognizer.extractFeature(aligned)
            if (snpeFeature != null) {
                return EmbeddingUtils.toNormalized256(snpeFeature) to "edge-snpe-int8"
            }
            snpeReady = false
        }

        if (ncnnReady) {
            val fallback = NcnnMobileFaceRecognizer.extractFeature(aligned)
            if (fallback != null) {
                return EmbeddingUtils.toNormalized256(fallback) to "edge-ncnn-fallback"
            }
        }

        return null
    }

    private fun matchTemplate(embedding: FloatArray): Pair<FaceTemplate, Float>? {
        if (templates.isEmpty()) return null
        var best: FaceTemplate? = null
        var bestScore = -1f
        for (template in templates) {
            val score = EmbeddingUtils.cosineSimilarity(embedding, template.embedding)
            if (score > bestScore) {
                bestScore = score
                best = template
            }
        }
        return if (best != null) best to bestScore else null
    }

    private fun unknownResult(confidence: Float, source: String): RecognitionResult {
        return RecognitionResult(
            studentId = null,
            studentName = null,
            className = null,
            confidence = confidence,
            tags = emptyList(),
            isKnown = false,
            source = source
        )
    }
}
