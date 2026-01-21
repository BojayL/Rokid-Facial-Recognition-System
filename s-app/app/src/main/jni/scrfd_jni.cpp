// SCRFD Face Detection JNI Interface for Glasses (s-app)
// Based on https://github.com/nihui/ncnn-android-scrfd

#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <string>
#include <vector>

#include "scrfd.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#define TAG "SCRFD_GLASSES_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static SCRFD* g_scrfd = nullptr;
static bool g_initialized = false;

extern "C" {

/**
 * Initialize SCRFD detector with model from assets
 * @param mgr AssetManager for loading model files
 * @param modelType Model type: "2.5g_kps" (recommended), "500m", "1g", etc.
 * @param useGpu Whether to use GPU (Vulkan) acceleration
 * @return true if initialization successful
 */
JNIEXPORT jboolean JNICALL
Java_com_sustech_bojayL_glasses_ml_GlassesFaceDetector_nativeInit(
    JNIEnv* env,
    jobject thiz,
    jobject assetManager,
    jstring modelType,
    jboolean useGpu
) {
    if (g_initialized && g_scrfd != nullptr) {
        LOGD("SCRFD already initialized");
        return JNI_TRUE;
    }

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (mgr == nullptr) {
        LOGE("Failed to get AssetManager");
        return JNI_FALSE;
    }

    const char* modelTypeStr = env->GetStringUTFChars(modelType, nullptr);
    bool gpu = (useGpu == JNI_TRUE);

    LOGD("Initializing SCRFD with model: %s, GPU: %d", modelTypeStr, gpu);

    if (g_scrfd == nullptr) {
        g_scrfd = new SCRFD();
    }
    
    int ret = g_scrfd->load(mgr, modelTypeStr, gpu);
    if (ret != 0) {
        LOGE("Failed to load SCRFD model: %d", ret);
        delete g_scrfd;
        g_scrfd = nullptr;
        env->ReleaseStringUTFChars(modelType, modelTypeStr);
        return JNI_FALSE;
    }

    g_initialized = true;
    LOGD("SCRFD initialized successfully");

    env->ReleaseStringUTFChars(modelType, modelTypeStr);
    return JNI_TRUE;
}

/**
 * Detect faces in a bitmap
 * @param bitmap Android Bitmap (ARGB_8888 or RGB_565)
 * @param probThreshold Detection confidence threshold (0.0-1.0)
 * @param nmsThreshold NMS threshold (0.0-1.0)
 * @return float array: [numFaces, face1_x, face1_y, face1_w, face1_h, face1_prob, lm1_x1, lm1_y1, ..., lm1_x5, lm1_y5, ...]
 *         Returns null if detection fails
 */
JNIEXPORT jfloatArray JNICALL
Java_com_sustech_bojayL_glasses_ml_GlassesFaceDetector_nativeDetect(
    JNIEnv* env,
    jobject thiz,
    jobject bitmap,
    jfloat probThreshold,
    jfloat nmsThreshold
) {
    if (!g_initialized || g_scrfd == nullptr) {
        LOGE("SCRFD not initialized");
        return nullptr;
    }

    // Get bitmap info
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed to get bitmap info");
        return nullptr;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 && 
        info.format != ANDROID_BITMAP_FORMAT_RGB_565) {
        LOGE("Unsupported bitmap format: %d", info.format);
        return nullptr;
    }

    // Lock bitmap pixels
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
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

    // Detect faces
    std::vector<FaceObject> faceObjects;
    g_scrfd->detect(rgb, faceObjects, probThreshold, nmsThreshold);

    AndroidBitmap_unlockPixels(env, bitmap);

    // Build result array
    // Format: [numFaces, x1, y1, w1, h1, prob1, lm1_x1, lm1_y1, ..., lm1_x5, lm1_y5, x2, y2, ...]
    // Each face: x, y, width, height, prob, landmark[0].x, landmark[0].y, ..., landmark[4].x, landmark[4].y
    int numFaces = faceObjects.size();
    int valuesPerFace = 15;  // 5 (bbox + prob) + 10 (5 landmarks * 2 coords)
    int resultSize = 1 + numFaces * valuesPerFace;  // 1 for count
    
    jfloatArray result = env->NewFloatArray(resultSize);
    if (result == nullptr) {
        LOGE("Failed to allocate result array");
        return nullptr;
    }

    std::vector<float> resultData(resultSize);
    resultData[0] = static_cast<float>(numFaces);

    for (int i = 0; i < numFaces; i++) {
        const FaceObject& face = faceObjects[i];
        int offset = 1 + i * valuesPerFace;
        
        // Bounding box and confidence
        resultData[offset + 0] = face.rect.x;
        resultData[offset + 1] = face.rect.y;
        resultData[offset + 2] = face.rect.width;
        resultData[offset + 3] = face.rect.height;
        resultData[offset + 4] = face.prob;
        
        // 5 facial landmarks (x, y pairs)
        resultData[offset + 5] = face.landmark[0].x;
        resultData[offset + 6] = face.landmark[0].y;
        resultData[offset + 7] = face.landmark[1].x;
        resultData[offset + 8] = face.landmark[1].y;
        resultData[offset + 9] = face.landmark[2].x;
        resultData[offset + 10] = face.landmark[2].y;
        resultData[offset + 11] = face.landmark[3].x;
        resultData[offset + 12] = face.landmark[3].y;
        resultData[offset + 13] = face.landmark[4].x;
        resultData[offset + 14] = face.landmark[4].y;
    }

    env->SetFloatArrayRegion(result, 0, resultSize, resultData.data());

    LOGD("Detected %d faces with landmarks", numFaces);
    return result;
}

/**
 * Release SCRFD detector resources
 */
JNIEXPORT void JNICALL
Java_com_sustech_bojayL_glasses_ml_GlassesFaceDetector_nativeRelease(
    JNIEnv* env,
    jobject thiz
) {
    LOGD("Releasing SCRFD");
    if (g_scrfd != nullptr) {
        delete g_scrfd;
        g_scrfd = nullptr;
    }
    g_initialized = false;
}

/**
 * Check if SCRFD is initialized
 */
JNIEXPORT jboolean JNICALL
Java_com_sustech_bojayL_glasses_ml_GlassesFaceDetector_nativeIsInitialized(
    JNIEnv* env,
    jobject thiz
) {
    return g_initialized ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
