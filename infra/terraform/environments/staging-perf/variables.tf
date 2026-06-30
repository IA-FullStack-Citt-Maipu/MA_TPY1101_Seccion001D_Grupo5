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

variable "frontend_image" {
  type = string
}

variable "cloudsql_instance_name" {
  type = string
}

variable "cloudsql_db_name" {
  type = string
}

variable "cloudsql_db_user" {
  type = string
}

variable "cloudsql_db_version" {
  type    = string
  default = "POSTGRES_16"
}

variable "cloudsql_db_tier" {
  type = string
}

variable "cloudsql_db_disk_size_gb" {
  type    = number
  default = 50
}

variable "cloudsql_db_availability_type" {
  type    = string
  default = "ZONAL"
}

variable "cloudsql_db_backups_enabled" {
  type    = bool
  default = true
}

variable "cloudsql_db_point_in_time_recovery_enabled" {
  type    = bool
  default = false
}

variable "cloudsql_db_deletion_protection" {
  type    = bool
  default = false
}

variable "cloudsql_ip_types" {
  type    = string
  default = "PUBLIC"
}

variable "cloudsql_hikari_min_idle" {
  type    = number
  default = 2
}

variable "cloudsql_hikari_max_pool_size" {
  type    = number
  default = 10
}

variable "cloudsql_hikari_connection_timeout_ms" {
  type    = number
  default = 30000
}

variable "cloudsql_hikari_idle_timeout_ms" {
  type    = number
  default = 600000
}

variable "cloudsql_hikari_max_lifetime_ms" {
  type    = number
  default = 1800000
}

variable "app_security_enabled" {
  type    = bool
  default = false
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
  default = "panol-backend-dev"
}

variable "app_auth_jwt_expiration_seconds" {
  type    = number
  default = 3600
}

variable "app_auth_refresh_expiration_seconds" {
  type    = number
  default = 604800
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
  type    = string
  default = ""
}

variable "backend_domain" {
  type    = string
  default = ""
}

variable "backend_min_instances" {
  type    = number
  default = 1
}

variable "backend_max_instances" {
  type    = number
  default = 6
}

variable "backend_timeout_seconds" {
  type    = number
  default = 300
}

variable "backend_concurrency" {
  type    = number
  default = 80
}

variable "frontend_min_instances" {
  type    = number
  default = 0
}

variable "frontend_max_instances" {
  type    = number
  default = 2
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

variable "db_cloudsql_password_secret_value" {
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
