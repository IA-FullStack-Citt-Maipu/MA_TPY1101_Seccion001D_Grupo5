-- Generated from snapshot/public-schema-full.sql on 2026-06-07.
-- Source: Supabase project panol-dev, schema public.

-- Name: individual_allocation_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."individual_allocation_status_enum" AS ENUM (
    'reserved',
    'prepared',
    'delivered',
    'returned',
    'cancelled'
);


--

-- Name: individual_condition_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."individual_condition_enum" AS ENUM (
    'good',
    'damaged_repairable',
    'damaged_no_diagnosis',
    'irreparable'
);


--

-- Name: individual_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."individual_status_enum" AS ENUM (
    'available',
    'loaned',
    'maintenance',
    'damaged',
    'blocked',
    'retired'
);


--

-- Name: inventory_movement_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."inventory_movement_type_enum" AS ENUM (
    'stock_in',
    'stock_out',
    'loan_delivery',
    'loan_return',
    'damage_report',
    'manual_adjustment',
    'consumption',
    'discard',
    'loss'
);


--

-- Name: item_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."item_type_enum" AS ENUM (
    'consumable',
    'reusable',
    'individual'
);


--

-- Name: loan_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."loan_status_enum" AS ENUM (
    'pending',
    'approved',
    'prepared',
    'delivered',
    'completed',
    'rejected',
    'cancelled',
    'expired',
    'overdue'
);


--

-- Name: outbox_status_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."outbox_status_enum" AS ENUM (
    'PENDING',
    'PROCESSING',
    'SENT',
    'FAILED'
);


--

-- Name: return_condition_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE "public"."return_condition_enum" AS ENUM (
    'good',
    'damaged',
    'lost',
    'discarded'
);


--

