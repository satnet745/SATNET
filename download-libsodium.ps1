# PowerShell script to download and place pre-compiled libsodium library

$ErrorActionPreference = "Stop"

# --- Configuration ---
$LibsodiumVersion = "1.0.18"
$DownloadUrl = "https://download.libsodium.org/libsodium/releases/libsodium-$($LibsodiumVersion)-android.tar.gz"
$ProjectRoot = "C:\Users\Test\AndroidStudioProjects\batphone"
$JniRoot = Join-Path $ProjectRoot "app\src\main\jni"
$TempDir = Join-Path $ProjectRoot "temp_libsodium"
$FinalDir = Join-Path $JniRoot "libsodium\libsodium-android-armv7-a"
$FinalLibPath = Join-Path $FinalDir "lib\libsodium.a"

# --- Script ---

Write-Host "🚀 Starting Libsodium Pre-compiled Setup..." -ForegroundColor Cyan

# 1. Check if the file already exists
if (Test-Path $FinalLibPath) {
    Write-Host "✅ SUCCESS: libsodium.a already exists at $FinalLibPath" -ForegroundColor Green
    Write-Host "You can now clean and rebuild the project in Android Studio."
    Read-Host "Press Enter to exit"
    exit 0
}

Write-Host "File not found. Proceeding with download..."

# 2. Create directories
Write-Host "📂 Creating necessary directories..."
New-Item -Path (Join-Path $FinalDir "lib") -ItemType Directory -Force | Out-Null
New-Item -Path (Join-Path $FinalDir "include") -ItemType Directory -Force | Out-Null
New-Item -Path $TempDir -ItemType Directory -Force | Out-Null
Write-Host "Directories created."

# 3. Download the file
$DownloadPath = Join-Path $TempDir "libsodium.tar.gz"
Write-Host "Downloading from $DownloadUrl..."
try {
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $DownloadPath
    Write-Host "✅ Download complete." -ForegroundColor Green
} catch {
    Write-Host "❌ ERROR: Download failed." -ForegroundColor Red
    Write-Host $_.Exception.Message
    exit 1
}

# 4. Extract the archive
Write-Host "📦 Extracting archive... (This may take a moment)"
try {
    # Windows 10/11 has tar command
    tar -xzf $DownloadPath -C $TempDir
    Write-Host "✅ Extraction complete." -ForegroundColor Green
} catch {
    Write-Host "❌ ERROR: Extraction failed. Please ensure 'tar' is available in your system PATH." -ForegroundColor Red
    Write-Host "Alternatively, manually extract '$DownloadPath' into '$TempDir' and re-run this script."
    exit 1
}

# 5. Copy the required files
$SourceLibPath = Join-Path $TempDir "libsodium-android-arm\libsodium\lib\armeabi-v7a\libsodium.a"
$SourceIncludePath = Join-Path $TempDir "libsodium-android-arm\libsodium\include"

Write-Host "Copying files..."
if (!(Test-Path $SourceLibPath)) {
    Write-Host "❌ ERROR: Could not find '$SourceLibPath' after extraction." -ForegroundColor Red
    exit 1
}

# Copy libsodium.a
Copy-Item -Path $SourceLibPath -Destination (Join-Path $FinalDir "lib") -Force
Write-Host "  - Copied libsodium.a"

# Copy include headers
Copy-Item -Path (Join-Path $SourceIncludePath "*") -Destination (Join-Path $FinalDir "include") -Recurse -Force
Write-Host "  - Copied header files"

# 6. Verify and Clean up
if (Test-Path $FinalLibPath) {
    Write-Host "✅ SUCCESS! libsodium.a is now in the correct location." -ForegroundColor Green
} else {
    Write-Host "❌ FINAL ERROR: File copy seems to have failed. Please check permissions." -ForegroundColor Red
    exit 1
}

Write-Host "🧹 Cleaning up temporary files..."
Remove-Item -Path $TempDir -Recurse -Force
Write-Host "Cleanup complete."

# --- Final Instructions ---
Write-Host "
-------------------------------------------------------------------
🎉 All Done! What to do next:
-------------------------------------------------------------------
1.  Go to Android Studio.
2.  Click on 'Build' -> 'Clean Project'.
3.  Click on 'Build' -> 'Rebuild Project'.

The build should now succeed!
" -ForegroundColor Yellow

Read-Host "Press Enter to exit"

