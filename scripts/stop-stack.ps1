#!/usr/bin/env pwsh
<#
.SYNOPSIS
  Stop the ITETE local stack (UI, orchestrator, AI engine, Docker infra).

.PARAMETER KeepInfra
  Leave Kafka/Postgres/Kafka UI running (only stop app processes).

.PARAMETER WipeVolume
  Also wipe the Postgres volume (implies stopping infra).
#>
param(
    [switch]$KeepInfra,
    [switch]$WipeVolume
)

$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot 'lib\common.ps1')
$root = Get-IteeRepoRoot
Set-Location $root
$services = Join-Path $PSScriptRoot 'services'

Write-Host ""
Write-Host "ITETE stop-stack" -ForegroundColor Cyan
Write-Host ""

Write-Host "[1/4] UI (:4200)..." -ForegroundColor Cyan
& (Join-Path $services 'stop-ui.ps1')

Write-Host "[2/4] Orchestrator (:8081)..." -ForegroundColor Cyan
& (Join-Path $services 'stop-orchestrator.ps1')

Write-Host "[3/4] AI engine (:8000)..." -ForegroundColor Cyan
& (Join-Path $services 'stop-ai-engine.ps1')

if ($KeepInfra -and -not $WipeVolume) {
    Write-Host "[4/4] Keeping Docker infra (-KeepInfra)" -ForegroundColor DarkGray
} else {
    Write-Host "[4/4] Docker infra..." -ForegroundColor Cyan
    if ($WipeVolume) {
        & (Join-Path $services 'stop-kafka.ps1') -WipeVolume
    } else {
        & (Join-Path $services 'stop-kafka.ps1')
    }
}

$failed = @()
foreach ($check in @(
        @{ Port = 4200; Name = 'ui' },
        @{ Port = 8081; Name = 'orchestrator' },
        @{ Port = 8000; Name = 'ai-engine' }
    )) {
    # Use TCP connect probe - netstat Listen rows can linger with zombie PIDs.
    if (Test-IteePortReallyServing -Port $check.Port) {
        $failed += "$($check.Name) (:$($check.Port))"
    }
}

Write-Host ""
if ($failed.Count -gt 0) {
    $left = $failed -join ', '
    Write-Host "Stack stop incomplete - still listening: $left" -ForegroundColor Red
    Write-Host "Retry: .\scripts\stop-stack.ps1" -ForegroundColor Yellow
    exit 1
}

Write-Host "Stack stopped." -ForegroundColor Green
Write-Host "Restart: .\scripts\start-stack.ps1" -ForegroundColor Cyan
Write-Host "Clean DB: .\scripts\start-stack.ps1 -FreshDb" -ForegroundColor Cyan
Write-Host ""
