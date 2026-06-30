-- Generated from snapshot/public-schema-full.sql on 2026-06-07.
-- Source: Supabase project panol-dev, schema public.

-- Name: fn_approve_loan(bigint, bigint, "text"); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_approve_loan"("p_loan_id" bigint, "p_actor_user_id" bigint, "p_notes" "text" DEFAULT NULL::"text") RETURNS TABLE("loan_id" bigint, "new_status" "public"."loan_status_enum", "approved_at" timestamp with time zone)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
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


--

-- Name: fn_auth_find_user_by_rut("text"); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_auth_find_user_by_rut"("p_rut" "text") RETURNS TABLE("user_uuid" "uuid", "rut" character varying, "user_name" character varying, "email" character varying, "password_hash" character varying, "role_name" character varying, "failed_login_attempts" integer, "blocked_until" timestamp with time zone)
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
    AS $$
    WITH normalized_input AS (
        SELECT replace(replace(replace(COALESCE(p_rut, ''), '.', ''), '-', ''), ' ', '') AS compact_rut
    )
    SELECT
        u.uuid,
        u.rut,
        u.name,
        u.email,
        u.password_hash,
        r.name,
        u.failed_login_attempts,
        u.blocked_until
    FROM public."user" u
    JOIN public.role r ON r.id = u.role_id
    CROSS JOIN normalized_input i
    WHERE (
        u.rut = i.compact_rut
        OR (
            length(i.compact_rut) > 1
            AND u.rut = substring(i.compact_rut, 1, length(i.compact_rut) - 1)
        )
    )
      AND u.active IS TRUE
    LIMIT 1
$$;


--

-- Name: fn_cancel_loan(bigint, bigint, "text"); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_cancel_loan"("p_loan_id" bigint, "p_actor_user_id" bigint, "p_notes" "text" DEFAULT NULL::"text") RETURNS TABLE("loan_id" bigint, "new_status" "public"."loan_status_enum", "cancelled_at" timestamp with time zone)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
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
        UPDATE public.loan_detail ld
           SET reserved_quantity = 0
         WHERE ld.loan_id = p_loan_id;

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


--

-- Name: fn_complete_loan(bigint, bigint, "text", "jsonb"); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_complete_loan"("p_loan_id" bigint, "p_actor_user_id" bigint, "p_notes" "text" DEFAULT NULL::"text", "p_return_payload" "jsonb" DEFAULT '[]'::"jsonb") RETURNS TABLE("loan_id" bigint, "new_status" "public"."loan_status_enum", "completed_at" timestamp with time zone)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
    AS $$
