@echo off
setlocal
cd /d "%~dp0.."
title NetBridgeX - TikTok Compatibility Mode
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-NetBridgeX.ps1" start -TikTokCompatibility
echo.
echo NetBridgeX TikTok Compatibility Mode has finished starting.
echo You can close this window after the VPN is active.
pause
