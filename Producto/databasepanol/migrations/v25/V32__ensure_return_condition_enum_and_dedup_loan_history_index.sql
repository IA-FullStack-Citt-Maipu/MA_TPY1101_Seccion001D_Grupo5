-- V32 minimal hardening
-- 1) Ensure return_condition_enum exists (idempotent guard)
-- 2) Remove duplicate loan_status_history index, preserving idx_loan_history_loan_changed

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname = 'public'
          AND t.typname = 'return_condition_enum'
    ) THEN
        CREATE TYPE public.return_condition_enum AS ENUM (
            'good',
            'damaged',
            'lost',
            'discarded'
        );
    END IF;
END
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_indexes i
        WHERE i.schemaname = 'public'
          AND i.indexname = 'idx_loan_status_history_loan_changed_at'
    ) THEN
        EXECUTE 'DROP INDEX public.idx_loan_status_history_loan_changed_at';
    END IF;
END
$$;
