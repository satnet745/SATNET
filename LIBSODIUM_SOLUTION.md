# Libsodium Build Error - SOLUTION

## The Problem

```
Android NDK: ERROR: LOCAL_SRC_FILES points to a missing file
Android NDK: Check that C:/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium/libsodium-android-armv7-a/lib/libsodium.a exists
```

**Root Cause:** LibSodium library hasn't been compiled for Android yet.

---

## QUICK SOLUTION (Choose One)

### ✅ Solution 1: Use WSL (Recommended for Windows 10/11)

**Step 1: Install WSL (if not already installed)**
```powershell
# Open PowerShell as Administrator
wsl --install
# Restart computer if prompted
```

**Step 2: Open WSL Terminal and run:**
```bash
# Navigate to the project
cd /mnt/c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium

# Install build tools (if needed)
sudo apt-get update
sudo apt-get install -y build-essential autoconf automake libtool

# Generate build scripts
./autogen.sh

# Build for Android ARM
cd dist-build
export ANDROID_NDK_HOME=/mnt/c/Users/Test/AppData/Local/Android/Sdk/ndk/21.4.7075529
./android-armv7-a.sh
```

**Step 3: Return to Android Studio and rebuild**

---

### ✅ Solution 2: Use Git Bash (If Git is installed)

**Step 1: Open Git Bash**

**Step 2: Run these commands:**
```bash
cd /c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium

# Install autotools if needed (via pacman in Git Bash)
# pacman -S autoconf automake libtool

# Generate configure script
./autogen.sh

# Build for Android
cd dist-build
export ANDROID_NDK_HOME=/c/Users/Test/AppData/Local/Android/Sdk/ndk/21.4.7075529
./android-armv7-a.sh
```

**Step 3: Rebuild in Android Studio**

---

### ✅ Solution 3: Download Prebuilt Library

I'll help you download and setup prebuilt libsodium:

**Step 1: Download prebuilt libsodium**
- Visit: https://github.com/jedisct1/libsodium/releases/latest
- Download: `libsodium-X.X.X-android.tar.gz`

**Step 2: Extract and copy**
```
Extract archive → Find armeabi-v7a folder
Copy libsodium.a to:
C:\Users\Test\AndroidStudioProjects\batphone\app\src\main\jni\libsodium\libsodium-android-armv7-a\lib\
```

**Step 3: Copy headers**
```
Copy include folder to:
C:\Users\Test\AndroidStudioProjects\batphone\app\src\main\jni\libsodium\libsodium-android-armv7-a\include\
```

---

### ✅ Solution 4: Automated PowerShell Script

Save this as `build-libsodium.ps1` and run in PowerShell:

```powershell
# Automated libsodium build using WSL
$ErrorActionPreference = "Stop"

Write-Host "Building libsodium for Android..." -ForegroundColor Green

# Check if WSL is available
if (!(Get-Command wsl -ErrorAction SilentlyContinue)) {
    Write-Host "ERROR: WSL not found. Please install WSL first." -ForegroundColor Red
    Write-Host "Run: wsl --install" -ForegroundColor Yellow
    exit 1
}

# Build using WSL
$buildScript = @"
cd /mnt/c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium
./autogen.sh
cd dist-build
export ANDROID_NDK_HOME=/mnt/c/Users/Test/AppData/Local/Android/Sdk/ndk/21.4.7075529
./android-armv7-a.sh
"@

wsl bash -c $buildScript

Write-Host "Build complete! You can now rebuild in Android Studio." -ForegroundColor Green
```

---

## Verification

After building, verify the file exists:

**Windows Command Prompt:**
```cmd
dir app\src\main\jni\libsodium\libsodium-android-armv7-a\lib\libsodium.a
```

**PowerShell:**
```powershell
Test-Path app\src\main\jni\libsodium\libsodium-android-armv7-a\lib\libsodium.a
```

---

## Troubleshooting

### Error: "autogen.sh: command not found"
**Solution:** Install autotools
```bash
# WSL/Ubuntu
sudo apt-get install autoconf automake libtool

# Git Bash (may not have package manager)
# Use WSL instead
```

### Error: "ANDROID_NDK_HOME not set"
**Solution:** Set the NDK path
```bash
export ANDROID_NDK_HOME=/mnt/c/Users/Test/AppData/Local/Android/Sdk/ndk/21.4.7075529
```

### Error: "configure: error: no acceptable C compiler found"
**Solution:** Install build tools
```bash
sudo apt-get install build-essential
```

### Still failing?
**Option:** Build on another machine and copy the files
- The libsodium-android-armv7-a folder is portable
- Build on Linux/Mac and copy back to Windows

---

## Why LibSodium is Required

LibSodium provides cryptographic functions for:
- ✅ End-to-end encryption
- ✅ Digital signatures
- ✅ Secure key generation
- ✅ Authentication

**Without it, SATNET will NOT have secure communication!**

---

## Alternative: Use Docker (Advanced)

If you have Docker Desktop:

```dockerfile
# Dockerfile
FROM ubuntu:20.04
RUN apt-get update && apt-get install -y build-essential autoconf automake libtool wget unzip
RUN wget https://dl.google.com/android/repository/android-ndk-r21e-linux-x86_64.zip
RUN unzip android-ndk-r21e-linux-x86_64.zip
ENV ANDROID_NDK_HOME=/android-ndk-r21e
WORKDIR /build
CMD ["./build.sh"]
```

Then mount your project and build.

---

## After Building

Once libsodium is built:

1. **Clean the project:**
   ```
   Build → Clean Project
   ```

2. **Rebuild:**
   ```
   Build → Rebuild Project
   ```

3. **If still failing:** Invalidate caches
   ```
   File → Invalidate Caches / Restart
   ```

---

## Need More Help?

- **Original upstream Batphone repo:** https://github.com/servalproject/batphone
- **LibSodium Docs:** https://doc.libsodium.org/
- **This project's INSTALL.md:** See root directory

---

**Quick Start: Run the batch script**
```cmd
setup-libsodium.bat
```

This will create directories and show all options.

