#!/usr/bin/env pwsh
# Stops whatever is listening on the Angular desk port (default 4200).
param(
    [int]$Port = 4200
)

. (Join-Path $PSScriptRoot '..\lib\common.ps1')
$ok = Stop-IteePortListener -Port $Port -Label 'ui'
if (-not $ok) {
    Write-Error "Failed to free ui port $Port"
}
