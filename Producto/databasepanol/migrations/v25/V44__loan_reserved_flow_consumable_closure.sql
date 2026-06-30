CREATE OR REPLACE FUNCTION public.fn_notify_new_loan_request(
    p_requester_user_uuid UUID,
    p_loan_uuid UUID
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
VOLATILE
SET search_path = public, pg_temp
SET row_security = off
AS $$
DECLARE
    v_requester_name CHARACTER VARYING;
    v_title TEXT := 'Nueva solicitud reservada';
    v_message TEXT;
    v_created_at TIMESTAMPTZ := now();
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

    v_message := 'El docente ' || v_requester_name || ' genero una nueva solicitud con reserva automatica.';

    INSERT INTO public.notification (
        user_id,
        title,
        message,
        read_status,
        created_at,
        reference_type,
        reference_id,
        metadata
    )
    SELECT u.id,
           v_title,
           v_message,
           false,
           v_created_at,
           'loan',
           p_loan_uuid,
           jsonb_build_object(
               'event_type', 'loan.request_submitted',
               'flow_mode', 'auto_reserved'
           )
      FROM public."user" u
      JOIN public.role r ON r.id = u.role_id
     WHERE r.name IN ('coordinador', 'director')
       AND u.active IS TRUE
       AND u.uuid <> '99999999-9999-9999-9999-999999999999'::UUID;

    INSERT INTO public.email_outbox (
        recipient_email,
        email_type,
        template_data,
        status,
        retry_count,
        occurred_at
    )
    SELECT u.email,
           'loan.request_submitted.coordinador',
           public.fn_build_loan_email_template_data(
               'loan.request_submitted',
               v_title,
               v_message,
               p_loan_uuid,
               v_created_at
           ),
           'PENDING'::public.outbox_status_enum,
           0,
           v_created_at
      FROM public."user" u
      JOIN public.role r ON r.id = u.role_id
     WHERE r.name IN ('coordinador', 'director')
       AND u.active IS TRUE
       AND u.uuid <> '99999999-9999-9999-9999-999999999999'::UUID
       AND u.email IS NOT NULL
       AND btrim(u.email) <> '';
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_notify_pending_loan_updated(
    p_requester_user_uuid UUID,
    p_loan_uuid UUID
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
VOLATILE
SET search_path = public, pg_temp
SET row_security = off
AS $$
DECLARE
    v_requester_name CHARACTER VARYING;
    v_title TEXT := 'Solicitud actualizada';
    v_message TEXT;
    v_created_at TIMESTAMPTZ := now();
BEGIN
    SELECT u.name
      INTO v_requester_name
      FROM public."user" u
     WHERE u.uuid = p_requester_user_uuid
       AND u.active IS TRUE
     LIMIT 1;

    IF v_requester_name IS NULL THEN
        RAISE EXCEPTION 'No se pudo resolver el docente solicitante para la notificacion de modificacion de prestamo';
    END IF;

    v_message := 'El docente ' || v_requester_name || ' modifico una solicitud reservada.';

    INSERT INTO public.notification (
        user_id,
        title,
        message,
        read_status,
        created_at,
        reference_type,
        reference_id,
        metadata
    )
    SELECT u.id,
           v_title,
           v_message,
           false,
           v_created_at,
           'loan',
           p_loan_uuid,
           jsonb_build_object(
               'event_type', 'loan.request_updated',
               'flow_mode', 'auto_reserved'
           )
      FROM public."user" u
      JOIN public.role r ON r.id = u.role_id
     WHERE r.name IN ('coordinador', 'director')
       AND u.active IS TRUE
       AND u.uuid <> '99999999-9999-9999-9999-999999999999'::UUID;

    INSERT INTO public.email_outbox (
        recipient_email,
        email_type,
        template_data,
        status,
        retry_count,
        occurred_at
    )
    SELECT u.email,
           'loan.request_updated.coordinador',
           public.fn_build_loan_email_template_data(
               'loan.request_updated',
               v_title,
               v_message,
               p_loan_uuid,
               v_created_at
           ),
           'PENDING'::public.outbox_status_enum,
           0,
           v_created_at
      FROM public."user" u
      JOIN public.role r ON r.id = u.role_id
     WHERE r.name IN ('coordinador', 'director')
       AND u.active IS TRUE
       AND u.uuid <> '99999999-9999-9999-9999-999999999999'::UUID
       AND u.email IS NOT NULL
       AND btrim(u.email) <> '';
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_trg_create_notification_on_loan_status()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_requester_id BIGINT;
    v_requester_email TEXT;
    v_loan_uuid UUID;
    v_requester_role TEXT;
    v_title TEXT;
    v_message TEXT;
BEGIN
    SELECT l.requester_id, u.email, l.uuid, r.name
      INTO v_requester_id, v_requester_email, v_loan_uuid, v_requester_role
      FROM public.loan l
      JOIN public."user" u
        ON u.id = l.requester_id
      JOIN public.role r
        ON r.id = u.role_id
     WHERE l.id = NEW.loan_id;

    IF v_requester_id IS NULL
       OR v_requester_role IS DISTINCT FROM 'docente'
       OR NEW.from_status IS NULL
       OR NEW.from_status = NEW.to_status
       OR NEW.to_status = 'approved' THEN
        RETURN NEW;
    END IF;

    v_title := CASE NEW.to_status
        WHEN 'prepared'  THEN 'Implementos preparados'
        WHEN 'delivered' THEN 'Prestamo en uso'
        WHEN 'completed' THEN 'Prestamo finalizado'
        WHEN 'rejected'  THEN 'Solicitud rechazada'
        WHEN 'cancelled' THEN 'Solicitud cancelada'
        WHEN 'expired'   THEN 'Solicitud vencida'
        WHEN 'overdue'   THEN 'Prestamo atrasado'
        ELSE 'Actualizacion de prestamo'
    END;

    v_message := CASE NEW.to_status
        WHEN 'prepared'  THEN format('Tu solicitud %s fue preparada y ya puede ser entregada.', v_loan_uuid)
        WHEN 'delivered' THEN format('Tu prestamo %s ya se encuentra en uso.', v_loan_uuid)
        WHEN 'completed' THEN format('Tu prestamo %s fue finalizado.', v_loan_uuid)
        WHEN 'rejected'  THEN format('Tu solicitud %s fue rechazada.', v_loan_uuid)
        WHEN 'cancelled' THEN format('Tu solicitud %s fue cancelada.', v_loan_uuid)
        WHEN 'expired'   THEN format('Tu solicitud %s expiro por tiempo.', v_loan_uuid)
        WHEN 'overdue'   THEN format('Tu prestamo %s esta overdue.', v_loan_uuid)
        ELSE format('Tu prestamo %s cambio de estado a %s.', v_loan_uuid, NEW.to_status::TEXT)
    END;

    INSERT INTO public.notification (
        user_id,
        title,
        message,
        read_status,
        created_at,
        reference_type,
        reference_id,
        metadata
    )
    VALUES (
        v_requester_id,
        v_title,
        v_message,
        false,
        NEW.changed_at,
        'loan',
        v_loan_uuid,
        jsonb_build_object(
            'event_type', 'loan.status_changed',
            'to_status', NEW.to_status::TEXT
        )
    );

    IF v_requester_email IS NOT NULL AND btrim(v_requester_email) <> '' THEN
        INSERT INTO public.email_outbox (
            recipient_email,
            email_type,
            template_data,
            status,
            retry_count,
            occurred_at
        )
        VALUES (
            v_requester_email,
            'loan.status_changed.docente',
            public.fn_build_loan_email_template_data(
                'loan.status_changed',
                v_title,
                v_message,
                v_loan_uuid,
                NEW.changed_at
            ),
            'PENDING'::public.outbox_status_enum,
            0,
            NEW.changed_at
        );
    END IF;

    RETURN NEW;
END;
$$;

DROP FUNCTION IF EXISTS public.fn_complete_loan(BIGINT, BIGINT, TEXT, JSONB);
CREATE OR REPLACE FUNCTION public.fn_complete_loan(
    p_loan_id BIGINT,
    p_actor_user_id BIGINT,
    p_notes TEXT DEFAULT NULL,
    p_return_payload JSONB DEFAULT '[]'::JSONB
)
RETURNS TABLE (
    loan_id BIGINT,
    new_status public.loan_status_enum,
    completed_at TIMESTAMP WITH TIME ZONE
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
SET row_security = off
AS $$
DECLARE
    v_status public.loan_status_enum;
    v_now TIMESTAMP WITH TIME ZONE := now();
    v_completed_at TIMESTAMP WITH TIME ZONE;
    v_detail RECORD;
    v_payload_item RECORD;
    v_breakdown_total INTEGER;
    v_individual_count INTEGER;
    v_open_quantity INTEGER;
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
        SELECT
            ld.implement_id,
            ld.delivered_quantity,
            ld.returned_quantity,
            ld.damaged_quantity,
            ld.lost_quantity,
            ld.consumed_quantity,
            ld.discarded_quantity,
            i.item_type
          FROM public.loan_detail ld
          JOIN public.implement i
            ON i.id = ld.implement_id
         WHERE ld.loan_id = p_loan_id
         FOR UPDATE OF ld
    LOOP
        v_open_quantity :=
            COALESCE(v_detail.delivered_quantity, 0)
          - COALESCE(v_detail.returned_quantity, 0)
          - COALESCE(v_detail.damaged_quantity, 0)
          - COALESCE(v_detail.lost_quantity, 0)
          - COALESCE(v_detail.consumed_quantity, 0)
          - COALESCE(v_detail.discarded_quantity, 0);

        IF v_open_quantity < 0 THEN
            RAISE EXCEPTION
                'Loan detail breakdown exceeds delivered quantity for implement_id=%',
                v_detail.implement_id;
        END IF;

        IF v_open_quantity = 0 THEN
            CONTINUE;
        END IF;

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

        IF v_breakdown_total <> v_open_quantity THEN
            RAISE EXCEPTION
                'Return breakdown mismatch for implement_id=%. open_quantity=% breakdown_total=%',
                v_detail.implement_id,
                v_open_quantity,
                v_breakdown_total;
        END IF;

        IF v_detail.item_type = 'individual'::public.item_type_enum
           AND v_payload_item.consumed_quantity > 0 THEN
            RAISE EXCEPTION
                'Individual implement_id=% cannot be completed with consumed_quantity > 0',
                v_detail.implement_id;
        END IF;

        UPDATE public.loan_detail ld
           SET returned_quantity = ld.returned_quantity + v_payload_item.returned_quantity,
               damaged_quantity = ld.damaged_quantity + v_payload_item.damaged_quantity,
               lost_quantity = ld.lost_quantity + v_payload_item.lost_quantity,
               consumed_quantity = ld.consumed_quantity + v_payload_item.consumed_quantity,
               discarded_quantity = ld.discarded_quantity + v_payload_item.discarded_quantity,
               return_notes = COALESCE(v_payload_item.return_notes, ld.return_notes)
         WHERE ld.loan_id = p_loan_id
           AND ld.implement_id = v_detail.implement_id;

        UPDATE public.stock s
           SET loaned = s.loaned - v_open_quantity,
               available = s.available + v_payload_item.returned_quantity,
               damaged = s.damaged + v_payload_item.damaged_quantity,
               total_stock = s.total_stock - (
                   v_payload_item.lost_quantity
                 + v_payload_item.consumed_quantity
                 + v_payload_item.discarded_quantity
               ),
               updated_at = now()
         WHERE s.implement_id = v_detail.implement_id
           AND s.loaned >= v_open_quantity
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

            IF v_individual_count < v_open_quantity THEN
                RAISE EXCEPTION
                    'Missing delivered individual assignments for implement_id=%. delivered_assignments=% open_quantity=%',
                    v_detail.implement_id,
                    v_individual_count,
                    v_open_quantity;
            END IF;

            WITH ordered AS (
                SELECT
                    ldi.individual_id,
                    row_number() OVER (ORDER BY ldi.individual_id) AS rn
                FROM public.loan_detail_individual ldi
                WHERE ldi.loan_id = p_loan_id
                  AND ldi.implement_id = v_detail.implement_id
                  AND ldi.allocation_status = 'delivered'::public.individual_allocation_status_enum
                LIMIT v_open_quantity
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
            COALESCE(NULLIF(btrim(p_notes), ''), 'Prestamo completado.')
      ) AS r;

    RETURN QUERY
    SELECT p_loan_id, 'completed'::public.loan_status_enum, v_completed_at;
END;
$$;

CREATE OR REPLACE VIEW public.v_active_loan_details AS
SELECT
    l.id AS loan_id,
    l.uuid AS loan_uuid,
    l.status,
    l.scheduled_at,
    l.expected_return_at,
    l.requester_id,
    u.name AS requester_name,
    ld.implement_id,
    i.name AS implement_name,
    i.item_type,
    ld.requested_quantity,
    ld.reserved_quantity,
    ld.delivered_quantity,
    ld.returned_quantity
FROM public.loan l
JOIN public.loan_detail ld
  ON ld.loan_id = l.id
JOIN public.implement i
  ON i.id = ld.implement_id
LEFT JOIN public."user" u
  ON u.id = l.requester_id
WHERE l.status IN ('approved', 'prepared', 'delivered', 'overdue');

CREATE OR REPLACE VIEW public.v_loan_requests_calendar AS
SELECT
    l.id AS loan_id,
    l.uuid AS loan_uuid,
    l.status,
    CASE l.status
        WHEN 'pending' THEN 'Pendiente'
        WHEN 'approved' THEN 'Reservado'
        WHEN 'prepared' THEN 'Preparado'
        WHEN 'delivered' THEN 'En uso'
        WHEN 'completed' THEN 'Finalizado'
        WHEN 'rejected' THEN 'Rechazado'
        WHEN 'cancelled' THEN 'Cancelado'
        WHEN 'expired' THEN 'Expirado'
        WHEN 'overdue' THEN 'Atrasado'
        ELSE l.status::TEXT
    END AS status_label,
    l.scheduled_at,
    l.expected_return_at,
    l.created_at,
    l.requester_id,
    u.name AS requester_name,
    u.email AS requester_email,
    l.room_id,
    r.name AS room_name,
    l.subject_id,
    s.code AS subject_code,
    s.name AS subject_name,
    ld.implement_id,
    i.name AS implement_name,
    i.item_type,
    ld.requested_quantity,
    ld.reserved_quantity,
    ld.delivered_quantity,
    d.approved_at,
    d.prepared_at,
    d.delivered_at,
    d.completed_at,
    ld.returned_quantity
FROM public.loan l
JOIN public.loan_detail ld
  ON ld.loan_id = l.id
JOIN public.implement i
  ON i.id = ld.implement_id
LEFT JOIN public."user" u
  ON u.id = l.requester_id
LEFT JOIN public.room r
  ON r.id = l.room_id
LEFT JOIN public.subject s
  ON s.id = l.subject_id
LEFT JOIN public.v_loan_state_dates d
  ON d.loan_id = l.id;

CREATE OR REPLACE VIEW public.v_loan_requests_summary AS
SELECT
    l.id AS loan_id,
    l.uuid AS loan_uuid,
    l.status,
    CASE l.status
        WHEN 'pending' THEN 'Pendiente'
        WHEN 'approved' THEN 'Reservado'
        WHEN 'prepared' THEN 'Preparado'
        WHEN 'delivered' THEN 'En uso'
        WHEN 'completed' THEN 'Finalizado'
        WHEN 'rejected' THEN 'Rechazado'
        WHEN 'cancelled' THEN 'Cancelado'
        WHEN 'expired' THEN 'Expirado'
        WHEN 'overdue' THEN 'Atrasado'
        ELSE l.status::TEXT
    END AS status_label,
    l.scheduled_at,
    l.expected_return_at,
    l.created_at,
    l.requester_id,
    u.name AS requester_name,
    u.email AS requester_email,
    l.room_id,
    r.name AS room_name,
    l.subject_id,
    s.code AS subject_code,
    s.name AS subject_name,
    COALESCE(COUNT(ld.implement_id), 0)::INTEGER AS total_implement_types,
    COALESCE(SUM(ld.requested_quantity), 0)::INTEGER AS total_requested_quantity,
    COALESCE(SUM(ld.reserved_quantity), 0)::INTEGER AS total_reserved_quantity,
    COALESCE(SUM(ld.delivered_quantity), 0)::INTEGER AS total_delivered_quantity,
    d.approved_at,
    d.prepared_at,
    d.delivered_at,
    d.completed_at,
    COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'implement_id', i.id,
                'implement_name', i.name,
                'item_type', i.item_type,
                'requested_quantity', ld.requested_quantity,
                'reserved_quantity', ld.reserved_quantity,
                'delivered_quantity', ld.delivered_quantity,
                'returned_quantity', ld.returned_quantity
            )
            ORDER BY i.name
        ) FILTER (WHERE ld.implement_id IS NOT NULL),
        '[]'::jsonb
    ) AS details,
    COALESCE(SUM(ld.returned_quantity), 0)::INTEGER AS total_returned_quantity
