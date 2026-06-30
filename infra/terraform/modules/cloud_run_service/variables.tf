variable "project_id" { type = string }
variable "region" { type = string }
variable "service_name" { type = string }
variable "image" { type = string }
variable "container_port" { type = number }
variable "service_account_email" { type = string }
variable "allow_unauthenticated" {
  type    = bool
  default = true
}
variable "ingress" {
  type    = string
  default = "INGRESS_TRAFFIC_ALL"
}
variable "labels" {
  type    = map(string)
  default = {}
}
variable "env_vars" {
  type    = map(string)
  default = {}
}
variable "secret_env_vars" {
  type = map(object({
    secret  = string
    version = string
  }))
  default = {}
}
variable "min_instance_count" {
  type    = number
  default = 0
}
variable "max_instance_count" {
  type    = number
  default = 3
}
variable "timeout_seconds" {
  type    = number
  default = 300
}
variable "max_instance_request_concurrency" {
  type    = number
  default = 80
}
variable "custom_domain" {
  type    = string
  default = null
}
