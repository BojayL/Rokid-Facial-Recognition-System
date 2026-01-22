# Rokid 眼镜端应用安装与调试指南

本文档详细说明如何将 AR 智慧课堂眼镜端应用 (s-app) 安装到 Rokid AR 眼镜上，并进行调试测试。

## 一、准备工作

### 1.1 硬件准备

- **Rokid CXR-M 或 CXR-S AR 眼镜**
- **USB-C 数据线**（用于连接眼镜和电脑）
- **Android 手机**（用于安装手机端 m-app）
- **电脑**（macOS/Windows/Linux，已安装 Android Studio 或命令行工具）

### 1.2 软件准备

#### 安装 ADB (Android Debug Bridge)

**macOS (使用 Homebrew):**
```bash
brew install android-platform-tools
```

**或者下载 Android SDK Platform Tools:**
- 访问 https://developer.android.com/studio/releases/platform-tools
- 下载对应平台的 zip 包
- 解压并添加到系统 PATH

**验证安装:**
```bash
adb version
```

#### 安装 Java 11+

```bash
# macOS
brew install openjdk@11

# 验证
java -version
```

### 1.3 获取认证文件（重要）

Rokid SDK 需要 SN 认证文件才能正常通信。请从 [ar.rokid.com](https://ar.rokid.com) 账户中心获取：

1. 登录 Rokid 开发者平台
2. 进入「账户中心」→「设备管理」
3. 下载 SN 认证文件（通常命名为 `sn_xxx.bin`）
4. 获取 `Client Secret` 密钥

将 SN 文件放置到：
```
m-app/app/src/main/res/raw/sn_your_device.bin
```

并更新 `m-app/app/src/main/java/com/sustech/bojayL/rokid/RokidManager.kt` 中的常量：
```kotlin
const val CLIENT_SECRET = "your-client-secret-here"
```

---

## 二、眼镜端连接与设置

### 2.1 开启眼镜开发者模式

1. **开启眼镜电源**
2. 进入眼镜的「设置」应用
3. 找到「关于设备」或「About」
4. **连续点击「版本号」7 次**，开启开发者选项
5. 返回设置，进入「开发者选项」
6. 开启以下选项：
   - **USB 调试**（USB Debugging）
   - **通过 USB 安装应用**（Install via USB）

### 2.2 连接眼镜到电脑

1. 使用 USB-C 线连接眼镜和电脑
2. 眼镜上会弹出「允许 USB 调试」提示，点击「允许」
3. 建议勾选「始终允许此计算机」

### 2.3 验证连接

```bash
# 查看已连接的设备
adb devices

# 正常输出示例：
# List of devices attached
# RKXXXXXXXX    device
```

如果显示 `unauthorized`，请在眼镜上确认授权弹窗。

### 2.4 查看眼镜信息

```bash
# 查看设备型号
adb shell getprop ro.product.model

# 查看 Android 版本
adb shell getprop ro.build.version.release

# 查看屏幕分辨率
adb shell wm size
```

---

## 三、构建眼镜端应用 (s-app)

### 3.1 进入项目目录

```bash
cd /path/to/FReg/s-app
```

### 3.2 检查配置文件

确保 `s-app/gradle.properties` 存在且含有以下内容：

```properties
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

> ⚠️ **注意**：如果缺少 `android.useAndroidX=true`，构建会报错 `Configuration contains AndroidX dependencies, but the android.useAndroidX property is not enabled`。

### 3.3 构建 Debug APK

```bash
# 清理并构建
./gradlew clean assembleDebug

# 仅构建（不清理）
./gradlew assembleDebug
```

构建成功后，APK 位于：
```
s-app/app/build/outputs/apk/debug/app-debug.apk
```

### 3.3 构建 Release APK（可选）

```bash
./gradlew assembleRelease
```

---

## 四、安装应用到眼镜

### 4.1 使用 ADB 安装

```bash
# 安装 Debug 版本
adb install -r s-app/app/build/outputs/apk/debug/app-debug.apk

# -r 参数表示替换已安装的应用
```

### 4.2 使用 Gradle 一键安装

```bash
cd /Users/bojay.l/Developer/Rokid/FReg/s-app

# 构建并安装到已连接的设备
./gradlew installDebug
```

### 4.3 验证安装

```bash
# 列出已安装的应用包名
adb shell pm list packages | grep bojayL

# 应该看到：
# package:com.sustech.bojayL.glasses
```

### 4.4 启动应用

```bash
# 通过 ADB 启动应用
adb shell am start -n com.sustech.bojayL.glasses/.MainActivity
```

或者在眼镜的应用列表中找到「Glasses App」手动启动。

---

## 五、手机端应用安装 (m-app)

### 5.1 构建手机端应用

```bash
cd /Users/bojay.l/Developer/Rokid/FReg/m-app

# 构建
./gradlew assembleDebug

# 安装到手机（通过 USB 连接手机）
./gradlew installDebug
```

### 5.2 手机端权限设置

首次运行 m-app 时，需要授予以下权限：
- **蓝牙**（扫描和连接）
- **位置**（蓝牙扫描需要）
- **相机**（手机相机模式）

---

## 六、调试与日志查看

### 6.1 实时查看眼镜端日志

```bash
# 过滤显示应用相关日志
adb logcat | grep -E "(GlassesCamera|HudViewModel|GlassesBridge|KeyReceiver)"

# 或使用 tag 过滤
adb logcat -s GlassesCamera:* HudViewModel:* GlassesBridge:*
```

### 6.2 查看手机端日志

先断开眼镜，连接手机：
```bash
adb logcat | grep -E "(RokidService|RokidManager|RecognitionProcessor)"
```

### 6.3 同时调试多设备

如果电脑同时连接眼镜和手机：
```bash
# 查看所有设备
adb devices

# 指定设备执行命令
adb -s <眼镜设备ID> logcat
adb -s <手机设备ID> logcat
```

### 6.4 使用 Android Studio 调试

1. 打开 Android Studio
2. 选择 `File` → `Open` → 选择 `s-app` 目录
3. 等待 Gradle 同步完成
4. 点击 `Run` → `Debug 'app'`
5. 选择 Rokid 眼镜设备

---

## 七、端到端测试流程

### 7.1 测试准备

1. **眼镜端**：安装并启动 s-app
2. **手机端**：安装并启动 m-app
3. 确保眼镜和手机在同一 WiFi 网络下（蓝牙连接不需要）

### 7.2 蓝牙配对测试

1. 在手机 m-app 中，进入「设备」页面
2. 点击「扫描设备」按钮
3. 等待扫描结果，应该能看到 Rokid 眼镜
4. 点击设备进行连接
5. 观察连接状态变为「已连接」

**日志验证：**
```bash
# 手机端日志
adb logcat | grep "RokidManager"
# 应该看到：onConnected, Glass info 等日志
```

### 7.3 相机采集测试

1. 眼镜端 s-app 启动后，相机会自动初始化
2. 观察 HUD 状态栏显示「已连接」和「相机已就绪」

**触摸板操作：**
- **单击**：自动模式下开始/停止采集
- **长按**：切换自动/手动模式
- **双击**：重置状态

**日志验证：**
```bash
adb logcat | grep "GlassesCamera"
# 应该看到：Image captured: xxx bytes
```

### 7.4 识别流程测试

1. 确保手机端已连接眼镜
2. 在手机端「学生」页面，添加一个测试学生并录入人脸
3. 眼镜端开始采集，对准该学生
4. 观察：
   - 眼镜端 HUD 显示识别结果（绿色框 + 姓名）
   - 手机端「课堂」页面显示识别记录

**日志验证：**
```bash
# 眼镜端
adb logcat | grep "GlassesBridge"
# 应该看到：Sent recognition request

# 手机端
adb logcat | grep "RecognitionProcessor"
# 应该看到：Processing image, Feature extracted, Best match
```

### 7.5 配置同步测试

1. 在手机端「设备」页面修改参数（如识别阈值）
2. 观察眼镜端日志是否收到配置更新

```bash
adb logcat | grep "Config updated"
```

---

## 八、常见问题排查

### 8.1 ADB 无法识别眼镜

**问题**：`adb devices` 不显示设备或显示 `unauthorized`

**解决方案**：
1. 检查 USB 线是否支持数据传输（非充电线）
2. 在眼镜上确认「允许 USB 调试」弹窗
3. 尝试重启 ADB 服务：
   ```bash
   adb kill-server
   adb start-server
   adb devices
   ```
4. 尝试更换 USB 端口

### 8.2 应用安装失败

**问题**：`INSTALL_FAILED_UPDATE_INCOMPATIBLE`

**解决方案**：
```bash
# 先卸载旧版本
adb uninstall com.sustech.bojayL.glasses

# 重新安装
adb install -r app-debug.apk
```

### 8.2.1 构建失败：AndroidX 依赖问题

**问题**：`Configuration contains AndroidX dependencies, but the android.useAndroidX property is not enabled`

**解决方案**：
创建或检查 `s-app/gradle.properties` 文件，确保包含：
```properties
android.useAndroidX=true
```

### 8.2.2 构建失败：图标资源不存在

**问题**：`AAPT: error: resource mipmap/ic_launcher not found`

**解决方案**：
确保图标文件位于正确的 mipmap 目录：
```
res/
  mipmap-hdpi/
    ic_launcher.webp
    ic_launcher_round.webp
  mipmap-mdpi/
    ic_launcher.webp
    ic_launcher_round.webp
  mipmap-xhdpi/
    ...
```

如果图标文件直接在 `res/` 目录下，需要移动到对应的 `mipmap-*` 子目录。

### 8.3 相机初始化失败

**问题**：日志显示 `Failed to bind camera`

**解决方案**：
1. 检查眼镜是否授予相机权限
2. 在眼镜「设置」→「应用」→「Glasses App」→「权限」中手动授予
3. 或卸载重装应用，首次启动时授权

### 8.4 蓝牙连接失败

**问题**：扫描不到设备或连接失败

**解决方案**：
1. 确保手机蓝牙已开启
2. 检查手机是否授予蓝牙和位置权限
3. 检查 SN 认证文件是否正确配置
4. 查看日志中的错误码：
   ```bash
   adb logcat | grep "onFailed"
   # 常见错误：
   # SN_CHECK_FAILED - SN 文件问题
   # BLE_CONNECT_FAILED - 蓝牙连接问题
   ```

### 8.5 识别不工作

**问题**：图像发送但无结果返回

**排查步骤**：
1. 检查手机端是否连接成功
2. 检查学生底库是否有已录入人脸的学生
3. 查看 RecognitionProcessor 日志：
   ```bash
   adb logcat | grep "RecognitionProcessor"
   ```
4. 常见问题：
   - `Not initialized` - 处理器未初始化
   - `No face detected` - 图像中无人脸
   - 相似度低于阈值 - 调低识别阈值

---

## 九、性能优化建议

### 9.1 图像传输优化

当前使用 Base64 编码传输图像，如果延迟过高：
- 降低图像分辨率（修改 `GlassesCamera.CAPTURE_WIDTH/HEIGHT`）
- 降低 JPEG 质量（修改 `GlassesCamera.JPEG_QUALITY`）

### 9.2 采集间隔调整

自动模式下默认 2 秒采集一次，可通过以下方式调整：
- 手机端「设备」页面修改采集间隔
- 或在代码中修改 `GlassesCamera.captureIntervalMs`

### 9.3 电量优化

眼镜端长时间运行相机会消耗大量电量：
- 使用手动模式，仅在需要时采集
- 降低相机分辨率
- 课间休息时关闭采集

---

## 十、开发调试工具

### 10.1 scrcpy（屏幕镜像）

可以在电脑上实时查看眼镜屏幕：

```bash
# macOS 安装
brew install scrcpy

# 运行
scrcpy -s <眼镜设备ID>
```

### 10.2 截图和录屏

```bash
# 截图
adb shell screencap /sdcard/screenshot.png
adb pull /sdcard/screenshot.png ./

# 录屏
adb shell screenrecord /sdcard/video.mp4
# 按 Ctrl+C 停止
adb pull /sdcard/video.mp4 ./
```

### 10.3 无线 ADB 调试

避免 USB 线束缚：

```bash
# 先通过 USB 连接，然后开启 TCP/IP 模式
adb tcpip 5555

# 断开 USB，通过 WiFi 连接（需要知道眼镜 IP）
adb connect <眼镜IP>:5555

# 查看眼镜 IP
adb shell ip addr show wlan0
```

---

## 十一、发布构建

### 11.1 生成签名密钥

```bash
keytool -genkey -v -keystore glasses-release.keystore \
  -alias glasses -keyalg RSA -keysize 2048 -validity 10000
```

### 11.2 配置签名

在 `s-app/app/build.gradle.kts` 中添加签名配置：

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("glasses-release.keystore")
            storePassword = "your-password"
            keyAlias = "glasses"
            keyPassword = "your-password"
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

### 11.3 构建发布版本

```bash
./gradlew assembleRelease
```

---

## 附录 A：SDK 差异说明

手机端 (m-app) 和眼镜端 (s-app) 使用不同的 Rokid SDK：

| 项目 | SDK | 依赖 |
|------|-----|------|
| m-app | 手机端 SDK | `com.rokid.cxr:client-m:1.0.4` |
| s-app | 眼镜端 SDK | `com.rokid.cxr:cxr-service-bridge:1.0-SNAPSHOT` |

**API 差异注意事项**：

眼镜端 SDK 的 `Caps` 类与手机端略有不同：
- 手机端支持 `writeFloat()`
- 眼镜端需使用 `writeInt32(Float.floatToIntBits(value))` 代替

```kotlin
// 眼镜端写入 Float 的方法
Caps().apply {
    writeInt32(java.lang.Float.floatToIntBits(floatValue))
}
```

---

## 附录 B：快速命令参考

```bash
# === 设备管理 ===
adb devices                          # 查看设备
adb reboot                           # 重启设备
adb shell input keyevent 26          # 模拟电源键

# === 应用管理 ===
adb install -r app.apk               # 安装应用
adb uninstall <package>              # 卸载应用
adb shell pm list packages           # 列出所有应用

# === 日志查看 ===
adb logcat                           # 所有日志
adb logcat -c                        # 清空日志
adb logcat -s TAG:*                  # 按 TAG 过滤

# === 文件操作 ===
adb push local remote                # 推送文件到设备
adb pull remote local                # 从设备拉取文件

# === 调试 ===
adb shell am start -n package/.Activity  # 启动 Activity
adb shell am force-stop package          # 强制停止应用
```

---

**文档版本**: v1.1  
**最后更新**: 2026-01-15  
**适用应用**: s-app (眼镜端) + m-app (手机端)

**更新记录**：
- v1.1 (2026-01-15): 添加 gradle.properties 配置说明、图标资源问题排查、SDK API 差异说明
- v1.0 (2026-01-14): 初始版本