DECLARE
    v_status public.loan_status_enum;
    v_now TIMESTAMP WITH TIME ZONE := now();
    v_completed_at TIMESTAMP WITH TIME ZONE;
    v_detail RECORD;
    v_payload_item RECORD;
    v_breakdown_total INTEGER;
    v_individual_count INTEGER;
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

    IF v_status NOT IN ('delivered', 'overdue') THEN
        RAISE EXCEPTION 'Loan % can only be completed from delivered/overdue (current status=%)', p_loan_id, v_status;
    END IF;

    FOR v_detail IN
        SELECT ld.implement_id, ld.delivered_quantity, i.item_type
          FROM public.loan_detail ld
          JOIN public.implement i
            ON i.id = ld.implement_id
         WHERE ld.loan_id = p_loan_id
         FOR UPDATE OF ld
    LOOP
        SELECT
            COALESCE(x.returned_quantity, 0) AS returned_quantity,
            COALESCE(x.damaged_quantity, 0) AS damaged_quantity,
            COALESCE(x.lost_quantity, 0) AS lost_quantity,
            COALESCE(x.consumed_quantity, 0) AS consumed_quantity,
            COALESCE(x.discarded_quantity, 0) AS discarded_quantity,
            NULLIF(btrim(x.return_notes), '') AS return_notes
          INTO v_payload_item
          FROM jsonb_to_recordset(COALESCE(p_return_payload, '[]'::JSONB)) AS x(
               implement_id BIGINT,
               returned_quantity INTEGER,
               damaged_quantity INTEGER,
               lost_quantity INTEGER,
               consumed_quantity INTEGER,
               discarded_quantity INTEGER,
               return_notes TEXT
          )
         WHERE x.implement_id = v_detail.implement_id
         LIMIT 1;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Missing return payload for implement_id=%', v_detail.implement_id;
        END IF;

        IF v_payload_item.returned_quantity < 0
           OR v_payload_item.damaged_quantity < 0
           OR v_payload_item.lost_quantity < 0
           OR v_payload_item.consumed_quantity < 0
           OR v_payload_item.discarded_quantity < 0 THEN
            RAISE EXCEPTION 'Negative quantities are not allowed in return payload for implement_id=%', v_detail.implement_id;
        END IF;

        v_breakdown_total :=
            v_payload_item.returned_quantity
          + v_payload_item.damaged_quantity
          + v_payload_item.lost_quantity
          + v_payload_item.consumed_quantity
          + v_payload_item.discarded_quantity;

        IF v_breakdown_total <> v_detail.delivered_quantity THEN
            RAISE EXCEPTION
                'Return breakdown mismatch for implement_id=%. delivered=% breakdown_total=%',
                v_detail.implement_id,
                v_detail.delivered_quantity,
                v_breakdown_total;
        END IF;

        IF v_detail.item_type = 'individual'::public.item_type_enum
           AND v_payload_item.consumed_quantity > 0 THEN
            RAISE EXCEPTION
                'Individual implement_id=% cannot be completed with consumed_quantity > 0',
                v_detail.implement_id;
        END IF;

        UPDATE public.loan_detail ld
           SET returned_quantity = v_payload_item.returned_quantity,
               damaged_quantity = v_payload_item.damaged_quantity,
               lost_quantity = v_payload_item.lost_quantity,
               consumed_quantity = v_payload_item.consumed_quantity,
               discarded_quantity = v_payload_item.discarded_quantity,
               return_notes = v_payload_item.return_notes
         WHERE ld.loan_id = p_loan_id
           AND ld.implement_id = v_detail.implement_id;

        UPDATE public.stock s
           SET loaned = s.loaned - v_detail.delivered_quantity,
               available = s.available + v_payload_item.returned_quantity,
               damaged = s.damaged + v_payload_item.damaged_quantity,
               total_stock = s.total_stock - (
                   v_payload_item.lost_quantity
                 + v_payload_item.consumed_quantity
                 + v_payload_item.discarded_quantity
               ),
               updated_at = now()
         WHERE s.implement_id = v_detail.implement_id
           AND s.loaned >= v_detail.delivered_quantity
           AND s.total_stock >= (
               v_payload_item.lost_quantity
             + v_payload_item.consumed_quantity
             + v_payload_item.discarded_quantity
           );

        IF NOT FOUND THEN
            RAISE EXCEPTION
                'Stock update failed for implement_id=%. Check loaned/total_stock integrity.',
                v_detail.implement_id;
        END IF;

        IF v_payload_item.returned_quantity > 0 THEN
            INSERT INTO public.inventory_movement (
                implement_id,
                actor_user_id,
                movement_type,
                quantity,
                delta_changes,
                systemic_metadata,
                created_at
            )
            VALUES (
                v_detail.implement_id,
                p_actor_user_id,
                'loan_return'::public.inventory_movement_type_enum,
                v_payload_item.returned_quantity,
                jsonb_build_object(
                    'loan_id', p_loan_id,
                    'implement_id', v_detail.implement_id,
                    'returned_quantity', v_payload_item.returned_quantity
                ),
                jsonb_build_object('source', 'fn_complete_loan'),
                v_now
            );
        END IF;

        IF v_payload_item.damaged_quantity > 0 THEN
            INSERT INTO public.inventory_movement (
                implement_id,
                actor_user_id,
                movement_type,
                quantity,
                delta_changes,
                systemic_metadata,
                created_at
            )
            VALUES (
                v_detail.implement_id,
                p_actor_user_id,
                'damage_report'::public.inventory_movement_type_enum,
                v_payload_item.damaged_quantity,
                jsonb_build_object(
                    'loan_id', p_loan_id,
                    'implement_id', v_detail.implement_id,
                    'damaged_quantity', v_payload_item.damaged_quantity
                ),
                jsonb_build_object('source', 'fn_complete_loan'),
                v_now
            );
        END IF;

        IF v_payload_item.lost_quantity > 0 THEN
            INSERT INTO public.inventory_movement (
                implement_id,
                actor_user_id,
                movement_type,
                quantity,
                delta_changes,
                systemic_metadata,
                created_at
            )
            VALUES (
                v_detail.implement_id,
                p_actor_user_id,
                'loss'::public.inventory_movement_type_enum,
                -v_payload_item.lost_quantity,
                jsonb_build_object(
                    'loan_id', p_loan_id,
                    'implement_id', v_detail.implement_id,
                    'lost_quantity', v_payload_item.lost_quantity
                ),
                jsonb_build_object('source', 'fn_complete_loan'),
                v_now
            );
        END IF;

        IF v_payload_item.consumed_quantity > 0 THEN
            INSERT INTO public.inventory_movement (
                implement_id,
                actor_user_id,
                movement_type,
                quantity,
                delta_changes,
                systemic_metadata,
                created_at
            )
            VALUES (
                v_detail.implement_id,
                p_actor_user_id,
                'consumption'::public.inventory_movement_type_enum,
                -v_payload_item.consumed_quantity,
                jsonb_build_object(
                    'loan_id', p_loan_id,
                    'implement_id', v_detail.implement_id,
                    'consumed_quantity', v_payload_item.consumed_quantity
                ),
                jsonb_build_object('source', 'fn_complete_loan'),
                v_now
            );
        END IF;

        IF v_payload_item.discarded_quantity > 0 THEN
            INSERT INTO public.inventory_movement (
                implement_id,
                actor_user_id,
                movement_type,
                quantity,
                delta_changes,
                systemic_metadata,
                created_at
            )
            VALUES (
                v_detail.implement_id,
                p_actor_user_id,
                'discard'::public.inventory_movement_type_enum,
                -v_payload_item.discarded_quantity,
                jsonb_build_object(
                    'loan_id', p_loan_id,
                    'implement_id', v_detail.implement_id,
                    'discarded_quantity', v_payload_item.discarded_quantity
                ),
                jsonb_build_object('source', 'fn_complete_loan'),
                v_now
            );
        END IF;

        IF v_detail.item_type = 'individual'::public.item_type_enum THEN
            SELECT COUNT(*)
              INTO v_individual_count
              FROM public.loan_detail_individual ldi
             WHERE ldi.loan_id = p_loan_id
               AND ldi.implement_id = v_detail.implement_id
               AND ldi.allocation_status = 'delivered'::public.individual_allocation_status_enum;

            IF v_individual_count < v_detail.delivered_quantity THEN
                RAISE EXCEPTION
                    'Missing delivered individual assignments for implement_id=%. delivered_assignments=% delivered_quantity=%',
                    v_detail.implement_id,
                    v_individual_count,
                    v_detail.delivered_quantity;
            END IF;

            WITH ordered AS (
                SELECT
                    ldi.individual_id,
                    row_number() OVER (ORDER BY ldi.individual_id) AS rn
                FROM public.loan_detail_individual ldi
                WHERE ldi.loan_id = p_loan_id
                  AND ldi.implement_id = v_detail.implement_id
                  AND ldi.allocation_status = 'delivered'::public.individual_allocation_status_enum
                LIMIT v_detail.delivered_quantity
            ),
            classified AS (
                SELECT
                    o.individual_id,
                    CASE
                        WHEN o.rn <= v_payload_item.damaged_quantity THEN 'damaged'::public.return_condition_enum
                        WHEN o.rn <= v_payload_item.damaged_quantity + v_payload_item.lost_quantity
                            THEN 'lost'::public.return_condition_enum
                        WHEN o.rn <= v_payload_item.damaged_quantity + v_payload_item.lost_quantity + v_payload_item.discarded_quantity
                            THEN 'discarded'::public.return_condition_enum
                        ELSE 'good'::public.return_condition_enum
                    END AS return_condition
                FROM ordered o
            ),
            updated_alloc AS (
                UPDATE public.loan_detail_individual ldi
                   SET allocation_status = 'returned'::public.individual_allocation_status_enum,
                       return_condition = c.return_condition,
                       returned_at = v_now
                  FROM classified c
                 WHERE ldi.loan_id = p_loan_id
                   AND ldi.implement_id = v_detail.implement_id
                   AND ldi.individual_id = c.individual_id
                RETURNING ldi.individual_id, c.return_condition
            )
            UPDATE public.individual i
               SET status = CASE ua.return_condition
                               WHEN 'good'::public.return_condition_enum THEN 'available'::public.individual_status_enum
                               WHEN 'damaged'::public.return_condition_enum THEN 'damaged'::public.individual_status_enum
                               WHEN 'lost'::public.return_condition_enum THEN 'blocked'::public.individual_status_enum
                               WHEN 'discarded'::public.return_condition_enum THEN 'retired'::public.individual_status_enum
                               ELSE i.status
                            END,
                   updated_at = v_now
              FROM updated_alloc ua
             WHERE i.id = ua.individual_id;
        END IF;
    END LOOP;

    SELECT r.changed_at
      INTO v_completed_at
      FROM public.fn_loan_change_status(
            p_loan_id,
            'completed'::public.loan_status_enum,
            p_actor_user_id,
            COALESCE(NULLIF(btrim(p_notes), ''), 'Préstamo completado.')
      ) AS r;

    RETURN QUERY
    SELECT p_loan_id, 'completed'::public.loan_status_enum, v_completed_at;
