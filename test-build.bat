@echo off
echo ========================================
echo Testing libsodium build fix
echo ========================================
echo.

cd /d "%~dp0"

echo Cleaning previous build...
call gradlew.bat clean

echo.
echo Building project (this may take a few minutes)...
call gradlew.bat assembleDebug

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo BUILD SUCCESSFUL!
    echo ========================================
    echo The libsodium issue has been resolved.
    echo You can now open the project in Android Studio.
) else (
    echo.
    echo ========================================
    echo BUILD FAILED
    echo ========================================
    echo Please check the error messages above.
)

pause

