# NetBridgeX

NetBridgeX is a Windows + Android **USB reverse-tethering** project.

Primary path:

**Windows Internet -> USB/ADB -> NetBridgeX relay -> Android VPN -> Android applications**

The goal of USB-only mode is that ordinary Android applications use the PC's Internet connection while the phone's normal Wi-Fi and mobile-data paths are disabled.

> NetBridgeX incorporates code derived from Genymobile Gnirehtet and remains subject to the Apache License 2.0. See NOTICE.

## Current USB-only mode

The currently tested configuration is:

- USB debugging authorized.
- ADB reverse tunnel: localabstract:gnirehtet -> tcp:31416.
- Android Wi-Fi: OFF.
- Android mobile data: OFF.
- NetBridgeX VPN: tun0, 10.0.0.2/32.
- VPN default route: 0.0.0.0/0.
- VPN DNS: 8.8.8.8.
- VPN MTU: 1500.
- Android VPN network reports INTERNET + VALIDATED.
- IPv4 is the primary transported address family.
- The Windows relay carries both TCP and UDP application traffic.

Android's NET_CAPABILITY_VALIDATED is the system's indication that the network has actual public-Internet reachability, rather than merely being configured as an Internet-capable network.

## Why the 1500-byte MTU matters

The Android VPN currently uses an MTU of 1500. This is intentionally conservative for USB reverse tethering and avoids the oversized-packet behavior seen during earlier testing.

The host relay and Android side should use compatible packet sizing when the relay is rebuilt. Do not independently change the Android MTU without testing the corresponding relay behavior.

## Windows quick start

### Option A - repository PowerShell controller

From the repository root:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\windows\Start-NetBridgeX.ps1
~~~

Check the state:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\windows\Status-NetBridgeX.ps1
~~~

Stop:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\windows\Stop-NetBridgeX.ps1
~~~

The start controller:

1. Verifies ADB and an authorized Android device.
2. Installs the local debug APK when it exists.
3. Starts the Windows relay.
4. Creates the ADB reverse tunnel.
5. Starts the Android VPN.
6. Verifies the VPN interface.
7. Starts the optional shell-side tethering component when that component is present.

### Option B - direct USB launcher

The working Windows USB launcher is also kept under:

~~~text
host-relay/gnirehtet-rust-win64/NetBridgeX-USB.cmd
~~~

It performs USB-only setup by disabling Wi-Fi and mobile data, creating the ADB reverse tunnel, and starting the Android VPN.

**Important:** stopping NetBridgeX does not automatically turn Wi-Fi or mobile data back on. Re-enable them manually when USB-only mode is no longer required.

## Android build

The Android project is under app/.

Build the debug APK:

~~~powershell
.\gradlew.bat assembleDebug --no-daemon
~~~

APK output:

~~~text
app/build/outputs/apk/debug/app-debug.apk
~~~

Install manually:

~~~powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
~~~

Prepare the USB reverse tunnel:

~~~powershell
adb reverse --remove-all
adb reverse localabstract:gnirehtet tcp:31416
~~~

Start the VPN:

~~~powershell
adb shell am start -a com.genymobile.gnirehtet.START -n com.netbridgex.android/com.genymobile.gnirehtet.GnirehtetActivity --esa dnsServers 8.8.8.8 --esa routes 0.0.0.0/0
~~~

Verify:

~~~powershell
adb shell ip addr show tun0
adb shell settings get global wifi_on
adb shell settings get global mobile_data
adb reverse --list
~~~

Expected USB-only state:

- tun0 exists with 10.0.0.2/32.
- wifi_on is 0.
- mobile_data is 0.
- adb reverse --list contains localabstract:gnirehtet tcp:31416.

## Throughput testing

NetBridgeX contains an Android-side HTTP test helper:

~~~text
com.genymobile.gnirehtet.NetBridgeXHttpTest
~~~

The helper can be launched with app_process using the installed APK:

~~~powershell
$apkPath=(adb shell cmd package path com.netbridgex.android | Select-Object -First 1).Trim() -replace '^package:',''
adb shell "CLASSPATH=$apkPath app_process / com.genymobile.gnirehtet.NetBridgeXHttpTest https://speed.cloudflare.com/__down?bytes=5000000"
~~~