END;
$$;


--

-- Name: fn_deliver_loan(bigint, bigint, "text"); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_deliver_loan"("p_loan_id" bigint, "p_actor_user_id" bigint, "p_notes" "text" DEFAULT NULL::"text") RETURNS TABLE("loan_id" bigint, "new_status" "public"."loan_status_enum", "delivered_at" timestamp with time zone)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
    AS $$
DECLARE
    v_status public.loan_status_enum;
    v_detail RECORD;
    v_stock_reserved INTEGER;
    v_delivered_at TIMESTAMP WITH TIME ZONE;
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

    IF v_status <> 'prepared' THEN
        RAISE EXCEPTION 'Loan % is not prepared (current status=%)', p_loan_id, v_status;
    END IF;

    FOR v_detail IN
        SELECT ld.implement_id, ld.reserved_quantity, i.item_type
          FROM public.loan_detail ld
          JOIN public.implement i
            ON i.id = ld.implement_id
         WHERE ld.loan_id = p_loan_id
         FOR UPDATE OF ld
    LOOP
        IF v_detail.reserved_quantity <= 0 THEN
            RAISE EXCEPTION
                'Loan % has implement_id=% with reserved_quantity <= 0',
                p_loan_id, v_detail.implement_id;
        END IF;

        SELECT s.reserved
          INTO v_stock_reserved
          FROM public.stock s
         WHERE s.implement_id = v_detail.implement_id
         FOR UPDATE;

        IF v_stock_reserved IS NULL THEN
            RAISE EXCEPTION 'Stock row not found for implement_id=%', v_detail.implement_id;
        END IF;

        IF v_stock_reserved < v_detail.reserved_quantity THEN
            RAISE EXCEPTION
                'Reserved stock is insufficient for delivery. implement_id=% reserved=% required=%',
                v_detail.implement_id,
                v_stock_reserved,
                v_detail.reserved_quantity;
        END IF;

        UPDATE public.stock s
           SET reserved = s.reserved - v_detail.reserved_quantity,
               loaned = s.loaned + v_detail.reserved_quantity,
               updated_at = now()
         WHERE s.implement_id = v_detail.implement_id;

        UPDATE public.loan_detail ld
           SET delivered_quantity = ld.reserved_quantity
         WHERE ld.loan_id = p_loan_id
           AND ld.implement_id = v_detail.implement_id;

        INSERT INTO public.inventory_movement (
            implement_id,
            actor_user_id,
            movement_type,
            quantity,
            delta_changes,
            systemic_metadata,
            created_at
        )
        VALUES (
            v_detail.implement_id,
            p_actor_user_id,
            'loan_delivery'::public.inventory_movement_type_enum,
            -v_detail.reserved_quantity,
            jsonb_build_object(
                'loan_id', p_loan_id,
                'implement_id', v_detail.implement_id,
                'delivered_quantity', v_detail.reserved_quantity
            ),
            jsonb_build_object('source', 'fn_deliver_loan'),
            now()
        );

        IF v_detail.item_type = 'individual'::public.item_type_enum THEN
            UPDATE public.loan_detail_individual ldi
               SET allocation_status = 'delivered'::public.individual_allocation_status_enum
             WHERE ldi.loan_id = p_loan_id
               AND ldi.implement_id = v_detail.implement_id
               AND ldi.allocation_status IN (
                   'reserved'::public.individual_allocation_status_enum,
                   'prepared'::public.individual_allocation_status_enum
               );

            UPDATE public.individual i
               SET status = 'loaned'::public.individual_status_enum,
                   updated_at = now()
              FROM public.loan_detail_individual ldi
             WHERE ldi.loan_id = p_loan_id
               AND ldi.implement_id = v_detail.implement_id
               AND ldi.individual_id = i.id
               AND ldi.allocation_status = 'delivered'::public.individual_allocation_status_enum;
        END IF;
    END LOOP;

    SELECT r.changed_at
      INTO v_delivered_at
      FROM public.fn_loan_change_status(
            p_loan_id,
            'delivered'::public.loan_status_enum,
            p_actor_user_id,
            COALESCE(NULLIF(btrim(p_notes), ''), 'Préstamo entregado.')
      ) AS r;

    RETURN QUERY
    SELECT p_loan_id, 'delivered'::public.loan_status_enum, v_delivered_at;
