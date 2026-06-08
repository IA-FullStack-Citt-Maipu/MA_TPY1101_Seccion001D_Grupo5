-- Generated from snapshot/public-schema-full.sql on 2026-06-07.
-- Source: Supabase project panol-dev, schema public.

-- Name: outbox_events; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."outbox_events" AS
 SELECT "event_id",
    "aggregate_type",
    "aggregate_id",
    "event_type",
    "payload",
    "occurred_at",
    "processed_at",
    "retry_count",
    "status"
   FROM "public"."outbox_event";


--

-- Name: v_active_loan_details; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_active_loan_details" AS
 SELECT "l"."id" AS "loan_id",
    "l"."uuid" AS "loan_uuid",
    "l"."status",
    "l"."scheduled_at",
    "l"."expected_return_at",
    "l"."requester_id",
    "u"."name" AS "requester_name",
    "ld"."implement_id",
    "i"."name" AS "implement_name",
    "i"."item_type",
    "ld"."requested_quantity",
    "ld"."reserved_quantity",
    "ld"."delivered_quantity"
   FROM ((("public"."loan" "l"
     JOIN "public"."loan_detail" "ld" ON (("ld"."loan_id" = "l"."id")))
     JOIN "public"."implement" "i" ON (("i"."id" = "ld"."implement_id")))
     LEFT JOIN "public"."user" "u" ON (("u"."id" = "l"."requester_id")))
  WHERE ("l"."status" = ANY (ARRAY['approved'::"public"."loan_status_enum", 'prepared'::"public"."loan_status_enum", 'delivered'::"public"."loan_status_enum", 'overdue'::"public"."loan_status_enum"]));


--

-- Name: v_individual_status_summary; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_individual_status_summary" AS
 SELECT "i"."id" AS "implement_id",
    "i"."name" AS "implement_name",
    ("count"("ind"."id"))::integer AS "total_individuals",
    ("count"("ind"."id") FILTER (WHERE ("ind"."status" = 'available'::"public"."individual_status_enum")))::integer AS "available_count",
    ("count"("ind"."id") FILTER (WHERE ("ind"."status" = 'loaned'::"public"."individual_status_enum")))::integer AS "loaned_count",
    ("count"("ind"."id") FILTER (WHERE ("ind"."status" = 'maintenance'::"public"."individual_status_enum")))::integer AS "maintenance_count",
    ("count"("ind"."id") FILTER (WHERE ("ind"."status" = 'damaged'::"public"."individual_status_enum")))::integer AS "damaged_count",
    ("count"("ind"."id") FILTER (WHERE ("ind"."status" = 'blocked'::"public"."individual_status_enum")))::integer AS "blocked_count",
    ("count"("ind"."id") FILTER (WHERE ("ind"."status" = 'retired'::"public"."individual_status_enum")))::integer AS "retired_count"
   FROM ("public"."implement" "i"
     LEFT JOIN "public"."individual" "ind" ON ((("ind"."implement_id" = "i"."id") AND ("ind"."active" IS TRUE))))
  GROUP BY "i"."id", "i"."name";


--

-- Name: v_loan_state_dates; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_loan_state_dates" AS
 SELECT "l"."id" AS "loan_id",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'approved'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "approved_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'prepared'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "prepared_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'delivered'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "delivered_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'completed'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "completed_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'rejected'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "rejected_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'cancelled'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "cancelled_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'expired'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "expired_at",
    "max"(
        CASE
            WHEN ("h"."to_status" = 'overdue'::"public"."loan_status_enum") THEN "h"."changed_at"
            ELSE NULL::timestamp with time zone
        END) AS "overdue_at"
   FROM ("public"."loan" "l"
     LEFT JOIN "public"."loan_status_history" "h" ON (("h"."loan_id" = "l"."id")))
  GROUP BY "l"."id";


--

