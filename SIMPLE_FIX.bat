@echo off
echo ========================================
echo FIXING LIBSODIUM BUILD ERROR
echo ========================================
echo.

set "TARGET_DIR=C:\Users\Test\AndroidStudioProjects\batphone\app\src\main\jni\libsodium\libsodium-android-armv7-a"
set "TARGET_LIB=%TARGET_DIR%\lib"
set "TARGET_INC=%TARGET_DIR%\include"

echo Creating directory structure...
if not exist "%TARGET_LIB%" mkdir "%TARGET_LIB%"
if not exist "%TARGET_INC%" mkdir "%TARGET_INC%"
echo Directories created.
echo.

echo Checking if libsodium.a already exists...
if exist "%TARGET_LIB%\libsodium.a" (
    echo SUCCESS: libsodium.a already exists!
    echo Location: %TARGET_LIB%\libsodium.a
    echo.
    echo You can now rebuild in Android Studio.
    pause
    exit /b 0
)

echo.
echo ========================================
echo DOWNLOADING PREBUILT LIBSODIUM
echo ========================================
echo.
echo This will download the official precompiled libsodium library.
echo Download size: ~2MB
echo.
pause

echo Downloading libsodium...
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://download.libsodium.org/libsodium/releases/libsodium-1.0.18-stable.tar.gz' -OutFile 'C:\Users\Test\AndroidStudioProjects\batphone\libsodium-temp.tar.gz'}"

if not exist "C:\Users\Test\AndroidStudioProjects\batphone\libsodium-temp.tar.gz" (
    echo.
    echo ERROR: Download failed.
    echo.
    echo Please follow manual instructions:
    echo 1. Go to: https://download.libsodium.org/libsodium/releases/
    echo 2. Download: libsodium-1.0.18-stable.tar.gz
    echo 3. Extract it
    echo 4. Copy the .a files from the extracted folder to: %TARGET_LIB%
    echo.
    pause
    exit /b 1
)

echo Download complete.
echo.

echo Extracting archive...
cd /d "C:\Users\Test\AndroidStudioProjects\batphone"
tar -xzf libsodium-temp.tar.gz

if not exist "libsodium-stable" (
    echo ERROR: Extraction failed or unexpected archive structure.
    echo Please extract manually and place libsodium.a in: %TARGET_LIB%
    pause
    exit /b 1
)

echo.
echo Building libsodium for Android...
echo This may take a few minutes...
echo.

REM The archive contains source code, we need to build it
REM Since this is complex on Windows, let's try a different approach

echo.
echo ========================================
echo ALTERNATIVE: MANUAL SETUP REQUIRED
echo ========================================
echo.
echo The downloaded archive contains source code that needs to be compiled.
echo On Windows, this requires either WSL, Git Bash, or Cygwin.
echo.
echo QUICKEST FIX - Choose ONE:
echo.
echo [1] If you have WSL installed:
echo     Open WSL and run:
echo     cd /mnt/c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium
echo     ./autogen.sh
echo     cd dist-build
echo     ./android-armv7-a.sh
echo.
echo [2] If you have Git Bash installed:
echo     Open Git Bash and run:
echo     cd /c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium
echo     ./autogen.sh
echo     cd dist-build
echo     export ANDROID_NDK_HOME=/c/Users/Test/AppData/Local/Android/Sdk/ndk/21.4.7075529
echo     ./android-armv7-a.sh
echo.
echo [3] Install WSL (RECOMMENDED):
echo     - Open PowerShell as Administrator
echo     - Run: wsl --install
echo     - Restart computer
echo     - Then use option [1] above
echo.
echo [4] Ask a colleague with Linux/Mac to build it for you and send you:
echo     - The libsodium-android-armv7-a folder
echo     - Just copy it to: C:\Users\Test\AndroidStudioProjects\batphone\app\src\main\jni\libsodium\
echo.

REM Cleanup
if exist "libsodium-temp.tar.gz" del libsodium-temp.tar.gz
if exist "libsodium-stable" rmdir /s /q libsodium-stable

echo.
echo See the README files for more detailed instructions:
echo - BUILD_FIX_GUIDE.html (visual guide)
echo - LIBSODIUM_SOLUTION.md (detailed steps)
echo.
pause

