-- =============================================================================
-- V29 - Loan flow data refinement
-- Normalizes loan lifecycle storage, enriches enums, and adds helper DB objects.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) New enum definitions (v2 / additional)
-- -----------------------------------------------------------------------------

CREATE TYPE public.item_type_enum_v2 AS ENUM (
    'consumable',
    'reusable',
    'individual'
);

CREATE TYPE public.individual_status_enum_v2 AS ENUM (
    'available',
    'loaned',
    'maintenance',
    'damaged',
    'blocked',
    'retired'
);

CREATE TYPE public.individual_condition_enum_v2 AS ENUM (
    'good',
    'damaged_repairable',
    'damaged_no_diagnosis',
    'irreparable'
);

CREATE TYPE public.loan_status_enum_v2 AS ENUM (
    'pending',
    'approved',
    'prepared',
    'delivered',
    'completed',
    'rejected',
    'cancelled',
    'expired',
    'overdue'
);

CREATE TYPE public.inventory_movement_type_enum_v2 AS ENUM (
    'stock_in',
    'stock_out',
    'loan_delivery',
    'loan_return',
    'damage_report',
    'manual_adjustment',
    'consumption',
    'discard',
    'loss'
);

CREATE TYPE public.individual_allocation_status_enum AS ENUM (
    'reserved',
    'prepared',
    'delivered',
    'returned',
    'cancelled'
);

CREATE TYPE public.return_condition_enum_v2 AS ENUM (
    'good',
    'damaged',
    'lost',
    'discarded'
);

-- -----------------------------------------------------------------------------
-- 2) Migrate columns to new enum types
-- -----------------------------------------------------------------------------

ALTER TABLE public.implement
    ALTER COLUMN item_type DROP DEFAULT;

ALTER TABLE public.implement
    ADD COLUMN item_type_v2_text TEXT;

UPDATE public.implement
SET item_type_v2_text = CASE
    WHEN item_type::text = 'fungible' THEN 'consumable'
    ELSE 'reusable'
END;

UPDATE public.implement i
SET item_type_v2_text = 'individual'
WHERE EXISTS (
    SELECT 1
    FROM public.individual ind
    WHERE ind.implement_id = i.id
);

ALTER TABLE public.implement
    ALTER COLUMN item_type TYPE public.item_type_enum_v2
    USING item_type_v2_text::public.item_type_enum_v2;

ALTER TABLE public.implement
    DROP COLUMN item_type_v2_text;

ALTER TABLE public.implement
    ALTER COLUMN item_type SET DEFAULT 'reusable'::public.item_type_enum_v2;

ALTER TABLE public.individual
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE public.individual
    ALTER COLUMN status TYPE public.individual_status_enum_v2
    USING status::text::public.individual_status_enum_v2;

ALTER TABLE public.individual
    ALTER COLUMN status SET DEFAULT 'available'::public.individual_status_enum_v2;

ALTER TABLE public.loan_detail_individual
    ALTER COLUMN return_condition TYPE public.return_condition_enum_v2
    USING (
        CASE
            WHEN return_condition IS NULL THEN NULL
            WHEN return_condition::text = 'good' THEN 'good'
            WHEN return_condition::text IN ('fair', 'poor', 'damaged') THEN 'damaged'
            ELSE 'damaged'
        END
    )::public.return_condition_enum_v2;

ALTER TABLE public.individual
    ALTER COLUMN condition DROP DEFAULT;

ALTER TABLE public.individual
    ALTER COLUMN condition TYPE public.individual_condition_enum_v2
    USING (
        CASE
            WHEN condition::text = 'good' THEN 'good'
            WHEN condition::text = 'fair' THEN 'damaged_repairable'
            WHEN condition::text = 'poor' THEN 'damaged_no_diagnosis'
            ELSE 'good'
        END
    )::public.individual_condition_enum_v2;

ALTER TABLE public.individual
    ALTER COLUMN condition SET DEFAULT 'good'::public.individual_condition_enum_v2;

