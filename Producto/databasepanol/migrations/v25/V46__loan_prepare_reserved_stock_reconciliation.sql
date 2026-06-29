CREATE OR REPLACE FUNCTION public.fn_prepare_loan(
    p_loan_id BIGINT,
    p_actor_user_id BIGINT,
    p_notes TEXT DEFAULT NULL
) RETURNS TABLE(loan_id BIGINT, new_status public.loan_status_enum, prepared_at TIMESTAMP WITH TIME ZONE)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public', 'pg_temp'
SET row_security TO 'off'
AS $$
DECLARE
    v_status public.loan_status_enum;
    v_detail RECORD;
    v_stock_available INTEGER;
    v_stock_reserved INTEGER;
    v_reserved_other_prepared INTEGER;
    v_free_reserved INTEGER;
    v_additional_reserved_needed INTEGER;
    v_assigned_count INTEGER;
    v_prepared_at TIMESTAMP WITH TIME ZONE;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public."user" u WHERE u.id = p_actor_user_id) THEN
        RAISE EXCEPTION 'Actor user not found for id=%', p_actor_user_id;
    END IF;

    SELECT l.status
      INTO v_status
      FROM public.loan l
     WHERE l.id = p_loan_id
     FOR UPDATE;

    IF v_status IS NULL THEN
        RAISE EXCEPTION 'Loan not found for id=%', p_loan_id;
    END IF;

    IF v_status <> 'approved' THEN
        RAISE EXCEPTION 'Loan % is not approved (current status=%)', p_loan_id, v_status;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM public.loan_detail ld
         WHERE ld.loan_id = p_loan_id
           AND ld.reserved_quantity > 0
    ) THEN
        RAISE EXCEPTION 'Loan % has no reserved quantities to prepare', p_loan_id;
    END IF;

    FOR v_detail IN
        SELECT ld.implement_id, ld.reserved_quantity, i.item_type
          FROM public.loan_detail ld
          JOIN public.implement i
            ON i.id = ld.implement_id
         WHERE ld.loan_id = p_loan_id
           AND ld.reserved_quantity > 0
         FOR UPDATE OF ld
    LOOP
        SELECT s.available, s.reserved
          INTO v_stock_available, v_stock_reserved
          FROM public.stock s
         WHERE s.implement_id = v_detail.implement_id
         FOR UPDATE;

        IF v_stock_available IS NULL OR v_stock_reserved IS NULL THEN
            RAISE EXCEPTION 'Stock row not found for implement_id=%', v_detail.implement_id;
        END IF;

        SELECT COALESCE(SUM(ld.reserved_quantity), 0)::INTEGER
          INTO v_reserved_other_prepared
          FROM public.loan_detail ld
          JOIN public.loan l
            ON l.id = ld.loan_id
         WHERE ld.implement_id = v_detail.implement_id
           AND l.id <> p_loan_id
           AND l.status = 'prepared'::public.loan_status_enum;

        v_free_reserved := GREATEST(COALESCE(v_stock_reserved, 0) - COALESCE(v_reserved_other_prepared, 0), 0);
        v_additional_reserved_needed := GREATEST(v_detail.reserved_quantity - v_free_reserved, 0);

        IF v_stock_available < v_additional_reserved_needed THEN
            RAISE EXCEPTION
                'Insufficient stock for prepare. implement_id=% available=% free_reserved=% reserved_required=%',
                v_detail.implement_id,
                v_stock_available,
                v_free_reserved,
                v_detail.reserved_quantity;
        END IF;

        IF v_additional_reserved_needed > 0 THEN
            UPDATE public.stock s
               SET available = s.available - v_additional_reserved_needed,
                   reserved = s.reserved + v_additional_reserved_needed,
                   updated_at = now()
             WHERE s.implement_id = v_detail.implement_id
               AND s.available >= v_additional_reserved_needed;

            IF NOT FOUND THEN
                RAISE EXCEPTION
                    'Unable to reconcile reserved stock for prepare. implement_id=% required=%',
                    v_detail.implement_id,
                    v_additional_reserved_needed;
            END IF;
        END IF;

        IF v_detail.item_type = 'individual'::public.item_type_enum THEN
            SELECT COUNT(*)
              INTO v_assigned_count
              FROM public.loan_detail_individual ldi
             WHERE ldi.loan_id = p_loan_id
               AND ldi.implement_id = v_detail.implement_id;

            IF v_assigned_count < v_detail.reserved_quantity THEN
                RAISE EXCEPTION
                    'Individual assignments missing for implement_id=%. assigned=% required=%',
                    v_detail.implement_id,
                    v_assigned_count,
                    v_detail.reserved_quantity;
            END IF;

            UPDATE public.loan_detail_individual ldi
               SET allocation_status = 'prepared'::public.individual_allocation_status_enum
             WHERE ldi.loan_id = p_loan_id
               AND ldi.implement_id = v_detail.implement_id
               AND ldi.allocation_status = 'reserved'::public.individual_allocation_status_enum;
        END IF;
    END LOOP;

    SELECT r.changed_at
      INTO v_prepared_at
      FROM public.fn_loan_change_status(
            p_loan_id,
            'prepared'::public.loan_status_enum,
            p_actor_user_id,
            COALESCE(NULLIF(btrim(p_notes), ''), 'Implementos separados fisicamente.')
      ) AS r;

    RETURN QUERY
    SELECT p_loan_id, 'prepared'::public.loan_status_enum, v_prepared_at;
END;
$$;
