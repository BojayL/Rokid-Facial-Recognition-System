package com.sustech.bojayL.glasses.ml

/**
 * 眼镜端人脸模板
 *
 * 由手机端下发，用于端侧余弦比对。
 */
data class FaceTemplate(
    val studentId: String,
    val studentName: String,
    val className: String,
    val tags: List<String> = emptyList(),
    val modelId: String? = null,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceTemplate) return false
        return studentId == other.studentId &&
                studentName == other.studentName &&
                className == other.className &&
                tags == other.tags &&
                modelId == other.modelId &&
                embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = studentId.hashCode()
        result = 31 * result + studentName.hashCode()
        result = 31 * result + className.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + (modelId?.hashCode() ?: 0)
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
