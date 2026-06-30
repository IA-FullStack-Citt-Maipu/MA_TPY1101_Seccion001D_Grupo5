-- Generated from snapshot/public-schema-full.sql on 2026-06-07.
-- Source: Supabase project panol-dev, schema public.

-- Name: audit_log audit_log_select_director; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "audit_log_select_director" ON "public"."audit_log" FOR SELECT USING ((("public"."get_current_user_role"())::"text" = 'director'::"text"));


--

-- Name: inventory_movement inventory_movement_insert_staff; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "inventory_movement_insert_staff" ON "public"."inventory_movement" FOR INSERT WITH CHECK ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[])));


--

-- Name: inventory_movement inventory_movement_select_staff; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "inventory_movement_select_staff" ON "public"."inventory_movement" FOR SELECT USING ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[])));


--

-- Name: loan loan_all_staff; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "loan_all_staff" ON "public"."loan" USING ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[]))) WITH CHECK ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[])));


--

-- Name: loan loan_insert_own; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "loan_insert_own" ON "public"."loan" FOR INSERT WITH CHECK (("requester_id" = "public"."get_current_user_id"()));


--

-- Name: loan loan_select_own; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "loan_select_own" ON "public"."loan" FOR SELECT USING (("requester_id" = "public"."get_current_user_id"()));


--

-- Name: notification notification_all_director; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "notification_all_director" ON "public"."notification" USING ((("public"."get_current_user_role"())::"text" = 'director'::"text")) WITH CHECK ((("public"."get_current_user_role"())::"text" = 'director'::"text"));


--

-- Name: notification notification_all_own; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "notification_all_own" ON "public"."notification" USING (("user_id" = "public"."get_current_user_id"())) WITH CHECK (("user_id" = "public"."get_current_user_id"()));


--

-- Name: stock stock_modify_staff; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "stock_modify_staff" ON "public"."stock" USING ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[]))) WITH CHECK ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[])));


--

-- Name: stock stock_select_authenticated; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "stock_select_authenticated" ON "public"."stock" FOR SELECT USING ("public"."is_authenticated"());


--

-- Name: user user_all_director; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "user_all_director" ON "public"."user" USING ((("public"."get_current_user_role"())::"text" = 'director'::"text")) WITH CHECK ((("public"."get_current_user_role"())::"text" = 'director'::"text"));


--

-- Name: user user_select_own; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "user_select_own" ON "public"."user" FOR SELECT USING (("uuid" = (NULLIF("current_setting"('app.current_user_uuid'::"text", true), ''::"text"))::"uuid"));


--

-- Name: user user_select_staff; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "user_select_staff" ON "public"."user" FOR SELECT USING ((("public"."get_current_user_role"())::"text" = ANY ((ARRAY['coordinador'::character varying, 'director'::character varying])::"text"[])));


--

-- Name: user user_update_own; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY "user_update_own" ON "public"."user" FOR UPDATE USING (("uuid" = (NULLIF("current_setting"('app.current_user_uuid'::"text", true), ''::"text"))::"uuid")) WITH CHECK (("uuid" = (NULLIF("current_setting"('app.current_user_uuid'::"text", true), ''::"text"))::"uuid"));


--

