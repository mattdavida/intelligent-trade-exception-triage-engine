#!/usr/bin/env pwsh
. (Join-Path $PSScriptRoot '..\lib\common.ps1')
$root = Get-IteeRepoRoot
Set-Location $root

$aiDir = Join-Path $root 'ai-engine'
$venvPython = Join-Path $aiDir '.venv\Scripts\python.exe'

if (-not (Test-Path -LiteralPath $venvPython)) {
    Write-Host "Creating ai-engine/.venv and installing requirements..." -ForegroundColor Cyan
    python -m venv (Join-Path $aiDir '.venv')
    & $venvPython -m pip install --upgrade pip
    & $venvPython -m pip install -r (Join-Path $aiDir 'requirements.txt')
}

Write-Host "Starting ITETE AI engine on :8000 ..." -ForegroundColor Cyan
Write-Host "Requires: repo-root .env from .\infra\deploy.ps1" -ForegroundColor DarkGray
Write-Host "Health: http://localhost:8000/api/health" -ForegroundColor DarkGray

Set-Location -LiteralPath $aiDir
# No --reload: reloader parent/child made stop-stack leave :8000 alive.
& $venvPython -m uvicorn main:app --host 0.0.0.0 --port 8000
