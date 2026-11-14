# P.T. Architech pearlescent triangle icon, consistent with the minimal watch complication.
# Generates assets; never changes installed apps or user watch music.
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$path=Join-Path (Split-Path -Parent $PSScriptRoot) 'app\src\main\res\drawable-nodpi\ptiv_icon.png'
$bmp=New-Object Drawing.Bitmap(512,512)
$g=[Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode=[Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.CompositingQuality=[Drawing.Drawing2D.CompositingQuality]::HighQuality
$g.TextRenderingHint=[Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$g.Clear([Drawing.Color]::Transparent)
function Brush([int]$a,[int]$r,[int]$gC,[int]$b){return (New-Object Drawing.SolidBrush([Drawing.Color]::FromArgb($a,$r,$gC,$b)))}
$dark=Brush 255 7 21 27
$inner=Brush 255 12 39 45
$gold=[Drawing.Color]::FromArgb(255,249,176,76)
$teal=[Drawing.Color]::FromArgb(255,52,225,203)
$purple=[Drawing.Color]::FromArgb(255,147,75,211)
$light=[Drawing.Color]::FromArgb(255,250,255,250)
$g.FillEllipse($dark,7,7,498,498)
$g.FillEllipse($inner,23,23,466,466)
$outerPen=New-Object Drawing.Pen($teal,7)
$g.DrawEllipse($outerPen,17,17,478,478)
$points=[Drawing.PointF[]]@(
    [Drawing.PointF]::new(256,51),
    [Drawing.PointF]::new(62,376),
    [Drawing.PointF]::new(450,376)
)
$tri=New-Object Drawing.Drawing2D.GraphicsPath
$tri.AddPolygon($points)
$g.FillPath((Brush 255 26 21 59),$tri)
$goldPen=New-Object Drawing.Pen($gold,17)
$goldPen.LineJoin=[Drawing.Drawing2D.LineJoin]::Round
$g.DrawPath($goldPen,$tri)
$offsetPen=New-Object Drawing.Pen($teal,4)
$offsetPen.LineJoin=[Drawing.Drawing2D.LineJoin]::Round
$g.DrawLine($offsetPen,251,72,82,360)
$g.DrawLine($offsetPen,82,360,435,360)
$g.DrawLine($offsetPen,435,360,261,72)
# Pearlescent sphere: teal, amethyst and pearl highlights inside clipped sphere.
$orbRect=New-Object Drawing.Rectangle(161,167,190,190)
$g.FillEllipse((Brush 255 43 96 111),$orbRect)
$orbPath=New-Object Drawing.Drawing2D.GraphicsPath
$orbPath.AddEllipse($orbRect)
$grad=New-Object Drawing.Drawing2D.PathGradientBrush($orbPath)
$grad.CenterPoint=[Drawing.PointF]::new(221,211)
$grad.CenterColor=[Drawing.Color]::FromArgb(255,255,255,244)
$grad.SurroundColors=[Drawing.Color[]]@([Drawing.Color]::FromArgb(255,96,56,155))
$g.FillEllipse($grad,$orbRect)
$oldClip=$g.Clip
$clipRegion=New-Object Drawing.Region($orbPath)
$g.SetClip($clipRegion,[Drawing.Drawing2D.CombineMode]::Replace)
$g.FillEllipse((Brush 56 50 255 227),177,185,124,133)
$g.FillEllipse((Brush 78 215 91 250),233,244,114,125)
$g.FillEllipse((Brush 115 255 255 255),183,194,60,38)
$g.FillEllipse((Brush 100 239 247 255),199,201,27,18)
$g.Clip=$oldClip
$orbPen=New-Object Drawing.Pen([Drawing.Color]::FromArgb(245,245,241,255),6)
$g.DrawEllipse($orbPen,$orbRect)
$font=New-Object Drawing.Font('Arial',55,[Drawing.FontStyle]::Bold,[Drawing.GraphicsUnit]::Pixel)
$format=New-Object Drawing.StringFormat
$format.Alignment=[Drawing.StringAlignment]::Center
$format.LineAlignment=[Drawing.StringAlignment]::Center
$g.DrawString('PTIV',$font,(Brush 255 244 255 247),(New-Object Drawing.RectangleF(65,389,382,83)),$format)
$bmp.Save($path,[Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp.Dispose()
'ICON_PEARL_TRIANGLE_CREATED'
Get-Item -LiteralPath $path | Select-Object FullName,Length
(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
