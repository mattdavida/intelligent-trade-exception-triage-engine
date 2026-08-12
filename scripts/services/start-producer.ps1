#!/usr/bin/env pwsh
. (Join-Path $PSScriptRoot '..\lib\common.ps1')
$root = Get-IteeRepoRoot
Set-Location $root

$file = if ($args.Length -gt 0) { $args[0] } else { 'exceptions.json' }
$delayMs = if ($args.Length -gt 1) { $args[1] } else { '200' }

Write-Host "Building and starting exception feed producer..." -ForegroundColor Cyan
Write-Host "  File : $file"
Write-Host "  Delay: ${delayMs} ms between messages (0 = max)"

Invoke-IteeGradle @(':producer:run', "--args=$file $delayMs")