ALTER TABLE public.loan
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE public.loan
    ALTER COLUMN status TYPE public.loan_status_enum_v2
    USING status::text::public.loan_status_enum_v2;

ALTER TABLE public.loan_status_history
    ALTER COLUMN from_status TYPE public.loan_status_enum_v2
    USING (
        CASE
            WHEN from_status IS NULL THEN NULL::public.loan_status_enum_v2
            ELSE from_status::text::public.loan_status_enum_v2
        END
    );

ALTER TABLE public.loan_status_history
    ALTER COLUMN to_status TYPE public.loan_status_enum_v2
    USING to_status::text::public.loan_status_enum_v2;

ALTER TABLE public.loan
    ALTER COLUMN status SET DEFAULT 'pending'::public.loan_status_enum_v2;

ALTER TABLE public.inventory_movement
    ALTER COLUMN movement_type TYPE public.inventory_movement_type_enum_v2
    USING (
        CASE movement_type::text
            WHEN 'STOCK_IN' THEN 'stock_in'
            WHEN 'STOCK_OUT' THEN 'stock_out'
            WHEN 'LOAN_DELIVERY' THEN 'loan_delivery'
            WHEN 'LOAN_RETURN' THEN 'loan_return'
            WHEN 'DAMAGE_REPORT' THEN 'damage_report'
            WHEN 'MANUAL_ADJUSTMENT' THEN 'manual_adjustment'
            ELSE lower(movement_type::text)
        END
    )::public.inventory_movement_type_enum_v2;

-- -----------------------------------------------------------------------------
-- 3) Swap enum names and remove legacy enum types
-- -----------------------------------------------------------------------------

ALTER TYPE public.item_type_enum RENAME TO item_type_enum_legacy;
ALTER TYPE public.item_type_enum_v2 RENAME TO item_type_enum;
DROP TYPE public.item_type_enum_legacy;

ALTER TYPE public.individual_status_enum RENAME TO individual_status_enum_legacy;
ALTER TYPE public.individual_status_enum_v2 RENAME TO individual_status_enum;
DROP TYPE public.individual_status_enum_legacy;

ALTER TYPE public.individual_condition_enum RENAME TO individual_condition_enum_legacy;
ALTER TYPE public.individual_condition_enum_v2 RENAME TO individual_condition_enum;
DROP TYPE public.individual_condition_enum_legacy;

ALTER TYPE public.loan_status_enum RENAME TO loan_status_enum_legacy;
ALTER TYPE public.loan_status_enum_v2 RENAME TO loan_status_enum;
DROP TYPE public.loan_status_enum_legacy;

ALTER TYPE public.inventory_movement_type_enum RENAME TO inventory_movement_type_enum_legacy;
ALTER TYPE public.inventory_movement_type_enum_v2 RENAME TO inventory_movement_type_enum;
DROP TYPE public.inventory_movement_type_enum_legacy;

ALTER TYPE public.return_condition_enum RENAME TO return_condition_enum_legacy;
ALTER TYPE public.return_condition_enum_v2 RENAME TO return_condition_enum;
DROP TYPE public.return_condition_enum_legacy;

-- -----------------------------------------------------------------------------
-- 4) Loan/loan detail structural refinement
-- -----------------------------------------------------------------------------

ALTER TABLE public.loan
    ADD COLUMN expected_return_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();

UPDATE public.loan
SET expected_return_at = COALESCE(due_date, scheduled_at + INTERVAL '2 hour'),
    updated_at = COALESCE(completed_at, delivered_at, approved_at, created_at, now())
WHERE expected_return_at IS NULL;

ALTER TABLE public.loan
    ALTER COLUMN expected_return_at SET NOT NULL;

ALTER TABLE public.loan_detail_individual
    ADD COLUMN allocation_status public.individual_allocation_status_enum NOT NULL
        DEFAULT 'delivered'::public.individual_allocation_status_enum;

UPDATE public.loan_detail_individual
SET allocation_status = CASE
    WHEN returned_at IS NOT NULL THEN 'returned'::public.individual_allocation_status_enum
    ELSE 'delivered'::public.individual_allocation_status_enum
END;

