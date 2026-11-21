-- C-15 reserva-publica-backend: token de gestión del paciente y marca de novedad para el odontólogo.
-- knowledge-base/04_modelo_de_datos.md §TokenGestionTurno. Convenciones: db/migration/CONVENCIONES.md

-- DD-30 (design D14): instante del último cambio hecho por el paciente que el odontólogo todavía no vio.
-- La escribe C-15 y la limpia C-12 con POST /api/v1/turnos/{id}/visto.
ALTER TABLE turno ADD COLUMN novedad_paciente_at TIMESTAMPTZ;

-- Sin expira_at: la vigencia se deriva del turno en cada consulta (design D3).
CREATE TABLE token_gestion_turno (
    id              UUID         NOT NULL,           -- UUIDv7 generado en la aplicación
    consultorio_id  UUID         NOT NULL,
    turno_id        UUID         NOT NULL,
    token_hash      CHAR(64)     NOT NULL,           -- SHA-256 en hexadecimal; el token en claro no se guarda
    revocado_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_token_gestion_turno PRIMARY KEY (id),
    CONSTRAINT uk_token_gestion_turno_consultorio_id UNIQUE (consultorio_id, id),
    CONSTRAINT fk_token_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorio (id),
    CONSTRAINT fk_token_turno FOREIGN KEY (consultorio_id, turno_id) REFERENCES turno (consultorio_id, id),
    CONSTRAINT ux_token_hash UNIQUE (token_hash),
    CONSTRAINT ux_token_turno UNIQUE (turno_id),
    CONSTRAINT ck_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

-- Un token se revoca, no se borra (design D3).
REVOKE DELETE ON token_gestion_turno FROM "${app-user}";
