CREATE OR REPLACE FUNCTION public.fn_cancel_loan(
    p_loan_id BIGINT,
    p_actor_user_id BIGINT,
    p_notes TEXT DEFAULT NULL
)
RETURNS TABLE (
    loan_id BIGINT,
    new_status public.loan_status_enum,
    cancelled_at TIMESTAMP WITH TIME ZONE
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
SET row_security = off
AS $$
DECLARE
    v_status public.loan_status_enum;
    v_detail RECORD;
    v_cancelled_at TIMESTAMP WITH TIME ZONE;
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

    IF v_status NOT IN ('pending', 'approved', 'prepared') THEN
        RAISE EXCEPTION 'Loan % cannot be cancelled from status=%', p_loan_id, v_status;
    END IF;

    IF v_status = 'prepared' THEN
        FOR v_detail IN
            SELECT ld.implement_id, ld.reserved_quantity
              FROM public.loan_detail ld
             WHERE ld.loan_id = p_loan_id
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
                    'Stock release failed while cancelling prepared loan. implement_id=%',
                    v_detail.implement_id;
            END IF;
        END LOOP;
    END IF;

    IF v_status IN ('approved', 'prepared') THEN
        UPDATE public.loan_detail_individual ldi
           SET allocation_status = 'cancelled'::public.individual_allocation_status_enum
         WHERE ldi.loan_id = p_loan_id
           AND ldi.allocation_status IN (
               'reserved'::public.individual_allocation_status_enum,
               'prepared'::public.individual_allocation_status_enum
           );
    END IF;

    SELECT r.changed_at
      INTO v_cancelled_at
      FROM public.fn_loan_change_status(
            p_loan_id,
            'cancelled'::public.loan_status_enum,
            p_actor_user_id,
            COALESCE(NULLIF(btrim(p_notes), ''), 'Solicitud cancelada.')
      ) AS r;

    RETURN QUERY
    SELECT p_loan_id, 'cancelled'::public.loan_status_enum, v_cancelled_at;
END;
$$;

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

UPDATE public.loan_detail ld
   SET reserved_quantity = ld.requested_quantity
  FROM public.loan l
 WHERE l.id = ld.loan_id
   AND l.status IN ('cancelled'::public.loan_status_enum, 'expired'::public.loan_status_enum)
   AND ld.requested_quantity > 0
   AND ld.reserved_quantity = 0
   AND ld.delivered_quantity = 0
   AND COALESCE(ld.returned_quantity, 0) = 0
   AND COALESCE(ld.damaged_quantity, 0) = 0
   AND COALESCE(ld.lost_quantity, 0) = 0
   AND COALESCE(ld.consumed_quantity, 0) = 0
   AND COALESCE(ld.discarded_quantity, 0) = 0;

WITH individual_state AS (
    SELECT
        i.id AS implement_id,
        COUNT(ind.id)::INTEGER AS total_stock,
        COUNT(ind.id) FILTER (
            WHERE ind.status = 'loaned'::public.individual_status_enum
        )::INTEGER AS loaned_count,
        COUNT(ind.id) FILTER (
            WHERE ind.status IN (
                'damaged'::public.individual_status_enum,
                'maintenance'::public.individual_status_enum,
                'blocked'::public.individual_status_enum
            )
        )::INTEGER AS damaged_count
    FROM public.implement i
    LEFT JOIN public.individual ind
      ON ind.implement_id = i.id
     AND ind.active = true
    WHERE i.item_type = 'individual'::public.item_type_enum
    GROUP BY i.id
),
individual_reserved AS (
    SELECT
        ldi.implement_id,
        COUNT(*)::INTEGER AS reserved_count
    FROM public.loan_detail_individual ldi
    JOIN public.loan l
      ON l.id = ldi.loan_id
    WHERE l.status IN ('approved'::public.loan_status_enum, 'prepared'::public.loan_status_enum)
      AND ldi.allocation_status IN (
          'reserved'::public.individual_allocation_status_enum,
          'prepared'::public.individual_allocation_status_enum
      )
    GROUP BY ldi.implement_id
),
bulk_reserved AS (
    SELECT
        ld.implement_id,
        COALESCE(SUM(ld.reserved_quantity), 0)::INTEGER AS reserved_quantity
    FROM public.loan_detail ld
    JOIN public.loan l
      ON l.id = ld.loan_id
    JOIN public.implement i
      ON i.id = ld.implement_id
    WHERE i.item_type <> 'individual'::public.item_type_enum
      AND l.status IN ('approved'::public.loan_status_enum, 'prepared'::public.loan_status_enum)
    GROUP BY ld.implement_id
),
bulk_loaned AS (
    SELECT
        ld.implement_id,
        COALESCE(SUM(GREATEST(
            ld.delivered_quantity
            - COALESCE(ld.returned_quantity, 0)
            - COALESCE(ld.damaged_quantity, 0)
            - COALESCE(ld.lost_quantity, 0)
            - COALESCE(ld.consumed_quantity, 0)
            - COALESCE(ld.discarded_quantity, 0),
            0
        )), 0)::INTEGER AS loaned_quantity
    FROM public.loan_detail ld
    JOIN public.loan l
      ON l.id = ld.loan_id
    JOIN public.implement i
      ON i.id = ld.implement_id
    WHERE i.item_type <> 'individual'::public.item_type_enum
      AND l.status IN ('delivered'::public.loan_status_enum, 'overdue'::public.loan_status_enum)
    GROUP BY ld.implement_id
)
UPDATE public.stock s
   SET total_stock = CASE
           WHEN i.item_type = 'individual'::public.item_type_enum
               THEN COALESCE(individual_state.total_stock, 0)
           ELSE COALESCE(s.total_stock, 0)
       END,
       available = CASE
           WHEN i.item_type = 'individual'::public.item_type_enum THEN GREATEST(
               COALESCE(individual_state.total_stock, 0)
               - COALESCE(individual_state.damaged_count, 0)
               - COALESCE(individual_state.loaned_count, 0)
               - COALESCE(individual_reserved.reserved_count, 0),
               0
           )
           ELSE GREATEST(
               COALESCE(s.total_stock, 0)
               - COALESCE(s.damaged, 0)
               - COALESCE(bulk_reserved.reserved_quantity, 0)
               - COALESCE(bulk_loaned.loaned_quantity, 0),
               0
           )
       END,
       reserved = CASE
           WHEN i.item_type = 'individual'::public.item_type_enum
               THEN COALESCE(individual_reserved.reserved_count, 0)
           ELSE COALESCE(bulk_reserved.reserved_quantity, 0)
       END,
       loaned = CASE
           WHEN i.item_type = 'individual'::public.item_type_enum
               THEN COALESCE(individual_state.loaned_count, 0)
           ELSE COALESCE(bulk_loaned.loaned_quantity, 0)
       END,
       damaged = CASE
           WHEN i.item_type = 'individual'::public.item_type_enum
               THEN COALESCE(individual_state.damaged_count, 0)
           ELSE COALESCE(s.damaged, 0)
       END,
       updated_at = now()
  FROM public.implement i
  LEFT JOIN individual_state
    ON individual_state.implement_id = i.id
  LEFT JOIN individual_reserved
    ON individual_reserved.implement_id = i.id
  LEFT JOIN bulk_reserved
    ON bulk_reserved.implement_id = i.id
  LEFT JOIN bulk_loaned
    ON bulk_loaned.implement_id = i.id
 WHERE s.implement_id = i.id;
