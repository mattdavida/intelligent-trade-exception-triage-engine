using '../main.bicep'

// Prod — higher TPM. Still local app processes for this capstone unless you
// extend infra later with App Service.

param environment = 'prod'
param location = 'eastus'
param projectName = 'itee'
param chatModelName = 'gpt-5.4'
