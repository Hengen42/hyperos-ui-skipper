<#
.SYNOPSIS
  Builds the HyperOS UI Skipper flashable module (.zip).

  This module packages module.prop, installer scripts, the bundled hook APK,
  and the WebUI used to switch between Mode 1 and Mode 2.
#>

$ErrorActionPreference = 'Stop'

$Root    = $PSScriptRoot
$OutDir  = "$Root\out"
$ZipName = "HyperOS-UI-Skipper.zip"

Write-Host "== assemble module zip ==" -ForegroundColor Cyan
if (Test-Path $OutDir) { Remove-Item $OutDir -Recurse -Force }
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
Copy-Item "$Root\module.prop" "$OutDir\module.prop"

# Android shells require LF line endings; force LF when copying scripts
$Utf8NoBom = New-Object System.Text.UTF8Encoding $false
foreach ($script in @('customize.sh', 'uninstall.sh', 'action.sh', 'service.sh', 'status.sh', 'activate.sh')) {
    $content = [System.IO.File]::ReadAllText("$Root\$script") -replace "`r`n", "`n"
    [System.IO.File]::WriteAllText("$OutDir\$script", $content, $Utf8NoBom)
}

# The hook APK is bundled and installed by customize.sh.
$Apk = "$Root\..\xposed-module\HyperOS-UI-Skipper.apk"
if (-not (Test-Path $Apk)) { throw "Missing $Apk - build ../xposed-module first." }
Copy-Item $Apk "$OutDir\HyperOS-UI-Skipper.apk"
Copy-Item -Recurse "$Root\webroot" "$OutDir\webroot"

$zipPath = "$Root\$ZipName"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    Get-ChildItem -Path $OutDir -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($OutDir.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip, $_.FullName, $relative, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $zip.Dispose()
}

Write-Host ""
Write-Host "Built: $zipPath" -ForegroundColor Green
