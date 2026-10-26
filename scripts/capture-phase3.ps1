param(
    [ValidateSet('reference','phone','compact','tablet','landscape')][string]$Viewport='reference',
    [string]$Serial='emulator-5554'
)
$ErrorActionPreference='Stop'
$adb='C:\Users\Vahram\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$root=Split-Path $PSScriptRoot
$sizes=@{reference='780x1386';phone='780x1688';compact='640x1280';tablet='1536x2048';landscape='1688x780'}
$avd=((& $adb -s $Serial emu avd name) | Select-Object -First 1).Trim()
if($avd -ne 'Medium_Phone_API_37.0'){throw "Expected task AVD, found $avd"}
& $adb -s $Serial shell wm size $sizes[$Viewport]
if($LASTEXITCODE -ne 0){throw 'Display resize failed'}
& $adb -s $Serial shell wm density 320
& $adb -s $Serial shell am force-stop com.spidertracker.app
& $adb -s $Serial shell am start -W -n com.spidertracker.app/.MainActivity --ez capture true | Out-Null
if($LASTEXITCODE -ne 0){throw 'Launch failed'}
Start-Sleep -Seconds 5
$destination=Join-Path $root "docs\phase-3-screenshots\$Viewport-map.png"
& $adb -s $Serial shell screencap -p /sdcard/phase3.png
& $adb -s $Serial pull /sdcard/phase3.png $destination | Out-Null
if($LASTEXITCODE -ne 0){throw 'Capture failed'}
@{serial=$Serial;avd=$avd;api=((& $adb -s $Serial shell getprop ro.build.version.sdk) | Select-Object -First 1).Trim();pixels=$sizes[$Viewport];densityDpi=320;animationTime=0.7;captureUtc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath "$destination.json" -Encoding utf8
Write-Output $destination
