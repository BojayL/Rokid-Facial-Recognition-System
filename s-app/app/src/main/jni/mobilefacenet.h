// MobileFaceNet Feature Extractor
// Based on InsightFace MobileFaceNet model
// https://github.com/deepinsight/insightface

#ifndef MOBILEFACENET_H
#define MOBILEFACENET_H

#include <opencv2/core/core.hpp>
#include <net.h>

/**
 * MobileFaceNet feature extractor
 * 
 * Input: 112x112 RGB aligned face image
 * Output: 512-dimensional feature vector
 */
class MobileFaceNet
{
public:
    /**
     * Load model from file system
     * @param modeltype Model name (e.g., "mobilefacenet")
     * @param use_gpu Whether to use GPU (Vulkan) acceleration
     * @return 0 if successful
     */
    int load(const char* modeltype, bool use_gpu = false);

    /**
     * Load model from Android assets
     * @param mgr AssetManager
     * @param modeltype Model name (e.g., "mobilefacenet")
     * @param use_gpu Whether to use GPU (Vulkan) acceleration
     * @return 0 if successful
     */
    int load(AAssetManager* mgr, const char* modeltype, bool use_gpu = false);

    /**
     * Extract 512-dimensional feature vector from aligned face
     * @param rgb 112x112 RGB face image
     * @param feature Output feature vector (512 floats)
     * @return 0 if successful
     */
    int extract(const cv::Mat& rgb, std::vector<float>& feature);

    /**
     * Compute cosine similarity between two feature vectors
     * @param feature1 First feature vector (512 floats)
     * @param feature2 Second feature vector (512 floats)
     * @return Similarity score (0.0 - 1.0)
     */
    static float cosineSimilarity(const std::vector<float>& feature1, 
                                   const std::vector<float>& feature2);

private:
    ncnn::Net mobilefacenet;
    
    /**
     * Normalize feature vector to unit length (L2 normalization)
     * @param feature Feature vector to normalize (in-place)
     */
    void normalizeFeature(std::vector<float>& feature);
};

#endif // MOBILEFACENET_H
