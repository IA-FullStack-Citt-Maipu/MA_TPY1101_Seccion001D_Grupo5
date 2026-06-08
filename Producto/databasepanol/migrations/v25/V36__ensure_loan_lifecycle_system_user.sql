DO $$
DECLARE
    v_role_id BIGINT;
BEGIN
    SELECT r.id
      INTO v_role_id
      FROM public.role r
     WHERE lower(r.name) = 'coordinador'
     LIMIT 1;

    IF v_role_id IS NULL THEN
        RAISE EXCEPTION 'No se encontro el rol coordinador para registrar el usuario tecnico del ciclo de prestamos';
    END IF;

    INSERT INTO public."user" (uuid, role_id, name, rut, email, password_hash, active)
    VALUES (
        '99999999-9999-9999-9999-999999999999'::UUID,
        v_role_id,
        'SISTEMA_OUTBOX',
        '99.999.999-9',
        'sistema.outbox@duocuc.cl',
        '$2a$10$falsa_pero_valida_para_not_null',
        true
    )
    ON CONFLICT (uuid) DO UPDATE
    SET role_id = EXCLUDED.role_id,
        name = EXCLUDED.name,
        rut = EXCLUDED.rut,
        email = EXCLUDED.email,
        password_hash = EXCLUDED.password_hash,
        active = true;
END
$$;
