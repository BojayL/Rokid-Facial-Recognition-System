// MobileFaceNet Feature Extractor Implementation
// Based on InsightFace MobileFaceNet model

#include "mobilefacenet.h"

#include <string.h>
#include <cmath>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#include "cpu.h"

int MobileFaceNet::load(const char* modeltype, bool use_gpu)
{
    mobilefacenet.clear();

    ncnn::set_cpu_powersave(2);
    ncnn::set_omp_num_threads(ncnn::get_big_cpu_count());

    mobilefacenet.opt = ncnn::Option();

#if NCNN_VULKAN
    mobilefacenet.opt.use_vulkan_compute = use_gpu;
#endif

    mobilefacenet.opt.num_threads = ncnn::get_big_cpu_count();

    char parampath[256];
    char modelpath[256];
    sprintf(parampath, "%s-opt.param", modeltype);
    sprintf(modelpath, "%s-opt.bin", modeltype);

    int ret1 = mobilefacenet.load_param(parampath);
    int ret2 = mobilefacenet.load_model(modelpath);
    
    if (ret1 != 0 || ret2 != 0) {
        return -1;
    }

    return 0;
}

int MobileFaceNet::load(AAssetManager* mgr, const char* modeltype, bool use_gpu)
{
    mobilefacenet.clear();

    ncnn::set_cpu_powersave(2);
    ncnn::set_omp_num_threads(ncnn::get_big_cpu_count());

    mobilefacenet.opt = ncnn::Option();

#if NCNN_VULKAN
    mobilefacenet.opt.use_vulkan_compute = use_gpu;
#endif

    mobilefacenet.opt.num_threads = ncnn::get_big_cpu_count();

    char parampath[256];
    char modelpath[256];
    sprintf(parampath, "%s-opt.param", modeltype);
    sprintf(modelpath, "%s-opt.bin", modeltype);

    int ret1 = mobilefacenet.load_param(mgr, parampath);
    int ret2 = mobilefacenet.load_model(mgr, modelpath);
    
    if (ret1 != 0 || ret2 != 0) {
        return -1;
    }

    return 0;
}

int MobileFaceNet::extract(const cv::Mat& rgb, std::vector<float>& feature)
{
    // Verify input size
    if (rgb.cols != 112 || rgb.rows != 112) {
        return -1;
    }
    
    if (rgb.channels() != 3) {
        return -2;
    }

    // Convert to NCNN Mat
    ncnn::Mat in = ncnn::Mat::from_pixels(rgb.data, ncnn::Mat::PIXEL_RGB, rgb.cols, rgb.rows);

    // NOTE: The model already has built-in preprocessing in the first layers:
    //   _minusscalar0: (x - 127.5)
    //   _mulscalar0: * 0.007813 (≈ 1/128)
    // So we should NOT do additional normalization here.
    // The model expects raw pixel values in [0, 255].

    // Extract features
    ncnn::Extractor ex = mobilefacenet.create_extractor();
    
    // Input layer name may vary depending on model conversion
    // Common names: "data", "input", "input.1"
    // Check your .param file for the actual input name
    ex.input("data", in);

    // Output layer name (512-dim embedding)
    // Common names: "fc1", "embedding", "output"
    ncnn::Mat out;
    int ret = ex.extract("fc1", out);
    
    if (ret != 0) {
        return ret;
    }

    // Convert NCNN Mat to std::vector
    feature.resize(out.w);
    for (int i = 0; i < out.w; i++) {
        feature[i] = out[i];
    }

    // L2 normalize
    normalizeFeature(feature);

    return 0;
}

void MobileFaceNet::normalizeFeature(std::vector<float>& feature)
{
    // L2 normalization: divide by the L2 norm
    float norm = 0.0f;
    for (float val : feature) {
        norm += val * val;
    }
    norm = std::sqrt(norm);

    if (norm > 1e-6f) {
        for (float& val : feature) {
            val /= norm;
        }
    }
}

float MobileFaceNet::cosineSimilarity(const std::vector<float>& feature1, 
                                       const std::vector<float>& feature2)
{
    if (feature1.size() != feature2.size()) {
        return -1.0f;
    }

    if (feature1.empty()) {
        return -1.0f;
    }

    // Compute dot product (since both vectors are normalized, this is cosine similarity)
    float dot_product = 0.0f;
    for (size_t i = 0; i < feature1.size(); i++) {
        dot_product += feature1[i] * feature2[i];
    }

    // Clamp to [-1, 1] to handle numerical errors
    if (dot_product > 1.0f) dot_product = 1.0f;
    if (dot_product < -1.0f) dot_product = -1.0f;

    return dot_product;
}
