# FReg - AR 智慧课堂伴侣
  基于人脸识别的 AR 智慧课堂学生考勤与互动系统



<p align="center">
  <a href="#功能特性">功能特性</a> •
  <a href="#系统架构">系统架构</a> •
  <a href="#快速开始">快速开始</a> •
  <a href="#使用指南">使用指南</a> •
  <a href="#开发指南">开发指南</a>
</p>

---

## 项目简介

FReg 是一款专为 Rokid AR 眼镜设计的智慧课堂解决方案，通过人脸识别技术实现学生自动考勤和身份识别。系统由两个 Android 应用组成：

| 应用 | 运行设备 | 主要功能 |
|------|----------|----------|
| **m-app** | 手机 | 人脸录入、学生管理、模板同步、参数下发、结果展示 |
| **s-app** | Rokid AR 眼镜 | 图像采集、端侧识别、AR 界面展示、手势交互 |

## 功能特性

### 🎯 核心功能

- **实时端侧人脸识别** - 眼镜端执行 BlazeFace + FaceNet-MobileNetV2(256d)
- **AR HUD 显示** - 在眼镜端实时叠加显示识别结果
- **学生管理** - 完整的学生信息与人脸特征库管理
- **自动考勤** - 识别结果自动记录为考勤数据

### 📱 手机端 (m-app)

- 学生信息管理（增删改查）
- 高质量人脸采集与特征提取
- 设备连接与状态管理
- 识别结果详情展示

### 👓 眼镜端 (s-app)

- CameraX 实时图像采集
- 自动/手动两种采集模式
- AR 界面显示识别结果
- 触控板手势控制

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                          m-app (手机)                           │
│  ┌───────────────┐    ┌──────────────────────┐                 │
│  │ StudentRepository│──▶ RokidMessageHandler │                 │
│  │ (人脸模板库)      │    │ (模板同步/参数下发) │                 │
│  └───────────────┘    └──────────┬───────────┘                 │
└──────────────────────────────────┼──────────────────────────────┘
                                   │ 模板/配置
                                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                        s-app (眼镜)                             │
│  CameraX → BlazeFace → FaceAlign(112) → FaceNet-MBV2(256, INT8/SNPE) │
│           ↓                                                 │    │
│        Cosine Match (本地模板) ───────────────▶ HudScreen  │    │
│           │                                                 │    │
│           └────────────── glass_result ─────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### ML Pipeline

```
眼镜图像输入
    ↓
[BlazeFaceDetector]
    │  主路径: ML Kit BlazeFace
    │  回退: SCRFD (NCNN)
    ↓
[FaceAligner]
    │  5点关键点 → 112x112 对齐人脸
    ↓
[FaceNet-MobileNetV2]
    │  主路径: SNPE INT8 → 256维特征
    │  回退: MobileFaceNet NCNN → 规整到256维
    ↓
余弦相似度匹配 (阈值: 0.7)
    ↓
端侧结果显示 + 上报手机端
```

## 快速开始

### 环境要求

- **Android Studio**: Hedgehog 或更新版本
- **JDK**: 11+
- **Android SDK**: API 26+ (m-app), API 31+ (s-app)
- **NDK**: 用于编译 NCNN 原生代码

### 1. 克隆仓库

```bash
git clone https://github.com/your-org/FReg.git
cd FReg
```

### 2. 下载模型文件

```bash
cd m-app
./scripts/download_mobilefacenet.sh
```

或手动下载 MobileFaceNet 模型，放置于 `m-app/app/src/main/assets/`:
- `mobilefacenet-opt.param`
- `mobilefacenet-opt.bin`

并放置 SNPE INT8 DLC（用于 256 维模板与端侧主链路）到两个端：
- `m-app/app/src/main/assets/facenet_mobilenetv2_256_int8.dlc`
- `s-app/app/src/main/assets/facenet_mobilenetv2_256_int8.dlc`

> SCRFD 与 NCNN MobileFaceNet 回退模型已包含在仓库中。
> 模板同步现已严格校验：仅 `FaceNet-MobileNetV2 256d + SNPE` 模型模板会下发到眼镜端。

### 3. 配置 Rokid SDK

