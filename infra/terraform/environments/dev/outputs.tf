output "backend_url" {
  value = module.backend_service.service_uri
}

output "frontend_url" {
  value = module.frontend_service.service_uri
}

output "bot_service_uri" {
  value = module.bot_service.service_uri
}

output "backend_service_name" {
  value = module.backend_service.service_name
}

output "bot_service_name" {
  value = module.bot_service.service_name
}

output "frontend_service_name" {
  value = module.frontend_service.service_name
}

output "bot_custom_domain" {
  value = var.bot_domain
}

output "artifact_registry_repository" {
  value = module.artifact_registry.repository_name
}

output "secret_ids" {
  value = module.secret_manager.secret_ids
}
