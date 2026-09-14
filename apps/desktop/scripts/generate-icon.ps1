param(
  [string]$OutputPath = (Join-Path $PSScriptRoot '..\build\icon.png')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$resolvedOutputPath = [System.IO.Path]::GetFullPath($OutputPath)
$outputDirectory = [System.IO.Path]::GetDirectoryName($resolvedOutputPath)
[System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null

$bitmap = [System.Drawing.Bitmap]::new(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
$graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$graphics.Clear([System.Drawing.Color]::Transparent)

$backgroundBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 255, 240, 222))
$outlineBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 60, 46, 45))
$furBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 38, 32, 34))
$innerEarBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 239, 139, 145))
$muzzleBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 53, 43, 43))
$irisBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 245, 177, 48))
$pupilBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 40, 32, 32))
$highlightBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
$noseBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(255, 231, 105, 116))
$blushBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(110, 239, 122, 131))
$mouthPen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 72, 55, 50), 10)
$mouthPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$mouthPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round

try {
  $graphics.FillEllipse($outlineBrush, 18, 18, 476, 476)
  $graphics.FillEllipse($backgroundBrush, 31, 31, 450, 450)

  $leftEarOutline = [System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(89, 195),
    [System.Drawing.Point]::new(121, 75),
    [System.Drawing.Point]::new(220, 165)
  )
  $rightEarOutline = [System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(423, 195),
    [System.Drawing.Point]::new(391, 75),
    [System.Drawing.Point]::new(292, 165)
  )
  $graphics.FillPolygon($outlineBrush, $leftEarOutline)
  $graphics.FillPolygon($outlineBrush, $rightEarOutline)

  $leftEar = [System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(104, 190),
    [System.Drawing.Point]::new(128, 103),
    [System.Drawing.Point]::new(205, 172)
  )
  $rightEar = [System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(408, 190),
    [System.Drawing.Point]::new(384, 103),
    [System.Drawing.Point]::new(307, 172)
  )
  $graphics.FillPolygon($furBrush, $leftEar)
  $graphics.FillPolygon($furBrush, $rightEar)

  $leftInnerEar = [System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(124, 165),
    [System.Drawing.Point]::new(137, 123),
    [System.Drawing.Point]::new(175, 161)
  )
  $rightInnerEar = [System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(388, 165),
    [System.Drawing.Point]::new(375, 123),
    [System.Drawing.Point]::new(337, 161)
  )
  $graphics.FillPolygon($innerEarBrush, $leftInnerEar)
  $graphics.FillPolygon($innerEarBrush, $rightInnerEar)

  $graphics.FillEllipse($outlineBrush, 73, 139, 366, 316)
  $graphics.FillEllipse($furBrush, 86, 151, 340, 290)
  $graphics.FillEllipse($muzzleBrush, 155, 276, 202, 120)

  $graphics.FillEllipse($irisBrush, 153, 233, 53, 65)
  $graphics.FillEllipse($irisBrush, 306, 233, 53, 65)
  $graphics.FillEllipse($pupilBrush, 170, 239, 25, 54)
  $graphics.FillEllipse($pupilBrush, 323, 239, 25, 54)
  $graphics.FillEllipse($highlightBrush, 168, 247, 13, 17)
  $graphics.FillEllipse($highlightBrush, 321, 247, 13, 17)
  $graphics.FillEllipse($blushBrush, 111, 305, 58, 27)
  $graphics.FillEllipse($blushBrush, 343, 305, 58, 27)

  $nose = [System.Drawing.Point[]]@(
    [System.Drawing.Point]::new(232, 310),
    [System.Drawing.Point]::new(280, 310),
    [System.Drawing.Point]::new(256, 335)
  )
  $graphics.FillPolygon($noseBrush, $nose)
  $graphics.DrawLine($mouthPen, 256, 334, 256, 346)
  $graphics.DrawArc($mouthPen, 211, 330, 45, 43, 10, 80)
  $graphics.DrawArc($mouthPen, 256, 330, 45, 43, 90, 80)

  $bitmap.Save($resolvedOutputPath, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
  $mouthPen.Dispose()
  $blushBrush.Dispose()
  $noseBrush.Dispose()
  $highlightBrush.Dispose()
  $pupilBrush.Dispose()
  $irisBrush.Dispose()
  $muzzleBrush.Dispose()
  $innerEarBrush.Dispose()
  $furBrush.Dispose()
  $outlineBrush.Dispose()
  $backgroundBrush.Dispose()
  $graphics.Dispose()
  $bitmap.Dispose()
}

Write-Output $resolvedOutputPath
