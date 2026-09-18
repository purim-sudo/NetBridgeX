# NetBridgeX

NetBridgeX is a modern Windows + Android reverse-tethering project derived from Gnirehtet.

It keeps the proven Rust relay protocol, targets the current Android SDK, and adds automatic Personal Hotspot route exclusion so devices connected to the phone hotspot are not accidentally forced through the reverse-tether VPN.

## What it does

- USB/ADB reverse tethering from Android to Windows.
- The Android phone's own Internet traffic uses the NetBridgeX VpnService over the Windows relay.
- Transparent Personal Hotspot bridging on supported Android devices using Android's test-network tethering path; hotspot clients use the NetBridgeX test network as their upstream.
- HTTP/HTTPS proxy fallback on port 8080 for devices where transparent tethering is unavailable.
- Android 13+ hotspot subnet exclusion using VpnService route throws in standard VPN mode.
- Automatic detection of the active hotspot network.
- Automatic recovery when the relay, VPN, or tether helper dies.
- Automatic reconfiguration when the Android network changes.
- Mobile/cellular data is not enabled by the controller; the intended path is Windows reverse tethering.
- Independent Android package: com.netbridgex.android.
- App label: NetBridgeX.

## Verified on the development PC

The debug APK was built successfully with Gradle 8.11.1, Android SDK 36, and Java 17.

The development Android handset has been verified with a live NetBridgeX VPN on tun0 and transparent hotspot tethering through a test TUN upstream. A Wi-Fi hotspot client was observed at 10.134.154.184, and Android tethering reported the NetBridgeX test network as its active upstream.

Recent relay logs show live TCP and UDP sessions originating from the NetBridgeX VPN address 192.0.2.2, confirming active reverse-tether traffic.

The Windows controller was also tested against relay/helper failures and automatically recreated the required components.

Cellular mobile data is deliberately disabled by the controller; NetBridgeX is intended to use the Windows reverse-tether path only.

## Build

Install Java 17, Android SDK 36, and Android platform-tools.

Then run:

    gradlew.bat :app:assembleDebug

APK:

    app/build/outputs/apk/debug/netbridgex-debug.apk

## Run on Windows

Connect the Android phone by USB and enable ADB debugging.

Run:

    Start-NetBridgeX.bat

For automatic logon startup:

    powershell -ExecutionPolicy Bypass -File .\Install-NetBridgeX.ps1

To stop it:

    Stop-NetBridgeX.bat

By default the Windows controller installs the APK, prepares the ADB reverse socket, starts the Rust relay, and launches the rootless hotspot bridge helper through Android's ADB shell process. The helper starts the Personal Hotspot, creates a test TUN network, selects that network as the tethering upstream, and forwards the TUN traffic through the Rust relay. Set NETBRIDGEX_HOTSPOT_BRIDGE=0 to use the legacy standard VPN/proxy mode instead.

## Hotspot clients

In the default mode, connect a second phone or other Wi-Fi client directly to the NetBridgeX Personal Hotspot. No proxy setting is required. Android tethering uses the NetBridgeX test TUN as its upstream, while the NetBridgeX helper forwards the packets through the Windows Rust relay.

The development handset has been verified with a Wi-Fi client at 10.134.154.184 and Android tethering reports the NetBridgeX test network as the active upstream. HTTP/HTTPS proxy fallback remains available on port 8080 by setting NETBRIDGEX_HOTSPOT_BRIDGE=0.

## First run

Android may request notification permission and VPN authorization the first time NetBridgeX is started. Approve both prompts.

## Architecture

app/ — Android VPN client.

relay-rust/ — Rust relay source.

gnirehtet-relay.exe — bundled Windows relay binary used by the controller.

tools/NetBridgeX.ps1 — Windows controller and recovery loop.

Start-NetBridgeX.bat / Stop-NetBridgeX.bat — simple Windows entry points.

## Limits

The underlying Gnirehtet transport is still primarily an IPv4 reverse-tethering transport. IPv6 forwarding is not implemented by the relay.

## Attribution

NetBridgeX is derived from Genymobile Gnirehtet and remains subject to the original Apache License 2.0 terms. See LICENSE and NOTICE.

[executed on device: RA4-PC (208e3cc7-73c6-4fa8-8412-076ebf265ed1)]