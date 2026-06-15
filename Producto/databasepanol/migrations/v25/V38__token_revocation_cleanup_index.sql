CREATE INDEX IF NOT EXISTS idx_token_revocation_expires_at
ON public.token_revocation (expires_at);
