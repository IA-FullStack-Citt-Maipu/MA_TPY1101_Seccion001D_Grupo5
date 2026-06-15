locals {
  env_suffix              = var.environment
  frontend_origin         = var.frontend_domain != "" ? "https://${var.frontend_domain}" : "https://panol-frontend-${local.env_suffix}-${data.google_project.current.number}.${var.region}.run.app"
  frontend_origin_pattern = var.frontend_domain != "" ? "" : "https://panol-frontend-${local.env_suffix}-*.${var.region}.run.app,https://panol-frontend-${local.env_suffix}-*.a.run.app"
  backend_public_url      = var.backend_domain != "" ? "https://${var.backend_domain}" : module.backend_service.service_uri

  common_labels = {
    environment = var.environment
    managed_by  = "terraform"
    project     = "panol"
  }

  backend_env = merge({
    APP_DB_ENV                                         = "cloudsql"
    APP_PORT                                           = "8080"
    APP_SECURITY_ENABLED                               = tostring(var.app_security_enabled)
    APP_AUTH_MAX_FAILED_ATTEMPTS                       = tostring(var.app_auth_max_failed_attempts)
    APP_AUTH_LOCK_MINUTES                              = tostring(var.app_auth_lock_minutes)
    APP_AUTH_JWT_ISSUER                                = var.app_auth_jwt_issuer
    APP_AUTH_JWT_EXPIRATION_SECONDS                    = tostring(var.app_auth_jwt_expiration_seconds)
    APP_AUTH_REFRESH_EXPIRATION_SECONDS                = tostring(var.app_auth_refresh_expiration_seconds)
    APP_AUTH_COOKIE_SECURE                             = tostring(var.app_auth_cookie_secure)
    APP_AUTH_COOKIE_SAME_SITE                          = var.app_auth_cookie_same_site
    APP_AUTH_TOKEN_REVOCATION_CLEANUP_ENABLED          = tostring(var.app_auth_token_revocation_cleanup_enabled)
    APP_AUTH_TOKEN_REVOCATION_CLEANUP_INITIAL_DELAY_MS = tostring(var.app_auth_token_revocation_cleanup_initial_delay_ms)
    APP_AUTH_TOKEN_REVOCATION_CLEANUP_DELAY_MS         = tostring(var.app_auth_token_revocation_cleanup_delay_ms)
    APP_AUTH_TOKEN_REVOCATION_CLEANUP_BATCH_SIZE       = tostring(var.app_auth_token_revocation_cleanup_batch_size)
    APP_DB_CLOUDSQL_PG16_NO_FORCE_RLS_ENABLED          = "true"
    FRONTEND_ORIGIN                                    = local.frontend_origin
    CORS_ALLOWED_ORIGINS                               = local.frontend_origin
    CORS_ALLOWED_ORIGIN_PATTERNS                       = local.frontend_origin_pattern
    DB_CLOUDSQL_INSTANCE_CONNECTION_NAME               = module.cloud_sql.connection_name
    DB_CLOUDSQL_NAME                                   = var.cloudsql_db_name
    DB_CLOUDSQL_USER                                   = var.cloudsql_db_user
    DB_CLOUDSQL_IP_TYPES                               = var.cloudsql_ip_types
    DB_CLOUDSQL_HIKARI_MIN_IDLE                        = tostring(var.cloudsql_hikari_min_idle)
    DB_CLOUDSQL_HIKARI_MAX_POOL_SIZE                   = tostring(var.cloudsql_hikari_max_pool_size)
    DB_CLOUDSQL_HIKARI_CONNECTION_TIMEOUT_MS           = tostring(var.cloudsql_hikari_connection_timeout_ms)
    DB_CLOUDSQL_HIKARI_IDLE_TIMEOUT_MS                 = tostring(var.cloudsql_hikari_idle_timeout_ms)
    DB_CLOUDSQL_HIKARI_MAX_LIFETIME_MS                 = tostring(var.cloudsql_hikari_max_lifetime_ms)
  })

  frontend_env = {
    VITE_API_BASE_URL = local.backend_public_url
  }
}

data "google_project" "current" {
  project_id = var.gcp_project_id
}

