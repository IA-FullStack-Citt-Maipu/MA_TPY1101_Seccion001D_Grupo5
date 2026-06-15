# Optional helper script to generate tfvars from env vars (non-secret fields only)
# Usage: pwsh ./scripts/render-tfvars.ps1 -Environment dev

param(
  [Parameter(Mandatory = $true)]
  [ValidateSet("dev", "prod", "staging-perf")]
  [string]$Environment
)

$target = Join-Path $PSScriptRoot "..\environments\$Environment\terraform.tfvars"

if ($Environment -eq "staging-perf") {
@"
gcp_project_id = "$($env:GCP_PROJECT_ID)"
region = "$($env:GCP_REGION)"
environment = "$Environment"
artifact_registry_location = "$($env:GCP_REGION)"
backend_image = "$($env:BACKEND_IMAGE)"
frontend_image = "$($env:FRONTEND_IMAGE)"
cloudsql_instance_name = "$($env:CLOUDSQL_INSTANCE_NAME)"
cloudsql_db_name = "$($env:CLOUDSQL_DB_NAME)"
cloudsql_db_user = "$($env:CLOUDSQL_DB_USER)"
cloudsql_db_tier = "$($env:CLOUDSQL_DB_TIER)"
cloudsql_db_disk_size_gb = $($env:CLOUDSQL_DB_DISK_SIZE_GB)
backend_domain = "$($env:BACKEND_DOMAIN)"
frontend_domain = "$($env:FRONTEND_DOMAIN)"
"@ | Set-Content -Encoding utf8 $target
} else {
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
}

Write-Host "Generated $target"