END;
$$;


--

-- Name: fn_expire_pending_loans(bigint, timestamp with time zone, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_expire_pending_loans"("p_actor_user_id" bigint, "p_now" timestamp with time zone DEFAULT "now"(), "p_grace_minutes" integer DEFAULT 120) RETURNS TABLE("expired_count" integer)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
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


--

-- Name: fn_get_bulk_availability(bigint[], timestamp with time zone, timestamp with time zone, boolean, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_get_bulk_availability"("p_implement_ids" bigint[], "p_start_at" timestamp with time zone, "p_end_at" timestamp with time zone, "p_include_pending" boolean DEFAULT false, "p_exclude_loan_id" bigint DEFAULT NULL::bigint) RETURNS TABLE("implement_id" bigint, "total_stock" integer, "damaged" integer, "blocked_quantity" integer, "available_quantity" integer)
    LANGUAGE "sql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
    AS $$
SELECT a.*
FROM unnest(COALESCE(p_implement_ids, ARRAY[]::BIGINT[])) AS x(implement_id)
CROSS JOIN LATERAL public.fn_get_implement_availability(
    x.implement_id,
    p_start_at,
    p_end_at,
    p_include_pending,
    p_exclude_loan_id
) AS a;
$$;


--

-- Name: fn_get_implement_availability(bigint, timestamp with time zone, timestamp with time zone, boolean, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_get_implement_availability"("p_implement_id" bigint, "p_start_at" timestamp with time zone, "p_end_at" timestamp with time zone, "p_include_pending" boolean DEFAULT false, "p_exclude_loan_id" bigint DEFAULT NULL::bigint) RETURNS TABLE("implement_id" bigint, "total_stock" integer, "damaged" integer, "blocked_quantity" integer, "available_quantity" integer)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
    AS $$
DECLARE
    v_total_stock INTEGER := 0;
    v_damaged INTEGER := 0;
    v_blocked INTEGER := 0;
BEGIN
    IF p_start_at IS NULL OR p_end_at IS NULL OR p_end_at <= p_start_at THEN
        RAISE EXCEPTION 'Invalid range: start_at=%, end_at=%', p_start_at, p_end_at;
    END IF;

    SELECT s.total_stock, s.damaged
      INTO v_total_stock, v_damaged
      FROM public.stock s
     WHERE s.implement_id = p_implement_id;

    IF v_total_stock IS NULL THEN
        v_total_stock := 0;
    END IF;
    IF v_damaged IS NULL THEN
        v_damaged := 0;
    END IF;

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

    RETURN QUERY
    SELECT
        p_implement_id AS implement_id,
        v_total_stock AS total_stock,
        v_damaged AS damaged,
        v_blocked AS blocked_quantity,
        GREATEST(v_total_stock - v_damaged - v_blocked, 0) AS available_quantity;
END;
$$;


--

-- Name: fn_guard_individual_item_type(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_guard_individual_item_type"() RETURNS "trigger"
    LANGUAGE "plpgsql"
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


--

-- Name: fn_is_valid_loan_status_transition("public"."loan_status_enum", "public"."loan_status_enum"); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_is_valid_loan_status_transition"("p_from" "public"."loan_status_enum", "p_to" "public"."loan_status_enum") RETURNS boolean
    LANGUAGE "sql" IMMUTABLE
    AS $$
SELECT CASE
    WHEN p_from = 'pending'   AND p_to IN ('approved', 'rejected', 'cancelled', 'expired') THEN TRUE
    WHEN p_from = 'approved'  AND p_to IN ('prepared', 'cancelled', 'expired') THEN TRUE
    WHEN p_from = 'prepared'  AND p_to IN ('delivered', 'cancelled', 'expired') THEN TRUE
    WHEN p_from = 'delivered' AND p_to IN ('overdue', 'completed') THEN TRUE
    WHEN p_from = 'overdue'   AND p_to = 'completed' THEN TRUE
    ELSE FALSE
END;
$$;


--

-- Name: fn_loan_change_status(bigint, "public"."loan_status_enum", bigint, "text"); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_loan_change_status"("p_loan_id" bigint, "p_new_status" "public"."loan_status_enum", "p_actor_user_id" bigint, "p_notes" "text" DEFAULT NULL::"text") RETURNS TABLE("loan_id" bigint, "old_status" "public"."loan_status_enum", "new_status" "public"."loan_status_enum", "changed_at" timestamp with time zone)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
    AS $$
DECLARE
    v_old_status public.loan_status_enum;
    v_changed_at TIMESTAMP WITH TIME ZONE := now();
BEGIN
    SELECT l.status
      INTO v_old_status
      FROM public.loan l
     WHERE l.id = p_loan_id
     FOR UPDATE;

    IF v_old_status IS NULL THEN
        RAISE EXCEPTION 'Loan not found for id=%', p_loan_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM public."user" u
        WHERE u.id = p_actor_user_id
    ) THEN
        RAISE EXCEPTION 'Actor user not found for id=%', p_actor_user_id;
    END IF;

    IF v_old_status IN ('completed', 'rejected', 'cancelled', 'expired') THEN
        RAISE EXCEPTION 'Transition not allowed from terminal status=%', v_old_status;
    END IF;

    IF NOT public.fn_is_valid_loan_status_transition(v_old_status, p_new_status) THEN
        RAISE EXCEPTION 'Invalid loan status transition: % -> %', v_old_status, p_new_status;
    END IF;

    UPDATE public.loan
       SET status = p_new_status,
           updated_at = v_changed_at
     WHERE id = p_loan_id;

    INSERT INTO public.loan_status_history (
        loan_id,
        actor_user_id,
        from_status,
        to_status,
        notes,
        changed_at
    )
    VALUES (
        p_loan_id,
        p_actor_user_id,
        v_old_status,
        p_new_status,
        NULLIF(btrim(p_notes), ''),
        v_changed_at
    );

    RETURN QUERY
    SELECT p_loan_id, v_old_status, p_new_status, v_changed_at;
END;
$$;


--

-- Name: fn_loan_change_status("uuid", "uuid", "public"."loan_status_enum", "text"); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_loan_change_status"("p_loan_uuid" "uuid", "p_actor_user_uuid" "uuid", "p_to_status" "public"."loan_status_enum", "p_notes" "text" DEFAULT NULL::"text") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
    AS $$
DECLARE
    v_loan_id BIGINT;
    v_actor_id BIGINT;
BEGIN
    SELECT l.id INTO v_loan_id
      FROM public.loan l
     WHERE l.uuid = p_loan_uuid;

    IF v_loan_id IS NULL THEN
        RAISE EXCEPTION 'Loan not found for uuid=%', p_loan_uuid;
    END IF;

    SELECT u.id INTO v_actor_id
      FROM public."user" u
     WHERE u.uuid = p_actor_user_uuid;

    IF v_actor_id IS NULL THEN
        RAISE EXCEPTION 'Actor user not found for uuid=%', p_actor_user_uuid;
    END IF;

    PERFORM *
      FROM public.fn_loan_change_status(
            v_loan_id,
            p_to_status,
            v_actor_id,
            p_notes
      );
END;
$$;


--

-- Name: fn_mark_overdue_loans(bigint, timestamp with time zone); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_mark_overdue_loans"("p_actor_user_id" bigint, "p_now" timestamp with time zone DEFAULT "now"()) RETURNS TABLE("overdue_count" integer)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
    AS $$
DECLARE
    v_loan RECORD;
    v_overdue_count INTEGER := 0;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public."user" u WHERE u.id = p_actor_user_id) THEN
        RAISE EXCEPTION 'Actor user not found for id=%', p_actor_user_id;
    END IF;

    FOR v_loan IN
        SELECT l.id
          FROM public.loan l
         WHERE l.status = 'delivered'
           AND p_now > l.expected_return_at
         FOR UPDATE SKIP LOCKED
    LOOP
        PERFORM *
          FROM public.fn_loan_change_status(
                v_loan.id,
                'overdue'::public.loan_status_enum,
                p_actor_user_id,
                'Marcado automáticamente como overdue por devolución fuera de plazo.'
          );

        v_overdue_count := v_overdue_count + 1;
    END LOOP;

    RETURN QUERY
    SELECT v_overdue_count;
END;
$$;


--

-- Name: fn_notify_new_loan_request("uuid"); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_notify_new_loan_request"("p_requester_user_uuid" "uuid") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
    AS $$
DECLARE
    v_requester_name CHARACTER VARYING;
BEGIN
    SELECT u.name
      INTO v_requester_name
      FROM public."user" u
     WHERE u.uuid = p_requester_user_uuid
       AND u.active IS TRUE
     LIMIT 1;

    IF v_requester_name IS NULL THEN
        RAISE EXCEPTION 'No se pudo resolver el docente solicitante para la notificacion de prestamo';
    END IF;

    INSERT INTO public.notification (user_id, title, message)
    SELECT u.id,
           U&'Nueva solicitud de pr\00E9stamo',
           'El docente ' || v_requester_name || ' ha enviado una nueva solicitud'
      FROM public."user" u
      JOIN public.role r ON r.id = u.role_id
     WHERE r.name = 'coordinador'
       AND u.active IS TRUE
       AND u.uuid <> '99999999-9999-9999-9999-999999999999'::UUID;
END;
$$;


--

-- Name: fn_prepare_loan(bigint, bigint, "text"); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_prepare_loan"("p_loan_id" bigint, "p_actor_user_id" bigint, "p_notes" "text" DEFAULT NULL::"text") RETURNS TABLE("loan_id" bigint, "new_status" "public"."loan_status_enum", "prepared_at" timestamp with time zone)
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
    AS $$
DECLARE
    v_status public.loan_status_enum;
    v_detail RECORD;
    v_stock_available INTEGER;
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

    FOR v_detail IN
        SELECT ld.implement_id, ld.reserved_quantity, i.item_type
          FROM public.loan_detail ld
          JOIN public.implement i
            ON i.id = ld.implement_id
         WHERE ld.loan_id = p_loan_id
         FOR UPDATE OF ld
    LOOP
        IF v_detail.reserved_quantity <= 0 THEN
            RAISE EXCEPTION
                'Loan % has implement_id=% with reserved_quantity <= 0',
                p_loan_id, v_detail.implement_id;
        END IF;

        SELECT s.available
          INTO v_stock_available
          FROM public.stock s
         WHERE s.implement_id = v_detail.implement_id
         FOR UPDATE;

        IF v_stock_available IS NULL THEN
            RAISE EXCEPTION 'Stock row not found for implement_id=%', v_detail.implement_id;
        END IF;

        IF v_stock_available < v_detail.reserved_quantity THEN
            RAISE EXCEPTION
                'Insufficient physical stock for prepare. implement_id=% available=% reserved_required=%',
                v_detail.implement_id,
                v_stock_available,
                v_detail.reserved_quantity;
        END IF;

        UPDATE public.stock s
           SET available = s.available - v_detail.reserved_quantity,
               reserved = s.reserved + v_detail.reserved_quantity,
               updated_at = now()
         WHERE s.implement_id = v_detail.implement_id;

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
            COALESCE(NULLIF(btrim(p_notes), ''), 'Implementos separados físicamente.')
      ) AS r;

    RETURN QUERY
    SELECT p_loan_id, 'prepared'::public.loan_status_enum, v_prepared_at;
END;
$$;


--

-- Name: fn_set_loan_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_set_loan_updated_at"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;


--

-- Name: fn_trg_create_notification_on_loan_status(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_trg_create_notification_on_loan_status"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
DECLARE
    v_requester_id BIGINT;
    v_loan_uuid UUID;
    v_title TEXT;
    v_message TEXT;
BEGIN
    SELECT l.requester_id, l.uuid
      INTO v_requester_id, v_loan_uuid
      FROM public.loan l
     WHERE l.id = NEW.loan_id;

    IF v_requester_id IS NULL THEN
        RETURN NEW;
    END IF;

    v_title := CASE NEW.to_status
        WHEN 'approved'  THEN 'Solicitud aprobada'
        WHEN 'prepared'  THEN 'Implementos preparados'
        WHEN 'delivered' THEN 'Préstamo entregado'
        WHEN 'completed' THEN 'Préstamo completado'
        WHEN 'rejected'  THEN 'Solicitud rechazada'
        WHEN 'cancelled' THEN 'Solicitud cancelada'
        WHEN 'expired'   THEN 'Solicitud vencida'
        WHEN 'overdue'   THEN 'Préstamo atrasado'
        ELSE 'Actualización de préstamo'
    END;

    v_message := CASE NEW.to_status
        WHEN 'approved'  THEN format('Tu solicitud %s fue aprobada.', v_loan_uuid)
        WHEN 'prepared'  THEN format('Tu solicitud %s está preparada para entrega.', v_loan_uuid)
        WHEN 'delivered' THEN format('Tu préstamo %s fue entregado.', v_loan_uuid)
        WHEN 'completed' THEN format('Tu préstamo %s fue cerrado.', v_loan_uuid)
        WHEN 'rejected'  THEN format('Tu solicitud %s fue rechazada.', v_loan_uuid)
        WHEN 'cancelled' THEN format('Tu solicitud %s fue cancelada.', v_loan_uuid)
        WHEN 'expired'   THEN format('Tu solicitud %s expiró por tiempo.', v_loan_uuid)
        WHEN 'overdue'   THEN format('Tu préstamo %s está overdue.', v_loan_uuid)
        ELSE format('Tu préstamo %s cambió de estado a %s.', v_loan_uuid, NEW.to_status::TEXT)
    END;

    INSERT INTO public.notification (
        user_id,
        title,
        message,
        read_status,
        created_at
    )
    VALUES (
        v_requester_id,
        v_title,
        v_message,
        false,
        NEW.changed_at
    );

    RETURN NEW;
END;
$$;


--

-- Name: fn_trg_create_outbox_event_on_loan_status(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_trg_create_outbox_event_on_loan_status"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
DECLARE
    v_loan_uuid UUID;
BEGIN
    SELECT l.uuid
      INTO v_loan_uuid
      FROM public.loan l
     WHERE l.id = NEW.loan_id;

    IF v_loan_uuid IS NULL THEN
        RETURN NEW;
    END IF;

    INSERT INTO public.outbox_event (
        aggregate_type,
        aggregate_id,
        event_type,
        payload,
        occurred_at
    )
    VALUES (
        'loan',
        v_loan_uuid,
        'loan.status_changed',
        jsonb_build_object(
            'loan_id', NEW.loan_id,
            'loan_uuid', v_loan_uuid,
            'from_status', NEW.from_status,
            'to_status', NEW.to_status,
            'actor_user_id', NEW.actor_user_id,
            'changed_at', NEW.changed_at,
            'notes', NEW.notes
        )::TEXT,
        NEW.changed_at
    );

    RETURN NEW;
END;
$$;


--

-- Name: fn_trg_implement_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_trg_implement_updated_at"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;


--

-- Name: fn_trg_individual_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_trg_individual_updated_at"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;


--

-- Name: fn_trg_inventory_movement_audit(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_trg_inventory_movement_audit"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    INSERT INTO public.audit_log (
        event,
        payload,
        actor_user_id,
        target_user_id,
        created_at
    )
    VALUES (
        'inventory_movement.created',
        jsonb_build_object(
            'movement_id', NEW.id,
            'implement_id', NEW.implement_id,
            'movement_type', NEW.movement_type,
            'quantity', NEW.quantity,
            'actor_user_id', NEW.actor_user_id
        ),
        NEW.actor_user_id,
        NULL,
        now()
    );

    RETURN NEW;
END;
$$;


--

-- Name: fn_trg_loan_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_trg_loan_updated_at"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;


--

-- Name: fn_trg_stock_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_trg_stock_updated_at"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;


--

-- Name: fn_trg_validate_loan_dates(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_trg_validate_loan_dates"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
BEGIN
    IF NEW.expected_return_at IS NULL OR NEW.scheduled_at IS NULL THEN
        RAISE EXCEPTION 'loan.scheduled_at and loan.expected_return_at cannot be NULL';
    END IF;

    IF NEW.expected_return_at <= NEW.scheduled_at THEN
        RAISE EXCEPTION 'loan.expected_return_at must be greater than loan.scheduled_at';
    END IF;

    RETURN NEW;
END;
$$;


--

-- Name: fn_validate_individual_item_type(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_validate_individual_item_type"() RETURNS "trigger"
    LANGUAGE "plpgsql"
    AS $$
DECLARE
    v_item_type public.item_type_enum;
BEGIN
    SELECT i.item_type
      INTO v_item_type
      FROM public.implement i
     WHERE i.id = NEW.implement_id;

    IF v_item_type IS NULL THEN
        RAISE EXCEPTION 'Implement not found for implement_id=%', NEW.implement_id;
    END IF;

    IF v_item_type <> 'individual'::public.item_type_enum THEN
        RAISE EXCEPTION
            'Only item_type=individual can create physical units. implement_id=% item_type=%',
            NEW.implement_id,
            v_item_type;
    END IF;

    RETURN NEW;
END;
$$;


--

-- Name: fn_validate_loan_status_transition(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_validate_loan_status_transition"() RETURNS "trigger"
    LANGUAGE "plpgsql"
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


--

-- Name: fn_write_audit_log(character varying, "uuid", "uuid", "jsonb"); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."fn_write_audit_log"("p_event" character varying, "p_actor_user_uuid" "uuid", "p_target_user_uuid" "uuid", "p_payload" "jsonb") RETURNS "void"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    SET "row_security" TO 'off'
    AS $$ DECLARE v_actor_user_id BIGINT; v_target_user_id BIGINT; BEGIN IF p_actor_user_uuid IS NOT NULL THEN SELECT id INTO v_actor_user_id FROM public."user" WHERE uuid = p_actor_user_uuid; END IF; IF p_target_user_uuid IS NOT NULL THEN SELECT id INTO v_target_user_id FROM public."user" WHERE uuid = p_target_user_uuid; END IF; INSERT INTO public.audit_log(event, payload, actor_user_id, target_user_id) VALUES (p_event, COALESCE(p_payload, '{}'::jsonb), v_actor_user_id, v_target_user_id); END; $$;


--

-- Name: get_current_user_id(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."get_current_user_id"() RETURNS bigint
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
    SELECT id
      FROM public."user"
     WHERE uuid = NULLIF(current_setting('app.current_user_uuid', true), '')::UUID
$$;


--

-- Name: get_current_user_role(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."get_current_user_role"() RETURNS character varying
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
    SELECT r.name
      FROM public."user" u
      JOIN public.role r ON u.role_id = r.id
     WHERE u.uuid = NULLIF(current_setting('app.current_user_uuid', true), '')::UUID
$$;


--

-- Name: is_authenticated(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION "public"."is_authenticated"() RETURNS boolean
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO 'public', 'pg_temp'
    AS $$
    SELECT NULLIF(current_setting('app.current_user_uuid', true), '') IS NOT NULL
$$;


SET default_tablespace = '';

SET default_table_access_method = "heap";

--

