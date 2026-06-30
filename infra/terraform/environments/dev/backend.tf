terraform {
  backend "gcs" {
    bucket = "REPLACE_ME_DEV_TFSTATE_BUCKET"
    prefix = "terraform/state"
  }
}
