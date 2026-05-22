CREATE OR REPLACE FUNCTION public.fn_notify_new_loan_request(p_requester_user_uuid UUID)
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

REVOKE ALL ON FUNCTION public.fn_notify_new_loan_request(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.fn_notify_new_loan_request(UUID) TO PUBLIC;
