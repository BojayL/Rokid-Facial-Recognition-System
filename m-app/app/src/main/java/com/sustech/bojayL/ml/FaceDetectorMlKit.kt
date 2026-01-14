package com.sustech.bojayL.ml

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object FaceDetectorMlKit {
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()

    private val detector by lazy { FaceDetection.getClient(options) }

    suspend fun hasFace(context: Context, imageUri: Uri): Boolean = suspendCoroutine { cont ->
        try {
            val image = InputImage.fromFilePath(context, imageUri)
            detector.process(image)
                .addOnSuccessListener { faces -> cont.resume(faces.isNotEmpty()) }
                .addOnFailureListener { _ -> cont.resume(false) }
        } catch (e: Exception) {
            cont.resume(false)
        }
    }
}
