

# Rokid SDK DEMO 使用指南

## 一、项目概述

Rokid SDK DEMO 包含两个独立的 Android 项目，分别用于**手机端**和**眼镜端**的 SDK 开发：

| 项目               | 用途                 | SDK 依赖                                   |
| ------------------ | -------------------- | ------------------------------------------ |
| **CXRMSamples**    | 手机端 App（控制器） | `com.rokid.cxr:client-m:1.0.4`             |
| **CXRSSDKSamples** | 眼镜端 App           | `com.rokid.cxr:cxr-service-bridge:1.0-xxx` |

---

## 二、开发环境配置

### 2.1 Maven 仓库配置

在 `settings.gradle.kts` 中添加 Rokid Maven 仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.rokid.com/repository/maven-public/") }
        google()
        mavenCentral()
    }
}
```
### 2.2 依赖配置

**手机端 SDK：**
```kotlin
implementation("com.rokid.cxr:client-m:1.0.4") {
    exclude(group = "com.rokid.cxr", module = "client-m-sources")
}
```
**眼镜端 SDK：**
```kotlin
implementation("com.rokid.cxr:cxr-service-bridge:1.0-20250519.061355-45")
```
### 2.3 最低 SDK 要求

- `minSdk = 31` (Android 12)
- `targetSdk = 36`
- `compileSdk = 36`

---

## 三、手机端 SDK (CXRMSamples) 详解

### 3.1 权限配置

在 `AndroidManifest.xml` 中需要声明以下权限：

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.INTERNET" />
```
### 3.2 认证配置

在使用 SDK 前需要配置认证信息（从 https://ar.rokid.com/ 账户中心获取）：

```kotlin
object CONSTANT {
    // 蓝牙服务 UUID
    const val SERVICE_UUID = "00009100-0000-1000-8000-00805f9b34fb"
    
    // Client Secret - 从 ar.rokid.com 账户中心获取
    const val CLIENT_SECRET = "your-client-secret"
    
    // SN 认证文件资源 ID (放在 res/raw/ 目录)
    fun getSNResource() = R.raw.sn_your_file
}
```
### 3.3 蓝牙连接流程

#### 步骤 1：BLE 扫描

```kotlin
// 创建扫描过滤器
val filter = ScanFilter.Builder()
    .setServiceUuid(ParcelUuid.fromString(CONSTANT.SERVICE_UUID))
    .build()
    
val scanSettings = ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
    .build()

// 开始扫描
bleScanner?.startScan(mutableListOf(filter), scanSettings, scanCallback)
```
#### 步骤 2：初始化蓝牙连接

```kotlin
// 实现连接状态回调
val connectionState = object : BluetoothStatusCallback {
    override fun onConnectionInfo(uuid: String?, macAddress: String?, p2: String?, glassesType: Int) {
        // uuid: 设备 UUID
        // macAddress: MAC 地址
        // glassesType: 0=无屏眼镜, 1=有屏眼镜
    }
    
    override fun onConnected() {
        // 连接成功
    }
    
    override fun onDisconnected() {
        // 断开连接
    }
    
    override fun onFailed(errorCode: ValueUtil.CxrBluetoothErrorCode?) {
        // 连接失败
        // PARAM_INVALID: 参数无效
        // BLE_CONNECT_FAILED: BLE连接失败
        // SOCKET_CONNECT_FAILED: Socket连接失败
        // SN_CHECK_FAILED: SN校验失败
    }
}

// 初始化蓝牙（点击设备后调用）
CxrApi.getInstance().initBluetooth(context, bluetoothDevice, connectionState)
```
#### 步骤 3：建立 Socket 连接

```kotlin
// 读取 SN 认证文件
val snBytes = context.resources.openRawResource(CONSTANT.getSNResource()).readBytes()

// 连接蓝牙 Socket
CxrApi.getInstance().connectBluetooth(
    context,
    uuid,                                           // 设备 UUID
    macAddress,                                     // MAC 地址
    connectionState,                                // 回调
    snBytes,                                        // SN 认证文件
    CONSTANT.CLIENT_SECRET.replace("-", "")        // Client Secret
)
```
#### 步骤 4：断开连接

