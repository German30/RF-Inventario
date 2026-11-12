<#
.SYNOPSIS
    Despliegue inalámbrico de RF Inventario por ADB Wi-Fi (Android 11+).

.DESCRIPTION
    Envuelve `adb pair` / `adb connect` y el `installDebug` de Gradle para
    instalar y lanzar la app en un dispositivo por red, sin cable.

    Requisitos en el teléfono (una sola vez):
      1. Ajustes > Información del teléfono > pulsar "Número de compilación" 7 veces.
      2. Opciones de desarrollo > "Depuración inalámbrica" = ON.
      3. Teléfono y PC en la MISMA red Wi-Fi (aquí la PC está en 192.168.1.x).

.EXAMPLE
    # 1) Emparejar (solo la primera vez por red). En el teléfono:
    #    Depuración inalámbrica > "Vincular dispositivo con código de vinculación"
    #    -> muestra  IP:PUERTO  y un código de 6 dígitos.
    .\scripts\wifi-deploy.ps1 -Pair 192.168.1.50:37123 -Code 482913

.EXAMPLE
    # 2) Conectar + compilar + instalar + abrir. La IP:PUERTO grande está en la
    #    pantalla principal de "Depuración inalámbrica".
    .\scripts\wifi-deploy.ps1 -Connect 192.168.1.50:41285 -Install

.EXAMPLE
    # Siguientes despliegues (reusa la última dirección):
    .\scripts\wifi-deploy.ps1 -Install
    .\scripts\wifi-deploy.ps1 -Install -Logcat

.EXAMPLE
    # Solo compilar el APK apuntando a la IP LAN de esta PC (lo usa el launch
    # "dispositivo Wi-Fi" de VS Code). -ApiBaseUrl fuerza otra URL.
    .\scripts\wifi-deploy.ps1 -Build
    .\scripts\wifi-deploy.ps1 -Install -ApiBaseUrl http://192.168.1.230:8082/
#>
[CmdletBinding()]
param(
    [string]$Pair,
    [string]$Code,
    [string]$Connect,
    [switch]$Install,
    [switch]$Build,
    [string]$ApiBaseUrl,
    [switch]$Logcat,
    [switch]$Disconnect
)

$ErrorActionPreference = 'Stop'
$AppId = 'com.rf.inventario'
$Activity = "$AppId/$AppId.MainActivity"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$TargetFile = Join-Path $PSScriptRoot '.wifi-target'

function Resolve-Adb {
    foreach ($c in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($c -and (Test-Path "$c\platform-tools\adb.exe")) { return "$c\platform-tools\adb.exe" }
    }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "No se encontró adb. Define ANDROID_HOME o agrega platform-tools al PATH."
}

# El default de Gradle (10.0.2.2) solo existe dentro del emulador; en un
# teléfono real hay que compilar con la IP LAN de la PC donde corre el backend.
function Resolve-ApiBaseUrl {
    if ($ApiBaseUrl) { return $ApiBaseUrl }
    $ip = Get-NetIPConfiguration |
        Where-Object { $_.IPv4DefaultGateway -and $_.NetAdapter.Status -eq 'Up' } |
        Select-Object -First 1 -ExpandProperty IPv4Address |
        Select-Object -First 1 -ExpandProperty IPAddress
    if (-not $ip) { throw "No se pudo detectar la IP LAN de la PC. Pasa -ApiBaseUrl http://<IP>:8082/" }
    return "http://${ip}:8082/"
}

function Invoke-Gradle([string]$Task) {
    $url = Resolve-ApiBaseUrl
    Write-Host "Backend: $url" -ForegroundColor Cyan
    Push-Location $RepoRoot
    try {
        & (Join-Path $RepoRoot 'gradlew.bat') $Task "-PapiBaseUrl=$url" --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Falló $Task." }
    } finally {
        Pop-Location
    }
}

if ($Build) {
    Invoke-Gradle 'assembleDebug'
    return
}

$Adb = Resolve-Adb
Write-Host "adb: $Adb" -ForegroundColor DarkGray

if ($Disconnect) {
    & $Adb disconnect
    Remove-Item $TargetFile -ErrorAction SilentlyContinue
    Write-Host "Desconectado." -ForegroundColor Yellow
    return
}

if ($Pair) {
    if (-not $Code) { throw "Falta -Code (el código de 6 dígitos que muestra el teléfono)." }
    Write-Host "Emparejando con $Pair ..." -ForegroundColor Cyan
    & $Adb pair $Pair $Code
    if ($LASTEXITCODE -ne 0) { throw "Falló el emparejamiento." }
    Write-Host "Emparejado. Ahora usa -Connect con la IP:PUERTO de la pantalla principal." -ForegroundColor Green
}

if ($Connect) {
    Write-Host "Conectando a $Connect ..." -ForegroundColor Cyan
    & $Adb connect $Connect
    if ($LASTEXITCODE -ne 0) { throw "Falló la conexión." }
    Set-Content -Path $TargetFile -Value $Connect -Encoding ascii
}

# Dirección objetivo: la de este comando o la última guardada.
$Target = $Connect
if (-not $Target -and (Test-Path $TargetFile)) { $Target = (Get-Content $TargetFile -Raw).Trim() }

if ($Install) {
    if (-not $Target) { throw "No hay dispositivo. Usa -Connect <ip:puerto> al menos una vez." }

    # Reconecta por si el ADB se reinició entre despliegues.
    & $Adb connect $Target | Out-Null
    $online = (& $Adb devices) -match [regex]::Escape($Target) -match 'device$'
    if (-not $online) {
        & $Adb devices
        throw "El dispositivo $Target no está 'device'. Revisa la depuración inalámbrica en el teléfono."
    }

    Write-Host "Compilando e instalando (installDebug) ..." -ForegroundColor Cyan
    Invoke-Gradle 'installDebug'

    Write-Host "Lanzando $Activity ..." -ForegroundColor Cyan
    & $Adb -s $Target shell am start -n $Activity | Out-Null
    Write-Host "OK: app instalada y abierta en $Target." -ForegroundColor Green
}

if ($Logcat) {
    if (-not $Target) { throw "No hay dispositivo para logcat." }
    $pidText = (& $Adb -s $Target shell pidof $AppId 2>$null | Out-String).Trim()
    if ($pidText) {
        Write-Host "logcat (pid $pidText). Ctrl+C para salir." -ForegroundColor DarkGray
        & $Adb -s $Target logcat --buffer=main --pid=$pidText
    } else {
        Write-Host "App no corriendo; logcat sin filtro de pid. Ctrl+C para salir." -ForegroundColor DarkGray
        & $Adb -s $Target logcat --buffer=main
    }
    return
}

if (-not ($Pair -or $Connect -or $Install -or $Build)) {
    Write-Host ""
    Write-Host "Estado actual:" -ForegroundColor Cyan
    & $Adb devices -l
    Write-Host ""
    Write-Host "Uso: .\scripts\wifi-deploy.ps1 -Pair <ip:puerto> -Code <123456>" -ForegroundColor Gray
    Write-Host "     .\scripts\wifi-deploy.ps1 -Connect <ip:puerto> -Install" -ForegroundColor Gray
    Write-Host "     .\scripts\wifi-deploy.ps1 -Install [-Logcat]" -ForegroundColor Gray
    Write-Host "     .\scripts\wifi-deploy.ps1 -Disconnect" -ForegroundColor Gray
}
