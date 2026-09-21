[CmdletBinding()]
param([ValidateSet("start","stop","status")][string]$Action="start")
$ErrorActionPreference="Stop"

$Root=Split-Path -Parent $PSScriptRoot
$Tools=Join-Path $Root "tools"
$Adb=Join-Path $Tools "platform-tools\adb.exe"
$Relay=Join-Path $Tools "gnirehtet\gnirehtet.exe"
$Apk=Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk"
$Pkg="com.netbridgex.android"
$Activity="$Pkg/com.genymobile.gnirehtet.GnirehtetActivity"

function Ensure-Device {
  if(!(Test-Path $Adb)){throw "ADB not found: $Adb"}
  $d=& $Adb devices
  if(-not ($d -match "	device$")){throw "No authorized Android device. Connect USB and accept debugging."}
}

function Ensure-Relay {
  if(Test-Path $Relay){return}
  New-Item -ItemType Directory -Force (Split-Path $Relay) | Out-Null
  $z=Join-Path $env:TEMP "gnirehtet-win64.zip"
  curl.exe -L --fail --silent --show-error "https://github.com/Genymobile/gnirehtet/releases/download/v2.5.1/gnirehtet-rust-win64-v2.5.1.zip" -o $z
  Expand-Archive $z -DestinationPath (Split-Path $Relay) -Force
  Move-Item (Join-Path (Split-Path $Relay) "gnirehtet-rust-win64\gnirehtet.exe") $Relay -Force
  Remove-Item (Join-Path (Split-Path $Relay) "gnirehtet-rust-win64") -Recurse -Force -ErrorAction SilentlyContinue
  Remove-Item $z -Force
}

function Start-NetBridgeX {
  Ensure-Device
  Ensure-Relay

  if(Test-Path $Apk){
    & $Adb install -r $Apk | Out-Host
  }

  # USB-only mode: do not allow Android applications to fall back to Wi-Fi
  # or normal mobile-data routing during the test.
  & $Adb shell svc wifi disable | Out-Null
  & $Adb shell svc data disable | Out-Null
  Write-Host "Wi-Fi: OFF"
  Write-Host "Mobile data: OFF"

  Get-Process gnirehtet -ErrorAction SilentlyContinue |
    Stop-Process -Force -ErrorAction SilentlyContinue

  & $Adb reverse --remove-all | Out-Host
  & $Adb reverse localabstract:gnirehtet tcp:31416 | Out-Host

  $p=Start-Process -FilePath $Relay -ArgumentList "relay" -WorkingDirectory (Split-Path $Relay) -PassThru -WindowStyle Hidden
  Start-Sleep -Milliseconds 600
  if($p.HasExited){throw "Gnirehtet relay failed to start."}

  & $Adb shell am start -W -a com.genymobile.gnirehtet.START -n $Activity --esa dnsServers 8.8.8.8 --esa routes 0.0.0.0/0 | Out-Host
  Start-Sleep -Seconds 2

  Write-Host "NetBridgeX USB-only VPN started."
  Write-Host "Verify with Status-NetBridgeX.ps1."
}

switch($Action){
  "start"  {Start-NetBridgeX}
  "stop"   {& "$PSScriptRoot\Stop-NetBridgeX.ps1"}
  "status" {& "$PSScriptRoot\Status-NetBridgeX.ps1"}
}
