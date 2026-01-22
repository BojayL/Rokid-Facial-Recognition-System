# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

FReg (AR 智慧课堂伴侣) is a face recognition-based student attendance system for Rokid AR glasses. The repo contains two Android apps:

- **m-app**: Mobile phone companion app - handles face enrollment, student management, and recognition processing
- **s-app**: AR glasses HUD app - displays recognition results on Rokid glasses

The phone captures/receives camera frames, runs face detection (SCRFD) and recognition (MobileFaceNet) via NCNN, then streams results to the glasses for AR overlay display.

## Common Commands

### m-app (Mobile Companion)

```bash
m-app/gradlew -p m-app assembleDebug          # Debug APK
m-app/gradlew -p m-app assembleRelease        # Release APK
m-app/gradlew -p m-app installDebug           # Install to connected device
m-app/gradlew -p m-app test                   # Unit tests
m-app/gradlew -p m-app connectedAndroidTest   # Instrumentation tests
m-app/gradlew -p m-app test --tests "com.sustech.bojayL.ExampleUnitTest"  # Single test
m-app/gradlew -p m-app lint                   # Android lint
```

### s-app (Glasses HUD)

```bash
s-app/gradlew -p s-app assembleDebug
s-app/gradlew -p s-app assembleRelease
s-app/gradlew -p s-app installDebug           # Via USB or wireless ADB
s-app/gradlew -p s-app test                   # Unit tests
s-app/gradlew -p s-app connectedAndroidTest   # Instrumentation tests
s-app/gradlew -p s-app lint
```

### Multi-Device Install

```bash
# Install to specific device by serial
m-app/gradlew -p m-app installDebug -PdeviceSerial=<phone-serial>
s-app/gradlew -p s-app installDebug -PdeviceSerial=<glasses-serial>

# Or via adb directly
adb -s <serial> install -r m-app/app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> install -r s-app/app/build/outputs/apk/debug/app-debug.apk
```

### Glasses ADB Connection

```bash
# USB connection
adb devices                       # Verify glasses connected
adb shell am start -n com.sustech.bojayL.glasses/.MainActivity  # Launch app

# Wireless ADB (for untethered testing)
adb tcpip 5555                    # Enable TCP mode while USB connected
adb connect <glasses-ip>:5555    # Then disconnect USB
adb shell ip addr show wlan0     # Get glasses IP if needed
```

### Debugging

```bash
# m-app logs
adb logcat | grep -E "(RokidService|RokidManager|RecognitionProcessor)"

# s-app logs  
adb logcat | grep -E "(GlassesCamera|HudViewModel|GlassesBridge|KeyReceiver|SwipeDetector)"

# Protocol debugging (see Caps message contents)
adb logcat | grep -E "(Received message|Sent.*result)"
```

See `DEBUG_GUIDE.md` for detailed device-specific debugging with serial numbers and troubleshooting steps.

### Model Setup

```bash
cd m-app
./scripts/download_mobilefacenet.sh  # Download MobileFaceNet model
```

### Test Datasets

```bash
cd m-app/test_datasets
python3 download_test_dataset.py     # Download LFW or create mock data
python3 ../scripts/import_lfw_data.py # Import as students
```

## Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                          m-app (Phone)                          │
│  ┌──────────┐    ┌───────────────┐    ┌──────────────────────┐ │
│  │ CameraX  │───▶│ ML Pipeline   │───▶│ RokidManager         │ │
│  │ or       │    │ (SCRFD+       │    │ (BLE + Rokid SDK)    │ │
│  │ Glasses  │    │  MobileFaceNet)│    │                      │ │
│  │ frames   │    └───────────────┘    └──────────┬───────────┘ │
│  └──────────┘                                    │             │
└──────────────────────────────────────────────────┼─────────────┘
                                                   │ Recognition
                                                   │ Results
                                                   ▼
┌─────────────────────────────────────────────────────────────────┐
│                        s-app (Glasses)                          │
│  ┌──────────────┐    ┌──────────────┐    ┌─────────────────┐   │
│  │ GlassesBridge│───▶│ HudViewModel │───▶│ HudScreen       │   │
│  │ (Socket)     │    │              │    │ (AR Overlay)    │   │
│  └──────────────┘    └──────────────┘    └─────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### ML Pipeline (m-app)

```
Image Input
    ↓
[CompositeFaceDetector]
    ├─ InsightfaceNcnnDetector (Primary: SCRFD via NCNN)
    └─ FaceDetectorMlKit       (Fallback: Google MLKit)
    ↓
[FaceAlignment]
    │  5-point landmark → 112x112 aligned face
    ↓
[FaceRecognizer]
    │  MobileFaceNet NCNN → 512-dim feature vector
    ↓
Cosine Similarity (threshold: 0.7)
    ↓
Student Match → RokidService → Glasses HUD
```

### Native Layer (JNI)

Location: `m-app/app/src/main/jni/`

