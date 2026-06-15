# Payloads usados en auditoría OWASP `staging-perf`

## SQL Injection
Usados sobre `GET /api/v2/implements?name=...`

```text
' OR '1'='1
' OR 1=1--
1; DROP TABLE implemento;--
' UNION SELECT username,password FROM usuario--
```

## RLS / IDOR
- Se creó o reutilizó un `Docente B` de prueba en `staging-perf`.
- Ese usuario generó un préstamo propio.
- Luego `Docente A` intentó acceder al UUID ajeno vía backend y vía frontend autenticado.
- El UUID del préstamo creado quedó registrado en:
  - `evidencias/02-seguridad/capturas/staging-perf-rls-idor-results.json`

## JWT manipulado
No se almacenaron tokens crudos en evidencia. Las pruebas realizadas fueron:

```text
1. Tomar un JWT válido de sesión QA
2. Modificar el claim "role" a "DIRECTOR" sin recalcular firma
3. Enviar el token adulterado a un endpoint protegido de director
4. Construir una variante con header {"alg":"none","typ":"JWT"}
5. Reenviar el token sin firma válida
```

## Sesión / cookies
Se validó que las cookies emitidas por login queden con atributos:

```text
Secure
HttpOnly
SameSite=None
```

La evidencia ya saneada quedó en:

- `evidencias/02-seguridad/capturas/staging-perf-crypto-auth-session.json`
- `evidencias/02-seguridad/capturas/frontend-staging-perf-network-security.json`

## Notas de seguridad de la evidencia
- No se dejaron tokens, cookies ni contraseñas crudas dentro de los archivos Markdown.
- Los artefactos con headers o sesión fueron saneados para preservar solo flags y metadata útil.
