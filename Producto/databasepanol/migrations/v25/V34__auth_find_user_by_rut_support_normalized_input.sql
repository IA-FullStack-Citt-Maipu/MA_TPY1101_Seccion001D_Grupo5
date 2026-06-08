CREATE OR REPLACE FUNCTION public.fn_auth_find_user_by_rut(p_rut TEXT)
RETURNS TABLE (
    user_uuid UUID,
    rut CHARACTER VARYING,
    user_name CHARACTER VARYING,
    email CHARACTER VARYING,
    password_hash CHARACTER VARYING,
    role_name CHARACTER VARYING,
    failed_login_attempts INTEGER,
    blocked_until TIMESTAMP WITH TIME ZONE
)
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public, pg_temp
SET row_security = off
AS $$
    WITH normalized_input AS (
        SELECT replace(replace(replace(COALESCE(p_rut, ''), '.', ''), '-', ''), ' ', '') AS compact_rut
    )
    SELECT
        u.uuid,
        u.rut,
        u.name,
        u.email,
        u.password_hash,
        r.name,
        u.failed_login_attempts,
        u.blocked_until
    FROM public."user" u
    JOIN public.role r ON r.id = u.role_id
    CROSS JOIN normalized_input i
    WHERE (
        u.rut = i.compact_rut
        OR (
            length(i.compact_rut) > 1
            AND u.rut = substring(i.compact_rut, 1, length(i.compact_rut) - 1)
        )
    )
      AND u.active IS TRUE
    LIMIT 1
$$;
