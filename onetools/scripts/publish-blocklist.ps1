# Publish one-blocklist.json into asrtroh-netizen/OneBlock (phone/)
param(
    [string]$JsonPath = "",
    [string]$Repo = "asrtroh-netizen/OneBlock",
    [string]$PathInRepo = "phone/one-blocklist.json"
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

$bytes = [IO.File]::ReadAllBytes($JsonPath)
$b64 = [Convert]::ToBase64String($bytes)

$sha = $null
try {
    $sha = gh api "repos/$Repo/contents/$PathInRepo" --jq .sha 2>$null
} catch {
    $sha = $null
}

$obj = [ordered]@{
    message = "chore: update OneTools phone blocklist"
    content = $b64
    branch = "main"
}
if ($sha) { $obj.sha = $sha }

$tmp = Join-Path $env:TEMP "oneblock-put-blocklist.json"
[IO.File]::WriteAllText($tmp, ($obj | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false))
gh api -X PUT "repos/$Repo/contents/$PathInRepo" --input $tmp | Out-Null

$raw = "https://raw.githubusercontent.com/$Repo/main/$PathInRepo"
Write-Host "OK OneBlock: $raw"

$assetCopy = Join-Path $root "onetools\src\main\assets\sample-one-blocklist.json"
Copy-Item $JsonPath $assetCopy -Force

if ($env:ONE_CDN_PUT_URL) {
    Write-Host "PUT to CDN via ONE_CDN_PUT_URL..."
    $headers = @{}
    if ($env:ONE_CDN_PUT_TOKEN) {
        $headers["Authorization"] = "Bearer $($env:ONE_CDN_PUT_TOKEN)"
    }
    Invoke-RestMethod -Method Put -Uri $env:ONE_CDN_PUT_URL -InFile $JsonPath -ContentType "application/json" -Headers $headers
    Write-Host "OK CDN PUT"
} else {
    Write-Host "SKIP real CDN: set ONE_CDN_PUT_URL to also push cdn.oneims.app"
}
