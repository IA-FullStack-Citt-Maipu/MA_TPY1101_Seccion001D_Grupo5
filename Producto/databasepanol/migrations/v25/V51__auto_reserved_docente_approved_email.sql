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
    v_requester_id BIGINT;
    v_requester_name CHARACTER VARYING;
    v_requester_email CHARACTER VARYING;
    v_title TEXT := 'Nueva solicitud reservada';
    v_message TEXT;
    v_docente_title TEXT := 'Reserva confirmada';
    v_docente_message TEXT := 'Tu solicitud fue aprobada y comenzaremos la preparacion de los implementos.';
    v_created_at TIMESTAMPTZ := now();
BEGIN
    SELECT u.id, u.name, u.email
      INTO v_requester_id, v_requester_name, v_requester_email
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

    IF v_requester_id IS NOT NULL THEN
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
            v_docente_title,
            v_docente_message,
            false,
            v_created_at,
            'loan',
            p_loan_uuid,
            jsonb_build_object(
                'event_type', 'loan.status_changed',
                'to_status', 'approved',
                'flow_mode', 'auto_reserved'
            )
        );
    END IF;

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
                v_docente_title,
                v_docente_message,
                p_loan_uuid,
                v_created_at
            ),
            'PENDING'::public.outbox_status_enum,
            0,
            v_created_at
        );
    END IF;
END;
$$;
