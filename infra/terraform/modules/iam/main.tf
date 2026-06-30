resource "google_service_account" "runtime" {
  project      = var.project_id
  account_id   = "${var.service_account_name}-${var.environment}"
  display_name = var.service_account_display_name
}

resource "google_project_iam_member" "runtime_roles" {
  for_each = toset(var.roles)
  project  = var.project_id
  role     = each.value
  member   = "serviceAccount:${google_service_account.runtime.email}"
}

resource "google_service_account_iam_member" "runtime_sa_user" {
  for_each           = toset(var.service_account_user_members)
  service_account_id = google_service_account.runtime.name
  role               = "roles/iam.serviceAccountUser"
  member             = each.value
}
