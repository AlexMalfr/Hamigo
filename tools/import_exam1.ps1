param([switch]$Download)
$ErrorActionPreference = 'Stop'
$examRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$examSources = Join-Path $examRoot '.tools/exam1-sources'
$examImages = Join-Path $examSources 'images'
New-Item -ItemType Directory -Force -Path $examSources,$examImages | Out-Null
if ($Download) {
    foreach ($examDownload in @(
        @{Remote='https://exam1.r-e-f.org/assets/questions.json'; Local='questions.raw.json'},
        @{Remote='https://exam1.r-e-f.org/assets/questions.zip'; Local='questions.zip'},
        @{Remote='https://exam1.r-e-f.org/assets/series.json'; Local='series.raw.json'},
        @{Remote='https://exam1.r-e-f.org/assets/contributeurs.json'; Local='contributeurs.raw.json'}
    )) {
        Invoke-WebRequest -Uri $examDownload.Remote -OutFile (Join-Path $examSources $examDownload.Local)
    }
    @{ downloadedOn = [DateTime]::UtcNow.ToString('yyyy-MM-dd'); downloadedAtUtc = [DateTime]::UtcNow.ToString('o'); source = 'https://exam1.r-e-f.org/' } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $examSources 'download.json') -Encoding utf8
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$examArchive = [IO.Compression.ZipFile]::OpenRead((Join-Path $examSources 'questions.zip'))
try {
    foreach ($examEntry in $examArchive.Entries) {
        # The source archive uses flat numeric PNG names. Reject unexpected paths.
        if ($examEntry.FullName -notmatch '^\d+\.png$') { throw "Unexpected archive entry: $($examEntry.FullName)" }
        $examTarget = [IO.Path]::GetFullPath((Join-Path $examImages $examEntry.FullName))
        if (-not $examTarget.StartsWith([IO.Path]::GetFullPath($examImages) + [IO.Path]::DirectorySeparatorChar)) { throw 'Archive target outside image directory' }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($examEntry, $examTarget, $true)
    }
} finally { $examArchive.Dispose() }
& node --preserve-symlinks-main (Join-Path $PSScriptRoot 'import_exam1.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Exam1 normalization failed' }
