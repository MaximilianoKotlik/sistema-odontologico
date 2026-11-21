-- C-10 agenda-esquema-dominio: pacientes, turnos y bloqueos.
-- knowledge-base/04_modelo_de_datos.md §Paciente, §Turno y §Bloqueo. Convenciones: db/migration/CONVENCIONES.md

-- Paciente: identidad por DNI dentro del consultorio (DD-24). Datos mínimos, sin datos clínicos (Ley 25.326).
CREATE TABLE paciente (
    id                      UUID         NOT NULL,           -- UUIDv7 generado en la aplicación
    consultorio_id          UUID         NOT NULL,
    nombre                  VARCHAR(80)  NOT NULL,
    apellido                VARCHAR(80)  NOT NULL,
    dni                     VARCHAR(8)   NOT NULL,           -- solo dígitos, normalizado por el value object Dni
    telefono_e164           VARCHAR(16)  NOT NULL,           -- contacto obligatorio (DD-32), no es único
    email                   CITEXT,
    origen                  VARCHAR(10)  NOT NULL,
    requiere_revision       BOOLEAN      NOT NULL DEFAULT false,
    consentimiento_at       TIMESTAMPTZ,
    -- 40 como usuario.terminos_version: la versión vigente ("2026-11-borrador") no entra en los 10 de 04.
    consentimiento_version  VARCHAR(40),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_paciente PRIMARY KEY (id),
    CONSTRAINT uk_paciente_consultorio_id UNIQUE (consultorio_id, id),
    CONSTRAINT fk_paciente_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorio (id),
    -- Clave de deduplicación (DD-24); también sirve para buscar por DNI dentro del consultorio.
    CONSTRAINT uk_paciente_dni UNIQUE (consultorio_id, dni),
    CONSTRAINT ck_paciente_dni CHECK (dni ~ '^[0-9]{7,8}$'),
    CONSTRAINT ck_paciente_origen CHECK (origen IN ('LINK', 'MANUAL')),
    -- Quien reserva por el link acepta la política de privacidad (LG-03).
    CONSTRAINT ck_paciente_consentimiento CHECK (
        origen <> 'LINK' OR (consentimiento_at IS NOT NULL AND consentimiento_version IS NOT NULL))
);

CREATE INDEX ix_paciente_consultorio_apellido ON paciente (consultorio_id, lower(apellido), lower(nombre));

-- RN-AG-07: un paciente no se borra.
REVOKE DELETE ON paciente FROM "${app-user}";

