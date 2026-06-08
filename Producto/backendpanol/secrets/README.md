# Secrets policy

Store sensitive values in this folder and never commit real values.

Required file:
- `application-secrets.properties`: backend-only secrets loaded by Spring Boot (`spring.config.import`).

Example `application-secrets.properties`:
```properties
DB_DOCKER_PASSWORD=replace_me
DB_SUPABASE_PASSWORD=replace_me
# Optional if needed:
# JWT_ISSUER_URI=https://your-project.supabase.co/auth/v1
```

## Local PostgreSQL note

The local PostgreSQL container now lives in `Producto/databasepanol`.
Use the same password configured in `Producto/databasepanol/.env` when setting `DB_DOCKER_PASSWORD` here or in `.env.local`.
