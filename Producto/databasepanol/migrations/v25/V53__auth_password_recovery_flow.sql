CREATE TABLE public.password_reset_request (
    id               BIGSERIAL,
    user_id          BIGINT            NOT NULL,
    code_hash        CHARACTER VARYING NOT NULL,
    reset_token_hash CHARACTER VARYING,
    expires_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at      TIMESTAMP WITH TIME ZONE,
    consumed_at      TIMESTAMP WITH TIME ZONE,
    last_sent_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    attempt_count    INTEGER           NOT NULL DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT password_reset_request_pkey PRIMARY KEY (id),
    CONSTRAINT password_reset_request_attempt_count_check CHECK (attempt_count >= 0),
    CONSTRAINT fk_password_reset_request_user FOREIGN KEY (user_id) REFERENCES public."user"(id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_request_user_created_at
    ON public.password_reset_request (user_id, created_at DESC);

CREATE INDEX idx_password_reset_request_expires_at
    ON public.password_reset_request (expires_at);
