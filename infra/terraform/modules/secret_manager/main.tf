resource "google_secret_manager_secret" "this" {
  for_each = toset(var.secrets)

  project   = var.project_id
  secret_id = each.value

  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "this" {
  for_each = {
    for secret_id in keys(nonsensitive(var.secret_values)) :
    secret_id => secret_id
    if contains(var.secrets, secret_id) && trimspace(nonsensitive(var.secret_values[secret_id])) != ""
  }

  secret      = google_secret_manager_secret.this[each.key].id
  secret_data = var.secret_values[each.key]
}
