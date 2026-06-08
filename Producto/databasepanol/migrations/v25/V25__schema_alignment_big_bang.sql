-- =============================================================================
-- V25 BIG-BANG RESTRUCTURE (NON-PROD)
-- Rebuilds the public operational schema aligned to panol_salud_schema_v3.
-- Data is intentionally discarded in this migration.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- -----------------------------------------------------------------------------
-- 0) CLEANUP: views, legacy functions, tables, and enums
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    v RECORD;
BEGIN
    FOR v IN
        SELECT schemaname, viewname
        FROM pg_catalog.pg_views
        WHERE schemaname = 'public'
    LOOP
        EXECUTE format('DROP VIEW IF EXISTS %I.%I CASCADE', v.schemaname, v.viewname);
    END LOOP;
END $$;

DO $$
DECLARE
    mv RECORD;
BEGIN
    FOR mv IN
        SELECT schemaname, matviewname
        FROM pg_catalog.pg_matviews
        WHERE schemaname = 'public'
    LOOP
        EXECUTE format('DROP MATERIALIZED VIEW IF EXISTS %I.%I CASCADE', mv.schemaname, mv.matviewname);
    END LOOP;
END $$;

DROP FUNCTION IF EXISTS public.fn_sync_user_role_keys() CASCADE;
DROP FUNCTION IF EXISTS public.fn_validate_uuid_migration() CASCADE;
DROP FUNCTION IF EXISTS public.fn_sync_user_auth_uuid_only() CASCADE;
DROP FUNCTION IF EXISTS public.fn_implement_ensure_stock_uuid() CASCADE;
DROP FUNCTION IF EXISTS public.fn_guard_individual_item_type() CASCADE;
DROP FUNCTION IF EXISTS public.fn_guard_individual_no_fungible() CASCADE;
DROP FUNCTION IF EXISTS public.get_current_user_id() CASCADE;
DROP FUNCTION IF EXISTS public.get_current_user_role() CASCADE;
DROP FUNCTION IF EXISTS public.is_authenticated() CASCADE;
DROP FUNCTION IF EXISTS public.fn_auth_find_user_by_rut(TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.fn_write_audit_log(CHARACTER VARYING, UUID, UUID, JSONB) CASCADE;

DROP TABLE IF EXISTS public.loan_detail_individual CASCADE;
DROP TABLE IF EXISTS public.loan_detail CASCADE;
DROP TABLE IF EXISTS public.loan_status_history CASCADE;
DROP TABLE IF EXISTS public.loan CASCADE;
DROP TABLE IF EXISTS public.stock CASCADE;
DROP TABLE IF EXISTS public.individual CASCADE;
DROP TABLE IF EXISTS public.implement CASCADE;
DROP TABLE IF EXISTS public.notification CASCADE;
DROP TABLE IF EXISTS public.inventory_movement CASCADE;
DROP TABLE IF EXISTS public.audit_log CASCADE;
DROP TABLE IF EXISTS public.user_session CASCADE;
DROP TABLE IF EXISTS public.token_revocation CASCADE;
DROP TABLE IF EXISTS public.email_outbox CASCADE;
DROP TABLE IF EXISTS public.outbox_event CASCADE;
DROP TABLE IF EXISTS public.outbox_events CASCADE;
DROP TABLE IF EXISTS public."user" CASCADE;
DROP TABLE IF EXISTS public.room CASCADE;
DROP TABLE IF EXISTS public.subject CASCADE;
DROP TABLE IF EXISTS public.career CASCADE;
DROP TABLE IF EXISTS public.category CASCADE;
DROP TABLE IF EXISTS public.location CASCADE;
DROP TABLE IF EXISTS public.role CASCADE;

DROP TYPE IF EXISTS public.outbox_status_enum CASCADE;
DROP TYPE IF EXISTS public.inventory_movement_type_enum CASCADE;
DROP TYPE IF EXISTS public.loan_status_enum CASCADE;
DROP TYPE IF EXISTS public.individual_condition_enum CASCADE;
DROP TYPE IF EXISTS public.individual_status_enum CASCADE;
DROP TYPE IF EXISTS public.item_type_enum CASCADE;

-- -----------------------------------------------------------------------------
-- 1) ENUMS
-- -----------------------------------------------------------------------------

