<#
.SYNOPSIS
    Deploy ITETE infrastructure to Azure (OpenAI + Key Vault) and write .env.

.DESCRIPTION
    Creates the resource group if needed, optional what-if preview, deploys Bicep,
    fetches the OpenAI key, and writes repo-root .env (required — no AI stub path).

    Default (dev):
        .\infra\deploy.ps1

    Skip preview:
        .\infra\deploy.ps1 -SkipWhatIf

    Prod:
        .\infra\deploy.ps1 -Environment prod -SkipWhatIf

.PARAMETER Environment
    Target environment: 'dev' or 'prod'. Default: dev.

.PARAMETER SkipWhatIf
    Skip the what-if preview and deploy immediately.

.PARAMETER SkipEnvWrite
    Write a temp file with .env contents but do not overwrite the repo-root .env.
#>

param(
    [ValidateSet('dev', 'prod')]
    [string]$Environment = 'dev',

    [switch]$SkipWhatIf,

    [switch]$SkipEnvWrite
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# --- Config ------------------------------------------------------------------
$ProjectName    = 'itee'
$ResourceGroup  = "rg-$ProjectName-$Environment"
$Location       = 'eastus'
$DeploymentName = "$ProjectName-$Environment-$(Get-Date -Format 'yyyyMMdd-HHmm')"
$TemplateFile   = Join-Path $PSScriptRoot 'main.bicep'
$ParamsFile     = Join-Path $PSScriptRoot "params\$Environment.bicepparam"
$RepoRoot       = Split-Path $PSScriptRoot -Parent
$EnvFile        = Join-Path $RepoRoot '.env'

# Shared secret for Java orchestrator → FastAPI (local POC). Rotatable later.
$AiEngineApiKey = -join ((1..32) | ForEach-Object { '{0:x}' -f (Get-Random -Maximum 16) })

# --- Pre-flight checks -------------------------------------------------------
Write-Host ''
Write-Host '=== ITETE — Bicep Deploy (OpenAI required) ===' -ForegroundColor Cyan
Write-Host "Environment  : $Environment"
Write-Host "Resource Grp : $ResourceGroup"
Write-Host "Location     : $Location"
Write-Host "Template     : $TemplateFile"
Write-Host "Params       : $ParamsFile"
Write-Host "Env file     : $EnvFile"
Write-Host ''

if (-not (Get-Command az -ErrorAction SilentlyContinue)) {
    throw 'Azure CLI not found. Install from https://aka.ms/installazurecliwindows and re-run.'
}

$accountJson = az account show 2>$null
if (-not $accountJson) {
    Write-Host 'Not logged in to Azure. Running az login...' -ForegroundColor Yellow
    az login | Out-Null
}
$account = az account show | ConvertFrom-Json
Write-Host "Logged in as  : $($account.user.name)" -ForegroundColor Green
Write-Host "Subscription  : $($account.name) ($($account.id))"
Write-Host ''

# --- Create resource group if needed -----------------------------------------
$rgExists = az group exists --name $ResourceGroup
if ($rgExists -eq 'false') {
    Write-Host "Creating resource group '$ResourceGroup' in '$Location'..." -ForegroundColor Yellow
    az group create --name $ResourceGroup --location $Location | Out-Null
    Write-Host 'Resource group created.' -ForegroundColor Green
} else {
    Write-Host "Resource group '$ResourceGroup' already exists." -ForegroundColor Green
}
Write-Host ''

# --- Build deployment arguments ----------------------------------------------
$deployArgs = @(
    '--resource-group', $ResourceGroup,
    '--template-file', $TemplateFile,
    '--parameters', $ParamsFile
)

# --- What-if preview ---------------------------------------------------------
if (-not $SkipWhatIf) {
    Write-Host 'Running what-if preview (no changes made yet)...' -ForegroundColor Cyan
    az deployment group what-if @deployArgs
    Write-Host ''
    $confirm = Read-Host 'Proceed with deployment? (y/N)'
    if ($confirm -ne 'y' -and $confirm -ne 'Y') {
        Write-Host 'Deployment cancelled.' -ForegroundColor Yellow
        exit 0
    }
    Write-Host ''
}

# --- Deploy ------------------------------------------------------------------
Write-Host 'Deploying... (OpenAI provisioning takes 3-5 minutes)' -ForegroundColor Cyan
$resultJson = az deployment group create `
    @deployArgs `
    --name $DeploymentName `
    --output json

if ($LASTEXITCODE -ne 0) {
    throw 'Deployment failed. Check the Azure portal Activity Log for details.'
}

$result  = $resultJson | ConvertFrom-Json
$outputs = $result.properties.outputs

Write-Host ''
Write-Host 'Deployment succeeded!' -ForegroundColor Green

# --- Fetch the OpenAI API key (not returned in Bicep outputs for security) ---
$openaiAccountName = $outputs.openaiAccountName.value
if (-not $openaiAccountName) {
    $openaiAccountName = az resource list `
        --resource-group $ResourceGroup `
        --resource-type 'Microsoft.CognitiveServices/accounts' `
        --query '[0].name' `
        --output tsv
}

$openaiKey = az cognitiveservices account keys list `
    --name $openaiAccountName `
    --resource-group $ResourceGroup `
    --query 'key1' `
    --output tsv

$openaiEndpoint = $outputs.openaiEndpoint.value
$chatDeployment = $outputs.chatDeploymentName.value
$keyVaultUri    = $outputs.keyVaultUri.value

# Preserve existing AI_ENGINE_API_KEY if .env already has one
if (Test-Path $EnvFile) {
    $existing = Get-Content $EnvFile -Raw
    if ($existing -match '(?m)^AI_ENGINE_API_KEY=(.+)$') {
        $AiEngineApiKey = $Matches[1].Trim()
    }
}

$envBlock = @"
# Generated by infra/deploy.ps1 — do not commit (.gitignore)
# Resource group: $ResourceGroup

# ─── Azure OpenAI (required — no stub path) ───────────────────────────────────
AZURE_OPENAI_API_KEY=$openaiKey
AZURE_OPENAI_ENDPOINT=$openaiEndpoint
AZURE_OPENAI_API_VERSION=2024-02-01
AZURE_OPENAI_CHAT_DEPLOYMENT=$chatDeployment

# ─── AI engine ↔ Java orchestrator ────────────────────────────────────────────
AI_ENGINE_API_KEY=$AiEngineApiKey
AI_ENGINE_BASE_URL=http://localhost:8000

# ─── Local stack ports (see plan.md) ──────────────────────────────────────────
ORCHESTRATOR_PORT=8081
POSTGRES_HOST=localhost
POSTGRES_PORT=5433
POSTGRES_DB=itee
POSTGRES_USER=itee
POSTGRES_PASSWORD=itee

# Key Vault URI (optional local use; secrets live in .env for this POC)
# KEY_VAULT_URI=$keyVaultUri
"@

# Do not print secrets to the console (shared terminals / screen shares).
if (-not $SkipEnvWrite) {
    Set-Content -Path $EnvFile -Value $envBlock -Encoding utf8
    Write-Host ''
    Write-Host "Wrote $EnvFile (gitignored) with Azure OpenAI + AI_ENGINE_API_KEY." -ForegroundColor Green
    Write-Host "  endpoint   : $openaiEndpoint"
    Write-Host "  deployment : $chatDeployment"
    Write-Host "  key vault  : $keyVaultUri"
    Write-Host '  secrets    : redacted (open .env locally if needed)' -ForegroundColor DarkGray
} else {
    $previewPath = Join-Path $env:TEMP "itee-env-$Environment.txt"
    Set-Content -Path $previewPath -Value $envBlock -Encoding utf8
    Write-Host ''
    Write-Host "Skipped writing repo .env (-SkipEnvWrite)." -ForegroundColor Yellow
    Write-Host "Redacted preview written to: $previewPath" -ForegroundColor Yellow
    Write-Host 'Copy into repo-root .env manually; do not commit it.' -ForegroundColor Yellow
}

Write-Host ''
Write-Host 'Next steps:' -ForegroundColor Cyan
Write-Host '  1. Confirm repo-root .env exists (gitignored) — never commit it'
Write-Host '  2. Start the local stack: .\scripts\start-stack.ps1 -FreshDb'
Write-Host '  3. Open http://localhost:4200 and follow DEMO.md'
Write-Host '  4. Tear down Azure later: .\infra\cleanup.ps1 -Environment ' -NoNewline
Write-Host $Environment -ForegroundColor Cyan
