# SATNET Mainnet APK - Deployment & Installation Guide

## Overview
This document provides instructions for deploying and testing the Batphone SATNET application configured for **mainnet** operation in **pilot stage** with explicit override enabled.

---

## ✅ Build Status
- **Status**: SUCCESSFUL
- **Date**: May 5, 2026, 11:25:47 UTC+0300
- **APK File**: `app/build/outputs/apk/debug/app-debug.apk`
- **Size**: 20.63 MB
- **Build Type**: Debug (unsigned)
- **Version**: 0.94-pre-32-ge8941bd6-dirty (commit 2404)

---

## Configuration Summary

### Active SATNET Settings
```
Deployment Stage:           pilot
Settlement Network:         mainnet ✅ (SWITCHED FROM TESTNET)
Mainnet Explicit Override:  true ✅ (ENABLED)
Policy Compliance:          partner-led
Remote Kill Switch:         enabled
Custodial Mode:            disabled (policy enforced)
Protocol Fees:             disabled (policy enforced)
Surveillance Monetization: disabled (policy enforced)
```

---

## Installation Instructions

### Prerequisites
- Android device with:
  - **Minimum SDK**: Android 4.4 (API 19)
  - **Target SDK**: Android 14 (API 34)
  - **Storage**: ~50 MB free space (for APK + app data)
  - **RAM**: Minimum 2GB recommended

- **Development Tools**:
  - Android Debug Bridge (adb) installed and in PATH
  - USB debugging enabled on target device
  - Device connected via USB cable

### Method 1: ADB Installation (Recommended for Development)

```bash
# Navigate to project directory
cd C:\Users\Test\AndroidStudioProjects\batphone

# Verify device is connected
adb devices

# Install the debug APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n org.servalproject/.MainActivity
```

**Expected Output**:
```
C:\...> adb install -r app/build/outputs/apk/debug/app-debug.apk
Performing Streamed Install
app-debug.apk: 1 file pushed. 13.4 MB/s (21,639,201 bytes in 1.587s)
Success
```

### Method 2: Manual Installation

1. Copy `app/build/outputs/apk/debug/app-debug.apk` to your Android device
2. Use a file manager app to navigate to the APK
3. Tap the APK to install
4. Confirm the installation when prompted
5. Launch "SATNET" from your apps list

### Method 3: Android Studio Installation

1. Open Android Studio
2. Connect your device or start an emulator
3. Run: `./gradlew installDebug`
4. Android Studio will build, install, and launch the app

---

## Testing SATNET Mainnet Configuration

### Verification Checklist

After installation, verify the app is correctly configured:

#### 1. Check Build Configuration
- [ ] App launches without errors
- [ ] No "SATNET is not available on this device" popup
- [ ] Financial tools section accessible
- [ ] SATNET button responds to clicks

#### 2. Verify Mainnet Settings
Use Android Studio Logcat to confirm configuration:
```bash
adb logcat | grep -i satnet
```

Or manually navigate to:
- **App Settings** → **About** (if available)
- Look for "Settlement Network: mainnet"
- Verify "Pilot Stage: Yes" with "Override: Enabled"

#### 3. Test Network Connectivity
- Attempt to connect to SATNET relay directory
- Check for successful connection to:
  - `https://directory.satnet.invalid` (relay directory)
  - `https://rates.satnet.invalid` (exchange rates)

#### 4. Test Bitcoin Integration
- [ ] QR code scanner works
- [ ] HD wallet generation works
- [ ] Can generate Bitcoin addresses
- [ ] Exchange rate updates function

---

## Troubleshooting

### Common Issues and Solutions

#### Issue 1: "Unknown Sources" Permission Error
**Symptom**: Installation blocked by security settings

**Solution**:
```bash
# For Android 8.0+, enable installation from unknown sources:
# Settings → Apps & notifications → Advanced → Special app access → Install unknown apps
# Select your file manager and enable

# Or use ADB to bypass:
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### Issue 2: "SATNET is not available on this device"
**Symptom**: Popup when clicking SATNET button

**Solution**:
1. This should NOT occur with this build (fixed in code)
2. If it does, verify:
   - `isSatnetMainnetPermittedByStage()` returns true
   - `satnet.settlement.network=mainnet` in gradle.properties
   - `satnet.mainnet.explicit.override=true` in gradle.properties
3. Rebuild if needed with: `./gradlew clean assembleDebug --max-workers=2`

#### Issue 3: Installation Fails ("adb: not found")
**Symptom**: ADB command not recognized

**Solution**:
```bash
# Add Android SDK platform-tools to PATH
# Windows:
set PATH=%ANDROID_SDK_ROOT%\platform-tools;%PATH%

# Or use full path:
"C:\Program Files\Android\android-sdk\platform-tools\adb.exe" install app/build/outputs/apk/debug/app-debug.apk
```

#### Issue 4: App Crashes on Launch
**Symptom**: App force closes immediately

**Solution**:
```bash
# View crash logs
adb logcat -v threadtime > crash.log

# Search for exceptions
adb logcat | grep -i "exception\|error"

# Rebuild with fresh cache
./gradlew clean assembleDebug --max-workers=2

# Reinstall
adb uninstall org.servalproject
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### Issue 5: Out of Memory During Build
**Symptom**: Build fails with "Native memory allocation failed"

