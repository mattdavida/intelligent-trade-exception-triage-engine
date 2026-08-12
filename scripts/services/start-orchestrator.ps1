#!/usr/bin/env pwsh
param(
    [switch]$Force
)

. (Join-Path $PSScriptRoot '..\lib\common.ps1')
$root = Get-IteeRepoRoot
Set-Location $root
$port = 8081

Import-IteeDotEnv (Join-Path $root '.env')

if (Test-IteePortListening -Port $port) {
    if ($Force) {
        Write-Host "Port $port in use - stopping existing process (-Force)..." -ForegroundColor Yellow
        & (Join-Path $PSScriptRoot 'stop-orchestrator.ps1') -Port $port
        if (Test-IteePortListening -Port $port) {
            Write-Host "Failed to free port $port" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "Port $port is already in use (leftover orchestrator?)." -ForegroundColor Red
        Write-Host "  Stop it:  .\scripts\services\stop-orchestrator.ps1" -ForegroundColor Yellow
        Write-Host "  Or force: .\scripts\services\start-orchestrator.ps1 -Force" -ForegroundColor Yellow
        exit 1
    }
}

if (-not $env:AI_ENGINE_API_KEY) {
    Write-Host "AI_ENGINE_API_KEY missing - run .\infra\deploy.ps1 and ensure .env exists." -ForegroundColor Red
    exit 1
}

Write-Host "Starting ITETE orchestrator on :$port ..." -ForegroundColor Cyan
Write-Host "Requires: Docker infra + AI engine (:8000)" -ForegroundColor DarkGray
Write-Host "Stop with Ctrl+C, or .\scripts\services\stop-orchestrator.ps1" -ForegroundColor DarkGray
Invoke-IteeGradle @(':orchestrator:bootRun')
