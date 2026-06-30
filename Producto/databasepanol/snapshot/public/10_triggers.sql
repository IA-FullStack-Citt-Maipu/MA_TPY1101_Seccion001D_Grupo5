-- Generated from snapshot/public-schema-full.sql on 2026-06-07.
-- Source: Supabase project panol-dev, schema public.

-- Name: loan_status_history trg_create_notification_on_loan_status; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_create_notification_on_loan_status" AFTER INSERT ON "public"."loan_status_history" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_create_notification_on_loan_status"();


--

-- Name: loan_status_history trg_create_outbox_event_on_loan_status; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_create_outbox_event_on_loan_status" AFTER INSERT ON "public"."loan_status_history" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_create_outbox_event_on_loan_status"();


--

-- Name: implement trg_implement_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_implement_updated_at" BEFORE UPDATE ON "public"."implement" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_implement_updated_at"();


--

-- Name: individual trg_individual_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_individual_updated_at" BEFORE UPDATE ON "public"."individual" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_individual_updated_at"();


--

-- Name: inventory_movement trg_inventory_movement_audit; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_inventory_movement_audit" AFTER INSERT ON "public"."inventory_movement" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_inventory_movement_audit"();


--

-- Name: loan trg_loan_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_loan_updated_at" BEFORE UPDATE ON "public"."loan" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_loan_updated_at"();


--

-- Name: stock trg_stock_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_stock_updated_at" BEFORE UPDATE ON "public"."stock" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_stock_updated_at"();


--

-- Name: individual trg_validate_individual_item_type; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_validate_individual_item_type" BEFORE INSERT OR UPDATE OF "implement_id" ON "public"."individual" FOR EACH ROW EXECUTE FUNCTION "public"."fn_validate_individual_item_type"();


--

-- Name: loan trg_validate_loan_dates; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_validate_loan_dates" BEFORE INSERT OR UPDATE OF "scheduled_at", "expected_return_at" ON "public"."loan" FOR EACH ROW EXECUTE FUNCTION "public"."fn_trg_validate_loan_dates"();


--

-- Name: loan trg_validate_loan_status_transition; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER "trg_validate_loan_status_transition" BEFORE UPDATE OF "status" ON "public"."loan" FOR EACH ROW EXECUTE FUNCTION "public"."fn_validate_loan_status_transition"();


--

