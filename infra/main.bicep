/*
  Intelligent Trade Exception Triage Engine — Azure Infrastructure
  ==================================================================
  Top-level deployment. OpenAI + Key Vault for local polyglot stack.
  App Service is intentionally out of scope for this capstone (run locally).

  Deploy:
    .\infra\deploy.ps1
    .\infra\deploy.ps1 -SkipWhatIf

  Prefer deploy.ps1 — it writes repo-root .env automatically.
*/

@description('Environment name — used to select SKUs and name resources.')
@allowed(['dev', 'prod'])
param environment string

@description('Azure region for all resources.')
param location string = resourceGroup().location

@description('Short name used in all resource names. Keep to 8 chars max.')
@maxLength(8)
param projectName string = 'itee'

@description('Azure OpenAI chat model deployment name (must match app .env).')
param chatModelName string = 'gpt-5.4'

// ── Name tokens ───────────────────────────────────────────────────────────────
var suffix = uniqueString(resourceGroup().id)
var shortSuffix = take(suffix, 6)

var names = {
  openai: 'oai-${projectName}-${environment}-${shortSuffix}'
  keyVault: 'kv-${projectName}-${environment}-${shortSuffix}'
}

// ── Modules ───────────────────────────────────────────────────────────────────

module openai 'modules/openai.bicep' = {
  name: 'openai-deploy'
  params: {
    name: names.openai
    location: location
    chatDeploymentName: chatModelName
    environment: environment
  }
}

module keyVault 'modules/keyvault.bicep' = {
  name: 'keyvault-deploy'
  params: {
    name: names.keyVault
    location: location
    environment: environment
  }
}

// ── Outputs — deploy.ps1 maps these into .env ─────────────────────────────────

@description('Paste into AZURE_OPENAI_ENDPOINT in .env')
output openaiEndpoint string = openai.outputs.endpoint

@description('Paste into AZURE_OPENAI_CHAT_DEPLOYMENT in .env')
output chatDeploymentName string = chatModelName

@description('Key Vault URI — use for secret references in prod')
output keyVaultUri string = keyVault.outputs.uri

@description('Cognitive Services account name (for key fetch / cleanup)')
output openaiAccountName string = openai.outputs.accountName
