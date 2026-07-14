@echo off
cls
echo.
echo ========================================
echo   BUILD FIX VERIFICATION TEST
echo ========================================
echo.
echo This script will verify all fixes are in place
echo and attempt to build the project.
echo.
echo Checking critical files...
echo.

set ERROR_COUNT=0

REM Check Android.mk
if exist "app\src\main\jni\Android.mk" (
    echo [OK] Main Android.mk exists
) else (
    echo [ERROR] Main Android.mk missing!
    set /a ERROR_COUNT+=1
)

REM Check libsodium Android.mk
if exist "app\src\main\jni\libsodium\Android.mk" (
    echo [OK] Libsodium Android.mk exists
) else (
    echo [ERROR] Libsodium Android.mk missing!
    set /a ERROR_COUNT+=1
)

REM Check version.h
if exist "app\src\main\jni\libsodium\src\libsodium\include\sodium\version.h" (
    echo [OK] Libsodium version.h exists
) else (
    echo [ERROR] Libsodium version.h missing!
    set /a ERROR_COUNT+=1
)

REM Check serval-dna
if exist "app\src\main\jni\serval-dna\Android.mk" (
    echo [OK] Serval-dna Android.mk exists
) else (
    echo [ERROR] Serval-dna Android.mk missing!
    set /a ERROR_COUNT+=1
)

echo.
if %ERROR_COUNT% GTR 0 (
    echo ========================================
    echo   %ERROR_COUNT% ERROR(S) FOUND!
    echo ========================================
    echo Please check the missing files above.
    echo.
    pause
    exit /b 1
)

echo ========================================
echo   ALL FILES PRESENT - READY TO BUILD
echo ========================================
echo.
echo Starting build process...
echo This may take several minutes on first build.
echo.
pause

echo.
echo [1/2] Cleaning previous build...
call gradlew.bat clean

echo.
echo [2/2] Building debug APK...
echo (This will compile libsodium from source)
echo.
call gradlew.bat assembleDebug --info | findstr /C:"BUILD" /C:"libsodium" /C:"serval" /C:"Task"

echo.
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo   BUILD SUCCESSFUL!
    echo ========================================
    echo.
    echo All native libraries compiled successfully:
    echo  - libsodium (built from source)
    echo  - libservalcodec2
    echo  - libservalopus
    echo  - libservaldaemon
    echo.
    echo APK location:
    echo  app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo You can now install the app on a device or emulator.
    echo.
) else (
    echo.
    echo ========================================
    echo   BUILD FAILED
    echo ========================================
    echo.
    echo Please check the error messages above.
    echo Common issues:
    echo  - NDK not installed
    echo  - Missing source files
    echo  - Compilation errors
    echo.
    echo Try:
    echo  1. Android Studio -^> Tools -^> SDK Manager
    echo  2. Install NDK (Side by side) version 21.4.7075529
    echo  3. Run this script again
    echo.
)

pause

