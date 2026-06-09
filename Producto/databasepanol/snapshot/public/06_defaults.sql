-- Generated from snapshot/public-schema-full.sql on 2026-06-07.
-- Source: Supabase project panol-dev, schema public.

-- Name: audit_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."audit_log" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."audit_log_id_seq"'::"regclass");


--

-- Name: career id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."career" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."career_id_seq"'::"regclass");


--

-- Name: category id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."category" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."category_id_seq"'::"regclass");


--

-- Name: email_outbox id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."email_outbox" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."email_outbox_id_seq"'::"regclass");


--

-- Name: implement id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."implement_id_seq"'::"regclass");


--

-- Name: individual id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."individual" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."individual_id_seq"'::"regclass");


--

-- Name: inventory_movement id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."inventory_movement" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."inventory_movement_id_seq"'::"regclass");


--

-- Name: loan id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."loan_id_seq"'::"regclass");


--

-- Name: loan_status_history id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_status_history" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."loan_status_history_id_seq"'::"regclass");


--

-- Name: location id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."location" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."location_id_seq"'::"regclass");


--

-- Name: notification id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."notification" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."notification_id_seq"'::"regclass");


--

-- Name: outbox_event id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."outbox_event" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."outbox_event_id_seq"'::"regclass");


--

-- Name: role id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."role" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."role_id_seq"'::"regclass");


--

-- Name: room id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."room" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."room_id_seq"'::"regclass");


--

-- Name: stock id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."stock" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."stock_id_seq"'::"regclass");


--

-- Name: subject id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."subject" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."subject_id_seq"'::"regclass");


--

-- Name: token_revocation id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."token_revocation" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."token_revocation_id_seq"'::"regclass");


--

-- Name: user id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."user_id_seq"'::"regclass");


--

-- Name: user_session id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user_session" ALTER COLUMN "id" SET DEFAULT "nextval"('"public"."user_session_id_seq"'::"regclass");


--

