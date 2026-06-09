-- Fix column ambiguity in loan flow SQL functions (strictly alias qualification)

DROP FUNCTION IF EXISTS public.fn_approve_loan(BIGINT, BIGINT, TEXT);
CREATE OR REPLACE FUNCTION public.fn_approve_loan(
    p_loan_id BIGINT,
    p_actor_user_id BIGINT,
    p_notes TEXT DEFAULT NULL
)
RETURNS TABLE (
    loan_id BIGINT,
    new_status public.loan_status_enum,
    approved_at TIMESTAMP WITH TIME ZONE
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
SET row_security = off
AS $$
DECLARE
    v_status public.loan_status_enum;
    v_scheduled_at TIMESTAMP WITH TIME ZONE;
    v_expected_return_at TIMESTAMP WITH TIME ZONE;
    v_approved_at TIMESTAMP WITH TIME ZONE;
    v_detail RECORD;
    v_availability RECORD;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public."user" u WHERE u.id = p_actor_user_id) THEN
        RAISE EXCEPTION 'Actor user not found for id=%', p_actor_user_id;
    END IF;

    SELECT l.status, l.scheduled_at, l.expected_return_at
      INTO v_status, v_scheduled_at, v_expected_return_at
      FROM public.loan l
     WHERE l.id = p_loan_id
     FOR UPDATE;

    IF v_status IS NULL THEN
        RAISE EXCEPTION 'Loan not found for id=%', p_loan_id;
    END IF;

    IF v_status <> 'pending' THEN
        RAISE EXCEPTION 'Loan % is not pending (current status=%)', p_loan_id, v_status;
    END IF;

    PERFORM 1
      FROM public.stock s
      JOIN public.loan_detail ld
        ON ld.implement_id = s.implement_id
     WHERE ld.loan_id = p_loan_id
     FOR UPDATE OF s;

    FOR v_detail IN
        SELECT ld.implement_id, ld.requested_quantity
          FROM public.loan_detail ld
         WHERE ld.loan_id = p_loan_id
         FOR UPDATE
    LOOP
        SELECT *
          INTO v_availability
          FROM public.fn_get_implement_availability(
                v_detail.implement_id,
                v_scheduled_at,
                v_expected_return_at,
                false,
                p_loan_id
          );

        IF COALESCE(v_availability.available_quantity, 0) < v_detail.requested_quantity THEN
            RAISE EXCEPTION
                'Insufficient availability for implement_id=%. available=% requested=%',
                v_detail.implement_id,
                COALESCE(v_availability.available_quantity, 0),
                v_detail.requested_quantity;
        END IF;
    END LOOP;

    UPDATE public.loan_detail AS ld
       SET reserved_quantity = ld.requested_quantity
     WHERE ld.loan_id = p_loan_id;

    SELECT r.changed_at
      INTO v_approved_at
      FROM public.fn_loan_change_status(
            p_loan_id,
            'approved'::public.loan_status_enum,
            p_actor_user_id,
            COALESCE(NULLIF(btrim(p_notes), ''), 'Solicitud aprobada por disponibilidad.')
      ) AS r;

    RETURN QUERY
    SELECT p_loan_id, 'approved'::public.loan_status_enum, v_approved_at;
END;
$$;

DROP FUNCTION IF EXISTS public.fn_expire_pending_loans(BIGINT, TIMESTAMP WITH TIME ZONE, INTEGER);
CREATE OR REPLACE FUNCTION public.fn_expire_pending_loans(
    p_actor_user_id BIGINT,
    p_now TIMESTAMP WITH TIME ZONE DEFAULT now(),
    p_grace_minutes INTEGER DEFAULT 120
)
RETURNS TABLE (
    expired_count INTEGER
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
SET row_security = off
AS $$
DECLARE
    v_loan RECORD;
    v_detail RECORD;
    v_expired_count INTEGER := 0;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public."user" u WHERE u.id = p_actor_user_id) THEN
        RAISE EXCEPTION 'Actor user not found for id=%', p_actor_user_id;
    END IF;

    FOR v_loan IN
        SELECT l.id, l.status
          FROM public.loan l
         WHERE l.status IN ('pending', 'approved', 'prepared')
           AND p_now > (l.scheduled_at + make_interval(mins => p_grace_minutes))
         FOR UPDATE SKIP LOCKED
    LOOP
        IF v_loan.status = 'prepared' THEN
            FOR v_detail IN
                SELECT ld.implement_id, ld.reserved_quantity
                  FROM public.loan_detail ld
                 WHERE ld.loan_id = v_loan.id
                   AND ld.reserved_quantity > 0
                 FOR UPDATE OF ld
            LOOP
                UPDATE public.stock s
                   SET reserved = s.reserved - v_detail.reserved_quantity,
                       available = s.available + v_detail.reserved_quantity,
                       updated_at = now()
                 WHERE s.implement_id = v_detail.implement_id
                   AND s.reserved >= v_detail.reserved_quantity;

                IF NOT FOUND THEN
                    RAISE EXCEPTION
                        'Stock release failed while expiring prepared loan. loan_id=% implement_id=%',
                        v_loan.id,
                        v_detail.implement_id;
                END IF;
            END LOOP;
        END IF;

        IF v_loan.status IN ('approved', 'prepared') THEN
            UPDATE public.loan_detail AS ld
               SET reserved_quantity = 0
             WHERE ld.loan_id = v_loan.id;

            UPDATE public.loan_detail_individual ldi
               SET allocation_status = 'cancelled'::public.individual_allocation_status_enum
             WHERE ldi.loan_id = v_loan.id
               AND ldi.allocation_status IN (
                   'reserved'::public.individual_allocation_status_enum,
                   'prepared'::public.individual_allocation_status_enum
               );
        END IF;

        PERFORM *
          FROM public.fn_loan_change_status(
                v_loan.id,
                'expired'::public.loan_status_enum,
                p_actor_user_id,
                format(
                    'Expirado automaticamente por superar %s minutos de gracia.',
                    p_grace_minutes
                )
          );

        v_expired_count := v_expired_count + 1;
    END LOOP;

    RETURN QUERY
    SELECT v_expired_count;
END;
$$;
