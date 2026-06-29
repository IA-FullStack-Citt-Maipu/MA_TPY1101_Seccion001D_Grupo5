CREATE OR REPLACE FUNCTION public.fn_get_implement_availability(
    p_implement_id BIGINT,
    p_start_at TIMESTAMP WITH TIME ZONE,
    p_end_at TIMESTAMP WITH TIME ZONE,
    p_include_pending BOOLEAN DEFAULT false,
    p_exclude_loan_id BIGINT DEFAULT NULL
)
RETURNS TABLE (
    implement_id BIGINT,
    total_stock INTEGER,
    damaged INTEGER,
    blocked_quantity INTEGER,
    available_quantity INTEGER
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
SET row_security = off
AS $$
DECLARE
    v_total_stock INTEGER := 0;
    v_damaged INTEGER := 0;
    v_blocked INTEGER := 0;
    v_item_type public.item_type_enum;
BEGIN
    IF p_start_at IS NULL OR p_end_at IS NULL OR p_end_at <= p_start_at THEN
        RAISE EXCEPTION 'Invalid range: start_at=%, end_at=%', p_start_at, p_end_at;
    END IF;

    SELECT COALESCE(s.total_stock, 0),
           COALESCE(s.damaged, 0),
           i.item_type
      INTO v_total_stock, v_damaged, v_item_type
      FROM public.implement i
      LEFT JOIN public.stock s
        ON s.implement_id = i.id
     WHERE i.id = p_implement_id;

    IF v_item_type = 'consumable'::public.item_type_enum THEN
        SELECT COALESCE(SUM(
            CASE l.status
                WHEN 'approved' THEN COALESCE(NULLIF(ld.reserved_quantity, 0), ld.requested_quantity)
                WHEN 'prepared' THEN ld.reserved_quantity
                WHEN 'pending' THEN CASE WHEN p_include_pending THEN ld.requested_quantity ELSE 0 END
                ELSE 0
            END
        ), 0)::INTEGER
          INTO v_blocked
          FROM public.loan_detail ld
          JOIN public.loan l
            ON l.id = ld.loan_id
         WHERE ld.implement_id = p_implement_id
           AND (p_exclude_loan_id IS NULL OR l.id <> p_exclude_loan_id)
           AND (
                l.status IN ('approved', 'prepared')
                OR (p_include_pending AND l.status = 'pending')
           );
    ELSE
        SELECT COALESCE(SUM(
            CASE l.status
                WHEN 'approved' THEN COALESCE(NULLIF(ld.reserved_quantity, 0), ld.requested_quantity)
                WHEN 'prepared' THEN ld.reserved_quantity
                WHEN 'delivered' THEN ld.delivered_quantity
                WHEN 'overdue' THEN ld.delivered_quantity
                WHEN 'pending' THEN CASE WHEN p_include_pending THEN ld.requested_quantity ELSE 0 END
                ELSE 0
            END
        ), 0)::INTEGER
          INTO v_blocked
          FROM public.loan_detail ld
          JOIN public.loan l
            ON l.id = ld.loan_id
         WHERE ld.implement_id = p_implement_id
           AND (p_exclude_loan_id IS NULL OR l.id <> p_exclude_loan_id)
           AND l.status IN ('approved', 'prepared', 'delivered', 'overdue', 'pending')
           AND l.scheduled_at < p_end_at
           AND l.expected_return_at > p_start_at
           AND (p_include_pending OR l.status <> 'pending');
    END IF;

    RETURN QUERY
    SELECT
        p_implement_id AS implement_id,
        v_total_stock AS total_stock,
        v_damaged AS damaged,
        v_blocked AS blocked_quantity,
        GREATEST(v_total_stock - v_damaged - v_blocked, 0) AS available_quantity;
END;
$$;
