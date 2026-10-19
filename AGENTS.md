# Pura Hotspot — Project Overview for AI Agents

## Tech Stack
- **Language**: Kotlin 1.9.24
- **Target OS**: Android 12–14 (EMUI 14.2 / HarmonyOS on Huawei Pura 70)
- **Min SDK**: 26 (Android 8.0) | **Target SDK**: 34 (Android 14)
- **UI**: AndroidX Material 3 + ViewBinding
- **System Automation**:
  - Primary: Accessibility Service (`PuraAccessibilityService` via Quick Settings shade automation, zero-PC, works locked)
  - Secondary/Fallback: Shizuku API 13.1.5 (ADB Shell UID 2000)
- **Concurrency**: Kotlin Coroutines (`Dispatchers.IO`, `Dispatchers.Main`)

## Key Architecture
- `com.pura.autohotspot.MainActivity`: UI configuration, permission handling, paired BT device selection, manual testing.
- `com.pura.autohotspot.PuraAccessibilityService`: Zero-PC automation engine. Pulls down Quick Settings shade even when screen is locked/sleeping, clicks the Hotspot tile, closes shade, and locks screen.
- `com.pura.autohotspot.HotspotService`: Persistent Foreground Service (`connectedDevice` type) with sticky restart and live notification.
- `com.pura.autohotspot.BluetoothReceiver`: Dynamic receiver for `ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED` with WakeLock and configurable debounce delay.
- `com.pura.autohotspot.HotspotController`: Unified switcher dispatching to `PuraAccessibilityService` first, or Shizuku fallback.
- `com.pura.autohotspot.ShizukuManager`: Shizuku binder lifecycle, permission checks, and shell execution.
- `com.pura.autohotspot.BootReceiver`: Restarts foreground service on `BOOT_COMPLETED`.
- `com.pura.autohotspot.PreferencesManager`: Persistent storage of selected MAC address, delay, and service state.

## Build & Run
- Requires JDK 17+ and Android SDK 34.
- Build debug APK: `./gradlew assembleDebug`
- Output: `app/build/outputs/apk/debug/app-debug.apk`
- GitHub repo: `https://github.com/C0dece/PuraHotspot`
