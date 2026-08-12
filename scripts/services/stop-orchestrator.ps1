#!/usr/bin/env pwsh
# Stops whatever is listening on the orchestrator port (default 8081).
param(
    [int]$Port = 8081
)

. (Join-Path $PSScriptRoot '..\lib\common.ps1')
$ok = Stop-IteePortListener -Port $Port -Label 'orchestrator'
if (-not $ok) {
    Write-Error "Failed to free orchestrator port $Port"
}
