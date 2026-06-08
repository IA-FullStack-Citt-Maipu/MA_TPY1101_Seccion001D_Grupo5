BEGIN;

SET search_path TO public;

INSERT INTO public.role (id, uuid, name, description)
VALUES
    (1, '063788f2-a716-4cf3-864c-d8dfaf8b1dc7', 'director', 'Acceso total de gesti?n'),
    (2, 'fa3adffe-9ca4-4b12-932f-ad2076484af6', 'coordinador', 'Gesti?n operativa de pa?ol'),
    (3, '32420958-c929-495a-9c68-bf380cfe254c', 'docente', 'Solicitante de pr?stamos')
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.career (id, uuid, code, name, active, created_at)
VALUES
    (1, '5d71ecb9-54c5-45cc-8e9f-2331f5f786d8', 'ENF', 'Enfermer?a', true, now()),
    (2, '4f44dce8-0bd4-4208-a7dc-9827029f9da8', 'FAR', 'Farmacolog?a Cl?nica', true, now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO public."user" (
    id,
    uuid,
    role_id,
    career_id,
    name,
    rut,
    email,
    password_hash,
    auth_uuid,
    active,
    failed_login_attempts,
    blocked_until,
    last_login_at,
    created_at,
    updated_at
)
VALUES
    (
        79,
        'efb264c4-6ca7-49c3-a4ad-77f2778d2a49',
        2,
        1,
        'Coordinador QA Local',
        '11111111',
        'coordinador.local@panolsalud.test',
        '$2b$12$cBAk2d9bJrAobQW1u349AumIkUAqehI7qZzD9AOCptNkDW/yOlCtW',
        NULL,
        true,
        0,
        NULL,
        NULL,
        now(),
        now()
    ),
    (
        80,
        'b5eafded-e45e-48e0-8066-837572d61d4a',
        3,
        1,
        'Docente QA Local',
        '22222222',
        'docente.local@panolsalud.test',
        '$2b$12$.3Ddd0MLBoYPDQPo4xh.d.FrrB4vwBGDxBVITDuIY94HRjfVH03Rq',
        NULL,
        true,
        0,
        NULL,
        NULL,
        now(),
        now()
    ),
    (
        81,
        'd88f775f-b555-474b-a3ed-3d6bf8ebf302',
        1,
        2,
        'Director QA Local',
        '33333333',
        'director.local@panolsalud.test',
        '$2b$12$qZkxz21Or9bWy4vQc0EUMeaNKvqIf.YxoQYgrYGfkWschGCwneaPG',
        NULL,
        true,
        0,
        NULL,
        NULL,
        now(),
        now()
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.category (id, uuid, name, description, active, created_at)
VALUES
    (1, '176fd508-7786-4b22-9bb7-0f66d913f77e', 'herramientas', 'Categor?a base local para pruebas de pr?stamo', true, now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.location (id, uuid, name, description, location_type, active)
VALUES
    (1, '2156dec5-c359-4b78-8c12-9195d813aae4', 'Estante B', 'Ubicaci?n base local para inventario QA', 'PANOL_SHELF', true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.subject (id, uuid, code, name, active, created_at)
VALUES
    (32, '2fbc919e-0de0-432a-b460-7fd83d82c0b6', 'SAL-ENF-101', 'Fundamentos de Enfermer?a', true, now()),
    (33, '92e054fa-f940-4ef7-8f08-732d84361d73', 'SAL-FAR-201', 'Farmacolog?a Cl?nica', true, now()),
    (34, 'c0549423-4b6e-4c95-940f-a747fd6cd31e', 'SAL-APS-120', 'Anatom?a Aplicada', true, now()),
    (35, '50a4856e-d49b-4602-ae43-2ae5da227a03', 'SAL-URG-150', 'Urgencias y Primeros Auxilios', true, now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.room (id, uuid, name, description, active)
VALUES
    (32, '0700ee77-a0d2-4391-b622-9c8d7b83deb0', 'Sala 320', 'Sala de simulaci?n cl?nica local', true),
    (33, '1d4006ff-1ee5-4ae1-a03c-90fe559f4fef', 'Sala 321', 'Sala de procedimientos local', true),
    (34, '1a925554-d8b7-4aa9-8a43-a50001bc45a2', 'Sala 322', 'Sala de entrenamiento de enfermer?a local', true),
    (35, '354571ca-5f32-4a58-bf10-8891d4a82163', 'Sala 323', 'Sala de farmacolog?a local', true),
    (36, '815e7ecd-d518-4530-9bcf-532d0e79f7e8', 'Sala 324', 'Sala de urgencias local', true)
ON CONFLICT (id) DO NOTHING;

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
    (
        1001,
        'f8d2f97d-63da-40c5-b1f1-c1f2e2f9ea11',
        1,
        1,
        'Jeringa 5ml',
        'Insumo consumible para pr?cticas de administraci?n de medicamentos',
        'consumable'::public.item_type_enum,
        'SEED-IMP-1001',
        NULL,
        true,
        'Semilla local de databasepanol',
        now(),
        now()
    ),
    (
        1002,
        '7e218e90-f42c-44ba-b1ba-f833d2671eb2',
        1,
        1,
        'Simulador de v?a venosa',
        'Implemento reusable para pr?cticas de canalizaci?n y preparaci?n',
        'reusable'::public.item_type_enum,
        'SEED-IMP-1002',
        NULL,
        true,
        'Semilla local de databasepanol',
        now(),
        now()
    ),
    (
        1003,
        '2e7aefde-9344-48b4-a8c6-0f9727f31772',
        1,
        1,
        'Estetoscopio acad?mico',
        'Implemento individual para control de estado unitario',
        'individual'::public.item_type_enum,
        'SEED-IMP-1003',
        NULL,
        true,
        'Semilla local de databasepanol',
        now(),
        now()
    )
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
    (1001, 1001, 120, 30, 100, 20, 0, 0, now()),
    (1002, 1002, 8, 2, 7, 1, 0, 0, now()),
    (1003, 1003, 3, 1, 3, 0, 0, 0, now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.individual (
    id,
    uuid,
    implement_id,
    current_location_id,
    asset_code,
    status,
    condition,
    notes,
    active,
    created_at,
    updated_at
)
VALUES
    (10001, 'f57289fa-e9eb-40d3-a95b-cd0c0d1a7d31', 1003, 1, 'SEED-EST-001', 'available'::public.individual_status_enum, 'good'::public.individual_condition_enum, 'Semilla local', true, now(), now()),
    (10002, 'af79d6a4-df75-4633-98f6-084370b4a308', 1003, 1, 'SEED-EST-002', 'available'::public.individual_status_enum, 'good'::public.individual_condition_enum, 'Semilla local', true, now(), now()),
    (10003, '30a9b919-7c6f-46cb-8b4f-4e895c4a8d1b', 1003, 1, 'SEED-EST-003', 'available'::public.individual_status_enum, 'good'::public.individual_condition_enum, 'Semilla local', true, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.inventory_movement (
    id,
    implement_id,
    actor_user_id,
    movement_type,
    quantity,
    delta_changes,
    systemic_metadata,
    created_at
)
VALUES
    (
        5001,
        1001,
        79,
        'stock_in'::public.inventory_movement_type_enum,
        120,
        '{"available": 100, "reserved": 20, "loaned": 0, "damaged": 0, "total_stock": 120}'::jsonb,
        '{"source": "databasepanol-seed", "note": "Carga inicial local"}'::jsonb,
        now()
    ),
    (
        5002,
        1002,
        79,
        'manual_adjustment'::public.inventory_movement_type_enum,
        8,
        '{"available": 7, "reserved": 1, "loaned": 0, "damaged": 0, "total_stock": 8}'::jsonb,
        '{"source": "databasepanol-seed", "note": "Ajuste inicial local"}'::jsonb,
        now()
    ),
    (
        5003,
        1003,
        79,
        'stock_in'::public.inventory_movement_type_enum,
        3,
        '{"available": 3, "reserved": 0, "loaned": 0, "damaged": 0, "total_stock": 3}'::jsonb,
        '{"source": "databasepanol-seed", "note": "Alta inicial de individuales"}'::jsonb,
        now()
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.loan (
    id,
    uuid,
    requester_id,
    room_id,
    subject_id,
    status,
    scheduled_at,
    created_at,
    expected_return_at,
    updated_at
)
VALUES
    (
        7001,
        '6f42fca6-3074-434d-81eb-bdfdbcae4488',
        80,
        34,
        32,
        'pending'::public.loan_status_enum,
        date_trunc('day', now()) + interval '2 day 9 hour',
        now(),
        date_trunc('day', now()) + interval '2 day 11 hour',
        now()
    ),
    (
        7002,
        '8a6b4afc-4954-4467-b0c1-53df927753b3',
        80,
        35,
        35,
        'approved'::public.loan_status_enum,
        date_trunc('day', now()) + interval '1 day 10 hour',
        now(),
        date_trunc('day', now()) + interval '1 day 12 hour',
        now()
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.loan_detail (
    loan_id,
    implement_id,
    requested_quantity,
    reserved_quantity,
    delivered_quantity,
    returned_quantity,
    damaged_quantity,
    lost_quantity,
    consumed_quantity,
    discarded_quantity,
    return_notes
)
VALUES
    (7001, 1001, 10, 0, 0, 0, 0, 0, 0, 0, NULL),
    (7001, 1002, 1, 0, 0, 0, 0, 0, 0, 0, NULL),
    (7002, 1001, 20, 20, 0, 0, 0, 0, 0, 0, NULL),
    (7002, 1002, 1, 1, 0, 0, 0, 0, 0, 0, NULL)
ON CONFLICT (loan_id, implement_id) DO NOTHING;

INSERT INTO public.loan_status_history (
    id,
    loan_id,
    actor_user_id,
    from_status,
    to_status,
    notes,
    changed_at
)
VALUES
    (7001, 7001, 80, NULL, 'pending'::public.loan_status_enum, 'Solicitud inicial creada desde seed local', now()),
    (7002, 7002, 80, NULL, 'pending'::public.loan_status_enum, 'Solicitud demo creada desde seed local', now()),
    (7003, 7002, 79, 'pending'::public.loan_status_enum, 'approved'::public.loan_status_enum, 'Solicitud aprobada en seed local para probar agenda operativa', now())
ON CONFLICT (id) DO NOTHING;

SELECT setval('public.audit_log_id_seq', COALESCE((SELECT MAX(id) FROM public.audit_log), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public.audit_log), false));
SELECT setval('public.career_id_seq', COALESCE((SELECT MAX(id) FROM public.career), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public.career), false));
SELECT setval('public.category_id_seq', COALESCE((SELECT MAX(id) FROM public.category), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public.category), false));
SELECT setval('public.implement_id_seq', COALESCE((SELECT MAX(id) FROM public.implement), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public.implement), false));
SELECT setval('public.individual_id_seq', COALESCE((SELECT MAX(id) FROM public.individual), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public.individual), false));
SELECT setval('public.inventory_movement_id_seq', COALESCE((SELECT MAX(id) FROM public.inventory_movement), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public.inventory_movement), false));
SELECT setval('public.loan_id_seq', COALESCE((SELECT MAX(id) FROM public.loan), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public.loan), false));
SELECT setval('public.loan_status_history_id_seq', COALESCE((SELECT MAX(id) FROM public.loan_status_history), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public.loan_status_history), false));
SELECT setval('public.location_id_seq', COALESCE((SELECT MAX(id) FROM public.location), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public.location), false));
SELECT setval('public.role_id_seq', COALESCE((SELECT MAX(id) FROM public.role), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public.role), false));
SELECT setval('public.room_id_seq', COALESCE((SELECT MAX(id) FROM public.room), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public.room), false));
SELECT setval('public.stock_id_seq', COALESCE((SELECT MAX(id) FROM public.stock), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public.stock), false));
SELECT setval('public.subject_id_seq', COALESCE((SELECT MAX(id) FROM public.subject), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public.subject), false));
SELECT setval('public.user_id_seq', COALESCE((SELECT MAX(id) FROM public."user"), 1), COALESCE((SELECT MAX(id) IS NOT NULL FROM public."user"), false));

COMMIT;
