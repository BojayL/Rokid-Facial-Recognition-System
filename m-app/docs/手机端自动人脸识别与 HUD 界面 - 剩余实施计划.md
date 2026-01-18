手机端自动人脸识别与 HUD 界面 - 剩余实施计划

📊 当前完成状态

✅ 已完成
| 阶段      | 任务                   | 文件                                 |
| --------- | ---------------------- | ------------------------------------ |
| Phase 1.1 | MobileFaceNet 模型下载 | assets/mobilefacenet-opt.param, .bin |
| Phase 1.2 | SCRFD 返回关键点       | scrfd_jni.cpp 已修改                 |
| Phase 1.3 | MobileFaceNet Native   | mobilefacenet.h, mobilefacenet.cpp   |
| Phase 1.4 | 人脸识别 JNI 接口      | face_recognition_jni.cpp             |
| Phase 1.5 | CMake 配置             | CMakeLists.txt                       |
| Phase 2.1 | FaceRecognizer.kt      | ml/FaceRecognizer.kt                 |
| Phase 2.2 | FaceAlignment.kt       | ml/FaceAlignment.kt                  |



📋 剩余任务详细清单

Phase 2 剩余: Kotlin 层完善

2.3 扩展 Student 模型
文件: data/model/Student.kt

修改内容:
kotlin
注意: 
•  需要添加扩展函数 List<Float>.toFloatArray() 用于转换
•  DataStore 序列化会自动支持 List<Float>

2.4 更新 InsightfaceNcnnDetector.kt
文件: ml/InsightfaceNcnnDetector.kt

修改内容:
1. 更新 FaceResult 数据类，添加关键点字段:
kotlin
2. 修改 detect() 和 detectFromBitmap() 方法，解析新的返回格式:
◦  每个人脸现在是 15 个值: [x, y, w, h, prob, lm1_x, lm1_y, ..., lm5_x, lm5_y]

2.5 创建 FaceTracker.kt (可选)
文件: ml/FaceTracker.kt

功能:
•  IoU (Intersection over Union) 匹配算法
•  跨帧人脸跟踪，分配稳定的 trackId
•  避免同一人脸重复识别



Phase 3: HUD UI 组件 (核心视觉)

3.1 创建 ReticleOverlay.kt
文件: ui/components/ReticleOverlay.kt

功能: 屏幕中心的十字准心
kotlin
样式: 
•  "+" 号十字线或断开的圆环
•  固定在屏幕正中央

3.2 改造 FaceDetectionOverlay.kt
文件: ui/components/FaceDetectionOverlay.kt

修改:
1. 将矩形框改为断角框（四个角的 L 型线条）
2. 添加状态颜色编码:
◦  绿色: 检测中
◦  青色: 已识别
◦  红色: 未知人员
3. 添加识别中的呼吸闪烁动画
kotlin
3.3 创建 FaceInfoBubble.kt
文件: ui/components/FaceInfoBubble.kt

功能: 跟随人脸的信息气泡卡片
kotlin
样式:
•  镂空边框设计（绿色线框，无填充背景）
•  姓名大号加粗
•  班级 + 状态图标小号
•  位置跟随人脸框下方

3.4 创建 HudStatusBar.kt
文件: ui/components/HudStatusBar.kt

功能: 顶部状态栏（HUD 风格）
•  极小字号显示: WiFi 图标、电量、识别统计
•  线性图标风格



Phase 4: 识别流程集成 (核心逻辑)

4.1 改造 PhoneCameraRecognitionScreen.kt
文件: ui/screens/classroom/PhoneCameraRecognitionScreen.kt

完整重构:
kotlin
新增状态模型:
kotlin
4.2 更新 ClassroomViewModel.kt
文件: ui/screens/classroom/ClassroomViewModel.kt

新增功能:
1. 自动识别逻辑:
kotlin
2. 去重机制（30秒冷却）:
kotlin
3. 添加检测人脸列表状态:
kotlin
4.3 更新 CameraFaceDetector.kt
文件: ml/CameraFaceDetector.kt

修改:
1. 返回关键点数据
2. 集成特征提取和学生匹配
3. 更新 FaceResult 包含 recognizedStudent



Phase 5: 测试与验证

5.1 编译验证
bash
5.2 功能测试清单
Native 库加载成功 (logcat: "SCRFD initialized")
MobileFaceNet 加载成功 (logcat: "MobileFaceNet initialized")
人脸录入时提取并保存特征向量
相机识别时自动匹配学生
HUD UI 正确显示（准心、断角框、信息气泡）
去重机制生效（30秒内不重复）
多人脸支持（最多5个）



📁 文件清单总览

需要创建的文件
| 文件路径                        | 用途              |
| ------------------------------- | ----------------- |
| ui/components/ReticleOverlay.kt | 十字准心          |
| ui/components/FaceInfoBubble.kt | 信息气泡          |
| ui/components/HudStatusBar.kt   | HUD 状态栏        |
| ml/FaceTracker.kt               | 多人脸跟踪 (可选) |

需要修改的文件
| 文件路径                                             | 修改内容              |
| ---------------------------------------------------- | --------------------- |
| data/model/Student.kt                                | 添加 faceFeature 字段 |
| ml/InsightfaceNcnnDetector.kt                        | 解析关键点数据        |
| ml/CameraFaceDetector.kt                             | 集成识别逻辑          |
| ui/components/FaceDetectionOverlay.kt                | 断角框样式            |
| ui/screens/classroom/PhoneCameraRecognitionScreen.kt | 集成 HUD              |
| ui/screens/classroom/ClassroomViewModel.kt           | 自动识别逻辑          |
| ui/screens/students/FaceEnrollmentScreen.kt          | 录入时保存特征        |



⚠️ 关键注意事项

1. 模型输入/输出层名称
◦  检查 mobilefacenet-opt.param 的输入层名（可能是 data 或 input）
◦  输出层名（可能是 fc1 或 embedding）
◦  如不匹配需修改 mobilefacenet.cpp 第 100, 105 行
2. 坐标系转换
◦  相机预览坐标 → 屏幕坐标
◦  图像坐标 → Canvas 坐标
◦  前后摄像头镜像处理
3. 性能优化
◦  检测频率: 2 FPS (500ms/帧)
◦  识别仅对稳定人脸执行（连续出现3帧）
◦  Bitmap 及时回收
4. HUD 设计原则
◦  高对比度: 纯绿色 #00FF00
◦  线条化: 使用 Icons.Outlined 系列
◦  避让中心: 信息在四周显示



🎯 建议执行顺序

1. Phase 2.3-2.4: Student 模型 + InsightfaceNcnnDetector 更新
2. Phase 3: HUD UI 组件（可并行开发）
3. Phase 4.1: PhoneCameraRecognitionScreen 集成
4. Phase 4.2-4.3: ViewModel 和 CameraFaceDetector
5. Phase 5: 测试验证