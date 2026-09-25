# NetBridgeX

NetBridgeX is a Windows + Android USB reverse-tethering project.

Primary path:

**Windows Internet → USB/ADB → Gnirehtet relay → Android VPN/TUN → Android applications**

The current development target is a reliable USB-only mode where Android Wi-Fi and mobile data can be disabled while application traffic uses the PC Internet path.

## Architecture

- GnirehtetActivity — Android control/status UI and VPN permission flow.
- GnirehtetService — Android VpnService and lifecycle management.
- Forwarder — moves IPv4 packets between Android TUN and the relay.
- RelayTunnel — connects to the ADB reverse local socket.
- PersistentRelayTunnel — reconnects when the relay channel is interrupted.
- HotspotProxyServer — optional HTTP CONNECT proxy helper.
- NetBridgeXTetherShell — experimental privileged/test-network component.
- windows/ — Windows bootstrap, start, status, stop, and validation scripts.

The transport currently targets IPv4. Gnirehtet itself also documents IPv4-only TCP/UDP support, so IPv6 is a separate future engineering task.

## Prerequisites

- Windows 10/11.
- One Android device with USB debugging enabled and authorized.
- A working USB data cable.
- JDK 17.
- Internet access on the Windows host for first-time tool provisioning.

The project checks in the Gradle Wrapper so a global Gradle installation is not required.
## Tool provisioning

From the repository root:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\windows\Ensure-NetBridgeXTools.ps1
~~~

This provisions:

- Android Platform-Tools (adb) into tools/platform-tools/.
- Gnirehtet Rust 2.5.1 Windows relay into tools/gnirehtet/.

The relay archive is SHA-256 verified before extraction. Generated binaries remain ignored by Git.

For local Android builds, set the JDK and SDK environment for the current PowerShell session:

~~~powershell
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot"
$env:ANDROID_SDK_ROOT="$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME=$env:ANDROID_SDK_ROOT
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:Path"
~~~

## Build and test

Build the complete development and release artifacts:

~~~powershell
.\gradlew.bat clean testDebugUnitTest assembleDebug assembleRelease
~~~

Debug APK:

app/build/outputs/apk/debug/app-debug.apk

Release build:

app/build/outputs/apk/release/app-release-unsigned.apk

The release artifact is intentionally unsigned until a proper signing/CI secret setup is added.
## Start NetBridgeX with the graphical Control Center

For normal day-to-day use on Windows, launch:

~~~text
Desktop -> NetBridgeX Control Center
~~~

The Control Center automatically detects the authorized Android device and provides these controls:

- **Start USB-only** — starts the strict USB reverse-tethering mode with Wi-Fi and mobile data disabled.
- **Start TikTok Compatibility** — starts the mode currently used for TikTok compatibility. Wi-Fi is disabled, while Android cellular data remains enabled so Android exposes a CELLULAR network that TikTok can accept alongside the NetBridgeX VPN.
- **Stop & Restore** — stops the VPN/relay and restores the Wi-Fi/mobile-data state that existed before startup.
- **Refresh Status** — shows Android connection, mode, Wi-Fi, mobile data, VPN, USB relay, and compatibility transport status.

The Control Center does not require typing a serial number or PowerShell commands.

The Windows GUI files are:

~~~text
windows/NetBridgeX-ControlCenter.ps1
windows/Launch-NetBridgeX-ControlCenter.vbs
windows/Start-NetBridgeX-ControlCenter.bat
~~~

A desktop shortcut named **NetBridgeX Control Center** launches the graphical interface without opening a command window.

## First-time Android setup

1. Connect the Android phone to Windows with a USB data cable.
2. Enable **USB debugging** in Android Developer Options.
3. Unlock the phone and accept the USB debugging authorization prompt.
4. Start NetBridgeX from the Control Center.
5. On the first VPN start, Android may display the system VPN approval dialog. Approve NetBridgeX.
6. Keep the USB connection active while using NetBridgeX.

The Windows startup script automatically installs the current debug APK from:

~~~text
app/build/outputs/apk/debug/app-debug.apk
~~~

## Start USB-only mode

Connect and authorize the Android device first. Android may request notification permission and then the system VPN approval the first time after installation; both approvals are required.

### Graphical method

Open **NetBridgeX Control Center** from the Windows desktop and select **Start USB-only**.

### Command-line method

~~~powershell
powershell -ExecutionPolicy Bypass -File .\windows\Start-NetBridgeX.ps1 start
~~~

The USB-only launcher:

