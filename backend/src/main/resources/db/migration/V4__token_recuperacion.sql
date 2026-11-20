-- C-05 auth-recuperacion-hardening: tokens de recuperación de contraseña (design D3, RN-AU-04).
-- Convenciones: db/migration/CONVENCIONES.md

-- Solo se guarda el SHA-256 del token que viaja en el link del email. Un token vale 30 minutos y una sola vez;
-- emitir uno nuevo revoca los anteriores sin usar del usuario.
CREATE TABLE token_recuperacion (
    id              UUID        NOT NULL,
    consultorio_id  UUID        NOT NULL,
    usuario_id      UUID        NOT NULL,
    token_hash      CHAR(64)    NOT NULL,
    expira_at       TIMESTAMPTZ NOT NULL,
    usado_at        TIMESTAMPTZ,
    revocado_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_token_recuperacion PRIMARY KEY (id),
    CONSTRAINT uk_token_recuperacion_hash UNIQUE (token_hash),
    CONSTRAINT fk_token_recuperacion_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorio (id),
    CONSTRAINT fk_token_recuperacion_usuario
        FOREIGN KEY (consultorio_id, usuario_id) REFERENCES usuario (consultorio_id, id)
);

-- Para revocar los tokens vigentes de un usuario al emitir uno nuevo. Empieza por consultorio_id como todo índice
-- de consulta (CONVENCIONES.md, regla 4).
CREATE INDEX ix_token_recuperacion_vigentes ON token_recuperacion (consultorio_id, usuario_id)
    WHERE usado_at IS NULL AND revocado_at IS NULL;

-- Los privilegios por defecto de C-01 le dan DELETE y TRUNCATE a la aplicación: los tokens se usan o se revocan,
-- no se borran.
REVOKE DELETE, TRUNCATE ON token_recuperacion FROM "${app-user}";
