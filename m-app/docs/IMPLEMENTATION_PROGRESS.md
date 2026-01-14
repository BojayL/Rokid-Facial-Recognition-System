# 手机端自动人脸识别与 HUD 界面实施进度

## ✅ 已完成 (Phase 1-2 部分)

### Phase 1: Native 层扩展
- ✅ **SCRFD JNI 修改**: 修改 `scrfd_jni.cpp` 返回关键点数据（5个点，10个坐标值）
- ✅ **MobileFaceNet 实现**: 
  - 创建 `mobilefacenet.h` 和 `mobilefacenet.cpp`
  - 实现特征提取、L2归一化、余弦相似度计算
- ✅ **JNI 接口**: 创建 `face_recognition_jni.cpp`，提供特征提取和相似度计算接口
- ✅ **编译配置**: 更新 `CMakeLists.txt`，添加新的源文件

### Phase 2: Kotlin 层封装
- ✅ **FaceRecognizer.kt**: 
  - 封装 MobileFaceNet JNI 调用
  - 提供特征提取 API
  - 实现学生匹配算法（基于余弦相似度）
- ✅ **FaceAlignment.kt**:
  - 实现基于5关键点的人脸对齐
  - 仿射变换对齐到 112x112 标准尺寸
  - 提供简化的双眼对齐方法

### 文档
- ✅ **MODEL_SETUP.md**: 模型获取和配置指南

## 🔄 待完成 (必需)

### Phase 2 剩余
1. **扩展 Student 模型**  
   - 添加 `faceFeature: List<Float>?` 字段
   - 更新序列化逻辑

2. **创建 FaceTracker.kt** (可选，用于多人脸跟踪)
   - IoU 匹配算法
   - 人脸 ID 跟踪

### Phase 3: HUD UI 组件 (核心)
1. **ReticleOverlay.kt** - 十字准心
2. **FaceDetectionOverlay.kt** (改造) - 断角框样式
3. **FaceInfoBubble.kt** - 跟随人脸的信息气泡
4. **StatusIcon.kt** - 线性图标组件

### Phase 4: 集成 (关键)
1. **更新 InsightfaceNcnnDetector.kt**
   - 解析新格式的检测结果（包含关键点）
   - 更新 `FaceResult` 数据类

2. **改造 PhoneCameraRecognitionScreen.kt**
   - 集成 HUD UI 组件
   - 实现完整识别流程：检测 → 对齐 → 特征提取 → 匹配

3. **更新 ClassroomViewModel.kt**
   - 添加自动识别逻辑
   - 实现去重机制（30秒冷却）

4. **更新 CameraFaceDetector.kt**
   - 集成人脸对齐和特征提取
   - 调用 FaceRecognizer 进行匹配

### Phase 5: 测试
1. 编译验证
2. 模型加载测试
3. 端到端功能测试

## 📦 需要准备的资源

### MobileFaceNet 模型文件
**位置**: `app/src/main/assets/`

```
mobilefacenet-opt.param
mobilefacenet-opt.bin
```

**获取方式**: 参考 `docs/MODEL_SETUP.md`

推荐来源：
- InsightFace 官方仓库
- NCNN Model Zoo
- 手动转换 PyTorch 模型

### 模型规格
- 输入尺寸: 112x112 RGB
- 输出维度: 512 维特征向量
- 模型大小: ~4MB
- 精度: LFW 99.5%+

## 🔧 下一步行动

### 立即需要
1. **获取 MobileFaceNet 模型** 并放置到 assets 目录
2. **扩展 Student 模型** 添加 faceFeature 字段
3. **更新 InsightfaceNcnnDetector** 解析关键点数据

### 后续
4. **创建 HUD UI 组件**（Phase 3）
5. **集成识别流程**（Phase 4）
6. **测试验证**（Phase 5）

## ⚠️ 注意事项

### 编译前检查
- [ ] MobileFaceNet 模型文件已放置
- [ ] CMakeLists.txt 正确配置
- [ ] JNI 函数名称与 Kotlin 包名匹配

### 运行时检查
- [ ] SCRFD 2.5g_kps 模型支持关键点检测
- [ ] MobileFaceNet 初始化成功
- [ ] 特征向量维度正确（512）

### 性能优化
- [ ] 使用 GPU 加速（Vulkan）
- [ ] 限制检测频率（2 FPS）
- [ ] 及时回收 Bitmap 资源
- [ ] 使用 LruCache 缓存特征

## 📝 关键代码片段

### 使用示例

```kotlin
// 初始化（应用启动时）
FaceRecognizer.init(context, useGpu = true)

// 人脸检测 + 对齐 + 识别
val detectionResult = InsightfaceNcnnDetector.detect(context, imageUri)
val landmarks = FaceAlignment.extractLandmarks(detectionResult)
if (landmarks != null && FaceAlignment.isValidLandmarks(landmarks)) {
    val alignedFace = FaceAlignment.alignFace(bitmap, landmarks)
    val feature = FaceRecognizer.extractFeature(alignedFace)
    if (feature != null) {
        val match = FaceRecognizer.matchStudent(feature, studentsList)
        if (match != null) {
            println("识别到学生: ${match.student.name}, 相似度: ${match.similarity}")
        }
    }
}
```

## 📊 预期性能指标

- **检测速度**: 2 FPS (500ms/帧)
- **识别延迟**: < 1 秒
- **内存占用**: < 200MB
- **识别准确率**: > 90% (取决于模型和录入质量)

## 🐛 潜在问题

1. **模型加载失败**: 检查文件名和路径
2. **JNI 链接错误**: 确认函数签名正确
3. **关键点为零**: SCRFD 模型不支持或检测失败
4. **识别准确率低**: 调整相似度阈值或录入多角度人脸

## 📚 参考资料

- [InsightFace](https://github.com/deepinsight/insightface)
- [NCNN](https://github.com/Tencent/ncnn)
- [SCRFD Paper](https://arxiv.org/abs/2105.04714)
- [MobileFaceNet Paper](https://arxiv.org/abs/1804.07573)