CREATE TYPE public.item_type_enum AS ENUM (
    'fungible',
    'no_fungible'
);

CREATE TYPE public.individual_status_enum AS ENUM (
    'available',
    'loaned',
    'maintenance',
    'damaged'
);

CREATE TYPE public.individual_condition_enum AS ENUM (
    'good',
    'fair',
    'poor'
);

CREATE TYPE public.loan_status_enum AS ENUM (
    'pending',
    'approved',
    'rejected',
    'delivered',
    'completed',
    'cancelled'
);

CREATE TYPE public.inventory_movement_type_enum AS ENUM (
    'STOCK_IN',
    'STOCK_OUT',
    'LOAN_DELIVERY',
    'LOAN_RETURN',
    'DAMAGE_REPORT',
    'MANUAL_ADJUSTMENT'
);

CREATE TYPE public.outbox_status_enum AS ENUM (
    'PENDING',
    'PROCESSING',
    'SENT',
    'FAILED'
);

-- -----------------------------------------------------------------------------
-- 2) MASTER TABLES
-- -----------------------------------------------------------------------------

CREATE TABLE public.role (
    id          BIGSERIAL,
    uuid        UUID              NOT NULL DEFAULT gen_random_uuid(),
    name        CHARACTER VARYING NOT NULL,
    description CHARACTER VARYING,
    CONSTRAINT role_pkey     PRIMARY KEY (id),
    CONSTRAINT role_uuid_key UNIQUE (uuid),
    CONSTRAINT role_name_key UNIQUE (name)
);

CREATE TABLE public.career (
    id         BIGSERIAL,
    uuid       UUID              NOT NULL DEFAULT gen_random_uuid(),
    code       CHARACTER VARYING NOT NULL,
    name       CHARACTER VARYING NOT NULL,
    active     BOOLEAN           NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT career_pkey      PRIMARY KEY (id),
    CONSTRAINT career_uuid_key  UNIQUE (uuid),
    CONSTRAINT career_code_key  UNIQUE (code)
);

CREATE TABLE public.subject (
    id         BIGSERIAL,
    uuid       UUID              NOT NULL DEFAULT gen_random_uuid(),
    code       CHARACTER VARYING NOT NULL,
    name       CHARACTER VARYING NOT NULL,
    active     BOOLEAN           NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT subject_pkey      PRIMARY KEY (id),
    CONSTRAINT subject_uuid_key  UNIQUE (uuid),
    CONSTRAINT subject_code_key  UNIQUE (code)
);

CREATE TABLE public.category (
    id          BIGSERIAL,
    uuid        UUID              NOT NULL DEFAULT gen_random_uuid(),
    name        CHARACTER VARYING NOT NULL,
    description TEXT,
    active      BOOLEAN           NOT NULL DEFAULT true,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT category_pkey      PRIMARY KEY (id),
    CONSTRAINT category_uuid_key  UNIQUE (uuid),
    CONSTRAINT category_name_key  UNIQUE (name)
);

CREATE TABLE public.location (
    id            BIGSERIAL,
    uuid          UUID              NOT NULL DEFAULT gen_random_uuid(),
    name          CHARACTER VARYING NOT NULL,
    description   TEXT,
    location_type CHARACTER VARYING NOT NULL DEFAULT 'PANOL_SHELF',
    active        BOOLEAN           NOT NULL DEFAULT true,
    CONSTRAINT location_pkey      PRIMARY KEY (id),
    CONSTRAINT location_uuid_key  UNIQUE (uuid),
    CONSTRAINT location_name_key  UNIQUE (name)
);

