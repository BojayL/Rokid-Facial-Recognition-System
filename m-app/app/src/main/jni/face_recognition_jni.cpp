// Face Recognition JNI Interface
// Provides MobileFaceNet feature extraction via JNI

#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <string>
#include <vector>

#include "mobilefacenet.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#define TAG "FaceRecognition_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static MobileFaceNet* g_mobilefacenet = nullptr;
static bool g_facenet_initialized = false;

extern "C" {

/**
 * Initialize MobileFaceNet model
 * @param mgr AssetManager for loading model files
 * @param modelType Model type name (e.g., "mobilefacenet")
 * @param useGpu Whether to use GPU (Vulkan) acceleration
 * @return true if initialization successful
 */
JNIEXPORT jboolean JNICALL
Java_com_sustech_bojayL_ml_FaceRecognizer_nativeInit(
    JNIEnv* env,
    jobject thiz,
    jobject assetManager,
    jstring modelType,
    jboolean useGpu
) {
    if (g_facenet_initialized && g_mobilefacenet != nullptr) {
        LOGD("MobileFaceNet already initialized");
        return JNI_TRUE;
    }

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (mgr == nullptr) {
        LOGE("Failed to get AssetManager");
        return JNI_FALSE;
    }

    const char* modelTypeStr = env->GetStringUTFChars(modelType, nullptr);
    bool gpu = (useGpu == JNI_TRUE);

    LOGD("Initializing MobileFaceNet with model: %s, GPU: %d", modelTypeStr, gpu);

    if (g_mobilefacenet == nullptr) {
        g_mobilefacenet = new MobileFaceNet();
    }
    
    int ret = g_mobilefacenet->load(mgr, modelTypeStr, gpu);
    if (ret != 0) {
        LOGE("Failed to load MobileFaceNet model: %d", ret);
        delete g_mobilefacenet;
        g_mobilefacenet = nullptr;
        env->ReleaseStringUTFChars(modelType, modelTypeStr);
        return JNI_FALSE;
    }

    g_facenet_initialized = true;
    LOGD("MobileFaceNet initialized successfully");

    env->ReleaseStringUTFChars(modelType, modelTypeStr);
    return JNI_TRUE;
}

/**
 * Extract 512-dimensional feature vector from aligned face image
 * @param faceBitmap 112x112 RGB aligned face bitmap
 * @return float array containing 512-dimensional feature vector, or null if extraction fails
 */
JNIEXPORT jfloatArray JNICALL
Java_com_sustech_bojayL_ml_FaceRecognizer_nativeExtractFeature(
    JNIEnv* env,
    jobject thiz,
    jobject faceBitmap
) {
    if (!g_facenet_initialized || g_mobilefacenet == nullptr) {
        LOGE("MobileFaceNet not initialized");
        return nullptr;
    }

    // Get bitmap info
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, faceBitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to get bitmap info");
        return nullptr;
    }

    // Verify size (must be 112x112)
    if (info.width != 112 || info.height != 112) {
        LOGE("Invalid bitmap size: %dx%d (expected 112x112)", info.width, info.height);
        return nullptr;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 && 
        info.format != ANDROID_BITMAP_FORMAT_RGB_565) {
        LOGE("Unsupported bitmap format: %d", info.format);
        return nullptr;
    }

    // Lock bitmap pixels
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, faceBitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to lock bitmap pixels");
        return nullptr;
    }

    // Convert bitmap to OpenCV Mat (RGB format)
    cv::Mat rgb;
    if (info.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        cv::Mat rgba(info.height, info.width, CV_8UC4, pixels);
        cv::cvtColor(rgba, rgb, cv::COLOR_RGBA2RGB);
    } else {
        // RGB_565
        cv::Mat rgb565(info.height, info.width, CV_8UC2, pixels);
        cv::cvtColor(rgb565, rgb, cv::COLOR_BGR5652RGB);
    }

    // Extract features
    std::vector<float> feature;
    int ret = g_mobilefacenet->extract(rgb, feature);

    AndroidBitmap_unlockPixels(env, faceBitmap);

    if (ret != 0) {
        LOGE("Feature extraction failed: %d", ret);
        return nullptr;
    }

    // Convert to jfloatArray
    jfloatArray result = env->NewFloatArray(feature.size());
    if (result == nullptr) {
        LOGE("Failed to allocate result array");
        return nullptr;
    }

    env->SetFloatArrayRegion(result, 0, feature.size(), feature.data());

    LOGD("Extracted %d-dimensional feature vector", (int)feature.size());
    return result;
}

/**
 * Compute cosine similarity between two feature vectors
 * @param feature1 First feature vector (512 floats)
 * @param feature2 Second feature vector (512 floats)
 * @return Similarity score (0.0 - 1.0), or -1.0 if error
 */
JNIEXPORT jfloat JNICALL
Java_com_sustech_bojayL_ml_FaceRecognizer_nativeCosineSimilarity(
    JNIEnv* env,
    jobject thiz,
    jfloatArray feature1Array,
    jfloatArray feature2Array
) {
    // Get array sizes
    jsize len1 = env->GetArrayLength(feature1Array);
    jsize len2 = env->GetArrayLength(feature2Array);

    if (len1 != len2) {
        LOGE("Feature vectors have different sizes: %d vs %d", len1, len2);
        return -1.0f;
    }

    if (len1 != 128 && len1 != 512) {
        LOGE("Invalid feature vector size: %d (expected 128 or 512)", len1);
        return -1.0f;
    }

    // Get array data
    jfloat* data1 = env->GetFloatArrayElements(feature1Array, nullptr);
    jfloat* data2 = env->GetFloatArrayElements(feature2Array, nullptr);

    if (data1 == nullptr || data2 == nullptr) {
        LOGE("Failed to get array data");
        if (data1) env->ReleaseFloatArrayElements(feature1Array, data1, 0);
        if (data2) env->ReleaseFloatArrayElements(feature2Array, data2, 0);
        return -1.0f;
    }

    // Convert to std::vector
    std::vector<float> feature1(data1, data1 + len1);
    std::vector<float> feature2(data2, data2 + len2);

    env->ReleaseFloatArrayElements(feature1Array, data1, 0);
    env->ReleaseFloatArrayElements(feature2Array, data2, 0);

    // Compute similarity
    float similarity = MobileFaceNet::cosineSimilarity(feature1, feature2);

    LOGD("Cosine similarity: %.4f", similarity);
    return similarity;
}

/**
 * Release MobileFaceNet resources
 */
JNIEXPORT void JNICALL
Java_com_sustech_bojayL_ml_FaceRecognizer_nativeRelease(
    JNIEnv* env,
    jobject thiz
) {
    LOGD("Releasing MobileFaceNet");
    if (g_mobilefacenet != nullptr) {
        delete g_mobilefacenet;
        g_mobilefacenet = nullptr;
    }
    g_facenet_initialized = false;
}

/**
 * Check if MobileFaceNet is initialized
 */
JNIEXPORT jboolean JNICALL
Java_com_sustech_bojayL_ml_FaceRecognizer_nativeIsInitialized(
    JNIEnv* env,
    jobject thiz
) {
    return g_facenet_initialized ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
