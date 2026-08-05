# PC-TempRoot-UI — progress + monitor + OneIMS recommend + sponsor QR
param(
    [switch]$DryRun,
    [switch]$NoAutoStart,
    [switch]$AutoClose
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$Adb = Join-Path $Root 'adb\adb.exe'
$SoDir = Join-Path $Root 'so'
$ShDir = Join-Path $Root 'sh'
$Assets = Join-Path $Root 'assets'
$SponsorImg = Join-Path $Assets 'sponsor_wechat.jpg'
$RemoteSo = '/data/local/tmp/preload-comet.so'
$Attempts = 4
$RetryGapSec = 3
$LdTimeoutSec = 180
$StatusFile = Join-Path $env:TEMP 'pc-temproot-ui-last.txt'
$OneImsUrl = 'https://github.com/asrtroh-netizen/OneIms'
$OneImsReleases = 'https://github.com/asrtroh-netizen/OneIms/releases'

if (-not (Test-Path -LiteralPath $Adb)) {
    [System.Windows.Forms.MessageBox]::Show("Missing adb:`n$Adb", 'PC-TempRoot-UI', 'OK', 'Error') | Out-Null
    exit 2
}

function Invoke-Adb {
    param([string[]]$AdbArgs, [int]$TimeoutSec = 30)
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $Adb
    $psi.WorkingDirectory = (Split-Path -Parent $Adb)
    $psi.Arguments = (($AdbArgs | ForEach-Object {
        if ($_ -match '[\s"]') { '"{0}"' -f ($_ -replace '"', '\"') } else { $_ }
    }) -join ' ')
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    $p = New-Object System.Diagnostics.Process
    $p.StartInfo = $psi
    [void]$p.Start()
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    while (-not $p.HasExited) {
        [System.Windows.Forms.Application]::DoEvents()
        Start-Sleep -Milliseconds 50
        if ($sw.Elapsed.TotalSeconds -ge $TimeoutSec) {
            try { $p.Kill() } catch {}
            return @{ Code = 124; Text = '[timeout]' }
        }
    }
    return @{
        Code = $p.ExitCode
        Text = ($p.StandardOutput.ReadToEnd() + $p.StandardError.ReadToEnd()).Trim()
    }
}

function Test-RootOk([string]$text) {
    if ([string]::IsNullOrEmpty($text)) { return $false }
    return ($text.Contains('uid=0(root)') -or $text.Contains('root=1'))
}

$form = New-Object System.Windows.Forms.Form
$form.Text = 'PC-TempRoot-UI'
$form.Size = New-Object System.Drawing.Size(960, 620)
$form.StartPosition = 'CenterScreen'
$form.BackColor = [System.Drawing.Color]::FromArgb(24, 28, 34)
$form.ForeColor = [System.Drawing.Color]::FromArgb(230, 236, 242)
$form.Font = New-Object System.Drawing.Font('Segoe UI', 9.5)
$form.MinimumSize = New-Object System.Drawing.Size(900, 560)

$title = New-Object System.Windows.Forms.Label
$title.Text = 'PC-TempRoot-UI'
$title.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 16)
$title.ForeColor = [System.Drawing.Color]::FromArgb(94, 210, 180)
$title.Location = New-Object System.Drawing.Point(20, 14)
$title.AutoSize = $true
$form.Controls.Add($title)

$subtitle = New-Object System.Windows.Forms.Label
$subtitle.Text = 'Temp root with progress · OneIMS recommend · WeChat sponsor'
$subtitle.ForeColor = [System.Drawing.Color]::FromArgb(140, 150, 160)
$subtitle.Location = New-Object System.Drawing.Point(22, 46)
$subtitle.AutoSize = $true
$form.Controls.Add($subtitle)

function New-Mon($text, $x, $y) {
    $l = New-Object System.Windows.Forms.Label
    $l.Text = $text
    $l.Location = New-Object System.Drawing.Point($x, $y)
    $l.Size = New-Object System.Drawing.Size(300, 22)
    $l.ForeColor = [System.Drawing.Color]::FromArgb(200, 210, 220)
    $form.Controls.Add($l)
    return $l
}

$lblSerial = New-Mon 'Serial: -' 20 84
$lblDevice = New-Mon 'Device / Build: -' 330 84
$lblSelinux = New-Mon 'SELinux: -' 20 108
$lblAttempt = New-Mon 'Attempt: -' 330 108
$lblStage = New-Mon 'Stage: idle' 20 132
$lblElapsed = New-Mon 'Step elapsed: 0s' 330 132

