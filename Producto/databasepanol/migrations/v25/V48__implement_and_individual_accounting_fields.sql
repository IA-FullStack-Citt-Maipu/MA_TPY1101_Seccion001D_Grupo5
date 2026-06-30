ALTER TABLE public.implement
    ADD COLUMN cost_center CHARACTER VARYING(100),
    ADD COLUMN net_value NUMERIC(14, 2);

ALTER TABLE public.individual
    ADD COLUMN remaining_life INTEGER,
    ADD COLUMN asset_code_reprint_required BOOLEAN NOT NULL DEFAULT false;
