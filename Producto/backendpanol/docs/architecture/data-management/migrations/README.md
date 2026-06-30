# Registro de Migraciones Vigentes

- Estado del documento: vigente
- Ultima verificacion: 2026-06-29
- Fuente de verdad: `Producto/databasepanol/migrations/v25/`

## Cadena aplicada en el repositorio (baseline + refinamientos)

1. `V25__schema_alignment_big_bang.sql`
2. `V26__loan_request_notification_function.sql`
3. `V27__loan_status_enum_add_expired.sql`
4. `V29__loan_flow_data_refinement.sql`
5. `V30__loan_flow_operational_refinement.sql`
6. `V31__fix_loan_function_column_aliases.sql`
7. `V32__ensure_return_condition_enum_and_dedup_loan_history_index.sql`
8. `V33__auth_login_user_summary.sql`
9. `V34__auth_find_user_by_rut_support_normalized_input.sql`
10. `V35__drop_legacy_varchar_auth_lookup_function.sql`
11. `V36__ensure_loan_lifecycle_system_user.sql`
12. `V37__allow_partial_loan_delivery.sql`
13. `V38__token_revocation_cleanup_index.sql`
14. `V39__notification_inbox_v1.sql`
15. `V40__notification_role_routing_and_stock_alerts.sql`
16. `V41__email_outbox_resend_pipeline.sql`
17. `V42__fix_loan_request_notification_title.sql`
18. `V43__auto_reserved_loan_flow_notifications.sql`
19. `V44__loan_reserved_flow_consumable_closure.sql`
20. `V45__consumable_global_reservation_availability.sql`
21. `V46__loan_prepare_reserved_stock_reconciliation.sql`
22. `V47__loan_history_and_stock_reconciliation.sql`
23. `V48__implement_and_individual_accounting_fields.sql` - agrega campos contables a implementos e individuales para trazabilidad patrimonial.
24. `V49__email_templates_request_note_and_copy_cleanup.sql` - incorpora `request_note` y limpia el copy visible de correos de prestamo.
25. `V50__loan_status_email_copy_for_approved_and_prepared.sql` - ajusta asuntos y mensajes de docente para `approved` y `prepared`.
26. `V51__auto_reserved_docente_approved_email.sql` - habilita correo al docente cuando la solicitud nace auto-reservada en `approved`.
27. `V52__director_without_loan_notifications.sql` - excluye al director de notificaciones de prestamo y mantiene solo alertas de stock.

## Nota de versionado

- No existe un archivo `V28__*.sql` vigente en el repositorio actual.
- Si aparece una referencia historica a `V28__seed_health_school_rooms_subjects.sql`, debe tratarse como desalineacion documental y no como fuente activa.

## Indice disponible en docs

- [V25__schema_alignment_big_bang.md](V25__schema_alignment_big_bang.md)
- Para el detalle consolidado historico de esquema, revisar [../16-catalogo-bd-v31.md](../16-catalogo-bd-v31.md).

## Convencion

- Nuevas migraciones se agregan en `Producto/databasepanol/migrations/v25` con version mayor a `51`.
- No se editan migraciones ya aplicadas en ambientes compartidos.
- No se reintroduce cadena legacy previa al baseline V25.
