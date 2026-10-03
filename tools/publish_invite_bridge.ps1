$ErrorActionPreference = 'Stop'
$repo = 'AlexMalfr/AlexMalfr.github.io'
$bridgeRoot = Join-Path $PSScriptRoot '..\web\invite'
$entries = @(
    @{Remote='index.html'; Content='<meta http-equiv="refresh" content="0;url=/hamigo/"><a href="/hamigo/">Hamigo</a>'},
    @{Remote='hamigo/index.html'; File='index.html'},
    @{Remote='hamigo/oauth/index.html'; Content='<meta charset="utf-8"><p>Retourne dans Hamigo pour terminer ta connexion GitHub.</p><a href="/hamigo/">Hamigo</a>'},
    @{Remote='.well-known/assetlinks.json'; File='assetlinks.json'},
    @{Remote='.nojekyll'; Content=''}
)
foreach ($entry in $entries) {
    $bodyText = if ($entry.ContainsKey('File')) { [IO.File]::ReadAllText((Join-Path $bridgeRoot $entry.File)) } else { $entry.Content }
    $payload = @{message='Publish Hamigo invitation bridge'; branch='main'; content=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($bodyText))}
    $ErrorActionPreference = 'Continue'
    $sha = & gh api "repos/$repo/contents/$($entry.Remote)" --jq '.sha' 2>$null
    $lookupExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($lookupExit -eq 0 -and $sha) { $payload.sha = $sha }
    $inputPath = Join-Path $env:TEMP 'hamigo-pages-payload.json'
    [IO.File]::WriteAllText($inputPath,($payload | ConvertTo-Json -Compress),[Text.UTF8Encoding]::new($false))
    & gh api "repos/$repo/contents/$($entry.Remote)" -X PUT --input $inputPath --jq '.content.path'
    if ($LASTEXITCODE -ne 0) { throw 'GitHub Pages file publication failed.' }
}
& gh api "repos/$repo/pages" -X POST -f 'source[branch]=main' -f 'source[path]=/' --jq '.html_url'
if ($LASTEXITCODE -ne 0) { & gh api "repos/$repo/pages" --jq '.html_url' }