1. 访问 [ar.rokid.com](https://ar.rokid.com) → 账号 → 设备管理
2. 下载 SN 文件 `sn_xxx.bin`
3. 放置于 `m-app/app/src/main/res/raw/`
4. 更新 `m-app/.../rokid/RokidManager.kt` 中的 `CLIENT_SECRET`

### 4. 编译安装

```bash
# 编译手机端
m-app/gradlew -p m-app assembleDebug

# 编译眼镜端
s-app/gradlew -p s-app assembleDebug

# 安装到设备
m-app/gradlew -p m-app installDebug
s-app/gradlew -p s-app installDebug
```

## 使用指南

### 配对流程

1. **启动眼镜端应用** - 眼镜显示 6 位配对码
2. **启动手机端应用** - 进入「设备」页面
3. **扫描并连接** - 选择眼镜设备，输入配对码
4. **配对成功** - 双方进入识别模式

### 学生录入

1. 手机端进入「学生」页面
2. 点击添加学生，填写基本信息
3. 进入人脸采集，拍摄正面照片
4. 系统自动提取 512 维人脸特征

### 课堂使用

1. 教师佩戴 AR 眼镜，手机置于讲台
2. 眼镜自动/手动采集图像
3. 手机端处理识别，结果实时显示在眼镜 HUD
4. 识别结果自动记录考勤

### 手势控制 (眼镜端)

| 手势 | 功能 |
|------|------|
| 单击 | 手动模式下触发采集 / 自动模式下暂停/恢复 |
| 双击 | 重置状态 |
| 长按 | 切换自动/手动模式 |
| 前滑/后滑 | 预留功能 |

## 开发指南

### 项目结构

```
FReg/
├── m-app/                      # 手机端应用
│   ├── app/src/main/
│   │   ├── java/.../           # Kotlin 源码
│   │   │   ├── ml/             # ML 模块 (检测/识别)
│   │   │   ├── rokid/          # Rokid SDK 封装
│   │   │   ├── data/           # 数据层 (Repository)
│   │   │   └── ui/             # Compose UI
│   │   ├── jni/                # Native 层 (NCNN/OpenCV)
│   │   └── assets/             # 模型文件
│   └── scripts/                # 工具脚本
├── s-app/                      # 眼镜端应用
│   └── app/src/main/java/.../
│       ├── camera/             # CameraX 采集
│       ├── communication/      # 通信协议
│       ├── input/              # 手势输入
│       └── ui/                 # Compose HUD
├── docs/                       # 设计文档
├── WARP.md                     # AI 开发助手指南
└── DEBUG_GUIDE.md              # 调试指南
```

### 常用命令

```bash
# 构建
m-app/gradlew -p m-app assembleDebug
s-app/gradlew -p s-app assembleDebug

# 测试
m-app/gradlew -p m-app test
m-app/gradlew -p m-app test --tests "com.sustech.bojayL.ExampleUnitTest"

# Lint
m-app/gradlew -p m-app lint
s-app/gradlew -p s-app lint

# 指定设备安装
m-app/gradlew -p m-app installDebug -PdeviceSerial=<serial>
adb -s <serial> install -r m-app/app/build/outputs/apk/debug/app-debug.apk
```

### 调试

```bash
# 手机端日志
adb logcat | grep -E "(RokidService|RokidManager|RecognitionProcessor)"

# 眼镜端日志
adb logcat | grep -E "(GlassesCamera|HudViewModel|GlassesBridge)"

# 通信协议调试
adb logcat | grep -E "(Received message|Sent.*result)"
```

详细调试指南请参阅 [DEBUG_GUIDE.md](DEBUG_GUIDE.md)。

### 通信协议

**消息类型:**

| Key | 方向 | 描述 |
|-----|------|------|
| `glass_result` | 眼镜 → 手机 | 端侧识别结果 |
| `glass_status` | 眼镜 → 手机 | 状态信息 (电量/模式) |
| `glass_pairing_code` | 眼镜 → 手机 | 配对码 |
| `phone_template_sync` | 手机 → 眼镜 | 人脸模板分片同步 |
| `phone_config` | 手机 → 眼镜 | 配置参数 |

**注意:** Rokid SDK 缺少 `writeFloat()` 方法，需使用:
```kotlin
writeInt32(java.lang.Float.floatToIntBits(floatValue))
```

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| 相机 | CameraX 1.3.1 |
| ML | SNPE(INT8) + NCNN(Fallback) + MLKit BlazeFace |
| 人脸检测 | BlazeFace (主) + SCRFD (回退) |
| 人脸识别 | FaceNet-MobileNetV2 256d (主) |
| 通信 | Rokid CxrApi SDK |
| 存储 | DataStore + kotlinx.serialization |

## 目录说明

| 目录 | 说明 |
|------|------|
| `m-app/` | 手机端 Android 应用 |
| `s-app/` | 眼镜端 Android 应用 |
| `docs/` | PRD、UI 设计等文档 |
| `Rokid SDK DEMO/` | SDK 示例代码参考 |



## 使用到的库

- [NCNN](https://github.com/Tencent/ncnn) - 高性能神经网络推理框架
- [InsightFace](https://github.com/deepinsight/insightface) - SCRFD 人脸检测模型
- [MobileFaceNet](https://github.com/AlfredXiangWu/MobileFaceNet) - 轻量级人脸识别模型
- [Rokid](https://www.rokid.com/) - AR 眼镜硬件与 SDK

