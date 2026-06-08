# Catalogo Completo de Base de Datos (Supabase)

- Estado del documento: vigente
- Ultima verificacion: 2026-05-31
- Fuente de verdad: esquema public introspectado historicamente en Supabase + Flyway hasta V31
- Rama de referencia: feature/db-loan-flow-refinement

## 1. Resumen Ejecutivo

- Tablas base: 22
- Vistas: 8
- Enums: 8
- Funciones (incluye trigger functions): 30
- Triggers: 10
- Indices: 62
- Politicas RLS: 14

## 2. Migraciones Canonicas Aplicadas

| Version | Script | Installed On | Success |
|---|---|---|---|
| 25 | V25__schema_alignment_big_bang.sql | 2026-05-17 06:22:03.694386 | t |
| 26 | V26__loan_request_notification_function.sql | 2026-05-22 02:07:57.052416 | t |
| 27 | V27__loan_status_enum_add_expired.sql | 2026-05-22 04:21:24.067135 | t |
| 28 | V28__seed_health_school_rooms_subjects.sql *(historico, sin archivo versionado vigente en el repo actual)* | 2026-05-24 04:29:10.174546 | t |
| 29 | V29__loan_flow_data_refinement.sql | 2026-05-31 05:21:20.159917 | t |
| 30 | V30__loan_flow_operational_refinement.sql | 2026-05-31 06:58:41.451196 | t |
| 31 | V31__fix_loan_function_column_aliases.sql | 2026-05-31 18:45:55.491277 | t |

## 3. Enums Vigentes

### 3.1 individual_allocation_status_enum

| Orden | Valor |
|---|---|
| 1 | reserved |
| 2 | prepared |
| 3 | delivered |
| 4 | returned |
| 5 | cancelled |

### 3.2 individual_condition_enum

| Orden | Valor |
|---|---|
| 1 | good |
| 2 | damaged_repairable |
| 3 | damaged_no_diagnosis |
| 4 | irreparable |

### 3.3 individual_status_enum

| Orden | Valor |
|---|---|
| 1 | available |
| 2 | loaned |
| 3 | maintenance |
| 4 | damaged |
| 5 | blocked |
| 6 | retired |

### 3.4 inventory_movement_type_enum

| Orden | Valor |
|---|---|
| 1 | stock_in |
| 2 | stock_out |
| 3 | loan_delivery |
| 4 | loan_return |
| 5 | damage_report |
| 6 | manual_adjustment |
| 7 | consumption |
| 8 | discard |
| 9 | loss |

### 3.5 item_type_enum

| Orden | Valor |
|---|---|
| 1 | consumable |
| 2 | reusable |
| 3 | individual |

### 3.6 loan_status_enum

| Orden | Valor |
|---|---|
| 1 | pending |
| 2 | approved |
| 3 | prepared |
| 4 | delivered |
| 5 | completed |
| 6 | rejected |
| 7 | cancelled |
| 8 | expired |
| 9 | overdue |

### 3.7 outbox_status_enum

| Orden | Valor |
|---|---|
| 1 | PENDING |
| 2 | PROCESSING |
| 3 | SENT |
| 4 | FAILED |

### 3.8 return_condition_enum

| Orden | Valor |
|---|---|
| 1 | good |
| 2 | damaged |
| 3 | lost |
| 4 | discarded |

## 4. Tablas Base (estructura y reglas)

### 4.1 public.audit_log

- Que trata: Bitacora de auditoria de eventos de seguridad y negocio.
- RLS: enabled=t, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('audit_log_id_seq'::regclass) |
| 2 | event | character varying | NO |  |
| 3 | payload | jsonb | NO |  |
| 4 | actor_user_id | bigint | YES |  |
| 5 | target_user_id | bigint | YES |  |
| 6 | created_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| FOREIGN KEY | fk_audit_actor_user | FOREIGN KEY (actor_user_id) REFERENCES "user"(id) ON DELETE SET NULL |
| FOREIGN KEY | fk_audit_target_user | FOREIGN KEY (target_user_id) REFERENCES "user"(id) ON DELETE SET NULL |
| PRIMARY KEY | audit_log_pkey | PRIMARY KEY (id) |

**Indices**

| Nombre | Definicion |
|---|---|
| audit_log_pkey | CREATE UNIQUE INDEX audit_log_pkey ON public.audit_log USING btree (id) |

### 4.2 public.career

- Que trata: Catalogo de carreras academicas.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('career_id_seq'::regclass) |
| 2 | uuid | uuid | NO | gen_random_uuid() |
| 3 | code | character varying | NO |  |
| 4 | name | character varying | NO |  |
| 5 | active | boolean | NO | true |
| 6 | created_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| PRIMARY KEY | career_pkey | PRIMARY KEY (id) |
| UNIQUE | career_code_key | UNIQUE (code) |
| UNIQUE | career_uuid_key | UNIQUE (uuid) |

**Indices**

| Nombre | Definicion |
|---|---|
| career_code_key | CREATE UNIQUE INDEX career_code_key ON public.career USING btree (code) |
| career_pkey | CREATE UNIQUE INDEX career_pkey ON public.career USING btree (id) |
| career_uuid_key | CREATE UNIQUE INDEX career_uuid_key ON public.career USING btree (uuid) |

### 4.3 public.category

- Que trata: Catalogo de categorias de implementos.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('category_id_seq'::regclass) |
| 2 | uuid | uuid | NO | gen_random_uuid() |
| 3 | name | character varying | NO |  |
| 4 | description | text | YES |  |
| 5 | active | boolean | NO | true |
| 6 | created_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| PRIMARY KEY | category_pkey | PRIMARY KEY (id) |
| UNIQUE | category_name_key | UNIQUE (name) |
| UNIQUE | category_uuid_key | UNIQUE (uuid) |

**Indices**

| Nombre | Definicion |
|---|---|
| category_name_key | CREATE UNIQUE INDEX category_name_key ON public.category USING btree (name) |
| category_pkey | CREATE UNIQUE INDEX category_pkey ON public.category USING btree (id) |
| category_uuid_key | CREATE UNIQUE INDEX category_uuid_key ON public.category USING btree (uuid) |

### 4.4 public.email_outbox

- Que trata: Cola transaccional para envio de correos.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('email_outbox_id_seq'::regclass) |
| 2 | event_id | uuid | NO | gen_random_uuid() |
| 3 | recipient_email | character varying | NO |  |
| 4 | email_type | character varying | NO |  |
| 5 | template_data | jsonb | NO |  |
| 6 | status | outbox_status_enum | NO | 'PENDING'::outbox_status_enum |
| 7 | retry_count | integer | NO | 0 |
| 8 | error_log | text | YES |  |
| 9 | occurred_at | timestamp with time zone | NO | now() |
| 10 | processed_at | timestamp with time zone | YES |  |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| PRIMARY KEY | email_outbox_pkey | PRIMARY KEY (id) |
| UNIQUE | email_outbox_event_id_key | UNIQUE (event_id) |

**Indices**

| Nombre | Definicion |
|---|---|
| email_outbox_event_id_key | CREATE UNIQUE INDEX email_outbox_event_id_key ON public.email_outbox USING btree (event_id) |
| email_outbox_pkey | CREATE UNIQUE INDEX email_outbox_pkey ON public.email_outbox USING btree (id) |

### 4.5 public.flyway_schema_history

- Que trata: Historial de migraciones Flyway aplicadas.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | installed_rank | integer | NO |  |
| 2 | version | character varying | YES |  |
| 3 | description | character varying | NO |  |
| 4 | type | character varying | NO |  |
| 5 | script | character varying | NO |  |
| 6 | checksum | integer | YES |  |
| 7 | installed_by | character varying | NO |  |
| 8 | installed_on | timestamp without time zone | NO | now() |
| 9 | execution_time | integer | NO |  |
| 10 | success | boolean | NO |  |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| PRIMARY KEY | flyway_schema_history_pk | PRIMARY KEY (installed_rank) |

**Indices**

| Nombre | Definicion |
|---|---|
| flyway_schema_history_pk | CREATE UNIQUE INDEX flyway_schema_history_pk ON public.flyway_schema_history USING btree (installed_rank) |
| flyway_schema_history_s_idx | CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success) |

### 4.6 public.implement

