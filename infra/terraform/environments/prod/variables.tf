variable "gcp_project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "environment" {
  type = string
}

variable "artifact_registry_location" {
  type = string
}

variable "backend_image" {
  type = string
}

variable "bot_image" {
  type = string
}

variable "frontend_image" {
  type = string
}

variable "supabase_db_host" {
  type = string
}

variable "supabase_db_port" {
  type = number
}

variable "supabase_db_name" {
  type = string
}

variable "supabase_db_user" {
  type = string
}

variable "supabase_db_ssl_mode" {
  type    = string
  default = "require"
}

variable "app_security_enabled" {
  type    = bool
  default = true
}

variable "app_auth_max_failed_attempts" {
  type    = number
  default = 5
}

variable "app_auth_lock_minutes" {
  type    = number
  default = 15
}

variable "app_auth_jwt_issuer" {
  type    = string
  default = "panol-backend-prod"
}

variable "app_auth_jwt_expiration_seconds" {
  type    = number
  default = 3600
}

variable "app_auth_refresh_expiration_seconds" {
  type    = number
  default = 604800
}

variable "app_auth_refresh_temporary_expiration_seconds" {
  type    = number
  default = 86400
}

variable "app_auth_cookie_secure" {
  type    = bool
  default = true
}

variable "app_auth_cookie_same_site" {
  type    = string
  default = "Lax"
}

variable "app_auth_token_revocation_cleanup_enabled" {
  type    = bool
  default = true
}

variable "app_auth_token_revocation_cleanup_initial_delay_ms" {
  type    = number
  default = 300000
}

variable "app_auth_token_revocation_cleanup_delay_ms" {
  type    = number
  default = 1800000
}

variable "app_auth_token_revocation_cleanup_batch_size" {
  type    = number
  default = 500
}

variable "frontend_domain" {
  type = string
}

variable "backend_domain" {
  type = string
}

variable "bot_domain" {
  type = string
}

variable "backend_min_instances" {
  type    = number
  default = 0
}

variable "backend_max_instances" {
  type    = number
  default = 3
}

variable "backend_timeout_seconds" {
  type    = number
  default = 300
}

variable "backend_concurrency" {
  type    = number
  default = 80
}

variable "bot_min_instances" {
  type    = number
  default = 0
}

variable "bot_max_instances" {
  type    = number
  default = 2
}

variable "bot_timeout_seconds" {
  type    = number
  default = 120
}

variable "bot_concurrency" {
  type    = number
  default = 10
}

variable "frontend_min_instances" {
  type    = number
  default = 0
}

variable "frontend_max_instances" {
  type    = number
  default = 3
}

variable "frontend_timeout_seconds" {
  type    = number
  default = 120
}

variable "frontend_concurrency" {
  type    = number
  default = 80
}

variable "runtime_sa_user_members" {
  type    = list(string)
  default = []
}

variable "db_supabase_password_secret_value" {
  type      = string
  default   = ""
  sensitive = true
}

variable "app_auth_jwt_secret_value" {
  type      = string
  default   = ""
  sensitive = true
}

variable "app_security_ai_agent_secret_value" {
  type      = string
  default   = ""
  sensitive = true
}

variable "google_api_key_secret_value" {
  type      = string
  default   = ""
  sensitive = true
}

variable "jwt_issuer_uri" {
  type = string
}

variable "vite_supabase_publishable_key" {
  type = string
}

variable "gemini_model" {
  type    = string
  default = "gemini-2.5-flash-lite"
}

variable "bot_llm_timeout_seconds" {
  type    = number
  default = 20
}

variable "bot_llm_total_timeout_seconds" {
  type    = number
  default = 75
}

variable "bot_backend_timeout_seconds" {
  type    = number
  default = 10
}

variable "bot_backend_retry_count" {
  type    = number
  default = 0
}
