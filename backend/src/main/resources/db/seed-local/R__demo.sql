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

-- C-23 (design D8): lo que falta para recorrer toda la v1 con la demo. Un turno AUSENTE, una reserva por link con
-- datos distintos de la ficha (paciente para revisar, DD-38), un turno reprogramado por el paciente con la novedad
-- sin ver, un turno con un link de gestión que se abre en local y los eventos de auditoría de esos cambios.
-- Las bases cargadas antes de este cambio reciben estos turnos en la semana actual: recreá el volumen (README).

-- Paciente Seis reservó por el link con otro nombre y otro email: queda para revisar con lo que informó.
INSERT INTO paciente (id, consultorio_id, nombre, apellido, dni, telefono_e164, email, origen, consentimiento_at,
                      consentimiento_version, requiere_revision, nombre_informado, apellido_informado,
                      email_informado, informado_at)
VALUES ('01936b2e-0000-7000-8000-000000000206', '01936b2e-0000-7000-8000-000000000001', 'Paciente Seis', 'Prueba',
        '90000006', '+5491100000006', 'paciente.seis@example.com', 'LINK', now() - interval '1 hour',
        '2026-11-borrador', true, 'Paciente Seys', 'Prueba', 'otro.seis@example.com', now() - interval '1 hour')
ON CONFLICT DO NOTHING;

WITH semana AS (
    SELECT date_trunc('week', now() AT TIME ZONE 'America/Argentina/Buenos_Aires') AS lunes
), datos (n, dia, hora, prestacion, duracion, paciente, estado, origen, novedad) AS (
    VALUES (11, 0, TIME '11:00', 11, 30, 203, 'AUSENTE', 'MANUAL', false),
           -- La reserva que dejó a Paciente Seis para revisar.
           (12, 3, TIME '09:00', 11, 30, 206, 'RESERVADO', 'LINK', false),
           -- El turno del link de gestión de demo (README).
           (13, 4, TIME '11:00', 14, 20, 202, 'RESERVADO', 'LINK', false),
           -- Reprogramado por el paciente desde el link (martes 11:30 a miércoles 11:00): novedad sin ver.
           (14, 2, TIME '11:00', 12, 45, 201, 'RESERVADO', 'LINK', true)
)
INSERT INTO turno (id, consultorio_id, paciente_id, tipo_prestacion_id, inicio, fin, duracion_min, estado, es_sobreturno,
                   origen, cancelado_por, novedad_paciente_at)
SELECT ('01936b2e-0000-7000-8000-000000000' || (300 + d.n))::uuid,
       '01936b2e-0000-7000-8000-000000000001',
       ('01936b2e-0000-7000-8000-000000000' || d.paciente)::uuid,
       ('01936b2e-0000-7000-8000-0000000000' || d.prestacion)::uuid,
       (s.lunes + d.dia * interval '1 day' + d.hora) AT TIME ZONE 'America/Argentina/Buenos_Aires',
       (s.lunes + d.dia * interval '1 day' + d.hora + make_interval(mins => d.duracion))
           AT TIME ZONE 'America/Argentina/Buenos_Aires',
       d.duracion, d.estado, false, d.origen, NULL,
       CASE WHEN d.novedad THEN date_trunc('second', now() - interval '30 minutes') END
FROM datos d CROSS JOIN semana s
ON CONFLICT DO NOTHING;

-- El turno de origen de lo informado, como lo completa la reserva (vincularTurnoDeOrigen).
UPDATE paciente SET informado_turno_id = '01936b2e-0000-7000-8000-000000000312'
 WHERE id = '01936b2e-0000-7000-8000-000000000206' AND requiere_revision AND informado_turno_id IS NULL;

-- Link de gestión de demo, en claro en el README. Se guarda solo el SHA-256, como con un token real. Vale solo en local.
INSERT INTO token_gestion_turno (id, consultorio_id, turno_id, token_hash)
VALUES ('01936b2e-0000-7000-8000-000000000601', '01936b2e-0000-7000-8000-000000000001',
        '01936b2e-0000-7000-8000-000000000313',
        encode(sha256(convert_to('demo-gestion-de-turno-solo-local-0000000001', 'UTF8')), 'hex'))
ON CONFLICT DO NOTHING;

