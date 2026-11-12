<#
.SYNOPSIS
    Abre el puerto 3306 (MySQL) en el Firewall de Windows para la LAN, así el
    teléfono puede conectarse. Se auto-eleva (pide UAC).

.DESCRIPTION
    Crea una regla INBOUND que permite TCP 3306 SOLO desde la subred local
    (por defecto 192.168.1.0/24), en todos los perfiles. No expone MySQL a
    internet.

.EXAMPLE
    .\scripts\allow-mysql-firewall.ps1
    .\scripts\allow-mysql-firewall.ps1 -Subnet 192.168.0.0/24
    .\scripts\allow-mysql-firewall.ps1 -Remove
#>
[CmdletBinding()]
param(
    [string]$Subnet = "192.168.1.0/24",
    [int]$Port = 3306,
    [switch]$Remove
)

$RuleName = "MySQL $Port (LAN)"

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
    -Description "Permite a la app RF Inventario conectarse a MySQL desde la LAN." | Out-Null

Write-Host "OK: TCP $Port abierto para $Subnet (inbound)." -ForegroundColor Green
Get-NetFirewallRule -DisplayName $RuleName |
    Select-Object DisplayName, Enabled, Direction, Action, Profile | Format-List
Write-Host "Probá de nuevo la app en el teléfono." -ForegroundColor Cyan
Read-Host "Enter para cerrar"
