[CmdletBinding()]
param([string]$ApiKeyFile,[string]$FormId='9NqlpK',[switch]$Publish,[switch]$SnapshotOnly)
$ErrorActionPreference='Stop'
if($FormId -ne '9NqlpK'){throw 'Ce script met à jour uniquement le formulaire Hamigo existant.'}
$tallyRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$tallyOutput=Join-Path $tallyRoot 'output/tally-retours-22'
New-Item -ItemType Directory -Path $tallyOutput -Force | Out-Null
if(-not $ApiKeyFile){$ApiKeyFile=Join-Path $tallyRoot 'tools/.env'}
if(Test-Path -LiteralPath $ApiKeyFile){
    $tallyKeyLines=@(Get-Content -LiteralPath $ApiKeyFile -Encoding utf8 | Where-Object {$_ -match '^\s*(?:export\s+)?TALLY_API_KEY\s*='})
    if($tallyKeyLines.Count -ne 1){throw 'Le fichier doit definir une seule variable TALLY_API_KEY.'}
    $tallyKeyValue=($tallyKeyLines[0] -replace '^\s*(?:export\s+)?TALLY_API_KEY\s*=\s*','').Trim()
    if($tallyKeyValue.StartsWith('"')){
        if($tallyKeyValue -notmatch '^"([^"\r\n]*)"\s*(?:#.*)?$'){throw 'Valeur TALLY_API_KEY entre guillemets non valide.'}
        $tallyKeyValue=$Matches[1]
    }elseif($tallyKeyValue.StartsWith("'")){
        if($tallyKeyValue -notmatch "^'([^'\r\n]*)'\s*(?:#.*)?$"){throw 'Valeur TALLY_API_KEY entre apostrophes non valide.'}
        $tallyKeyValue=$Matches[1]
    }else{$tallyKeyValue=($tallyKeyValue -split '\s+#',2)[0].Trim()}
    if([string]::IsNullOrWhiteSpace($tallyKeyValue) -or $tallyKeyValue -match '\s'){throw 'TALLY_API_KEY est vide ou contient un espace.'}
    $tallySecret=ConvertTo-SecureString -String $tallyKeyValue -AsPlainText -Force
    $tallyKeyLines=$null;$tallyKeyValue=$null
}else{
    $tallySecret=Read-Host 'Cle API Tally (saisie masquee, jamais enregistree)' -AsSecureString
}

$tallyPointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($tallySecret)
try {
 $tallyHeaders=@{Authorization='Bearer '+[Runtime.InteropServices.Marshal]::PtrToStringBSTR($tallyPointer)}
 try {
  $tallyBefore=Invoke-RestMethod -Method Get -Uri "https://api.tally.so/forms/$FormId" -Headers $tallyHeaders -TimeoutSec 45
  if($tallyBefore.id -ne $FormId){throw 'Unexpected form ID'}
  $tallySnapshot=[ordered]@{id=$tallyBefore.id;name=$tallyBefore.name;status=$tallyBefore.status;settings=$tallyBefore.settings;blocks=$tallyBefore.blocks}
  $tallySnapshotPath=Join-Path $tallyOutput 'before-form.json'
  if(-not(Test-Path -LiteralPath $tallySnapshotPath)){$tallySnapshot | ConvertTo-Json -Depth 90 | Set-Content -LiteralPath $tallySnapshotPath -Encoding utf8}
  $tallyCurrentPath=Join-Path $tallyOutput 'latest-before-form.json'
  $tallySnapshot | ConvertTo-Json -Depth 90 | Set-Content -LiteralPath $tallyCurrentPath -Encoding utf8
  if($SnapshotOnly){
   Write-Output "Snapshot Hamigo conservé : statut $($tallyBefore.status), $(@($tallyBefore.blocks).Count) blocs."
   return
  }
  if(-not $Publish){throw 'Employer -SnapshotOnly pour la lecture ou -Publish pour appliquer la configuration relue.'}
  $tallyTemplate=Get-Content -LiteralPath (Join-Path $tallyRoot 'data/tally/hamigo-feedback.create.json') -Raw -Encoding utf8 | ConvertFrom-Json
  $tallySettings=$tallyBefore.settings
  foreach($tallyProperty in $tallyTemplate.settings.PSObject.Properties){$tallySettings | Add-Member -NotePropertyName $tallyProperty.Name -NotePropertyValue $tallyProperty.Value -Force}
  $tallyPatch=[ordered]@{status='PUBLISHED';blocks=$tallyTemplate.blocks;settings=$tallySettings}
  $tallyPatch | ConvertTo-Json -Depth 90 | Set-Content -LiteralPath (Join-Path $tallyOutput 'published-request.json') -Encoding utf8
  $null=Invoke-RestMethod -Method Patch -Uri "https://api.tally.so/forms/$FormId" -Headers $tallyHeaders -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes(($tallyPatch | ConvertTo-Json -Depth 90))) -TimeoutSec 45
  $tallyAfter=Invoke-RestMethod -Method Get -Uri "https://api.tally.so/forms/$FormId" -Headers $tallyHeaders -TimeoutSec 45
  [ordered]@{id=$tallyAfter.id;name=$tallyAfter.name;status=$tallyAfter.status;settings=$tallyAfter.settings;blocks=$tallyAfter.blocks} | ConvertTo-Json -Depth 90 | Set-Content -LiteralPath (Join-Path $tallyOutput 'published-form.readback.json') -Encoding utf8
  if($tallyAfter.status -ne 'PUBLISHED' -or @($tallyAfter.blocks).Count -ne @($tallyTemplate.blocks).Count){throw 'Readback mismatch'}
  foreach($tallyProperty in $tallyTemplate.settings.PSObject.Properties){if($tallyAfter.settings.($tallyProperty.Name) -ne $tallyProperty.Value){throw 'Settings readback mismatch'}}
  [ordered]@{id=$tallyAfter.id;status=$tallyAfter.status;publicUrl="https://tally.so/r/$FormId";updatedAt=(Get-Date).ToString('o');blocks=@($tallyAfter.blocks).Count;ownerEmailNotificationsEnabled=$tallyAfter.settings.hasSelfEmailNotifications;restoreOldFormProgressEnabled=$tallyAfter.settings.saveForLater;realSubmissionSent=$false;testEmailSent=$false} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $tallyOutput 'published-form.json') -Encoding utf8
  Write-Output "Formulaire Hamigo mis à jour et relu : $(@($tallyAfter.blocks).Count) blocs, notifications propriétaire $($tallyAfter.settings.hasSelfEmailNotifications)."
 }catch {
  if($_.ErrorDetails.Message){$tallyErrorBody=$_.ErrorDetails.Message.Replace($tallyHeaders.Authorization,'[redacted]');$tallyErrorBody | Set-Content -LiteralPath (Join-Path $tallyOutput 'api-validation-error.txt') -Encoding utf8}
  $tallyHttpStatus=0
  if($_.Exception.Response){$tallyHttpStatus=[int]$_.Exception.Response.StatusCode}
  throw "Mise à jour Tally non confirmée (HTTP $tallyHttpStatus). Consulter la relecture et le tableau de bord avant de réessayer. Aucun secret affiché."
 }
}finally {
 if($tallyHeaders){$tallyHeaders.Clear()}
 [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($tallyPointer)
 $tallySecret.Dispose()
}
