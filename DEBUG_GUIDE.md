# 眼镜端与手机端通信调试指南

## 设备信息

| 设备 | 序列号 | 应用 |
|------|--------|------|
| 眼镜端 | `1901092544001429` | s-app |
| 手机端 | `adb-461QYFDS229B8-Bev7Si._adb-tls-connect._tcp` | m-app |

## 一、准备工作

### 1.1 编译并安装应用

```bash
# 编译并安装眼镜端应用
cd /Users/bojay.l/Developer/Rokid/FReg/s-app
./gradlew installDebug -PdeviceSerial=1901092544001429

# 编译并安装手机端应用
cd /Users/bojay.l/Developer/Rokid/FReg/m-app
./gradlew installDebug -PdeviceSerial=adb-461QYFDS229B8-Bev7Si._adb-tls-connect._tcp
```

或者使用 adb 直接安装已编译的 APK：

```bash
# 安装到眼镜
adb -s 1901092544001429 install -r s-app/app/build/outputs/apk/debug/app-debug.apk

# 安装到手机
adb -s adb-461QYFDS229B8-Bev7Si._adb-tls-connect._tcp install -r m-app/app/build/outputs/apk/debug/app-debug.apk
```

### 1.2 清除旧日志

```bash
# 清除眼镜端日志
adb -s 1901092544001429 logcat -c

# 清除手机端日志
adb -s adb-461QYFDS229B8-Bev7Si._adb-tls-connect._tcp logcat -c
```

## 二、查看实时日志

### 2.1 打开两个终端窗口

**终端 1 - 眼镜端日志：**

```bash
adb -s 1901092544001429 logcat | grep -E "GlassesBridge|HudViewModel"
```

**终端 2 - 手机端日志：**

```bash
adb -s adb-461QYFDS229B8-Bev7Si._adb-tls-connect._tcp logcat | grep -E "RokidService|RokidMessageHandler|MainActivity"
```

### 2.2 更精确的过滤（推荐）

**眼镜端：**

```bash
adb -s 1901092544001429 logcat -s GlassesBridge:D HudViewModel:D
```

**手机端：**

```bash
adb -s adb-461QYFDS229B8-Bev7Si._adb-tls-connect._tcp logcat -s RokidService:D RokidMessageHandler:D MainActivity:D
```

### 2.3 保存日志到文件

```bash
# 眼镜端日志 → glasses.log
adb -s 1901092544001429 logcat -s GlassesBridge:D HudViewModel:D > ~/Desktop/glasses.log

# 手机端日志 → phone.log  
adb -s adb-461QYFDS229B8-Bev7Si._adb-tls-connect._tcp logcat -s RokidService:D RokidMessageHandler:D > ~/Desktop/phone.log
```

## 三、测试流程

### 步骤 1：启动应用

1. 在眼镜端启动 s-app
2. 在手机端启动 m-app

### 步骤 2：建立连接

1. 手机端进入「设备」页面
2. 点击扫描，找到眼镜设备
3. 点击连接

### 步骤 3：检查连接日志

**眼镜端应该看到：**

```
D GlassesBridge: Connected: uuid=xxx, type=xxx
D GlassesBridge: Auto-paired on connection, isConnected=true, isPaired=true
D GlassesBridge: Subscribe to phone messages: key=rk_custom_client, result=0
D GlassesBridge: Successfully subscribed to phone messages
```

**手机端应该看到：**

```
D RokidService: Connected, starting message listener
D RokidService: Auto-paired on connection
D RokidMessageHandler: ===== Starting message listener =====
D RokidMessageHandler: setCustomCmdListener called successfully
```

### 步骤 4：触发识别

1. 眼镜端对准人脸，触发图像采集
2. 眼镜端发送图像到手机端

**眼镜端日志：**

```
D GlassesBridge: sendRecognitionRequest called, imageData size=xxx
D GlassesBridge: sendMessage result: 0 (key=glass_recognize)
```

**手机端日志（收到请求）：**

```
D RokidMessageHandler: ===== Received custom cmd: cmdKey='glass_recognize' =====
D RokidMessageHandler: Parsed recognition request, image size: xxxXxxx
D RokidService: Received recognition request from glasses
```

### 步骤 5：检查结果发送（关键！）

**手机端日志（发送结果）：**

```
D RokidService: ===== sendRecognitionResult called =====
D RokidService: Connection status: CONNECTED
D RokidService: isKnown=xxx, student=xxx, confidence=xxx
D RokidService: Connection OK, sending to glasses via messageHandler...
D RokidMessageHandler: ===== sendRecognitionResult called =====
D RokidMessageHandler: Caps created with 6 fields, sending to glasses...
D RokidMessageHandler: sendCustomCmd called successfully for KEY_PHONE_RESULT
```

**眼镜端日志（接收结果）：**

```
D GlassesBridge: ===== Received message from phone =====
D GlassesBridge: name='phone_result', args size=6, bytes size=0
D GlassesBridge: Matched KEY_PHONE_RESULT, parsing recognition result...
D GlassesBridge: Parsed result: isKnown=xxx, name=xxx, confidence=xxx
```

## 四、常见问题排查

### 问题 1：眼镜端收不到消息

**检查点：**

1. 查看眼镜端订阅结果：
   ```
   Subscribe to phone messages: key=rk_custom_client, result=0
   ```
   - `result=0` 表示成功
   - `result!=0` 表示失败，需要在连接后重试

2. 查看是否有 "Unknown message name" 日志：
   ```
   W GlassesBridge: Unknown message name: 'xxx', not handled
   ```
   - 说明收到了消息但 key 不匹配

### 问题 2：手机端没有发送

**检查点：**

1. 连接状态是否正确：
   ```
   Connection status: CONNECTED
   ```
   - 如果是 `DISCONNECTED`，说明连接已断开

2. 是否有 "Cannot send result" 日志：
   ```
   W RokidService: Cannot send result: not connected
   ```

### 问题 3：识别结果为空

**检查点：**

1. 手机端是否有学生数据？
2. RecognitionProcessor 是否正确初始化？
3. 查看 `isKnown` 和 `confidence` 值

## 五、快捷命令

```bash
# 定义别名（可添加到 ~/.config/fish/config.fish）
alias glasses-log="adb -s 1901092544001429 logcat -s GlassesBridge:D HudViewModel:D"
alias phone-log="adb -s adb-461QYFDS229B8-Bev7Si._adb-tls-connect._tcp logcat -s RokidService:D RokidMessageHandler:D"

# 使用
glasses-log  # 查看眼镜日志
phone-log    # 查看手机日志
```

## 六、一键测试脚本

创建 `test_comm.sh`：

```bash
#!/bin/bash

GLASSES="1901092544001429"
PHONE="adb-461QYFDS229B8-Bev7Si._adb-tls-connect._tcp"

echo "=== 清除日志 ==="
adb -s $GLASSES logcat -c
adb -s $PHONE logcat -c

echo "=== 开始监听 ==="
echo "眼镜端日志保存到: ~/Desktop/glasses.log"
echo "手机端日志保存到: ~/Desktop/phone.log"

adb -s $GLASSES logcat -s GlassesBridge:D HudViewModel:D > ~/Desktop/glasses.log &
GLASSES_PID=$!

adb -s $PHONE logcat -s RokidService:D RokidMessageHandler:D > ~/Desktop/phone.log &
PHONE_PID=$!

echo "按 Enter 停止监听..."
read

kill $GLASSES_PID $PHONE_PID 2>/dev/null
echo "日志已保存"
```

运行：`bash test_comm.sh`
