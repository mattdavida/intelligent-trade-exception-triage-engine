using '../main.bicep'

// Dev — cheap TPM, destroy and recreate freely.
// OpenAI + Key Vault only; stack runs locally (Kafka / Spring / FastAPI / Angular).

param environment = 'dev'
param location = 'eastus'
param projectName = 'itee'
param chatModelName = 'gpt-5.4'
