@echo off
REM Batch script to help setup libsodium for Batphone Android build
REM Run this from the batphone root directory

echo ========================================
echo Libsodium Setup for Batphone (Windows)
echo ========================================
echo.

set LIBSODIUM_DIR=app\src\main\jni\libsodium
set TARGET_DIR=%LIBSODIUM_DIR%\libsodium-android-armv7-a\lib
set INCLUDE_DIR=%LIBSODIUM_DIR%\libsodium-android-armv7-a\include

REM Check if already built
if exist "%TARGET_DIR%\libsodium.a" (
    echo [SUCCESS] libsodium.a already exists!
    echo Location: %TARGET_DIR%\libsodium.a
    echo.
    echo You can now build the project in Android Studio.
    pause
    exit /b 0
)

echo [INFO] libsodium.a not found. Need to build it.
echo.

echo OPTIONS TO BUILD LIBSODIUM:
echo ===========================
echo.

echo OPTION 1: Use WSL (Windows Subsystem for Linux) - RECOMMENDED
echo --------------------------------------------------------------
echo 1. Install WSL if not already installed:
echo    Open PowerShell as Admin and run: wsl --install
echo.
echo 2. After WSL is installed, run in WSL:
echo    cd /mnt/c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium
echo    ./autogen.sh
echo    cd dist-build
echo    ./android-armv7-a.sh
echo.

echo OPTION 2: Use Git Bash
echo ----------------------
echo 1. Install Git for Windows if not installed (includes Git Bash)
echo    Download from: https://git-scm.com/download/win
echo.
echo 2. Open Git Bash and run:
echo    cd /c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium
echo    ./autogen.sh
echo    cd dist-build
echo    export ANDROID_NDK_HOME=/c/Users/Test/AppData/Local/Android/Sdk/ndk/21.4.7075529
echo    ./android-armv7-a.sh
echo.

echo OPTION 3: Build on Linux/Mac (then copy to Windows)
echo ----------------------------------------------------
echo If you have access to a Linux/Mac machine:
echo 1. Clone the project there
echo 2. Run the build script
echo 3. Copy the libsodium-android-armv7-a folder back to Windows
echo.

echo OPTION 4: Download Prebuilt (Advanced)
echo ---------------------------------------
echo 1. Download libsodium prebuilt from:
echo    https://github.com/jedisct1/libsodium/releases/latest
echo.
echo 2. Or use libsodium-jni prebuilt:
echo    https://repo1.maven.org/maven2/com/github/joshjdevl/libsodiumjni/
echo.
echo 3. Extract and copy libsodium.a for armeabi-v7a to:
echo    %TARGET_DIR%
echo.

echo.
echo Creating directory structure...
if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"
if not exist "%INCLUDE_DIR%" mkdir "%INCLUDE_DIR%"
echo [SUCCESS] Directories created.
echo.

echo NEXT STEPS:
echo ===========
echo 1. Choose one of the options above to build libsodium
echo 2. After libsodium is built, retry the gradle build in Android Studio
echo 3. See LIBSODIUM_BUILD.md for detailed instructions
echo.

echo For more help, see:
echo - LIBSODIUM_BUILD.md (in project root)
echo - INSTALL.md (local project copy)
echo.

pause

