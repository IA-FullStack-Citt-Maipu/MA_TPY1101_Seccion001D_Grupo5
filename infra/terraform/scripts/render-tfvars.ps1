# Optional helper script to generate tfvars from env vars (non-secret fields only)
# Usage: pwsh ./scripts/render-tfvars.ps1 -Environment dev

param(
  [Parameter(Mandatory = $true)]
  [ValidateSet("dev", "prod")]
  [string]$Environment
)

$target = Join-Path $PSScriptRoot "..\environments\$Environment\terraform.tfvars"

@"
gcp_project_id = "$($env:GCP_PROJECT_ID)"
region = "$($env:GCP_REGION)"
environment = "$Environment"
artifact_registry_location = "$($env:GCP_REGION)"
backend_image = "$($env:BACKEND_IMAGE)"
frontend_image = "$($env:FRONTEND_IMAGE)"
supabase_db_host = "$($env:SUPABASE_DB_HOST)"
supabase_db_port = 5432
supabase_db_name = "$($env:SUPABASE_DB_NAME)"
supabase_db_user = "$($env:SUPABASE_DB_USER)"
backend_domain = "$($env:BACKEND_DOMAIN)"
frontend_domain = "$($env:FRONTEND_DOMAIN)"
"@ | Set-Content -Encoding utf8 $target

Write-Host "Generated $target"
