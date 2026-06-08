-- Generated from snapshot/public-schema-full.sql on 2026-06-07.
-- Source: Supabase project panol-dev, schema public.

-- Name: audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."audit_log" (
    "id" bigint NOT NULL,
    "event" character varying NOT NULL,
    "payload" "jsonb" NOT NULL,
    "actor_user_id" bigint,
    "target_user_id" bigint,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--

-- Name: career; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."career" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "code" character varying NOT NULL,
    "name" character varying NOT NULL,
    "active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--

-- Name: category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."category" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" character varying NOT NULL,
    "description" "text",
    "active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--

-- Name: email_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."email_outbox" (
    "id" bigint NOT NULL,
    "event_id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "recipient_email" character varying NOT NULL,
    "email_type" character varying NOT NULL,
    "template_data" "jsonb" NOT NULL,
    "status" "public"."outbox_status_enum" DEFAULT 'PENDING'::"public"."outbox_status_enum" NOT NULL,
    "retry_count" integer DEFAULT 0 NOT NULL,
    "error_log" "text",
    "occurred_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "processed_at" timestamp with time zone
);


--

-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."flyway_schema_history" (
    "installed_rank" integer NOT NULL,
    "version" character varying(50),
    "description" character varying(200) NOT NULL,
    "type" character varying(20) NOT NULL,
    "script" character varying(1000) NOT NULL,
    "checksum" integer,
    "installed_by" character varying(100) NOT NULL,
    "installed_on" timestamp without time zone DEFAULT "now"() NOT NULL,
    "execution_time" integer NOT NULL,
    "success" boolean NOT NULL
);


--

-- Name: implement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."implement" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "category_id" bigint,
    "location_id" bigint,
    "name" character varying NOT NULL,
    "description" "text",
    "item_type" "public"."item_type_enum" DEFAULT 'reusable'::"public"."item_type_enum" NOT NULL,
    "barcode" character varying,
    "img_url" "text",
    "active" boolean DEFAULT true NOT NULL,
    "observations" "text",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--

-- Name: individual; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."individual" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "implement_id" bigint NOT NULL,
    "current_location_id" bigint,
    "asset_code" character varying NOT NULL,
    "status" "public"."individual_status_enum" DEFAULT 'available'::"public"."individual_status_enum" NOT NULL,
    "condition" "public"."individual_condition_enum" DEFAULT 'good'::"public"."individual_condition_enum" NOT NULL,
    "notes" "text",
    "active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--

-- Name: inventory_movement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."inventory_movement" (
    "id" bigint NOT NULL,
    "implement_id" bigint NOT NULL,
    "actor_user_id" bigint NOT NULL,
    "movement_type" "public"."inventory_movement_type_enum" NOT NULL,
    "quantity" integer NOT NULL,
    "delta_changes" "jsonb" NOT NULL,
    "systemic_metadata" "jsonb",
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "inventory_movement_qty_nonzero" CHECK (("quantity" <> 0))
);


--

-- Name: loan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."loan" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "requester_id" bigint NOT NULL,
    "room_id" bigint,
    "subject_id" bigint,
    "status" "public"."loan_status_enum" DEFAULT 'pending'::"public"."loan_status_enum" NOT NULL,
    "scheduled_at" timestamp with time zone NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "expected_return_at" timestamp with time zone NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "chk_loan_expected_return_after_scheduled" CHECK (("expected_return_at" > "scheduled_at"))
);


--

-- Name: loan_detail; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."loan_detail" (
    "loan_id" bigint NOT NULL,
    "implement_id" bigint NOT NULL,
    "requested_quantity" integer NOT NULL,
    "reserved_quantity" integer DEFAULT 0 NOT NULL,
    "delivered_quantity" integer DEFAULT 0 NOT NULL,
    "returned_quantity" integer DEFAULT 0 NOT NULL,
    "damaged_quantity" integer DEFAULT 0 NOT NULL,
    "lost_quantity" integer DEFAULT 0 NOT NULL,
    "consumed_quantity" integer DEFAULT 0 NOT NULL,
    "discarded_quantity" integer DEFAULT 0 NOT NULL,
    "return_notes" "text",
    CONSTRAINT "chk_ld_consumed_qty_nonnegative" CHECK (("consumed_quantity" >= 0)),
    CONSTRAINT "chk_ld_damaged_qty_nonnegative" CHECK (("damaged_quantity" >= 0)),
    CONSTRAINT "chk_ld_delivered_lte_reserved" CHECK (("delivered_quantity" <= "reserved_quantity")),
    CONSTRAINT "chk_ld_discarded_qty_nonnegative" CHECK (("discarded_quantity" >= 0)),
    CONSTRAINT "chk_ld_lost_qty_nonnegative" CHECK (("lost_quantity" >= 0)),
    CONSTRAINT "chk_ld_reserved_lte_requested" CHECK (("reserved_quantity" <= "requested_quantity")),
    CONSTRAINT "chk_ld_return_breakdown_lte_delivered" CHECK (((((("returned_quantity" + "damaged_quantity") + "lost_quantity") + "consumed_quantity") + "discarded_quantity") <= "delivered_quantity")),
    CONSTRAINT "chk_ld_returned_qty_nonnegative" CHECK (("returned_quantity" >= 0)),
    CONSTRAINT "loan_detail_delivered_qty_check" CHECK (("delivered_quantity" >= 0)),
    CONSTRAINT "loan_detail_requested_qty_check" CHECK (("requested_quantity" > 0)),
    CONSTRAINT "loan_detail_reserved_qty_check" CHECK (("reserved_quantity" >= 0))
);


