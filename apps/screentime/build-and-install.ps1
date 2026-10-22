# Build the "เวลาดูทีวี" (TV Time) parental screen-time app and install it on the TV, then grant the
# app-ops it needs and start its watcher service. Uses the portable JDK/SDK under tools\buildenv when
# JAVA_HOME / ANDROID_HOME are not already set.
#
# Usage: apps\screentime\build-and-install.ps1 -TvHost 192.168.9.107
param(
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$here = $PSScriptRoot
$repo = (Resolve-Path (Join-Path $here '..\..')).Path
$be = Join-Path $repo 'tools\buildenv'

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $env:JAVA_HOME = (Get-ChildItem $be -Directory | Where-Object { $_.Name -like 'jdk*' } | Select-Object -First 1).FullName
}
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $be 'sdk' }
if (-not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { throw "JDK not found; set JAVA_HOME (looked in $be)" }

Set-Location $here
$sdk = $env:ANDROID_HOME
"sdk.dir=$($sdk -replace '\\', '\\')" | Out-File -Encoding ascii local.properties

.\gradlew.bat assembleRelease -q --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }

$py = Join-Path $repo 'tools\venv\Scripts\python.exe'
$apk = 'app\build\outputs\apk\release\app-release.apk'
& $py -I (Join-Path $repo 'tools\b64push.py') $apk /data/local/tmp/screentime.apk
if ($LASTEXITCODE -ne 0) { throw 'APK upload failed' }
$out = & $py -I (Join-Path $repo 'tools\tv.py') 'pm install -r /data/local/tmp/screentime.apk; rm -f /data/local/tmp/screentime.apk'
Write-Host $out
if ("$out" -notmatch 'Success') { throw 'pm install failed' }

# Permissions that cannot be asked for at runtime on a TV:
#  GET_USAGE_STATS  -> read which app is in the foreground
#  SYSTEM_ALERT_WINDOW -> draw the warning and start the lock screen from the background
@(
    'appops set com.gathin.screentime GET_USAGE_STATS allow',
    'appops set com.gathin.screentime SYSTEM_ALERT_WINDOW allow',
    'cmd package compile -m speed -f com.gathin.screentime'
) | & $py -I (Join-Path $repo 'tools\tv.py') --batch

# Start the watcher by opening the app (its MainActivity starts the service from inside the app;
# the service is not exported, so `am start-foreground-service` from the shell is denied). The app
# also starts the service on every boot via BootReceiver.
& $py -I (Join-Path $repo 'tools\tv.py') 'am start -n com.gathin.screentime/.MainActivity' | Out-Null
Write-Host 'Installed "เวลาดูทีวี". Open it from the launcher to set the parent PIN.'