CREATE TABLE public.room (
    id          BIGSERIAL,
    uuid        UUID              NOT NULL DEFAULT gen_random_uuid(),
    name        CHARACTER VARYING NOT NULL,
    description TEXT,
    active      BOOLEAN           NOT NULL DEFAULT true,
    CONSTRAINT room_pkey      PRIMARY KEY (id),
    CONSTRAINT room_uuid_key  UNIQUE (uuid),
    CONSTRAINT room_name_key  UNIQUE (name)
);

INSERT INTO public.role (name, description)
VALUES
    ('director', 'Acceso total de gestión'),
    ('coordinador', 'Gestión operativa de pañol'),
    ('docente', 'Solicitante de préstamos')
ON CONFLICT (name) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 3) USERS AND IMPLEMENTS
-- -----------------------------------------------------------------------------

CREATE TABLE public."user" (
    id                    BIGSERIAL,
    uuid                  UUID              NOT NULL DEFAULT gen_random_uuid(),
    role_id               BIGINT            NOT NULL,
    career_id             BIGINT,
    name                  CHARACTER VARYING NOT NULL,
    rut                   CHARACTER VARYING NOT NULL,
    email                 CHARACTER VARYING NOT NULL,
    password_hash         CHARACTER VARYING NOT NULL,
    auth_uuid             UUID,
    active                BOOLEAN           NOT NULL DEFAULT true,
    failed_login_attempts INTEGER           NOT NULL DEFAULT 0,
    blocked_until         TIMESTAMP WITH TIME ZONE,
    last_login_at         TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT user_pkey          PRIMARY KEY (id),
    CONSTRAINT user_uuid_key      UNIQUE (uuid),
    CONSTRAINT user_rut_key       UNIQUE (rut),
    CONSTRAINT user_email_key     UNIQUE (email),
    CONSTRAINT user_auth_uuid_key UNIQUE (auth_uuid),
    CONSTRAINT fk_user_role   FOREIGN KEY (role_id)   REFERENCES public.role(id)   ON DELETE RESTRICT,
    CONSTRAINT fk_user_career FOREIGN KEY (career_id) REFERENCES public.career(id) ON DELETE RESTRICT
);

-- System actor used by asynchronous workers (Outbox, schedulers) under strict RLS.
INSERT INTO public."user" (uuid, role_id, name, rut, email, password_hash, active)
SELECT
    '99999999-9999-9999-9999-999999999999'::UUID,
    r.id,
    'SISTEMA_OUTBOX',
    '99.999.999-9',
    'sistema.outbox@duocuc.cl',
    '$2a$10$falsa_pero_valida_para_not_null',
    true
FROM public.role r
WHERE r.name = 'coordinador'
ON CONFLICT (uuid) DO NOTHING;

CREATE TABLE public.implement (
    id           BIGSERIAL,
    uuid         UUID              NOT NULL DEFAULT gen_random_uuid(),
    category_id  BIGINT,
    location_id  BIGINT,
    name         CHARACTER VARYING NOT NULL,
    description  TEXT,
    item_type    public.item_type_enum NOT NULL DEFAULT 'no_fungible'::public.item_type_enum,
    barcode      CHARACTER VARYING,
    img_url      TEXT,
    active       BOOLEAN           NOT NULL DEFAULT true,
    observations TEXT,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT implement_pkey                 PRIMARY KEY (id),
    CONSTRAINT implement_uuid_key             UNIQUE (uuid),
    CONSTRAINT implement_name_category_key    UNIQUE (name, category_id),
    CONSTRAINT implement_barcode_key          UNIQUE (barcode),
    CONSTRAINT fk_implement_category FOREIGN KEY (category_id) REFERENCES public.category(id) ON DELETE RESTRICT,
    CONSTRAINT fk_implement_location FOREIGN KEY (location_id) REFERENCES public.location(id) ON DELETE RESTRICT
);

