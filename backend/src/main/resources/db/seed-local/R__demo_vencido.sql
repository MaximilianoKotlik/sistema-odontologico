-- C-23 (design D8): segundo consultorio de demo, con el plan VENCIDO, SOLO para el perfil local (como R__demo.sql).
-- Sirve para ver la agenda en solo lectura, la exportación y la página pública que no toma turnos online.
-- Datos ficticios (regla dura 17). Idempotente: ids fijos y ON CONFLICT DO NOTHING. Cuentas en db/seed-local/README.md.

INSERT INTO consultorio (id, nombre, slug, direccion, telefono_contacto, plan, prueba_hasta)
VALUES ('01936b2e-0000-7000-8000-00000000a001', 'Consultorio Vencido (demo)', 'demo-vencido',
        'Calle Falsa 123, CABA', '+5491100000020', 'VENCIDO', DATE '2026-01-31')
ON CONFLICT DO NOTHING;

-- Misma contraseña de desarrollo que la cuenta de demo (README). Hash BCrypt de costo 12.
INSERT INTO usuario (id, consultorio_id, email, password_hash, nombre, apellido, password_cambiado_at,
                     terminos_version, terminos_aceptados_at)
VALUES ('01936b2e-0000-7000-8000-00000000a002', '01936b2e-0000-7000-8000-00000000a001', 'vencido@consultorio.test',
        '$2a$12$Je9J94jl173aVqzC54JYnuC8mVQUCzSaVLWSGd.4XyHJq81ye6Kie', 'Beto', 'Vencido', now(), '2026-11-borrador',
        now())
ON CONFLICT DO NOTHING;

INSERT INTO tipo_prestacion (id, consultorio_id, nombre, duracion_min, orden)
VALUES ('01936b2e-0000-7000-8000-00000000a011', '01936b2e-0000-7000-8000-00000000a001', 'Consulta', 30, 1),
       ('01936b2e-0000-7000-8000-00000000a012', '01936b2e-0000-7000-8000-00000000a001', 'Control', 20, 2)
ON CONFLICT DO NOTHING;

-- Lunes a viernes, de 09:00 a 13:00.
INSERT INTO disponibilidad (id, consultorio_id, dia_semana, hora_inicio, hora_fin)
SELECT ('01936b2e-0000-7000-8000-00000000a1' || lpad(dia::text, 2, '0'))::uuid, '01936b2e-0000-7000-8000-00000000a001',
       dia, TIME '09:00', TIME '13:00'
FROM generate_series(1, 5) AS dia
ON CONFLICT DO NOTHING;

INSERT INTO paciente (id, consultorio_id, nombre, apellido, dni, telefono_e164, email, origen)
VALUES ('01936b2e-0000-7000-8000-00000000a201', '01936b2e-0000-7000-8000-00000000a001', 'Paciente Vencido Uno',
        'Prueba', '90000011', '+5491100000011', 'paciente.vencido.uno@example.com', 'MANUAL'),
       ('01936b2e-0000-7000-8000-00000000a202', '01936b2e-0000-7000-8000-00000000a001', 'Paciente Vencido Dos',
        'Prueba', '90000012', '+5491100000012', NULL, 'MANUAL')
ON CONFLICT DO NOTHING;

-- Dos turnos de la semana en curso, fijos desde la primera carga (como en R__demo.sql).
WITH semana AS (
    SELECT date_trunc('week', now() AT TIME ZONE 'America/Argentina/Buenos_Aires') AS lunes
), datos (n, dia, hora, prestacion, duracion, paciente, estado) AS (
    VALUES (1, 1, TIME '09:00', 11, 30, 201, 'CONFIRMADO'),
           (2, 3, TIME '10:00', 12, 20, 202, 'RESERVADO')
)
INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min, estado, es_sobreturno,
                   origen)
SELECT ('01936b2e-0000-7000-8000-00000000a3' || lpad(d.n::text, 2, '0'))::uuid,
       '01936b2e-0000-7000-8000-00000000a001',
       ('01936b2e-0000-7000-8000-00000000a' || d.paciente)::uuid,
       ('01936b2e-0000-7000-8000-00000000a0' || d.prestacion)::uuid,
       (s.lunes + d.dia * interval '1 day' + d.hora) AT TIME ZONE 'America/Argentina/Buenos_Aires',
       (s.lunes + d.dia * interval '1 day' + d.hora + make_interval(mins => d.duracion))
           AT TIME ZONE 'America/Argentina/Buenos_Aires',
       d.duracion, d.estado, false, 'MANUAL'
FROM datos d CROSS JOIN semana s
ON CONFLICT DO NOTHING;