1. Provisions local ADB and relay tools.
2. Finds the authorized device.
3. Installs the locally built debug APK.
4. Records the existing Wi-Fi/mobile-data state.
5. Disables Wi-Fi and mobile data for the test.
6. Creates localabstract:gnirehtet -> tcp:31416.
7. Starts the Windows relay.
8. Starts the NetBridgeX Android VPN.
9. Prints a diagnostic status report.

## TikTok Compatibility Mode

TikTok Compatibility Mode is the current mode to use when TikTok needs Android to expose a cellular network while NetBridgeX is active.

### Graphical method

Open **NetBridgeX Control Center** and select **Start TikTok Compatibility**.

### Command-line method

~~~powershell
powershell -ExecutionPolicy Bypass -File .\windows\Start-NetBridgeX.ps1 start -TikTokCompatibility
~~~

Expected test configuration:

- Wi-Fi: **OFF**
- Mobile data setting: **ON**
- NetBridgeX VPN: **ON**
- USB reverse tunnel: **ON**
- Android tun0: **ACTIVE**
- TikTok: For You feed, scrolling, and profiles can be tested over the NetBridgeX path.

The mobile-data setting being ON is intentional in this compatibility mode. On the tested stock Android device, keeping a cellular network exposed is currently required for TikTok compatibility; this mode therefore does **not** meet the stricter requirement of mobile-data toggle OFF.

## Check state

Graphical method: open **NetBridgeX Control Center** and select **Refresh Status**.

Command-line method:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\windows\Status-NetBridgeX.ps1
~~~

## Stop and restore

Graphical method: select **Stop & Restore** in the Control Center.

Command-line method:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\windows\Stop-NetBridgeX.ps1
~~~

On stop, NetBridgeX restores the Wi-Fi/mobile-data state it recorded at startup. Use -KeepRadiosOff when intentionally leaving the radios disabled.

For more than one ADB device, pass -Serial <device-serial>.

## End-to-end validation

Run:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\windows\Test-NetBridgeX.ps1
~~~

The validation checks:

- Authorized ADB device.
- NetBridgeX package installed.
- ADB reverse mapping.
- Wi-Fi/mobile-data state.
- Windows relay process.
- Android TUN/VPN interface.
- Android Internet request through the VPN using NetBridgeXHttpTest.

For a larger throughput test:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\windows\Test-NetBridgeX.ps1 -Url "https://speed.cloudflare.com/__down?bytes=5000000"
~~~

Treat the Android result as the end-to-end USB tunnel measurement; a normal Windows browser speed test measures only the host connection.

## Troubleshooting

### VPN says ACTIVE but an application has no Internet

Check, in order:

1. Wi-Fi is off.
2. Mobile data is off.
3. adb reverse --list contains the gnirehtet mapping.
4. gnirehtet.exe is still running.
5. The Android VPN interface exists.
6. Connectivity diagnostics show INTERNET and VALIDATED.
7. Run Test-NetBridgeX.ps1 and inspect its HTTP result.

### ADB device is unauthorized

Run:

~~~powershell
.\tools\platform-tools\adb.exe devices
~~~

Unlock the phone and accept the USB debugging authorization prompt.

### VPN permission is requested

Android allows only one active VPN at a time. Stop any other VPN before starting NetBridgeX.

### Relay connection drops

Restart the USB reverse mapping:

~~~powershell
.\tools\platform-tools\adb.exe reverse --remove-all
.\tools\platform-tools\adb.exe reverse localabstract:gnirehtet tcp:31416
~~~
Then restart NetBridgeX.

## Compatibility scope

The production target is USB reverse tethering over IPv4.

Applications may exercise HTTPS/TCP, UDP, DNS, HTTP/2, QUIC/UDP 443, and long-lived connections differently. Real application testing is therefore required in addition to the basic HTTP probe.

Known limitation: IPv6 is not currently an end-to-end transport in NetBridgeX.

## Development rules

Do not commit generated APKs, Gradle build output, local relay binaries, machine-specific state, or logs.

When changing transport behavior, rebuild the Android APK and retest:

DNS → HTTPS/TCP → UDP → QUIC/443 → sustained connections → real applications → throughput.

The repository contains code derived from Genymobile Gnirehtet and retains the Apache License 2.0 attribution in NOTICE.

## CI

GitHub Actions uses the checked-in Gradle Wrapper, Java 17, Android SDK 36, unit tests, debug build, and unsigned release build.

A green CI build proves compilation and unit tests. It does not replace physical USB/Android integration testing.