$progress = New-Object System.Windows.Forms.ProgressBar
$progress.Location = New-Object System.Drawing.Point(20, 168)
$progress.Size = New-Object System.Drawing.Size(620, 22)
$progress.Style = 'Continuous'
$form.Controls.Add($progress)

$lblPct = New-Object System.Windows.Forms.Label
$lblPct.Text = '0%'
$lblPct.Location = New-Object System.Drawing.Point(20, 196)
$lblPct.AutoSize = $true
$lblPct.ForeColor = [System.Drawing.Color]::FromArgb(94, 210, 180)
$form.Controls.Add($lblPct)

$log = New-Object System.Windows.Forms.TextBox
$log.Multiline = $true
$log.ScrollBars = 'Vertical'
$log.ReadOnly = $true
$log.BackColor = [System.Drawing.Color]::FromArgb(16, 18, 22)
$log.ForeColor = [System.Drawing.Color]::FromArgb(210, 220, 230)
$log.Font = New-Object System.Drawing.Font('Consolas', 9)
$log.Location = New-Object System.Drawing.Point(20, 224)
$log.Size = New-Object System.Drawing.Size(620, 280)
$log.Anchor = 'Top,Bottom,Left'
$form.Controls.Add($log)

# Right panel: recommend + sponsor
$panel = New-Object System.Windows.Forms.Panel
$panel.Location = New-Object System.Drawing.Point(660, 84)
$panel.Size = New-Object System.Drawing.Size(260, 420)
$panel.BackColor = [System.Drawing.Color]::FromArgb(30, 34, 42)
$panel.Anchor = 'Top,Right,Bottom'
$form.Controls.Add($panel)

$recTitle = New-Object System.Windows.Forms.Label
$recTitle.Text = 'OneIMS Recommend'
$recTitle.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 11)
$recTitle.ForeColor = [System.Drawing.Color]::FromArgb(94, 210, 180)
$recTitle.Location = New-Object System.Drawing.Point(12, 12)
$recTitle.AutoSize = $true
$panel.Controls.Add($recTitle)

$recBody = New-Object System.Windows.Forms.Label
$recBody.Text = "After temp root, use OneIMS App for carrier / VoLTE tools.`nThis pack only does PC temp root."
$recBody.Location = New-Object System.Drawing.Point(12, 40)
$recBody.Size = New-Object System.Drawing.Size(236, 70)
$recBody.ForeColor = [System.Drawing.Color]::FromArgb(180, 190, 200)
$panel.Controls.Add($recBody)

$btnGitHub = New-Object System.Windows.Forms.Button
$btnGitHub.Text = 'Open OneIMS GitHub'
$btnGitHub.Location = New-Object System.Drawing.Point(12, 118)
$btnGitHub.Size = New-Object System.Drawing.Size(236, 30)
$btnGitHub.BackColor = [System.Drawing.Color]::FromArgb(40, 120, 100)
$btnGitHub.ForeColor = [System.Drawing.Color]::White
$btnGitHub.FlatStyle = 'Flat'
$panel.Controls.Add($btnGitHub)

$btnRel = New-Object System.Windows.Forms.Button
$btnRel.Text = 'Open Releases (APK)'
$btnRel.Location = New-Object System.Drawing.Point(12, 154)
$btnRel.Size = New-Object System.Drawing.Size(236, 30)
$btnRel.BackColor = [System.Drawing.Color]::FromArgb(50, 60, 72)
$btnRel.ForeColor = [System.Drawing.Color]::White
$btnRel.FlatStyle = 'Flat'
$panel.Controls.Add($btnRel)

$spTitle = New-Object System.Windows.Forms.Label
$spTitle.Text = 'WeChat Sponsor'
$spTitle.Font = New-Object System.Drawing.Font('Segoe UI Semibold', 11)
$spTitle.ForeColor = [System.Drawing.Color]::FromArgb(94, 210, 180)
$spTitle.Location = New-Object System.Drawing.Point(12, 200)
$spTitle.AutoSize = $true
$panel.Controls.Add($spTitle)

