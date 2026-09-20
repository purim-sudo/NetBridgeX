# NetBridgeX

NetBridgeX is a Windows + Android reverse-tethering system derived from the Gnirehtet transport.

## Intended path

Windows Internet -> USB/ADB -> NetBridgeX relay -> Android VPN -> phone Internet.

On supported Android devices, the same relay can also feed the Android Personal Hotspot through a shell-side test-network bridge:

Windows -> USB/ADB -> relay -> Android test TUN -> Wi-Fi hotspot -> other phones/devices.

The Android bridge is implemented by `NetBridgeXTetherShell`. It creates a test TUN, asks Android tethering to prefer that TEST network as its upstream, starts the Wi-Fi hotspot and forwards hotspot-client IP traffic through the same relay.

## Windows controller

Use:

`windows/Start-NetBridgeX.bat`

Status:

`powershell -ExecutionPolicy Bypass -File .\windows\Status-NetBridgeX.ps1`

Stop:

`powershell -ExecutionPolicy Bypass -File .\windows\Stop-NetBridgeX.ps1`

The controller expects an authorized USB-debugging Android phone and automatically obtains the official Gnirehtet v2.5.1 Windows relay when needed.

## Build

GitHub Actions builds the Android debug APK with Java 17, Android SDK 36 and Gradle 8.14.3.

Local Gradle output:

`app/build/outputs/apk/debug/app-debug.apk`

The CI workflow is also registered on `main`, so pull requests and pushes run the Android build automatically.

## Architecture

- `GnirehtetService`: Android `VpnService` for the phone's own traffic.
- `Forwarder`: moves IP packets between the Android TUN and the relay.
- `RelayTunnel`: local abstract socket connected through ADB reverse.
- `NetBridgeXTetherShell`: privileged shell-side test-network + hotspot bridge.
- `HotspotProxyServer`: optional HTTPS CONNECT fallback.

The transport remains primarily IPv4, matching the underlying Gnirehtet relay.

## Attribution

NetBridgeX incorporates code derived from Genymobile Gnirehtet and remains subject to the Apache License 2.0. See NOTICE.
