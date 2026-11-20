-- C-02 core-compartido-tenant: extensiones y tabla raíz del tenant.
-- Convenciones para las tablas siguientes: db/migration/CONVENCIONES.md

-- Extensiones confiables en PG16: las crea app_owner, dueño de la base y del esquema.
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS citext;

-- knowledge-base/04_modelo_de_datos.md §Consultorio. El id (UUIDv7) lo genera la aplicación.
CREATE TABLE consultorio (
    id                            UUID         NOT NULL,
    nombre                        VARCHAR(120) NOT NULL,
    slug                          VARCHAR(40)  NOT NULL,
    direccion                     VARCHAR(200),
    telefono_contacto             VARCHAR(20),
    zona_horaria                  VARCHAR(50)  NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    anticipacion_cancelacion_min  INT          NOT NULL DEFAULT 1440,
    anticipacion_reserva_min      INT          NOT NULL DEFAULT 120,
    horizonte_reserva_dias        INT          NOT NULL DEFAULT 60,
    paso_huecos_min               INT          NOT NULL DEFAULT 15,
    reserva_publica_activa        BOOLEAN      NOT NULL DEFAULT true,
    plan                          VARCHAR(10)  NOT NULL,
    prueba_hasta                  DATE,
    version                       BIGINT       NOT NULL DEFAULT 0,
    created_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_consultorio PRIMARY KEY (id),
    CONSTRAINT uk_consultorio_slug UNIQUE (slug),
    -- RN-TE-03: 3 a 40 caracteres [a-z0-9-], sin guion al inicio o al final ni guiones dobles.
    -- Las palabras reservadas se validan en la aplicación (PalabrasReservadas).
    CONSTRAINT ck_consultorio_slug
        CHECK (slug ~ '^([a-z0-9])(-?[a-z0-9])+$' AND char_length(slug) BETWEEN 3 AND 40),
    CONSTRAINT ck_consultorio_anticipacion_cancelacion
        CHECK (anticipacion_cancelacion_min BETWEEN 0 AND 10080),
    CONSTRAINT ck_consultorio_anticipacion_reserva
        CHECK (anticipacion_reserva_min >= 0),
    CONSTRAINT ck_consultorio_horizonte_reserva
        CHECK (horizonte_reserva_dias > 0),
    CONSTRAINT ck_consultorio_paso_huecos
        CHECK (paso_huecos_min IN (5, 10, 15, 20, 30)),
    CONSTRAINT ck_consultorio_plan
        CHECK (plan IN ('PRUEBA', 'ACTIVO', 'VENCIDO'))
);

-- Los privilegios por defecto de C-01 le dan DELETE a app_rw; la baja de consultorio no existe en v1.
-- ${app_user} = DB_APP_USER (placeholder de Flyway en application.yml).
REVOKE DELETE ON consultorio FROM "${app_user}";
