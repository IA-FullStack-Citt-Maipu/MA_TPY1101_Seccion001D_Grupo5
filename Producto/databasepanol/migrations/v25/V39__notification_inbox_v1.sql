-- =============================================================================
-- V39 - Notification inbox V1
-- =============================================================================

ALTER TABLE public.notification
    ADD COLUMN IF NOT EXISTS reference_type TEXT,
    ADD COLUMN IF NOT EXISTS reference_id UUID,
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE public.notification
    DROP CONSTRAINT IF EXISTS chk_notification_reference_type;

ALTER TABLE public.notification
    ADD CONSTRAINT chk_notification_reference_type
        CHECK (
            reference_type IS NULL
            OR reference_type IN ('loan', 'implement')
        );

DROP POLICY IF EXISTS notification_all_director ON public.notification;

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
    v_reference_type TEXT;
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

    v_reference_type := CASE
        WHEN p_loan_uuid IS NULL THEN NULL
        ELSE 'loan'
    END;

    INSERT INTO public.notification (
        user_id,
        title,
        message,
        reference_type,
        reference_id,
        metadata
    )
    SELECT u.id,
           'Nueva solicitud de prestamo',
           'El docente ' || v_requester_name || ' ha enviado una nueva solicitud',
           v_reference_type,
           p_loan_uuid,
           jsonb_build_object('event_type', 'loan.request_submitted')
      FROM public."user" u
      JOIN public.role r ON r.id = u.role_id
     WHERE r.name = 'coordinador'
       AND u.active IS TRUE
       AND u.uuid <> '99999999-9999-9999-9999-999999999999'::UUID;
END;
$$;

DROP FUNCTION IF EXISTS public.fn_notify_new_loan_request(UUID);

REVOKE ALL ON FUNCTION public.fn_notify_new_loan_request(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.fn_notify_new_loan_request(UUID, UUID) TO PUBLIC;

CREATE OR REPLACE FUNCTION public.fn_trg_create_notification_on_loan_status()
RETURNS trigger
LANGUAGE plpgsql
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

    RETURN NEW;
END;
$$;
