-- Generated from snapshot/public-schema-full.sql on 2026-06-07.
-- Source: Supabase project panol-dev, schema public.

-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "flyway_schema_history_s_idx" ON "public"."flyway_schema_history" USING "btree" ("success");


--

-- Name: idx_individual_implement_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_individual_implement_status" ON "public"."individual" USING "btree" ("implement_id", "status");


--

-- Name: idx_inventory_movement_implement_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_inventory_movement_implement_date" ON "public"."inventory_movement" USING "btree" ("implement_id", "created_at" DESC);


--

-- Name: idx_loan_detail_implement_loan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_detail_implement_loan" ON "public"."loan_detail" USING "btree" ("implement_id", "loan_id");


--

-- Name: idx_loan_history_loan_changed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_history_loan_changed" ON "public"."loan_status_history" USING "btree" ("loan_id", "changed_at" DESC);


--

-- Name: idx_loan_history_to_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_history_to_status" ON "public"."loan_status_history" USING "btree" ("loan_id", "to_status", "changed_at" DESC);


--

-- Name: idx_loan_requester_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_requester_status" ON "public"."loan" USING "btree" ("requester_id", "status");


--

-- Name: idx_loan_schedule_range; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_schedule_range" ON "public"."loan" USING "btree" ("scheduled_at", "expected_return_at");


--

-- Name: idx_loan_status_history_to_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_status_history_to_status" ON "public"."loan_status_history" USING "btree" ("to_status");


--

-- Name: idx_loan_status_scheduled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_loan_status_scheduled" ON "public"."loan" USING "btree" ("status", "scheduled_at");


--

-- Name: idx_notification_user_read; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX "idx_notification_user_read" ON "public"."notification" USING "btree" ("user_id", "read_status", "created_at" DESC);


--