FROM public.loan l
LEFT JOIN public.loan_detail ld
  ON ld.loan_id = l.id
LEFT JOIN public.implement i
  ON i.id = ld.implement_id
LEFT JOIN public."user" u
  ON u.id = l.requester_id
LEFT JOIN public.room r
  ON r.id = l.room_id
LEFT JOIN public.subject s
  ON s.id = l.subject_id
LEFT JOIN public.v_loan_state_dates d
  ON d.loan_id = l.id
GROUP BY
    l.id, l.uuid, l.status, l.scheduled_at, l.expected_return_at, l.created_at,
    l.requester_id, u.name, u.email, l.room_id, r.name, l.subject_id, s.code, s.name,
    d.approved_at, d.prepared_at, d.delivered_at, d.completed_at;

DO $$
DECLARE
    v_system_user_id BIGINT;
    v_pending_loan RECORD;
BEGIN
    SELECT id
      INTO v_system_user_id
      FROM public."user"
     WHERE uuid = '99999999-9999-9999-9999-999999999999'::UUID
     LIMIT 1;

    IF v_system_user_id IS NULL THEN
        RAISE NOTICE 'No system user found for pending-loan migration; skipping pending migration.';
        RETURN;
    END IF;

    FOR v_pending_loan IN
        SELECT l.id
          FROM public.loan l
         WHERE l.status = 'pending'::public.loan_status_enum
         ORDER BY l.created_at ASC, l.id ASC
    LOOP
        BEGIN
            PERFORM *
              FROM public.fn_approve_loan(
                    v_pending_loan.id,
                    v_system_user_id,
                    'Migracion automatica al flujo reservado vigente'
              );
        EXCEPTION WHEN OTHERS THEN
            PERFORM *
              FROM public.fn_cancel_loan(
                    v_pending_loan.id,
                    v_system_user_id,
                    'Cancelado automaticamente durante migracion al flujo reservado por falta de disponibilidad'
              );
        END;
    END LOOP;
END;
$$;