- Que trata: Maestro de implementos (consumable/reusable/individual).
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('implement_id_seq'::regclass) |
| 2 | uuid | uuid | NO | gen_random_uuid() |
| 3 | category_id | bigint | YES |  |
| 4 | location_id | bigint | YES |  |
| 5 | name | character varying | NO |  |
| 6 | description | text | YES |  |
| 7 | item_type | item_type_enum | NO | 'reusable'::item_type_enum |
| 8 | barcode | character varying | YES |  |
| 9 | img_url | text | YES |  |
| 10 | active | boolean | NO | true |
| 11 | observations | text | YES |  |
| 12 | created_at | timestamp with time zone | NO | now() |
| 13 | updated_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| FOREIGN KEY | fk_implement_category | FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE RESTRICT |
| FOREIGN KEY | fk_implement_location | FOREIGN KEY (location_id) REFERENCES location(id) ON DELETE RESTRICT |
| PRIMARY KEY | implement_pkey | PRIMARY KEY (id) |
| UNIQUE | implement_barcode_key | UNIQUE (barcode) |
| UNIQUE | implement_name_category_key | UNIQUE (name, category_id) |
| UNIQUE | implement_uuid_key | UNIQUE (uuid) |

**Indices**

| Nombre | Definicion |
|---|---|
| implement_barcode_key | CREATE UNIQUE INDEX implement_barcode_key ON public.implement USING btree (barcode) |
| implement_name_category_key | CREATE UNIQUE INDEX implement_name_category_key ON public.implement USING btree (name, category_id) |
| implement_pkey | CREATE UNIQUE INDEX implement_pkey ON public.implement USING btree (id) |
| implement_uuid_key | CREATE UNIQUE INDEX implement_uuid_key ON public.implement USING btree (uuid) |

### 4.7 public.individual

- Que trata: Unidades fisicas individualizadas de implementos tipo individual.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('individual_id_seq'::regclass) |
| 2 | uuid | uuid | NO | gen_random_uuid() |
| 3 | implement_id | bigint | NO |  |
| 4 | current_location_id | bigint | YES |  |
| 5 | asset_code | character varying | NO |  |
| 6 | status | individual_status_enum | NO | 'available'::individual_status_enum |
| 7 | condition | individual_condition_enum | NO | 'good'::individual_condition_enum |
| 8 | notes | text | YES |  |
| 9 | active | boolean | NO | true |
| 10 | created_at | timestamp with time zone | NO | now() |
| 11 | updated_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| FOREIGN KEY | fk_individual_implement | FOREIGN KEY (implement_id) REFERENCES implement(id) ON DELETE CASCADE |
| FOREIGN KEY | fk_individual_location | FOREIGN KEY (current_location_id) REFERENCES location(id) ON DELETE RESTRICT |
| PRIMARY KEY | individual_pkey | PRIMARY KEY (id) |
| UNIQUE | individual_asset_code_key | UNIQUE (asset_code) |
| UNIQUE | individual_uuid_key | UNIQUE (uuid) |

**Indices**

| Nombre | Definicion |
|---|---|
| idx_individual_implement_status | CREATE INDEX idx_individual_implement_status ON public.individual USING btree (implement_id, status) |
| individual_asset_code_key | CREATE UNIQUE INDEX individual_asset_code_key ON public.individual USING btree (asset_code) |
| individual_pkey | CREATE UNIQUE INDEX individual_pkey ON public.individual USING btree (id) |
| individual_uuid_key | CREATE UNIQUE INDEX individual_uuid_key ON public.individual USING btree (uuid) |

### 4.8 public.inventory_movement

- Que trata: Movimientos auditables de inventario.
- RLS: enabled=t, forced=f
- Politicas RLS activas:
  - inventory_movement_insert_staff [INSERT] roles={public} qual= with_check=((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[]))
  - inventory_movement_select_staff [SELECT] roles={public} qual=((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[])) with_check=

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('inventory_movement_id_seq'::regclass) |
| 2 | implement_id | bigint | NO |  |
| 3 | actor_user_id | bigint | NO |  |
| 4 | movement_type | inventory_movement_type_enum | NO |  |
| 5 | quantity | integer | NO |  |
| 6 | delta_changes | jsonb | NO |  |
| 7 | systemic_metadata | jsonb | YES |  |
| 8 | created_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| CHECK | inventory_movement_qty_nonzero | CHECK (quantity <> 0) |
| FOREIGN KEY | fk_inv_movement_actor | FOREIGN KEY (actor_user_id) REFERENCES "user"(id) ON DELETE RESTRICT |
| FOREIGN KEY | fk_inv_movement_implement | FOREIGN KEY (implement_id) REFERENCES implement(id) ON DELETE RESTRICT |
| PRIMARY KEY | inventory_movement_pkey | PRIMARY KEY (id) |

**Indices**

| Nombre | Definicion |
|---|---|
| idx_inventory_movement_implement_date | CREATE INDEX idx_inventory_movement_implement_date ON public.inventory_movement USING btree (implement_id, created_at DESC) |
| inventory_movement_pkey | CREATE UNIQUE INDEX inventory_movement_pkey ON public.inventory_movement USING btree (id) |

### 4.9 public.loan

- Que trata: Cabecera de solicitud/prestamo y su estado actual.
- RLS: enabled=t, forced=f
- Politicas RLS activas:
  - loan_all_staff [ALL] roles={public} qual=((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[])) with_check=((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[]))
  - loan_insert_own [INSERT] roles={public} qual= with_check=(requester_id = get_current_user_id())
  - loan_select_own [SELECT] roles={public} qual=(requester_id = get_current_user_id()) with_check=

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('loan_id_seq'::regclass) |
| 2 | uuid | uuid | NO | gen_random_uuid() |
| 3 | requester_id | bigint | NO |  |
| 4 | room_id | bigint | YES |  |
| 5 | subject_id | bigint | YES |  |
| 6 | status | loan_status_enum | NO | 'pending'::loan_status_enum |
| 10 | scheduled_at | timestamp with time zone | NO |  |
| 15 | created_at | timestamp with time zone | NO | now() |
| 16 | expected_return_at | timestamp with time zone | NO |  |
| 17 | updated_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| CHECK | chk_loan_expected_return_after_scheduled | CHECK (expected_return_at > scheduled_at) |
| FOREIGN KEY | fk_loan_requester | FOREIGN KEY (requester_id) REFERENCES "user"(id) ON DELETE RESTRICT |
| FOREIGN KEY | fk_loan_room | FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE RESTRICT |
| FOREIGN KEY | fk_loan_subject | FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE RESTRICT |
| PRIMARY KEY | loan_pkey | PRIMARY KEY (id) |
| UNIQUE | loan_uuid_key | UNIQUE (uuid) |

**Indices**

| Nombre | Definicion |
|---|---|
| idx_loan_requester_status | CREATE INDEX idx_loan_requester_status ON public.loan USING btree (requester_id, status) |
| idx_loan_schedule_range | CREATE INDEX idx_loan_schedule_range ON public.loan USING btree (scheduled_at, expected_return_at) |
| idx_loan_status_scheduled | CREATE INDEX idx_loan_status_scheduled ON public.loan USING btree (status, scheduled_at) |
| loan_pkey | CREATE UNIQUE INDEX loan_pkey ON public.loan USING btree (id) |
| loan_uuid_key | CREATE UNIQUE INDEX loan_uuid_key ON public.loan USING btree (uuid) |

### 4.10 public.loan_detail

