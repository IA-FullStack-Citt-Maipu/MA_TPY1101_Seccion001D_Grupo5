BEGIN;

SET search_path TO public;

INSERT INTO public.implement (
    id,
    uuid,
    category_id,
    location_id,
    name,
    description,
    item_type,
    barcode,
    img_url,
    active,
    observations,
    created_at,
    updated_at
)
VALUES
    (2001, 'a4d14b48-e453-4f4b-bafe-f7db99ae9001', 1, 1, 'Set de curacion A', 'Implemento sintetico para carga local', 'consumable'::public.item_type_enum, 'LOAD-IMP-2001', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2002, 'a4d14b48-e453-4f4b-bafe-f7db99ae9002', 1, 1, 'Set de curacion B', 'Implemento sintetico para carga local', 'consumable'::public.item_type_enum, 'LOAD-IMP-2002', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2003, 'a4d14b48-e453-4f4b-bafe-f7db99ae9003', 1, 1, 'Simulador de inyeccion A', 'Implemento sintetico para carga local', 'reusable'::public.item_type_enum, 'LOAD-IMP-2003', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2004, 'a4d14b48-e453-4f4b-bafe-f7db99ae9004', 1, 1, 'Simulador de inyeccion B', 'Implemento sintetico para carga local', 'reusable'::public.item_type_enum, 'LOAD-IMP-2004', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2005, 'a4d14b48-e453-4f4b-bafe-f7db99ae9005', 1, 1, 'Mascarilla clinica A', 'Implemento sintetico para carga local', 'consumable'::public.item_type_enum, 'LOAD-IMP-2005', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2006, 'a4d14b48-e453-4f4b-bafe-f7db99ae9006', 1, 1, 'Mascarilla clinica B', 'Implemento sintetico para carga local', 'consumable'::public.item_type_enum, 'LOAD-IMP-2006', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2007, 'a4d14b48-e453-4f4b-bafe-f7db99ae9007', 1, 1, 'Bandeja de procedimiento A', 'Implemento sintetico para carga local', 'reusable'::public.item_type_enum, 'LOAD-IMP-2007', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2008, 'a4d14b48-e453-4f4b-bafe-f7db99ae9008', 1, 1, 'Bandeja de procedimiento B', 'Implemento sintetico para carga local', 'reusable'::public.item_type_enum, 'LOAD-IMP-2008', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2009, 'a4d14b48-e453-4f4b-bafe-f7db99ae9009', 1, 1, 'Guantes nitrilo A', 'Implemento sintetico para carga local', 'consumable'::public.item_type_enum, 'LOAD-IMP-2009', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2010, 'a4d14b48-e453-4f4b-bafe-f7db99ae9010', 1, 1, 'Guantes nitrilo B', 'Implemento sintetico para carga local', 'consumable'::public.item_type_enum, 'LOAD-IMP-2010', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2011, 'a4d14b48-e453-4f4b-bafe-f7db99ae9011', 1, 1, 'Pinza quirurgica A', 'Implemento sintetico para carga local', 'reusable'::public.item_type_enum, 'LOAD-IMP-2011', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2012, 'a4d14b48-e453-4f4b-bafe-f7db99ae9012', 1, 1, 'Pinza quirurgica B', 'Implemento sintetico para carga local', 'reusable'::public.item_type_enum, 'LOAD-IMP-2012', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2013, 'a4d14b48-e453-4f4b-bafe-f7db99ae9013', 1, 1, 'Vendas A', 'Implemento sintetico para carga local', 'consumable'::public.item_type_enum, 'LOAD-IMP-2013', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2014, 'a4d14b48-e453-4f4b-bafe-f7db99ae9014', 1, 1, 'Vendas B', 'Implemento sintetico para carga local', 'consumable'::public.item_type_enum, 'LOAD-IMP-2014', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2015, 'a4d14b48-e453-4f4b-bafe-f7db99ae9015', 1, 1, 'Simulador de sutura A', 'Implemento sintetico para carga local', 'reusable'::public.item_type_enum, 'LOAD-IMP-2015', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2016, 'a4d14b48-e453-4f4b-bafe-f7db99ae9016', 1, 1, 'Simulador de sutura B', 'Implemento sintetico para carga local', 'reusable'::public.item_type_enum, 'LOAD-IMP-2016', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2017, 'a4d14b48-e453-4f4b-bafe-f7db99ae9017', 1, 1, 'Cateter A', 'Implemento sintetico para carga local', 'consumable'::public.item_type_enum, 'LOAD-IMP-2017', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2018, 'a4d14b48-e453-4f4b-bafe-f7db99ae9018', 1, 1, 'Cateter B', 'Implemento sintetico para carga local', 'consumable'::public.item_type_enum, 'LOAD-IMP-2018', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2019, 'a4d14b48-e453-4f4b-bafe-f7db99ae9019', 1, 1, 'Fonendoscopio de practica A', 'Implemento sintetico para carga local', 'reusable'::public.item_type_enum, 'LOAD-IMP-2019', NULL, true, 'Seed local de rendimiento', now(), now()),
    (2020, 'a4d14b48-e453-4f4b-bafe-f7db99ae9020', 1, 1, 'Fonendoscopio de practica B', 'Implemento sintetico para carga local', 'reusable'::public.item_type_enum, 'LOAD-IMP-2020', NULL, true, 'Seed local de rendimiento', now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.stock (
    id,
    implement_id,
    total_stock,
    min_stock,
    available,
    reserved,
    loaned,
    damaged,
    updated_at
)
VALUES
    (2001, 2001, 50, 5, 50, 0, 0, 0, now()),
    (2002, 2002, 50, 5, 50, 0, 0, 0, now()),
    (2003, 2003, 50, 5, 50, 0, 0, 0, now()),
    (2004, 2004, 50, 5, 50, 0, 0, 0, now()),
    (2005, 2005, 50, 5, 50, 0, 0, 0, now()),
    (2006, 2006, 50, 5, 50, 0, 0, 0, now()),
    (2007, 2007, 50, 5, 50, 0, 0, 0, now()),
    (2008, 2008, 50, 5, 50, 0, 0, 0, now()),
    (2009, 2009, 50, 5, 50, 0, 0, 0, now()),
    (2010, 2010, 50, 5, 50, 0, 0, 0, now()),
    (2011, 2011, 50, 5, 50, 0, 0, 0, now()),
    (2012, 2012, 50, 5, 50, 0, 0, 0, now()),
    (2013, 2013, 50, 5, 50, 0, 0, 0, now()),
    (2014, 2014, 50, 5, 50, 0, 0, 0, now()),
    (2015, 2015, 50, 5, 50, 0, 0, 0, now()),
    (2016, 2016, 50, 5, 50, 0, 0, 0, now()),
    (2017, 2017, 50, 5, 50, 0, 0, 0, now()),
    (2018, 2018, 50, 5, 50, 0, 0, 0, now()),
    (2019, 2019, 50, 5, 50, 0, 0, 0, now()),
    (2020, 2020, 50, 5, 50, 0, 0, 0, now())
ON CONFLICT (id) DO NOTHING;

COMMIT;
