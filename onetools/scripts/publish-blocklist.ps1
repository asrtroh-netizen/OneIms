# Publish one-blocklist.json to GitHub release mirror (+ optional CDN PUT).
param(
    [string]$JsonPath = "",
    [string]$Tag = "onetools-cdn-assets",
    [string]$Repo = "asrtroh-netizen/OneIms"
)

$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not $JsonPath) {
    $JsonPath = Join-Path $root "docs\product\samples\one-blocklist.json"
}
if (-not (Test-Path $JsonPath)) {
    throw "Missing $JsonPath"
}

Write-Host "Signing (best-effort)..."
$sign = Join-Path $root "onetools\scripts\sign_one_index.py"
if (Test-Path $sign) {
    python $sign sign $JsonPath
}

$assetDir = Join-Path $env:TEMP "onetools-cdn-assets"
New-Item -ItemType Directory -Force -Path $assetDir | Out-Null
$asset = Join-Path $assetDir "one-blocklist.json"
Copy-Item $JsonPath $asset -Force

Write-Host "Ensuring release $Tag ..."
$releaseOk = $false
try {
    gh release view $Tag --repo $Repo | Out-Null
    $releaseOk = $true
} catch {
    $releaseOk = $false
}
if (-not $releaseOk) {
    gh release create $Tag --repo $Repo --title "OneTools CDN assets" --notes "Mirror for one-blocklist.json until cdn.oneims.app credentials are wired."
}

Write-Host "Uploading asset..."
gh release upload $Tag $asset --repo $Repo --clobber

$mirror = "https://github.com/$Repo/releases/download/$Tag/one-blocklist.json"
Write-Host "OK mirror: $mirror"

if ($env:ONE_CDN_PUT_URL) {
    Write-Host "PUT to CDN via ONE_CDN_PUT_URL..."
    $headers = @{}
    if ($env:ONE_CDN_PUT_TOKEN) {
        $headers["Authorization"] = "Bearer $($env:ONE_CDN_PUT_TOKEN)"
    }
    Invoke-RestMethod -Method Put -Uri $env:ONE_CDN_PUT_URL -InFile $asset -ContentType "application/json" -Headers $headers
    Write-Host "OK CDN PUT"
} else {
    Write-Host "SKIP real CDN: set ONE_CDN_PUT_URL (+ optional ONE_CDN_PUT_TOKEN) to push cdn.oneims.app"
}