-- -----------------------------------------------------------------------------
-- 5) Migrate legacy loan notes/timestamps into loan_status_history
-- -----------------------------------------------------------------------------

INSERT INTO public.loan_status_history (loan_id, actor_user_id, from_status, to_status, notes, changed_at)
SELECT
    l.id,
    COALESCE(
        (SELECT u.id FROM public."user" u WHERE u.uuid = '99999999-9999-9999-9999-999999999999'::UUID),
        l.requester_id
    ) AS actor_user_id,
    'pending'::public.loan_status_enum,
    'approved'::public.loan_status_enum,
    COALESCE(NULLIF(btrim(l.review_notes), ''), 'Migrated from legacy approved_at'),
    COALESCE(l.approved_at, l.created_at)
FROM public.loan l
WHERE l.approved_at IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM public.loan_status_history h
      WHERE h.loan_id = l.id
        AND h.to_status = 'approved'::public.loan_status_enum
  );

INSERT INTO public.loan_status_history (loan_id, actor_user_id, from_status, to_status, notes, changed_at)
SELECT
    l.id,
    COALESCE(
        (SELECT u.id FROM public."user" u WHERE u.uuid = '99999999-9999-9999-9999-999999999999'::UUID),
        l.requester_id
    ) AS actor_user_id,
    CASE
        WHEN EXISTS (
            SELECT 1
            FROM public.loan_status_history h
            WHERE h.loan_id = l.id
              AND h.to_status = 'prepared'::public.loan_status_enum
        ) THEN 'prepared'::public.loan_status_enum
        ELSE 'approved'::public.loan_status_enum
    END AS from_status,
    'delivered'::public.loan_status_enum,
    'Migrated from legacy delivered_at',
    COALESCE(l.delivered_at, l.created_at)
FROM public.loan l
WHERE l.delivered_at IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM public.loan_status_history h
      WHERE h.loan_id = l.id
        AND h.to_status = 'delivered'::public.loan_status_enum
  );

INSERT INTO public.loan_status_history (loan_id, actor_user_id, from_status, to_status, notes, changed_at)
SELECT
    l.id,
    COALESCE(
        (SELECT u.id FROM public."user" u WHERE u.uuid = '99999999-9999-9999-9999-999999999999'::UUID),
        l.requester_id
    ) AS actor_user_id,
    'delivered'::public.loan_status_enum,
    'completed'::public.loan_status_enum,
    'Migrated from legacy completed_at',
    COALESCE(l.completed_at, l.created_at)
FROM public.loan l
WHERE l.completed_at IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM public.loan_status_history h
      WHERE h.loan_id = l.id
        AND h.to_status = 'completed'::public.loan_status_enum
  );

UPDATE public.loan_status_history h
SET notes = l.rejection_reason
FROM public.loan l
WHERE h.loan_id = l.id
  AND h.to_status = 'rejected'::public.loan_status_enum
  AND l.rejection_reason IS NOT NULL
  AND (h.notes IS NULL OR btrim(h.notes) = '');

INSERT INTO public.loan_status_history (loan_id, actor_user_id, from_status, to_status, notes, changed_at)
SELECT
    l.id,
    COALESCE(
        (SELECT u.id FROM public."user" u WHERE u.uuid = '99999999-9999-9999-9999-999999999999'::UUID),
        l.requester_id
    ) AS actor_user_id,
    'pending'::public.loan_status_enum,
    'rejected'::public.loan_status_enum,
    l.rejection_reason,
    l.created_at
FROM public.loan l
WHERE l.status = 'rejected'::public.loan_status_enum
  AND l.rejection_reason IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM public.loan_status_history h
      WHERE h.loan_id = l.id
        AND h.to_status = 'rejected'::public.loan_status_enum
  );

UPDATE public.loan_status_history h
SET notes = l.cancellation_reason
FROM public.loan l
WHERE h.loan_id = l.id
  AND h.to_status = 'cancelled'::public.loan_status_enum
  AND l.cancellation_reason IS NOT NULL
  AND (h.notes IS NULL OR btrim(h.notes) = '');