It reports:

~~~text
HTTP_RESULT bytes=<bytes> seconds=<seconds> Mbps=<average>
~~~

Always measure the Android process separately from a direct PC speed test. A direct PC result measures the upstream connection; the Android result measures the complete Android -> USB/ADB -> relay -> Internet path.

## Application compatibility testing

USB reverse tethering is more than a browser test. Applications may use:

- HTTPS/TCP.
- UDP.
- DNS over UDP.
- DNS over TLS.
- HTTP/2.
- HTTP/3/QUIC.
- Long-lived TCP connections.
- Multiple simultaneous destinations.

The relay therefore needs to remain healthy under mixed TCP/UDP traffic.

### Facebook Lite

Facebook Lite has been tested with Wi-Fi and mobile data disabled. Its Fizz client successfully established its connection through the NetBridgeX VPN during testing.

### TikTok

TikTok can load video/reel content through the tunnel, but a separate page/information view has exhibited an application-level "No internet connection - tap to retry" state.

This is being treated as a transport/application-compatibility issue rather than assuming the entire VPN is offline. During investigation the relay has shown simultaneous TikTok-like TCP and UDP/443 traffic, while the Android VPN remains VALIDATED.

When debugging this symptom, collect both:

~~~powershell
adb logcat -d -v time
~~~

and the Windows relay log. Do not enable Wi-Fi or mobile data as a workaround during a USB-only test, because that would invalidate the test.

## Architecture

- GnirehtetService - Android VpnService; creates the phone-side TUN.
- Forwarder - moves IPv4 packets between the Android TUN and the relay tunnel.
- RelayTunnel - connects the Android side to the host through the ADB reverse local socket.
- PersistentRelayTunnel - reconnects the relay tunnel when the host-side channel disappears.
- NetBridgeXTetherShell - optional privileged shell-side test-network/hotspot component.
- HotspotProxyServer - optional HTTPS CONNECT fallback.
- host-relay/gnirehtet-rust-win64 - Windows Rust relay and USB launcher.

The core transport is currently IPv4. The Android VPN may expose an IPv6 link-local address, but the reverse-tether relay path is not an end-to-end IPv6 transport. IPv6 support should therefore be treated as a separate engineering task rather than assumed from the presence of an IPv6 address on tun0.

## Troubleshooting

### VPN exists but applications say "No internet"

1. Confirm Wi-Fi is off.
2. Confirm mobile data is off.
3. Confirm tun0 exists.
4. Confirm the VPN reports VALIDATED.
5. Confirm the ADB reverse mapping.
6. Confirm the Windows relay is still running.
7. Check relay logs for TCP resets, unexpected first packets, invalid IPv4 packets, and UDP/443 activity.
8. Repeat the test without changing the phone's radios.

### ADB reverse disappeared

Run:

~~~powershell
adb reverse --remove-all
adb reverse localabstract:gnirehtet tcp:31416
adb reverse --list
~~~

Then restart the VPN.

### VPN does not start

Check that Android USB debugging is authorized and that the NetBridgeX VPN permission has already been granted. Android permits only one active VPN at a time.

## GitHub Actions

The repository contains a CI workflow that builds the Android debug APK. Local builds use the Gradle wrapper included with the project.

## Development notes

Do not commit generated APKs, Gradle build output, local relay logs, phone screenshots, or machine-specific paths.

When changing the transport:

1. Build the Android APK.
2. Build/update the matching host relay when packet sizing changes.
3. Install the APK.
4. Disable Wi-Fi and mobile data.
5. Establish the ADB reverse tunnel.
6. Verify tun0 and VALIDATED.
7. Test DNS.
8. Test HTTPS/TCP.
9. Test UDP/443.
10. Test real applications such as Facebook Lite and TikTok.
11. Measure Android throughput separately from PC throughput.

## Attribution

NetBridgeX incorporates code derived from Genymobile Gnirehtet and remains subject to the Apache License 2.0. See NOTICE.
