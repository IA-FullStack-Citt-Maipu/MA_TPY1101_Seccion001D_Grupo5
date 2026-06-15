variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "instance_name" {
  type = string
}

variable "database_name" {
  type = string
}

variable "database_user" {
  type = string
}

variable "database_password" {
  type      = string
  sensitive = true
}

variable "database_version" {
  type    = string
  default = "POSTGRES_16"
}

variable "edition" {
  type    = string
  default = "ENTERPRISE"
}

variable "tier" {
  type = string
}

variable "disk_size_gb" {
  type    = number
  default = 50
}

variable "availability_type" {
  type    = string
  default = "ZONAL"
}

variable "backups_enabled" {
  type    = bool
  default = true
}

variable "point_in_time_recovery_enabled" {
  type    = bool
  default = false
}

variable "query_insights_enabled" {
  type    = bool
  default = true
}

variable "ipv4_enabled" {
  type    = bool
  default = true
}

variable "deletion_protection" {
  type    = bool
  default = false
}

variable "backup_start_time" {
  type    = string
  default = "03:00"
}

variable "maintenance_day" {
  type    = number
  default = 7
}

variable "maintenance_hour" {
  type    = number
  default = 4
}

variable "labels" {
  type    = map(string)
  default = {}
}