```kotlin
CxrApi.getInstance().deinitBluetooth()
```
### 3.4 获取设备信息

```kotlin
// 获取所有设备信息
CxrApi.getInstance().getGlassInfo(GlassInfoResultCallback { status, glassInfo ->
    if (status == ValueUtil.CxrStatus.RESPONSE_SUCCEED) {
        glassInfo?.let { info ->
            val deviceName = info.deviceName        // 设备名称
            val deviceId = info.deviceId            // 设备 SN
            val systemVersion = info.systemVersion  // 系统版本
            val wearingStatus = info.wearingStatus  // 佩戴状态 ("1"=佩戴中)
            val brightness = info.brightness        // 亮度 (0-15)
            val volume = info.volume                // 音量 (0-100)
            val batteryLevel = info.batteryLevel    // 电量
            val isCharging = info.isCharging        // 是否充电中
        }
    }
})
```
### 3.5 监听设备状态变化

```kotlin
// 电量监听
CxrApi.getInstance().setBatteryLevelUpdateListener(
    BatteryLevelUpdateListener { level, isCharging ->
        // level: 电量百分比
        // isCharging: 是否充电
    }
)

// 亮度监听
CxrApi.getInstance().setBrightnessUpdateListener(
    BrightnessUpdateListener { level ->
        // level: 亮度等级 (0-15)
    }
)

// 音量监听
CxrApi.getInstance().setVolumeUpdateListener(
    VolumeUpdateListener { level ->
        // level: 音量百分比 (0-100)
    }
)

// 屏幕状态监听
CxrApi.getInstance().setScreenStatusUpdateListener(
    ScreenStatusUpdateListener { isScreenOn ->
        // isScreenOn: 屏幕是否开启
    }
)
```
### 3.6 控制眼镜设备

```kotlin
// 设置亮度 (0-15)
CxrApi.getInstance().setGlassBrightness(level)

// 设置音量 (0-15)
CxrApi.getInstance().setGlassVolume(level)

// 关闭屏幕
CxrApi.getInstance().notifyGlassScreenOff()

// 设置通讯设备（用于音频输出到眼镜）
CxrApi.getInstance().setCommunicationDevice()
CxrApi.getInstance().clearCommunicationDevice()
```
### 3.7 音频录制与拾音

```kotlin
// 设置音频流监听器
CxrApi.getInstance().setAudioStreamListener(object : AudioStreamListener {
    override fun onStartAudioStream(codeType: Int, streamType: String?) {
        // codeType: 1 = PCM 16Bit 16KHz 单声道
        // streamType: 流名称
    }
    
    override fun onAudioStream(data: ByteArray?, offset: Int, size: Int) {
        // 音频数据回调，保存到文件
        val realBytes = data?.copyOfRange(offset, offset + size)
    }
})

// 开始音频录制
CxrApi.getInstance().openAudioRecord(1, "audio_stream")

// 停止音频录制
CxrApi.getInstance().closeAudioRecord("audio_stream")

// 切换拾音场景
// 0: 近场, 1: 远场, 2: 全景
CxrApi.getInstance().changeAudioSceneId(sceneId) { id, success ->
    // 切换完成回调
}
```
### 3.8 拍照功能

```kotlin
// 设置拍照参数
CxrApi.getInstance().setPhotoParams(width, height)

// 拍照（眼镜相机旋转90°，width为实际高度，height为实际宽度）
CxrApi.getInstance().takeGlassPhotoGlobal(
    width,      // 图片高度
    height,     // 图片宽度
    100,        // 质量 (0-100)
    PhotoResultCallback { status, imageData ->
        if (status == ValueUtil.CxrStatus.RESPONSE_SUCCEED) {
            // imageData 是 WebP 格式图片
            val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        }
    }
)
```
### 3.9 视频录制控制

