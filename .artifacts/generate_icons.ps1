Add-Type -AssemblyName System.Drawing
$source = "C:\Users\PC 2021\Downloads\Buecher.png"
$img = [System.Drawing.Image]::FromFile($source)

$sizes = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

foreach ($dir in $sizes.Keys) {
    $size = $sizes[$dir]
    $targetDir = "C:\Users\PC 2021\StudioProjects\Buecher\app\src\main\res\$dir"
    if (!(Test-Path $targetDir)) { New-Item -ItemType Directory -Path $targetDir | Out-Null }

    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $graphics = [System.Drawing.Graphics]::FromImage($bmp)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($img, 0, 0, $size, $size)

    $bmp.Save("$targetDir\ic_launcher.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Save("$targetDir\ic_launcher_round.png", [System.Drawing.Imaging.ImageFormat]::Png)

    $graphics.Dispose()
    $bmp.Dispose()
}
$img.Dispose()
Write-Host "Icons generated successfully!"
