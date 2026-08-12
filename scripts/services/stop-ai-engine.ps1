#!/usr/bin/env pwsh
# Stops whatever is listening on the AI engine port (default 8000).
param(
    [int]$Port = 8000
)

. (Join-Path $PSScriptRoot '..\lib\common.ps1')
$ok = Stop-IteePortListener -Port $Port -Label 'ai-engine'
# Do not call exit - it terminates a parent stop-stack session when invoked with &.
if (-not $ok) {
    Write-Error "Failed to free ai-engine port $Port"
}
