# LibSodium Build Instructions for Windows

## Problem
The NDK build fails because libsodium hasn't been compiled yet.

## Solutions

### Option 1: Use Windows Subsystem for Linux (WSL) - RECOMMENDED

1. **Install WSL:**
   - Open PowerShell as Administrator
   - Run: `wsl --install`
   - Restart computer

2. **Build libsodium in WSL:**
   ```bash
   cd /mnt/c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium
   ./autogen.sh
   cd dist-build
   ./android-armv7-a.sh
   ```

3. **Build complete - return to Android Studio and rebuild**

### Option 2: Use Git Bash (if installed)

1. **Open Git Bash**
2. **Navigate to libsodium:**
   ```bash
   cd /c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium
   ```

3. **Run autogen:**
   ```bash
   ./autogen.sh
   ```

4. **Build for Android:**
   ```bash
   cd dist-build
   export ANDROID_NDK_HOME=/c/Users/Test/AppData/Local/Android/Sdk/ndk/21.4.7075529
   ./android-armv7-a.sh
   ```

### Option 3: Download Prebuilt Libsodium

1. **Download prebuilt binaries:**
   - Visit: https://github.com/jedisct1/libsodium/releases
   - Or use this direct link: https://download.libsodium.org/libsodium/releases/

2. **Extract to correct location:**
   - Extract `libsodium-X.X.X-stable.tar.gz`
   - Copy `src/libsodium/.libs/libsodium.a` to:
     `C:\Users\Test\AndroidStudioProjects\batphone\app\src\main\jni\libsodium\libsodium-android-armv7-a\lib\libsodium.a`

### Option 4: Build on Linux/Mac (if available)

If you have access to a Linux machine or Mac:

```bash
cd app/src/main/jni/libsodium
./autogen.sh
cd dist-build
export ANDROID_NDK_HOME=/path/to/your/android-ndk
./android-armv7-a.sh
```

Then copy the entire `libsodium-android-armv7-a` folder back to Windows.

### Option 5: Temporary Workaround - Disable Crypto Features

If you just want to test the build (NOT for production):

Edit `app/src/main/jni/Android.mk` and comment out the libsodium module:

```makefile
# include $(CLEAR_VARS)
# LOCAL_MODULE:= sodium
# LOCAL_SRC_FILES:= $(SODIUM_BASE)/lib/libsodium.a
# include $(PREBUILT_STATIC_LIBRARY)
```

Also edit `app/src/main/jni/serval-dna/Android.mk` and remove sodium dependency.

**WARNING: This will break encryption features!**

## What libsodium does

LibSodium is a cryptography library used by the upstream Serval DNA layer for:
- End-to-end encryption
- Digital signatures
- Key generation
- Secure communication

Without it, SATNET will NOT have proper security!

## Recommended Solution

For Windows developers, **Option 1 (WSL)** is the easiest and most reliable method.

---

**Need Help?**
- Original upstream Batphone repo: https://github.com/servalproject/batphone
- LibSodium: https://github.com/jedisct1/libsodium

