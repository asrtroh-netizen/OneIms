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
    # Python urllib is the most reliable on this toolchain; curl/IWR as fallbacks.
    $py = Get-Command python -ErrorAction SilentlyContinue
    if ($py) {
        $code = @"
import urllib.request
req = urllib.request.Request('$Url', headers={'User-Agent': 'OneRoot-Lite/1.0'})
with urllib.request.urlopen(req, timeout=60) as r:
    print(r.read().decode('utf-8', 'replace'))
"@
        $out = & python -c $code
        if ($LASTEXITCODE -eq 0 -and $out) { return ($out -join "`n") }
    }
    if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
        $out = & curl.exe -fsSL --max-time 60 -A "OneRoot-Lite/1.0" $Url
        if ($LASTEXITCODE -eq 0 -and $out) { return ($out -join "`n") }
    }
    return [string](Invoke-RestMethod -Uri $Url -TimeoutSec 60)
}

function Get-CloudFile([string]$Url, [string]$OutFile) {
    $py = Get-Command python -ErrorAction SilentlyContinue
    if ($py) {
        $code = @"
import urllib.request
req = urllib.request.Request('$Url', headers={'User-Agent': 'OneRoot-Lite/1.0'})
with urllib.request.urlopen(req, timeout=120) as r:
    open(r'$OutFile', 'wb').write(r.read())
"@
        & python -c $code
        if ($LASTEXITCODE -eq 0 -and (Test-Path -LiteralPath $OutFile)) { return }
    }
    if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
        & curl.exe -fsSL --max-time 120 -A "OneRoot-Lite/1.0" -o $OutFile $Url
        if ($LASTEXITCODE -eq 0 -and (Test-Path -LiteralPath $OutFile)) { return }
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

if ((Test-Path -LiteralPath $dst) -and ((Get-Item -LiteralPath $dst).Length -ge 64)) {
    $h = (Get-FileHash -LiteralPath $dst -Algorithm SHA256).Hash.ToLower()
    if (-not $want -or $h -eq $want) {
        Write-Output $dst
        exit 0
    }
}

Get-CloudFile $url $dst
if (-not (Test-Path -LiteralPath $dst)) {
    throw "download failed"
}
$h = (Get-FileHash -LiteralPath $dst -Algorithm SHA256).Hash.ToLower()
if ($want -and $h -ne $want) {
    Remove-Item -Force $dst -ErrorAction SilentlyContinue
    throw "sha mismatch got=$h want=$want"
}
Write-Output $dst