-- -----------------------------------------------------------------------------
-- 4) INVENTORY LAYER
-- -----------------------------------------------------------------------------

CREATE TABLE public.individual (
    id                  BIGSERIAL,
    uuid                UUID              NOT NULL DEFAULT gen_random_uuid(),
    implement_id        BIGINT            NOT NULL,
    current_location_id BIGINT,
    asset_code          CHARACTER VARYING NOT NULL,
    status              public.individual_status_enum    NOT NULL DEFAULT 'available'::public.individual_status_enum,
    condition           public.individual_condition_enum NOT NULL DEFAULT 'good'::public.individual_condition_enum,
    notes               TEXT,
    active              BOOLEAN           NOT NULL DEFAULT true,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT individual_pkey           PRIMARY KEY (id),
    CONSTRAINT individual_uuid_key       UNIQUE (uuid),
    CONSTRAINT individual_asset_code_key UNIQUE (asset_code),
    CONSTRAINT fk_individual_implement FOREIGN KEY (implement_id)        REFERENCES public.implement(id) ON DELETE CASCADE,
    CONSTRAINT fk_individual_location  FOREIGN KEY (current_location_id) REFERENCES public.location(id)  ON DELETE RESTRICT
);

CREATE OR REPLACE FUNCTION public.fn_guard_individual_no_fungible()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_item_type public.item_type_enum;
BEGIN
    SELECT item_type
      INTO v_item_type
      FROM public.implement
     WHERE id = NEW.implement_id;

    IF v_item_type = 'fungible' THEN
        RAISE EXCEPTION
            'Violación de integridad: no se pueden crear unidades individuales para implemento fungible. implement_id=%, item_type=%',
            NEW.implement_id, v_item_type;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_guard_individual_no_fungible
    BEFORE INSERT OR UPDATE OF implement_id ON public.individual
    FOR EACH ROW
    EXECUTE FUNCTION public.fn_guard_individual_no_fungible();

CREATE TABLE public.stock (
    id           BIGSERIAL,
    implement_id BIGINT  NOT NULL,
    total_stock  INTEGER NOT NULL DEFAULT 0,
    min_stock    INTEGER NOT NULL DEFAULT 0,
    available    INTEGER NOT NULL DEFAULT 0,
    reserved     INTEGER NOT NULL DEFAULT 0,
    loaned       INTEGER NOT NULL DEFAULT 0,
    damaged      INTEGER NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT stock_pkey             PRIMARY KEY (id),
    CONSTRAINT stock_implement_id_key UNIQUE (implement_id),
    CONSTRAINT fk_stock_implement     FOREIGN KEY (implement_id) REFERENCES public.implement(id) ON DELETE CASCADE,
    CONSTRAINT stock_total_stock_check CHECK (total_stock >= 0),
    CONSTRAINT stock_min_stock_check   CHECK (min_stock    >= 0),
    CONSTRAINT stock_available_check   CHECK (available    >= 0),
    CONSTRAINT stock_reserved_check    CHECK (reserved     >= 0),
    CONSTRAINT stock_loaned_check      CHECK (loaned       >= 0),
    CONSTRAINT stock_damaged_check     CHECK (damaged      >= 0),
    CONSTRAINT chk_stock_counters_integrity CHECK (available + reserved + loaned + damaged = total_stock)
);

-- -----------------------------------------------------------------------------
-- 5) LOAN LIFECYCLE
-- -----------------------------------------------------------------------------