- Que trata: Detalle por implemento solicitado en cada prestamo.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | loan_id | bigint | NO |  |
| 2 | implement_id | bigint | NO |  |
| 3 | requested_quantity | integer | NO |  |
| 4 | reserved_quantity | integer | NO | 0 |
| 5 | delivered_quantity | integer | NO | 0 |
| 6 | returned_quantity | integer | NO | 0 |
| 7 | damaged_quantity | integer | NO | 0 |
| 8 | lost_quantity | integer | NO | 0 |
| 9 | consumed_quantity | integer | NO | 0 |
| 10 | discarded_quantity | integer | NO | 0 |
| 11 | return_notes | text | YES |  |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| CHECK | chk_ld_consumed_qty_nonnegative | CHECK (consumed_quantity >= 0) |
| CHECK | chk_ld_damaged_qty_nonnegative | CHECK (damaged_quantity >= 0) |
| CHECK | chk_ld_delivered_lte_reserved | CHECK (delivered_quantity <= reserved_quantity) |
| CHECK | chk_ld_discarded_qty_nonnegative | CHECK (discarded_quantity >= 0) |
| CHECK | chk_ld_lost_qty_nonnegative | CHECK (lost_quantity >= 0) |
| CHECK | chk_ld_reserved_lte_requested | CHECK (reserved_quantity <= requested_quantity) |
| CHECK | chk_ld_return_breakdown_lte_delivered | CHECK ((returned_quantity + damaged_quantity + lost_quantity + consumed_quantity + discarded_quantity) <= delivered_quantity) |
| CHECK | chk_ld_returned_qty_nonnegative | CHECK (returned_quantity >= 0) |
| CHECK | loan_detail_delivered_qty_check | CHECK (delivered_quantity >= 0) |
| CHECK | loan_detail_requested_qty_check | CHECK (requested_quantity > 0) |
| CHECK | loan_detail_reserved_qty_check | CHECK (reserved_quantity >= 0) |
| FOREIGN KEY | fk_loan_detail_implement | FOREIGN KEY (implement_id) REFERENCES implement(id) ON DELETE RESTRICT |
| FOREIGN KEY | fk_loan_detail_loan | FOREIGN KEY (loan_id) REFERENCES loan(id) ON DELETE CASCADE |
| PRIMARY KEY | loan_detail_pkey | PRIMARY KEY (loan_id, implement_id) |

**Indices**

| Nombre | Definicion |
|---|---|
| idx_loan_detail_implement_loan | CREATE INDEX idx_loan_detail_implement_loan ON public.loan_detail USING btree (implement_id, loan_id) |
| loan_detail_pkey | CREATE UNIQUE INDEX loan_detail_pkey ON public.loan_detail USING btree (loan_id, implement_id) |

### 4.11 public.loan_detail_individual

- Que trata: Asignacion de unidades fisicas a una linea de prestamo.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | loan_id | bigint | NO |  |
| 2 | implement_id | bigint | NO |  |
| 3 | individual_id | bigint | NO |  |
| 4 | return_condition | return_condition_enum | YES |  |
| 5 | returned_at | timestamp with time zone | YES |  |
| 6 | allocation_status | individual_allocation_status_enum | NO | 'delivered'::individual_allocation_status_enum |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| FOREIGN KEY | fk_loan_detail_individual_parent | FOREIGN KEY (loan_id, implement_id) REFERENCES loan_detail(loan_id, implement_id) ON DELETE CASCADE |
| FOREIGN KEY | fk_loan_detail_individual_unit | FOREIGN KEY (individual_id) REFERENCES individual(id) ON DELETE RESTRICT |
| PRIMARY KEY | loan_detail_individual_pkey | PRIMARY KEY (loan_id, implement_id, individual_id) |

**Indices**

| Nombre | Definicion |
|---|---|
| loan_detail_individual_pkey | CREATE UNIQUE INDEX loan_detail_individual_pkey ON public.loan_detail_individual USING btree (loan_id, implement_id, individual_id) |

### 4.12 public.loan_status_history

- Que trata: Historial de transiciones de estado del prestamo.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('loan_status_history_id_seq'::regclass) |
| 2 | loan_id | bigint | NO |  |
| 3 | actor_user_id | bigint | NO |  |
| 4 | from_status | loan_status_enum | YES |  |
| 5 | to_status | loan_status_enum | NO |  |
| 6 | notes | text | YES |  |
| 7 | changed_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| FOREIGN KEY | fk_loan_history_actor | FOREIGN KEY (actor_user_id) REFERENCES "user"(id) ON DELETE RESTRICT |
| FOREIGN KEY | fk_loan_history_loan | FOREIGN KEY (loan_id) REFERENCES loan(id) ON DELETE CASCADE |
| PRIMARY KEY | loan_status_history_pkey | PRIMARY KEY (id) |

**Indices**

| Nombre | Definicion |
|---|---|
| idx_loan_history_loan_changed | CREATE INDEX idx_loan_history_loan_changed ON public.loan_status_history USING btree (loan_id, changed_at DESC) |
| idx_loan_history_to_status | CREATE INDEX idx_loan_history_to_status ON public.loan_status_history USING btree (loan_id, to_status, changed_at DESC) |
| idx_loan_status_history_loan_changed_at | CREATE INDEX idx_loan_status_history_loan_changed_at ON public.loan_status_history USING btree (loan_id, changed_at DESC) |
| idx_loan_status_history_to_status | CREATE INDEX idx_loan_status_history_to_status ON public.loan_status_history USING btree (to_status) |
| loan_status_history_pkey | CREATE UNIQUE INDEX loan_status_history_pkey ON public.loan_status_history USING btree (id) |

### 4.13 public.location

- Que trata: Catalogo de ubicaciones logisticas.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('location_id_seq'::regclass) |
| 2 | uuid | uuid | NO | gen_random_uuid() |
| 3 | name | character varying | NO |  |
| 4 | description | text | YES |  |
| 5 | location_type | character varying | NO | 'PANOL_SHELF'::character varying |
| 6 | active | boolean | NO | true |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| PRIMARY KEY | location_pkey | PRIMARY KEY (id) |
| UNIQUE | location_name_key | UNIQUE (name) |
| UNIQUE | location_uuid_key | UNIQUE (uuid) |

**Indices**

| Nombre | Definicion |
|---|---|
| location_name_key | CREATE UNIQUE INDEX location_name_key ON public.location USING btree (name) |
| location_pkey | CREATE UNIQUE INDEX location_pkey ON public.location USING btree (id) |
| location_uuid_key | CREATE UNIQUE INDEX location_uuid_key ON public.location USING btree (uuid) |

### 4.14 public.notification

- Que trata: Notificaciones de usuario.
- RLS: enabled=t, forced=f
- Politicas RLS activas:
  - notification_all_director [ALL] roles={public} qual=((get_current_user_role())::text = 'director'::text) with_check=((get_current_user_role())::text = 'director'::text)
  - notification_all_own [ALL] roles={public} qual=(user_id = get_current_user_id()) with_check=(user_id = get_current_user_id())

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('notification_id_seq'::regclass) |
| 2 | uuid | uuid | NO | gen_random_uuid() |
| 3 | user_id | bigint | NO |  |
| 4 | title | character varying | NO |  |
| 5 | message | text | NO |  |
| 6 | read_status | boolean | NO | false |
| 7 | created_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| FOREIGN KEY | fk_notification_user | FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE |
| PRIMARY KEY | notification_pkey | PRIMARY KEY (id) |
| UNIQUE | notification_uuid_key | UNIQUE (uuid) |

**Indices**

| Nombre | Definicion |
|---|---|
| idx_notification_user_read | CREATE INDEX idx_notification_user_read ON public.notification USING btree (user_id, read_status, created_at DESC) |
| notification_pkey | CREATE UNIQUE INDEX notification_pkey ON public.notification USING btree (id) |
| notification_uuid_key | CREATE UNIQUE INDEX notification_uuid_key ON public.notification USING btree (uuid) |

### 4.15 public.outbox_event

- Que trata: Outbox de eventos de dominio para integraciones.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('outbox_event_id_seq'::regclass) |
| 2 | event_id | uuid | NO | gen_random_uuid() |
| 3 | aggregate_type | character varying | NO |  |
| 4 | aggregate_id | uuid | YES |  |
| 5 | event_type | character varying | NO |  |
| 6 | payload | text | NO |  |
| 7 | status | outbox_status_enum | NO | 'PENDING'::outbox_status_enum |
| 8 | retry_count | integer | NO | 0 |
| 9 | occurred_at | timestamp with time zone | NO | now() |
| 10 | processed_at | timestamp with time zone | YES |  |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| PRIMARY KEY | outbox_event_pkey | PRIMARY KEY (id) |
| UNIQUE | outbox_event_id_key | UNIQUE (event_id) |

**Indices**

| Nombre | Definicion |
|---|---|
| outbox_event_id_key | CREATE UNIQUE INDEX outbox_event_id_key ON public.outbox_event USING btree (event_id) |
| outbox_event_pkey | CREATE UNIQUE INDEX outbox_event_pkey ON public.outbox_event USING btree (id) |

### 4.16 public.role

