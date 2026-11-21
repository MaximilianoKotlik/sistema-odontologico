-- C-08 (design D11): datos de demo SOLO para el perfil local (application-local.yml agrega esta carpeta a
-- spring.flyway.locations). Nunca se carga en producción ni en los tests.
-- Datos ficticios (regla dura 17). Idempotente: ids fijos y ON CONFLICT DO NOTHING.
-- knowledge-base/04_modelo_de_datos.md §Seed data inicial. Pacientes y turnos de ejemplo: C-10 / C-11.

INSERT INTO consultorio (id, nombre, slug, direccion, telefono_contacto, plan, prueba_hasta)
VALUES ('01936b2e-0000-7000-8000-000000000001', 'Consultorio Demo', 'demo', 'Av. Siempreviva 742, CABA',
        '+5491100000010', 'PRUEBA', DATE '2099-12-31')
ON CONFLICT DO NOTHING;

-- Contraseña de desarrollo: ver db/seed-local/README.md. Hash BCrypt de costo 12.
INSERT INTO usuario (id, consultorio_id, email, password_hash, nombre, apellido, password_cambiado_at,
                     terminos_version, terminos_aceptados_at)
VALUES ('01936b2e-0000-7000-8000-000000000002', '01936b2e-0000-7000-8000-000000000001', 'demo@consultorio.test',
        '$2a$12$Je9J94jl173aVqzC54JYnuC8mVQUCzSaVLWSGd.4XyHJq81ye6Kie', 'Ana', 'Demo', now(), '2026-11-borrador', now())
ON CONFLICT DO NOTHING;

INSERT INTO tipo_prestacion (id, consultorio_id, nombre, duracion_min, orden)
VALUES ('01936b2e-0000-7000-8000-000000000011', '01936b2e-0000-7000-8000-000000000001', 'Consulta', 30, 1),
       ('01936b2e-0000-7000-8000-000000000012', '01936b2e-0000-7000-8000-000000000001', 'Limpieza', 45, 2),
       ('01936b2e-0000-7000-8000-000000000013', '01936b2e-0000-7000-8000-000000000001', 'Extracción', 60, 3),
       ('01936b2e-0000-7000-8000-000000000014', '01936b2e-0000-7000-8000-000000000001', 'Control', 20, 4)
ON CONFLICT DO NOTHING;

-- Lunes a viernes, de 09:00 a 13:00 y de 15:00 a 19:00.
INSERT INTO disponibilidad (id, consultorio_id, dia_semana, hora_inicio, hora_fin)
SELECT ('01936b2e-0000-7000-8000-0000000001' || dia || franja)::uuid, '01936b2e-0000-7000-8000-000000000001', dia,
       CASE franja WHEN 1 THEN TIME '09:00' ELSE TIME '15:00' END,
       CASE franja WHEN 1 THEN TIME '13:00' ELSE TIME '19:00' END
FROM generate_series(1, 5) AS dia, generate_series(1, 2) AS franja
ON CONFLICT DO NOTHING;
