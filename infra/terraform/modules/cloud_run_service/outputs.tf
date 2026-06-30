output "service_name" {
  value = google_cloud_run_v2_service.this.name
}

output "service_uri" {
  value = google_cloud_run_v2_service.this.uri
}
