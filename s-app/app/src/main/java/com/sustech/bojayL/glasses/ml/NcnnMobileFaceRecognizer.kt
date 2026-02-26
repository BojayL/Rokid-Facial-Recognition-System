package com.sustech.bojayL.glasses.ml

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NCNN MobileFaceNet 回退识别器。
 */
object NcnnMobileFaceRecognizer {

    private const val TAG = "NcnnMobileFaceRec"
    private const val DEFAULT_MODEL_TYPE = "mobilefacenet"

    private var nativeLoaded = false
    private var initialized = false

    suspend fun init(
        context: Context,
        modelType: String = DEFAULT_MODEL_TYPE,
        useGpu: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        if (initialized && nativeIsInitialized()) return@withContext true
        if (!loadNativeLibrary()) return@withContext false
        return@withContext try {
            val ok = nativeInit(context.assets, modelType, useGpu)
            initialized = ok
            if (ok) {
                Log.i(TAG, "Initialized fallback recognizer: $modelType")
            } else {
                Log.e(TAG, "Failed to initialize fallback recognizer")
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
            false
        }
    }

    suspend fun extractFeature(face112: Bitmap): FloatArray? = withContext(Dispatchers.IO) {
        if (!initialized || !nativeIsInitialized()) return@withContext null
        if (face112.width != 112 || face112.height != 112) return@withContext null
        return@withContext try {
            nativeExtractFeature(face112)
        } catch (e: Exception) {
            Log.e(TAG, "Extract feature failed", e)
            null
        }
    }

    fun cosineSimilarity(lhs: FloatArray, rhs: FloatArray): Float {
        if (lhs.size != rhs.size) return -1f
        return try {
            nativeCosineSimilarity(lhs, rhs)
        } catch (e: Exception) {
            Log.e(TAG, "Cosine failed", e)
            -1f
        }
    }

    fun release() {
        if (!initialized) return
        try {
            nativeRelease()
        } catch (e: Exception) {
            Log.e(TAG, "Release failed", e)
        } finally {
            initialized = false
        }
    }

    private fun loadNativeLibrary(): Boolean {
        if (nativeLoaded) return true
        return try {
            System.loadLibrary("scrfd_glasses_jni")
            nativeLoaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load JNI library", e)
            false
        }
    }

    private external fun nativeInit(
        assetManager: AssetManager,
        modelType: String,
        useGpu: Boolean
    ): Boolean

    private external fun nativeExtractFeature(faceBitmap: Bitmap): FloatArray?

    private external fun nativeCosineSimilarity(lhs: FloatArray, rhs: FloatArray): Float

    private external fun nativeRelease()

    private external fun nativeIsInitialized(): Boolean
}
