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
    v_requester_role TEXT;
    v_docente_title TEXT := 'Solicitud enviada';
    v_docente_message TEXT := format('Tu solicitud %s fue registrada y quedo pendiente de revision.', p_loan_uuid);
    v_coordinator_title TEXT := 'Nueva solicitud de préstamo';
    v_coordinator_message TEXT;
    v_created_at TIMESTAMPTZ := now();
BEGIN
    SELECT u.id, u.name, u.email, r.name
      INTO v_requester_id, v_requester_name, v_requester_email, v_requester_role
      FROM public."user" u
      JOIN public.role r
        ON r.id = u.role_id
     WHERE u.uuid = p_requester_user_uuid
       AND u.active IS TRUE
     LIMIT 1;

    IF v_requester_id IS NULL OR v_requester_name IS NULL THEN
        RAISE EXCEPTION 'No se pudo resolver el docente solicitante para la notificacion de prestamo';
    END IF;

    v_coordinator_message := 'El docente ' || v_requester_name || ' ha enviado una nueva solicitud';

    IF v_requester_role = 'docente' THEN
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
                'event_type', 'loan.request_registered',
                'to_status', 'pending'
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
                'loan.request_registered.docente',
                public.fn_build_loan_email_template_data(
                    'loan.request_registered',
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
    END IF;

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
           v_coordinator_title,
           v_coordinator_message,
           false,
           v_created_at,
           'loan',
           p_loan_uuid,
           jsonb_build_object('event_type', 'loan.request_submitted')
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
           'loan.request_submitted.coordinador',
           public.fn_build_loan_email_template_data(
               'loan.request_submitted',
               v_coordinator_title,
               v_coordinator_message,
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
