param(
    [string]$Serial = 'emulator-5554',
    [string]$Label = 'current',
    [string]$Output = 'output/widgets-0.34',
    [double]$FontScale = 1.0,
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$VisualOnly
)
$ErrorActionPreference = 'Stop'
$taskAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$taskBuildRoot = '.tools/build-navigation-0.13'
New-Item -ItemType Directory -Path $Output -Force | Out-Null
if (-not $SkipBuild) {
    & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest "-PhamigoBuildRoot=$taskBuildRoot" *> "$Output/build-$Label.txt"
    if ($LASTEXITCODE -ne 0) { throw 'Widget audit compilation failed.' }
}
if (-not $SkipInstall) {
    & $taskAdb -s $Serial install -r "$taskBuildRoot/app/outputs/apk/debug/app-debug.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Debug APK installation failed.' }
    & $taskAdb -s $Serial install -r "$taskBuildRoot/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    if ($LASTEXITCODE -ne 0) { throw 'Widget audit APK installation failed.' }
}
$taskInitialFont = (& $taskAdb -s $Serial shell settings get system font_scale).Trim()
try {
    & $taskAdb -s $Serial shell settings put system font_scale $FontScale.ToString([System.Globalization.CultureInfo]::InvariantCulture)
    $taskTestClass = 'com.malfreyt.alexandre.hamigo.platform.WidgetAtlasInstrumentedTest'
    if ($VisualOnly) { $taskTestClass += '#renderEveryWidgetAcrossTheSizeAtlas' }
    & $taskAdb -s $Serial shell am instrument -w -e atlasLabel $Label -e class $taskTestClass com.malfreyt.alexandre.hamigo.test/androidx.test.runner.AndroidJUnitRunner *> "$Output/android-$Label-font$FontScale.txt"
    Get-Content -LiteralPath "$Output/android-$Label-font$FontScale.txt" -Tail 35
    New-Item -ItemType Directory -Path "$Output/atlas" -Force | Out-Null
    $taskFontSuffix = $FontScale.ToString('0.0', [System.Globalization.CultureInfo]::InvariantCulture)
    & $taskAdb -s $Serial pull "/sdcard/Android/data/com.malfreyt.alexandre.hamigo/files/widgets-atlas/$Label-font$taskFontSuffix" "$Output/atlas"
} finally {
    if ($taskInitialFont -eq 'null') { & $taskAdb -s $Serial shell settings delete system font_scale }
    else { & $taskAdb -s $Serial shell settings put system font_scale $taskInitialFont }
}
if ((Get-Content -LiteralPath "$Output/android-$Label-font$FontScale.txt" -Raw) -notmatch 'OK \([0-9]+ tests?\)') {
    throw 'Native widget audit failed; images and diagnostic report were preserved.'
}
