# Rebuild OneRoot-Lite/UI zips from oneroot-public (local truth), sync zipstage + OneSo-assets.
$ErrorActionPreference = 'Stop'
$release = 'E:\GQ\One\OneIMS\release'
$publicRoot = Join-Path $release 'oneroot-public\oneroot'
$stageRoot = Join-Path $release '_zipstage'
$assetsRoot = 'E:\GQ\One\OneSo-assets\oneroot'
$tmp = Join-Path $release '_tmp\zipbuild'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Sync-Dir {
  param([string]$Src, [string]$Dst)
  if (Test-Path -LiteralPath $Dst) {
    Remove-Item -LiteralPath $Dst -Recurse -Force
  }
  New-Item -ItemType Directory -Path (Split-Path $Dst) -Force | Out-Null
  Copy-Item -LiteralPath $Src -Destination $Dst -Recurse -Force
}

function New-ZipFromFolder {
  param(
    [Parameter(Mandatory)][string]$SourceDir,
    [Parameter(Mandatory)][string]$ZipPath,
    [Parameter(Mandatory)][string]$RootName
  )
  if (Test-Path -LiteralPath $ZipPath) {
    Remove-Item -LiteralPath $ZipPath -Force
  }
  $staging = Join-Path $tmp $RootName
  if (Test-Path -LiteralPath $staging) {
    Remove-Item -LiteralPath $staging -Recurse -Force
  }
  New-Item -ItemType Directory -Path $tmp -Force | Out-Null
  Copy-Item -LiteralPath $SourceDir -Destination $staging -Recurse -Force
  # Ensure shell scripts stay LF (zip as-is from public)
  [System.IO.Compression.ZipFile]::CreateFromDirectory(
    (Split-Path $staging -Parent),
    $ZipPath,
    [System.IO.Compression.CompressionLevel]::Optimal,
    $false
  )
  # CreateFromDirectory of parent would include sibling folders — rebuild carefully:
}

function New-ZipExact {
  param(
    [Parameter(Mandatory)][string]$SourceDir,
    [Parameter(Mandatory)][string]$ZipPath,
    [Parameter(Mandatory)][string]$RootName
  )
  if (Test-Path -LiteralPath $ZipPath) { Remove-Item -LiteralPath $ZipPath -Force }
  $wrap = Join-Path $tmp ("wrap-" + $RootName)
  $inner = Join-Path $wrap $RootName
  if (Test-Path -LiteralPath $wrap) { Remove-Item -LiteralPath $wrap -Recurse -Force }
  New-Item -ItemType Directory -Path $inner -Force | Out-Null
  Copy-Item -LiteralPath (Join-Path $SourceDir '*') -Destination $inner -Recurse -Force
  if (Test-Path -LiteralPath $ZipPath) { Remove-Item -LiteralPath $ZipPath -Force }
  [System.IO.Compression.ZipFile]::CreateFromDirectory(
    $wrap,
    $ZipPath,
    [System.IO.Compression.CompressionLevel]::Optimal,
    $false
  )
}

Write-Host '== sync public -> zipstage =='
Sync-Dir (Join-Path $publicRoot 'Lite') (Join-Path $stageRoot 'OneRoot-Lite')
Sync-Dir (Join-Path $publicRoot 'UI') (Join-Path $stageRoot 'OneRoot-UI')

Write-Host '== rebuild zips =='
New-ZipExact -SourceDir (Join-Path $stageRoot 'OneRoot-Lite') -ZipPath (Join-Path $release 'OneRoot-Lite.zip') -RootName 'OneRoot-Lite'
New-ZipExact -SourceDir (Join-Path $stageRoot 'OneRoot-UI') -ZipPath (Join-Path $release 'OneRoot-UI.zip') -RootName 'OneRoot-UI'

Write-Host '== sync zips -> OneSo-assets =='
$allowCloud = $env:ONESO_ALLOW_CLOUD_WRITE
if ($allowCloud -notin @('1', 'true', 'yes', 'on')) {
  Write-Host 'SKIP sync to OneSo-assets (set ONESO_ALLOW_CLOUD_WRITE=1 to allow)'
} else {
  if (-not (Test-Path -LiteralPath $assetsRoot)) {
    throw "OneSo-assets oneroot missing: $assetsRoot"
  }
  Copy-Item -LiteralPath (Join-Path $release 'OneRoot-Lite.zip') -Destination (Join-Path $assetsRoot 'OneRoot-Lite.zip') -Force
  Copy-Item -LiteralPath (Join-Path $release 'OneRoot-UI.zip') -Destination (Join-Path $assetsRoot 'OneRoot-UI.zip') -Force
}

# Also refresh nested extract OneRoot-Lite if present
$nested = Join-Path $release 'OneRoot-Lite\OneRoot-Lite'
if (Test-Path -LiteralPath (Join-Path $release 'OneRoot-Lite')) {
  Write-Host '== refresh nested extract release/OneRoot-Lite =='
  Remove-Item -LiteralPath (Join-Path $release 'OneRoot-Lite') -Recurse -Force
  Expand-Archive -LiteralPath (Join-Path $release 'OneRoot-Lite.zip') -DestinationPath (Join-Path $release 'OneRoot-Lite') -Force
}

Write-Host '== hashes =='
Get-FileHash -Algorithm SHA256 (Join-Path $release 'OneRoot-Lite.zip'), (Join-Path $release 'OneRoot-UI.zip') |
  ForEach-Object { '{0} {1}' -f $_.Hash, $_.Path }
Write-Host 'OK'
