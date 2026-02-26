package com.sustech.bojayL.glasses.ml

import kotlin.math.sqrt

object EmbeddingUtils {

    const val TARGET_DIMENSION = 256

    /**
     * 将任意维度向量规整到 256 维并做 L2 归一化。
     */
    fun toNormalized256(input: FloatArray): FloatArray {
        val resized = FloatArray(TARGET_DIMENSION)
        val copySize = minOf(input.size, TARGET_DIMENSION)
        for (i in 0 until copySize) {
            resized[i] = input[i]
        }
        return l2Normalize(resized)
    }

    fun l2Normalize(vector: FloatArray): FloatArray {
        var sum = 0f
        for (v in vector) {
            sum += v * v
        }
        if (sum <= 1e-12f) {
            return vector
        }
        val inv = 1f / sqrt(sum)
        for (i in vector.indices) {
            vector[i] *= inv
        }
        return vector
    }

    fun cosineSimilarity(lhs: FloatArray, rhs: FloatArray): Float {
        if (lhs.size != rhs.size) return -1f
        var dot = 0f
        var lhsNorm = 0f
        var rhsNorm = 0f
        for (i in lhs.indices) {
            val l = lhs[i]
            val r = rhs[i]
            dot += l * r
            lhsNorm += l * l
            rhsNorm += r * r
        }
        if (lhsNorm <= 1e-12f || rhsNorm <= 1e-12f) return -1f
        return (dot / (sqrt(lhsNorm) * sqrt(rhsNorm))).coerceIn(-1f, 1f)
    }
}
