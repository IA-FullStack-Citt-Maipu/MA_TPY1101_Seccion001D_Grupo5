output "backend_url" {
  value = module.backend_service.service_uri
}

output "frontend_url" {
  value = module.frontend_service.service_uri
}

output "backend_service_name" {
  value = module.backend_service.service_name
}

output "frontend_service_name" {
  value = module.frontend_service.service_name
}

output "artifact_registry_repository" {
  value = module.artifact_registry.repository_name
}

output "secret_ids" {
  value = module.secret_manager.secret_ids
}

output "cloudsql_instance_name" {
  value = module.cloud_sql.instance_name
}

output "cloudsql_connection_name" {
  value = module.cloud_sql.connection_name
}

output "cloudsql_public_ip_address" {
  value = module.cloud_sql.public_ip_address
}
