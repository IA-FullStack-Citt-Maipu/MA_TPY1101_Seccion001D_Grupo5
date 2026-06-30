# Frontend Panol - Inventario (Categorías)

Aplicación React + Vite para la vista de **Gestión de Inventario** enfocada en **categorías**.

## Stack

- React 19 + TypeScript
- Vite
- Axios
- Lucide React

## Funcionalidad implementada

- Vista en español con layout tipo dashboard (topbar + sidebar + panel principal).
- CRUD de categorías conectado al backend:
  - Crear
  - Editar
  - Desactivar
  - Forzar desactivación cuando hay implementos activos asociados
  - Eliminar (solo si backend lo permite)
- Estados visuales:
  - Badge `Activa` / `Inactiva`
  - Error bajo el campo `Nombre` para duplicados
  - Mensajes de error globales

## Configuración

1. Copiar variables:

```bash
cp .env.example .env.local
```

2. Ajustar `VITE_API_BASE_URL` (por defecto `http://localhost:18080`).

## Ejecutar local

```bash
npm install
npm run dev
```

## Build

```bash
npm run build
npm run preview
```

## Docker

```bash
docker build -t panol-frontend .
docker run --rm -p 18081:80 panol-frontend
```

## Estructura

```text
src/
  components/
    categories/
    layout/
  hooks/
  pages/
  services/
  styles/
  types/
```

Documentación extendida en [`docs/FRONTEND.md`](./docs/FRONTEND.md).