- Que trata: Catalogo de roles de usuario.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('role_id_seq'::regclass) |
| 2 | uuid | uuid | NO | gen_random_uuid() |
| 3 | name | character varying | NO |  |
| 4 | description | character varying | YES |  |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| PRIMARY KEY | role_pkey | PRIMARY KEY (id) |
| UNIQUE | role_name_key | UNIQUE (name) |
| UNIQUE | role_uuid_key | UNIQUE (uuid) |

**Indices**

| Nombre | Definicion |
|---|---|
| role_name_key | CREATE UNIQUE INDEX role_name_key ON public.role USING btree (name) |
| role_pkey | CREATE UNIQUE INDEX role_pkey ON public.role USING btree (id) |
| role_uuid_key | CREATE UNIQUE INDEX role_uuid_key ON public.role USING btree (uuid) |

### 4.17 public.room

- Que trata: Catalogo de salas/laboratorios.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('room_id_seq'::regclass) |
| 2 | uuid | uuid | NO | gen_random_uuid() |
| 3 | name | character varying | NO |  |
| 4 | description | text | YES |  |
| 5 | active | boolean | NO | true |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| PRIMARY KEY | room_pkey | PRIMARY KEY (id) |
| UNIQUE | room_name_key | UNIQUE (name) |
| UNIQUE | room_uuid_key | UNIQUE (uuid) |

**Indices**

| Nombre | Definicion |
|---|---|
| room_name_key | CREATE UNIQUE INDEX room_name_key ON public.room USING btree (name) |
| room_pkey | CREATE UNIQUE INDEX room_pkey ON public.room USING btree (id) |
| room_uuid_key | CREATE UNIQUE INDEX room_uuid_key ON public.room USING btree (uuid) |

### 4.18 public.stock

- Que trata: Contadores de stock por implemento.
- RLS: enabled=t, forced=f
- Politicas RLS activas:
  - stock_modify_staff [ALL] roles={public} qual=((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[])) with_check=((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[]))
  - stock_select_authenticated [SELECT] roles={public} qual=is_authenticated() with_check=

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('stock_id_seq'::regclass) |
| 2 | implement_id | bigint | NO |  |
| 3 | total_stock | integer | NO | 0 |
| 4 | min_stock | integer | NO | 0 |
| 5 | available | integer | NO | 0 |
| 6 | reserved | integer | NO | 0 |
| 7 | loaned | integer | NO | 0 |
| 8 | damaged | integer | NO | 0 |
| 9 | updated_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| CHECK | chk_stock_counters_integrity | CHECK ((available + reserved + loaned + damaged) = total_stock) |
| CHECK | stock_available_check | CHECK (available >= 0) |
| CHECK | stock_damaged_check | CHECK (damaged >= 0) |
| CHECK | stock_loaned_check | CHECK (loaned >= 0) |
| CHECK | stock_min_stock_check | CHECK (min_stock >= 0) |
| CHECK | stock_reserved_check | CHECK (reserved >= 0) |
| CHECK | stock_total_stock_check | CHECK (total_stock >= 0) |
| FOREIGN KEY | fk_stock_implement | FOREIGN KEY (implement_id) REFERENCES implement(id) ON DELETE CASCADE |
| PRIMARY KEY | stock_pkey | PRIMARY KEY (id) |
| UNIQUE | stock_implement_id_key | UNIQUE (implement_id) |

**Indices**

| Nombre | Definicion |
|---|---|
| stock_implement_id_key | CREATE UNIQUE INDEX stock_implement_id_key ON public.stock USING btree (implement_id) |
| stock_pkey | CREATE UNIQUE INDEX stock_pkey ON public.stock USING btree (id) |

### 4.19 public.subject

- Que trata: Catalogo de asignaturas.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('subject_id_seq'::regclass) |
| 2 | uuid | uuid | NO | gen_random_uuid() |
| 3 | code | character varying | NO |  |
| 4 | name | character varying | NO |  |
| 5 | active | boolean | NO | true |
| 6 | created_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| PRIMARY KEY | subject_pkey | PRIMARY KEY (id) |
| UNIQUE | subject_code_key | UNIQUE (code) |
| UNIQUE | subject_uuid_key | UNIQUE (uuid) |

**Indices**

| Nombre | Definicion |
|---|---|
| subject_code_key | CREATE UNIQUE INDEX subject_code_key ON public.subject USING btree (code) |
| subject_pkey | CREATE UNIQUE INDEX subject_pkey ON public.subject USING btree (id) |
| subject_uuid_key | CREATE UNIQUE INDEX subject_uuid_key ON public.subject USING btree (uuid) |

### 4.20 public.token_revocation

- Que trata: Tokens JWT revocados.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('token_revocation_id_seq'::regclass) |
| 2 | user_id | bigint | NO |  |
| 3 | jti | character varying | NO |  |
| 4 | expires_at | timestamp with time zone | NO |  |
| 5 | created_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| FOREIGN KEY | fk_token_revocation_user | FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE |
| PRIMARY KEY | token_revocation_pkey | PRIMARY KEY (id) |
| UNIQUE | token_revocation_jti_key | UNIQUE (jti) |

**Indices**

| Nombre | Definicion |
|---|---|
| token_revocation_jti_key | CREATE UNIQUE INDEX token_revocation_jti_key ON public.token_revocation USING btree (jti) |
| token_revocation_pkey | CREATE UNIQUE INDEX token_revocation_pkey ON public.token_revocation USING btree (id) |

### 4.21 public.user

- Que trata: Usuarios del sistema y atributos de autenticacion.
- RLS: enabled=t, forced=f
- Politicas RLS activas:
  - user_all_director [ALL] roles={public} qual=((get_current_user_role())::text = 'director'::text) with_check=((get_current_user_role())::text = 'director'::text)
  - user_select_own [SELECT] roles={public} qual=(uuid = (NULLIF(current_setting('app.current_user_uuid'::text, true), ''::text))::uuid) with_check=
  - user_select_staff [SELECT] roles={public} qual=((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[])) with_check=
  - user_update_own [UPDATE] roles={public} qual=(uuid = (NULLIF(current_setting('app.current_user_uuid'::text, true), ''::text))::uuid) with_check=(uuid = (NULLIF(current_setting('app.current_user_uuid'::text, true), ''::text))::uuid)

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('user_id_seq'::regclass) |
| 2 | uuid | uuid | NO | gen_random_uuid() |
| 3 | role_id | bigint | NO |  |
| 4 | career_id | bigint | YES |  |
| 5 | name | character varying | NO |  |
| 6 | rut | character varying | NO |  |
| 7 | email | character varying | NO |  |
| 8 | password_hash | character varying | NO |  |
| 9 | auth_uuid | uuid | YES |  |
| 10 | active | boolean | NO | true |
| 11 | failed_login_attempts | integer | NO | 0 |
| 12 | blocked_until | timestamp with time zone | YES |  |
| 13 | last_login_at | timestamp with time zone | YES |  |
| 14 | created_at | timestamp with time zone | NO | now() |
| 15 | updated_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| FOREIGN KEY | fk_user_career | FOREIGN KEY (career_id) REFERENCES career(id) ON DELETE RESTRICT |
| FOREIGN KEY | fk_user_role | FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE RESTRICT |
| PRIMARY KEY | user_pkey | PRIMARY KEY (id) |
| UNIQUE | user_auth_uuid_key | UNIQUE (auth_uuid) |
| UNIQUE | user_email_key | UNIQUE (email) |
| UNIQUE | user_rut_key | UNIQUE (rut) |
| UNIQUE | user_uuid_key | UNIQUE (uuid) |

**Indices**

| Nombre | Definicion |
|---|---|
| user_auth_uuid_key | CREATE UNIQUE INDEX user_auth_uuid_key ON public."user" USING btree (auth_uuid) |
| user_email_key | CREATE UNIQUE INDEX user_email_key ON public."user" USING btree (email) |
| user_pkey | CREATE UNIQUE INDEX user_pkey ON public."user" USING btree (id) |
| user_rut_key | CREATE UNIQUE INDEX user_rut_key ON public."user" USING btree (rut) |
| user_uuid_key | CREATE UNIQUE INDEX user_uuid_key ON public."user" USING btree (uuid) |

### 4.22 public.user_session

- Que trata: Sesiones refresh token activas.
- RLS: enabled=f, forced=f

