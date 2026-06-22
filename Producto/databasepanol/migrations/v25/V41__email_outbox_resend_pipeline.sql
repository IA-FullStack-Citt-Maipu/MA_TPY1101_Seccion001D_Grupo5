ALTER TABLE public.email_outbox
    ADD COLUMN IF NOT EXISTS provider_message_id TEXT;

CREATE INDEX IF NOT EXISTS idx_email_outbox_status_occurred_at
    ON public.email_outbox (status, occurred_at);

CREATE INDEX IF NOT EXISTS idx_email_outbox_status_processed_at
    ON public.email_outbox (status, processed_at);

CREATE OR REPLACE FUNCTION public.fn_build_loan_email_template_data(
    p_event_type TEXT,
    p_title TEXT,
    p_message TEXT,
    p_loan_uuid UUID,
    p_created_at TIMESTAMPTZ
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
            'details', COALESCE(v_details, '[]'::JSONB)
        )
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.fn_build_stock_email_template_data(
    p_event_type TEXT,
    p_title TEXT,
    p_message TEXT,
    p_implement_uuid UUID,
    p_implement_name TEXT,
    p_available INTEGER,
    p_min_stock INTEGER,
    p_stock_status TEXT,
    p_created_at TIMESTAMPTZ
)
RETURNS JSONB
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
    RETURN jsonb_strip_nulls(
        jsonb_build_object(
            'event_type', p_event_type,
            'title', p_title,
            'message', p_message,
            'reference_type', 'implement',
            'reference_id', p_implement_uuid,
            'created_at', p_created_at,
            'implement_uuid', p_implement_uuid,
            'implement_name', p_implement_name,
            'available', p_available,
            'min_stock', p_min_stock,
            'stock_status', p_stock_status
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
    v_requester_id BIGINT;
    v_requester_name CHARACTER VARYING;
    v_requester_email CHARACTER VARYING;
    v_requester_role TEXT;
    v_docente_title TEXT := 'Solicitud enviada';
    v_docente_message TEXT := format('Tu solicitud %s fue registrada y quedo pendiente de revision.', p_loan_uuid);
    v_coordinator_title TEXT := 'Nueva solicitud de prestamo';
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
    v_title TEXT := 'Solicitud pendiente actualizada';
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

    v_message := 'El docente ' || v_requester_name || ' modifico una solicitud pendiente';

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
               'loan_status', 'pending'
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
       OR NEW.from_status = NEW.to_status THEN
        RETURN NEW;
    END IF;

    v_title := CASE NEW.to_status
        WHEN 'approved'  THEN 'Solicitud aprobada'
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
        WHEN 'approved'  THEN format('Tu solicitud %s fue aprobada.', v_loan_uuid)
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

CREATE OR REPLACE FUNCTION public.fn_trg_create_notification_on_stock_threshold()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_old_level INTEGER;
    v_new_level INTEGER;
    v_stock_status TEXT;
    v_implement_uuid UUID;
    v_implement_name TEXT;
    v_title TEXT;
    v_message TEXT;
    v_created_at TIMESTAMPTZ := now();
BEGIN
    v_old_level := CASE
        WHEN OLD.available = 0 THEN 2
        WHEN OLD.available < OLD.min_stock THEN 1
        ELSE 0
    END;

    v_new_level := CASE
        WHEN NEW.available = 0 THEN 2
        WHEN NEW.available < NEW.min_stock THEN 1
        ELSE 0
    END;

    IF v_new_level = 0 OR v_new_level <= v_old_level THEN
        RETURN NEW;
    END IF;

    v_stock_status := CASE
        WHEN v_new_level = 2 THEN 'out_of_stock'
        ELSE 'low_stock'
    END;

    SELECT i.uuid, i.name
      INTO v_implement_uuid, v_implement_name
      FROM public.implement i
     WHERE i.id = NEW.implement_id
       AND i.active IS TRUE;

    IF v_implement_uuid IS NULL THEN
        RETURN NEW;
    END IF;

    v_title := CASE v_stock_status
        WHEN 'out_of_stock' THEN 'Stock critico'
        ELSE 'Stock bajo'
    END;

    v_message := CASE v_stock_status
        WHEN 'out_of_stock' THEN format(
            'El implemento %s quedo sin stock disponible.',
            COALESCE(v_implement_name, v_implement_uuid::TEXT)
        )
        ELSE format(
            'El implemento %s quedo bajo el stock minimo.',
            COALESCE(v_implement_name, v_implement_uuid::TEXT)
        )
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
    SELECT u.id,
           v_title,
           v_message,
           false,
           v_created_at,
           'implement',
           v_implement_uuid,
           jsonb_build_object(
               'event_type', 'implement.stock_alert',
               'stock_status', v_stock_status,
               'available', NEW.available,
               'min_stock', NEW.min_stock,
               'stock_gap', GREATEST(NEW.min_stock - NEW.available, 0)
           )
      FROM public."user" u
      JOIN public.role r ON r.id = u.role_id
     WHERE u.active IS TRUE
       AND r.name IN ('coordinador', 'director')
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
           CASE
               WHEN r.name = 'director' THEN 'implement.stock_alert.director'
               ELSE 'implement.stock_alert.coordinador'
           END,
           public.fn_build_stock_email_template_data(
               'implement.stock_alert',
               v_title,
               v_message,
               v_implement_uuid,
               v_implement_name,
               NEW.available,
               NEW.min_stock,
               v_stock_status,
               v_created_at
           ),
           'PENDING'::public.outbox_status_enum,
           0,
           v_created_at
      FROM public."user" u
      JOIN public.role r ON r.id = u.role_id
     WHERE u.active IS TRUE
       AND r.name IN ('coordinador', 'director')
       AND u.uuid <> '99999999-9999-9999-9999-999999999999'::UUID
       AND u.email IS NOT NULL
       AND btrim(u.email) <> '';

    RETURN NEW;
END;
$$;
