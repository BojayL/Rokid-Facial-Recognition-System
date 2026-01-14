# 人脸识别模型配置指南

## MobileFaceNet 模型获取

### 选项 1: 从 InsightFace 官方获取（推荐）

```bash
# 克隆 InsightFace 仓库
git clone https://github.com/deepinsight/insightface.git

# 进入模型目录
cd insightface/recognition/arcface_torch

# 下载预训练模型（需要安装 gdown 或手动下载）
# 模型下载地址: https://github.com/deepinsight/insightface/tree/master/model_zoo
```

### 选项 2: 使用 NCNN Model Zoo 转换版本

访问 https://github.com/nihui/ncnn-assets 获取已转换的 NCNN 模型。

### 选项 3: 手动转换 PyTorch 模型

```bash
# 安装转换工具
pip install onnx onnx-simplifier

# 转换为 ONNX
python3 -m insightface.model_zoo.convert_to_onnx \
    --model mobilefacenet \
    --output mobilefacenet.onnx

# 转换为 NCNN
# 使用 onnx2ncnn 工具
./onnx2ncnn mobilefacenet.onnx mobilefacenet.param mobilefacenet.bin
```

## 模型文件放置

将下载的模型文件放置到以下位置：

```
app/src/main/assets/
├── scrfd_2.5g_kps-opt2.param  (已存在)
├── scrfd_2.5g_kps-opt2.bin    (已存在)
├── mobilefacenet-opt.param    (新增)
└── mobilefacenet-opt.bin      (新增)
```

## 模型规格

- **模型名称**: MobileFaceNet
- **输入尺寸**: 112x112 RGB
- **输出维度**: 512 维特征向量
- **模型大小**: ~4MB
- **精度**: LFW 99.5%+
- **推理速度**: < 50ms (移动端)

## 验证模型

```kotlin
// 在 Android 应用中验证模型加载
val success = FaceRecognizer.init(context)
if (success) {
    Log.d("MODEL", "MobileFaceNet loaded successfully")
} else {
    Log.e("MODEL", "Failed to load MobileFaceNet")
}
```

## 故障排查

### 问题 1: 模型加载失败
- 检查文件名是否正确
- 确认文件已放置在 assets 目录
- 查看 logcat 错误信息

### 问题 2: 推理速度慢
- 尝试使用量化模型（INT8）
- 启用 Vulkan GPU 加速
- 降低输入图像分辨率

### 问题 3: 识别准确率低
- 确保人脸对齐正确（112x112）
- 提高相似度阈值（0.7 → 0.8）
- 录入时采集多角度人脸