CREATE TABLE public.loan (
    id                  BIGSERIAL,
    uuid                UUID    NOT NULL DEFAULT gen_random_uuid(),
    requester_id        BIGINT  NOT NULL,
    room_id             BIGINT,
    subject_id          BIGINT,
    status              public.loan_status_enum NOT NULL DEFAULT 'pending'::public.loan_status_enum,
    review_notes        TEXT,
    rejection_reason    TEXT,
    cancellation_reason TEXT,
    scheduled_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    due_date            TIMESTAMP WITH TIME ZONE,
    approved_at         TIMESTAMP WITH TIME ZONE,
    delivered_at        TIMESTAMP WITH TIME ZONE,
    completed_at        TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT loan_pkey     PRIMARY KEY (id),
    CONSTRAINT loan_uuid_key UNIQUE (uuid),
    CONSTRAINT fk_loan_requester FOREIGN KEY (requester_id) REFERENCES public."user"(id) ON DELETE RESTRICT,
    CONSTRAINT fk_loan_room      FOREIGN KEY (room_id)      REFERENCES public.room(id)   ON DELETE RESTRICT,
    CONSTRAINT fk_loan_subject   FOREIGN KEY (subject_id)   REFERENCES public.subject(id) ON DELETE RESTRICT
);

CREATE TABLE public.loan_detail (
    loan_id            BIGINT  NOT NULL,
    implement_id       BIGINT  NOT NULL,
    requested_quantity INTEGER NOT NULL,
    reserved_quantity  INTEGER NOT NULL DEFAULT 0,
    delivered_quantity INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT loan_detail_pkey PRIMARY KEY (loan_id, implement_id),
    CONSTRAINT fk_loan_detail_loan      FOREIGN KEY (loan_id)      REFERENCES public.loan(id)      ON DELETE CASCADE,
    CONSTRAINT fk_loan_detail_implement FOREIGN KEY (implement_id) REFERENCES public.implement(id) ON DELETE RESTRICT,
    CONSTRAINT loan_detail_requested_qty_check CHECK (requested_quantity > 0),
    CONSTRAINT loan_detail_reserved_qty_check  CHECK (reserved_quantity >= 0),
    CONSTRAINT loan_detail_delivered_qty_check CHECK (delivered_quantity >= 0),
    CONSTRAINT chk_ld_reserved_lte_requested   CHECK (reserved_quantity <= requested_quantity),
    CONSTRAINT chk_ld_delivered_lte_reserved   CHECK (delivered_quantity <= reserved_quantity)
);

CREATE TABLE public.loan_detail_individual (
    loan_id          BIGINT NOT NULL,
    implement_id     BIGINT NOT NULL,
    individual_id    BIGINT NOT NULL,
    return_condition public.individual_condition_enum,
    returned_at      TIMESTAMP WITH TIME ZONE,
    CONSTRAINT loan_detail_individual_pkey PRIMARY KEY (loan_id, implement_id, individual_id),
    CONSTRAINT fk_loan_detail_individual_parent FOREIGN KEY (loan_id, implement_id)
        REFERENCES public.loan_detail(loan_id, implement_id) ON DELETE CASCADE,
    CONSTRAINT fk_loan_detail_individual_unit FOREIGN KEY (individual_id)
        REFERENCES public.individual(id) ON DELETE RESTRICT
);

CREATE TABLE public.loan_status_history (
    id            BIGSERIAL,
    loan_id       BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    from_status   public.loan_status_enum,
    to_status     public.loan_status_enum NOT NULL,
    notes         TEXT,
    changed_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT loan_status_history_pkey PRIMARY KEY (id),
    CONSTRAINT fk_loan_history_loan  FOREIGN KEY (loan_id)       REFERENCES public.loan(id)   ON DELETE CASCADE,
    CONSTRAINT fk_loan_history_actor FOREIGN KEY (actor_user_id) REFERENCES public."user"(id) ON DELETE RESTRICT
);

-- -----------------------------------------------------------------------------
-- 6) AUDIT / TRACEABILITY
-- -----------------------------------------------------------------------------

