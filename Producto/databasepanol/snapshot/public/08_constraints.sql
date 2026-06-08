-- Generated from snapshot/public-schema-full.sql on 2026-06-07.
-- Source: Supabase project panol-dev, schema public.

-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."audit_log"
    ADD CONSTRAINT "audit_log_pkey" PRIMARY KEY ("id");


--

-- Name: career career_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."career"
    ADD CONSTRAINT "career_code_key" UNIQUE ("code");


--

-- Name: career career_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."career"
    ADD CONSTRAINT "career_pkey" PRIMARY KEY ("id");


--

-- Name: career career_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."career"
    ADD CONSTRAINT "career_uuid_key" UNIQUE ("uuid");


--

-- Name: category category_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."category"
    ADD CONSTRAINT "category_name_key" UNIQUE ("name");


--

-- Name: category category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."category"
    ADD CONSTRAINT "category_pkey" PRIMARY KEY ("id");


--

-- Name: category category_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."category"
    ADD CONSTRAINT "category_uuid_key" UNIQUE ("uuid");


--

-- Name: email_outbox email_outbox_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."email_outbox"
    ADD CONSTRAINT "email_outbox_event_id_key" UNIQUE ("event_id");


--

-- Name: email_outbox email_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."email_outbox"
    ADD CONSTRAINT "email_outbox_pkey" PRIMARY KEY ("id");


--

-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."flyway_schema_history"
    ADD CONSTRAINT "flyway_schema_history_pk" PRIMARY KEY ("installed_rank");


--

-- Name: implement implement_barcode_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement"
    ADD CONSTRAINT "implement_barcode_key" UNIQUE ("barcode");


--

-- Name: implement implement_name_category_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement"
    ADD CONSTRAINT "implement_name_category_key" UNIQUE ("name", "category_id");


--

-- Name: implement implement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement"
    ADD CONSTRAINT "implement_pkey" PRIMARY KEY ("id");


--

-- Name: implement implement_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement"
    ADD CONSTRAINT "implement_uuid_key" UNIQUE ("uuid");


--

-- Name: individual individual_asset_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."individual"
    ADD CONSTRAINT "individual_asset_code_key" UNIQUE ("asset_code");


--

-- Name: individual individual_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."individual"
    ADD CONSTRAINT "individual_pkey" PRIMARY KEY ("id");


--

-- Name: individual individual_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."individual"
    ADD CONSTRAINT "individual_uuid_key" UNIQUE ("uuid");


--

-- Name: inventory_movement inventory_movement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."inventory_movement"
    ADD CONSTRAINT "inventory_movement_pkey" PRIMARY KEY ("id");


--

-- Name: loan_detail_individual loan_detail_individual_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_detail_individual"
    ADD CONSTRAINT "loan_detail_individual_pkey" PRIMARY KEY ("loan_id", "implement_id", "individual_id");


--

-- Name: loan_detail loan_detail_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_detail"
    ADD CONSTRAINT "loan_detail_pkey" PRIMARY KEY ("loan_id", "implement_id");


--

-- Name: loan loan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan"
    ADD CONSTRAINT "loan_pkey" PRIMARY KEY ("id");


--

-- Name: loan_status_history loan_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_status_history"
    ADD CONSTRAINT "loan_status_history_pkey" PRIMARY KEY ("id");


--

-- Name: loan loan_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan"
    ADD CONSTRAINT "loan_uuid_key" UNIQUE ("uuid");


--

-- Name: location location_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."location"
    ADD CONSTRAINT "location_name_key" UNIQUE ("name");


--

-- Name: location location_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."location"
    ADD CONSTRAINT "location_pkey" PRIMARY KEY ("id");


--

-- Name: location location_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."location"
    ADD CONSTRAINT "location_uuid_key" UNIQUE ("uuid");


--

-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."notification"
    ADD CONSTRAINT "notification_pkey" PRIMARY KEY ("id");


--

-- Name: notification notification_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."notification"
    ADD CONSTRAINT "notification_uuid_key" UNIQUE ("uuid");


