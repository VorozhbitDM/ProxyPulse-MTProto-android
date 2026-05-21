# Генерация ic_launcher_foreground.png для mipmap-* из Desktop app.png
# Масштаб 52% — вписывается в safe zone adaptive icon на любой маске
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing

$source = Resolve-Path (Join-Path $PSScriptRoot '..\..\Desktop\src\ProxyPulse\app.png')
$resRoot = Join-Path $PSScriptRoot '..\app\src\main\res'

$sizes = @{
    'mipmap-mdpi'    = 108
    'mipmap-hdpi'    = 162
    'mipmap-xhdpi'   = 216
    'mipmap-xxhdpi'  = 324
    'mipmap-xxxhdpi' = 432
}

$scale = 0.52

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

# Legacy launcher (то же изображение)
foreach ($folder in $sizes.Keys) {
    $px = $sizes[$folder]
    $out = Join-Path $resRoot "$folder\ic_launcher.png"
    Copy-Item (Join-Path $resRoot "$folder\ic_launcher_foreground.png") $out -Force
    $round = Join-Path $resRoot "$folder\ic_launcher_round.png"
    Copy-Item (Join-Path $resRoot "$folder\ic_launcher_foreground.png") $round -Force
}

Write-Host "Done. Background color: res/values/colors.xml ic_launcher_background"
