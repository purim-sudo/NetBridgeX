Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$Root = Split-Path -Parent $PSScriptRoot
$StartScript = Join-Path $PSScriptRoot "Start-NetBridgeX.ps1"
$StopScript = Join-Path $PSScriptRoot "Stop-NetBridgeX.ps1"
$Tools = Join-Path $Root "tools"
$Adb = Join-Path $Tools "platform-tools\adb.exe"
$Relay = Join-Path $Tools "gnirehtet\gnirehtet.exe"
$StateFile = Join-Path $Root ".netbridgex-state.json"
$Pkg = "com.netbridgex.android"

$form = New-Object System.Windows.Forms.Form
$form.Text = "NetBridgeX Control Center"
$form.StartPosition = "CenterScreen"
$form.Size = New-Object System.Drawing.Size(760,560)
$form.MinimumSize = New-Object System.Drawing.Size(700,520)

$title = New-Object System.Windows.Forms.Label
$title.Text = "NetBridgeX Control Center"
$title.Font = New-Object System.Drawing.Font("Segoe UI",18,[System.Drawing.FontStyle]::Bold)
$title.Location = New-Object System.Drawing.Point(24,18)
$title.AutoSize = $true
$form.Controls.Add($title)

$subtitle = New-Object System.Windows.Forms.Label
$subtitle.Text = "USB reverse-tethering controls - no command entry required"
$subtitle.Location = New-Object System.Drawing.Point(27,54)
$subtitle.AutoSize = $true
$form.Controls.Add($subtitle)

$group = New-Object System.Windows.Forms.GroupBox
$group.Text = "Connection"
$group.Location = New-Object System.Drawing.Point(22,82)
$group.Size = New-Object System.Drawing.Size(700,155)
$form.Controls.Add($group)

$deviceLabel = New-Object System.Windows.Forms.Label
$deviceLabel.Location = New-Object System.Drawing.Point(18,28)
$deviceLabel.Size = New-Object System.Drawing.Size(650,24)
$deviceLabel.Text = "Android device: Checking..."
$group.Controls.Add($deviceLabel)

$modeLabel = New-Object System.Windows.Forms.Label
$modeLabel.Location = New-Object System.Drawing.Point(18,58)
$modeLabel.Size = New-Object System.Drawing.Size(650,24)
$modeLabel.Text = "Mode: Checking..."
$group.Controls.Add($modeLabel)

$radioLabel = New-Object System.Windows.Forms.Label
$radioLabel.Location = New-Object System.Drawing.Point(18,88)
$radioLabel.Size = New-Object System.Drawing.Size(650,24)
$radioLabel.Text = "Wi-Fi: -    Mobile data: -"
$group.Controls.Add($radioLabel)

$vpnLabel = New-Object System.Windows.Forms.Label
$vpnLabel.Location = New-Object System.Drawing.Point(18,118)
$vpnLabel.Size = New-Object System.Drawing.Size(650,24)
$vpnLabel.Text = "VPN: -    USB relay: -    Compatibility transport: -"
$group.Controls.Add($vpnLabel)

$startUsb = New-Object System.Windows.Forms.Button
$startUsb.Text = "Start USB-only"
$startUsb.Location = New-Object System.Drawing.Point(22,254)
$startUsb.Size = New-Object System.Drawing.Size(160,42)
$form.Controls.Add($startUsb)

$startTikTok = New-Object System.Windows.Forms.Button
$startTikTok.Text = "Start TikTok Compatibility"
$startTikTok.Location = New-Object System.Drawing.Point(194,254)
$startTikTok.Size = New-Object System.Drawing.Size(210,42)
$form.Controls.Add($startTikTok)

$stopBtn = New-Object System.Windows.Forms.Button
$stopBtn.Text = "Stop & Restore"
$stopBtn.Location = New-Object System.Drawing.Point(416,254)
$stopBtn.Size = New-Object System.Drawing.Size(140,42)
$form.Controls.Add($stopBtn)

$refreshBtn = New-Object System.Windows.Forms.Button
$refreshBtn.Text = "Refresh Status"
$refreshBtn.Location = New-Object System.Drawing.Point(568,254)
$refreshBtn.Size = New-Object System.Drawing.Size(154,42)
$form.Controls.Add($refreshBtn)

$log = New-Object System.Windows.Forms.TextBox
$log.Location = New-Object System.Drawing.Point(22,314)
$log.Size = New-Object System.Drawing.Size(700,180)
$log.Multiline = $true
$log.ScrollBars = "Vertical"
$log.ReadOnly = $true
$log.Font = New-Object System.Drawing.Font("Consolas",9)
$form.Controls.Add($log)

$status = New-Object System.Windows.Forms.Label
$status.Location = New-Object System.Drawing.Point(24,502)
$status.Size = New-Object System.Drawing.Size(690,24)
$status.Text = "Ready."
$form.Controls.Add($status)

function Get-DeviceSerial {
    if (!(Test-Path $Adb)) { throw "ADB not found. Run the NetBridgeX setup/provisioning first." }
    $rows = @(& $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "^\S+\s+device$" })
    if ($rows.Count -eq 0) { return $null }
    if ($rows.Count -gt 1) { throw "Multiple authorized Android devices are connected. Disconnect extras, then refresh." }
    return ($rows[0] -split "\s+")[0]
}