--

-- Name: outbox_event outbox_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."outbox_event"
    ADD CONSTRAINT "outbox_event_id_key" UNIQUE ("event_id");


--

-- Name: outbox_event outbox_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."outbox_event"
    ADD CONSTRAINT "outbox_event_pkey" PRIMARY KEY ("id");


--

-- Name: role role_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."role"
    ADD CONSTRAINT "role_name_key" UNIQUE ("name");


--

-- Name: role role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."role"
    ADD CONSTRAINT "role_pkey" PRIMARY KEY ("id");


--

-- Name: role role_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."role"
    ADD CONSTRAINT "role_uuid_key" UNIQUE ("uuid");


--

-- Name: room room_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."room"
    ADD CONSTRAINT "room_name_key" UNIQUE ("name");


--

-- Name: room room_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."room"
    ADD CONSTRAINT "room_pkey" PRIMARY KEY ("id");


--

-- Name: room room_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."room"
    ADD CONSTRAINT "room_uuid_key" UNIQUE ("uuid");


--

-- Name: stock stock_implement_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."stock"
    ADD CONSTRAINT "stock_implement_id_key" UNIQUE ("implement_id");


--

-- Name: stock stock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."stock"
    ADD CONSTRAINT "stock_pkey" PRIMARY KEY ("id");


--

-- Name: subject subject_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."subject"
    ADD CONSTRAINT "subject_code_key" UNIQUE ("code");


--

-- Name: subject subject_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."subject"
    ADD CONSTRAINT "subject_pkey" PRIMARY KEY ("id");


--

-- Name: subject subject_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."subject"
    ADD CONSTRAINT "subject_uuid_key" UNIQUE ("uuid");


--

-- Name: token_revocation token_revocation_jti_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."token_revocation"
    ADD CONSTRAINT "token_revocation_jti_key" UNIQUE ("jti");


--

-- Name: token_revocation token_revocation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."token_revocation"
    ADD CONSTRAINT "token_revocation_pkey" PRIMARY KEY ("id");


--

-- Name: user user_auth_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "user_auth_uuid_key" UNIQUE ("auth_uuid");


--

-- Name: user user_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "user_email_key" UNIQUE ("email");


--

-- Name: user user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "user_pkey" PRIMARY KEY ("id");


--

-- Name: user user_rut_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "user_rut_key" UNIQUE ("rut");


--

-- Name: user_session user_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user_session"
    ADD CONSTRAINT "user_session_pkey" PRIMARY KEY ("id");


--

-- Name: user_session user_session_refresh_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user_session"
    ADD CONSTRAINT "user_session_refresh_token_hash_key" UNIQUE ("refresh_token_hash");


--

-- Name: user user_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "user_uuid_key" UNIQUE ("uuid");


--

-- Name: audit_log fk_audit_actor_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."audit_log"
    ADD CONSTRAINT "fk_audit_actor_user" FOREIGN KEY ("actor_user_id") REFERENCES "public"."user"("id") ON DELETE SET NULL;


--

-- Name: audit_log fk_audit_target_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."audit_log"
    ADD CONSTRAINT "fk_audit_target_user" FOREIGN KEY ("target_user_id") REFERENCES "public"."user"("id") ON DELETE SET NULL;


--

-- Name: implement fk_implement_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement"
    ADD CONSTRAINT "fk_implement_category" FOREIGN KEY ("category_id") REFERENCES "public"."category"("id") ON DELETE RESTRICT;


--

-- Name: implement fk_implement_location; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."implement"
    ADD CONSTRAINT "fk_implement_location" FOREIGN KEY ("location_id") REFERENCES "public"."location"("id") ON DELETE RESTRICT;


--

-- Name: individual fk_individual_implement; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."individual"
    ADD CONSTRAINT "fk_individual_implement" FOREIGN KEY ("implement_id") REFERENCES "public"."implement"("id") ON DELETE CASCADE;


--

-- Name: individual fk_individual_location; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."individual"
    ADD CONSTRAINT "fk_individual_location" FOREIGN KEY ("current_location_id") REFERENCES "public"."location"("id") ON DELETE RESTRICT;


