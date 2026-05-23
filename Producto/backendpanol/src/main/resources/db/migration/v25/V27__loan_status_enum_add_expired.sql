DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_enum
        WHERE enumlabel = 'expired'
          AND enumtypid = (SELECT oid FROM pg_type WHERE typname = 'loan_status_enum')
    ) THEN
        ALTER TYPE public.loan_status_enum ADD VALUE 'expired' AFTER 'cancelled';
    END IF;
END;
$$;
