-- Flyway callback: guarantees enum availability for historical migrations (V29/V30)
-- on bootstrap from an empty database. No business-logic changes.
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