**Columnas**

| # | Columna | Tipo | Null | Default |
|---|---|---|---|---|
| 1 | id | bigint | NO | nextval('user_session_id_seq'::regclass) |
| 2 | user_id | bigint | NO |  |
| 3 | refresh_token_hash | character varying | NO |  |
| 4 | device_info | character varying | YES |  |
| 5 | expires_at | timestamp with time zone | NO |  |
| 6 | created_at | timestamp with time zone | NO | now() |

**Constraints**

| Tipo | Nombre | Definicion |
|---|---|---|
| FOREIGN KEY | fk_user_session_user | FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE |
| PRIMARY KEY | user_session_pkey | PRIMARY KEY (id) |
| UNIQUE | user_session_refresh_token_hash_key | UNIQUE (refresh_token_hash) |

**Indices**

| Nombre | Definicion |
|---|---|
| user_session_pkey | CREATE UNIQUE INDEX user_session_pkey ON public.user_session USING btree (id) |
| user_session_refresh_token_hash_key | CREATE UNIQUE INDEX user_session_refresh_token_hash_key ON public.user_session USING btree (refresh_token_hash) |

## 5. Vistas (salida y uso)

### 5.1 public.outbox_events

- Que muestra: Proyeccion de outbox_event para consumo operacional.

| # | Columna | Tipo |
|---|---|---|
| 1 | event_id | uuid |
| 2 | aggregate_type | character varying |
| 3 | aggregate_id | uuid |
| 4 | event_type | character varying |
| 5 | payload | text |
| 6 | occurred_at | timestamp with time zone |
| 7 | processed_at | timestamp with time zone |
| 8 | retry_count | integer |
| 9 | status | outbox_status_enum |

### 5.2 public.v_active_loan_details

- Que muestra: Lineas de prestamos activos (approved/prepared/delivered/overdue).

| # | Columna | Tipo |
|---|---|---|
| 1 | loan_id | bigint |
| 2 | loan_uuid | uuid |
| 3 | status | loan_status_enum |
| 4 | scheduled_at | timestamp with time zone |
| 5 | expected_return_at | timestamp with time zone |
| 6 | requester_id | bigint |
| 7 | requester_name | character varying |
| 8 | implement_id | bigint |
| 9 | implement_name | character varying |
| 10 | item_type | item_type_enum |
| 11 | requested_quantity | integer |
| 12 | reserved_quantity | integer |
| 13 | delivered_quantity | integer |

### 5.3 public.v_individual_status_summary

- Que muestra: Resumen de estados de unidades individuales por implemento.

| # | Columna | Tipo |
|---|---|---|
| 1 | implement_id | bigint |
| 2 | implement_name | character varying |
| 3 | total_individuals | integer |
| 4 | available_count | integer |
| 5 | loaned_count | integer |
| 6 | maintenance_count | integer |
| 7 | damaged_count | integer |
| 8 | blocked_count | integer |
| 9 | retired_count | integer |

### 5.4 public.v_loan_requests_calendar

- Que muestra: Vista calendario de solicitudes con detalle de implementos y fechas de hitos.

| # | Columna | Tipo |
|---|---|---|
| 1 | loan_id | bigint |
| 2 | loan_uuid | uuid |
| 3 | status | loan_status_enum |
| 4 | status_label | text |
| 5 | scheduled_at | timestamp with time zone |
| 6 | expected_return_at | timestamp with time zone |
| 7 | created_at | timestamp with time zone |
| 8 | requester_id | bigint |
| 9 | requester_name | character varying |
| 10 | requester_email | character varying |
| 11 | room_id | bigint |
| 12 | room_name | character varying |
| 13 | subject_id | bigint |
| 14 | subject_code | character varying |
| 15 | subject_name | character varying |
| 16 | implement_id | bigint |
| 17 | implement_name | character varying |
| 18 | item_type | item_type_enum |
| 19 | requested_quantity | integer |
| 20 | reserved_quantity | integer |
| 21 | delivered_quantity | integer |
| 22 | approved_at | timestamp with time zone |
| 23 | prepared_at | timestamp with time zone |
| 24 | delivered_at | timestamp with time zone |
| 25 | completed_at | timestamp with time zone |

### 5.5 public.v_loan_requests_summary

- Que muestra: Resumen agregado de solicitudes por prestamo con detalle JSON.

| # | Columna | Tipo |
|---|---|---|
| 1 | loan_id | bigint |
| 2 | loan_uuid | uuid |
| 3 | status | loan_status_enum |
| 4 | status_label | text |
| 5 | scheduled_at | timestamp with time zone |
| 6 | expected_return_at | timestamp with time zone |
| 7 | created_at | timestamp with time zone |
| 8 | requester_id | bigint |
| 9 | requester_name | character varying |
| 10 | requester_email | character varying |
| 11 | room_id | bigint |
| 12 | room_name | character varying |
| 13 | subject_id | bigint |
| 14 | subject_code | character varying |
| 15 | subject_name | character varying |
| 16 | total_implement_types | integer |
| 17 | total_requested_quantity | integer |
| 18 | total_reserved_quantity | integer |
| 19 | total_delivered_quantity | integer |
| 20 | approved_at | timestamp with time zone |
| 21 | prepared_at | timestamp with time zone |
| 22 | delivered_at | timestamp with time zone |
| 23 | completed_at | timestamp with time zone |
| 24 | details | jsonb |

### 5.6 public.v_loan_state_dates

- Que muestra: Fechas de ocurrencia por estado del ciclo de vida del prestamo.

| # | Columna | Tipo |
|---|---|---|
| 1 | loan_id | bigint |
| 2 | approved_at | timestamp with time zone |
| 3 | prepared_at | timestamp with time zone |
| 4 | delivered_at | timestamp with time zone |
| 5 | completed_at | timestamp with time zone |
| 6 | rejected_at | timestamp with time zone |
| 7 | cancelled_at | timestamp with time zone |
| 8 | expired_at | timestamp with time zone |
| 9 | overdue_at | timestamp with time zone |

### 5.7 public.v_loan_status_timeline

- Que muestra: Timeline completo de transiciones de estado del prestamo.

| # | Columna | Tipo |
|---|---|---|
| 1 | history_id | bigint |
| 2 | loan_id | bigint |
| 3 | loan_uuid | uuid |
| 4 | from_status | loan_status_enum |
| 5 | to_status | loan_status_enum |
| 6 | actor_user_id | bigint |
| 7 | actor_name | character varying |
| 8 | actor_email | character varying |
| 9 | notes | text |
| 10 | changed_at | timestamp with time zone |

### 5.8 public.v_low_stock

- Que muestra: Vista de stock bajo y brecha contra minimos.

| # | Columna | Tipo |
|---|---|---|
| 1 | implement_id | bigint |
| 2 | implement_uuid | uuid |
| 3 | implement_name | character varying |
| 4 | category_id | bigint |
| 5 | category_name | character varying |
| 6 | location_id | bigint |
| 7 | location_name | character varying |
| 8 | item_type | item_type_enum |
| 9 | total_stock | integer |
| 10 | min_stock | integer |
| 11 | available | integer |
| 12 | reserved | integer |
| 13 | loaned | integer |
| 14 | damaged | integer |
| 15 | stock_gap | integer |
| 16 | stock_status | text |
| 17 | updated_at | timestamp with time zone |

## 6. Funciones SQL (entrada, salida y efectos)

### 6.1 public.fn_approve_loan(p_loan_id bigint, p_actor_user_id bigint, p_notes text)

- Que trata: Aprueba un prestamo pending, valida ventana/fechas y disponibilidad, reserva cantidades y registra historial.
- Entrada: p_loan_id bigint, p_actor_user_id bigint, p_notes text
- Salida: TABLE(loan_id bigint, new_status loan_status_enum, approved_at timestamp with time zone)
- Lenguaje: plpgsql
- Security definer: t
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: loan_detail
- Llama a funciones: fn_get_implement_availability, fn_loan_change_status

### 6.2 public.fn_auth_find_user_by_rut(p_rut character varying)

