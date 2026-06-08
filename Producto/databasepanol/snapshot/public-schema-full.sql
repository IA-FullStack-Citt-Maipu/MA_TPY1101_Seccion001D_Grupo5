--
-- PostgreSQL database dump
--

\restrict MbWnuFeujAy3SGRnmK80TpSOfxhaHv6N93sNEz1f62oWNYkzGfsHRcwOBDVq63y

-- Dumped from database version 17.6
-- Dumped by pg_dump version 17.10 (Debian 17.10-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA "public";


--
-- Name: individual_allocation_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."individual_allocation_status_enum" AS ENUM (
    'reserved',
    'prepared',
    'delivered',
    'returned',
    'cancelled'
);


--
-- Name: individual_condition_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."individual_condition_enum" AS ENUM (
    'good',
    'damaged_repairable',
    'damaged_no_diagnosis',
    'irreparable'
);


--
-- Name: individual_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."individual_status_enum" AS ENUM (
    'available',
    'loaned',
    'maintenance',
    'damaged',
    'blocked',
    'retired'
);


--
-- Name: inventory_movement_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."inventory_movement_type_enum" AS ENUM (
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


--
-- Name: item_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."item_type_enum" AS ENUM (
    'consumable',
    'reusable',
    'individual'
);


--
-- Name: loan_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."loan_status_enum" AS ENUM (
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


--
-- Name: outbox_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."outbox_status_enum" AS ENUM (
    'PENDING',
    'PROCESSING',
    'SENT',
    'FAILED'
);


--
-- Name: return_condition_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."return_condition_enum" AS ENUM (
    'good',
    'damaged',
    'lost',
    'discarded'
);


--
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
-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."audit_log" (
    "id" bigint NOT NULL,
    "event" character varying NOT NULL,
    "payload" "jsonb" NOT NULL,
    "actor_user_id" bigint,
    "target_user_id" bigint,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--
-- Name: audit_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."audit_log_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."audit_log_id_seq" OWNED BY "public"."audit_log"."id";


--
-- Name: career; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."career" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "code" character varying NOT NULL,
    "name" character varying NOT NULL,
    "active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--
-- Name: career_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."career_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: career_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."career_id_seq" OWNED BY "public"."career"."id";


--
-- Name: category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."category" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" character varying NOT NULL,
    "description" "text",
    "active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--
-- Name: category_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."category_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: category_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."category_id_seq" OWNED BY "public"."category"."id";


--
-- Name: email_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."email_outbox" (
    "id" bigint NOT NULL,
    "event_id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "recipient_email" character varying NOT NULL,
    "email_type" character varying NOT NULL,
    "template_data" "jsonb" NOT NULL,
    "status" "public"."outbox_status_enum" DEFAULT 'PENDING'::"public"."outbox_status_enum" NOT NULL,
    "retry_count" integer DEFAULT 0 NOT NULL,
    "error_log" "text",
    "occurred_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "processed_at" timestamp with time zone
);


--
-- Name: email_outbox_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."email_outbox_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_outbox_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."email_outbox_id_seq" OWNED BY "public"."email_outbox"."id";


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."flyway_schema_history" (
    "installed_rank" integer NOT NULL,
    "version" character varying(50),
    "description" character varying(200) NOT NULL,
    "type" character varying(20) NOT NULL,
    "script" character varying(1000) NOT NULL,
    "checksum" integer,
    "installed_by" character varying(100) NOT NULL,
    "installed_on" timestamp without time zone DEFAULT "now"() NOT NULL,
    "execution_time" integer NOT NULL,
    "success" boolean NOT NULL
);


--
-- Name: implement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."implement" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "category_id" bigint,
    "location_id" bigint,
    "name" character varying NOT NULL,
    "description" "text",
    "item_type" "public"."item_type_enum" DEFAULT 'reusable'::"public"."item_type_enum" NOT NULL,
    "barcode" character varying,
    "img_url" "text",
    "active" boolean DEFAULT true NOT NULL,
    "observations" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--
-- Name: implement_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."implement_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: implement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."implement_id_seq" OWNED BY "public"."implement"."id";


--
-- Name: individual; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."individual" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "implement_id" bigint NOT NULL,
    "current_location_id" bigint,
    "asset_code" character varying NOT NULL,
    "status" "public"."individual_status_enum" DEFAULT 'available'::"public"."individual_status_enum" NOT NULL,
    "condition" "public"."individual_condition_enum" DEFAULT 'good'::"public"."individual_condition_enum" NOT NULL,
    "notes" "text",
    "active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--
-- Name: individual_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."individual_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: individual_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."individual_id_seq" OWNED BY "public"."individual"."id";


--
-- Name: inventory_movement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."inventory_movement" (
    "id" bigint NOT NULL,
    "implement_id" bigint NOT NULL,
    "actor_user_id" bigint NOT NULL,
    "movement_type" "public"."inventory_movement_type_enum" NOT NULL,
    "quantity" integer NOT NULL,
    "delta_changes" "jsonb" NOT NULL,
    "systemic_metadata" "jsonb",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "inventory_movement_qty_nonzero" CHECK (("quantity" <> 0))
);


--
-- Name: inventory_movement_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."inventory_movement_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventory_movement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."inventory_movement_id_seq" OWNED BY "public"."inventory_movement"."id";


--
-- Name: loan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."loan" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "requester_id" bigint NOT NULL,
    "room_id" bigint,
    "subject_id" bigint,
    "status" "public"."loan_status_enum" DEFAULT 'pending'::"public"."loan_status_enum" NOT NULL,
    "scheduled_at" timestamp with time zone NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "expected_return_at" timestamp with time zone NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "chk_loan_expected_return_after_scheduled" CHECK (("expected_return_at" > "scheduled_at"))
);


--
-- Name: loan_detail; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."loan_detail" (
    "loan_id" bigint NOT NULL,
    "implement_id" bigint NOT NULL,
    "requested_quantity" integer NOT NULL,
    "reserved_quantity" integer DEFAULT 0 NOT NULL,
    "delivered_quantity" integer DEFAULT 0 NOT NULL,
    "returned_quantity" integer DEFAULT 0 NOT NULL,
    "damaged_quantity" integer DEFAULT 0 NOT NULL,
    "lost_quantity" integer DEFAULT 0 NOT NULL,
    "consumed_quantity" integer DEFAULT 0 NOT NULL,
    "discarded_quantity" integer DEFAULT 0 NOT NULL,
    "return_notes" "text",
    CONSTRAINT "chk_ld_consumed_qty_nonnegative" CHECK (("consumed_quantity" >= 0)),
    CONSTRAINT "chk_ld_damaged_qty_nonnegative" CHECK (("damaged_quantity" >= 0)),
    CONSTRAINT "chk_ld_delivered_lte_reserved" CHECK (("delivered_quantity" <= "reserved_quantity")),
    CONSTRAINT "chk_ld_discarded_qty_nonnegative" CHECK (("discarded_quantity" >= 0)),
    CONSTRAINT "chk_ld_lost_qty_nonnegative" CHECK (("lost_quantity" >= 0)),
    CONSTRAINT "chk_ld_reserved_lte_requested" CHECK (("reserved_quantity" <= "requested_quantity")),
    CONSTRAINT "chk_ld_return_breakdown_lte_delivered" CHECK (((((("returned_quantity" + "damaged_quantity") + "lost_quantity") + "consumed_quantity") + "discarded_quantity") <= "delivered_quantity")),
    CONSTRAINT "chk_ld_returned_qty_nonnegative" CHECK (("returned_quantity" >= 0)),
    CONSTRAINT "loan_detail_delivered_qty_check" CHECK (("delivered_quantity" >= 0)),
    CONSTRAINT "loan_detail_requested_qty_check" CHECK (("requested_quantity" > 0)),
    CONSTRAINT "loan_detail_reserved_qty_check" CHECK (("reserved_quantity" >= 0))
);


--
-- Name: loan_detail_individual; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."loan_detail_individual" (
    "loan_id" bigint NOT NULL,
    "implement_id" bigint NOT NULL,
    "individual_id" bigint NOT NULL,
    "return_condition" "public"."return_condition_enum",
    "returned_at" timestamp with time zone,
    "allocation_status" "public"."individual_allocation_status_enum" DEFAULT 'delivered'::"public"."individual_allocation_status_enum" NOT NULL
);


--
-- Name: loan_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."loan_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: loan_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."loan_id_seq" OWNED BY "public"."loan"."id";


--
-- Name: loan_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."loan_status_history" (
    "id" bigint NOT NULL,
    "loan_id" bigint NOT NULL,
    "actor_user_id" bigint NOT NULL,
    "from_status" "public"."loan_status_enum",
    "to_status" "public"."loan_status_enum" NOT NULL,
    "notes" "text",
    "changed_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--
-- Name: loan_status_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."loan_status_history_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: loan_status_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."loan_status_history_id_seq" OWNED BY "public"."loan_status_history"."id";


--
-- Name: location; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."location" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" character varying NOT NULL,
    "description" "text",
    "location_type" character varying DEFAULT 'PANOL_SHELF'::character varying NOT NULL,
    "active" boolean DEFAULT true NOT NULL
);


--
-- Name: location_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."location_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: location_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."location_id_seq" OWNED BY "public"."location"."id";


--
-- Name: notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."notification" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" bigint NOT NULL,
    "title" character varying NOT NULL,
    "message" "text" NOT NULL,
    "read_status" boolean DEFAULT false NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--
-- Name: notification_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."notification_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: notification_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."notification_id_seq" OWNED BY "public"."notification"."id";


--
-- Name: outbox_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."outbox_event" (
    "id" bigint NOT NULL,
    "event_id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "aggregate_type" character varying NOT NULL,
    "aggregate_id" "uuid",
    "event_type" character varying NOT NULL,
    "payload" "text" NOT NULL,
    "status" "public"."outbox_status_enum" DEFAULT 'PENDING'::"public"."outbox_status_enum" NOT NULL,
    "retry_count" integer DEFAULT 0 NOT NULL,
    "occurred_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "processed_at" timestamp with time zone
);


--
-- Name: outbox_event_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."outbox_event_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: outbox_event_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."outbox_event_id_seq" OWNED BY "public"."outbox_event"."id";


--
-- Name: outbox_events; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."outbox_events" AS
 SELECT "event_id",
    "aggregate_type",
    "aggregate_id",
    "event_type",
    "payload",
    "occurred_at",
    "processed_at",
    "retry_count",
    "status"
   FROM "public"."outbox_event";


--
-- Name: role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."role" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" character varying NOT NULL,
    "description" character varying
);


--
-- Name: role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."role_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."role_id_seq" OWNED BY "public"."role"."id";


--
-- Name: room; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."room" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" character varying NOT NULL,
    "description" "text",
    "active" boolean DEFAULT true NOT NULL
);


--
-- Name: room_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."room_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: room_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."room_id_seq" OWNED BY "public"."room"."id";


--
-- Name: stock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."stock" (
    "id" bigint NOT NULL,
    "implement_id" bigint NOT NULL,
    "total_stock" integer DEFAULT 0 NOT NULL,
    "min_stock" integer DEFAULT 0 NOT NULL,
    "available" integer DEFAULT 0 NOT NULL,
    "reserved" integer DEFAULT 0 NOT NULL,
    "loaned" integer DEFAULT 0 NOT NULL,
    "damaged" integer DEFAULT 0 NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "chk_stock_counters_integrity" CHECK ((((("available" + "reserved") + "loaned") + "damaged") = "total_stock")),
    CONSTRAINT "stock_available_check" CHECK (("available" >= 0)),
    CONSTRAINT "stock_damaged_check" CHECK (("damaged" >= 0)),
    CONSTRAINT "stock_loaned_check" CHECK (("loaned" >= 0)),
    CONSTRAINT "stock_min_stock_check" CHECK (("min_stock" >= 0)),
    CONSTRAINT "stock_reserved_check" CHECK (("reserved" >= 0)),
    CONSTRAINT "stock_total_stock_check" CHECK (("total_stock" >= 0))
);


--
-- Name: stock_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."stock_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: stock_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."stock_id_seq" OWNED BY "public"."stock"."id";


--
-- Name: subject; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."subject" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "code" character varying NOT NULL,
    "name" character varying NOT NULL,
    "active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--
-- Name: subject_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."subject_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: subject_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."subject_id_seq" OWNED BY "public"."subject"."id";


--
-- Name: token_revocation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."token_revocation" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "jti" character varying NOT NULL,
    "expires_at" timestamp with time zone NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--
-- Name: token_revocation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."token_revocation_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: token_revocation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."token_revocation_id_seq" OWNED BY "public"."token_revocation"."id";


--
-- Name: user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."user" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "role_id" bigint NOT NULL,
    "career_id" bigint,
    "name" character varying NOT NULL,
    "rut" character varying NOT NULL,
    "email" character varying NOT NULL,
    "password_hash" character varying NOT NULL,
    "auth_uuid" "uuid",
    "active" boolean DEFAULT true NOT NULL,
    "failed_login_attempts" integer DEFAULT 0 NOT NULL,
    "blocked_until" timestamp with time zone,
    "last_login_at" timestamp with time zone,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--
-- Name: user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."user_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."user_id_seq" OWNED BY "public"."user"."id";


--
-- Name: user_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."user_session" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "refresh_token_hash" character varying NOT NULL,
    "device_info" character varying,
    "expires_at" timestamp with time zone NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--
-- Name: user_session_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE "public"."user_session_id_seq"
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: user_session_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."user_session_id_seq" OWNED BY "public"."user_session"."id";


--
-- Name: v_active_loan_details; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_active_loan_details" AS
 SELECT "l"."id" AS "loan_id",
    "l"."uuid" AS "loan_uuid",
    "l"."status",
    "l"."scheduled_at",
    "l"."expected_return_at",
    "l"."requester_id",
    "u"."name" AS "requester_name",
    "ld"."implement_id",
    "i"."name" AS "implement_name",
    "i"."item_type",
    "ld"."requested_quantity",
    "ld"."reserved_quantity",
    "ld"."delivered_quantity"
   FROM ((("public"."loan" "l"
     JOIN "public"."loan_detail" "ld" ON (("ld"."loan_id" = "l"."id")))
     JOIN "public"."implement" "i" ON (("i"."id" = "ld"."implement_id")))
     LEFT JOIN "public"."user" "u" ON (("u"."id" = "l"."requester_id")))
  WHERE ("l"."status" = ANY (ARRAY['approved'::"public"."loan_status_enum", 'prepared'::"public"."loan_status_enum", 'delivered'::"public"."loan_status_enum", 'overdue'::"public"."loan_status_enum"]));


--
-- Name: v_individual_status_summary; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_individual_status_summary" AS
 SELECT "i"."id" AS "implement_id",
    "i"."name" AS "implement_name",
    ("count"("ind"."id"))::integer AS "total_individuals",
    ("count"("ind"."id") FILTER (WHERE ("ind"."status" = 'available'::"public"."individual_status_enum")))::integer AS "available_count",
    ("count"("ind"."id") FILTER (WHERE ("ind"."status" = 'loaned'::"public"."individual_status_enum")))::integer AS "loaned_count",
    ("count"("ind"."id") FILTER (WHERE ("ind"."status" = 'maintenance'::"public"."individual_status_enum")))::integer AS "maintenance_count",
    ("count"("ind"."id") FILTER (WHERE ("ind"."status" = 'damaged'::"public"."individual_status_enum")))::integer AS "damaged_count",
    ("count"("ind"."id") FILTER (WHERE ("ind"."status" = 'blocked'::"public"."individual_status_enum")))::integer AS "blocked_count",
    ("count"("ind"."id") FILTER (WHERE ("ind"."status" = 'retired'::"public"."individual_status_enum")))::integer AS "retired_count"
   FROM ("public"."implement" "i"
     LEFT JOIN "public"."individual" "ind" ON ((("ind"."implement_id" = "i"."id") AND ("ind"."active" IS TRUE))))
  GROUP BY "i"."id", "i"."name";


--
-- Name: v_loan_state_dates; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_loan_state_dates" AS
 SELECT "l"."id" AS "loan_id",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'approved'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "approved_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'prepared'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "prepared_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'delivered'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "delivered_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'completed'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "completed_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'rejected'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "rejected_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'cancelled'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "cancelled_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'expired'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "expired_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'overdue'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "overdue_at"
   FROM ("public"."loan" "l"
     LEFT JOIN "public"."loan_status_history" "h" ON (("h"."loan_id" = "l"."id")))
  GROUP BY "l"."id";


--
-- Name: v_loan_requests_calendar; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_loan_requests_calendar" AS
 SELECT "l"."id" AS "loan_id",
    "l"."uuid" AS "loan_uuid",
    "l"."status",
        CASE "l"."status"
            WHEN 'pending'::"public"."loan_status_enum" THEN 'Pendiente'::"text"
            WHEN 'approved'::"public"."loan_status_enum" THEN 'Aprobado'::"text"
            WHEN 'prepared'::"public"."loan_status_enum" THEN 'Preparado'::"text"
            WHEN 'delivered'::"public"."loan_status_enum" THEN 'Entregado'::"text"
            WHEN 'completed'::"public"."loan_status_enum" THEN 'Completado'::"text"
            WHEN 'rejected'::"public"."loan_status_enum" THEN 'Rechazado'::"text"
            WHEN 'cancelled'::"public"."loan_status_enum" THEN 'Cancelado'::"text"
            WHEN 'expired'::"public"."loan_status_enum" THEN 'Expirado'::"text"
            WHEN 'overdue'::"public"."loan_status_enum" THEN 'Atrasado'::"text"
            ELSE ("l"."status")::"text"
        END AS "status_label",
    "l"."scheduled_at",
    "l"."expected_return_at",
    "l"."created_at",
    "l"."requester_id",
    "u"."name" AS "requester_name",
    "u"."email" AS "requester_email",
    "l"."room_id",
    "r"."name" AS "room_name",
    "l"."subject_id",
    "s"."code" AS "subject_code",
    "s"."name" AS "subject_name",
    "ld"."implement_id",
    "i"."name" AS "implement_name",
    "i"."item_type",
    "ld"."requested_quantity",
    "ld"."reserved_quantity",
    "ld"."delivered_quantity",
    "d"."approved_at",
    "d"."prepared_at",
    "d"."delivered_at",
    "d"."completed_at"
   FROM (((((("public"."loan" "l"
     JOIN "public"."loan_detail" "ld" ON (("ld"."loan_id" = "l"."id")))
     JOIN "public"."implement" "i" ON (("i"."id" = "ld"."implement_id")))
     LEFT JOIN "public"."user" "u" ON (("u"."id" = "l"."requester_id")))
     LEFT JOIN "public"."room" "r" ON (("r"."id" = "l"."room_id")))
     LEFT JOIN "public"."subject" "s" ON (("s"."id" = "l"."subject_id")))
     LEFT JOIN "public"."v_loan_state_dates" "d" ON (("d"."loan_id" = "l"."id")));


--
-- Name: v_loan_requests_summary; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_loan_requests_summary" AS
 SELECT "l"."id" AS "loan_id",
    "l"."uuid" AS "loan_uuid",
    "l"."status",
        CASE "l"."status"
            WHEN 'pending'::"public"."loan_status_enum" THEN 'Pendiente'::"text"
            WHEN 'approved'::"public"."loan_status_enum" THEN 'Aprobado'::"text"
            WHEN 'prepared'::"public"."loan_status_enum" THEN 'Preparado'::"text"
            WHEN 'delivered'::"public"."loan_status_enum" THEN 'Entregado'::"text"
            WHEN 'completed'::"public"."loan_status_enum" THEN 'Completado'::"text"
            WHEN 'rejected'::"public"."loan_status_enum" THEN 'Rechazado'::"text"
            WHEN 'cancelled'::"public"."loan_status_enum" THEN 'Cancelado'::"text"
            WHEN 'expired'::"public"."loan_status_enum" THEN 'Expirado'::"text"
            WHEN 'overdue'::"public"."loan_status_enum" THEN 'Atrasado'::"text"
            ELSE ("l"."status")::"text"
        END AS "status_label",
    "l"."scheduled_at",
    "l"."expected_return_at",
    "l"."created_at",
    "l"."requester_id",
    "u"."name" AS "requester_name",
    "u"."email" AS "requester_email",
    "l"."room_id",
    "r"."name" AS "room_name",
    "l"."subject_id",
    "s"."code" AS "subject_code",
    "s"."name" AS "subject_name",
    (COALESCE("count"("ld"."implement_id"), (0)::bigint))::integer AS "total_implement_types",
    (COALESCE("sum"("ld"."requested_quantity"), (0)::bigint))::integer AS "total_requested_quantity",
    (COALESCE("sum"("ld"."reserved_quantity"), (0)::bigint))::integer AS "total_reserved_quantity",
    (COALESCE("sum"("ld"."delivered_quantity"), (0)::bigint))::integer AS "total_delivered_quantity",
    "d"."approved_at",
    "d"."prepared_at",
    "d"."delivered_at",
    "d"."completed_at",
    COALESCE("jsonb_agg"("jsonb_build_object"('implement_id', "i"."id", 'implement_name', "i"."name", 'item_type', "i"."item_type", 'requested_quantity', "ld"."requested_quantity", 'reserved_quantity', "ld"."reserved_quantity", 'delivered_quantity', "ld"."delivered_quantity") ORDER BY "i"."name") FILTER (WHERE ("ld"."implement_id" IS NOT NULL)), '[]'::"jsonb") AS "details"
   FROM (((((("public"."loan" "l"
     LEFT JOIN "public"."loan_detail" "ld" ON (("ld"."loan_id" = "l"."id")))
     LEFT JOIN "public"."implement" "i" ON (("i"."id" = "ld"."implement_id")))
     LEFT JOIN "public"."user" "u" ON (("u"."id" = "l"."requester_id")))
     LEFT JOIN "public"."room" "r" ON (("r"."id" = "l"."room_id")))
     LEFT JOIN "public"."subject" "s" ON (("s"."id" = "l"."subject_id")))
     LEFT JOIN "public"."v_loan_state_dates" "d" ON (("d"."loan_id" = "l"."id")))
  GROUP BY "l"."id", "l"."uuid", "l"."status", "l"."scheduled_at", "l"."expected_return_at", "l"."created_at", "l"."requester_id", "u"."name", "u"."email", "l"."room_id", "r"."name", "l"."subject_id", "s"."code", "s"."name", "d"."approved_at", "d"."prepared_at", "d"."delivered_at", "d"."completed_at";


--
-- Name: v_loan_status_timeline; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_loan_status_timeline" AS
 SELECT "h"."id" AS "history_id",
    "h"."loan_id",
    "l"."uuid" AS "loan_uuid",
    "h"."from_status",
    "h"."to_status",
    "h"."actor_user_id",
    "u"."name" AS "actor_name",
    "u"."email" AS "actor_email",
    "h"."notes",
    "h"."changed_at"
   FROM (("public"."loan_status_history" "h"
     JOIN "public"."loan" "l" ON (("l"."id" = "h"."loan_id")))
     LEFT JOIN "public"."user" "u" ON (("u"."id" = "h"."actor_user_id")));


--
-- Name: v_low_stock; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_low_stock" AS
 SELECT "i"."id" AS "implement_id",
    "i"."uuid" AS "implement_uuid",
    "i"."name" AS "implement_name",
    "i"."category_id",
    "c"."name" AS "category_name",
    "i"."location_id",
    "loc"."name" AS "location_name",
    "i"."item_type",
    "s"."total_stock",
    "s"."min_stock",
    "s"."available",
    "s"."reserved",
    "s"."loaned",
    "s"."damaged",
    ("s"."min_stock" - "s"."available") AS "stock_gap",
        CASE
            WHEN ("s"."available" = 0) THEN 'out_of_stock'::"text"
            WHEN ("s"."available" < "s"."min_stock") THEN 'low_stock'::"text"
            ELSE 'ok'::"text"
        END AS "stock_status",
    "s"."updated_at"
   FROM ((("public"."stock" "s"
     JOIN "public"."implement" "i" ON (("i"."id" = "s"."implement_id")))
     LEFT JOIN "public"."category" "c" ON (("c"."id" = "i"."category_id")))
     LEFT JOIN "public"."location" "loc" ON (("loc"."id" = "i"."location_id")));


--
-- Name: audit_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."audit_log" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."audit_log_id_seq"'::"regclass");


--
-- Name: career id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."career" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."career_id_seq"'::"regclass");


--
-- Name: category id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."category" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."category_id_seq"'::"regclass");


--
-- Name: email_outbox id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."email_outbox" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."email_outbox_id_seq"'::"regclass");


--
-- Name: implement id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."implement_id_seq"'::"regclass");


--
-- Name: individual id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."individual" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."individual_id_seq"'::"regclass");


--
-- Name: inventory_movement id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."inventory_movement" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."inventory_movement_id_seq"'::"regclass");


--
-- Name: loan id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."loan_id_seq"'::"regclass");


--
-- Name: loan_status_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_status_history" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."loan_status_history_id_seq"'::"regclass");


--
-- Name: location id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."location" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."location_id_seq"'::"regclass");


--
-- Name: notification id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."notification" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."notification_id_seq"'::"regclass");


--
-- Name: outbox_event id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."outbox_event" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."outbox_event_id_seq"'::"regclass");


--
-- Name: role id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."role" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."role_id_seq"'::"regclass");


--
-- Name: room id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."room" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."room_id_seq"'::"regclass");


--
-- Name: stock id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."stock" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."stock_id_seq"'::"regclass");


--
-- Name: subject id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."subject" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."subject_id_seq"'::"regclass");


--
-- Name: token_revocation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."token_revocation" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."token_revocation_id_seq"'::"regclass");


--
-- Name: user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."user_id_seq"'::"regclass");


--
-- Name: user_session id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user_session" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."user_session_id_seq"'::"regclass");


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."audit_log"
    ADD CONSTRAINT "audit_log_pkey" PRIMARY KEY ("id");


--
-- Name: career career_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."career"
    ADD CONSTRAINT "career_code_key" UNIQUE ("code");


--
-- Name: career career_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."career"
    ADD CONSTRAINT "career_pkey" PRIMARY KEY ("id");


--
-- Name: career career_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."career"
    ADD CONSTRAINT "career_uuid_key" UNIQUE ("uuid");


--
-- Name: category category_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."category"
    ADD CONSTRAINT "category_name_key" UNIQUE ("name");


--
-- Name: category category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."category"
    ADD CONSTRAINT "category_pkey" PRIMARY KEY ("id");


--
-- Name: category category_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."category"
    ADD CONSTRAINT "category_uuid_key" UNIQUE ("uuid");


--
-- Name: email_outbox email_outbox_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."email_outbox"
    ADD CONSTRAINT "email_outbox_event_id_key" UNIQUE ("event_id");


--
-- Name: email_outbox email_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."email_outbox"
    ADD CONSTRAINT "email_outbox_pkey" PRIMARY KEY ("id");


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."flyway_schema_history"
    ADD CONSTRAINT "flyway_schema_history_pk" PRIMARY KEY ("installed_rank");


--
-- Name: implement implement_barcode_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement"
    ADD CONSTRAINT "implement_barcode_key" UNIQUE ("barcode");


--
-- Name: implement implement_name_category_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement"
    ADD CONSTRAINT "implement_name_category_key" UNIQUE ("name", "category_id");


--
-- Name: implement implement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement"
    ADD CONSTRAINT "implement_pkey" PRIMARY KEY ("id");


--
-- Name: implement implement_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement"
    ADD CONSTRAINT "implement_uuid_key" UNIQUE ("uuid");


--
-- Name: individual individual_asset_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."individual"
    ADD CONSTRAINT "individual_asset_code_key" UNIQUE ("asset_code");


--
-- Name: individual individual_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."individual"
    ADD CONSTRAINT "individual_pkey" PRIMARY KEY ("id");


--
-- Name: individual individual_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."individual"
    ADD CONSTRAINT "individual_uuid_key" UNIQUE ("uuid");


--
-- Name: inventory_movement inventory_movement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."inventory_movement"
    ADD CONSTRAINT "inventory_movement_pkey" PRIMARY KEY ("id");


--
-- Name: loan_detail_individual loan_detail_individual_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_detail_individual"
    ADD CONSTRAINT "loan_detail_individual_pkey" PRIMARY KEY ("loan_id", "implement_id", "individual_id");


--
-- Name: loan_detail loan_detail_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_detail"
    ADD CONSTRAINT "loan_detail_pkey" PRIMARY KEY ("loan_id", "implement_id");


--
-- Name: loan loan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan"
    ADD CONSTRAINT "loan_pkey" PRIMARY KEY ("id");


--
-- Name: loan_status_history loan_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_status_history"
    ADD CONSTRAINT "loan_status_history_pkey" PRIMARY KEY ("id");


--
-- Name: loan loan_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan"
    ADD CONSTRAINT "loan_uuid_key" UNIQUE ("uuid");


--
-- Name: location location_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."location"
    ADD CONSTRAINT "location_name_key" UNIQUE ("name");


--
-- Name: location location_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."location"
    ADD CONSTRAINT "location_pkey" PRIMARY KEY ("id");


--
-- Name: location location_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."location"
    ADD CONSTRAINT "location_uuid_key" UNIQUE ("uuid");


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."notification"
    ADD CONSTRAINT "notification_pkey" PRIMARY KEY ("id");


--
-- Name: notification notification_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."notification"
    ADD CONSTRAINT "notification_uuid_key" UNIQUE ("uuid");


--
-- Name: outbox_event outbox_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."outbox_event"
    ADD CONSTRAINT "outbox_event_id_key" UNIQUE ("event_id");


--
-- Name: outbox_event outbox_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."outbox_event"
    ADD CONSTRAINT "outbox_event_pkey" PRIMARY KEY ("id");


--
-- Name: role role_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."role"
    ADD CONSTRAINT "role_name_key" UNIQUE ("name");


--
-- Name: role role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."role"
    ADD CONSTRAINT "role_pkey" PRIMARY KEY ("id");


--
-- Name: role role_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."role"
    ADD CONSTRAINT "role_uuid_key" UNIQUE ("uuid");


--
-- Name: room room_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."room"
    ADD CONSTRAINT "room_name_key" UNIQUE ("name");


--
-- Name: room room_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."room"
    ADD CONSTRAINT "room_pkey" PRIMARY KEY ("id");


--
-- Name: room room_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."room"
    ADD CONSTRAINT "room_uuid_key" UNIQUE ("uuid");


--
-- Name: stock stock_implement_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."stock"
    ADD CONSTRAINT "stock_implement_id_key" UNIQUE ("implement_id");


--
-- Name: stock stock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."stock"
    ADD CONSTRAINT "stock_pkey" PRIMARY KEY ("id");


--
-- Name: subject subject_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."subject"
    ADD CONSTRAINT "subject_code_key" UNIQUE ("code");


--
-- Name: subject subject_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."subject"
    ADD CONSTRAINT "subject_pkey" PRIMARY KEY ("id");


--
-- Name: subject subject_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."subject"
    ADD CONSTRAINT "subject_uuid_key" UNIQUE ("uuid");


--
-- Name: token_revocation token_revocation_jti_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."token_revocation"
    ADD CONSTRAINT "token_revocation_jti_key" UNIQUE ("jti");


--
-- Name: token_revocation token_revocation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."token_revocation"
    ADD CONSTRAINT "token_revocation_pkey" PRIMARY KEY ("id");


--
-- Name: user user_auth_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "user_auth_uuid_key" UNIQUE ("auth_uuid");


--
-- Name: user user_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "user_email_key" UNIQUE ("email");


--
-- Name: user user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "user_pkey" PRIMARY KEY ("id");


--
-- Name: user user_rut_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "user_rut_key" UNIQUE ("rut");


--
-- Name: user_session user_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user_session"
    ADD CONSTRAINT "user_session_pkey" PRIMARY KEY ("id");


--
-- Name: user_session user_session_refresh_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user_session"
    ADD CONSTRAINT "user_session_refresh_token_hash_key" UNIQUE ("refresh_token_hash");


--
-- Name: user user_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "user_uuid_key" UNIQUE ("uuid");


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "flyway_schema_history_s_idx" ON "public"."flyway_schema_history" USING "btree" ("success");


--
-- Name: idx_individual_implement_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_individual_implement_status" ON "public"."individual" USING "btree" ("implement_id", "status");


--
-- Name: idx_inventory_movement_implement_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_inventory_movement_implement_date" ON "public"."inventory_movement" USING "btree" ("implement_id", "created_at" DESC);


--
-- Name: idx_loan_detail_implement_loan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_detail_implement_loan" ON "public"."loan_detail" USING "btree" ("implement_id", "loan_id");


--
-- Name: idx_loan_history_loan_changed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_history_loan_changed" ON "public"."loan_status_history" USING "btree" ("loan_id", "changed_at" DESC);


--
-- Name: idx_loan_history_to_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_history_to_status" ON "public"."loan_status_history" USING "btree" ("loan_id", "to_status", "changed_at" DESC);


--
-- Name: idx_loan_requester_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_requester_status" ON "public"."loan" USING "btree" ("requester_id", "status");


--
-- Name: idx_loan_schedule_range; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_schedule_range" ON "public"."loan" USING "btree" ("scheduled_at", "expected_return_at");


--
-- Name: idx_loan_status_history_to_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_status_history_to_status" ON "public"."loan_status_history" USING "btree" ("to_status");


--
-- Name: idx_loan_status_scheduled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_status_scheduled" ON "public"."loan" USING "btree" ("status", "scheduled_at");


--
-- Name: idx_notification_user_read; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_notification_user_read" ON "public"."notification" USING "btree" ("user_id", "read_status", "created_at" DESC);


--
-- Name: loan_status_history trg_create_notification_on_loan_status; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_create_notification_on_loan_status" AFTER INSERT ON "public"."loan_status_history" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_create_notification_on_loan_status"();


--
-- Name: loan_status_history trg_create_outbox_event_on_loan_status; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_create_outbox_event_on_loan_status" AFTER INSERT ON "public"."loan_status_history" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_create_outbox_event_on_loan_status"();


--
-- Name: implement trg_implement_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_implement_updated_at" BEFORE UPDATE ON "public"."implement" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_implement_updated_at"();


--
-- Name: individual trg_individual_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_individual_updated_at" BEFORE UPDATE ON "public"."individual" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_individual_updated_at"();


--
-- Name: inventory_movement trg_inventory_movement_audit; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_inventory_movement_audit" AFTER INSERT ON "public"."inventory_movement" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_inventory_movement_audit"();


--
-- Name: loan trg_loan_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_loan_updated_at" BEFORE UPDATE ON "public"."loan" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_loan_updated_at"();


--
-- Name: stock trg_stock_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_stock_updated_at" BEFORE UPDATE ON "public"."stock" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_stock_updated_at"();


--
-- Name: individual trg_validate_individual_item_type; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_validate_individual_item_type" BEFORE INSERT OR UPDATE OF "implement_id" ON "public"."individual" FOR EACH ROW EXECUTE FUNCTION "public"."fn_validate_individual_item_type"();


--
-- Name: loan trg_validate_loan_dates; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_validate_loan_dates" BEFORE INSERT OR UPDATE OF "scheduled_at", "expected_return_at" ON "public"."loan" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_validate_loan_dates"();


--
-- Name: loan trg_validate_loan_status_transition; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_validate_loan_status_transition" BEFORE UPDATE OF "status" ON "public"."loan" FOR EACH ROW EXECUTE FUNCTION "public"."fn_validate_loan_status_transition"();


--
-- Name: audit_log fk_audit_actor_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."audit_log"
    ADD CONSTRAINT "fk_audit_actor_user" FOREIGN KEY ("actor_user_id") REFERENCES "public"."user"("id") ON DELETE SET NULL;


--
-- Name: audit_log fk_audit_target_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."audit_log"
    ADD CONSTRAINT "fk_audit_target_user" FOREIGN KEY ("target_user_id") REFERENCES "public"."user"("id") ON DELETE SET NULL;


--
-- Name: implement fk_implement_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement"
    ADD CONSTRAINT "fk_implement_category" FOREIGN KEY ("category_id") REFERENCES "public"."category"("id") ON DELETE RESTRICT;


--
-- Name: implement fk_implement_location; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement"
    ADD CONSTRAINT "fk_implement_location" FOREIGN KEY ("location_id") REFERENCES "public"."location"("id") ON DELETE RESTRICT;


--
-- Name: individual fk_individual_implement; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."individual"
    ADD CONSTRAINT "fk_individual_implement" FOREIGN KEY ("implement_id") REFERENCES "public"."implement"("id") ON DELETE CASCADE;


--
-- Name: individual fk_individual_location; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."individual"
    ADD CONSTRAINT "fk_individual_location" FOREIGN KEY ("current_location_id") REFERENCES "public"."location"("id") ON DELETE RESTRICT;


--
-- Name: inventory_movement fk_inv_movement_actor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."inventory_movement"
    ADD CONSTRAINT "fk_inv_movement_actor" FOREIGN KEY ("actor_user_id") REFERENCES "public"."user"("id") ON DELETE RESTRICT;


--
-- Name: inventory_movement fk_inv_movement_implement; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."inventory_movement"
    ADD CONSTRAINT "fk_inv_movement_implement" FOREIGN KEY ("implement_id") REFERENCES "public"."implement"("id") ON DELETE RESTRICT;


--
-- Name: loan_detail fk_loan_detail_implement; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_detail"
    ADD CONSTRAINT "fk_loan_detail_implement" FOREIGN KEY ("implement_id") REFERENCES "public"."implement"("id") ON DELETE RESTRICT;


--
-- Name: loan_detail_individual fk_loan_detail_individual_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_detail_individual"
    ADD CONSTRAINT "fk_loan_detail_individual_parent" FOREIGN KEY ("loan_id", "implement_id") REFERENCES "public"."loan_detail"("loan_id", "implement_id") ON DELETE CASCADE;


--
-- Name: loan_detail_individual fk_loan_detail_individual_unit; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_detail_individual"
    ADD CONSTRAINT "fk_loan_detail_individual_unit" FOREIGN KEY ("individual_id") REFERENCES "public"."individual"("id") ON DELETE RESTRICT;


--
-- Name: loan_detail fk_loan_detail_loan; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_detail"
    ADD CONSTRAINT "fk_loan_detail_loan" FOREIGN KEY ("loan_id") REFERENCES "public"."loan"("id") ON DELETE CASCADE;


--
-- Name: loan_status_history fk_loan_history_actor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_status_history"
    ADD CONSTRAINT "fk_loan_history_actor" FOREIGN KEY ("actor_user_id") REFERENCES "public"."user"("id") ON DELETE RESTRICT;


--
-- Name: loan_status_history fk_loan_history_loan; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_status_history"
    ADD CONSTRAINT "fk_loan_history_loan" FOREIGN KEY ("loan_id") REFERENCES "public"."loan"("id") ON DELETE CASCADE;


--
-- Name: loan fk_loan_requester; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan"
    ADD CONSTRAINT "fk_loan_requester" FOREIGN KEY ("requester_id") REFERENCES "public"."user"("id") ON DELETE RESTRICT;


--
-- Name: loan fk_loan_room; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan"
    ADD CONSTRAINT "fk_loan_room" FOREIGN KEY ("room_id") REFERENCES "public"."room"("id") ON DELETE RESTRICT;


--
-- Name: loan fk_loan_subject; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan"
    ADD CONSTRAINT "fk_loan_subject" FOREIGN KEY ("subject_id") REFERENCES "public"."subject"("id") ON DELETE RESTRICT;


--
-- Name: notification fk_notification_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."notification"
    ADD CONSTRAINT "fk_notification_user" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE CASCADE;


--
-- Name: stock fk_stock_implement; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."stock"
    ADD CONSTRAINT "fk_stock_implement" FOREIGN KEY ("implement_id") REFERENCES "public"."implement"("id") ON DELETE CASCADE;


--
-- Name: token_revocation fk_token_revocation_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."token_revocation"
    ADD CONSTRAINT "fk_token_revocation_user" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE CASCADE;


--
-- Name: user fk_user_career; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "fk_user_career" FOREIGN KEY ("career_id") REFERENCES "public"."career"("id") ON DELETE RESTRICT;


--
-- Name: user fk_user_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "fk_user_role" FOREIGN KEY ("role_id") REFERENCES "public"."role"("id") ON DELETE RESTRICT;


--
-- Name: user_session fk_user_session_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user_session"
    ADD CONSTRAINT "fk_user_session_user" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE CASCADE;


--
-- Name: audit_log; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE "public"."audit_log" ENABLE ROW LEVEL SECURITY;

--
-- Name: audit_log audit_log_select_director; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "audit_log_select_director" ON "public"."audit_log" FOR SELECT USING ((("public"."get_current_user_role"())::"text" = 'director'::"text"));


--
-- Name: inventory_movement; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE "public"."inventory_movement" ENABLE ROW LEVEL SECURITY;

--
-- Name: inventory_movement inventory_movement_insert_staff; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "inventory_movement_insert_staff" ON "public"."inventory_movement" FOR INSERT WITH CHECK ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[])));


