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
       OR NEW.from_status = NEW.to_status THEN
        RETURN NEW;
    END IF;

    v_title := CASE NEW.to_status
        WHEN 'approved'  THEN 'Reserva confirmada'
        WHEN 'prepared'  THEN 'Implementos listos para retiro'
        WHEN 'delivered' THEN 'Prestamo en uso'
        WHEN 'completed' THEN 'Prestamo finalizado'
        WHEN 'rejected'  THEN 'Solicitud rechazada'
        WHEN 'cancelled' THEN 'Solicitud cancelada'
        WHEN 'expired'   THEN 'Solicitud vencida'
        WHEN 'overdue'   THEN 'Prestamo atrasado'
        ELSE 'Actualizacion de prestamo'
    END;

    v_message := CASE NEW.to_status
        WHEN 'approved'  THEN 'Tu solicitud fue aprobada y comenzaremos la preparacion de los implementos.'
        WHEN 'prepared'  THEN 'Tus implementos ya estan listos para retiro.'
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
