#!/usr/bin/env pwsh
# Smoke: list PENDING_REVIEW, show confidence, resolve one APPROVE.
. (Join-Path $PSScriptRoot '..\lib\common.ps1')
$root = Get-IteeRepoRoot
Set-Location $root

Write-Host "GET /api/health" -ForegroundColor Cyan
Invoke-RestMethod http://localhost:8081/api/health | ConvertTo-Json

Write-Host "`nGET /api/exceptions?status=PENDING_REVIEW" -ForegroundColor Cyan
$pending = @(Invoke-RestMethod 'http://localhost:8081/api/exceptions?status=PENDING_REVIEW')
"Pending count: $($pending.Count)"
if ($pending.Count -lt 1) {
    Write-Host "No PENDING_REVIEW yet. Re-run producer while orchestrator + AI engine are up, wait ~10s." -ForegroundColor Yellow
    exit 1
}

$one = $pending[0]
Write-Host "Sample: $($one.tradeId) severity=$($one.severity) confidence=$($one.confidenceScore)" -ForegroundColor Green
$one.confidenceFactors | ConvertTo-Json -Depth 4

Write-Host "`nPOST /api/exceptions/$($one.id)/resolve APPROVE" -ForegroundColor Cyan
$body = @{ action = 'APPROVE'; notes = 'Smoke resolve approve' } | ConvertTo-Json
$resolved = Invoke-RestMethod `
    -Method Post `
    -Uri "http://localhost:8081/api/exceptions/$($one.id)/resolve" `
    -ContentType 'application/json' `
    -Body $body

"Resolved status: $($resolved.status) action=$($resolved.resolveAction)"
if ($resolved.status -ne 'RESOLVED') {
    Write-Host "Unexpected status" -ForegroundColor Red
    exit 1
}

Write-Host "Resolve smoke OK" -ForegroundColor Green
