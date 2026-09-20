[CmdletBinding()]
param()
$ErrorActionPreference="Stop"
$adb=Join-Path $PSScriptRoot "..\tools\platform-tools\adb.exe"
if(!(Test-Path $adb)){throw "ADB not found. Run Start-NetBridgeX.ps1 once or install platform-tools."}
& $adb shell am force-stop com.netbridgex.android | Out-Null
$pid=(& $adb shell "cat /data/local/tmp/netbridgex-tether.pid 2>/dev/null").ToString().Trim()
if($pid -match '^\d+$'){& $adb shell kill $pid 2>$null}
& $adb reverse --remove-all | Out-Null
Get-Process gnirehtet -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Write-Host "NetBridgeX stopped."
