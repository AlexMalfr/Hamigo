param([string]$Serial='emulator-5554', [string]$RawDirectory='.tools/exam1-sources')
$ErrorActionPreference='Stop'
$examWorkspace=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$examAdb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$examHardware=(& $examAdb -s $Serial shell getprop ro.hardware).Trim()
if($examHardware -notin @('ranchu','goldfish')){throw 'This fixture is reserved for an emulator.'}
$examRaw=[IO.Path]::GetFullPath((Join-Path $examWorkspace $RawDirectory))
if(-not $examRaw.StartsWith($examWorkspace+[IO.Path]::DirectorySeparatorChar)){throw 'Fixture source outside workspace'}
$examJson=Join-Path $examRaw 'questions.raw.json';$examZip=Join-Path $examRaw 'questions.zip'
if(-not (Test-Path -LiteralPath $examJson)-or -not (Test-Path -LiteralPath $examZip)){throw 'Run tools/import_exam1.ps1 -Download first.'}
$examJsonHash=(Get-FileHash -LiteralPath $examJson -Algorithm SHA256).Hash.ToLowerInvariant()
$examZipHash=(Get-FileHash -LiteralPath $examZip -Algorithm SHA256).Hash.ToLowerInvariant()
$examSha=[Security.Cryptography.SHA256]::Create()
try {$examGeneration=[Convert]::ToHexString($examSha.ComputeHash([Text.Encoding]::UTF8.GetBytes($examJsonHash+$examZipHash))).ToLowerInvariant()}finally{$examSha.Dispose()}
$examMetadata=@{generation=$examGeneration;version=(Get-Content -LiteralPath $examJson -Raw|ConvertFrom-Json).version;jsonHash=$examJsonHash;zipHash=$examZipHash;zipBytes=(Get-Item -LiteralPath $examZip).Length;checkedAt=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();zipVerifiedAt=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()}
$examTemp=Join-Path $examWorkspace 'output/exam1-test-current.json'
[IO.File]::WriteAllText($examTemp,($examMetadata|ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false))
$examPackage='com.malfreyt.alexandre.hamigo'
$examExternal="/sdcard/Android/data/$examPackage/files/exam1-fixture"
& $examAdb -s $Serial shell mkdir -p $examExternal
& $examAdb -s $Serial push $examJson "$examExternal/questions.json"
& $examAdb -s $Serial push $examZip "$examExternal/images.zip"
& $examAdb -s $Serial push $examTemp "$examExternal/current.json"
& $examAdb -s $Serial shell run-as $examPackage mkdir -p "no_backup/exam-bank/$examGeneration"
foreach($examCopy in @(@('questions.json',"no_backup/exam-bank/$examGeneration/questions.json"),@('images.zip',"no_backup/exam-bank/$examGeneration/images.zip"),@('current.json','no_backup/exam-bank/current.json'))){
    # Android's run-as mount namespace cannot read files pushed to emulated shared storage.
    # Stream public fixture bytes from the shell into the app-owned destination instead.
    & $examAdb -s $Serial shell "cat $examExternal/$($examCopy[0]) | run-as $examPackage tee $($examCopy[1]) > /dev/null"
    if($LASTEXITCODE -ne 0){throw 'Failed to copy emulator bank fixture'}
}
if($LASTEXITCODE -ne 0){throw 'Failed to prepare isolated emulator bank'}
Write-Output 'Official bank fixture prepared in emulator app storage; no bank included in APK or repository.'
