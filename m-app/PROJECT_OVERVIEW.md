# mobile-app 项目技术概述

## 项目简介
这是一个基于 Android 平台的移动应用项目，使用 Kotlin 语言和 Jetpack Compose UI 框架构建。应用采用 Material Design 3 设计规范，支持自适应导航。

**项目基本信息：**
- 包名：`com.sustech.bojayL`
- 最低 SDK 版本：26 (Android 8.0)
- 目标 SDK 版本：36
- JVM 版本：11
- Kotlin 版本：2.0.21
- AGP 版本：8.13.2

## 核心组件

### 1. MainActivity
**职责：** 应用的主入口 Activity

**位置：** `app/src/main/java/com/sustech/bojayL/MainActivity.kt`

**关键功能：**
- 继承自 `ComponentActivity`
- 启用边到边（Edge-to-Edge）显示
- 设置 Compose 内容并应用主题
- 托管主应用组件 `MobileappApp`

**关键类和函数：**
- `MainActivity` 类：应用的主 Activity
- `MobileappApp()` Composable 函数：应用的根 UI 组件
- `AppDestinations` 枚举类：定义导航目的地（HOME、FAVORITES、PROFILE）
- `Greeting()` Composable 函数：示例问候界面组件

### 2. UI 主题系统
**职责：** 管理应用的视觉样式和主题

**位置：** `app/src/main/java/com/sustech/bojayL/ui/theme/`

**包含模块：**

#### Theme.kt
- 定义深色和浅色主题配色方案
- 支持 Android 12+ 动态颜色（Dynamic Color）
- `MobileappTheme()` 可组合函数提供主题包装器

#### Color.kt
- 定义主题颜色常量
- 包含 Material 3 颜色系统：Purple80/40、PurpleGrey80/40、Pink80/40

#### Type.kt
- 定义 Material 3 排版样式
- 配置字体系列、字重、字号、行高和字间距

**设计模式：**
- 采用 Material Design 3 设计系统
- 支持主题响应式切换（深色/浅色模式）

### 3. 导航系统
**职责：** 管理应用内的页面导航

**实现方式：**
- 使用 `NavigationSuiteScaffold` 实现自适应导航
- 支持底部导航栏/侧边导航栏自动适配不同屏幕尺寸
- 使用 `rememberSaveable` 保存导航状态

**导航目的地：**
- HOME：主页（Home 图标）
- FAVORITES：收藏（Favorite 图标）
- PROFILE：个人资料（AccountBox 图标）

### 4. 测试组件
**位置：**
- 单元测试：`app/src/test/java/com/sustech/bojayL/`
- UI 测试：`app/src/androidTest/java/com/sustech/bojayL/`

## 组件交互

### 数据流
```
MainActivity.onCreate()
    ↓
启用 EdgeToEdge
    ↓
setContent { MobileappTheme { ... } }
    ↓
MobileappApp() - 根 Composable
    ↓
NavigationSuiteScaffold - 自适应导航容器
    ↓
Scaffold - 页面脚手架
    ↓
Greeting - 当前页面内容
```

### 控制流
1. **应用启动：** MainActivity 的 `onCreate()` 初始化应用
2. **主题应用：** MobileappTheme 根据系统设置应用主题
3. **导航管理：** 用户点击导航项 → 更新 `currentDestination` 状态 → UI 重组显示对应内容
4. **状态保存：** 使用 `rememberSaveable` 在配置变更时保持导航状态

### 通信机制
- **组件通信：** Compose 状态管理（State Hoisting）
- **UI 更新：** 响应式状态变化触发重组（Recomposition）
- **导航控制：** 枚举类型 + 状态变量实现类型安全的导航

### 架构模式
- **声明式 UI：** 使用 Jetpack Compose 声明式编程模型
- **单一 Activity 架构：** 所有 UI 在一个 Activity 中通过 Compose 管理
- **状态提升：** 将状态提升到合适的层级以实现共享和管理

## 部署架构

### 构建系统
**工具：** Gradle 8.x + Kotlin DSL

**关键配置文件：**
- `build.gradle.kts`（根目录）：项目级配置
- `app/build.gradle.kts`：应用模块配置
- `settings.gradle.kts`：项目设置和模块包含
- `gradle/libs.versions.toml`：集中式依赖版本管理

