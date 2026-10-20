[CmdletBinding()]
param([string]$WorkspaceId, [string]$ApiKeyFile, [switch]$CreateAnotherDraft)
$ErrorActionPreference='Stop'
$tallyRoot=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$tallyBodyPath=Join-Path $tallyRoot 'data/tally/hamigo-feedback.create.json'
$tallyResultPath=Join-Path $tallyRoot 'output/tally/created-form.json'
if((Test-Path -LiteralPath $tallyResultPath) -and -not $CreateAnotherDraft){
    $existing=Get-Content -LiteralPath $tallyResultPath -Raw -Encoding utf8 | ConvertFrom-Json
    if($existing.id){
        Write-Output "Brouillon existant : https://tally.so/forms/$($existing.id)/edit"
        return
    }
    if($existing.creationState -ne 'REJECTED'){
        throw 'Une creation precedente est incertaine. Verifier le tableau de bord Tally avant une nouvelle tentative volontaire avec -CreateAnotherDraft.'
    }
}
$tallyBody=Get-Content -LiteralPath $tallyBodyPath -Raw -Encoding utf8 | ConvertFrom-Json
if($tallyBody.status -ne 'DRAFT'){throw 'Le modele doit creer un brouillon.'}
if($WorkspaceId){$tallyBody | Add-Member -NotePropertyName workspaceId -NotePropertyValue $WorkspaceId -Force}
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
try{
    $tallyHeaders=@{Authorization='Bearer '+[Runtime.InteropServices.Marshal]::PtrToStringBSTR($tallyPointer)}
    New-Item -ItemType Directory -Path (Split-Path $tallyResultPath) -Force | Out-Null
    [pscustomobject]@{creationState='REQUEST_IN_PROGRESS';startedAt=(Get-Date).ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath $tallyResultPath -Encoding utf8
    try{
        $tallyResponse=Invoke-RestMethod -Method Post -Uri 'https://api.tally.so/forms' -MaximumRedirection 0 -TimeoutSec 45 -Headers $tallyHeaders -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes(($tallyBody | ConvertTo-Json -Depth 80)))
    }catch{
        $tallyCode=0
        if($_.Exception.Response){$tallyCode=[int]$_.Exception.Response.StatusCode}
        $tallyState=if($tallyCode -ge 400 -and $tallyCode -lt 500){'REJECTED'}else{'UNCERTAIN'}
        [pscustomobject]@{creationState=$tallyState;httpStatus=$tallyCode;updatedAt=(Get-Date).ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath $tallyResultPath -Encoding utf8
        if($tallyState -eq 'REJECTED'){throw "Tally a refuse la creation (HTTP $tallyCode). Verifier la cle et le modele. Aucune cle n'a ete affichee ni sauvegardee."}
        throw 'Reponse Tally incertaine. Verifier le tableau de bord avant de retenter : le brouillon peut avoir ete cree. Aucune cle affichee ni sauvegardee.'
    }
    if(-not $tallyResponse.id -or $tallyResponse.id -notmatch '^[A-Za-z0-9_-]+$'){throw 'Tally ne retourne pas de formId valide; verifier le tableau de bord avant toute nouvelle tentative.'}
    [pscustomobject]@{creationState='CREATED';id=$tallyResponse.id;status=$tallyResponse.status;createdAt=$tallyResponse.createdAt;builderUrl="https://tally.so/forms/$($tallyResponse.id)/edit"} | ConvertTo-Json | Set-Content -LiteralPath $tallyResultPath -Encoding utf8
    Write-Output "Brouillon cree : https://tally.so/forms/$($tallyResponse.id)/edit"
    try{
        $tallyReadback=Invoke-RestMethod -Method Get -Uri "https://api.tally.so/forms/$($tallyResponse.id)" -MaximumRedirection 0 -TimeoutSec 45 -Headers $tallyHeaders
        [pscustomobject]@{id=$tallyReadback.id;name=$tallyReadback.name;status=$tallyReadback.status;settings=$tallyReadback.settings;blocks=$tallyReadback.blocks} | ConvertTo-Json -Depth 80 | Set-Content -LiteralPath (Join-Path $tallyRoot 'output/tally/created-form.readback.json') -Encoding utf8
        Write-Output "Relecture API : statut $($tallyReadback.status), $(@($tallyReadback.blocks).Count) blocs."
    }catch{
        Write-Warning 'Brouillon cree, mais relecture API indisponible. Son identifiant est conserve; ne pas recreer le formulaire.'
    }
    Write-Output 'Verifier les branches en apercu, puis publier depuis Tally pour obtenir le lien a brancher dans Hamigo.'
}finally{
    if($tallyHeaders){$tallyHeaders.Clear()}
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($tallyPointer)
    $tallySecret.Dispose()
}
