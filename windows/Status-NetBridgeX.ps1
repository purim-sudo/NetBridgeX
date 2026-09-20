[CmdletBinding()]
param()
$ErrorActionPreference="Stop"
$adb=Join-Path $PSScriptRoot "..\tools\platform-tools\adb.exe"
if(!(Test-Path $adb)){throw "ADB not found."}
& $adb devices -l
Write-Host "--- reverse ---"
& $adb reverse --list
Write-Host "--- package ---"
& $adb shell dumpsys package com.netbridgex.android | Select-String "versionName=|versionCode="
Write-Host "--- interfaces ---"
& $adb shell ip -br addr | Select-String "testtun|tun"
Write-Host "--- tethering ---"
& $adb shell dumpsys tethering | Select-String "Upstream wanted|Current upstream|testtun|TetheredState|getConnectedClientList"
Write-Host "--- relay ---"
Get-Process gnirehtet -ErrorAction SilentlyContinue | Select-Object Id,StartTime,Path
