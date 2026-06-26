locals {
  env_suffix      = var.environment
  backend_origin  = var.backend_domain != "" ? "https://${var.backend_domain}" : module.backend_service.service_uri
  bot_origin      = var.bot_domain != "" ? "https://${var.bot_domain}" : module.bot_service.service_uri
  frontend_origin = var.frontend_domain != "" ? "https://${var.frontend_domain}" : "https://panol-frontend-${local.env_suffix}-${data.google_project.current.number}.${var.region}.run.app"

  common_labels = {
    environment = var.environment
    managed_by  = "terraform"
    project     = "panol"
  }

  backend_env = merge({
    APP_DB_ENV                                         = "supabase"
    APP_PORT                                           = "8080"
    APP_SECURITY_ENABLED                               = tostring(var.app_security_enabled)
    APP_AUTH_MAX_FAILED_ATTEMPTS                       = tostring(var.app_auth_max_failed_attempts)
    APP_AUTH_LOCK_MINUTES                              = tostring(var.app_auth_lock_minutes)
    APP_AUTH_JWT_ISSUER                                = var.app_auth_jwt_issuer
    APP_AUTH_JWT_EXPIRATION_SECONDS                    = tostring(var.app_auth_jwt_expiration_seconds)
    APP_AUTH_REFRESH_EXPIRATION_SECONDS                = tostring(var.app_auth_refresh_expiration_seconds)
    APP_AUTH_REFRESH_TEMPORARY_EXPIRATION_SECONDS      = tostring(var.app_auth_refresh_temporary_expiration_seconds)
    APP_AUTH_COOKIE_SECURE                             = tostring(var.app_auth_cookie_secure)
    APP_AUTH_COOKIE_SAME_SITE                          = var.app_auth_cookie_same_site
    APP_AUTH_TOKEN_REVOCATION_CLEANUP_ENABLED          = tostring(var.app_auth_token_revocation_cleanup_enabled)
    APP_AUTH_TOKEN_REVOCATION_CLEANUP_INITIAL_DELAY_MS = tostring(var.app_auth_token_revocation_cleanup_initial_delay_ms)
    APP_AUTH_TOKEN_REVOCATION_CLEANUP_DELAY_MS         = tostring(var.app_auth_token_revocation_cleanup_delay_ms)
    APP_AUTH_TOKEN_REVOCATION_CLEANUP_BATCH_SIZE       = tostring(var.app_auth_token_revocation_cleanup_batch_size)
    JWT_ISSUER_URI                                     = var.jwt_issuer_uri
    FRONTEND_ORIGIN                                    = local.frontend_origin
    CORS_ALLOWED_ORIGINS                               = local.frontend_origin
    DB_SUPABASE_HOST                                   = var.supabase_db_host
    DB_SUPABASE_PORT                                   = tostring(var.supabase_db_port)
    DB_SUPABASE_NAME                                   = var.supabase_db_name
    DB_SUPABASE_USER                                   = var.supabase_db_user
    DB_SUPABASE_SSL_MODE                               = var.supabase_db_ssl_mode
  })

  bot_env = {
    GEMINI_MODEL            = var.gemini_model
    LLM_TIMEOUT_SECONDS     = tostring(var.bot_llm_timeout_seconds)
    BACKEND_BASE_URL        = local.backend_origin
    BACKEND_TIMEOUT_SECONDS = tostring(var.bot_backend_timeout_seconds)
    BACKEND_RETRY_COUNT     = tostring(var.bot_backend_retry_count)
    JWT_ISSUER              = var.app_auth_jwt_issuer
    CORS_ALLOWED_ORIGINS    = local.frontend_origin
  }

  frontend_env = {
    VITE_API_BASE_URL             = local.backend_origin
    VITE_BOT_API_BASE_URL         = local.bot_origin
    VITE_SUPABASE_PUBLISHABLE_KEY = var.vite_supabase_publishable_key
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
    "DB_SUPABASE_PASSWORD",
    "APP_AUTH_JWT_SECRET",
    "APP_SECURITY_AI_AGENT_SECRET",
    "GOOGLE_API_KEY"
  ]
  secret_values = {
    DB_SUPABASE_PASSWORD         = var.db_supabase_password_secret_value
    APP_AUTH_JWT_SECRET          = var.app_auth_jwt_secret_value
    APP_SECURITY_AI_AGENT_SECRET = var.app_security_ai_agent_secret_value
    GOOGLE_API_KEY               = var.google_api_key_secret_value
  }
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
    "roles/monitoring.metricWriter"
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
    DB_SUPABASE_PASSWORD = {
      secret  = module.secret_manager.secret_ids["DB_SUPABASE_PASSWORD"]
      version = "latest"
    }
    APP_AUTH_JWT_SECRET = {
      secret  = module.secret_manager.secret_ids["APP_AUTH_JWT_SECRET"]
      version = try(module.secret_manager.secret_versions["APP_AUTH_JWT_SECRET"], "latest")
    }
    APP_SECURITY_AI_AGENT_SECRET = {
      secret  = module.secret_manager.secret_ids["APP_SECURITY_AI_AGENT_SECRET"]
      version = try(module.secret_manager.secret_versions["APP_SECURITY_AI_AGENT_SECRET"], "latest")
    }
  }
  custom_domain = var.backend_domain
  providers = {
    google      = google
    google-beta = google-beta
  }
}

module "bot_service" {
  source                           = "../../modules/cloud_run_service"
  project_id                       = var.gcp_project_id
  region                           = var.region
  service_name                     = "panol-bot-${local.env_suffix}"
  image                            = var.bot_image
  container_port                   = 8000
  labels                           = local.common_labels
  ingress                          = "INGRESS_TRAFFIC_ALL"
  allow_unauthenticated            = true
  service_account_email            = module.runtime_iam.service_account_email
  min_instance_count               = var.bot_min_instances
  max_instance_count               = var.bot_max_instances
  timeout_seconds                  = var.bot_timeout_seconds
  max_instance_request_concurrency = var.bot_concurrency
  env_vars                         = local.bot_env
  secret_env_vars = {
    GOOGLE_API_KEY = {
      secret  = module.secret_manager.secret_ids["GOOGLE_API_KEY"]
      version = try(module.secret_manager.secret_versions["GOOGLE_API_KEY"], "latest")
    }
    BACKEND_CLIENT_SECRET = {
      secret  = module.secret_manager.secret_ids["APP_SECURITY_AI_AGENT_SECRET"]
      version = try(module.secret_manager.secret_versions["APP_SECURITY_AI_AGENT_SECRET"], "latest")
    }
    JWT_SECRET_KEY = {
      secret  = module.secret_manager.secret_ids["APP_AUTH_JWT_SECRET"]
      version = try(module.secret_manager.secret_versions["APP_AUTH_JWT_SECRET"], "latest")
    }
  }
  custom_domain = var.bot_domain
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
