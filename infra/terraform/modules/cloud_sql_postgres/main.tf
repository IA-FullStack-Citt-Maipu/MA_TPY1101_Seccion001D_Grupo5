resource "google_sql_database_instance" "this" {
  project             = var.project_id
  name                = var.instance_name
  region              = var.region
  database_version    = var.database_version
  deletion_protection = var.deletion_protection

  settings {
    edition           = var.edition
    tier              = var.tier
    availability_type = var.availability_type
    disk_type         = "PD_SSD"
    disk_size         = var.disk_size_gb
    disk_autoresize   = true

    backup_configuration {
      enabled                        = var.backups_enabled
      point_in_time_recovery_enabled = var.point_in_time_recovery_enabled
      start_time                     = var.backup_start_time
    }

    insights_config {
      query_insights_enabled  = var.query_insights_enabled
      query_string_length     = 2048
      record_application_tags = true
      record_client_address   = false
    }

    ip_configuration {
      ipv4_enabled = var.ipv4_enabled
    }

    maintenance_window {
      day          = var.maintenance_day
      hour         = var.maintenance_hour
      update_track = "stable"
    }

    user_labels = var.labels
  }
}

resource "google_sql_database" "app" {
  project  = var.project_id
  name     = var.database_name
  instance = google_sql_database_instance.this.name
}

resource "google_sql_user" "app" {
  project  = var.project_id
  name     = var.database_user
  instance = google_sql_database_instance.this.name
  password = var.database_password
}
