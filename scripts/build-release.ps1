<#
.SYNOPSIS
    Arma el APK de release firmado, listo para subir a la consola del MDM.

.DESCRIPTION
    Corre `gradlew assembleRelease` y muestra dónde quedó el APK, su tamaño,
    versionCode/versionName y si quedó firmado (si falta keystore.properties,
    Gradle avisa y el APK sale sin firmar — no instalable por el MDM).

.EXAMPLE
    .\scripts\build-release.ps1
    .\scripts\build-release.ps1 -ApiBaseUrl http://192.168.10.5:8082/
#>
[CmdletBinding()]
param(
    [string]$ApiBaseUrl
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot

Push-Location $RepoRoot
try {
    $gradleArgs = @('assembleRelease', '--console=plain')
    if ($ApiBaseUrl) { $gradleArgs += "-PapiBaseUrl=$ApiBaseUrl" }
    # El release bloquea HTTP sin cifrar salvo que se pida explícitamente: un
    # backend en http:// (LAN cerrada, sin certificado) necesita esta excepción.
    if (-not $ApiBaseUrl -or $ApiBaseUrl.StartsWith('http://')) {
        $gradleArgs += '-PallowCleartext=true'
        Write-Host "AVISO: la URL del backend es http:// -> se permite tráfico sin cifrar en este APK. Para producción usá https://." -ForegroundColor Yellow
    }

    Write-Host "Compilando release..." -ForegroundColor Cyan
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Falló assembleRelease." }

    $apk = Get-ChildItem "app\build\outputs\apk\release\*.apk" | Select-Object -First 1
    if (-not $apk) { throw "No se encontró el APK de salida." }

    $signed = $apk.Name -notlike "*unsigned*"
    Write-Host ""
    Write-Host "APK: $($apk.FullName)" -ForegroundColor Green
    Write-Host "Tamaño: $([math]::Round($apk.Length / 1MB, 1)) MB"

    if ($signed) {
        $adb = "$env:ANDROID_HOME\build-tools"
        $buildTools = Get-ChildItem $adb -Directory | Sort-Object Name -Descending | Select-Object -First 1
        $apksigner = Join-Path $buildTools.FullName "apksigner.bat"
        if (Test-Path $apksigner) {
            Write-Host ""
            Write-Host "Firma:" -ForegroundColor Cyan
            & $apksigner verify --print-certs $apk.FullName
        }
        Write-Host ""
        Write-Host "Firmado y listo para distribuir por el MDM." -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "SIN FIRMAR: no lo va a poder instalar ningún MDM." -ForegroundColor Red
        Write-Host "Generá keystore.properties (ver docs/RELEASE.md) y volvé a correr este script." -ForegroundColor Yellow
    }
} finally {
    Pop-Location
}
