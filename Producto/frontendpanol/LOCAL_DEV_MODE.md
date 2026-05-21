# Modo Desarrollo Local

## Descripción

El proyecto está configurado en **modo desarrollo local** para permitir navegar libremente por la aplicación sin autenticación y guardar datos en archivos JSON locales.

## Características Activadas

### 1. Autenticación Desactivada ✅
- No es necesario hacer login
- Se asigna automáticamente el rol `DIRECTOR` (puedes cambiar esto en `src/App.tsx` línea 48)
- Acceso directo a todas las páginas

### 2. Base de Datos Local (JSON) ✅
- Los datos se guardan en `localStorage` del navegador
- Archivos JSON iniciales en `/public/db/`:
  - `categories.json` - Categorías de implementos
  - `implements.json` - Implementos/Items
  - `locations.json` - Ubicaciones de almacenamiento
  - `users.json` - Usuarios del sistema

### 3. Servicios Locales ✅
- Servicio `localDB.ts` maneja la persistencia en localStorage
- Servicio `categoryServiceLocal.ts` simula operaciones CRUD para categorías
- Los cambios se persisten entre refrescos de página

## Estructura de Datos

Los datos se almacenan en `localStorage` con la clave `panol_db_{colección}`:

```typescript
interface Categoria {
  id: string;
  name: string;
  description: string;
  createdAt: string;
}

interface Implement {
  id: string;
  name: string;
  categoryId: string;
  quantity: number;
  locationId: string;
  description: string;
  createdAt: string;
}

interface Location {
  id: string;
  name: string;
  floor: number;
  section: string;
  description: string;
  createdAt: string;
}
```

## Cambiar el Rol por Defecto

En `src/App.tsx` línea 48, cambia:
```typescript
const role = "DIRECTOR"; // Cambiar a "COORDINADOR" o "DOCENTE"
```

Opciones disponibles:
- `DIRECTOR` - Acceso a dashboard de director
- `COORDINADOR` - Acceso a inventario coordinador
- `DOCENTE` - Acceso a inventario docente

## Próximos Pasos

Una vez que el sistema esté funcionando y los datos se persistan correctamente:

1. **Mejorar UX/UI** - Según lo planeado
2. **Integrar Backend Real** - Reemplazar servicios locales con llamadas a API
3. **Configurar Autenticación Real** - Volver a activar el flujo de login

## Limpiar Datos Locales

Para borrar todos los datos guardados en localStorage:
```javascript
// En la consola del navegador:
Object.keys(localStorage).forEach(key => {
  if (key.startsWith('panol_db_')) localStorage.removeItem(key);
});
```

## Archivos Modificados

- `src/App.tsx` - Desactivó autenticación
- `src/hooks/useCategories.ts` - Usa servicio local
- `src/services/localDB.ts` - Nuevo servicio de DB local
- `src/services/categoryServiceLocal.ts` - Nuevo servicio de categorías local
- `public/db/*.json` - Archivos de datos iniciales
