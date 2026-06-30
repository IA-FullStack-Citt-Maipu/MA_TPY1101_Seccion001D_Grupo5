-- =============================================================================
-- V40 - Notification routing by role and stock alert recipients
-- =============================================================================

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
    v_requester_role TEXT;
BEGIN
    SELECT u.id, u.name, r.name
      INTO v_requester_id, v_requester_name, v_requester_role
      FROM public."user" u
      JOIN public.role r
        ON r.id = u.role_id
     WHERE u.uuid = p_requester_user_uuid
       AND u.active IS TRUE
     LIMIT 1;

    IF v_requester_id IS NULL OR v_requester_name IS NULL THEN
        RAISE EXCEPTION 'No se pudo resolver el docente solicitante para la notificacion de prestamo';
    END IF;

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
            'Solicitud enviada',
            format('Tu solicitud %s fue registrada y quedo pendiente de revision.', p_loan_uuid),
            false,
            now(),
            'loan',
            p_loan_uuid,
            jsonb_build_object(
                'event_type', 'loan.request_registered',
                'to_status', 'pending'
            )
        );
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
           'Nueva solicitud de prestamo',
           'El docente ' || v_requester_name || ' ha enviado una nueva solicitud',
           false,
           now(),
           'loan',
           p_loan_uuid,
           jsonb_build_object('event_type', 'loan.request_submitted')
      FROM public."user" u
      JOIN public.role r ON r.id = u.role_id
     WHERE r.name = 'coordinador'
       AND u.active IS TRUE
       AND u.uuid <> '99999999-9999-9999-9999-999999999999'::UUID;
END;
$$;

REVOKE ALL ON FUNCTION public.fn_notify_new_loan_request(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.fn_notify_new_loan_request(UUID, UUID) TO PUBLIC;

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
           'Solicitud pendiente actualizada',
           'El docente ' || v_requester_name || ' modifico una solicitud pendiente',
           false,
           now(),
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
END;
$$;

REVOKE ALL ON FUNCTION public.fn_notify_pending_loan_updated(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.fn_notify_pending_loan_updated(UUID, UUID) TO PUBLIC;

CREATE OR REPLACE FUNCTION public.fn_trg_create_notification_on_loan_status()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_requester_id BIGINT;
    v_loan_uuid UUID;
    v_requester_role TEXT;
    v_title TEXT;
    v_message TEXT;
BEGIN
    SELECT l.requester_id, l.uuid, r.name
      INTO v_requester_id, v_loan_uuid, v_requester_role
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
           now(),
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

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_create_notification_on_stock_threshold ON public.stock;
CREATE TRIGGER trg_create_notification_on_stock_threshold
    AFTER UPDATE OF available, min_stock ON public.stock
    FOR EACH ROW
    EXECUTE FUNCTION public.fn_trg_create_notification_on_stock_threshold();