- Que trata: Busca usuario autenticable por RUT normalizado (security definer y RLS off).
- Entrada: p_rut character varying
- Salida: TABLE(user_uuid uuid, rut character varying, password_hash character varying, role_name character varying, failed_login_attempts integer, blocked_until timestamp with time zone)
- Lenguaje: sql
- Security definer: t
- Volatilidad: STABLE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.3 public.fn_cancel_loan(p_loan_id bigint, p_actor_user_id bigint, p_notes text)

- Que trata: Cancela prestamo segun reglas de estado y libera reservas/preparaciones cuando corresponde.
- Entrada: p_loan_id bigint, p_actor_user_id bigint, p_notes text
- Salida: TABLE(loan_id bigint, new_status loan_status_enum, cancelled_at timestamp with time zone)
- Lenguaje: plpgsql
- Security definer: t
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: loan_detail, loan_detail_individual, stock
- Llama a funciones: fn_loan_change_status

### 6.4 public.fn_complete_loan(p_loan_id bigint, p_actor_user_id bigint, p_notes text, p_return_payload jsonb)

- Que trata: Completa un prestamo delivered/overdue procesando devolucion y ajustando stock/unidades.
- Entrada: p_loan_id bigint, p_actor_user_id bigint, p_notes text, p_return_payload jsonb
- Salida: TABLE(loan_id bigint, new_status loan_status_enum, completed_at timestamp with time zone)
- Lenguaje: plpgsql
- Security definer: t
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: individual, inventory_movement, loan_detail, loan_detail_individual, stock
- Llama a funciones: fn_loan_change_status

### 6.5 public.fn_deliver_loan(p_loan_id bigint, p_actor_user_id bigint, p_notes text)

- Que trata: Entrega un prestamo prepared, mueve reserved->loaned, genera movimientos y actualiza unidades.
- Entrada: p_loan_id bigint, p_actor_user_id bigint, p_notes text
- Salida: TABLE(loan_id bigint, new_status loan_status_enum, delivered_at timestamp with time zone)
- Lenguaje: plpgsql
- Security definer: t
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: individual, inventory_movement, loan_detail, loan_detail_individual, stock
- Llama a funciones: fn_loan_change_status

### 6.6 public.fn_expire_pending_loans(p_actor_user_id bigint, p_now timestamp with time zone, p_grace_minutes integer)

- Que trata: Job batch que expira prestamos pending/approved/prepared vencidos por gracia.
- Entrada: p_actor_user_id bigint, p_now timestamp with time zone, p_grace_minutes integer
- Salida: TABLE(expired_count integer)
- Lenguaje: plpgsql
- Security definer: t
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: loan_detail, loan_detail_individual, stock
- Llama a funciones: fn_loan_change_status

### 6.7 public.fn_get_bulk_availability(p_implement_ids bigint[], p_start_at timestamp with time zone, p_end_at timestamp with time zone, p_include_pending boolean, p_exclude_loan_id bigint)

- Que trata: Calcula disponibilidad por lote de implementos para un rango temporal.
- Entrada: p_implement_ids bigint[], p_start_at timestamp with time zone, p_end_at timestamp with time zone, p_include_pending boolean, p_exclude_loan_id bigint
- Salida: TABLE(implement_id bigint, total_stock integer, damaged integer, blocked_quantity integer, available_quantity integer)
- Lenguaje: sql
- Security definer: t
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: fn_get_implement_availability

### 6.8 public.fn_get_implement_availability(p_implement_id bigint, p_start_at timestamp with time zone, p_end_at timestamp with time zone, p_include_pending boolean, p_exclude_loan_id bigint)

- Que trata: Calcula disponibilidad de un implemento en un rango temporal.
- Entrada: p_implement_id bigint, p_start_at timestamp with time zone, p_end_at timestamp with time zone, p_include_pending boolean, p_exclude_loan_id bigint
- Salida: TABLE(implement_id bigint, total_stock integer, damaged integer, blocked_quantity integer, available_quantity integer)
- Lenguaje: plpgsql
- Security definer: t
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.9 public.fn_guard_individual_item_type()

- Que trata: Guard de trigger para bloquear individuales en implementos no individual.
- Entrada: 
- Salida: trigger
- Lenguaje: plpgsql
- Security definer: f
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.10 public.fn_is_valid_loan_status_transition(p_from loan_status_enum, p_to loan_status_enum)

- Que trata: Valida transicion de estado permitida segun matriz del flujo.
- Entrada: p_from loan_status_enum, p_to loan_status_enum
- Salida: boolean
- Lenguaje: sql
- Security definer: f
- Volatilidad: IMMUTABLE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.11 public.fn_loan_change_status(p_loan_id bigint, p_new_status loan_status_enum, p_actor_user_id bigint, p_notes text)

- Que trata: Operacion centralizada de cambio de estado (por id o por uuids) con historial.
- Entrada: p_loan_id bigint, p_new_status loan_status_enum, p_actor_user_id bigint, p_notes text
- Salida: TABLE(loan_id bigint, old_status loan_status_enum, new_status loan_status_enum, changed_at timestamp with time zone)
- Lenguaje: plpgsql
- Security definer: t
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: loan, loan_status_history
- Llama a funciones: fn_is_valid_loan_status_transition

### 6.12 public.fn_loan_change_status(p_loan_uuid uuid, p_actor_user_uuid uuid, p_to_status loan_status_enum, p_notes text)

- Que trata: Operacion centralizada de cambio de estado (por id o por uuids) con historial.
- Entrada: p_loan_uuid uuid, p_actor_user_uuid uuid, p_to_status loan_status_enum, p_notes text
- Salida: void
- Lenguaje: plpgsql
- Security definer: t
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.13 public.fn_mark_overdue_loans(p_actor_user_id bigint, p_now timestamp with time zone)

- Que trata: Job batch que marca overdue prestamos delivered fuera de expected_return_at.
- Entrada: p_actor_user_id bigint, p_now timestamp with time zone
- Salida: TABLE(overdue_count integer)
- Lenguaje: plpgsql
- Security definer: t
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: fn_loan_change_status

### 6.14 public.fn_notify_new_loan_request(p_requester_user_uuid uuid)

- Que trata: Genera notificaciones de nueva solicitud para coordinadores/directores.
- Entrada: p_requester_user_uuid uuid
- Salida: void
- Lenguaje: plpgsql
- Security definer: t
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: notification
- Llama a funciones: ninguna

### 6.15 public.fn_prepare_loan(p_loan_id bigint, p_actor_user_id bigint, p_notes text)

- Que trata: Prepara prestamo approved, mueve available->reserved fisico y marca individuales prepared.
- Entrada: p_loan_id bigint, p_actor_user_id bigint, p_notes text
- Salida: TABLE(loan_id bigint, new_status loan_status_enum, prepared_at timestamp with time zone)
- Lenguaje: plpgsql
- Security definer: t
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: loan_detail_individual, stock
- Llama a funciones: fn_loan_change_status

### 6.16 public.fn_set_loan_updated_at()

- Que trata: Trigger helper legacy para updated_at en loan.
- Entrada: 
- Salida: trigger
- Lenguaje: plpgsql
- Security definer: f
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.17 public.fn_trg_create_notification_on_loan_status()

- Que trata: Trigger: crea notificaciones de usuario cuando cambia estado de prestamo.
- Entrada: 
- Salida: trigger
- Lenguaje: plpgsql
- Security definer: f
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: notification
- Llama a funciones: ninguna

### 6.18 public.fn_trg_create_outbox_event_on_loan_status()

- Que trata: Trigger: publica evento en outbox_event por cambio de estado.
- Entrada: 
- Salida: trigger
- Lenguaje: plpgsql
- Security definer: f
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: outbox_event
- Llama a funciones: ninguna

### 6.19 public.fn_trg_implement_updated_at()

- Que trata: Trigger: setea implement.updated_at en UPDATE.
- Entrada: 
- Salida: trigger
- Lenguaje: plpgsql
- Security definer: f
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.20 public.fn_trg_individual_updated_at()

- Que trata: Trigger: setea individual.updated_at en UPDATE.
- Entrada: 
- Salida: trigger
- Lenguaje: plpgsql
- Security definer: f
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.21 public.fn_trg_inventory_movement_audit()

- Que trata: Trigger: registra evento en audit_log al insertar inventory_movement.
- Entrada: 
- Salida: trigger
- Lenguaje: plpgsql
- Security definer: f
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: audit_log
- Llama a funciones: ninguna

### 6.22 public.fn_trg_loan_updated_at()

