#!/usr/bin/env pwsh
# Starts Kafka + Zookeeper + Kafka UI + PostgreSQL (full infra compose).
. (Join-Path $PSScriptRoot '..\lib\common.ps1')
$root = Get-IteeRepoRoot
Set-Location $root

Write-Host "Starting ITETE infra (Kafka :9092, UI :8080, Postgres :5433)..." -ForegroundColor Cyan
docker compose up -d
if ($LASTEXITCODE -eq 0) {
    Write-Host "Kafka UI : http://localhost:8080" -ForegroundColor Green
    Write-Host "Postgres : localhost:5433 / db=itee user=itee" -ForegroundColor Green
    Write-Host "Running detached in Docker. Stop with: .\scripts\services\stop-kafka.ps1" -ForegroundColor DarkGray
} else {
    Write-Host "docker compose failed - is Docker Desktop running?" -ForegroundColor Red
    exit 1
}
