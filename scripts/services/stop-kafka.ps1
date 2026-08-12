#!/usr/bin/env pwsh
# Stops Kafka + Zookeeper + Kafka UI + PostgreSQL.
param(
    [switch]$WipeVolume
)

. (Join-Path $PSScriptRoot '..\lib\common.ps1')
$root = Get-IteeRepoRoot
Set-Location $root

Write-Host "Stopping ITETE infra containers..." -ForegroundColor Cyan
if ($WipeVolume) {
    docker compose down -v
} else {
    docker compose down
}
if ($LASTEXITCODE -eq 0) {
    if ($WipeVolume) {
        Write-Host "Stopped and wiped Postgres volume." -ForegroundColor Green
    } else {
        Write-Host "Stopped. (Postgres volume kept - data survives restart.)" -ForegroundColor Green
        Write-Host "To wipe DB volume: .\scripts\services\stop-kafka.ps1 -WipeVolume" -ForegroundColor DarkGray
    }
} else {
    Write-Host "docker compose down failed" -ForegroundColor Red
    Write-Error "docker compose down failed"
}
