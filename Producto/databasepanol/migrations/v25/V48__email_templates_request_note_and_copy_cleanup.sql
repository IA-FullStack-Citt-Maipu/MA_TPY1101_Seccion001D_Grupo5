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
    COALESCE(COUNT(ld.implement_id), 0::BIGINT)::INTEGER AS total_implement_types,
    COALESCE(SUM(ld.requested_quantity), 0::BIGINT)::INTEGER AS total_requested_quantity,
    COALESCE(SUM(ld.reserved_quantity), 0::BIGINT)::INTEGER AS total_reserved_quantity,
    COALESCE(SUM(ld.delivered_quantity), 0::BIGINT)::INTEGER AS total_delivered_quantity,
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
        '[]'::JSONB
    ) AS details,
    COALESCE(SUM(ld.returned_quantity), 0::BIGINT)::INTEGER AS total_returned_quantity,
    request_note.request_note
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
LEFT JOIN LATERAL (
    SELECT lsh.notes AS request_note
    FROM public.loan_status_history lsh
    WHERE lsh.loan_id = l.id
      AND lsh.actor_user_id = l.requester_id
      AND lsh.notes IS NOT NULL
      AND btrim(lsh.notes) <> ''
      AND lsh.notes NOT IN (
          'Reserva automatica al crear solicitud',
          'Solicitud modificada por docente'
      )
      AND (
          (lsh.from_status IS NULL AND lsh.to_status IN (
              'pending'::public.loan_status_enum,
              'approved'::public.loan_status_enum
          ))
          OR
          (lsh.from_status = lsh.to_status AND lsh.to_status IN (
              'pending'::public.loan_status_enum,
              'approved'::public.loan_status_enum
          ))
      )
    ORDER BY lsh.changed_at DESC, lsh.id DESC
    LIMIT 1
) request_note
  ON true
GROUP BY
    l.id,
    l.uuid,
    l.status,
    l.scheduled_at,
    l.expected_return_at,
    l.created_at,
    l.requester_id,
    u.name,
    u.email,
    l.room_id,
    r.name,
    l.subject_id,
    s.code,
    s.name,
    d.approved_at,
    d.prepared_at,
    d.delivered_at,
    d.completed_at,
    request_note.request_note;

CREATE OR REPLACE FUNCTION public.fn_build_loan_email_template_data(
    p_event_type TEXT,
    p_title TEXT,
    p_message TEXT,
    p_loan_uuid UUID,
    p_created_at TIMESTAMP WITH TIME ZONE
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_status TEXT;
    v_status_label TEXT;
    v_requester_name TEXT;
    v_requester_email TEXT;
    v_room_name TEXT;
    v_subject_name TEXT;
    v_scheduled_at TIMESTAMPTZ;
    v_expected_return_at TIMESTAMPTZ;
    v_request_note TEXT;
    v_details JSONB;
BEGIN
    SELECT
        lrs.status::TEXT,
        lrs.status_label,
        lrs.requester_name,
        lrs.requester_email,
        lrs.room_name,
        lrs.subject_name,
        lrs.scheduled_at,
        lrs.expected_return_at,
        lrs.request_note,
        lrs.details
    INTO
        v_status,
        v_status_label,
        v_requester_name,
        v_requester_email,
        v_room_name,
        v_subject_name,
        v_scheduled_at,
        v_expected_return_at,
        v_request_note,
        v_details
    FROM public.v_loan_requests_summary lrs
    WHERE lrs.loan_uuid = p_loan_uuid
    LIMIT 1;

    RETURN jsonb_strip_nulls(
        jsonb_build_object(
            'event_type', p_event_type,
            'title', p_title,
            'message', p_message,
            'reference_type', 'loan',
            'reference_id', p_loan_uuid,
            'created_at', p_created_at,
            'loan_uuid', p_loan_uuid,
            'status', COALESCE(v_status, 'pending'),
            'status_label', COALESCE(v_status_label, 'Pendiente'),
            'requester_name', v_requester_name,
            'requester_email', v_requester_email,
            'room_name', v_room_name,
            'subject_name', v_subject_name,
            'scheduled_at', v_scheduled_at,
            'expected_return_at', v_expected_return_at,
            'request_note', v_request_note,
            'details', COALESCE(v_details, '[]'::JSONB)
        )
    );
END;
$$;

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

    v_message := 'El docente ' || v_requester_name || ' actualizo una solicitud reservada y requiere nueva revision.';

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
        WHEN 'prepared'  THEN 'Tu solicitud fue preparada y ya puede ser entregada.'
        WHEN 'delivered' THEN 'Tu prestamo ya se encuentra en uso.'
        WHEN 'completed' THEN 'Tu prestamo fue finalizado correctamente.'
        WHEN 'rejected'  THEN 'Tu solicitud fue rechazada. Revisa el detalle para conocer la observacion registrada.'
        WHEN 'cancelled' THEN 'Tu solicitud fue cancelada.'
        WHEN 'expired'   THEN 'Tu solicitud expiro por tiempo y ya no puede continuar.'
        WHEN 'overdue'   THEN 'Tu prestamo supero la fecha estimada de devolucion.'
        ELSE 'Tu prestamo recibio una actualizacion. Revisa el detalle para continuar.'
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