```kotlin
// 设置视频参数
CxrApi.getInstance().setVideoParams(
    duration,       // 时长
    30,             // 帧率
    width,          // 宽度
    height,         // 高度
    timeUnit        // 时间单位: 0=分钟, 1=秒
)

// 控制视频录制场景
CxrApi.getInstance().controlScene(
    ValueUtil.CxrSceneType.VIDEO_RECORD, 
    true,   // true=开始, false=停止
    null
)

// 监听场景状态
CxrApi.getInstance().setSceneStatusUpdateListener(
    SceneStatusUpdateListener { status ->
        val isVideoRecording = status?.isVideoRecordRunning
        val isAudioRecording = status?.isAudioRecordRunning
    }
)
```
### 3.10 自定义协议通信

```kotlin
// 发送自定义命令
val caps = Caps().apply {
    write("String Message")
    writeInt32(123)
    write(true)
    write(Caps().apply {
        write("Nested Message")
    })
}
CxrApi.getInstance().sendCustomCmd("Custom Message", caps)

// 接收自定义命令
CxrApi.getInstance().setCustomCmdListener(CustomCmdListener { cmdKey, caps ->
    if (cmdKey == "rk_custom_key") {
        // 解析 caps 数据
        for (i in 0 until caps.size()) {
            val value = caps.at(i)
            when (value.type()) {
                Caps.Value.TYPE_STRING -> value.string
                Caps.Value.TYPE_INT32 -> value.int
                Caps.Value.TYPE_FLOAT -> value.float
                Caps.Value.TYPE_OBJECT -> parseCaps(value.`object`)
                // ...
            }
        }
    }
})
```
---

## 四、眼镜端 SDK (CXRSSDKSamples) 详解

### 4.1 权限配置

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```
### 4.2 自定义命令通信

```kotlin
// 创建 CXRServiceBridge 实例
private val cxrServiceBridge: CXRServiceBridge = CXRServiceBridge()

// 设置连接状态监听
cxrServiceBridge.setStatusListener(object : CXRServiceBridge.StatusListener {
    override fun onConnected(p0: String?, p1: Int) {
        // 连接成功
    }
    override fun onDisconnected() {
        // 断开连接
    }
    override fun onARTCStatus(p0: Float, p1: Boolean) {
        // ARTC 状态（暂不使用）
    }
})

// 订阅消息
cxrServiceBridge.subscribe("rk_custom_client", object : CXRServiceBridge.MsgCallback {
    override fun onReceive(name: String?, args: Caps?, bytes: ByteArray?) {
        // 接收到消息
    }
})

// 发送自定义命令
val cap = Caps().apply {
    write("key")
    writeInt32(keyCode)
}
cxrServiceBridge.sendMessage("rk_custom_key", cap)
```
### 4.3 按键事件监听

眼镜端支持通过广播接收按键事件：

```kotlin
// 定义按键类型
enum class KeyType(val action: String) {
    CLICK("com.android.action.ACTION_SPRITE_BUTTON_CLICK"),
    BUTTON_DOWN("com.android.action.ACTION_SPRITE_BUTTON_DOWN"),
    BUTTON_UP("com.android.action.ACTION_SPRITE_BUTTON_UP"),
    DOUBLE_CLICK("com.android.action.ACTION_SPRITE_BUTTON_DOUBLE_CLICK"),
    AI_START("com.android.action.ACTION_AI_START"),
    LONG_PRESS("com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS")
}

// 创建广播接收器
class KeyReceiver : BroadcastReceiver() {
    var listener: KeyReceiverListener? = null
    
    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.action?.let {
            when (it) {
                KeyType.CLICK.action -> {
                    listener?.onReceive(KeyType.CLICK)
                    abortBroadcast()  // 截断广播，阻止系统处理
                }
                // ... 其他按键类型
            }
        }
    }
}

