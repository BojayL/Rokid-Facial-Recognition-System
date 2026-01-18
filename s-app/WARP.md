# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

s-app is the AR glasses HUD component of FReg (AR 智慧课堂伴侣). It runs on Rokid AR glasses to:
- Capture images via CameraX and send to m-app (phone) for face recognition
- Receive recognition results and display AR overlays
- Handle touchpad input for user interaction

For full system architecture, see `../WARP.md`.

## Common Commands

```bash
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK  
./gradlew installDebug           # Install via ADB
./gradlew test                   # Unit tests
./gradlew connectedAndroidTest   # Instrumentation tests
./gradlew lint                   # Android lint
```

### Glasses ADB

```bash
# USB connection
adb devices
adb shell am start -n com.sustech.bojayL.glasses/.MainActivity

# Wireless ADB (untethered testing)
adb tcpip 5555
adb connect <glasses-ip>:5555
```

### Debugging

```bash
adb logcat | grep -E "(GlassesCamera|HudViewModel|GlassesBridge|KeyReceiver|SwipeDetector)"

# Protocol debugging
adb logcat | grep -E "(Received message|Sent.*result)"
```

## Architecture

### Data Flow

```
GlassesCamera (CameraX)
    ↓ JPEG images (720x1280, rotated 90°)
HudViewModel
    ↓ sendRecognitionRequest()
GlassesBridge (CXRServiceBridge)
    ↓ Caps → Base64 image
────────────────────────────
[Phone m-app processes recognition]
────────────────────────────
    ↓ phone_result message
GlassesBridge.parseRecognitionResult()
    ↓ RecognitionResult
HudViewModel._recognitionResult (StateFlow)
    ↓
HudScreen (Compose) → RotatedLayout (90° CCW)
```

### Key Components

**communication/**
- `GlassesBridge`: Wraps `CXRServiceBridge` - handles connection, pairing, sending images, receiving results
- `MessageProtocol`: Message keys (`glass_recognize`, `phone_result`, etc.) and data classes

**camera/**
- `GlassesCamera`: CameraX image capture with YUV→JPEG conversion and rotation handling

**input/**
- `KeyReceiver`: BroadcastReceiver for touchpad gestures (CLICK, DOUBLE_CLICK, LONG_PRESS, AI_START)
- `SwipeDetector`: Keycode sequence detection (22→20 = forward, 21→19 = backward)

**viewmodel/**
- `HudViewModel`: Central state manager - camera lifecycle, communication, UI state

**ui/**
- `HudScreen`: Main screen with `RotatedLayout` wrapper for physical screen orientation
- `PairingScreen`: Displayed until phone connects
- Components: `FaceFrame`, `IdentityTag`, `StatusBar`, `ToastBar`

### Communication Protocol Notes

Rokid SDK lacks `writeFloat()` - encode floats as:
```kotlin
writeInt32(java.lang.Float.floatToIntBits(floatValue))
// Decode:
java.lang.Float.intBitsToFloat(caps.at(n).int)
```

### Input Handling

| Gesture | Action |
|---------|--------|
| CLICK | Manual mode: trigger capture; Auto mode: toggle capture |
| DOUBLE_CLICK | Reset state |
| LONG_PRESS | Toggle AUTO/MANUAL mode |
| AI_START | Start capture |
| Swipe forward/backward | (Available for future features) |

### Capture Modes

- `AUTO`: Continuous capture at configurable interval (default 2s)
- `MANUAL`: Single capture on touchpad click

## Key Dependencies

- **minSdk**: 31 (Android 12) - Rokid glasses SDK requirement
- **Rokid SDK**: `com.rokid.cxr:cxr-service-bridge:1.0-SNAPSHOT`
- **CameraX**: 1.3.1
- **Compose BOM**: 2024.09.00

Version catalog: `gradle/libs.versions.toml`
