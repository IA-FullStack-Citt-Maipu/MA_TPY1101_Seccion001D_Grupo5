-- Generated from snapshot/public-schema-full.sql on 2026-06-07.
-- Source: Supabase project panol-dev, schema public.

-- Name: audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."audit_log_id_seq" OWNED BY "public"."audit_log"."id";


--

-- Name: career_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."career_id_seq" OWNED BY "public"."career"."id";


--

-- Name: category_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."category_id_seq" OWNED BY "public"."category"."id";


--

-- Name: email_outbox_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."email_outbox_id_seq" OWNED BY "public"."email_outbox"."id";


--

-- Name: implement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."implement_id_seq" OWNED BY "public"."implement"."id";


--

-- Name: individual_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."individual_id_seq" OWNED BY "public"."individual"."id";


--

-- Name: inventory_movement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."inventory_movement_id_seq" OWNED BY "public"."inventory_movement"."id";


--

-- Name: loan_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."loan_id_seq" OWNED BY "public"."loan"."id";


--

-- Name: loan_status_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."loan_status_history_id_seq" OWNED BY "public"."loan_status_history"."id";


--

-- Name: location_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."location_id_seq" OWNED BY "public"."location"."id";


--

-- Name: notification_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."notification_id_seq" OWNED BY "public"."notification"."id";


--

-- Name: outbox_event_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."outbox_event_id_seq" OWNED BY "public"."outbox_event"."id";


--

-- Name: role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."role_id_seq" OWNED BY "public"."role"."id";


--

-- Name: room_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."room_id_seq" OWNED BY "public"."room"."id";


--

-- Name: stock_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."stock_id_seq" OWNED BY "public"."stock"."id";


--

-- Name: subject_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."subject_id_seq" OWNED BY "public"."subject"."id";


--

-- Name: token_revocation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."token_revocation_id_seq" OWNED BY "public"."token_revocation"."id";


--

-- Name: user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."user_id_seq" OWNED BY "public"."user"."id";


--

-- Name: user_session_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE "public"."user_session_id_seq" OWNED BY "public"."user_session"."id";


--

