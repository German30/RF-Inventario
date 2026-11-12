<#
.SYNOPSIS
    Abre el puerto del backend Spring (8082) en el Firewall de Windows para la
    LAN, así el teléfono puede llegar a la API. Se auto-eleva (pide UAC).

.DESCRIPTION
    Desde que el acceso a MySQL vive en el backend, la app Android solo necesita
    alcanzar la API por HTTP. Este script crea una regla INBOUND que permite el
    puerto SOLO desde la subred local (por defecto 192.168.1.0/24). No expone
    nada a internet.

.EXAMPLE
    .\scripts\allow-api-firewall.ps1
    .\scripts\allow-api-firewall.ps1 -Port 8082 -Subnet 192.168.0.0/24
    .\scripts\allow-api-firewall.ps1 -Remove
#>
[CmdletBinding()]
param(
    [string]$Subnet = "192.168.1.0/24",
    [int]$Port = 8082,
    [switch]$Remove
)

$RuleName = "RF Inventario API $Port (LAN)"

# --- auto-elevación ---
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).
    IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "Elevando (UAC)..." -ForegroundColor Yellow
    $argList = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "`"$PSCommandPath`"",
                 "-Subnet", $Subnet, "-Port", $Port)
    if ($Remove) { $argList += "-Remove" }
    Start-Process -FilePath "powershell.exe" -Verb RunAs -ArgumentList $argList
    return
}

if ($Remove) {
    Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    Write-Host "Regla '$RuleName' eliminada." -ForegroundColor Yellow
    Read-Host "Enter para cerrar"
    return
}

Get-NetFirewallRule -DisplayName $RuleName -ErrorAction SilentlyContinue | Remove-NetFirewallRule

New-NetFirewallRule `
    -DisplayName $RuleName `
    -Direction Inbound `
    -Protocol TCP `
    -LocalPort $Port `
    -RemoteAddress $Subnet `
    -Action Allow `
    -Profile Any `
    -Description "Permite a la app RF Inventario llegar al backend Spring desde la LAN." | Out-Null

Write-Host "OK: TCP $Port abierto para $Subnet (inbound)." -ForegroundColor Green
Get-NetFirewallRule -DisplayName $RuleName |
    Select-Object DisplayName, Enabled, Direction, Action, Profile | Format-List
Write-Host "Levantá el backend (mvnw spring-boot:run) y probá la app en el teléfono." -ForegroundColor Cyan
Read-Host "Enter para cerrar"