-- Eventos de auditoría de esos turnos, con el mismo formato que escribe la aplicación. Actor PACIENTE con el id del
-- paciente (C-18) y ODONTOLOGO con el id de la cuenta de demo.
WITH formato AS (
    SELECT t.id, t.paciente_id, t.tipo_prestacion_id, t.duracion_min, t.es_sobreturno,
           to_char(t.inicio AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS inicio,
           to_char(t.fin AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS fin,
           to_char((t.inicio - interval '23 hours 30 minutes') AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
               AS inicio_previo,
           to_char((t.fin - interval '23 hours 30 minutes') AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
               AS fin_previo
      FROM turno t
     WHERE t.consultorio_id = '01936b2e-0000-7000-8000-000000000001'
), eventos (n, turno, hace, actor_tipo, actor, accion, anterior, nuevo) AS (
    VALUES
        -- Turno 1: reservado por el link y confirmado por el odontólogo.
        (501, 301, interval '3 days', 'PACIENTE', 201, 'TURNO_CREADO', NULL, 'creado-link'),
        (502, 301, interval '2 days', 'ODONTOLOGO', NULL, 'TURNO_ESTADO_CAMBIADO', 'reservado', 'confirmado'),
        -- Turno 7: reservado y cancelado por el paciente desde el link.
        (503, 307, interval '3 days', 'PACIENTE', 202, 'TURNO_CREADO', NULL, 'creado-link'),
        (504, 307, interval '1 day', 'PACIENTE', 202, 'TURNO_ESTADO_CAMBIADO', 'reservado', 'cancelado-paciente'),
        -- Turno 11: cargado por el odontólogo y marcado ausente.
        (505, 311, interval '4 days', 'ODONTOLOGO', NULL, 'TURNO_CREADO', NULL, 'creado-manual'),
        (506, 311, interval '1 hour', 'ODONTOLOGO', NULL, 'TURNO_ESTADO_CAMBIADO', 'reservado', 'ausente'),
        -- Turnos 12 y 13: reservas por el link.
        (507, 312, interval '1 hour', 'PACIENTE', 206, 'TURNO_CREADO', NULL, 'creado-link'),
        (508, 313, interval '2 hours', 'PACIENTE', 202, 'TURNO_CREADO', NULL, 'creado-link'),
        -- Turno 14: reservado por el link y reprogramado por el paciente (la novedad sin ver).
        (509, 314, interval '2 days', 'PACIENTE', 201, 'TURNO_CREADO', NULL, 'creado-link-previo'),
        (510, 314, interval '30 minutes', 'PACIENTE', 201, 'TURNO_REPROGRAMADO', 'horario-previo', 'horario')
)
INSERT INTO evento_auditoria (id, consultorio_id, ocurrido_at, actor_tipo, actor_id, entidad, entidad_id, accion,
                              valor_anterior, valor_nuevo)
SELECT ('01936b2e-0000-7000-8000-000000000' || e.n)::uuid,
       '01936b2e-0000-7000-8000-000000000001',
       date_trunc('second', now() - e.hace),
       e.actor_tipo,
       CASE WHEN e.actor_tipo = 'PACIENTE' THEN ('01936b2e-0000-7000-8000-000000000' || e.actor)::uuid
            ELSE '01936b2e-0000-7000-8000-000000000002'::uuid END,
       'turno',
       f.id,
       e.accion,
       CASE e.anterior
           WHEN 'reservado' THEN jsonb_build_object('estado', 'RESERVADO', 'cancelado_por', NULL)
           WHEN 'horario-previo' THEN jsonb_build_object('inicio', f.inicio_previo, 'fin', f.fin_previo)
       END,
       CASE e.nuevo
           WHEN 'creado-link' THEN jsonb_build_object('inicio', f.inicio, 'fin', f.fin, 'duracion_min', f.duracion_min,
               'tipo_prestacion_id', f.tipo_prestacion_id, 'paciente_id', f.paciente_id, 'estado', 'RESERVADO',
               'origen', 'LINK', 'es_sobreturno', f.es_sobreturno)
           WHEN 'creado-link-previo' THEN jsonb_build_object('inicio', f.inicio_previo, 'fin', f.fin_previo,
               'duracion_min', f.duracion_min, 'tipo_prestacion_id', f.tipo_prestacion_id,
               'paciente_id', f.paciente_id, 'estado', 'RESERVADO', 'origen', 'LINK', 'es_sobreturno', f.es_sobreturno)
           WHEN 'creado-manual' THEN jsonb_build_object('inicio', f.inicio, 'fin', f.fin, 'duracion_min', f.duracion_min,
               'tipo_prestacion_id', f.tipo_prestacion_id, 'paciente_id', f.paciente_id, 'estado', 'RESERVADO',
               'origen', 'MANUAL', 'es_sobreturno', f.es_sobreturno)
           WHEN 'confirmado' THEN jsonb_build_object('estado', 'CONFIRMADO', 'cancelado_por', NULL)
           WHEN 'cancelado-paciente' THEN jsonb_build_object('estado', 'CANCELADO', 'cancelado_por', 'PACIENTE')
           WHEN 'ausente' THEN jsonb_build_object('estado', 'AUSENTE', 'cancelado_por', NULL)
           WHEN 'horario' THEN jsonb_build_object('inicio', f.inicio, 'fin', f.fin)
       END
FROM eventos e JOIN formato f ON f.id = ('01936b2e-0000-7000-8000-000000000' || e.turno)::uuid
ON CONFLICT DO NOTHING;
