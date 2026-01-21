# Rokid SDK 自定义消息接收问题修复

## 问题描述

眼镜端 (s-app) 无法正确接收来自手机端 (m-app) 发送的自定义消息,导致识别结果无法显示在 AR HUD 上。

## 问题原因

眼镜端无法接收手机消息的根本原因是:**订阅时机不正确**。

`subscribe()` 方法必须在**连接建立之后**调用才能生效,而不能在初始化时就调用。如果在连接建立前调用 `subscribe()`,监听器无法正确注册。

**错误的实现方式**:
```kotlin
// ❌ 在 init() 中直接订阅,此时可能尚未连接
fun init() {
    cxrServiceBridge.setStatusListener(statusListener)
    subscribePhoneMessages()  // 太早了!
}
```

## 解决方案

在连接成功回调 `onConnected()` 中调用 `subscribe()` 方法:

**正确的实现方式**:
```kotlin
// ✅ 在连接成功后订阅消息
private val statusListener = object : CXRServiceBridge.StatusListener {
    override fun onConnected(uuid: String?, type: Int) {
        _isConnected.value = true
        _isPaired.value = true
        
        // 关键:在连接成功后才订阅,确保监听器能正确注册
        subscribePhoneMessages()
    }
    // ...
}

private val msgCallback = object : CXRServiceBridge.MsgCallback {
    override fun onReceive(name: String?, args: Caps?, bytes: ByteArray?) {
        when (name) {
            MessageProtocol.KEY_PHONE_RESULT -> {
                args?.let { parseRecognitionResult(it) }
            }
            // ...
        }
    }
}

private fun subscribePhoneMessages() {
    val result = cxrServiceBridge.subscribe(
        MessageProtocol.KEY_SUBSCRIBE_PHONE, 
        msgCallback
    )
    Log.d(TAG, "Subscribe result: $result")
}
```

## 修改文件

- `s-app/app/src/main/java/com/sustech/bojayL/glasses/communication/GlassesBridge.kt`
  - 在 `statusListener.onConnected()` 回调中调用 `subscribePhoneMessages()`
  - 确保 `subscribePhoneMessages()` 只在连接成功后调用
  - 保留原有的 `msgCallback` 和 `subscribePhoneMessages()` 方法

## 验证方法

1. 重新编译安装 s-app:
   ```bash
   ./gradlew installDebug
   ```

2. 连接眼镜并查看日志:
   ```bash
   adb logcat | grep -E "(GlassesBridge|Received custom command)"
   ```

3. 在手机端触发识别后,应能看到类似日志:
   ```
   GlassesBridge: ===== Received custom command from phone =====
   GlassesBridge: cmdKey='phone_result', caps size=6
   GlassesBridge: Matched KEY_PHONE_RESULT, parsing recognition result...
   ```

## SDK 版本信息

- **SDK**: `com.rokid.cxr:cxr-service-bridge:1.0-SNAPSHOT`
- **问题影响**: 当前版本的 SDK 文档与实际 API 不一致
- **修复日期**: 2026-01-21

## 相关文件

- `GlassesBridge.kt`: 眼镜端通信桥接器
- `MessageProtocol.kt`: 消息协议定义
- `HudViewModel.kt`: HUD 视图模型,消费识别结果

## 注意事项

1. **订阅时机很关键**: 必须在 `onConnected()` 回调中调用 `subscribe()`,否则监听器无法生效
2. **可能需要重复订阅**: 每次重新连接后都需要重新调用 `subscribe()`
3. **Float 编码**: Rokid SDK 缺少 `writeFloat()`,需要使用 `writeInt32(Float.floatToIntBits(value))`
4. **订阅键**: 眼镜端使用 `"rk_custom_client"` 作为订阅键来接收手机端的自定义消息
