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

    v_message := 'El docente ' || v_requester_name || ' modifico una solicitud de prestamo';

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
     WHERE r.name = 'coordinador'
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
     WHERE r.name = 'coordinador'
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
        WHEN 'delivered' THEN 'Prestamo entregado'
        WHEN 'completed' THEN 'Prestamo completado'
        WHEN 'rejected'  THEN 'Solicitud rechazada'
        WHEN 'cancelled' THEN 'Solicitud cancelada'
        WHEN 'expired'   THEN 'Solicitud vencida'
        WHEN 'overdue'   THEN 'Prestamo atrasado'
        ELSE 'Actualizacion de prestamo'
    END;

    v_message := CASE NEW.to_status
        WHEN 'prepared'  THEN format('Tu solicitud %s esta preparada para entrega.', v_loan_uuid)
        WHEN 'delivered' THEN format('Tu prestamo %s fue entregado.', v_loan_uuid)
        WHEN 'completed' THEN format('Tu prestamo %s fue cerrado.', v_loan_uuid)
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
        WHEN 'delivered' THEN 'Entregado'
        WHEN 'completed' THEN 'Completado'
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
        WHEN 'delivered' THEN 'Entregado'
        WHEN 'completed' THEN 'Completado'
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
