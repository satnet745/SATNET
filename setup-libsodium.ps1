# PowerShell script to download and setup prebuilt libsodium for Android
# Run this from the batphone root directory

Write-Host "Libsodium Setup Script for Batphone" -ForegroundColor Green
Write-Host "====================================" -ForegroundColor Green
Write-Host ""

$LIBSODIUM_DIR = "app\src\main\jni\libsodium"
$TARGET_DIR = "$LIBSODIUM_DIR\libsodium-android-armv7-a\lib"

Write-Host "Checking if libsodium already built..." -ForegroundColor Yellow

if (Test-Path "$TARGET_DIR\libsodium.a") {
    Write-Host "libsodium.a already exists!" -ForegroundColor Green
    Write-Host "Location: $TARGET_DIR\libsodium.a" -ForegroundColor Green
    Write-Host ""
    Write-Host "You can now build the project in Android Studio." -ForegroundColor Green
    exit 0
}

Write-Host "libsodium.a not found. Setting up..." -ForegroundColor Yellow
Write-Host ""

Write-Host "OPTION 1: Use Windows Subsystem for Linux (WSL)" -ForegroundColor Cyan
Write-Host "-----------------------------------------------" -ForegroundColor Cyan
Write-Host "If you have WSL installed:"
Write-Host '  wsl bash -c "cd /mnt/c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium && ./autogen.sh && cd dist-build && ./android-armv7-a.sh"'
Write-Host ""

Write-Host "OPTION 2: Manual Download" -ForegroundColor Cyan
Write-Host "-------------------------" -ForegroundColor Cyan
Write-Host "1. Download libsodium prebuilt for Android from:"
Write-Host "   https://github.com/jedisct1/libsodium/releases"
Write-Host ""
Write-Host "2. Or download from Maven repository:"
Write-Host "   https://repo1.maven.org/maven2/com/github/joshjdevl/libsodiumjni/libsodium-jni-aar/"
Write-Host ""
Write-Host "3. Extract libsodium.a for armeabi-v7a"
Write-Host "4. Copy to: $TARGET_DIR"
Write-Host ""

Write-Host "OPTION 3: Build using Git Bash" -ForegroundColor Cyan
Write-Host "-------------------------------" -ForegroundColor Cyan
Write-Host "If you have Git Bash installed:"
Write-Host "1. Open Git Bash"
Write-Host "2. Run:"
Write-Host "   cd /c/Users/Test/AndroidStudioProjects/batphone/app/src/main/jni/libsodium"
Write-Host "   ./autogen.sh"
Write-Host "   cd dist-build"
Write-Host "   export ANDROID_NDK_HOME=/c/Users/Test/AppData/Local/Android/Sdk/ndk/21.4.7075529"
Write-Host "   ./android-armv7-a.sh"
Write-Host ""

Write-Host "Creating directory structure..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path $TARGET_DIR | Out-Null
Write-Host "Directory created: $TARGET_DIR" -ForegroundColor Green
Write-Host ""

Write-Host "TEMPORARY WORKAROUND (FOR TESTING ONLY):" -ForegroundColor Red
Write-Host "-----------------------------------------" -ForegroundColor Red
Write-Host "To build without libsodium (WARNING: Breaks encryption!):"
Write-Host "See LIBSODIUM_BUILD.md for instructions" -ForegroundColor Red
Write-Host ""

Write-Host "After building libsodium, retry the gradle build." -ForegroundColor Green