- Que trata: Trigger: setea loan.updated_at en UPDATE.
- Entrada: 
- Salida: trigger
- Lenguaje: plpgsql
- Security definer: f
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.23 public.fn_trg_stock_updated_at()

- Que trata: Trigger: setea stock.updated_at en UPDATE.
- Entrada: 
- Salida: trigger
- Lenguaje: plpgsql
- Security definer: f
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.24 public.fn_trg_validate_loan_dates()

- Que trata: Trigger: valida reglas de fechas de prestamo antes de INSERT/UPDATE.
- Entrada: 
- Salida: trigger
- Lenguaje: plpgsql
- Security definer: f
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.25 public.fn_validate_individual_item_type()

- Que trata: Trigger: valida que individual solo referencie implementos item_type=individual.
- Entrada: 
- Salida: trigger
- Lenguaje: plpgsql
- Security definer: f
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.26 public.fn_validate_loan_status_transition()

- Que trata: Trigger: bloquea UPDATE de loan.status fuera de transiciones permitidas.
- Entrada: 
- Salida: trigger
- Lenguaje: plpgsql
- Security definer: f
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: fn_is_valid_loan_status_transition

### 6.27 public.fn_write_audit_log(p_event character varying, p_actor_user_uuid uuid, p_target_user_uuid uuid, p_payload jsonb)

- Que trata: Inserta registro en audit_log resolviendo actor/target por UUID.
- Entrada: p_event character varying, p_actor_user_uuid uuid, p_target_user_uuid uuid, p_payload jsonb
- Salida: void
- Lenguaje: plpgsql
- Security definer: t
- Volatilidad: VOLATILE
- Escrituras SQL detectadas: audit_log
- Llama a funciones: ninguna

### 6.28 public.get_current_user_id()

- Que trata: Obtiene user.id desde app.current_user_uuid (contexto DB).
- Entrada: 
- Salida: bigint
- Lenguaje: sql
- Security definer: t
- Volatilidad: STABLE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.29 public.get_current_user_role()

- Que trata: Obtiene role.name del usuario actual de contexto DB.
- Entrada: 
- Salida: character varying
- Lenguaje: sql
- Security definer: t
- Volatilidad: STABLE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

### 6.30 public.is_authenticated()

- Que trata: Indica si existe app.current_user_uuid valido en contexto DB.
- Entrada: 
- Salida: boolean
- Lenguaje: sql
- Security definer: t
- Volatilidad: STABLE
- Escrituras SQL detectadas: ninguna directa
- Llama a funciones: ninguna

## 7. Triggers (evento y accion disparada)

| Trigger | Tabla | Evento | Funcion disparada | Definicion |
|---|---|---|---|---|
| trg_implement_updated_at | implement | BEFORE UPDATE | fn_trg_implement_updated_at | CREATE TRIGGER trg_implement_updated_at BEFORE UPDATE ON implement FOR EACH ROW EXECUTE FUNCTION fn_trg_implement_updated_at() |
| trg_individual_updated_at | individual | BEFORE UPDATE | fn_trg_individual_updated_at | CREATE TRIGGER trg_individual_updated_at BEFORE UPDATE ON individual FOR EACH ROW EXECUTE FUNCTION fn_trg_individual_updated_at() |
| trg_validate_individual_item_type | individual | BEFORE INSERT OR UPDATE OF implement_id | fn_validate_individual_item_type | CREATE TRIGGER trg_validate_individual_item_type BEFORE INSERT OR UPDATE OF implement_id ON individual FOR EACH ROW EXECUTE FUNCTION fn_validate_individual_item_type() |
| trg_inventory_movement_audit | inventory_movement | AFTER INSERT | fn_trg_inventory_movement_audit | CREATE TRIGGER trg_inventory_movement_audit AFTER INSERT ON inventory_movement FOR EACH ROW EXECUTE FUNCTION fn_trg_inventory_movement_audit() |
| trg_loan_updated_at | loan | BEFORE UPDATE | fn_trg_loan_updated_at | CREATE TRIGGER trg_loan_updated_at BEFORE UPDATE ON loan FOR EACH ROW EXECUTE FUNCTION fn_trg_loan_updated_at() |
| trg_validate_loan_dates | loan | BEFORE INSERT OR UPDATE OF scheduled_at, expected_return_at | fn_trg_validate_loan_dates | CREATE TRIGGER trg_validate_loan_dates BEFORE INSERT OR UPDATE OF scheduled_at, expected_return_at ON loan FOR EACH ROW EXECUTE FUNCTION fn_trg_validate_loan_dates() |
| trg_validate_loan_status_transition | loan | BEFORE UPDATE OF status | fn_validate_loan_status_transition | CREATE TRIGGER trg_validate_loan_status_transition BEFORE UPDATE OF status ON loan FOR EACH ROW EXECUTE FUNCTION fn_validate_loan_status_transition() |
| trg_create_notification_on_loan_status | loan_status_history | AFTER INSERT | fn_trg_create_notification_on_loan_status | CREATE TRIGGER trg_create_notification_on_loan_status AFTER INSERT ON loan_status_history FOR EACH ROW EXECUTE FUNCTION fn_trg_create_notification_on_loan_status() |
| trg_create_outbox_event_on_loan_status | loan_status_history | AFTER INSERT | fn_trg_create_outbox_event_on_loan_status | CREATE TRIGGER trg_create_outbox_event_on_loan_status AFTER INSERT ON loan_status_history FOR EACH ROW EXECUTE FUNCTION fn_trg_create_outbox_event_on_loan_status() |
| trg_stock_updated_at | stock | BEFORE UPDATE | fn_trg_stock_updated_at | CREATE TRIGGER trg_stock_updated_at BEFORE UPDATE ON stock FOR EACH ROW EXECUTE FUNCTION fn_trg_stock_updated_at() |

## 8. Politicas RLS (detalle)

| Tabla | Policy | CMD | Roles | Qual | With Check |
|---|---|---|---|---|---|
| audit_log | audit_log_select_director | SELECT | {public} | ((get_current_user_role())::text = 'director'::text) |  |
| inventory_movement | inventory_movement_insert_staff | INSERT | {public} |  | ((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[])) |
| inventory_movement | inventory_movement_select_staff | SELECT | {public} | ((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[])) |  |
| loan | loan_all_staff | ALL | {public} | ((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[])) | ((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[])) |
| loan | loan_insert_own | INSERT | {public} |  | (requester_id = get_current_user_id()) |
| loan | loan_select_own | SELECT | {public} | (requester_id = get_current_user_id()) |  |
| notification | notification_all_director | ALL | {public} | ((get_current_user_role())::text = 'director'::text) | ((get_current_user_role())::text = 'director'::text) |
| notification | notification_all_own | ALL | {public} | (user_id = get_current_user_id()) | (user_id = get_current_user_id()) |
| stock | stock_modify_staff | ALL | {public} | ((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[])) | ((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[])) |
| stock | stock_select_authenticated | SELECT | {public} | is_authenticated() |  |
| user | user_all_director | ALL | {public} | ((get_current_user_role())::text = 'director'::text) | ((get_current_user_role())::text = 'director'::text) |
| user | user_select_own | SELECT | {public} | (uuid = (NULLIF(current_setting('app.current_user_uuid'::text, true), ''::text))::uuid) |  |
| user | user_select_staff | SELECT | {public} | ((get_current_user_role())::text = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::text[])) |  |
| user | user_update_own | UPDATE | {public} | (uuid = (NULLIF(current_setting('app.current_user_uuid'::text, true), ''::text))::uuid) | (uuid = (NULLIF(current_setting('app.current_user_uuid'::text, true), ''::text))::uuid) |

## 9. Inventario de indices (global)

