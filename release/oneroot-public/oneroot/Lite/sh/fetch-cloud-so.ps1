# Fetch preload.so from GitHub OneSo-assets (cloud only; no bundled so/).
param(
    [Parameter(Mandatory = $true)][string]$Device,
    [Parameter(Mandatory = $true)][string]$Build,
    [Parameter(Mandatory = $true)][string]$CacheDir
)

$ErrorActionPreference = "Stop"
$base = "https://raw.githubusercontent.com/asrtroh-netizen/OneSo-assets/main/"
New-Item -ItemType Directory -Force -Path $CacheDir | Out-Null

function Get-CloudText([string]$Url) {
    if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
        $out = & curl.exe -fsSL --max-time 60 $Url
        if ($LASTEXITCODE -ne 0) { throw "curl failed: $Url" }
        return ($out -join "`n")
    }
    return [string](Invoke-RestMethod -Uri $Url -TimeoutSec 60)
}

function Get-CloudFile([string]$Url, [string]$OutFile) {
    if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
        & curl.exe -fsSL --max-time 120 -o $OutFile $Url
        if ($LASTEXITCODE -ne 0) { throw "curl download failed: $Url" }
        return
    }
    Invoke-WebRequest -Uri $Url -OutFile $OutFile -TimeoutSec 120
}

$catJson = Get-CloudText ($base + "catalog.json")
$cat = $catJson | ConvertFrom-Json
$rel = $cat.devices.$Device.$Build
if (-not $rel) {
    throw "catalog miss $Device/$Build"
}
$relStr = [string]$rel
$name = [IO.Path]::GetFileName($relStr)
$dst = Join-Path $CacheDir $name
$url = if ($relStr -match '^https?://') { $relStr } else { $base + $relStr.TrimStart('/') }

$sumsRaw = Get-CloudText ($base + "SHA256SUMS")
$want = $null
foreach ($line in ($sumsRaw -split "`n")) {
    $t = $line.Trim()
    if (-not $t) { continue }
    $p = $t -split "\s+"
    if ($p.Count -lt 2) { continue }
    $n = $p[-1].TrimStart("*").Replace("\", "/")
    if ($n -eq $relStr -or $n -eq $name -or [IO.Path]::GetFileName($n) -eq $name) {
        $want = $p[0].ToLower()
        break
    }
}

if (-not $want) {
    throw "SHA256SUMS miss for $relStr — refuse without cloud hash"
}

if ((Test-Path -LiteralPath $dst) -and ((Get-Item -LiteralPath $dst).Length -ge 64)) {
    $h = (Get-FileHash -LiteralPath $dst -Algorithm SHA256).Hash.ToLower()
    if ($h -eq $want) {
        Write-Output $dst
        exit 0
    }
}

Get-CloudFile $url $dst
if (-not (Test-Path -LiteralPath $dst)) {
    throw "download failed"
}
$h = (Get-FileHash -LiteralPath $dst -Algorithm SHA256).Hash.ToLower()
if ($h -ne $want) {
    Remove-Item -Force $dst -ErrorAction SilentlyContinue
    throw "sha mismatch got=$h want=$want"
}
Write-Output $dst
