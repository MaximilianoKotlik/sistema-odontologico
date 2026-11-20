-- C-04 auth-sesion-tokens: cuenta del odontólogo y refresh tokens (design D12).
-- Número provisorio: se pasa al siguiente libre al rebasar sobre main (CHANGES.md).

-- knowledge-base/04_modelo_de_datos.md §Usuario. Un consultorio tiene un solo usuario en v1 (DD-08).
CREATE TABLE usuario (
    id                     UUID         NOT NULL,
    consultorio_id         UUID         NOT NULL,
    email                  CITEXT       NOT NULL,
    password_hash          VARCHAR(100) NOT NULL,
    nombre                 VARCHAR(80)  NOT NULL,
    apellido               VARCHAR(80)  NOT NULL,
    matricula              VARCHAR(30),
    intentos_fallidos      INT          NOT NULL DEFAULT 0,
    bloqueado_hasta        TIMESTAMPTZ,
    password_cambiado_at   TIMESTAMPTZ  NOT NULL,
    -- Claim "ver" del access token; C-05 la incrementa al cambiar o recuperar la contraseña (design D3).
    credenciales_version   INT          NOT NULL DEFAULT 1,
    -- Aceptación de términos versionada sin texto definitivo (design D7, DD-23, PA-06).
    terminos_version       VARCHAR(40)  NOT NULL,
    terminos_aceptados_at  TIMESTAMPTZ  NOT NULL,
    version                BIGINT       NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT uk_usuario_consultorio UNIQUE (consultorio_id),
    CONSTRAINT uk_usuario_consultorio_id UNIQUE (consultorio_id, id),
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT fk_usuario_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorio (id),
    CONSTRAINT ck_usuario_intentos_fallidos CHECK (intentos_fallidos >= 0),
    CONSTRAINT ck_usuario_credenciales_version CHECK (credenciales_version >= 1)
);

-- Solo se guarda el SHA-256 del valor que viaja en la cookie (design D4).
CREATE TABLE refresh_token (
    id              UUID        NOT NULL,
    consultorio_id  UUID        NOT NULL,
    usuario_id      UUID        NOT NULL,
    token_hash      CHAR(64)    NOT NULL,
    expira_at       TIMESTAMPTZ NOT NULL,
    revocado_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorio (id),
    CONSTRAINT fk_refresh_token_usuario
        FOREIGN KEY (consultorio_id, usuario_id) REFERENCES usuario (consultorio_id, id)
);

-- Para revocar la familia completa de un usuario (detección de reuso).
CREATE INDEX ix_refresh_token_usuario_vigentes ON refresh_token (usuario_id) WHERE revocado_at IS NULL;

-- Los privilegios por defecto de C-01 le dan DELETE a app_rw: cuentas y tokens se revocan, no se borran.
REVOKE DELETE ON usuario, refresh_token FROM "${app-user}";