- **scrfd.cpp/h** + **scrfd_jni.cpp**: SCRFD face detection
- **mobilefacenet.cpp/h** + **face_recognition_jni.cpp**: Feature extraction
- Uses NCNN (Vulkan) + OpenCV Mobile
- NDK ABIs: `arm64-v8a`, `armeabi-v7a`

### Phone-Glasses Communication

m-app uses Rokid CxrApi SDK:
- `RokidManager`: BLE scanning, connection, device management
- `RokidService`: Foreground service maintaining connection
- `RokidMessageHandler`: Protocol for sending recognition results
- `RecognitionProcessor`: Processes frames and dispatches results

s-app receives via:
- `GlassesBridge`: Wraps CXRServiceBridge for phone communication
- `MessageProtocol`: Message keys and Caps field definitions
- `HudViewModel`: Manages camera, communication, and UI state

### Communication Protocol

**Message Keys:**
- `glass_recognize`: Glasses → Phone (image + optional face rect)
- `glass_status`: Glasses → Phone (battery, mode, connection)
- `glass_pairing_code`: Glasses → Phone (6-digit pairing code + device name)
- `phone_result`: Phone → Glasses (recognition result)
- `phone_config`: Phone → Glasses (threshold, interval, brightness)
- `phone_verify_code`: Phone → Glasses (verify pairing code)
- `pairing_confirmed`: Glasses → Phone (pairing success/fail)

**Subscribe Key:** `rk_custom_client` (glasses subscribe to phone messages)

**Pairing Flow:**
1. Glasses generate 6-digit code on launch, display on PairingScreen
2. Phone connects via BLE, sees "input pairing code" prompt
3. User enters code on phone, phone sends `phone_verify_code`
4. Glasses verify code, respond with `pairing_confirmed`
5. On success, both sides transition to recognition mode

**Caps Serialization Note:** Rokid SDK lacks `writeFloat()` - always use:
```kotlin
writeInt32(java.lang.Float.floatToIntBits(floatValue))
```

### s-app Architecture

**Data Flow:**
```
GlassesCamera (CameraX)
    ↓ JPEG images
HudViewModel
    ↓ sendRecognitionRequest()
GlassesBridge (CXRServiceBridge)
    ↓ Caps serialization
────────────────────────────
[Phone processes recognition]
────────────────────────────
    ↓ phone_result message
GlassesBridge.parseRecognitionResult()
    ↓ RecognitionResult
HudViewModel._recognitionResult
    ↓ StateFlow
HudScreen (Compose UI)
```

**Input Handling:**
- `KeyReceiver`: BroadcastReceiver for touchpad gestures
- Gestures: CLICK, DOUBLE_CLICK, LONG_PRESS, AI_START
- `SwipeDetector`: Detects forward/backward swipes from keycode sequences (22→20 = forward, 21→19 = backward)

**Capture Modes:**
- `AUTO`: Continuous capture at configurable interval (default 2s)
- `MANUAL`: Single capture triggered by touchpad click

### Data Layer (m-app)

- **StudentRepository**: DataStore + kotlinx.serialization
- **Student**: Model with 512-dim `faceFeature: List<Float>`
- **ClassSession**: Attendance session tracking

### Navigation (m-app)

```
MainActivity → ARClassroomApp → NavigationSuiteScaffold
    ├─ CLASSROOM → ClassroomScreen → PhoneCameraRecognitionScreen
    ├─ STUDENTS → StudentsScreen → StudentDetailScreen → FaceEnrollmentScreen  
    ├─ DEVICE → DeviceScreen (Rokid connection)
    └─ PROFILE → ProfileScreen
```

## Model Files

Required in `m-app/app/src/main/assets/`:

```
scrfd_2.5g_kps-opt2.param/bin   # SCRFD (included)
mobilefacenet-opt.param/bin     # MobileFaceNet (run download script)
```

See `m-app/docs/GET_MOBILEFACENET_MODEL.md` for manual alternatives.

## Rokid SDK Setup

Both apps require SN authentication to communicate:

1. Get SN file from [ar.rokid.com](https://ar.rokid.com) → Account → Device Management
2. Place `sn_xxx.bin` in `m-app/app/src/main/res/raw/`
3. Update `CLIENT_SECRET` in `m-app/.../rokid/RokidManager.kt`

**SDK API Note**: The glasses SDK lacks `writeFloat()` - use `writeInt32(Float.floatToIntBits(value))` instead.

## Key Dependencies

- **Kotlin**: 2.0.21, **AGP**: 8.13.2
- **Compose BOM**: 2024.09.00
- **CameraX**: 1.3.1
- **MLKit Face Detection**: 16.1.6
- **NCNN**: 20250503-android-vulkan
- **OpenCV Mobile**: 4.12.0
- **Rokid SDK**: m-app uses `client-m`, s-app uses `cxr-service-bridge:1.0-SNAPSHOT`

**SDK Requirements:**
- m-app: minSdk 26 (Android 8.0)
- s-app: minSdk 31 (Android 12) - Rokid glasses SDK requirement

Version catalogs: `m-app/gradle/libs.versions.toml`, `s-app/gradle/libs.versions.toml`