--
-- Name: inventory_movement inventory_movement_select_staff; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "inventory_movement_select_staff" ON "public"."inventory_movement" FOR SELECT USING ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[])));


--
-- Name: loan; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE "public"."loan" ENABLE ROW LEVEL SECURITY;

--
-- Name: loan loan_all_staff; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "loan_all_staff" ON "public"."loan" USING ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[]))) WITH CHECK ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[])));


--
-- Name: loan loan_insert_own; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "loan_insert_own" ON "public"."loan" FOR INSERT WITH CHECK (("requester_id" = "public"."get_current_user_id"()));


--
-- Name: loan loan_select_own; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "loan_select_own" ON "public"."loan" FOR SELECT USING (("requester_id" = "public"."get_current_user_id"()));


--
-- Name: notification; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE "public"."notification" ENABLE ROW LEVEL SECURITY;

--
-- Name: notification notification_all_director; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "notification_all_director" ON "public"."notification" USING ((("public"."get_current_user_role"())::"text" = 'director'::"text")) WITH CHECK ((("public"."get_current_user_role"())::"text" = 'director'::"text"));


--
-- Name: notification notification_all_own; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "notification_all_own" ON "public"."notification" USING (("user_id" = "public"."get_current_user_id"())) WITH CHECK (("user_id" = "public"."get_current_user_id"()));


