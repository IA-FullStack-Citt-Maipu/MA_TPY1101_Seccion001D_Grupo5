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

variable "frontend_domain" {
  type = string
}

variable "backend_domain" {
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

variable "jwt_issuer_uri" {
  type = string
}

variable "vite_supabase_publishable_key" {
  type = string
}