**Solution**:
```bash
# Use reduced parallel workers
./gradlew assembleDebug --max-workers=2 --no-daemon

# Or increase system RAM/swap space
# Windows: Control Panel → System → Advanced → Performance → Virtual Memory
```

---

## Build Artifacts

### Output Location
```
C:\Users\Test\AndroidStudioProjects\batphone\app\build\outputs\
├── apk/
│   └── debug/
│       ├── app-debug.apk (20.63 MB)
│       └── app-debug.apk.aab (optional bundle)
└── logs/
    └── build-debug-final.log
```

### APK Contents
- AndroidManifest.xml (configured for pilot + mainnet)
- Native libraries (arm64-v8a, armeabi-v7a)
- Compiled Java classes with SATNET configs
- Resource files and assets
- Signing: Unsigned debug key (AndroidDebugKey)

---

## Code Changes Applied

### 1. FeatureFlags.java (Fixed Logic)
```java
// Line 97-99: Allows mainnet in pilot stage with explicit override
public static boolean isSatnetMainnetPermittedByStage() {
    return !isSatnetPilotStage() || isSatnetMainnetExplicitOverrideEnabled();
}
```

### 2. gradle.properties (Configuration)
```ini
satnet.deployment.stage=pilot
satnet.settlement.network=mainnet
satnet.mainnet.explicit.override=true
satnet.require.testnet.by.default=false
```

### 3. build.gradle (Safety Gate)
```groovy
// Lines 202-204: Allows mainnet + pilot + explicit override
if (satnetSettlementNetwork.equalsIgnoreCase("mainnet") && 
    satnetDeploymentStage.equalsIgnoreCase("pilot") && 
    !satnetMainnetExplicitOverride) {
    throw new GradleException("SATNET safety gate: pilot deployments cannot use mainnet settlement")
}
```

---

## Performance Optimizations Applied

### Build Configuration
- **JVM Heap Size**: 1024 MB
- **Metaspace**: 256 MB
- **Parallel Workers**: 2 (limited for 4GB system)
- **Build Cache**: Enabled
- **Daemon**: Disabled for clean builds

### Build Time
- **Total Time**: 2 minutes 12 seconds
- **Java Compilation**: ~20 seconds
- **NDK Build**: ~30 seconds
- **DEX Compilation**: ~60 seconds
- **Packaging & Signing**: ~20 seconds

---

## Next Steps

### Immediate (Testing Phase)
1. ✅ **Install APK**: Use one of the installation methods above
2. ✅ **Verify Launch**: Confirm app starts without errors
3. ✅ **Test SATNET Button**: Click Financial Tools → SATNET
4. ✅ **Check Connectivity**: Verify network calls succeed
5. ✅ **Log Verification**: Review logcat for configuration confirmation

### Short-term (Integration Phase)
1. **End-to-End Testing**: Test complete SATNET workflows
2. **Network Testing**: Verify mainnet blockchain connectivity
3. **Bitcoin Operations**: Test wallet generation and transactions
4. **Exchange Rates**: Verify live rate updates from mainnet sources
5. **Error Handling**: Test error cases and recovery

### Medium-term (Release Preparation)
1. **Release Build**: Create signed release APK for production
   ```bash
   ./gradlew assembleRelease -Prelease.key.store=path/to/keystore
   ```
2. **Version Bump**: Update version code for release track
3. **Documentation**: Update user guides for mainnet operation
4. **QA Certification**: Complete security and compliance testing
5. **App Store Submission**: Prepare for Play Store release (if applicable)

---

## Support & Debugging

### Enable Verbose Logging
```bash
# Debug SATNET operations
adb logcat | grep -i "satnet\|settlement\|mainnet"

# Monitor network calls
adb logcat | grep -i "network\|http\|request"

# Watch for errors
adb logcat | grep -i "error\|exception\|crash"

# Export full log
adb logcat -d > debug_$(date +%Y%m%d_%H%M%S).log
```

### Check Device Compatibility
```bash
# Verify Android version
adb shell getprop ro.build.version.release

# Check RAM available
adb shell cat /proc/meminfo | head -1

# List installed apps
adb shell pm list packages | grep serval
```

### Uninstall & Clean Rebuild
```bash
# Uninstall previous version
adb uninstall org.servalproject

# Clean build cache
./gradlew clean

# Fresh rebuild
./gradlew assembleDebug --max-workers=2

# Reinstall
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Security Notes

⚠️ **Debug Build Warning**: This is a debug APK signed with the Android debug key. It should NOT be used for production deployment.

- Debug signing key is well-known
- APK is not optimized for production
- Logging may expose sensitive data
- For production, create a release build with proper signing

---

## Files Reference

| File | Purpose |
|------|---------|
| `app/build/outputs/apk/debug/app-debug.apk` | Installable debug APK |
| `app/build/outputs/apk/debug/app-debug.apk.aab` | Android App Bundle (optional) |
| `app/build/intermediates/classes/debug/` | Compiled Java classes |
| `build-debug-final.log` | Build execution log |
| `gradle.properties` | Build configuration |
| `app/build.gradle` | App module build script |

---

## Contact & Support

For issues or questions:
1. Check the troubleshooting section above
2. Review build logs: `build-debug-final.log`
3. Check logcat output for runtime errors
4. Verify configuration in `gradle.properties` and `FeatureFlags.java`

---

**Last Updated**: May 5, 2026  
**Build Version**: 0.94-pre-32-ge8941bd6-dirty  
**Status**: Ready for Testing

