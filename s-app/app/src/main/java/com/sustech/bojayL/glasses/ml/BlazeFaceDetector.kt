package com.sustech.bojayL.glasses.ml

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * BlazeFace 检测器（ML Kit 实现）
 */
class BlazeFaceDetector {

    companion object {
        private const val TAG = "BlazeFaceDetector"
    }

    data class Detection(
        val rect: RectF,
        val confidence: Float,
        val landmarks: FloatArray // [x1,y1,...,x5,y5]
    )

    private val detector: FaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    suspend fun detect(bitmap: Bitmap): Detection? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = detector.process(image).await()
            val bestFace = faces.maxByOrNull { area(it.boundingBox) } ?: return null
            val landmarks = extractFivePointLandmarks(bestFace, bestFace.boundingBox)
            Detection(
                rect = RectF(bestFace.boundingBox),
                confidence = 1f,
                landmarks = landmarks
            )
        } catch (e: Exception) {
            Log.e(TAG, "BlazeFace detection failed", e)
            null
        }
    }

    fun close() {
        try {
            detector.close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close detector", e)
        }
    }

    private fun extractFivePointLandmarks(face: Face, rect: Rect): FloatArray {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position

        val fallbackLeftEye = PointF(rect.left + rect.width() * 0.32f, rect.top + rect.height() * 0.38f)
        val fallbackRightEye = PointF(rect.left + rect.width() * 0.68f, rect.top + rect.height() * 0.38f)
        val fallbackNose = PointF(rect.left + rect.width() * 0.5f, rect.top + rect.height() * 0.56f)
        val fallbackMouthLeft = PointF(rect.left + rect.width() * 0.36f, rect.top + rect.height() * 0.74f)
        val fallbackMouthRight = PointF(rect.left + rect.width() * 0.64f, rect.top + rect.height() * 0.74f)

        val le = leftEye ?: fallbackLeftEye
        val re = rightEye ?: fallbackRightEye
        val n = nose ?: fallbackNose
        val ml = mouthLeft ?: fallbackMouthLeft
        val mr = mouthRight ?: fallbackMouthRight

        return floatArrayOf(
            le.x, le.y,
            re.x, re.y,
            n.x, n.y,
            ml.x, ml.y,
            mr.x, mr.y
        )
    }

    private fun area(rect: Rect): Int = rect.width() * rect.height()

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
        addOnCanceledListener {
            if (cont.isActive) {
                cont.cancel()
            }
        }
    }
}
