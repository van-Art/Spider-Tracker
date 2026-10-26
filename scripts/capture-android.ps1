param(
    [ValidateSet('phone','tablet','landscape')][string]$Viewport='phone',
    [string[]]$Screens=@('map','menu','log','detail','events','report','webwatch','help','videos'),
    [string]$Suffix='final',
    [ValidatePattern('^emulator-\d+$')][string]$Serial='emulator-5554'
)
$ErrorActionPreference='Stop'
$adb='C:\Users\Vahram\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$root=Split-Path $PSScriptRoot
$sizes=@{phone='780x1688';tablet='1536x2048';landscape='1688x780'}
$avd=((& $adb -s $Serial emu avd name) | Select-Object -First 1).Trim()
if($avd -ne 'Medium_Phone_API_37.0'){throw "Expected task AVD Medium_Phone_API_37.0, found $avd on $Serial"}
$api=((& $adb -s $Serial shell getprop ro.build.version.sdk) | Select-Object -First 1).Trim()
& $adb -s $Serial shell wm size $sizes[$Viewport]
if($LASTEXITCODE -ne 0){throw 'Could not set emulator viewport'}
& $adb -s $Serial shell wm density 320
foreach($screen in $Screens) {
    if($screen -notin @('map','menu','log','detail','events','report','webwatch','help','videos','confirmed-off','both-off','card','media','gallery','sound','tutorial')){throw 'Unknown screenshot state'}
    & $adb -s $Serial shell am force-stop com.spidertracker.app
    & $adb -s $Serial shell am start -W -n com.spidertracker.app/.MainActivity --ez capture true --es screen $screen | Out-Null
    if($LASTEXITCODE -ne 0){throw "Launch failed: $screen"}
    Start-Sleep -Seconds 4
    & $adb -s $Serial shell screencap -p /sdcard/tracker-capture.png
    $destination=Join-Path $root "docs\screenshots\$Viewport-$screen-$Suffix.png"
    & $adb -s $Serial pull /sdcard/tracker-capture.png $destination | Out-Null
    if($LASTEXITCODE -ne 0 -or !(Test-Path $destination)){throw "Capture failed: $screen"}
    @{serial=$Serial;avd=$avd;api=[int]$api;physicalPixels=$sizes[$Viewport];densityDpi=320;fontScale=((& $adb -s $Serial shell settings get system font_scale) | Select-Object -First 1).Trim();state=$screen;capturedAtUtc=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath "$destination.json" -Encoding utf8
    Write-Output $destination
}