module "artifact_registry" {
  source        = "../../modules/artifact_registry"
  project_id    = var.gcp_project_id
  location      = var.artifact_registry_location
  repository_id = "panol-apps-${local.env_suffix}"
  labels        = local.common_labels
}

module "secret_manager" {
  source     = "../../modules/secret_manager"
  project_id = var.gcp_project_id
  secrets = [
    "DB_CLOUDSQL_PASSWORD",
    "APP_AUTH_JWT_SECRET"
  ]
  secret_values = {
    DB_CLOUDSQL_PASSWORD = var.db_cloudsql_password_secret_value
    APP_AUTH_JWT_SECRET  = var.app_auth_jwt_secret_value
  }
}

module "cloud_sql" {
  source                         = "../../modules/cloud_sql_postgres"
  project_id                     = var.gcp_project_id
  region                         = var.region
  instance_name                  = var.cloudsql_instance_name
  database_name                  = var.cloudsql_db_name
  database_user                  = var.cloudsql_db_user
  database_password              = var.db_cloudsql_password_secret_value
  database_version               = var.cloudsql_db_version
  tier                           = var.cloudsql_db_tier
  disk_size_gb                   = var.cloudsql_db_disk_size_gb
  availability_type              = var.cloudsql_db_availability_type
  backups_enabled                = var.cloudsql_db_backups_enabled
  point_in_time_recovery_enabled = var.cloudsql_db_point_in_time_recovery_enabled
  deletion_protection            = var.cloudsql_db_deletion_protection
  labels                         = local.common_labels
}

module "runtime_iam" {
  source                       = "../../modules/iam"
  project_id                   = var.gcp_project_id
  environment                  = var.environment
  service_account_name         = "panol-runtime"
  service_account_display_name = "Panol Runtime ${upper(var.environment)}"
  service_account_user_members = var.runtime_sa_user_members
  roles = [
    "roles/secretmanager.secretAccessor",
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/cloudsql.client"
  ]
}

module "backend_service" {
  source                           = "../../modules/cloud_run_service"
  project_id                       = var.gcp_project_id
  region                           = var.region
  service_name                     = "panol-backend-${local.env_suffix}"
  image                            = var.backend_image
  container_port                   = 8080
  labels                           = local.common_labels
  ingress                          = "INGRESS_TRAFFIC_ALL"
  allow_unauthenticated            = true
  service_account_email            = module.runtime_iam.service_account_email
  min_instance_count               = var.backend_min_instances
  max_instance_count               = var.backend_max_instances
  timeout_seconds                  = var.backend_timeout_seconds
  max_instance_request_concurrency = var.backend_concurrency
  env_vars                         = local.backend_env
  secret_env_vars = {
    DB_CLOUDSQL_PASSWORD = {
      secret  = module.secret_manager.secret_ids["DB_CLOUDSQL_PASSWORD"]
      version = "latest"
    }
    APP_AUTH_JWT_SECRET = {
      secret  = module.secret_manager.secret_ids["APP_AUTH_JWT_SECRET"]
      version = try(module.secret_manager.secret_versions["APP_AUTH_JWT_SECRET"], "latest")
    }
  }
  custom_domain = var.backend_domain
  providers = {
    google      = google
    google-beta = google-beta
  }
}

module "frontend_service" {
  source                           = "../../modules/cloud_run_service"
  project_id                       = var.gcp_project_id
  region                           = var.region
  service_name                     = "panol-frontend-${local.env_suffix}"
  image                            = var.frontend_image
  container_port                   = 80
  labels                           = local.common_labels
  ingress                          = "INGRESS_TRAFFIC_ALL"
  allow_unauthenticated            = true
  service_account_email            = module.runtime_iam.service_account_email
  min_instance_count               = var.frontend_min_instances
  max_instance_count               = var.frontend_max_instances
  timeout_seconds                  = var.frontend_timeout_seconds
  max_instance_request_concurrency = var.frontend_concurrency
  env_vars                         = local.frontend_env
  secret_env_vars                  = {}
  custom_domain                    = var.frontend_domain
  providers = {
    google      = google
    google-beta = google-beta
  }
}