-- Name: v_loan_requests_calendar; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_loan_requests_calendar" AS
 SELECT "l"."id" AS "loan_id",
    "l"."uuid" AS "loan_uuid",
    "l"."status",
        CASE "l"."status"
            WHEN 'pending'::"public"."loan_status_enum" THEN 'Pendiente'::"text"
            WHEN 'approved'::"public"."loan_status_enum" THEN 'Aprobado'::"text"
            WHEN 'prepared'::"public"."loan_status_enum" THEN 'Preparado'::"text"
            WHEN 'delivered'::"public"."loan_status_enum" THEN 'Entregado'::"text"
            WHEN 'completed'::"public"."loan_status_enum" THEN 'Completado'::"text"
            WHEN 'rejected'::"public"."loan_status_enum" THEN 'Rechazado'::"text"
            WHEN 'cancelled'::"public"."loan_status_enum" THEN 'Cancelado'::"text"
            WHEN 'expired'::"public"."loan_status_enum" THEN 'Expirado'::"text"
            WHEN 'overdue'::"public"."loan_status_enum" THEN 'Atrasado'::"text"
            ELSE ("l"."status")::"text"
        END AS "status_label",
    "l"."scheduled_at",
    "l"."expected_return_at",
    "l"."created_at",
    "l"."requester_id",
    "u"."name" AS "requester_name",
    "u"."email" AS "requester_email",
    "l"."room_id",
    "r"."name" AS "room_name",
    "l"."subject_id",
    "s"."code" AS "subject_code",
    "s"."name" AS "subject_name",
    "ld"."implement_id",
    "i"."name" AS "implement_name",
    "i"."item_type",
    "ld"."requested_quantity",
    "ld"."reserved_quantity",
    "ld"."delivered_quantity",
    "d"."approved_at",
    "d"."prepared_at",
    "d"."delivered_at",
    "d"."completed_at"
   FROM (((((("public"."loan" "l"
     JOIN "public"."loan_detail" "ld" ON (("ld"."loan_id" = "l"."id")))
     JOIN "public"."implement" "i" ON (("i"."id" = "ld"."implement_id")))
     LEFT JOIN "public"."user" "u" ON (("u"."id" = "l"."requester_id")))
     LEFT JOIN "public"."room" "r" ON (("r"."id" = "l"."room_id")))
     LEFT JOIN "public"."subject" "s" ON (("s"."id" = "l"."subject_id")))
     LEFT JOIN "public"."v_loan_state_dates" "d" ON (("d"."loan_id" = "l"."id")));


--