--

-- Name: loan_detail_individual; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."loan_detail_individual" (
    "loan_id" bigint NOT NULL,
    "implement_id" bigint NOT NULL,
    "individual_id" bigint NOT NULL,
    "return_condition" "public"."return_condition_enum",
    "returned_at" timestamp with time zone,
    "allocation_status" "public"."individual_allocation_status_enum" DEFAULT 'delivered'::"public"."individual_allocation_status_enum" NOT NULL
);


--

-- Name: loan_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."loan_status_history" (
    "id" bigint NOT NULL,
    "loan_id" bigint NOT NULL,
    "actor_user_id" bigint NOT NULL,
    "from_status" "public"."loan_status_enum",
    "to_status" "public"."loan_status_enum" NOT NULL,
    "notes" "text",
    "changed_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--

-- Name: location; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."location" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" character varying NOT NULL,
    "description" "text",
    "location_type" character varying DEFAULT 'PANOL_SHELF'::character varying NOT NULL,
    "active" boolean DEFAULT true NOT NULL
);


--

-- Name: notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."notification" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" bigint NOT NULL,
    "title" character varying NOT NULL,
    "message" "text" NOT NULL,
    "read_status" boolean DEFAULT false NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--

-- Name: outbox_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."outbox_event" (
    "id" bigint NOT NULL,
    "event_id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "aggregate_type" character varying NOT NULL,
    "aggregate_id" "uuid",
    "event_type" character varying NOT NULL,
    "payload" "text" NOT NULL,
    "status" "public"."outbox_status_enum" DEFAULT 'PENDING'::"public"."outbox_status_enum" NOT NULL,
    "retry_count" integer DEFAULT 0 NOT NULL,
    "occurred_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "processed_at" timestamp with time zone
);


--

-- Name: role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."role" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" character varying NOT NULL,
    "description" character varying
);


--

-- Name: room; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."room" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "name" character varying NOT NULL,
    "description" "text",
    "active" boolean DEFAULT true NOT NULL
);


--

-- Name: stock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."stock" (
    "id" bigint NOT NULL,
    "implement_id" bigint NOT NULL,
    "total_stock" integer DEFAULT 0 NOT NULL,
    "min_stock" integer DEFAULT 0 NOT NULL,
    "available" integer DEFAULT 0 NOT NULL,
    "reserved" integer DEFAULT 0 NOT NULL,
    "loaned" integer DEFAULT 0 NOT NULL,
    "damaged" integer DEFAULT 0 NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    CONSTRAINT "chk_stock_counters_integrity" CHECK ((((("available" + "reserved") + "loaned") + "damaged") = "total_stock")),
    CONSTRAINT "stock_available_check" CHECK (("available" >= 0)),
    CONSTRAINT "stock_damaged_check" CHECK (("damaged" >= 0)),
    CONSTRAINT "stock_loaned_check" CHECK (("loaned" >= 0)),
    CONSTRAINT "stock_min_stock_check" CHECK (("min_stock" >= 0)),
    CONSTRAINT "stock_reserved_check" CHECK (("reserved" >= 0)),
    CONSTRAINT "stock_total_stock_check" CHECK (("total_stock" >= 0))
);


--

-- Name: subject; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."subject" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "code" character varying NOT NULL,
    "name" character varying NOT NULL,
    "active" boolean DEFAULT true NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--

-- Name: token_revocation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."token_revocation" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "jti" character varying NOT NULL,
    "expires_at" timestamp with time zone NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--

-- Name: user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."user" (
    "id" bigint NOT NULL,
    "uuid" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "role_id" bigint NOT NULL,
    "career_id" bigint,
    "name" character varying NOT NULL,
    "rut" character varying NOT NULL,
    "email" character varying NOT NULL,
    "password_hash" character varying NOT NULL,
    "auth_uuid" "uuid",
    "active" boolean DEFAULT true NOT NULL,
    "failed_login_attempts" integer DEFAULT 0 NOT NULL,
    "blocked_until" timestamp with time zone,
    "last_login_at" timestamp with time zone,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL,
    "updated_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--

-- Name: user_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE "public"."user_session" (
    "id" bigint NOT NULL,
    "user_id" bigint NOT NULL,
    "refresh_token_hash" character varying NOT NULL,
    "device_info" character varying,
    "expires_at" timestamp with time zone NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);


--