INSERT INTO public.loan_status_history (loan_id, actor_user_id, from_status, to_status, notes, changed_at)
SELECT
    l.id,
    COALESCE(
        (SELECT u.id FROM public."user" u WHERE u.uuid = '99999999-9999-9999-9999-999999999999'::UUID),
        l.requester_id
    ) AS actor_user_id,
    'pending'::public.loan_status_enum,
    'cancelled'::public.loan_status_enum,
    l.cancellation_reason,
    l.created_at
FROM public.loan l
WHERE l.status = 'cancelled'::public.loan_status_enum
  AND l.cancellation_reason IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM public.loan_status_history h
      WHERE h.loan_id = l.id
        AND h.to_status = 'cancelled'::public.loan_status_enum
  );

UPDATE public.loan_status_history h
SET notes = l.review_notes
FROM public.loan l
WHERE h.loan_id = l.id
  AND h.to_status = 'approved'::public.loan_status_enum
  AND l.review_notes IS NOT NULL
  AND (h.notes IS NULL OR btrim(h.notes) = '');

-- -----------------------------------------------------------------------------
-- 6) Drop legacy columns no longer stored in public.loan
-- -----------------------------------------------------------------------------

ALTER TABLE public.loan
    DROP COLUMN IF EXISTS due_date,
    DROP COLUMN IF EXISTS approved_at,
    DROP COLUMN IF EXISTS prepared_at,
    DROP COLUMN IF EXISTS delivered_at,
    DROP COLUMN IF EXISTS completed_at,
    DROP COLUMN IF EXISTS review_notes,
    DROP COLUMN IF EXISTS rejection_reason,
    DROP COLUMN IF EXISTS cancellation_reason;

-- -----------------------------------------------------------------------------
-- 7) Triggers/functions/views for refined data flow
-- -----------------------------------------------------------------------------

DROP TRIGGER IF EXISTS trg_guard_individual_no_fungible ON public.individual;
DROP TRIGGER IF EXISTS trg_guard_individual_item_type ON public.individual;
DROP FUNCTION IF EXISTS public.fn_guard_individual_no_fungible() CASCADE;
DROP FUNCTION IF EXISTS public.fn_guard_individual_item_type() CASCADE;

CREATE OR REPLACE FUNCTION public.fn_guard_individual_item_type()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_item_type public.item_type_enum;
BEGIN
    SELECT i.item_type
      INTO v_item_type
      FROM public.implement i
     WHERE i.id = NEW.implement_id;

    IF v_item_type <> 'individual'::public.item_type_enum THEN
        RAISE EXCEPTION
            'Integrity violation: implement_id=% has item_type=%, only item_type=individual can create physical units',
            NEW.implement_id, v_item_type;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_guard_individual_item_type
    BEFORE INSERT OR UPDATE OF implement_id ON public.individual
    FOR EACH ROW
    EXECUTE FUNCTION public.fn_guard_individual_item_type();

CREATE OR REPLACE FUNCTION public.fn_set_loan_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_set_loan_updated_at ON public.loan;
CREATE TRIGGER trg_set_loan_updated_at
    BEFORE UPDATE ON public.loan
    FOR EACH ROW
    EXECUTE FUNCTION public.fn_set_loan_updated_at();

CREATE OR REPLACE FUNCTION public.fn_is_valid_loan_status_transition(
    p_from public.loan_status_enum,
    p_to public.loan_status_enum
)
RETURNS BOOLEAN
LANGUAGE sql
IMMUTABLE
AS $$
SELECT
    CASE
        WHEN p_from = p_to THEN TRUE
        WHEN p_from = 'pending'   AND p_to IN ('approved', 'rejected', 'cancelled', 'expired') THEN TRUE
        WHEN p_from = 'approved'  AND p_to IN ('prepared', 'delivered', 'cancelled', 'expired') THEN TRUE
        WHEN p_from = 'prepared'  AND p_to IN ('delivered', 'cancelled', 'expired') THEN TRUE
        WHEN p_from = 'delivered' AND p_to IN ('overdue', 'completed') THEN TRUE
        WHEN p_from = 'overdue'   AND p_to = 'completed' THEN TRUE
        ELSE FALSE
    END;
