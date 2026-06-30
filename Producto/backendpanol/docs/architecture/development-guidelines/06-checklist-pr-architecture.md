- Estado del documento: vigente
- Ultima verificacion: 2026-05-15
- Fuente de verdad: ver matriz canonica vigente y codigo fuente actual

# 06 - Checklist de PR (Arquitectura)

Usar este checklist antes de aprobar PR.

## Modularidad

- [ ] El cambio está dentro del módulo correcto.
- [ ] No hay imports de infraestructura de otro módulo.
- [ ] No hay acoplamiento lateral no documentado.

## Hexagonal

- [ ] `domain` no depende de framework.
- [ ] `application` usa puertos, no tecnologías concretas.
- [ ] `infrastructure` implementa interfaces del dominio.

## Datos y consistencia

- [ ] Escritura canónica en SQL está protegida por transacción.
- [ ] Eventos/auditoría no reemplazan estado canónico.
- [ ] Hay manejo de errores/reintentos para fallas cross-DB.

## API y seguridad

- [ ] Endpoints con autorización correcta.
- [ ] Errores de negocio usan `code` estable.

## Calidad

- [ ] Tests unitarios e integración suficientes.
- [ ] Documentación actualizada (si cambia arquitectura).
- [ ] Deudas técnicas explícitas con ticket.