--

-- Name: inventory_movement fk_inv_movement_actor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."inventory_movement"
    ADD CONSTRAINT "fk_inv_movement_actor" FOREIGN KEY ("actor_user_id") REFERENCES "public"."user"("id") ON DELETE RESTRICT;


--

-- Name: inventory_movement fk_inv_movement_implement; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."inventory_movement"
    ADD CONSTRAINT "fk_inv_movement_implement" FOREIGN KEY ("implement_id") REFERENCES "public"."implement"("id") ON DELETE RESTRICT;


--

-- Name: loan_detail fk_loan_detail_implement; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_detail"
    ADD CONSTRAINT "fk_loan_detail_implement" FOREIGN KEY ("implement_id") REFERENCES "public"."implement"("id") ON DELETE RESTRICT;


--

-- Name: loan_detail_individual fk_loan_detail_individual_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_detail_individual"
    ADD CONSTRAINT "fk_loan_detail_individual_parent" FOREIGN KEY ("loan_id", "implement_id") REFERENCES "public"."loan_detail"("loan_id", "implement_id") ON DELETE CASCADE;


--

-- Name: loan_detail_individual fk_loan_detail_individual_unit; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_detail_individual"
    ADD CONSTRAINT "fk_loan_detail_individual_unit" FOREIGN KEY ("individual_id") REFERENCES "public"."individual"("id") ON DELETE RESTRICT;


--

-- Name: loan_detail fk_loan_detail_loan; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_detail"
    ADD CONSTRAINT "fk_loan_detail_loan" FOREIGN KEY ("loan_id") REFERENCES "public"."loan"("id") ON DELETE CASCADE;


--

-- Name: loan_status_history fk_loan_history_actor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_status_history"
    ADD CONSTRAINT "fk_loan_history_actor" FOREIGN KEY ("actor_user_id") REFERENCES "public"."user"("id") ON DELETE RESTRICT;


--

-- Name: loan_status_history fk_loan_history_loan; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan_status_history"
    ADD CONSTRAINT "fk_loan_history_loan" FOREIGN KEY ("loan_id") REFERENCES "public"."loan"("id") ON DELETE CASCADE;


--

-- Name: loan fk_loan_requester; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan"
    ADD CONSTRAINT "fk_loan_requester" FOREIGN KEY ("requester_id") REFERENCES "public"."user"("id") ON DELETE RESTRICT;


--

-- Name: loan fk_loan_room; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan"
    ADD CONSTRAINT "fk_loan_room" FOREIGN KEY ("room_id") REFERENCES "public"."room"("id") ON DELETE RESTRICT;


--

-- Name: loan fk_loan_subject; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."loan"
    ADD CONSTRAINT "fk_loan_subject" FOREIGN KEY ("subject_id") REFERENCES "public"."subject"("id") ON DELETE RESTRICT;


--

-- Name: notification fk_notification_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."notification"
    ADD CONSTRAINT "fk_notification_user" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE CASCADE;


--

-- Name: stock fk_stock_implement; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."stock"
    ADD CONSTRAINT "fk_stock_implement" FOREIGN KEY ("implement_id") REFERENCES "public"."implement"("id") ON DELETE CASCADE;


--

-- Name: token_revocation fk_token_revocation_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."token_revocation"
    ADD CONSTRAINT "fk_token_revocation_user" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE CASCADE;


--

-- Name: user fk_user_career; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "fk_user_career" FOREIGN KEY ("career_id") REFERENCES "public"."career"("id") ON DELETE RESTRICT;


--

-- Name: user fk_user_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user"
    ADD CONSTRAINT "fk_user_role" FOREIGN KEY ("role_id") REFERENCES "public"."role"("id") ON DELETE RESTRICT;


--

-- Name: user_session fk_user_session_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY "public"."user_session"
    ADD CONSTRAINT "fk_user_session_user" FOREIGN KEY ("user_id") REFERENCES "public"."user"("id") ON DELETE CASCADE;


--

