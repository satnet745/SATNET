[CmdletBinding()]
param(
    [string]$DeviceId,
    [string[]]$Classes = @(
        'org.servalproject.satnet.ui.QRScannerActivityDeviceSmokeTest',
        'org.servalproject.satnet.ui.VoucherRedemptionActivityDeviceSmokeTest'
    )
)

$ErrorActionPreference = 'Stop'

function Get-RepoRoot {
    return Split-Path -Parent $PSScriptRoot
}

function Get-SdkDir([string]$RepoRoot) {
    $localPropertiesPath = Join-Path $RepoRoot 'local.properties'
    if (-not (Test-Path $localPropertiesPath)) {
        throw "Could not find local.properties at $localPropertiesPath"
    }

    $sdkLine = Get-Content $localPropertiesPath | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if (-not $sdkLine) {
        throw "local.properties does not define sdk.dir"
    }

    $sdkDir = ($sdkLine -replace '^sdk\.dir=', '').Trim()
    $sdkDir = $sdkDir.Replace('\:', ':')
    $sdkDir = $sdkDir.Replace('\\', '\')
    return $sdkDir
}

function Get-AdbPath([string]$SdkDir) {
    $adbPath = Join-Path $SdkDir 'platform-tools\adb.exe'
    if (-not (Test-Path $adbPath)) {
        throw "Could not find adb.exe at $adbPath"
    }
    return $adbPath
}

$repoRoot = Get-RepoRoot
$sdkDir = Get-SdkDir -RepoRoot $repoRoot
$adbPath = Get-AdbPath -SdkDir $sdkDir

Write-Host "Repo root: $repoRoot"
Write-Host "Android SDK: $sdkDir"
Write-Host "Using adb: $adbPath"

$adbDevicesOutput = & $adbPath devices
$adbDevicesOutput | ForEach-Object { Write-Host $_ }
$connectedDevices = $adbDevicesOutput | Where-Object { $_ -match '\sdevice$' } | ForEach-Object { ($_ -split '\s+')[0] }

if (-not $connectedDevices -or $connectedDevices.Count -eq 0) {
    throw 'No connected device or emulator detected. Connect a device, authorize adb, then rerun this script.'
}

if ($DeviceId) {
    if ($connectedDevices -notcontains $DeviceId) {
        throw "Requested device '$DeviceId' is not connected. Connected devices: $($connectedDevices -join ', ')"
    }
    $env:ANDROID_SERIAL = $DeviceId
    Write-Host "Targeting device: $DeviceId"
} elseif ($connectedDevices.Count -gt 1) {
    Write-Host "Multiple devices detected: $($connectedDevices -join ', ')"
    Write-Host 'Gradle will target all attached compatible devices unless you rerun with -DeviceId.'
}

$classesArg = 'android.testInstrumentationRunnerArguments.class=' + ($Classes -join ',')
$gradleArgs = @(
    ':app:connectedDebugAndroidTest',
    '--project-prop', $classesArg,
    '--no-daemon',
    '--stacktrace'
)

Push-Location $repoRoot
try {
    & (Join-Path $repoRoot 'gradlew.bat') @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "connectedDebugAndroidTest failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
    if ($DeviceId) {
        Remove-Item Env:ANDROID_SERIAL -ErrorAction SilentlyContinue
    }
}

