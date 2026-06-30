output "secret_ids" {
  value = {
    for k, v in google_secret_manager_secret.this :
    k => v.secret_id
  }
}

output "secret_versions" {
  value = {
    for k, v in google_secret_manager_secret_version.this :
    k => v.version
  }
}
