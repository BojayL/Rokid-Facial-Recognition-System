package com.sustech.bojayL.glasses.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 5 点人脸对齐（112x112）。
 */
object FaceAligner {

    private const val TAG = "FaceAligner"
    const val FACE_SIZE = 112

    // InsightFace 常用 112x112 模板点位
    private val TARGET = floatArrayOf(
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        56.0252f, 71.7366f,
        41.5493f, 92.3655f,
        70.7299f, 92.2041f
    )

    fun align(bitmap: Bitmap, landmarks: FloatArray): Bitmap? {
        if (landmarks.size != 10) {
            Log.w(TAG, "Invalid landmarks size: ${landmarks.size}")
            return null
        }
        val matrix = similarityTransform(landmarks, TARGET) ?: return null
        return try {
            val out = Bitmap.createBitmap(FACE_SIZE, FACE_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bitmap, matrix, paint)
            out
        } catch (e: Exception) {
            Log.e(TAG, "Align failed", e)
            null
        }
    }

    private fun similarityTransform(src: FloatArray, dst: FloatArray): Matrix? {
        val n = 5
        var srcCx = 0f
        var srcCy = 0f
        var dstCx = 0f
        var dstCy = 0f
        for (i in 0 until n) {
            srcCx += src[i * 2]
            srcCy += src[i * 2 + 1]
            dstCx += dst[i * 2]
            dstCy += dst[i * 2 + 1]
        }
        srcCx /= n
        srcCy /= n
        dstCx /= n
        dstCy /= n

        var srcSq = 0f
        var aNum = 0f
        var bNum = 0f

        for (i in 0 until n) {
            val sx = src[i * 2] - srcCx
            val sy = src[i * 2 + 1] - srcCy
            val dx = dst[i * 2] - dstCx
            val dy = dst[i * 2 + 1] - dstCy
            srcSq += sx * sx + sy * sy
            aNum += sx * dx + sy * dy
            bNum += sx * dy - sy * dx
        }
        if (srcSq <= 1e-10f) return null

        val a = aNum / srcSq
        val b = bNum / srcSq
        val tx = dstCx - (a * srcCx - b * srcCy)
        val ty = dstCy - (b * srcCx + a * srcCy)
        val det = a * a + b * b
        if (det <= 1e-10f) return null

        val matrix = Matrix()
        matrix.setValues(
            floatArrayOf(
                a, -b, tx,
                b, a, ty,
                0f, 0f, 1f
            )
        )
        Log.d(TAG, "Transform scale=${sqrt(det)}, angle=${Math.toDegrees(atan2(b.toDouble(), a.toDouble()))}")
        return matrix
    }
}
