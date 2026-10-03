param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$signingDir=Join-Path $repoRoot '.tools'
$keyFile=Join-Path $signingDir 'hamigo-release.jks'
$propsFile=Join-Path $signingDir 'signing.properties'
if(Test-Path -LiteralPath $keyFile) {
    if(!(Test-Path -LiteralPath $propsFile)){throw 'La clé existe mais ses propriétés sont absentes. Restaurer les propriétés originales.'}
    Write-Output 'Clé locale existante conservée.'
    exit 0
}
New-Item -ItemType Directory -Force -Path $signingDir | Out-Null
$bytes=New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$password=[Convert]::ToBase64String($bytes)
$keytool=Join-Path $JavaHome 'bin\keytool.exe'
if(!(Test-Path -LiteralPath $keytool)){throw 'Configurer JAVA_HOME vers un JDK (17 ou plus).'}
& $keytool -genkeypair -keystore $keyFile -storetype JKS -alias hamigo -keyalg RSA -keysize 3072 -validity 10000 -storepass $password -keypass $password -dname 'CN=Hamigo, OU=Personal Android, O=Alexandre Malfreyt, C=FR' -noprompt
if($LASTEXITCODE -ne 0){throw 'La création de la clé de signature a échoué.'}
[IO.File]::WriteAllText($propsFile,"storePassword=$password`nkeyPassword=$password`n",[Text.UTF8Encoding]::new($false))
Write-Output 'Signature locale prête dans .tools (exclue de Git).'
