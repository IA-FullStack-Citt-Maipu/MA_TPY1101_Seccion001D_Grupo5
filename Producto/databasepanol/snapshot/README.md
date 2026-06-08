# Snapshot de Supabase

- Estado del documento: vigente
- Ultima verificacion: 2026-06-07
- Proyecto origen: `panol-dev`
- Alcance actual: esquema `public`

## Objetivo

Esta carpeta guarda una fotografia estructural del esquema de aplicacion actual en Supabase, separada de la cadena de migraciones.

Se usa para:

- revisar diferencias entre la BD viva y la base versionada;
- reconstruir el esquema localmente por tipo de objeto;
- tener una referencia exacta de tablas, funciones, vistas, constraints, indices, triggers, RLS y policies del `public` actual.

## Contenido

- `public-schema-full.sql`: dump raw schema-only del `public` actual.
- `public/`: version separada por concern (`types`, `functions`, `tables`, `views`, `constraints`, `indexes`, `triggers`, `policies`, etc.).

## Alcance deliberado

Este snapshot versiona el esquema de aplicacion `public`.

No versiona schemas gestionados por la plataforma Supabase, por ejemplo:

- `auth`
- `storage`
- `graphql_public`
- `extensions`
- objetos internos de administracion o replicacion

Motivo: esos componentes dependen de la plataforma administrada y no forman parte del contrato SQL propio del sistema Pa?ol Salud.