CREATE TABLE public.inventory_movement (
    id                BIGSERIAL,
    implement_id      BIGINT  NOT NULL,
    actor_user_id     BIGINT  NOT NULL,
    movement_type     public.inventory_movement_type_enum NOT NULL,
    quantity          INTEGER NOT NULL,
    delta_changes     JSONB   NOT NULL,
    systemic_metadata JSONB,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT inventory_movement_pkey PRIMARY KEY (id),
    CONSTRAINT fk_inv_movement_implement FOREIGN KEY (implement_id)  REFERENCES public.implement(id) ON DELETE RESTRICT,
    CONSTRAINT fk_inv_movement_actor     FOREIGN KEY (actor_user_id) REFERENCES public."user"(id)    ON DELETE RESTRICT,
    CONSTRAINT inventory_movement_qty_nonzero CHECK (quantity <> 0)
);

CREATE TABLE public.audit_log (
    id             BIGSERIAL,
    event          CHARACTER VARYING NOT NULL,
    payload        JSONB             NOT NULL,
    actor_user_id  BIGINT,
    target_user_id BIGINT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT audit_log_pkey PRIMARY KEY (id),
    CONSTRAINT fk_audit_actor_user  FOREIGN KEY (actor_user_id)  REFERENCES public."user"(id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_target_user FOREIGN KEY (target_user_id) REFERENCES public."user"(id) ON DELETE SET NULL
);

-- -----------------------------------------------------------------------------
-- 7) NOTIFICATIONS
-- -----------------------------------------------------------------------------

CREATE TABLE public.notification (
    id          BIGSERIAL,
    uuid        UUID              NOT NULL DEFAULT gen_random_uuid(),
    user_id     BIGINT            NOT NULL,
    title       CHARACTER VARYING NOT NULL,
    message     TEXT              NOT NULL,
    read_status BOOLEAN           NOT NULL DEFAULT false,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT notification_pkey     PRIMARY KEY (id),
    CONSTRAINT notification_uuid_key UNIQUE (uuid),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES public."user"(id) ON DELETE CASCADE
);

CREATE TABLE public.email_outbox (
    id              BIGSERIAL,
    event_id        UUID              NOT NULL DEFAULT gen_random_uuid(),
    recipient_email CHARACTER VARYING NOT NULL,
    email_type      CHARACTER VARYING NOT NULL,
    template_data   JSONB             NOT NULL,
    status          public.outbox_status_enum NOT NULL DEFAULT 'PENDING'::public.outbox_status_enum,
    retry_count     INTEGER           NOT NULL DEFAULT 0,
    error_log       TEXT,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    processed_at    TIMESTAMP WITH TIME ZONE,
    CONSTRAINT email_outbox_pkey         PRIMARY KEY (id),
    CONSTRAINT email_outbox_event_id_key UNIQUE (event_id)
);

-- -----------------------------------------------------------------------------
-- 8) SECURITY SESSIONS + INTERNAL OUTBOX
-- -----------------------------------------------------------------------------

CREATE TABLE public.user_session (
    id                 BIGSERIAL,
    user_id            BIGINT            NOT NULL,
    refresh_token_hash CHARACTER VARYING NOT NULL,
    device_info        CHARACTER VARYING,
    expires_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT user_session_pkey PRIMARY KEY (id),
    CONSTRAINT user_session_refresh_token_hash_key UNIQUE (refresh_token_hash),
    CONSTRAINT fk_user_session_user FOREIGN KEY (user_id) REFERENCES public."user"(id) ON DELETE CASCADE
);

CREATE TABLE public.token_revocation (
    id         BIGSERIAL,
    user_id    BIGINT            NOT NULL,
    jti        CHARACTER VARYING NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT token_revocation_pkey PRIMARY KEY (id),
    CONSTRAINT token_revocation_jti_key UNIQUE (jti),
    CONSTRAINT fk_token_revocation_user FOREIGN KEY (user_id) REFERENCES public."user"(id) ON DELETE CASCADE
);