// 注册广播接收器
registerReceiver(keyReceiver, IntentFilter().apply {
    addAction(KeyType.CLICK.action)
    addAction(KeyType.BUTTON_DOWN.action)
    addAction(KeyType.BUTTON_UP.action)
    addAction(KeyType.DOUBLE_CLICK.action)
    addAction(KeyType.AI_START.action)
    addAction(KeyType.LONG_PRESS.action)
    priority = 100  // 设置高优先级
})
```
### 4.4 音频录制（眼镜端）

```kotlin
companion object {
    private const val SAMPLE_RATE = 16000  // 16kHz
    private const val CHANNEL_CONFIG = 0x6000FC  // 8声道配置
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val BUFFER_SIZE = 1024
}

// 创建 AudioRecord
val recorder = AudioRecord.Builder()
    .setAudioSource(MediaRecorder.AudioSource.MIC)
    .setAudioFormat(
        AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()
    )
    .build()

// 开始录音
recorder.startRecording()

// 读取音频数据
Thread {
    FileOutputStream(file).use { outputStream ->
        val buffer = ByteArray(BUFFER_SIZE)
        while (isRecordingActive) {
            val read = recorder.read(buffer, 0, BUFFER_SIZE)
            if (read > 0) {
                outputStream.write(buffer, 0, read)
            }
        }
    }
}.start()
```
### 4.5 视频录制（眼镜端 + CameraX）

```kotlin
// 初始化相机
val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
val cameraProvider = cameraProviderFuture.get()

val qualitySelector = QualitySelector.fromOrderedList(
    listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
)

val recorder = Recorder.Builder()
    .setQualitySelector(qualitySelector)
    .build()

val videoCapture = VideoCapture.Builder(recorder).build()

cameraProvider.bindToLifecycle(
    lifecycleOwner,
    CameraSelector.DEFAULT_BACK_CAMERA,
    videoCapture
)

// 开始录制
val outputOptions = FileOutputOptions.Builder(file).build()
recording = videoCapture.output
    .prepareRecording(context, outputOptions)
    .start(executor) { event ->
        when (event) {
            is VideoRecordEvent.Start -> { /* 开始录制 */ }
            is VideoRecordEvent.Finalize -> { /* 录制完成 */ }
        }
    }

// 停止录制
recording?.stop()
```
---

## 五、项目架构

### 5.1 手机端功能模块

```
CXRMSamples
├── activities/
│   ├── main/                      # 启动页（权限检查）
│   ├── bluetoothConnection/       # 蓝牙连接
│   ├── usageSelection/            # 功能选择
│   ├── deviceInformation/         # 设备信息
│   ├── audio/                     # 音频录制
│   ├── picture/                   # 拍照
│   ├── video/                     # 视频录制
│   ├── customProtocol/            # 自定义协议
│   ├── customView/                # 自定义视图
│   ├── mediaFile/                 # 媒体文件
│   ├── ttsAndNotification/        # TTS 和通知
│   ├── useAIScene/                # AI 场景
│   ├── useTeleprompter/           # 提词器场景
│   └── useTranslation/            # 翻译场景
└── dataBeans/                     # 数据模型
```
### 5.2 眼镜端功能模块

```
CXRSSDKSamples
├── activities/
│   ├── main/              # 主页
│   ├── selfCMD/           # 自定义命令
│   ├── keys/              # 按键监听
│   ├── audioRecord/       # 音频录制
│   └── videoRecord/       # 视频录制
├── default/               # 常量配置
└── theme/                 # UI 主题
```
---

## 六、构建和运行

### 6.1 手机端 App

```bash
cd "Rokid SDK DEMO/CXRMSamples"
./gradlew assembleDebug
./gradlew installDebug
```
### 6.2 眼镜端 App

```bash
cd "Rokid SDK DEMO/CXRSSDKSamples"
./gradlew assembleDebug
# 需要通过 adb 安装到眼镜设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
---

## 七、常见问题

### Q1: 蓝牙连接失败
- 确保 SN 认证文件正确
- 检查 Client Secret 是否正确
- 确保眼镜已开启且在范围内

### Q2: 音频录制没有声音
- 检查音频权限是否授予
- 眼镜端使用特殊的 8 声道配置 (`0x6000FC`)

### Q3: 相机初始化失败
- 眼镜相机默认旋转 90°，注意宽高参数
- 确保相机权限已授予