-- Name: v_loan_requests_summary; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_loan_requests_summary" AS
 SELECT "l"."id" AS "loan_id",
    "l"."uuid" AS "loan_uuid",
    "l"."status",
        CASE "l"."status"
            WHEN 'pending'::"public"."loan_status_enum" THEN 'Pendiente'::"text"
            WHEN 'approved'::"public"."loan_status_enum" THEN 'Aprobado'::"text"
            WHEN 'prepared'::"public"."loan_status_enum" THEN 'Preparado'::"text"
            WHEN 'delivered'::"public"."loan_status_enum" THEN 'Entregado'::"text"
            WHEN 'completed'::"public"."loan_status_enum" THEN 'Completado'::"text"
            WHEN 'rejected'::"public"."loan_status_enum" THEN 'Rechazado'::"text"
            WHEN 'cancelled'::"public"."loan_status_enum" THEN 'Cancelado'::"text"
            WHEN 'expired'::"public"."loan_status_enum" THEN 'Expirado'::"text"
            WHEN 'overdue'::"public"."loan_status_enum" THEN 'Atrasado'::"text"
            ELSE ("l"."status")::"text"
        END AS "status_label",
    "l"."scheduled_at",
    "l"."expected_return_at",
    "l"."created_at",
    "l"."requester_id",
    "u"."name" AS "requester_name",
    "u"."email" AS "requester_email",
    "l"."room_id",
    "r"."name" AS "room_name",
    "l"."subject_id",
    "s"."code" AS "subject_code",
    "s"."name" AS "subject_name",
    (COALESCE("count"("ld"."implement_id"), (0)::bigint))::integer AS "total_implement_types",
    (COALESCE("sum"("ld"."requested_quantity"), (0)::bigint))::integer AS "total_requested_quantity",
    (COALESCE("sum"("ld"."reserved_quantity"), (0)::bigint))::integer AS "total_reserved_quantity",
    (COALESCE("sum"("ld"."delivered_quantity"), (0)::bigint))::integer AS "total_delivered_quantity",
    "d"."approved_at",
    "d"."prepared_at",
    "d"."delivered_at",
    "d"."completed_at",
    COALESCE("jsonb_agg"("jsonb_build_object"('implement_id', "i"."id", 'implement_name', "i"."name", 'item_type', "i"."item_type", 'requested_quantity', "ld"."requested_quantity", 'reserved_quantity', "ld"."reserved_quantity", 'delivered_quantity', "ld"."delivered_quantity") ORDER BY "i"."name") FILTER (WHERE ("ld"."implement_id" IS NOT NULL)), '[]'::"jsonb") AS "details"
   FROM (((((("public"."loan" "l"
     LEFT JOIN "public"."loan_detail" "ld" ON (("ld"."loan_id" = "l"."id")))
     LEFT JOIN "public"."implement" "i" ON (("i"."id" = "ld"."implement_id")))
     LEFT JOIN "public"."user" "u" ON (("u"."id" = "l"."requester_id")))
     LEFT JOIN "public"."room" "r" ON (("r"."id" = "l"."room_id")))
     LEFT JOIN "public"."subject" "s" ON (("s"."id" = "l"."subject_id")))
     LEFT JOIN "public"."v_loan_state_dates" "d" ON (("d"."loan_id" = "l"."id")))
  GROUP BY "l"."id", "l"."uuid", "l"."status", "l"."scheduled_at", "l"."expected_return_at", "l"."created_at", "l"."requester_id", "u"."name", "u"."email", "l"."room_id", "r"."name", "l"."subject_id", "s"."code", "s"."name", "d"."approved_at", "d"."prepared_at", "d"."delivered_at", "d"."completed_at";


--

-- Name: v_loan_status_timeline; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_loan_status_timeline" AS
 SELECT "h"."id" AS "history_id",
    "h"."loan_id",
    "l"."uuid" AS "loan_uuid",
    "h"."from_status",
    "h"."to_status",
    "h"."actor_user_id",
    "u"."name" AS "actor_name",
    "u"."email" AS "actor_email",
    "h"."notes",
    "h"."changed_at"
   FROM (("public"."loan_status_history" "h"
     JOIN "public"."loan" "l" ON (("l"."id" = "h"."loan_id")))
     LEFT JOIN "public"."user" "u" ON (("u"."id" = "h"."actor_user_id")));


--

-- Name: v_low_stock; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW "public"."v_low_stock" AS
 SELECT "i"."id" AS "implement_id",
    "i"."uuid" AS "implement_uuid",
    "i"."name" AS "implement_name",
    "i"."category_id",
    "c"."name" AS "category_name",
    "i"."location_id",
    "loc"."name" AS "location_name",
    "i"."item_type",
    "s"."total_stock",
    "s"."min_stock",
    "s"."available",
    "s"."reserved",
    "s"."loaned",
    "s"."damaged",
    ("s"."min_stock" - "s"."available") AS "stock_gap",
        CASE
            WHEN ("s"."available" = 0) THEN 'out_of_stock'::"text"
            WHEN ("s"."available" < "s"."min_stock") THEN 'low_stock'::"text"
            ELSE 'ok'::"text"
        END AS "stock_status",
    "s"."updated_at"
   FROM ((("public"."stock" "s"
     JOIN "public"."implement" "i" ON (("i"."id" = "s"."implement_id")))
     LEFT JOIN "public"."category" "c" ON (("c"."id" = "i"."category_id")))
     LEFT JOIN "public"."location" "loc" ON (("loc"."id" = "i"."location_id")));


--

