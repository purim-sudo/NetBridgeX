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
  Ensure-Device; Ensure-Relay
  if(Test-Path $Apk){& $Adb install -r $Apk | Out-Host}
  Get-Process gnirehtet -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
  & $Adb reverse localabstract:gnirehtet tcp:31416 | Out-Host
  $p=Start-Process -FilePath $Relay -ArgumentList "relay" -WorkingDirectory (Split-Path $Relay) -PassThru -WindowStyle Hidden
  Start-Sleep -Milliseconds 600
  if($p.HasExited){throw "Gnirehtet relay failed to start."}
  & $Adb shell am start -W -n $Activity -a com.netbridgex.android.START --esa dnsServers 8.8.8.8 --esa routes 0.0.0.0/0 | Out-Host
  Start-Sleep -Seconds 2
  $apkPath=(& $Adb shell pm path $Pkg | Select-Object -First 1).ToString().Trim()
  if(!$apkPath.StartsWith("package:")){throw "Installed NetBridgeX APK path could not be resolved."}
  $apkPath=$apkPath.Substring(8)
  Start-Process -FilePath $Adb -ArgumentList @("shell","CLASSPATH=$apkPath","app_process","--nice-name=netbridgex-tether","/","com.genymobile.gnirehtet.NetBridgeXTetherShell") -WindowStyle Hidden | Out-Null
  Start-Sleep -Seconds 3
  & $PSScriptRootStatus-NetBridgeX.ps1
}
switch($Action){
  "start"{Start-NetBridgeX}
  "stop"{& $PSScriptRootStop-NetBridgeX.ps1}
  "status"{& $PSScriptRootStatus-NetBridgeX.ps1}
}
