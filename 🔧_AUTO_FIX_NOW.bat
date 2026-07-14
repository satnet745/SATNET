@echo off
setlocal enabledelayedexpansion

echo.
echo ╔═══════════════════════════════════════════════════════════════╗
echo ║         AUTOMATIC LIBSODIUM BUILD SCRIPT                      ║
echo ║         This will attempt to build libsodium for you          ║
echo ╚═══════════════════════════════════════════════════════════════╝
echo.

set "TARGET_FILE=C:\Users\Test\AndroidStudioProjects\batphone\app\src\main\jni\libsodium\libsodium-android-armv7-a\lib\libsodium.a"

REM Check if file already exists
if exist "%TARGET_FILE%" (
    echo ✅ SUCCESS: libsodium.a already exists!
    echo.
    echo You can now rebuild in Android Studio:
    echo 1. Build -^> Clean Project
    echo 2. Build -^> Rebuild Project
    echo.
    pause
    exit /b 0
)

echo [INFO] libsodium.a not found. Attempting to build it...
echo.

REM Try WSL
echo [1/3] Checking for WSL...
wsl --status >nul 2>&1
if %errorlevel% equ 0 (
    echo ✓ WSL detected!
    echo.
    echo [2/3] Attempting to build with WSL...
    echo This will take 1-2 minutes. Please wait...
    echo.

    wsl bash -c "sudo apt-get update -qq && sudo apt-get install -y -qq build-essential autoconf automake libtool 2>&1 | grep -v 'Reading\|Building\|Unpacking' && cd /mnt/c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium && ./autogen.sh && cd dist-build && export ANDROID_NDK_HOME=/mnt/c/Users/Test/AppData/Local/Android/Sdk/ndk/21.4.7075529 && ./android-armv7-a.sh"

    REM Check if successful
    if exist "%TARGET_FILE%" (
        echo.
        echo ╔═══════════════════════════════════════════════════════════════╗
        echo ║                    ✅ SUCCESS!                                 ║
        echo ╚═══════════════════════════════════════════════════════════════╝
        echo.
        echo libsodium has been compiled successfully!
        echo File location: %TARGET_FILE%
        echo.
        echo NEXT STEPS:
        echo 1. Go to Android Studio
        echo 2. Click Build -^> Clean Project
        echo 3. Click Build -^> Rebuild Project
        echo 4. The build should now succeed! ✅
        echo.
        pause
        exit /b 0
    ) else (
        echo.
        echo ⚠️ WSL build attempt did not produce the file.
        echo Trying next method...
        echo.
    )
) else (
    echo ✗ WSL not found
)

REM Try Git Bash
echo [2/3] Checking for Git Bash...
if exist "C:\Program Files\Git\bin\bash.exe" (
    echo ✓ Git Bash detected!
    echo.
    echo [3/3] Attempting to build with Git Bash...
    echo This will take 1-2 minutes. Please wait...
    echo.

    "C:\Program Files\Git\bin\bash.exe" -lc "cd /c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium && ./autogen.sh && cd dist-build && export ANDROID_NDK_HOME=/c/Users/Test/AppData/Local/Android/Sdk/ndk/21.4.7075529 && ./android-armv7-a.sh"

    REM Check if successful
    if exist "%TARGET_FILE%" (
        echo.
        echo ╔═══════════════════════════════════════════════════════════════╗
        echo ║                    ✅ SUCCESS!                                 ║
        echo ╚═══════════════════════════════════════════════════════════════╝
        echo.
        echo libsodium has been compiled successfully!
        echo File location: %TARGET_FILE%
        echo.
        echo NEXT STEPS:
        echo 1. Go to Android Studio
        echo 2. Click Build -^> Clean Project
        echo 3. Click Build -^> Rebuild Project
        echo 4. The build should now succeed! ✅
        echo.
        pause
        exit /b 0
    ) else (
        echo.
        echo ⚠️ Git Bash build attempt did not produce the file.
        echo.
    )
) else (
    echo ✗ Git Bash not found
)

REM If we got here, nothing worked
echo.
echo ╔═══════════════════════════════════════════════════════════════╗
echo ║            ❌ AUTOMATIC BUILD FAILED                           ║
echo ╚═══════════════════════════════════════════════════════════════╝
echo.
echo Neither WSL nor Git Bash could build libsodium automatically.
echo.
echo MANUAL STEPS REQUIRED:
echo.
echo OPTION 1 - Install WSL (RECOMMENDED):
echo   1. Open PowerShell AS ADMINISTRATOR
echo   2. Run: wsl --install
echo   3. Restart your computer
echo   4. Run this script again
echo.
echo OPTION 2 - Install Git Bash:
echo   1. Download from: https://git-scm.com/download/win
echo   2. Install it
echo   3. Run this script again
echo.
echo OPTION 3 - Manual Instructions:
echo   See file: ⚠️_MUST_READ_TO_FIX_BUILD.txt
echo   (Should be open in your editor)
echo.
pause

