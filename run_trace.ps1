# Find the highest-version AfterFX.exe and run apply_paths.jsx via -r.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Jsx = Join-Path $Root "apply_paths.jsx"
$Json = Join-Path $Root "paths.json"

if (-not (Test-Path -LiteralPath $Jsx)) {
    throw "Missing apply_paths.jsx at $Jsx"
}
if (-not (Test-Path -LiteralPath $Json)) {
    throw "Missing paths.json at $Json — run analyze_lines.py first"
}

function Get-AfterFxCandidates {
    $list = New-Object System.Collections.Generic.List[string]

    # Prefer currently running instance
    Get-Process -Name "AfterFX" -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.Path) { $list.Add($_.Path) }
    }

    $roots = @(
        "D:\Work\ADOBE",
        "C:\Program Files\Adobe",
        "C:\Program Files (x86)\Adobe",
        "D:\Program Files\Adobe",
        "E:\Program Files\Adobe"
    )
    foreach ($r in $roots) {
        if (Test-Path -LiteralPath $r) {
            Get-ChildItem -LiteralPath $r -Recurse -Filter "AfterFX.exe" -ErrorAction SilentlyContinue |
                ForEach-Object { $list.Add($_.FullName) }
        }
    }

    # Registry App Paths
    try {
        $reg = Get-ItemProperty "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\AfterFX.exe" -ErrorAction SilentlyContinue
        if ($reg.'(default)') { $list.Add([string]$reg.'(default)') }
    } catch {}

    return $list | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -Unique
}

function Get-AeVersionScore([string]$exePath) {
    # Higher score = newer. Parse year from path like "After Effects 2025".
    $score = 0
    if ($exePath -match "After Effects\s+(\d{4})") {
        $score = [int]$Matches[1] * 1000
    } elseif ($exePath -match "(20\d{2})") {
        $score = [int]$Matches[1] * 1000
    }
    try {
        $vi = [version](Get-Item -LiteralPath $exePath).VersionInfo.ProductVersion
        $score += $vi.Major * 10 + $vi.Minor
    } catch {}
    return $score
}

$candidates = @(Get-AfterFxCandidates)
if ($candidates.Count -eq 0) {
    throw "AfterFX.exe not found. Is After Effects installed?"
}

$ranked = $candidates |
    ForEach-Object { [pscustomobject]@{ Path = $_; Score = (Get-AeVersionScore $_) } } |
    Sort-Object Score -Descending

$AfterFx = $ranked[0].Path
Write-Host "Using AfterFX: $AfterFx (score=$($ranked[0].Score))"
Write-Host "Running JSX: $Jsx"

# -r runs the script immediately in the current/open AE instance when possible.
$args = @("-r", $Jsx)
$p = Start-Process -FilePath $AfterFx -ArgumentList $args -PassThru -Wait
Write-Host "AfterFX exit code: $($p.ExitCode)"
exit $p.ExitCode