$spHint = New-Object System.Windows.Forms.Label
$spHint.Text = 'Same QR as OneIMS App. Voluntary.'
$spHint.Location = New-Object System.Drawing.Point(12, 226)
$spHint.Size = New-Object System.Drawing.Size(236, 32)
$spHint.ForeColor = [System.Drawing.Color]::FromArgb(150, 160, 170)
$panel.Controls.Add($spHint)

$pic = New-Object System.Windows.Forms.PictureBox
$pic.Location = New-Object System.Drawing.Point(42, 262)
$pic.Size = New-Object System.Drawing.Size(176, 176)
$pic.SizeMode = 'Zoom'
$pic.BackColor = [System.Drawing.Color]::White
$pic.BorderStyle = 'FixedSingle'
if (Test-Path -LiteralPath $SponsorImg) {
    $pic.Image = [System.Drawing.Image]::FromFile($SponsorImg)
} else {
    $spHint.Text = 'sponsor_wechat.jpg missing in assets\'
}
$panel.Controls.Add($pic)

function New-Btn($text, $x, $back) {
    $b = New-Object System.Windows.Forms.Button
    $b.Text = $text
    $b.Location = New-Object System.Drawing.Point($x, 530)
    $b.Size = New-Object System.Drawing.Size(140, 32)
    $b.BackColor = $back
    $b.ForeColor = [System.Drawing.Color]::White
    $b.FlatStyle = 'Flat'
    $b.Anchor = 'Bottom,Left'
    $form.Controls.Add($b)
    return $b
}

$btnStart = New-Btn 'Start Temp Root' 20 ([System.Drawing.Color]::FromArgb(40, 120, 100))
$btnDry = New-Btn 'Dry-run' 170 ([System.Drawing.Color]::FromArgb(50, 60, 72))
$btnDry.Size = New-Object System.Drawing.Size(100, 32)
$btnClose = New-Btn 'Close' 540 ([System.Drawing.Color]::FromArgb(50, 60, 72))
$btnClose.Size = New-Object System.Drawing.Size(100, 32)

$script:busy = $false
$stepWatch = [System.Diagnostics.Stopwatch]::StartNew()
$tick = New-Object System.Windows.Forms.Timer
$tick.Interval = 400
$tick.Add_Tick({
    if ($script:busy) {
        $lblElapsed.Text = ('Step elapsed: {0}s' -f [int]$stepWatch.Elapsed.TotalSeconds)
    }
})
$tick.Start()

function Ui-Log([string]$msg) {
    $line = '[{0}] {1}' -f (Get-Date -Format 'HH:mm:ss'), $msg
    $log.AppendText($line + [Environment]::NewLine)
    [System.Windows.Forms.Application]::DoEvents()
}
function Ui-Progress([int]$pct, [string]$stage) {
    $pct = [Math]::Max(0, [Math]::Min(100, $pct))
    $progress.Value = $pct
    $lblPct.Text = "$pct%"
    $lblStage.Text = "Stage: $stage"
    $stepWatch.Restart()
    [System.Windows.Forms.Application]::DoEvents()
}
function Ui-Monitor($serial, $device, $build, $selinux, $attemptText) {
    if ($null -ne $serial) { $lblSerial.Text = "Serial: $serial" }
    if ($null -ne $device) { $lblDevice.Text = "Device / Build: $device / $build" }
    if ($null -ne $selinux) { $lblSelinux.Text = "SELinux: $selinux" }
    if ($null -ne $attemptText) { $lblAttempt.Text = "Attempt: $attemptText" }
    [System.Windows.Forms.Application]::DoEvents()
}