### 构建步骤
1. **清理：** `./gradlew clean`
2. **编译：** `./gradlew assembleDebug` (Debug) 或 `./gradlew assembleRelease` (Release)
3. **安装：** `./gradlew installDebug`
4. **测试：** 
   - 单元测试：`./gradlew test`
   - UI 测试：`./gradlew connectedAndroidTest`

### 核心依赖

**AndroidX 库：**
- `androidx.core:core-ktx:1.10.1` - Kotlin 扩展
- `androidx.lifecycle:lifecycle-runtime-ktx:2.6.1` - 生命周期管理
- `androidx.activity:activity-compose:1.8.0` - Compose Activity 支持

**Compose 生态：**
- `androidx.compose:compose-bom:2024.09.00` - Compose 版本管理
- `androidx.compose.ui:ui` - UI 组件
- `androidx.compose.material3:material3` - Material 3 组件
- `androidx.compose.material3:material3-adaptive-navigation-suite` - 自适应导航

**测试框架：**
- `junit:junit:4.13.2` - 单元测试
- `androidx.test.ext:junit:1.1.5` - Android 测试扩展
- `androidx.test.espresso:espresso-core:3.5.1` - UI 测试

### 构建类型
**Debug 构建：**
- 不启用混淆和优化
- 包含调试工具和测试清单

**Release 构建：**
- 可选的代码混淆（当前禁用）
- ProGuard 规则在 `proguard-rules.pro` 中配置

### 环境要求
- **开发环境：** Android Studio Hedgehog+ (推荐最新版本)
- **JDK：** JDK 11+
- **Android SDK：** API 26+ (编译 SDK 36)
- **Gradle：** 通过 Gradle Wrapper 管理（包含在项目中）

### 依赖管理
采用 **版本目录（Version Catalog）** 方式管理依赖：
- 集中在 `gradle/libs.versions.toml` 中定义版本
- 使用 `libs.xxx` 引用依赖，提高可维护性

