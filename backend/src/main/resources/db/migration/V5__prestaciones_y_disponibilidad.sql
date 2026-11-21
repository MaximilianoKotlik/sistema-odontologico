-- C-08 consultorio-config-backend: tipos de prestación y franjas semanales de atención.
-- knowledge-base/04_modelo_de_datos.md §TipoDePrestacion y §Disponibilidad. Convenciones: db/migration/CONVENCIONES.md

CREATE TABLE tipo_prestacion (
    id               UUID        NOT NULL,           -- UUIDv7 generado en la aplicación
    consultorio_id   UUID        NOT NULL,
    nombre           VARCHAR(80) NOT NULL,
    duracion_min     INT         NOT NULL,
    activa           BOOLEAN     NOT NULL DEFAULT true,
    visible_publica  BOOLEAN     NOT NULL DEFAULT true,
    orden            INT         NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_tipo_prestacion PRIMARY KEY (id),
    -- Destino de la FK compuesta de turno (C-10).
    CONSTRAINT uk_tipo_prestacion_consultorio_id UNIQUE (consultorio_id, id),
    CONSTRAINT fk_tipo_prestacion_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorio (id),
    -- RN-AG-08: de 5 a 480 minutos, en múltiplos de 5.
    CONSTRAINT ck_tipo_prestacion_duracion CHECK (duracion_min BETWEEN 5 AND 480 AND duracion_min % 5 = 0)
);

-- Nombre único por consultorio sin distinguir mayúsculas (design D8). Sirve también para el listado por consultorio.
CREATE UNIQUE INDEX uk_tipo_prestacion_nombre ON tipo_prestacion (consultorio_id, lower(nombre));

-- RN-AG-07: una prestación no se borra, se desactiva.
REVOKE DELETE ON tipo_prestacion FROM "${app-user}";

CREATE TABLE disponibilidad (
    id              UUID        NOT NULL,
    consultorio_id  UUID        NOT NULL,
    dia_semana      SMALLINT    NOT NULL,            -- ISO: 1 = lunes … 7 = domingo
    hora_inicio     TIME        NOT NULL,            -- local a la zona del consultorio (RN-TE-05)
    hora_fin        TIME        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_disponibilidad PRIMARY KEY (id),
    CONSTRAINT uk_disponibilidad_consultorio_id UNIQUE (consultorio_id, id),
    CONSTRAINT fk_disponibilidad_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorio (id),
    CONSTRAINT ck_disponibilidad_dia CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT ck_disponibilidad_horas CHECK (hora_fin > hora_inicio),
    -- Extiende RN-AG-08 a las franjas (design D5): sin segundos y en minutos múltiplos de 5.
    CONSTRAINT ck_disponibilidad_multiplo_5 CHECK (
        EXTRACT(SECOND FROM hora_inicio) = 0 AND EXTRACT(MINUTE FROM hora_inicio)::int % 5 = 0
        AND EXTRACT(SECOND FROM hora_fin) = 0 AND EXTRACT(MINUTE FROM hora_fin)::int % 5 = 0),
    -- Red de seguridad de la validación de la aplicación (design D5): dos franjas del mismo día no se solapan.
    -- date + time es inmutable; la fecha ancla da igual porque solo se comparan horas del mismo día. [) deja
    -- coexistir franjas contiguas. btree_gist (V1) da el = sobre uuid y smallint.
    CONSTRAINT ex_disponibilidad_sin_solape EXCLUDE USING gist (
        consultorio_id WITH =,
        dia_semana WITH =,
        tsrange(DATE '2000-01-03' + hora_inicio, DATE '2000-01-03' + hora_fin, '[)') WITH &&)
);

-- La exclusión ya crea un índice GiST que empieza por consultorio_id; este sirve para leer la grilla ordenada.
CREATE INDEX ix_disponibilidad_consultorio_dia ON disponibilidad (consultorio_id, dia_semana, hora_inicio);
-- disponibilidad conserva DELETE: el reemplazo completo de la grilla lo necesita (design D12).