| Tabla | Index | Definicion |
|---|---|---|
| audit_log | audit_log_pkey | CREATE UNIQUE INDEX audit_log_pkey ON public.audit_log USING btree (id) |
| career | career_code_key | CREATE UNIQUE INDEX career_code_key ON public.career USING btree (code) |
| career | career_pkey | CREATE UNIQUE INDEX career_pkey ON public.career USING btree (id) |
| career | career_uuid_key | CREATE UNIQUE INDEX career_uuid_key ON public.career USING btree (uuid) |
| category | category_name_key | CREATE UNIQUE INDEX category_name_key ON public.category USING btree (name) |
| category | category_pkey | CREATE UNIQUE INDEX category_pkey ON public.category USING btree (id) |
| category | category_uuid_key | CREATE UNIQUE INDEX category_uuid_key ON public.category USING btree (uuid) |
| email_outbox | email_outbox_event_id_key | CREATE UNIQUE INDEX email_outbox_event_id_key ON public.email_outbox USING btree (event_id) |
| email_outbox | email_outbox_pkey | CREATE UNIQUE INDEX email_outbox_pkey ON public.email_outbox USING btree (id) |
| flyway_schema_history | flyway_schema_history_pk | CREATE UNIQUE INDEX flyway_schema_history_pk ON public.flyway_schema_history USING btree (installed_rank) |
| flyway_schema_history | flyway_schema_history_s_idx | CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success) |
| implement | implement_barcode_key | CREATE UNIQUE INDEX implement_barcode_key ON public.implement USING btree (barcode) |
| implement | implement_name_category_key | CREATE UNIQUE INDEX implement_name_category_key ON public.implement USING btree (name, category_id) |
| implement | implement_pkey | CREATE UNIQUE INDEX implement_pkey ON public.implement USING btree (id) |
| implement | implement_uuid_key | CREATE UNIQUE INDEX implement_uuid_key ON public.implement USING btree (uuid) |
| individual | idx_individual_implement_status | CREATE INDEX idx_individual_implement_status ON public.individual USING btree (implement_id, status) |
| individual | individual_asset_code_key | CREATE UNIQUE INDEX individual_asset_code_key ON public.individual USING btree (asset_code) |
| individual | individual_pkey | CREATE UNIQUE INDEX individual_pkey ON public.individual USING btree (id) |
| individual | individual_uuid_key | CREATE UNIQUE INDEX individual_uuid_key ON public.individual USING btree (uuid) |
| inventory_movement | idx_inventory_movement_implement_date | CREATE INDEX idx_inventory_movement_implement_date ON public.inventory_movement USING btree (implement_id, created_at DESC) |
| inventory_movement | inventory_movement_pkey | CREATE UNIQUE INDEX inventory_movement_pkey ON public.inventory_movement USING btree (id) |
| loan | idx_loan_requester_status | CREATE INDEX idx_loan_requester_status ON public.loan USING btree (requester_id, status) |
| loan | idx_loan_schedule_range | CREATE INDEX idx_loan_schedule_range ON public.loan USING btree (scheduled_at, expected_return_at) |
| loan | idx_loan_status_scheduled | CREATE INDEX idx_loan_status_scheduled ON public.loan USING btree (status, scheduled_at) |
| loan | loan_pkey | CREATE UNIQUE INDEX loan_pkey ON public.loan USING btree (id) |
| loan | loan_uuid_key | CREATE UNIQUE INDEX loan_uuid_key ON public.loan USING btree (uuid) |
| loan_detail | idx_loan_detail_implement_loan | CREATE INDEX idx_loan_detail_implement_loan ON public.loan_detail USING btree (implement_id, loan_id) |
| loan_detail | loan_detail_pkey | CREATE UNIQUE INDEX loan_detail_pkey ON public.loan_detail USING btree (loan_id, implement_id) |
| loan_detail_individual | loan_detail_individual_pkey | CREATE UNIQUE INDEX loan_detail_individual_pkey ON public.loan_detail_individual USING btree (loan_id, implement_id, individual_id) |
| loan_status_history | idx_loan_history_loan_changed | CREATE INDEX idx_loan_history_loan_changed ON public.loan_status_history USING btree (loan_id, changed_at DESC) |
| loan_status_history | idx_loan_history_to_status | CREATE INDEX idx_loan_history_to_status ON public.loan_status_history USING btree (loan_id, to_status, changed_at DESC) |
| loan_status_history | idx_loan_status_history_loan_changed_at | CREATE INDEX idx_loan_status_history_loan_changed_at ON public.loan_status_history USING btree (loan_id, changed_at DESC) |
| loan_status_history | idx_loan_status_history_to_status | CREATE INDEX idx_loan_status_history_to_status ON public.loan_status_history USING btree (to_status) |
| loan_status_history | loan_status_history_pkey | CREATE UNIQUE INDEX loan_status_history_pkey ON public.loan_status_history USING btree (id) |
| location | location_name_key | CREATE UNIQUE INDEX location_name_key ON public.location USING btree (name) |
| location | location_pkey | CREATE UNIQUE INDEX location_pkey ON public.location USING btree (id) |
| location | location_uuid_key | CREATE UNIQUE INDEX location_uuid_key ON public.location USING btree (uuid) |
| notification | idx_notification_user_read | CREATE INDEX idx_notification_user_read ON public.notification USING btree (user_id, read_status, created_at DESC) |
| notification | notification_pkey | CREATE UNIQUE INDEX notification_pkey ON public.notification USING btree (id) |
| notification | notification_uuid_key | CREATE UNIQUE INDEX notification_uuid_key ON public.notification USING btree (uuid) |
| outbox_event | outbox_event_id_key | CREATE UNIQUE INDEX outbox_event_id_key ON public.outbox_event USING btree (event_id) |
| outbox_event | outbox_event_pkey | CREATE UNIQUE INDEX outbox_event_pkey ON public.outbox_event USING btree (id) |
| role | role_name_key | CREATE UNIQUE INDEX role_name_key ON public.role USING btree (name) |
| role | role_pkey | CREATE UNIQUE INDEX role_pkey ON public.role USING btree (id) |
| role | role_uuid_key | CREATE UNIQUE INDEX role_uuid_key ON public.role USING btree (uuid) |
| room | room_name_key | CREATE UNIQUE INDEX room_name_key ON public.room USING btree (name) |
| room | room_pkey | CREATE UNIQUE INDEX room_pkey ON public.room USING btree (id) |
| room | room_uuid_key | CREATE UNIQUE INDEX room_uuid_key ON public.room USING btree (uuid) |
| stock | stock_implement_id_key | CREATE UNIQUE INDEX stock_implement_id_key ON public.stock USING btree (implement_id) |
| stock | stock_pkey | CREATE UNIQUE INDEX stock_pkey ON public.stock USING btree (id) |
| subject | subject_code_key | CREATE UNIQUE INDEX subject_code_key ON public.subject USING btree (code) |
| subject | subject_pkey | CREATE UNIQUE INDEX subject_pkey ON public.subject USING btree (id) |
| subject | subject_uuid_key | CREATE UNIQUE INDEX subject_uuid_key ON public.subject USING btree (uuid) |
| token_revocation | token_revocation_jti_key | CREATE UNIQUE INDEX token_revocation_jti_key ON public.token_revocation USING btree (jti) |
| token_revocation | token_revocation_pkey | CREATE UNIQUE INDEX token_revocation_pkey ON public.token_revocation USING btree (id) |
| user | user_auth_uuid_key | CREATE UNIQUE INDEX user_auth_uuid_key ON public."user" USING btree (auth_uuid) |
| user | user_email_key | CREATE UNIQUE INDEX user_email_key ON public."user" USING btree (email) |
| user | user_pkey | CREATE UNIQUE INDEX user_pkey ON public."user" USING btree (id) |
| user | user_rut_key | CREATE UNIQUE INDEX user_rut_key ON public."user" USING btree (rut) |
| user | user_uuid_key | CREATE UNIQUE INDEX user_uuid_key ON public."user" USING btree (uuid) |
| user_session | user_session_pkey | CREATE UNIQUE INDEX user_session_pkey ON public.user_session USING btree (id) |
| user_session | user_session_refresh_token_hash_key | CREATE UNIQUE INDEX user_session_refresh_token_hash_key ON public.user_session USING btree (refresh_token_hash) |

## 10. Notas operativas

- Regla DB-first: las transiciones del flujo de prestamo y los ajustes de stock se concentran en funciones SQL (approve/prepare/deliver/complete/cancel/expire/overdue).
- Trazabilidad: toda transicion efectiva de estado persiste en loan_status_history y se proyecta en v_loan_state_dates + v_loan_status_timeline.
- Integracion async: cambios de estado disparan notificaciones y outbox via triggers en loan_status_history.
- Seguridad de datos: RLS habilitado en tablas criticas (loan, stock, inventory_movement, notification, user, audit_log).

