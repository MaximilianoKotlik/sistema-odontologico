-- C-08 (design D11): datos de demo SOLO para el perfil local (application-local.yml agrega esta carpeta a
-- spring.flyway.locations). Nunca se carga en producción ni en los tests.
-- Datos ficticios (regla dura 17). Idempotente: ids fijos y ON CONFLICT DO NOTHING.
-- knowledge-base/04_modelo_de_datos.md §Seed data inicial. Pacientes y turnos de ejemplo: C-10 / C-11.

-- La demo es un consultorio pago (ACTIVO): sin aviso de prueba ni solo lectura, para probar todo.
INSERT INTO consultorio (id, nombre, slug, direccion, telefono_contacto, plan, prueba_hasta)
VALUES ('01936b2e-0000-7000-8000-000000000001', 'Consultorio Demo', 'demo', 'Av. Siempreviva 742, CABA',
        '+5491100000010', 'ACTIVO', NULL)
ON CONFLICT DO NOTHING;

-- Las bases que ya tenían la demo en PRUEBA (antes del seed en ACTIVO) la pasan a ACTIVO sin recrear el volumen.
UPDATE consultorio SET plan = 'ACTIVO', prueba_hasta = NULL
 WHERE id = '01936b2e-0000-7000-8000-000000000001' AND plan <> 'ACTIVO';

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

-- C-10 (design D9): 5 pacientes ficticios, 10 turnos de la semana en curso (1 sobreturno solapado) y 1 bloqueo.
INSERT INTO paciente (id, consultorio_id, nombre, apellido, dni, telefono_e164, email, origen, consentimiento_at,
                      consentimiento_version)
VALUES ('01936b2e-0000-7000-8000-000000000201', '01936b2e-0000-7000-8000-000000000001', 'Paciente Uno', 'Prueba',
        '90000001', '+5491100000001', 'paciente.uno@example.com', 'LINK', now(), '2026-11-borrador'),
       ('01936b2e-0000-7000-8000-000000000202', '01936b2e-0000-7000-8000-000000000001', 'Paciente Dos', 'Prueba',
        '90000002', '+5491100000002', 'paciente.dos@example.com', 'LINK', now(), '2026-11-borrador'),
       ('01936b2e-0000-7000-8000-000000000203', '01936b2e-0000-7000-8000-000000000001', 'Paciente Tres', 'Prueba',
        '90000003', '+5491100000003', NULL, 'MANUAL', NULL, NULL),
       ('01936b2e-0000-7000-8000-000000000204', '01936b2e-0000-7000-8000-000000000001', 'Paciente Cuatro', 'Prueba',
        '90000004', '+5491100000004', 'paciente.cuatro@example.com', 'MANUAL', NULL, NULL),
       ('01936b2e-0000-7000-8000-000000000205', '01936b2e-0000-7000-8000-000000000001', 'Paciente Cinco', 'Prueba',
        '90000005', '+5491100000005', NULL, 'MANUAL', NULL, NULL)
ON CONFLICT DO NOTHING;

-- Fechas relativas al lunes de la semana en curso en Buenos Aires, en el momento de la primera carga. Flyway vuelve a
-- correr este archivo solo cuando cambia su contenido, así que quedan fijas hasta recrear el volumen (README).
WITH semana AS (
    SELECT date_trunc('week', now() AT TIME ZONE 'America/Argentina/Buenos_Aires') AS lunes
), datos (n, dia, hora, prestacion, duracion, paciente, estado, sobreturno, origen, cancelado_por) AS (
    VALUES (1, 0, TIME '09:00', 11, 30, 201, 'CONFIRMADO', false, 'LINK', NULL),
           (2, 0, TIME '10:00', 12, 45, 202, 'RESERVADO', false, 'LINK', NULL),
           (3, 0, TIME '15:00', 14, 20, 203, 'RESERVADO', false, 'MANUAL', NULL),
           (4, 1, TIME '09:00', 13, 60, 204, 'CONFIRMADO', false, 'MANUAL', NULL),
           (5, 1, TIME '11:00', 11, 30, 205, 'RESERVADO', false, 'MANUAL', NULL),
           (6, 2, TIME '09:30', 12, 45, 201, 'RESERVADO', false, 'LINK', NULL),
           (7, 2, TIME '16:00', 11, 30, 202, 'CANCELADO', false, 'LINK', 'PACIENTE'),
           (8, 3, TIME '10:00', 14, 20, 203, 'RESERVADO', false, 'MANUAL', NULL),
           (9, 4, TIME '09:00', 11, 30, 204, 'RESERVADO', false, 'MANUAL', NULL),
           -- Sobreturno solapado con el turno 1 (RN-AG-04): solo el odontólogo, siempre MANUAL.
           (10, 0, TIME '09:10', 14, 20, 205, 'RESERVADO', true, 'MANUAL', NULL)
)
INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min, estado, es_sobreturno,
                   origen, cancelado_por)
SELECT ('01936b2e-0000-7000-8000-000000000' || (300 + d.n))::uuid,
       '01936b2e-0000-7000-8000-000000000001',
       ('01936b2e-0000-7000-8000-000000000' || d.paciente)::uuid,
       ('01936b2e-0000-7000-8000-0000000000' || d.prestacion)::uuid,
       (s.lunes + d.dia * interval '1 day' + d.hora) AT TIME ZONE 'America/Argentina/Buenos_Aires',
       (s.lunes + d.dia * interval '1 day' + d.hora + make_interval(mins => d.duracion))
           AT TIME ZONE 'America/Argentina/Buenos_Aires',
       d.duracion, d.estado, d.sobreturno, d.origen, d.cancelado_por
FROM datos d CROSS JOIN semana s
ON CONFLICT DO NOTHING;

-- Jueves a la tarde, sin turnos activos (RN-AG-05).
INSERT INTO bloqueo (id, consultorio_id, inicio, fin, motivo)
SELECT '01936b2e-0000-7000-8000-000000000401', '01936b2e-0000-7000-8000-000000000001',
       (s.lunes + interval '3 days' + TIME '15:00') AT TIME ZONE 'America/Argentina/Buenos_Aires',
       (s.lunes + interval '3 days' + TIME '19:00') AT TIME ZONE 'America/Argentina/Buenos_Aires',
       'Curso'
FROM (SELECT date_trunc('week', now() AT TIME ZONE 'America/Argentina/Buenos_Aires') AS lunes) s
ON CONFLICT DO NOTHING;
