#!/usr/bin/env pwsh
. (Join-Path $PSScriptRoot '..\lib\common.ps1')
$root = Get-IteeRepoRoot
$uiDir = Join-Path $root 'ui'
Set-Location $uiDir

if (-not (Test-Path -LiteralPath (Join-Path $uiDir 'node_modules'))) {
    Write-Host "Installing ui/ npm dependencies (first run)..." -ForegroundColor Cyan
    npm install
    if ($null -ne $LASTEXITCODE -and $LASTEXITCODE -ne 0) {
        Write-Host "npm install failed." -ForegroundColor Red
        exit 1
    }
}

Write-Host "Starting ITETE Angular desk on :4200 ..." -ForegroundColor Cyan
Write-Host "Requires: orchestrator on :8081 (REST + SSE)" -ForegroundColor DarkGray
npx ng serve --host 0.0.0.0 --port 4200
