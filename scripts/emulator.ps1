<#
.SYNOPSIS
    Arranca un emulador de Android a mano (sin abrir Android Studio) y,
    opcionalmente, instala y lanza RF Inventario en él.

.EXAMPLE
    .\scripts\emulator.ps1 -List

.EXAMPLE
    # arranca el AVD y espera a que termine de bootear
    .\scripts\emulator.ps1 -Avd Medium_Phone_API_36

.EXAMPLE
    # arranque en frío + instala + abre la app SOLO en el emulador
    .\scripts\emulator.ps1 -Avd Medium_Phone_API_36 -ColdBoot -Install

.EXAMPLE
    .\scripts\emulator.ps1 -Install            # usa el primer AVD disponible
    .\scripts\emulator.ps1 -Stop
#>
[CmdletBinding()]
param(
    [string]$Avd,
    [int]$Port = 5554,
    [switch]$List,
    [switch]$ColdBoot,
    [switch]$Wipe,
    [switch]$NoWindow,
    [switch]$Install,
    [switch]$Stop,
    [switch]$IfNoDevice
)

$ErrorActionPreference = 'Stop'
$AppId = 'com.rf.inventario'
$Activity = "$AppId/$AppId.MainActivity"
$RepoRoot = Split-Path -Parent $PSScriptRoot

function Resolve-SdkTool([string]$relative) {
    foreach ($c in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)) {
        if ($c -and (Test-Path "$c\$relative")) { return "$c\$relative" }
    }
    throw "No se encontró $relative. Define ANDROID_HOME."
}

$Emu = Resolve-SdkTool 'emulator\emulator.exe'
$Adb = Resolve-SdkTool 'platform-tools\adb.exe'
$Serial = "emulator-$Port"

# adb escribe a stderr y sale != 0 cuando un serial no existe; bajo
# $ErrorActionPreference='Stop' eso se convierte en excepción. Este wrapper
# aísla esas llamadas de sondeo.
function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)] $AdbArgs)
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $out = & $Adb @AdbArgs 2>$null } catch { $out = @() }
    $ErrorActionPreference = $old
    return $out
}

function Test-DeviceOnline([string]$serial) {
    foreach ($line in (Invoke-Adb devices)) {
        $t = "$line" -split '\s+'
        if ($t.Count -ge 2 -and $t[0] -eq $serial -and $t[1] -eq 'device') { return $true }
    }
    return $false
}

function Test-AnyDeviceOnline {
    foreach ($line in (Invoke-Adb devices)) {
        $t = "$line" -split '\s+'
        if ($t.Count -ge 2 -and $t[1] -eq 'device') { return $true }
    }
    return $false
}

if ($List) {
    Write-Host "AVDs disponibles:" -ForegroundColor Cyan
    & $Emu -list-avds
    Write-Host "`nEmuladores corriendo:" -ForegroundColor Cyan
    (Invoke-Adb devices) | Select-String '^emulator-'
    return
}

if ($Stop) {
    Invoke-Adb -s $Serial emu kill | Out-Null
    Write-Host "Enviada orden de apagado a $Serial." -ForegroundColor Yellow
    return
}

# Usado como preLaunchTask: si ya hay un dispositivo/emulador conectado, no hace nada.
if ($IfNoDevice) {
    if (Test-AnyDeviceOnline) {
        Write-Host "Ya hay un dispositivo conectado; no arranco el emulador." -ForegroundColor DarkGray
        return
    }
    Write-Host "Sin dispositivos; arranco el emulador." -ForegroundColor Cyan
}

# Elegir AVD si no se pasó uno.
if (-not $Avd) {
    $Avd = (& $Emu -list-avds | Select-Object -First 1)
    if (-not $Avd) { throw "No hay AVDs. Creá uno con avdmanager o Android Studio." }
    Write-Host "Sin -Avd; uso el primero: $Avd" -ForegroundColor DarkGray
}

# ¿Ya está corriendo en ese puerto?
if (-not (Test-DeviceOnline $Serial)) {
    $emuArgs = @('-avd', $Avd, '-port', $Port, '-gpu', 'host', '-no-boot-anim',
                 '-netdelay', 'none', '-netspeed', 'full')
    if ($ColdBoot)  { $emuArgs += '-no-snapshot-load' }
    if ($Wipe)      { $emuArgs += '-wipe-data' }
    if ($NoWindow)  { $emuArgs += @('-no-window', '-no-audio') }

    Write-Host "Arrancando $Avd en $Serial ..." -ForegroundColor Cyan
    Start-Process -FilePath $Emu -ArgumentList $emuArgs -WindowStyle Minimized

    Invoke-Adb -s $Serial wait-for-device | Out-Null
    Write-Host "Esperando boot completo" -NoNewline
    do {
        Start-Sleep -Seconds 2
        Write-Host "." -NoNewline
        $done = (Invoke-Adb -s $Serial shell getprop sys.boot_completed | Out-String).Trim()
    } while ($done -ne '1')
    Write-Host " listo." -ForegroundColor Green
    Invoke-Adb -s $Serial shell input keyevent 82 | Out-Null   # desbloquea la pantalla
} else {
    Write-Host "$Serial ya estaba corriendo; no arranco otro." -ForegroundColor DarkGray
}

if ($Install) {
    Write-Host "installDebug -> $Serial ..." -ForegroundColor Cyan
    $prev = $env:ANDROID_SERIAL
    $env:ANDROID_SERIAL = $Serial          # acota gradle/adb a este emulador
    Push-Location $RepoRoot
    try {
        & (Join-Path $RepoRoot 'gradlew.bat') installDebug --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Falló installDebug." }
    } finally {
        Pop-Location
        if ($null -eq $prev) { Remove-Item Env:\ANDROID_SERIAL } else { $env:ANDROID_SERIAL = $prev }
    }
    Invoke-Adb -s $Serial shell am start -n $Activity | Out-Null
    Write-Host "OK: app instalada y abierta en $Serial." -ForegroundColor Green
}