CREATE TABLE turno (
    id                  UUID         NOT NULL,
    consultorio_id      UUID         NOT NULL,
    paciente_id         UUID         NOT NULL,
    tipo_prestacion_id  UUID         NOT NULL,
    inicio              TIMESTAMPTZ  NOT NULL,
    -- Siempre inicio + duracion_min (RN-AG-12). Columna materializada con CHECK y no generada: timestamptz + interval
    -- es STABLE y Postgres no la acepta en GENERATED ALWAYS AS. Tenerla permite indexar el rango (design D8).
    fin                 TIMESTAMPTZ  NOT NULL,
    duracion_min        INT          NOT NULL,               -- copia de la prestación al crear (RN-AG-02)
    estado              VARCHAR(12)  NOT NULL,
    es_sobreturno       BOOLEAN      NOT NULL DEFAULT false, -- marca, no estado (DD-10)
    origen              VARCHAR(10)  NOT NULL,
    cancelado_por       VARCHAR(12),
    motivo_cancelacion  VARCHAR(200),
    nota_interna        VARCHAR(500),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_turno PRIMARY KEY (id),
    CONSTRAINT uk_turno_consultorio_id UNIQUE (consultorio_id, id),
    CONSTRAINT fk_turno_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorio (id),
    CONSTRAINT fk_turno_paciente FOREIGN KEY (consultorio_id, paciente_id) REFERENCES paciente (consultorio_id, id),
    CONSTRAINT fk_turno_tipo_prestacion FOREIGN KEY (consultorio_id, tipo_prestacion_id)
        REFERENCES tipo_prestacion (consultorio_id, id),
    CONSTRAINT ck_turno_duracion CHECK (duracion_min BETWEEN 5 AND 480 AND duracion_min % 5 = 0),
    -- Epochs: no dependen de la zona de la sesión.
    CONSTRAINT ck_turno_fin CHECK (extract(epoch FROM fin) - extract(epoch FROM inicio) = duracion_min * 60),
    -- RN-AG-08 en numeric, sin redondear: un inicio a las 09:00:00.6 no pasa (revisión de Maxi en #18). La grilla en
    -- UTC equivale a la local en toda zona con offset múltiplo de 5 minutos (todas las vigentes).
    CONSTRAINT ck_turno_grilla CHECK (extract(epoch FROM inicio) % 300 = 0),
    CONSTRAINT ck_turno_estado CHECK (estado IN ('RESERVADO', 'CONFIRMADO', 'CANCELADO', 'AUSENTE')),
    CONSTRAINT ck_turno_origen CHECK (origen IN ('LINK', 'MANUAL')),
    -- RN-AG-04: el sobreturno lo crea solo el odontólogo.
    CONSTRAINT ck_turno_sobreturno_manual CHECK (NOT es_sobreturno OR origen = 'MANUAL'),
    -- Quién canceló existe solo en un turno cancelado, y el motivo solo si está cancelado.
    CONSTRAINT ck_turno_cancelacion CHECK (
        (estado = 'CANCELADO') = (cancelado_por IS NOT NULL)
        AND (cancelado_por IS NULL OR cancelado_por IN ('ODONTOLOGO', 'PACIENTE'))
        AND (motivo_cancelacion IS NULL OR estado = 'CANCELADO')),
    -- RN-AG-01 / DD-12: dos turnos activos que no son sobreturno no se solapan, aunque se saltee la aplicación.
    -- Es turno_sin_solapamiento de 04 con el prefijo de la convención. btree_gist (V1) da el = sobre uuid.
    CONSTRAINT ex_turno_sin_solapamiento EXCLUDE USING gist (
        consultorio_id WITH =,
        tstzrange(inicio, fin, '[)') WITH &&)
        WHERE (NOT es_sobreturno AND estado IN ('RESERVADO', 'CONFIRMADO'))
);

CREATE INDEX ix_turno_consultorio_inicio ON turno (consultorio_id, inicio);
CREATE INDEX ix_turno_consultorio_paciente_inicio ON turno (consultorio_id, paciente_id, inicio);

-- RN-AG-07: un turno se cancela, no se borra.
REVOKE DELETE ON turno FROM "${app-user}";

CREATE TABLE bloqueo (
    id              UUID         NOT NULL,
    consultorio_id  UUID         NOT NULL,
    inicio          TIMESTAMPTZ  NOT NULL,
    fin             TIMESTAMPTZ  NOT NULL,
    motivo          VARCHAR(120),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_bloqueo PRIMARY KEY (id),
    CONSTRAINT uk_bloqueo_consultorio_id UNIQUE (consultorio_id, id),
    CONSTRAINT fk_bloqueo_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorio (id),
    CONSTRAINT ck_bloqueo_rango CHECK (fin > inicio),
    CONSTRAINT ck_bloqueo_grilla CHECK (extract(epoch FROM inicio) % 300 = 0 AND extract(epoch FROM fin) % 300 = 0)
);

-- Bloqueos superpuestos permitidos (design D10, aprobado en #18): índice, no exclusión.
CREATE INDEX ix_bloqueo_rango ON bloqueo USING gist (consultorio_id, tstzrange(inicio, fin, '[)'));
-- bloqueo conserva DELETE: C-11 elimina bloqueos con el evento BLOQUEO_ELIMINADO.
