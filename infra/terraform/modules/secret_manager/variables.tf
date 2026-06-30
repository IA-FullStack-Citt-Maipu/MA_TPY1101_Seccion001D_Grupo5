variable "project_id" { type = string }
variable "secrets" {
  type = list(string)
}

variable "secret_values" {
  description = "Map of secret_id => secret value to create a new secret version."
  type        = map(string)
  default     = {}
  sensitive   = true
}
