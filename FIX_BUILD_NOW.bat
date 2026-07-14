@echo off
echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║          BATPHONE BUILD - LIBSODIUM REQUIRED                 ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
echo The Android build is failing because libsodium needs to be compiled.
echo.
echo IMMEDIATE SOLUTION (Choose ONE):
echo ════════════════════════════════════════════════════════════════
echo.
echo [1] Install WSL and build automatically (RECOMMENDED)
echo     1. Open PowerShell as Administrator
echo     2. Run: wsl --install
echo     3. Restart computer
echo     4. Run this script again
echo.
echo [2] Use Git Bash (if Git is installed)
echo     1. Open Git Bash
echo     2. Run: cd /c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium
echo     3. Run: ./autogen.sh ^&^& cd dist-build ^&^& ./android-armv7-a.sh
echo.
echo [3] Download prebuilt libsodium
echo     See: BUILD_ERROR_FIX.md for download links
echo.
echo ════════════════════════════════════════════════════════════════
echo.
echo Checking your system...
echo.

REM Try WSL
wsl --version >nul 2>&1
if %errorlevel% equ 0 (
    echo ✓ WSL is installed!
    echo.
    echo Do you want to build libsodium now using WSL? [Y/N]
    set /p choice=
    if /i "%choice%"=="Y" (
        echo.
        echo Building libsodium with WSL...
        wsl bash -c "cd /mnt/c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium && ./autogen.sh && cd dist-build && export ANDROID_NDK_HOME=/mnt/c/Users/Test/AppData/Local/Android/Sdk/ndk/21.4.7075529 && ./android-armv7-a.sh"
        if %errorlevel% equ 0 (
            echo.
            echo ✓✓✓ BUILD SUCCESSFUL! ✓✓✓
            echo.
            echo Now you can:
            echo 1. Open Android Studio
            echo 2. Build -^> Clean Project
            echo 3. Build -^> Rebuild Project
            echo.
        ) else (
            echo.
            echo ✗ Build failed. See error above.
            echo Try running: wsl sudo apt-get install build-essential autoconf automake libtool
        )
        pause
        exit /b
    )
) else (
    echo ✗ WSL not found
)

REM Try Git Bash
if exist "C:\Program Files\Git\bin\bash.exe" (
    echo ✓ Git Bash is installed!
    echo.
    echo Do you want to build libsodium now using Git Bash? [Y/N]
    set /p choice=
    if /i "%choice%"=="Y" (
        echo.
        echo Building libsodium with Git Bash...
        "C:\Program Files\Git\bin\bash.exe" -c "cd /c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium && ./autogen.sh && cd dist-build && export ANDROID_NDK_HOME=/c/Users/Test/AppData/Local/Android/Sdk/ndk/21.4.7075529 && ./android-armv7-a.sh"
        if %errorlevel% equ 0 (
            echo.
            echo ✓✓✓ BUILD SUCCESSFUL! ✓✓✓
            echo.
            echo Now you can:
            echo 1. Open Android Studio
            echo 2. Build -^> Clean Project
            echo 3. Build -^> Rebuild Project
            echo.
        ) else (
            echo ✗ Build failed. See error above.
        )
        pause
        exit /b
    )
) else (
    echo ✗ Git Bash not found
)

echo.
echo ════════════════════════════════════════════════════════════════
echo NEXT STEPS:
echo ════════════════════════════════════════════════════════════════
echo.
echo You need to install either:
echo   - WSL: Run 'wsl --install' in PowerShell as Admin
echo   - Git Bash: Download from https://git-scm.com/download/win
echo.
echo After installing, run this script again.
echo.
echo OR see BUILD_ERROR_FIX.md for manual download instructions.
echo.
pause

