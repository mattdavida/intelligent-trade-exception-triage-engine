/*
  Azure Key Vault module — Intelligent Trade Exception Triage Engine
  ───────────────────────────────────────────────────────────────────
  Provisions a Key Vault for secret storage.

  In this capstone, local .env is the runtime source of truth after deploy.ps1.
  Key Vault is still provisioned so secrets can graduate there without a
  redesign later.

  Access model: RBAC (recommended over legacy access policies).
*/

param name string
param location string

@allowed(['dev', 'prod'])
param environment string

// ── Key Vault ─────────────────────────────────────────────────────────────────

resource keyVault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: name
  location: location
  properties: {
    sku: {
      family: 'A'
      name: 'standard'
    }
    tenantId: subscription().tenantId
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: environment == 'prod' ? 90 : 7
    enabledForDeployment: false
    enabledForTemplateDeployment: true
    publicNetworkAccess: 'Enabled'
  }
}

// ── Secret placeholders ───────────────────────────────────────────────────────
// Populate real values post-deploy via:
//   az keyvault secret set --vault-name <name> --name AZURE-OPENAI-API-KEY --value <key>

resource secretOpenAIKey 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: keyVault
  name: 'AZURE-OPENAI-API-KEY'
  properties: {
    value: 'REPLACE-AFTER-DEPLOY'
    contentType: 'text/plain'
    attributes: { enabled: true }
  }
}

// ── Outputs ───────────────────────────────────────────────────────────────────

output vaultName string = keyVault.name
output uri string = keyVault.properties.vaultUri
output id string = keyVault.id
