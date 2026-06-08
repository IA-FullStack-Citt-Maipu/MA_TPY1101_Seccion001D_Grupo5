DROP FUNCTION IF EXISTS public.fn_auth_find_user_by_rut(TEXT);

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
    WHERE u.rut = CASE
                      WHEN length(replace(replace(replace(COALESCE(p_rut, ''), '.', ''), '-', ''), ' ', '')) > 1
                      THEN substring(
                          replace(replace(replace(COALESCE(p_rut, ''), '.', ''), '-', ''), ' ', ''),
                          1,
                          length(replace(replace(replace(COALESCE(p_rut, ''), '.', ''), '-', ''), ' ', '')) - 1
                      )
                      ELSE ''
                  END
      AND u.active IS TRUE
    LIMIT 1
$$;

REVOKE ALL ON FUNCTION public.fn_auth_find_user_by_rut(TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.fn_auth_find_user_by_rut(TEXT) TO PUBLIC;
