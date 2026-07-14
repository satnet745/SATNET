@echo off
echo Testing NDK Build Configuration...
echo.

cd /d "%~dp0\app\src\main\jni"

echo Current directory: %CD%
echo.

echo Checking if ndk-build can find files...
echo.

set NDK_BUILD="C:\Users\Test\AppData\Local\Android\Sdk\ndk\21.4.7075529\ndk-build.cmd"

echo Running ndk-build dry-run...
%NDK_BUILD% ^
  NDK_PROJECT_PATH=. ^
  APP_BUILD_SCRIPT=Android.mk ^
  NDK_APPLICATION_MK=Application.mk ^
  APP_ABI=armeabi-v7a ^
  APP_PLATFORM=android-16 ^
  -n

echo.
echo Exit code: %ERRORLEVEL%
echo.

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] ndk-build configuration has errors!
    echo.
    echo Try running actual build to see specific errors:
    echo %NDK_BUILD% NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk
) else (
    echo [OK] ndk-build configuration looks good!
)

pause