--
-- Name: stock; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE "public"."stock" ENABLE ROW LEVEL SECURITY;

--
-- Name: stock stock_modify_staff; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "stock_modify_staff" ON "public"."stock" USING ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[]))) WITH CHECK ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[])));


--
-- Name: stock stock_select_authenticated; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "stock_select_authenticated" ON "public"."stock" FOR SELECT USING ("public"."is_authenticated"());


--
-- Name: user; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE "public"."user" ENABLE ROW LEVEL SECURITY;

--
-- Name: user user_all_director; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "user_all_director" ON "public"."user" USING ((("public"."get_current_user_role"())::"text" = 'director'::"text")) WITH CHECK ((("public"."get_current_user_role"())::"text" = 'director'::"text"));


--
-- Name: user user_select_own; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "user_select_own" ON "public"."user" FOR SELECT USING (("uuid" = (NULLIF("current_setting"('app.current_user_uuid'::"text", true), ''::"text"))::"uuid"));


--
-- Name: user user_select_staff; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "user_select_staff" ON "public"."user" FOR SELECT USING ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[])));


--
-- Name: user user_update_own; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "user_update_own" ON "public"."user" FOR UPDATE USING (("uuid" = (NULLIF("current_setting"('app.current_user_uuid'::"text", true), ''::"text"))::"uuid")) WITH CHECK (("uuid" = (NULLIF("current_setting"('app.current_user_uuid'::"text", true), ''::"text"))::"uuid"));


--
-- PostgreSQL database dump complete
--

\unrestrict MbWnuFeujAy3SGRnmK80TpSOfxhaHv6N93sNEz1f62oWNYkzGfsHRcwOBDVq63y