$$;

CREATE OR REPLACE FUNCTION public.fn_validate_loan_status_transition()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status
       AND NOT public.fn_is_valid_loan_status_transition(OLD.status, NEW.status) THEN
        RAISE EXCEPTION
            'Invalid loan status transition: % -> %',
            OLD.status, NEW.status;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_loan_status_transition ON public.loan;
CREATE TRIGGER trg_validate_loan_status_transition
    BEFORE UPDATE OF status ON public.loan
    FOR EACH ROW
    EXECUTE FUNCTION public.fn_validate_loan_status_transition();

CREATE OR REPLACE FUNCTION public.fn_loan_change_status(
    p_loan_uuid UUID,
    p_actor_user_uuid UUID,
    p_to_status public.loan_status_enum,
    p_notes TEXT DEFAULT NULL
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_loan_id BIGINT;
    v_actor_id BIGINT;
    v_from_status public.loan_status_enum;
BEGIN
    SELECT l.id, l.status
      INTO v_loan_id, v_from_status
      FROM public.loan l
     WHERE l.uuid = p_loan_uuid
     FOR UPDATE;

    IF v_loan_id IS NULL THEN
        RAISE EXCEPTION 'Loan not found for uuid=%', p_loan_uuid;
    END IF;

    SELECT u.id
      INTO v_actor_id
      FROM public."user" u
     WHERE u.uuid = p_actor_user_uuid;

    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'Actor user not found for uuid=%', p_actor_user_uuid;
    END IF;

    IF NOT public.fn_is_valid_loan_status_transition(v_from_status, p_to_status) THEN
        RAISE EXCEPTION 'Invalid transition % -> %', v_from_status, p_to_status;
    END IF;

    UPDATE public.loan
       SET status = p_to_status
     WHERE id = v_loan_id;

    INSERT INTO public.loan_status_history (
        loan_id,
        actor_user_id,
        from_status,
        to_status,
        notes,
        changed_at
    )
    VALUES (
        v_loan_id,
        v_actor_id,
        v_from_status,
        p_to_status,
        NULLIF(btrim(p_notes), ''),
        now()
    );
END;
$$;

CREATE OR REPLACE VIEW public.v_loan_state_dates AS
SELECT
    l.id,
    l.uuid,
    l.requester_id,
    l.room_id,
    l.subject_id,
    l.status,
    l.scheduled_at,
    l.expected_return_at,
    l.created_at,
    l.updated_at,
    MAX(CASE WHEN h.to_status = 'approved'  THEN h.changed_at END) AS approved_at,
    MAX(CASE WHEN h.to_status = 'prepared'  THEN h.changed_at END) AS prepared_at,
    MAX(CASE WHEN h.to_status = 'delivered' THEN h.changed_at END) AS delivered_at,
    MAX(CASE WHEN h.to_status = 'completed' THEN h.changed_at END) AS completed_at
FROM public.loan l
LEFT JOIN public.loan_status_history h
       ON h.loan_id = l.id
GROUP BY
    l.id, l.uuid, l.requester_id, l.room_id, l.subject_id,
    l.status, l.scheduled_at, l.expected_return_at, l.created_at, l.updated_at;

CREATE OR REPLACE VIEW public.v_loan_status_timeline AS
SELECT
    h.id,
    l.uuid AS loan_uuid,
    h.loan_id,
    h.actor_user_id,
    u.uuid AS actor_uuid,
    u.name AS actor_name,
    h.from_status,
    h.to_status,
    h.notes,
    h.changed_at
FROM public.loan_status_history h
JOIN public.loan l
  ON l.id = h.loan_id
LEFT JOIN public."user" u
  ON u.id = h.actor_user_id;

CREATE INDEX IF NOT EXISTS idx_loan_status_history_loan_changed_at
    ON public.loan_status_history (loan_id, changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_loan_status_history_to_status
    ON public.loan_status_history (to_status);
