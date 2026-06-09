# Secrets locales de databasepanol

No subas secretos reales al repositorio.

Uso recomendado:

- copia `../.env.example` a `../.env`;
- define ahi `POSTGRES_PASSWORD` con un valor local;
- usa la misma password en `Producto/backendpanol/.env.local` o en `Producto/backendpanol/secrets/application-secrets.properties` como `DB_DOCKER_PASSWORD`.

Si guardas archivos adicionales en esta carpeta, deben quedar fuera de git.
