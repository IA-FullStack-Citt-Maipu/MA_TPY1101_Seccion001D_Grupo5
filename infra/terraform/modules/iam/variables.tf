variable "project_id" { type = string }
variable "environment" { type = string }
variable "service_account_name" { type = string }
variable "service_account_display_name" { type = string }
variable "roles" {
  type = list(string)
}

variable "service_account_user_members" {
  type    = list(string)
  default = []
}