CREATE TABLE public.outbox_event (
    id             BIGSERIAL,
    event_id       UUID              NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type CHARACTER VARYING NOT NULL,
    aggregate_id   UUID,
    event_type     CHARACTER VARYING NOT NULL,
    payload        TEXT              NOT NULL,
    status         public.outbox_status_enum NOT NULL DEFAULT 'PENDING'::public.outbox_status_enum,
    retry_count    INTEGER           NOT NULL DEFAULT 0,
    occurred_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    processed_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT outbox_event_pkey PRIMARY KEY (id),
    CONSTRAINT outbox_event_id_key UNIQUE (event_id)
);

-- Optional compatibility object for old code that still points to outbox_events.
CREATE VIEW public.outbox_events AS
SELECT
    event_id,
    aggregate_type,
    aggregate_id,
    event_type,
    payload,
    occurred_at,
    processed_at,
    retry_count,
    status
FROM public.outbox_event;

-- -----------------------------------------------------------------------------
-- 9) RLS FOR SPRING BOOT SESSION VARIABLE app.current_user_uuid
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.get_current_user_id()
RETURNS BIGINT
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public, pg_temp
AS $$
    SELECT id
      FROM public."user"
     WHERE uuid = NULLIF(current_setting('app.current_user_uuid', true), '')::UUID
$$;

CREATE OR REPLACE FUNCTION public.get_current_user_role()
RETURNS CHARACTER VARYING
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public, pg_temp
AS $$
    SELECT r.name
      FROM public."user" u
      JOIN public.role r ON u.role_id = r.id
     WHERE u.uuid = NULLIF(current_setting('app.current_user_uuid', true), '')::UUID
$$;

CREATE OR REPLACE FUNCTION public.is_authenticated()
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public, pg_temp
AS $$
    SELECT NULLIF(current_setting('app.current_user_uuid', true), '') IS NOT NULL
$$;

-- Security-definer lookup used by login flow to avoid direct user-table scans
-- from application code under strict RLS.
CREATE OR REPLACE FUNCTION public.fn_auth_find_user_by_rut(p_rut TEXT)
RETURNS TABLE (
    user_uuid UUID,
    rut CHARACTER VARYING,
    password_hash CHARACTER VARYING,
    role_name CHARACTER VARYING,
    failed_login_attempts INTEGER,
    blocked_until TIMESTAMP WITH TIME ZONE
)
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public, pg_temp
SET row_security = off
AS $$
    SELECT
        u.uuid,
        u.rut,
        u.password_hash,
        r.name,
        u.failed_login_attempts,
        u.blocked_until
    FROM public."user" u
    JOIN public.role r ON r.id = u.role_id
    WHERE u.rut = CASE
                      WHEN length(replace(replace(replace(COALESCE(p_rut, ''), '.', ''), '-', ''), ' ', '')) > 1
                      THEN substring(
                          replace(replace(replace(COALESCE(p_rut, ''), '.', ''), '-', ''), ' ', ''),
                          1,
                          length(replace(replace(replace(COALESCE(p_rut, ''), '.', ''), '-', ''), ' ', '')) - 1
                      )
                      ELSE ''
                  END
      AND u.active IS TRUE
    LIMIT 1
$$;

