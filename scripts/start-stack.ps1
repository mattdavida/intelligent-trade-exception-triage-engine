#!/usr/bin/env pwsh
<#
.SYNOPSIS
  Cold-start the ITETE local stack for demos and local development.

.DESCRIPTION
  Order: Docker infra -> AI engine -> orchestrator -> optional UI -> optional producer.
  Long-running Java/Python/Node processes open in separate PowerShell windows.
  Requires repo-root .env from .\infra\deploy.ps1 (Azure OpenAI).

.PARAMETER SkipUi
  Do not open the Angular desk window.

.PARAMETER SkipProducer
  Do not replay sample-data after services are healthy.

.PARAMETER FreshDb
  Wipe the Postgres volume before starting (clean demo queue).

.PARAMETER ProducerFile
  Fixture under sample-data/ (default exceptions.json).

.PARAMETER ProducerDelayMs
  Delay between Kafka messages (default 200; 0 = max speed).
#>
param(
    [switch]$SkipUi,
    [switch]$SkipProducer,
    [switch]$FreshDb,
    [string]$ProducerFile = 'exceptions.json',
    [string]$ProducerDelayMs = '200'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'lib\common.ps1')
$root = Get-IteeRepoRoot
Set-Location $root
$services = Join-Path $PSScriptRoot 'services'

Write-Host ""
Write-Host "ITETE start-stack" -ForegroundColor Cyan
Write-Host "Repo: $root"
Write-Host ""

if (-not (Test-Path -LiteralPath (Join-Path $root '.env'))) {
    Write-Host "Missing .env - run once: .\infra\deploy.ps1" -ForegroundColor Red
    exit 1
}
Import-IteeDotEnv (Join-Path $root '.env')
if (-not $env:AI_ENGINE_API_KEY) {
    Write-Host "AI_ENGINE_API_KEY missing in .env - re-run .\infra\deploy.ps1" -ForegroundColor Red
    exit 1
}

# 1) Infra
if ($FreshDb) {
    Write-Host "[1/5] Fresh DB - wiping compose volumes..." -ForegroundColor Cyan
    & (Join-Path $services 'stop-kafka.ps1') -WipeVolume
}
Write-Host "[1/5] Docker infra (Kafka / Postgres / Kafka UI)..." -ForegroundColor Cyan
& (Join-Path $services 'start-kafka.ps1')
if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Host "Waiting for Postgres on :5433 ..." -ForegroundColor DarkGray
$pgReady = $false
for ($i = 0; $i -lt 45; $i++) {
    docker exec itee-postgres pg_isready -U itee -d itee 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $pgReady = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (-not $pgReady) {
    Write-Host "Postgres did not become ready. Is Docker Desktop running?" -ForegroundColor Red
    exit 1
}
Write-Host "Ready: Postgres" -ForegroundColor Green

# 2) AI engine
Write-Host "[2/5] AI engine (:8000)..." -ForegroundColor Cyan
if (-not (Test-IteePortListening -Port 8000)) {
    Start-IteeServiceWindow -ScriptPath (Join-Path $services 'start-ai-engine.ps1') -Title 'ITETE AI engine'
} else {
    Write-Host "AI engine already listening on :8000" -ForegroundColor DarkGray
}
if (-not (Wait-IteeHttpOk -Uri 'http://localhost:8000/api/health' -TimeoutSec 120 -Label 'AI engine')) {
    exit 1
}

# 3) Orchestrator
Write-Host "[3/5] Orchestrator (:8081)..." -ForegroundColor Cyan
if (-not (Test-IteePortListening -Port 8081)) {
    Start-IteeServiceWindow -ScriptPath (Join-Path $services 'start-orchestrator.ps1') -Title 'ITETE orchestrator'
} else {
    Write-Host "Orchestrator already listening on :8081 (use stop-stack then start, or -Force via services script)" -ForegroundColor DarkGray
}
if (-not (Wait-IteeHttpOk -Uri 'http://localhost:8081/api/health' -TimeoutSec 180 -Label 'orchestrator')) {
    exit 1
}

# 4) UI
if (-not $SkipUi) {
    Write-Host "[4/5] Angular desk (:4200)..." -ForegroundColor Cyan
    if (-not (Test-IteePortListening -Port 4200)) {
        Start-IteeServiceWindow -ScriptPath (Join-Path $services 'start-ui.ps1') -Title 'ITETE UI'
    } else {
        Write-Host "UI already listening on :4200" -ForegroundColor DarkGray
    }
} else {
    Write-Host "[4/5] Skipping UI (-SkipUi)" -ForegroundColor DarkGray
}

# 5) Producer (foreground, one-shot)
if (-not $SkipProducer) {
    Write-Host "[5/5] Producer replay ($ProducerFile, delay=${ProducerDelayMs}ms)..." -ForegroundColor Cyan
    & (Join-Path $services 'start-producer.ps1') $ProducerFile $ProducerDelayMs
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Producer failed." -ForegroundColor Red
        exit 1
    }
    Write-Host "Waiting briefly for async AI analysis..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 8
} else {
    Write-Host "[5/5] Skipping producer (-SkipProducer)" -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "Stack is up." -ForegroundColor Green
Write-Host "  Desk      http://localhost:4200"
Write-Host "  REST      http://localhost:8081/api/exceptions"
Write-Host "  Health    http://localhost:8081/api/health"
Write-Host "  AI        http://localhost:8000/api/health"
Write-Host "  Kafka UI  http://localhost:8080"
Write-Host ""
Write-Host "Demo guide: DEMO.md" -ForegroundColor Cyan
Write-Host "Stop all:   .\scripts\stop-stack.ps1" -ForegroundColor Cyan
Write-Host ""
