# Terraform Runbook (GCP)

## 1. Bootstrap (one-time per project/environment)

1. Create/select GCP project.
2. Enable required APIs:
   - run.googleapis.com
   - artifactregistry.googleapis.com
   - secretmanager.googleapis.com
   - iam.googleapis.com
   - cloudresourcemanager.googleapis.com
3. Create remote state bucket (per env):
   - `<project>-tfstate-dev`
   - `<project>-tfstate-prod`
4. Update `environments/*/backend.tf` with real bucket names.

## 2. Configure secrets

Create secrets in Secret Manager and add versions:

- DB_SUPABASE_PASSWORD
- JWT_ISSUER_URI
- VITE_SUPABASE_PUBLISHABLE_KEY

Example:

```bash
echo -n "<secret>" | gcloud secrets versions add DB_SUPABASE_PASSWORD --data-file=-
```

## 3. Deploy dev

```bash
cd infra/terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars
# edit values
terraform init
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

## 4. Promote to prod

1. Build and push versioned images for backend/frontend to prod registry.
2. Update `backend_image` and `frontend_image` in prod `terraform.tfvars`.
3. Run `plan` and `apply` in `environments/prod`.

## 5. Secret rotation

1. Add new secret version in Secret Manager.
2. Redeploy Cloud Run service (`terraform apply`) to pick latest.

## 6. DNS/domain mapping

After apply, configure DNS records as requested by Cloud Run domain mappings.
Managed TLS certs become active once DNS is propagated.

If you want to use only native Cloud Run URLs (`*.run.app`), set:

- `backend_domain = ""`
- `frontend_domain = ""`

in your `terraform.tfvars`. In that mode, domain mapping resources are skipped.

## 7. Observability

Use Cloud Logging and Cloud Monitoring dashboards for:
- request count
- error rates
- container restarts
- latency percentiles

## 8. Cost profile guidance

- For **dev**:
  - use `backend min_instance_count = 1` when login latency or cold starts affect UX.
  - `frontend min_instance_count = 0` is still reasonable when only the API needs to stay warm.
- For **prod**:
  - `min_instance_count = 0` is possible and valid when occasional cold starts are acceptable.
  - use `min_instance_count >= 1` for low-latency APIs or strict UX/SLA requirements.