-- Security-definer write path for audit_log under strict RLS.
CREATE OR REPLACE FUNCTION public.fn_write_audit_log(
    p_event CHARACTER VARYING,
    p_actor_user_uuid UUID,
    p_target_user_uuid UUID,
    p_payload JSONB
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
VOLATILE
SET search_path = public, pg_temp
SET row_security = off
AS $$
DECLARE
    v_actor_user_id BIGINT;
    v_target_user_id BIGINT;
BEGIN
    IF p_actor_user_uuid IS NOT NULL THEN
        SELECT id INTO v_actor_user_id FROM public."user" WHERE uuid = p_actor_user_uuid;
    END IF;

    IF p_target_user_uuid IS NOT NULL THEN
        SELECT id INTO v_target_user_id FROM public."user" WHERE uuid = p_target_user_uuid;
    END IF;

    INSERT INTO public.audit_log(event, payload, actor_user_id, target_user_id)
    VALUES (p_event, COALESCE(p_payload, '{}'::jsonb), v_actor_user_id, v_target_user_id);
END;
$$;

REVOKE ALL ON FUNCTION public.fn_auth_find_user_by_rut(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.fn_auth_find_user_by_rut(TEXT) TO PUBLIC;

REVOKE ALL ON FUNCTION public.fn_write_audit_log(CHARACTER VARYING, UUID, UUID, JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.fn_write_audit_log(CHARACTER VARYING, UUID, UUID, JSONB) TO PUBLIC;

ALTER TABLE public."user" ENABLE ROW LEVEL SECURITY;
ALTER TABLE public."user" FORCE ROW LEVEL SECURITY;

CREATE POLICY user_select_own
    ON public."user" FOR SELECT
    USING (uuid = NULLIF(current_setting('app.current_user_uuid', true), '')::UUID);

CREATE POLICY user_select_staff
    ON public."user" FOR SELECT
    USING (public.get_current_user_role() IN ('coordinador', 'director'));

CREATE POLICY user_update_own
    ON public."user" FOR UPDATE
    USING (uuid = NULLIF(current_setting('app.current_user_uuid', true), '')::UUID)
    WITH CHECK (uuid = NULLIF(current_setting('app.current_user_uuid', true), '')::UUID);

CREATE POLICY user_all_director
    ON public."user" FOR ALL
    USING (public.get_current_user_role() = 'director')
    WITH CHECK (public.get_current_user_role() = 'director');

ALTER TABLE public.loan ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.loan FORCE ROW LEVEL SECURITY;

CREATE POLICY loan_select_own
    ON public.loan FOR SELECT
    USING (requester_id = public.get_current_user_id());

CREATE POLICY loan_insert_own
    ON public.loan FOR INSERT
    WITH CHECK (requester_id = public.get_current_user_id());

CREATE POLICY loan_all_staff
    ON public.loan FOR ALL
    USING (public.get_current_user_role() IN ('coordinador', 'director'))
    WITH CHECK (public.get_current_user_role() IN ('coordinador', 'director'));

ALTER TABLE public.notification ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notification FORCE ROW LEVEL SECURITY;

CREATE POLICY notification_all_own
    ON public.notification FOR ALL
    USING (user_id = public.get_current_user_id())
    WITH CHECK (user_id = public.get_current_user_id());

CREATE POLICY notification_all_director
    ON public.notification FOR ALL
    USING (public.get_current_user_role() = 'director')
    WITH CHECK (public.get_current_user_role() = 'director');

ALTER TABLE public.audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_log FORCE ROW LEVEL SECURITY;

CREATE POLICY audit_log_select_director
    ON public.audit_log FOR SELECT
    USING (public.get_current_user_role() = 'director');

ALTER TABLE public.inventory_movement ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.inventory_movement FORCE ROW LEVEL SECURITY;

CREATE POLICY inventory_movement_select_staff
    ON public.inventory_movement FOR SELECT
    USING (public.get_current_user_role() IN ('coordinador', 'director'));

CREATE POLICY inventory_movement_insert_staff
    ON public.inventory_movement FOR INSERT
    WITH CHECK (public.get_current_user_role() IN ('coordinador', 'director'));

ALTER TABLE public.stock ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.stock FORCE ROW LEVEL SECURITY;

CREATE POLICY stock_select_authenticated
    ON public.stock FOR SELECT
    USING (public.is_authenticated());

CREATE POLICY stock_modify_staff
    ON public.stock FOR ALL
    USING (public.get_current_user_role() IN ('coordinador', 'director'))
    WITH CHECK (public.get_current_user_role() IN ('coordinador', 'director'));