### 构建优化配置
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
```

## 运行时行为

### 应用初始化流程
1. **系统启动：** Android 系统创建应用进程
2. **Application 创建：** 系统创建默认 Application 对象
3. **MainActivity 创建：**
   - `onCreate()` 生命周期方法被调用
   - 调用 `enableEdgeToEdge()` 启用全屏显示
   - 调用 `setContent {}` 设置 Compose UI 树
4. **UI 初始化：**
   - `MobileappTheme` 应用主题（根据系统深色模式和动态颜色设置）
   - `MobileappApp` 初始化导航状态
   - 渲染初始 UI（默认 HOME 页面）

### 请求处理
**导航请求处理：**
1. 用户点击导航项
2. `onClick` 回调被触发
3. 更新 `currentDestination` 状态变量
4. Compose 检测到状态变化
5. 触发相关组件的重组（Recomposition）
6. UI 更新显示新页面内容

**UI 交互流程：**
- 所有 UI 交互通过 Compose 的事件系统
- 事件处理器（onClick、onChange 等）修改状态
- 状态变化自动触发 UI 重组

### 业务工作流
**当前实现：**
- 应用目前是基础骨架，包含三个导航目的地
- 所有页面当前显示相同的示例内容（Greeting 组件）
- 导航状态通过 `rememberSaveable` 在配置变更时保持

**扩展点：**
- 可以为每个导航目的地添加独立的内容组件
- 可以集成 ViewModel 进行业务逻辑和数据管理
- 可以添加数据层（Repository、DataSource）进行数据操作

### 错误处理
**当前状态：**
- 使用 Android 默认的崩溃处理
- Compose 提供内置的错误边界处理

**生命周期管理：**
- Activity 生命周期由 ComponentActivity 管理
- Compose UI 生命周期与 Activity 生命周期绑定
- 使用 `rememberSaveable` 保存和恢复 UI 状态

### 后台任务
**当前实现：**
- 项目未实现后台任务或服务
- 所有操作在主线程的 UI 生命周期内完成

**潜在扩展：**
- 可以使用 WorkManager 进行后台任务调度
- 可以使用 Kotlin Coroutines 进行异步操作
- 可以使用 Service 或 Foreground Service 处理长时间运行任务

### 内存管理
- Compose 自动管理 UI 组件生命周期
- 状态变量在配置变更时通过 `rememberSaveable` 保存
- Activity 重建时自动恢复状态

### 性能特性
- **边到边显示：** 充分利用屏幕空间
- **自适应导航：** 根据屏幕尺寸自动选择最佳导航模式
- **动态主题：** Android 12+ 支持根据壁纸动态生成主题色
- **声明式 UI：** Compose 提供高效的 UI 更新机制

## 项目结构
```
m-app/
├── app/
│   ├── build.gradle.kts          # 应用模块构建配置
│   ├── proguard-rules.pro        # ProGuard 混淆规则
│   └── src/
│       ├── androidTest/          # Android 仪器化测试
│       ├── test/                 # 单元测试
│       └── main/
│           ├── AndroidManifest.xml
│           ├── assets/                  # 模型文件
│           │   └── scrfd_2.5g_kps-opt2.* # SCRFD 人脸检测模型
│           ├── java/com/sustech/bojayL/
│           │   ├── MainActivity.kt       # 主入口
│           │   ├── ml/                   # 机器学习模块
│           │   │   ├── FaceDetector.kt           # 人脸检测接口
│           │   │   ├── FaceDetectorMlKit.kt      # MLKit 实现
│           │   │   └── InsightfaceNcnnDetector.kt # SCRFD/NCNN 实现
│           │   └── ui/theme/            # UI 主题
│           │       ├── Color.kt
│           │       ├── Theme.kt
│           │       └── Type.kt
│           ├── jni/                     # Native JNI 模块
│           │   ├── CMakeLists.txt       # CMake 构建配置
│           │   ├── scrfd.cpp/h          # SCRFD 检测核心
│           │   ├── scrfd_jni.cpp        # JNI 接口
│           │   ├── ncnn-*/              # NCNN 库
│           │   └── opencv-mobile-*/     # OpenCV Mobile 库
│           └── res/                     # 资源文件
│               ├── drawable/
│               ├── mipmap-*/           # 应用图标
│               ├── values/             # 字符串、样式等
│               └── xml/                # 备份规则等
├── gradle/
│   ├── libs.versions.toml        # 依赖版本目录
│   └── wrapper/                  # Gradle Wrapper
├── build.gradle.kts              # 根项目构建配置
├── settings.gradle.kts           # 项目设置
├── gradle.properties             # Gradle 属性配置
├── gradlew                       # Gradle Wrapper 脚本 (Unix)
└── gradlew.bat                   # Gradle Wrapper 脚本 (Windows)
```

## 技术栈总结

**编程语言：** Kotlin 2.0.21

**UI 框架：** Jetpack Compose (Material 3)

**架构组件：**
- Activity (ComponentActivity)
- ViewModel (可扩展)
- Lifecycle

**机器学习：**
- InsightFace SCRFD (NCNN) - 高精度人脸检测
- Google MLKit Face Detection - 回退方案

**Native 层：**
- NCNN (Vulkan 加速)
- OpenCV Mobile
- CMake + NDK

**依赖管理：** Gradle Version Catalog

**测试框架：** JUnit + Espresso

**设计系统：** Material Design 3 + Adaptive Navigation

## 开发建议

### 后续扩展方向
1. **导航实现：** 为每个导航目的地创建独立的屏幕组件
2. **数据层：** 添加 ViewModel、Repository 和数据源
3. **依赖注入：** 考虑集成 Hilt 或 Koin
4. **网络层：** 集成 Retrofit/Ktor 进行 API 调用
5. **本地存储：** 集成 Room 或 DataStore
6. **异步处理：** 使用 Kotlin Coroutines 和 Flow

### 最佳实践
- 遵循单一数据源原则（Single Source of Truth）
- 使用 Repository 模式分离数据层
- 采用 MVVM 或 MVI 架构模式
- 编写单元测试和 UI 测试
- 使用 Kotlin Coroutines 处理异步操作

---

*文档生成时间：2026-01-12*
*项目版本：1.0*