function Start-TempRootJob([bool]$dry) {
    if ($script:busy) { return }
    $script:busy = $true
    $btnStart.Enabled = $false
    $btnDry.Enabled = $false
    $log.Clear()
    try {
        Ui-Log 'starting...'
        Ui-Progress 2 'init'
        Ui-Log 'adb devices...'
        Ui-Progress 8 'detect device'
        [void](Invoke-Adb @('start-server') 20)
        $devOut = (Invoke-Adb @('devices') 20).Text
        Ui-Log $devOut
        $serial = $null
        foreach ($line in ($devOut -split "`r?`n")) {
            if ($line -match '^(\S+)\s+device') { $serial = $Matches[1]; break }
        }
        if (-not $serial) {
            Ui-Progress 100 'FAIL: no device'
            Ui-Log 'FAIL: no authorized device'
            Set-Content -LiteralPath $StatusFile -Value 'FAIL no device' -Encoding UTF8
            return
        }
        Ui-Monitor $serial $null $null $null '-'
        Ui-Progress 15 'read props'
        $device = ((Invoke-Adb @('-s', $serial, 'shell', 'getprop', 'ro.product.device') 15).Text -replace '\s', '')
        $build = ((Invoke-Adb @('-s', $serial, 'shell', 'getprop', 'ro.build.id') 15).Text -replace '\s', '')
        Ui-Monitor $serial $device $build $null '-'
        Ui-Log "device=$device build=$build"
        $so = Join-Path $SoDir ("preload-$device-$build.so")
        if (-not (Test-Path -LiteralPath $so)) { $so = Join-Path $SoDir 'preload-comet.so' }
        if (-not (Test-Path -LiteralPath $so)) {
            Ui-Progress 100 'FAIL: no so'
            Set-Content -LiteralPath $StatusFile -Value 'FAIL no so' -Encoding UTF8
            return
        }
        Ui-Progress 25 'so matched'
        Ui-Log "so=$so"
        if ($dry) {
            Ui-Progress 100 'dry-run done'
            Ui-Log 'dry-run only'
            Set-Content -LiteralPath $StatusFile -Value "DRY_OK device=$device build=$build so=$so" -Encoding UTF8
            if ($AutoClose) { $form.Close() }
            return
        }
        Ui-Progress 35 'push so'
        $push = Invoke-Adb @('-s', $serial, 'push', $so, $RemoteSo) 120
        Ui-Log ("push rc={0} {1}" -f $push.Code, $push.Text)
        if ($push.Code -ne 0) {
            Ui-Progress 100 'FAIL: push'
            Set-Content -LiteralPath $StatusFile -Value 'FAIL push' -Encoding UTF8
            return
        }
        [void](Invoke-Adb @('-s', $serial, 'shell', 'chmod', '644', $RemoteSo) 15)
        $ok = $false
        $last = ''
        for ($i = 1; $i -le $Attempts; $i++) {
            $base = 40 + [int](50 * ($i - 1) / $Attempts)
            Ui-Monitor $serial $device $build $null ("$i / $Attempts")
            Ui-Progress $base "kill stuck $i/$Attempts"
            $killSh = Join-Path $ShDir 'kill-stuck.sh'
            [void](Invoke-Adb @('-s', $serial, 'push', $killSh, '/data/local/tmp/oneroot-kill.sh') 30)
            $k = Invoke-Adb @('-s', $serial, 'shell', 'sh', '/data/local/tmp/oneroot-kill.sh') 20
            Ui-Log ("kill: {0}" -f $k.Text)
            Ui-Progress ($base + 8) "LD_PRELOAD $i/$Attempts"
            Ui-Log "LD_PRELOAD attempt $i ..."
            $psi = New-Object System.Diagnostics.ProcessStartInfo
            $psi.FileName = $Adb
            $psi.WorkingDirectory = (Split-Path -Parent $Adb)
            $psi.Arguments = "-s $serial shell LD_PRELOAD=$RemoteSo /system/bin/id"
            $psi.UseShellExecute = $false
            $psi.RedirectStandardOutput = $true
            $psi.RedirectStandardError = $true
            $psi.CreateNoWindow = $true
            $p = New-Object System.Diagnostics.Process
            $p.StartInfo = $psi
            [void]$p.Start()
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            $lastBeat = 0
            while (-not $p.HasExited) {
                [System.Windows.Forms.Application]::DoEvents()
                Start-Sleep -Milliseconds 200
                $elapsed = [int]$sw.Elapsed.TotalSeconds
                $lblElapsed.Text = ('Step elapsed: {0}s' -f $elapsed)
                if ($elapsed -ge $LdTimeoutSec) { try { $p.Kill() } catch {}; break }
                if ($elapsed - $lastBeat -ge 5) {
                    Ui-Log ("... still running LD_PRELOAD {0}s / {1}s" -f $elapsed, $LdTimeoutSec)
                    Ui-Progress ($base + 8) ("LD_PRELOAD wait {0}s/{1}s" -f $elapsed, $LdTimeoutSec)
                    $lastBeat = $elapsed
                }
            }
            $last = ($p.StandardOutput.ReadToEnd() + $p.StandardError.ReadToEnd()).Trim()
            if (-not $p.HasExited) { $last = ($last + "`n[timeout]").Trim() }
            Ui-Log ("ld_preload: {0}" -f $last)
            if (Test-RootOk $last) { $ok = $true; break }
            Ui-Progress ($base + 16) "verify su $i/$Attempts"
            foreach ($suCmd in @('/data/local/tmp/su -c /system/bin/id','/apex/com.android.virt/bin/su -c /system/bin/id')) {
                $s = Invoke-Adb @('-s', $serial, 'shell', $suCmd) 12
                Ui-Log ("verify: {0}" -f $s.Text)
                if (Test-RootOk $s.Text) { $ok = $true; $last = $s.Text; break }
            }
            if ($ok) { break }
            if ($i -lt $Attempts) {
                Ui-Log ("no uid=0 yet, retry in {0}s" -f $RetryGapSec)
                $waitSw = [System.Diagnostics.Stopwatch]::StartNew()
                while ($waitSw.Elapsed.TotalSeconds -lt $RetryGapSec) {
                    [System.Windows.Forms.Application]::DoEvents()
                    Start-Sleep -Milliseconds 100
                }
            }
        }
        $en = (Invoke-Adb @('-s', $serial, 'shell', 'getenforce') 8).Text
        Ui-Monitor $serial $device $build $en 'done'
        Ui-Log ("getenforce: {0}" -f $en)
        if ($ok) {
            # 禁止 su -c libshizuku：会造 root 态 server，App 立刻掉线
            Ui-Progress 96 'rebind Shizuku as shell (no su -c libshizuku)'
            Ui-Log 'rebind: kill shizuku_server then adb-shell start (FORBIDDEN: su -c libshizuku)'
            [void](Invoke-Adb @('-s', $serial, 'shell', "/data/local/tmp/su -c '/system/bin/killall -9 shizuku_server' 2>/dev/null; /system/bin/killall -9 shizuku_server 2>/dev/null; true") 12)
            Start-Sleep -Milliseconds 400
            $apkLine = (Invoke-Adb @('-s', $serial, 'shell', 'pm path moe.shizuku.privileged.api') 10).Text
            $apk = ($apkLine -replace 'package:', '').Trim()
            if ($apk) {
                $lib = $apk -replace 'base\.apk$', 'lib/arm64/libshizuku.so'
                $st = Invoke-Adb @('-s', $serial, 'shell', "$lib --apk=$apk") 25
                Ui-Log ("shell-start shizuku: {0}" -f $st.Text)
                $ps = Invoke-Adb @('-s', $serial, 'shell', 'ps -A | grep shizuku_server || true') 8
                Ui-Log ("ps: {0}" -f $ps.Text)
            } else {
                Ui-Log 'WARN: Shizuku not installed; skip rebind'
            }
            Ui-Progress 100 'SUCCESS: temp root ok'
            Ui-Log ("SUCCESS: {0}" -f $last)
            Set-Content -LiteralPath $StatusFile -Value "SUCCESS $last" -Encoding UTF8
        } else {
            Ui-Progress 100 'FAIL: no uid=0'
            Set-Content -LiteralPath $StatusFile -Value 'FAIL no uid=0' -Encoding UTF8
        }
        if ($AutoClose) { $form.Close() }
    } catch {
        Ui-Log ("ERROR: {0}" -f $_.Exception.Message)
        Ui-Progress 100 'ERROR'
        Set-Content -LiteralPath $StatusFile -Value ("ERROR {0}" -f $_.Exception.Message) -Encoding UTF8
        if ($AutoClose) { $form.Close() }
    } finally {
        $script:busy = $false
        $btnStart.Enabled = $true
        $btnDry.Enabled = $true
    }
}

$btnGitHub.Add_Click({ Start-Process $OneImsUrl })
$btnRel.Add_Click({ Start-Process $OneImsReleases })
$btnClose.Add_Click({ $form.Close() })
$btnStart.Add_Click({ Start-TempRootJob $false })
$btnDry.Add_Click({ Start-TempRootJob $true })
$form.Add_Shown({
    if ($DryRun) { Start-TempRootJob $true }
    elseif (-not $NoAutoStart) { Start-TempRootJob $false }
})
[void]$form.ShowDialog()
$tick.Stop()
if ($pic.Image) { $pic.Image.Dispose() }
exit 0