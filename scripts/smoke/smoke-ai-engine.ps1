#!/usr/bin/env pwsh
# Smoke: POST /api/v1/analyze-exception against a running AI engine.
. (Join-Path $PSScriptRoot '..\lib\common.ps1')
$root = Get-IteeRepoRoot
Set-Location $root

$envPath = Join-Path $root '.env'
if (-not (Test-Path $envPath)) {
    Write-Host "Missing .env - run .\infra\deploy.ps1 first." -ForegroundColor Red
    exit 1
}

$apiKey = $null
Get-Content $envPath | ForEach-Object {
    if ($_ -match '^\s*AI_ENGINE_API_KEY=(.+)\s*$') {
        $apiKey = $Matches[1].Trim()
    }
}
if (-not $apiKey) {
    Write-Host "AI_ENGINE_API_KEY not found in .env" -ForegroundColor Red
    exit 1
}

$body = @{
    tradeId         = 'TRD-10042'
    counterparty    = 'ACME-BANK'
    discrepancyType = 'SSI_MISMATCH'
    instrument      = 'ZN'
    amount          = 2500000.00
    currency        = 'USD'
    side            = 'SELL'
    detectedAt      = '2026-08-12T13:00:00Z'
    rawDetails      = 'Settlement account on affirm differs from SSI master'
} | ConvertTo-Json

Write-Host "POST http://localhost:8000/api/v1/analyze-exception ..." -ForegroundColor Cyan
try {
    $resp = Invoke-RestMethod `
        -Method Post `
        -Uri 'http://localhost:8000/api/v1/analyze-exception' `
        -Headers @{ 'X-API-Key' = $apiKey; 'Content-Type' = 'application/json' } `
        -Body $body
} catch {
    Write-Host "Request failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

$resp | ConvertTo-Json -Depth 5
if (-not $resp.severity -or -not $resp.recommendation -or -not $resp.reasoning) {
    Write-Host "Response missing required fields." -ForegroundColor Red
    exit 1
}
if ($resp.PSObject.Properties.Name -contains 'confidence') {
    Write-Host "FAIL: response must not include confidence (Java-owned)." -ForegroundColor Red
    exit 1
}

Write-Host "Smoke OK - live Azure OpenAI analysis returned." -ForegroundColor Green
