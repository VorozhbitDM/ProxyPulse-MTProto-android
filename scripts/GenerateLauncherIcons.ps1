# Генерация ic_launcher*.png для mipmap-* из android/icon.png (или корня репозитория).
# Запасной вариант: Desktop/src/ProxyPulse/app.png. Масштаб 52% — safe zone adaptive icon.
param(
    [string]$SourcePath,
    [double]$Scale = -1
)

$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$androidRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$resRoot = Join-Path $PSScriptRoot '..\app\src\main\res'

$candidates = @(
    (Join-Path $androidRoot 'Newicon333.jpg'),
    (Join-Path $androidRoot 'Newicon333.png'),
    (Join-Path $androidRoot 'Newicon333.webp'),
    (Join-Path $androidRoot 'icon.png'),
    (Join-Path $androidRoot 'icon.jpg'),
    (Join-Path $androidRoot 'icon.webp'),
    (Join-Path $repoRoot 'icon.png'),
    (Join-Path $repoRoot 'icon.jpg'),
    (Join-Path $repoRoot 'icon.webp')
)

$desktopFallback = Join-Path $repoRoot 'Desktop\src\ProxyPulse\app.png'

if ($SourcePath) {
    if (-not (Test-Path $SourcePath)) { throw "Source not found: $SourcePath" }
    $source = (Resolve-Path $SourcePath).Path
}
else {
    $source = $null
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            $source = (Resolve-Path $candidate).Path
            break
        }
    }
    if (-not $source) {
        if (-not (Test-Path $desktopFallback)) {
            throw @"
Launcher icon source not found.
Place icon.png in android/ or repo root, or pass -SourcePath.
"@
        }
        $source = (Resolve-Path $desktopFallback).Path
        Write-Warning "No icon.png in android/ or repo root; using Desktop\src\ProxyPulse\app.png"
    }
}

Write-Host "Source: $source"

$sizes = @{
    'mipmap-mdpi'    = 108
    'mipmap-hdpi'    = 162
    'mipmap-xhdpi'   = 216
    'mipmap-xxhdpi'  = 324
    'mipmap-xxxhdpi' = 432
}

# 1.0 — иконка уже с фоном (Newicon333); 0.88 — отступ от маски; 0.52 — логотип без фона
if ($Scale -lt 0) {
    $baseName = [System.IO.Path]::GetFileNameWithoutExtension($source)
    $scale = if ($baseName -match '^(?i)Newicon') { 0.68 } else { 0.88 }
} else {
    $scale = $Scale
}

function Get-CenterSquareCrop([System.Drawing.Image]$img) {
    $s = [Math]::Min($img.Width, $img.Height)
    $x = [int](($img.Width - $s) / 2)
    $y = [int](($img.Height - $s) / 2)
    return New-Object System.Drawing.Rectangle $x, $y, $s, $s
}

function New-ForegroundPng([string]$srcPath, [int]$size, [string]$outPath, [double]$iconScale) {
    $src = [System.Drawing.Image]::FromFile($srcPath)
    try {
        $bmp = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

        $inner = [int][Math]::Round($size * $iconScale)
        $offset = [int](($size - $inner) / 2)
        $dest = New-Object System.Drawing.Rectangle $offset, $offset, $inner, $inner
        $crop = Get-CenterSquareCrop $src
        $g.DrawImage($src, $dest, $crop, [System.Drawing.GraphicsUnit]::Pixel)

        $dir = Split-Path $outPath -Parent
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        $bmp.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $g.Dispose()
        $bmp.Dispose()
    }
    finally {
        $src.Dispose()
    }
}

foreach ($folder in $sizes.Keys) {
    $px = $sizes[$folder]
    $out = Join-Path $resRoot "$folder\ic_launcher_foreground.png"
    New-ForegroundPng $source $px $out $scale
    Write-Host "Wrote $out ($px px)"
}

foreach ($folder in $sizes.Keys) {
    $out = Join-Path $resRoot "$folder\ic_launcher.png"
    Copy-Item (Join-Path $resRoot "$folder\ic_launcher_foreground.png") $out -Force
    $round = Join-Path $resRoot "$folder\ic_launcher_round.png"
    Copy-Item (Join-Path $resRoot "$folder\ic_launcher_foreground.png") $round -Force
}

Write-Host "Done. Background: res/values/colors.xml -> ic_launcher_background"
