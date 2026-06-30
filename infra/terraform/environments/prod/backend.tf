terraform {
  backend "gcs" {
    bucket = "REPLACE_ME_PROD_TFSTATE_BUCKET"
    prefix = "terraform/state"
  }
}
