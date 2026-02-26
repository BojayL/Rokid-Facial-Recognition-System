package com.sustech.bojayL.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap
import kotlin.math.sqrt

/**
 * FaceNet-MobileNetV2 (256d) SNPE INT8 特征提取器（手机端模板生成专用）。
 *
 * 用途：
 * - 学生录入/LFW 导入时生成与眼镜端同模型同特征空间的 256 维模板。
 */
object SnpeFaceNetRecognizer {

    private const val TAG = "SnpeFaceNetRecognizer"
    private val MODEL_ASSET_CANDIDATES = arrayOf(
        "facenet_mobilenetv2_256_int8.dlc",
        "facenet_mobilenetv2_256.dlc"
    )
    const val MODEL_ID = "facenet_mobilenetv2_256_int8_snpe"
    const val OUTPUT_DIM = 256
    private const val DEFAULT_FACE_SIZE = 112

    private var initialized = false
    private var warningPrinted = false
    private var snpeNetwork: Any? = null
    private var inputTensorName: String? = null
    private var outputTensorName: String? = null
    private var inputShape: IntArray = intArrayOf(1, DEFAULT_FACE_SIZE, DEFAULT_FACE_SIZE, 3)

    fun init(context: Context): Boolean {
        if (initialized && snpeNetwork != null) return true
        release()

        val hasRuntime = hasSnpeRuntime()
        val asset = resolveModelAsset(context)
        if (!hasRuntime || asset == null) {
            initialized = false
            if (!warningPrinted) {
                warningPrinted = true
                Log.w(TAG, "SNPE unavailable: runtime=$hasRuntime modelAsset=${asset ?: "missing"}")
            }
            return false
        }

        return try {
            val copiedModel = copyModelToCache(context, asset)
            val builder = createBuilder(context)
            if (builder == null) {
                Log.e(TAG, "Failed to create SNPE builder")
                return false
            }

            if (!configureBuilder(builder, context, copiedModel, asset)) {
                Log.e(TAG, "Failed to configure SNPE builder")
                return false
            }

            val buildResult = invokeReflect(builder, "build")
            val network = if (buildResult.invoked) buildResult.value else null
            if (network == null) {
                Log.e(TAG, "Failed to build SNPE network")
                return false
            }

            val inputName = readTensorName(network, input = true)
            val outputName = readTensorName(network, input = false)
            if (inputName.isNullOrBlank()) {
                Log.e(TAG, "Failed to resolve SNPE input tensor")
                releaseNetwork(network)
                return false
            }

            snpeNetwork = network
            inputTensorName = inputName
            outputTensorName = outputName
            inputShape = resolveInputShape(network, inputName)
            initialized = true
            Log.i(TAG, "SNPE ready: model=$asset input=$inputTensorName output=$outputTensorName shape=${inputShape.contentToString()}")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "SNPE init failed", e)
            release()
            false
        }
    }

    fun extractFeature(alignedFace112: Bitmap): FloatArray? {
        if (!initialized) return null
        val network = snpeNetwork ?: return null
        val inputName = inputTensorName ?: return null

        var inputTensor: Any? = null
        var outputTensor: Any? = null

        return try {
            val normalizedInput = preprocessInput(alignedFace112, inputShape)
            inputTensor = createInputTensor(network, inputShape)
            if (inputTensor == null || !writeInputTensor(inputTensor, normalizedInput)) {
                Log.e(TAG, "Failed to prepare SNPE input tensor")
                initialized = false
                return null
            }

            val inputs = LinkedHashMap<String, Any>(1)
            inputs[inputName] = inputTensor
            val executeResult = invokeReflect(network, "execute", inputs)
            if (!executeResult.invoked || executeResult.value !is Map<*, *>) {
                Log.e(TAG, "SNPE execute failed")
                initialized = false
                return null
            }

            outputTensor = selectOutputTensor(executeResult.value, outputTensorName)
            if (outputTensor == null) {
                Log.e(TAG, "SNPE output tensor is empty")
                initialized = false
                return null
            }

            val raw = readOutputTensor(outputTensor) ?: run {
                Log.e(TAG, "Failed to read SNPE output tensor")
                initialized = false
                return null
            }
            normalizeTo256(raw)
        } catch (e: Throwable) {
            Log.e(TAG, "SNPE inference failed", e)
            initialized = false
            null
        } finally {
            releaseTensor(inputTensor)
            releaseTensor(outputTensor)
        }
    }

    fun release() {
        releaseNetwork(snpeNetwork)
        snpeNetwork = null
        inputTensorName = null
        outputTensorName = null
        inputShape = intArrayOf(1, DEFAULT_FACE_SIZE, DEFAULT_FACE_SIZE, 3)
        initialized = false
    }

    private fun normalizeTo256(raw: FloatArray): FloatArray {
        val out = FloatArray(OUTPUT_DIM)
        val copySize = minOf(OUTPUT_DIM, raw.size)
        for (i in 0 until copySize) {
            out[i] = raw[i]
        }
        var sum = 0f
        for (value in out) {
            sum += value * value
        }
        if (sum > 1e-12f) {
            val inv = 1f / sqrt(sum)
            for (i in out.indices) {
                out[i] *= inv
            }
        }
        return out
    }

    private fun hasSnpeRuntime(): Boolean {
        return findClass(
            "com.qualcomm.qti.snpe.SNPE",
            "com.qualcomm.qti.snpe.NeuralNetwork"
        ) != null
    }

    private fun resolveModelAsset(context: Context): String? {
        for (asset in MODEL_ASSET_CANDIDATES) {
            try {
                context.assets.open(asset).use { return asset }
            } catch (_: Exception) {
                // try next
            }
        }
        return null
    }

    private fun copyModelToCache(context: Context, assetName: String): File {
        val dir = File(context.filesDir, "snpe_models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val output = File(dir, assetName)
        context.assets.open(assetName).use { input ->
            output.outputStream().use { out ->
                input.copyTo(out)
            }
        }
        return output
    }

    private fun createBuilder(context: Context): Any? {
        val builderClass = findClass(
            "com.qualcomm.qti.snpe.SNPE\$NeuralNetworkBuilder",
            "com.qualcomm.qti.snpe.NeuralNetwork\$Builder"
        ) ?: return null

        val appContext = context.applicationContext
        val ctor = builderClass.constructors.firstOrNull { constructor ->
            val params = constructor.parameterTypes
            params.size == 1 && params[0].isAssignableFrom(appContext.javaClass)
        } ?: return null

        return try {
            ctor.newInstance(appContext)
        } catch (e: Throwable) {
            Log.e(TAG, "Builder create failed", e)
            null
        }
    }

    private fun configureBuilder(
        builder: Any,
        context: Context,
        modelPath: File,
        assetName: String
    ): Boolean {
        if (!setModel(builder, context, modelPath, assetName)) {
            return false
        }
        invokeReflect(builder, "setCpuFallbackEnabled", true)
        invokeReflect(builder, "setUseUserSuppliedBuffers", false)
        invokeReflect(builder, "setDebugEnabled", false)
        configureRuntimeOrder(builder)
        return true
    }

    private fun setModel(builder: Any, context: Context, modelPath: File, assetName: String): Boolean {
        if (invokeReflect(builder, "setModel", modelPath).invoked) {
            return true
        }
        context.assets.open(assetName).use { input ->
            if (invokeReflect(builder, "setModel", input).invoked) {
                return true
            }
        }
        val bytes = modelPath.readBytes()
        if (invokeReflect(builder, "setModel", bytes).invoked) {
            return true
        }
        return false
    }

    private fun configureRuntimeOrder(builder: Any) {
        val runtimeClass = findClass(
            "com.qualcomm.qti.snpe.NeuralNetwork\$Runtime",
            "com.qualcomm.qti.snpe.SNPE\$Runtime"
        ) ?: return

        val enumValues = runtimeClass.enumConstants ?: return
        val byName = enumValues.associateBy { (it as Enum<*>).name }
        val preferred = listOf("DSP", "AIP", "GPU_FLOAT16", "GPU", "CPU")
            .mapNotNull { byName[it] }
        if (preferred.isEmpty()) return

        val runtimeArray = ReflectArray.newInstance(runtimeClass, preferred.size)
        for (i in preferred.indices) {
            ReflectArray.set(runtimeArray, i, preferred[i])
        }
        if (invokeReflect(builder, "setRuntimeOrder", runtimeArray).invoked) {
            return
        }
        invokeReflect(builder, "setRuntimeOrder", preferred)
    }

    private fun readTensorName(network: Any, input: Boolean): String? {
        val method = if (input) "getInputTensorsNames" else "getOutputTensorsNames"
        val result = invokeReflect(network, method)
        if (!result.invoked) return null
        return toStringList(result.value).firstOrNull()
    }

    private fun resolveInputShape(network: Any, inputName: String): IntArray {
        val shapes = invokeReflect(network, "getInputTensorsShapes")
        if (shapes.invoked && shapes.value is Map<*, *>) {
            val map = shapes.value as Map<*, *>
            val byKey = map[inputName]
            val anyShape = byKey ?: map.values.firstOrNull()
            toIntArray(anyShape)?.let { if (it.isNotEmpty()) return it }
        }

        val direct = invokeReflect(network, "getInputTensorShape", inputName)
        if (direct.invoked) {
            toIntArray(direct.value)?.let { if (it.isNotEmpty()) return it }
        }
        return intArrayOf(1, DEFAULT_FACE_SIZE, DEFAULT_FACE_SIZE, 3)
    }

    private fun createInputTensor(network: Any, shape: IntArray): Any? {
        val direct = invokeReflect(network, "createFloatTensor", shape)
        if (direct.invoked && direct.value != null) return direct.value

        val longShape = LongArray(shape.size) { idx -> shape[idx].toLong() }
        val longResult = invokeReflect(network, "createFloatTensor", longShape)
        if (longResult.invoked && longResult.value != null) return longResult.value
        return null
    }

    private fun writeInputTensor(tensor: Any, input: FloatArray): Boolean {
        if (invokeReflect(tensor, "write", input, 0, input.size, 0).invoked) return true
        if (invokeReflect(tensor, "write", input, 0, input.size).invoked) return true
        if (invokeReflect(tensor, "write", input).invoked) return true
        return false
    }

    private fun readOutputTensor(tensor: Any): FloatArray? {
        val size = readTensorSize(tensor)
        if (size <= 0) return null
        val out = FloatArray(size)
        if (invokeReflect(tensor, "read", out, 0, size, 0).invoked) return out
        if (invokeReflect(tensor, "read", out, 0, size).invoked) return out
        if (invokeReflect(tensor, "read", out).invoked) return out

        val bufferResult = invokeReflect(tensor, "getBuffer")
        val buffer = if (bufferResult.invoked) bufferResult.value as? ByteBuffer else null
        if (buffer != null) {
            buffer.order(ByteOrder.nativeOrder())
            val floatBuffer = buffer.asFloatBuffer()
            val copy = FloatArray(minOf(floatBuffer.remaining(), size))
            floatBuffer.get(copy)
            if (copy.isNotEmpty()) {
                return copy
            }
        }
        return null
    }

    private fun readTensorSize(tensor: Any): Int {
        val bySize = invokeReflect(tensor, "getSize")
        if (bySize.invoked && bySize.value is Number) {
            return (bySize.value as Number).toInt()
        }
        val byShape = invokeReflect(tensor, "getShape")
        if (byShape.invoked) {
            val shape = toIntArray(byShape.value)
            if (shape != null && shape.isNotEmpty()) {
                var total = 1
                for (dim in shape) {
                    if (dim > 0) total *= dim
                }
                if (total > 0) return total
            }
        }
        return OUTPUT_DIM
    }

    private fun selectOutputTensor(outputs: Map<*, *>, outputName: String?): Any? {
        if (!outputName.isNullOrBlank()) {
            val byName = outputs[outputName]
            if (byName != null) return byName
        }
        return outputs.values.firstOrNull()
    }

    private fun preprocessInput(bitmap: Bitmap, tensorShape: IntArray): FloatArray {
        val (isNchw, width, height) = resolveLayout(tensorShape)
        val scaled = if (bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        try {
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)

            val expected = expectedInputLength(tensorShape, width, height)
            val input = FloatArray(expected)
            if (isNchw) {
                val plane = width * height
                for (i in pixels.indices) {
                    val px = pixels[i]
                    val r = ((px shr 16) and 0xFF).toFloat()
                    val g = ((px shr 8) and 0xFF).toFloat()
                    val b = (px and 0xFF).toFloat()
                    input[i] = normalizePixel(r)
                    input[plane + i] = normalizePixel(g)
                    input[plane * 2 + i] = normalizePixel(b)
                }
            } else {
                var offset = 0
                for (px in pixels) {
                    val r = ((px shr 16) and 0xFF).toFloat()
                    val g = ((px shr 8) and 0xFF).toFloat()
                    val b = (px and 0xFF).toFloat()
                    input[offset++] = normalizePixel(r)
                    input[offset++] = normalizePixel(g)
                    input[offset++] = normalizePixel(b)
                }
            }
            return input
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
        }
    }

    private fun normalizePixel(value: Float): Float {
        return (value - 127.5f) / 128f
    }

    private fun resolveLayout(shape: IntArray): Triple<Boolean, Int, Int> {
        val dims = shape.filter { it > 0 }
        if (dims.size >= 4) {
            val cFirst = dims[1] == 3
            if (cFirst) {
                return Triple(true, dims[3], dims[2])
            }
            if (dims.last() == 3) {
                return Triple(false, dims[dims.size - 2], dims[dims.size - 3])
            }
        }
        if (dims.size == 3) {
            if (dims[0] == 3) {
                return Triple(true, dims[2], dims[1])
            }
            if (dims[2] == 3) {
                return Triple(false, dims[1], dims[0])
            }
        }
        return Triple(false, DEFAULT_FACE_SIZE, DEFAULT_FACE_SIZE)
    }

    private fun expectedInputLength(shape: IntArray, width: Int, height: Int): Int {
        var product = 1
        for (dim in shape) {
            if (dim > 0) product *= dim
        }
        val fallback = width * height * 3
        return if (product > 0) product else fallback
    }

    private fun releaseTensor(tensor: Any?) {
        if (tensor == null) return
        invokeReflect(tensor, "release")
        invokeReflect(tensor, "close")
    }

    private fun releaseNetwork(network: Any?) {
        if (network == null) return
        invokeReflect(network, "release")
        invokeReflect(network, "close")
        invokeReflect(network, "dispose")
    }

    private fun findClass(vararg names: String): Class<*>? {
        for (name in names) {
            try {
                return Class.forName(name)
            } catch (_: Throwable) {
                // try next
            }
        }
        return null
    }

    private data class InvocationResult(
        val invoked: Boolean,
        val value: Any?
    )

    private fun invokeReflect(target: Any, methodName: String, vararg args: Any?): InvocationResult {
        val methods = target.javaClass.methods.filter { method ->
            method.name == methodName && method.parameterTypes.size == args.size
        }
        for (method in methods) {
            val converted = convertArguments(method, args) ?: continue
            try {
                method.isAccessible = true
                return InvocationResult(true, method.invoke(target, *converted))
            } catch (_: Throwable) {
                // try next overload
            }
        }
        return InvocationResult(false, null)
    }

    private fun convertArguments(method: Method, args: Array<out Any?>): Array<Any?>? {
        val params = method.parameterTypes
        val out = arrayOfNulls<Any>(args.size)
        for (i in args.indices) {
            val converted = convertArgument(params[i], args[i]) ?: return null
            out[i] = converted
        }
        return out
    }

    private fun convertArgument(paramType: Class<*>, value: Any?): Any? {
        if (value == null) {
            return if (paramType.isPrimitive) null else null
        }
        if (paramType.isAssignableFrom(value.javaClass)) {
            return value
        }
        if (paramType.isPrimitive) {
            return when (paramType) {
                Boolean::class.javaPrimitiveType -> if (value is Boolean) value else null
                Int::class.javaPrimitiveType -> (value as? Number)?.toInt()
                Long::class.javaPrimitiveType -> (value as? Number)?.toLong()
                Float::class.javaPrimitiveType -> (value as? Number)?.toFloat()
                Double::class.javaPrimitiveType -> (value as? Number)?.toDouble()
                Short::class.javaPrimitiveType -> (value as? Number)?.toShort()
                Byte::class.javaPrimitiveType -> (value as? Number)?.toByte()
                Char::class.javaPrimitiveType -> if (value is Char) value else null
                else -> null
            }
        }
        if (Number::class.java.isAssignableFrom(paramType) && value is Number) {
            return when (paramType) {
                java.lang.Integer::class.java -> value.toInt()
                java.lang.Long::class.java -> value.toLong()
                java.lang.Float::class.java -> value.toFloat()
                java.lang.Double::class.java -> value.toDouble()
                java.lang.Short::class.java -> value.toShort()
                java.lang.Byte::class.java -> value.toByte()
                else -> null
            }
        }
        return null
    }

    private fun toStringList(value: Any?): List<String> {
        return when (value) {
            is Collection<*> -> value.mapNotNull { it?.toString() }
            is Array<*> -> value.mapNotNull { it?.toString() }
            else -> emptyList()
        }
    }

    private fun toIntArray(value: Any?): IntArray? {
        return when (value) {
            is IntArray -> value
            is LongArray -> IntArray(value.size) { idx -> value[idx].toInt() }
            is Array<*> -> {
                val out = IntArray(value.size)
                for (i in value.indices) {
                    val number = value[i] as? Number ?: return null
                    out[i] = number.toInt()
                }
                out
            }
            is Collection<*> -> {
                val out = IntArray(value.size)
                var idx = 0
                for (item in value) {
                    val number = item as? Number ?: return null
                    out[idx++] = number.toInt()
                }
                out
            }
            else -> null
        }
    }
}