function Invoke-ExternalScript {
    param([string]$ScriptPath,[string[]]$Arguments)
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "powershell.exe"
    $psi.Arguments = "-NoProfile -ExecutionPolicy Bypass -File " + [char]34 + $ScriptPath + [char]34 + " " + ($Arguments -join " ")
    $psi.WorkingDirectory = $Root
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $p = New-Object System.Diagnostics.Process
    $p.StartInfo = $psi
    [void]$p.Start()
    $stdout = $p.StandardOutput.ReadToEnd()
    $stderr = $p.StandardError.ReadToEnd()
    $p.WaitForExit()
    if ($stdout) { $log.AppendText($stdout + [Environment]::NewLine) }
    if ($stderr) { $log.AppendText($stderr + [Environment]::NewLine) }
    return $p.ExitCode
}
function Refresh-Status {
    try {
        $serial = Get-DeviceSerial
        if (!$serial) {
            $deviceLabel.Text = "Android device: Not connected"
            $modeLabel.Text = "Mode: -"
            $radioLabel.Text = "Wi-Fi: -    Mobile data: -"
            $vpnLabel.Text = "VPN: -    USB relay: -    Compatibility transport: -"
            $status.Text = "Connect the Android phone by USB, then refresh."
            return
        }

        $wifi = ((& $Adb -s $serial shell settings get global wifi_on).Trim() -eq "1")
        $mobile = ((& $Adb -s $serial shell settings get global mobile_data).Trim() -eq "1")
        $reverse = ((& $Adb -s $serial reverse --list) -join " ") -match "gnirehtet"
        $connectivity = (& $Adb -s $serial shell dumpsys connectivity 2>$null) -join [Environment]::NewLine
        $vpn = $connectivity -match "VPN:com\.netbridgex\.android"
        $relay = @(Get-Process gnirehtet -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $Relay }).Count -gt 0
        $transport = ((& $Adb -s $serial shell sh -c "cat /data/local/tmp/netbridgex-tether.status 2>/dev/null" 2>$null) -join " ").Trim()

        $compat = $false
        if (Test-Path $StateFile) {
            try { $state = Get-Content $StateFile -Raw | ConvertFrom-Json; $compat = [bool]$state.tiktokCompatibility } catch {}
        }

        $deviceLabel.Text = "Android device: Connected ($serial)"
        if ($compat) { $modeLabel.Text = "Mode: TikTok Compatibility" } elseif ($reverse -or $vpn -or $relay) { $modeLabel.Text = "Mode: USB-only" } else { $modeLabel.Text = "Mode: Stopped" }
        $radioLabel.Text = "Wi-Fi: " + ($(if($wifi){"ON"}else{"OFF"})) + "    Mobile data: " + ($(if($mobile){"ON"}else{"OFF"}))
        $transportShort = if ($transport -match "^ACTIVE") { "ACTIVE" } elseif ($transport -match "^ERROR") { "ERROR" } else { "-" }
        $vpnLabel.Text = "VPN: " + ($(if($vpn){"ACTIVE"}else{"INACTIVE"})) + "    USB relay: " + ($(if($relay){"ACTIVE"}else{"INACTIVE"})) + "    Compatibility transport: " + $transportShort
        $status.Text = "Status refreshed " + (Get-Date).ToString("HH:mm:ss")
        $log.AppendText("`r`nDevice: $serial | VPN=$vpn | Relay=$relay | Reverse=$reverse | WiFi=$wifi | Mobile=$mobile`r`n")
    } catch {
        $status.Text = "Status error: " + $_.Exception.Message
    }
}
function Start-Mode {
    param([bool]$TikTok)
    try {
        $serial = Get-DeviceSerial
        if (!$serial) { throw "No authorized Android device found. Connect the phone by USB." }
        $log.Clear()
        $status.Text = "Starting NetBridgeX..."
        $startUsb.Enabled = $false
        $startTikTok.Enabled = $false
        $stopBtn.Enabled = $false
        $refreshBtn.Enabled = $false
        $args = @("start")
        if ($TikTok) { $args += "-TikTokCompatibility" }
        $code = Invoke-ExternalScript -ScriptPath $StartScript -Arguments $args
        if ($code -eq 0) {
            $status.Text = "NetBridgeX started successfully."
        } else {
            $status.Text = "NetBridgeX start failed. See the log."
        }
    } catch {
        $status.Text = "Start error: " + $_.Exception.Message
        [System.Windows.Forms.MessageBox]::Show($_.Exception.Message,"NetBridgeX","OK","Error") | Out-Null
    } finally {
        $startUsb.Enabled = $true
        $startTikTok.Enabled = $true
        $stopBtn.Enabled = $true
        $refreshBtn.Enabled = $true
        Refresh-Status
    }
}

$startUsb.Add_Click({ Start-Mode $false })
$startTikTok.Add_Click({ Start-Mode $true })

$stopBtn.Add_Click({
    try {
        $serial = Get-DeviceSerial
        if (!$serial) { throw "No authorized Android device found." }
        $log.Clear()
        $status.Text = "Stopping NetBridgeX and restoring previous radio state..."
        $code = Invoke-ExternalScript -ScriptPath $StopScript -Arguments @("-Serial",$serial)
        if ($code -eq 0) { $status.Text = "NetBridgeX stopped; previous radio state restored." } else { $status.Text = "Stop failed. See the log." }
    } catch {
        $status.Text = "Stop error: " + $_.Exception.Message
        [System.Windows.Forms.MessageBox]::Show($_.Exception.Message,"NetBridgeX","OK","Error") | Out-Null
    } finally { Refresh-Status }
})
$refreshBtn.Add_Click({ Refresh-Status })
$form.Add_Shown({ Refresh-Status })
$form.Add_FormClosing({
    # Do not alter phone radios when the Control Center itself closes.
})
[void]$form.ShowDialog()
