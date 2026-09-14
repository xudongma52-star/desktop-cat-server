param(
  [Parameter(Mandatory = $true)]
  [string]$InputPath,

  [Parameter(Mandatory = $true)]
  [string]$OutputPath,

  [int]$MaxWidth = 512,
  [int]$MaxHeight = 384
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

if (-not ('DesktopCat.AssetProcessor' -as [type])) {
  $drawingAssemblyPath = [System.Drawing.Bitmap].Assembly.Location
  $runtimeDirectory = [System.Runtime.InteropServices.RuntimeEnvironment]::GetRuntimeDirectory()
  $gdiAssemblyPath = Join-Path $runtimeDirectory 'System.Private.Windows.GdiPlus.dll'
  $windowsCoreAssemblyPath = Join-Path $runtimeDirectory 'System.Private.Windows.Core.dll'
  $drawingPrimitivesAssemblyPath = Join-Path $runtimeDirectory 'System.Drawing.Primitives.dll'
  Add-Type -ReferencedAssemblies @(
    $drawingAssemblyPath,
    $drawingPrimitivesAssemblyPath,
    $gdiAssemblyPath,
    $windowsCoreAssemblyPath
  ) -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;

namespace DesktopCat
{
    public static class AssetProcessor
    {
        public static void RemoveGreenAndResize(string inputPath, string outputPath, int maxWidth, int maxHeight)
        {
            using (var source = new Bitmap(inputPath))
            using (var keyed = new Bitmap(source.Width, source.Height, PixelFormat.Format32bppArgb))
            {
                using (var graphics = Graphics.FromImage(keyed))
                {
                    graphics.DrawImageUnscaled(source, 0, 0);
                }

                var rectangle = new Rectangle(0, 0, keyed.Width, keyed.Height);
                var bitmapData = keyed.LockBits(rectangle, ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
                var bytes = Math.Abs(bitmapData.Stride) * keyed.Height;
                var pixels = new byte[bytes];
                Marshal.Copy(bitmapData.Scan0, pixels, 0, bytes);

                var minX = keyed.Width;
                var minY = keyed.Height;
                var maxX = -1;
                var maxY = -1;

                for (var y = 0; y < keyed.Height; y++)
                {
                    for (var x = 0; x < keyed.Width; x++)
                    {
                        var index = y * bitmapData.Stride + x * 4;
                        var blue = pixels[index];
                        var green = pixels[index + 1];
                        var red = pixels[index + 2];
                        var greenExcess = green - Math.Max(red, blue);

                        var alpha = 255;
                        if (greenExcess > 4)
                        {
                            alpha = 255 - greenExcess * 255 / 230;
                            alpha = Math.Max(0, Math.Min(255, alpha));
                        }

                        if (alpha < 16)
                        {
                            alpha = 0;
                        }
                        if (green > Math.Max(red, blue))
                        {
                            pixels[index + 1] = (byte)Math.Min(green, Math.Max(red, blue));
                        }

                        pixels[index + 3] = (byte)alpha;
                        if (alpha > 16)
                        {
                            minX = Math.Min(minX, x);
                            minY = Math.Min(minY, y);
                            maxX = Math.Max(maxX, x);
                            maxY = Math.Max(maxY, y);
                        }
                    }
                }

                Marshal.Copy(pixels, 0, bitmapData.Scan0, bytes);
                keyed.UnlockBits(bitmapData);

                if (maxX < minX || maxY < minY)
                {
                    throw new InvalidOperationException("No foreground cat pixels were found.");
                }

                var padding = 12;
                minX = Math.Max(0, minX - padding);
                minY = Math.Max(0, minY - padding);
                maxX = Math.Min(keyed.Width - 1, maxX + padding);
                maxY = Math.Min(keyed.Height - 1, maxY + padding);

                var cropWidth = maxX - minX + 1;
                var cropHeight = maxY - minY + 1;
                var scale = Math.Min((double)maxWidth / cropWidth, (double)maxHeight / cropHeight);
                scale = Math.Min(1.0, scale);
                var outputWidth = Math.Max(1, (int)Math.Round(cropWidth * scale));
                var outputHeight = Math.Max(1, (int)Math.Round(cropHeight * scale));

                using (var output = new Bitmap(outputWidth, outputHeight, PixelFormat.Format32bppArgb))
                using (var graphics = Graphics.FromImage(output))
                {
                    graphics.Clear(Color.Transparent);
                    graphics.CompositingMode = CompositingMode.SourceCopy;
                    graphics.CompositingQuality = CompositingQuality.HighQuality;
                    graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
                    graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
                    graphics.SmoothingMode = SmoothingMode.HighQuality;
                    graphics.DrawImage(
                        keyed,
                        new Rectangle(0, 0, outputWidth, outputHeight),
                        new Rectangle(minX, minY, cropWidth, cropHeight),
                        GraphicsUnit.Pixel);

                    var directory = Path.GetDirectoryName(outputPath);
                    if (!string.IsNullOrEmpty(directory))
                    {
                        Directory.CreateDirectory(directory);
                    }
                    output.Save(outputPath, ImageFormat.Png);
                }
            }
        }
    }
}
'@
}

$resolvedInputPath = (Resolve-Path -LiteralPath $InputPath).Path
$resolvedOutputPath = [System.IO.Path]::GetFullPath($OutputPath)
[DesktopCat.AssetProcessor]::RemoveGreenAndResize(
  $resolvedInputPath,
  $resolvedOutputPath,
  $MaxWidth,
  $MaxHeight
)

Write-Output $resolvedOutputPath
