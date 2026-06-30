# 🤝 Guía de Contribución

Gracias por contribuir 🚀  
Este repositorio sigue un flujo de trabajo estándar orientado a calidad, colaboración y buenas prácticas.

---

## 🌱 Flujo de trabajo

1. Crea una rama desde `dev`:
   ```bash
   git checkout -b tipo/descripcion-corta
   ```

2. Realiza tus cambios

3. Haz commit siguiendo la convención

4. Sube tu rama:

   ```bash
   git push origin tipo/descripcion-corta
   ```

5. Abre un Pull Request hacia `dev` (o `main` si el flujo del sprint lo define)

6. Espera:

   - ✔ Tests (GitHub Actions)
   - ✔ Revisión de al menos 1 persona

7. Una vez aprobado → merge

---

## 🌿 Convención de ramas

```text
tipo/descripcion
```

Ejemplos:

```text
feature/login
fix/error-token
refactor/api-users
chore/update-deps
```

---

## 🏷️ Convención de commits

Formato:

```text
tipo(scope): descripción
```

Tipos:

- feat: nueva funcionalidad
- fix: corrección de bug
- refactor: mejora interna
- docs: documentación
- test: tests
- ci: CI/CD
- chore: tareas varias

Ejemplos:

```text
feat(auth): login con JWT
fix(api): error en endpoint de usuarios
refactor(db): normalización de tablas
```

---

## 🔒 Reglas importantes

- ❌ No hacer push directo a `main` o `dev`
- ✔ Todo cambio debe ir por Pull Request
- ✔ Se requiere al menos 1 aprobación
- ✔ Los tests deben pasar antes del merge
- ✔ Resolver todos los comentarios del PR

---

## 🧪 Testing

Antes de abrir un PR:

- Ejecuta tests localmente
- Verifica que el proyecto compile
- Revisa errores o warnings

Comandos recomendados:

### Backend

```bash
cd Producto/backendpanol
./mvnw test
```

### Frontend

```bash
cd Producto/frontendpanol
npm ci
npm run build
```

---

## 🔐 Seguridad

- ❌ No subir:
  - `.env`
  - API keys
  - Tokens
- Usa variables de entorno o GitHub Secrets

---

## 📦 Buenas prácticas

- Mantén PR pequeños y claros
- Escribe código legible
- Comenta solo cuando sea necesario
- Sigue la estructura del proyecto

---

## 📌 Issues

- Usa templates para reportar bugs o features
- Describe claramente el problema o necesidad

---

## 💬 Comunicación

Si tienes dudas:

- Comenta en el PR
- Abre un issue
- Consulta al equipo

---

Gracias por contribuir al proyecto 💙
