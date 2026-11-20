-- C-03 auditoria-base: registro de auditoría append-only (RN-AD-01..03).
-- knowledge-base/04_modelo_de_datos.md §EventoAuditoria. Convenciones: db/migration/CONVENCIONES.md

CREATE TABLE evento_auditoria (
    id              UUID         NOT NULL,           -- UUIDv7 generado en la aplicación
    consultorio_id  UUID         NOT NULL,
    -- Lo fija la aplicación con el Clock (design D-4); el DEFAULT es solo una red.
    ocurrido_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_tipo      VARCHAR(12)  NOT NULL,
    actor_id        UUID,
    entidad         VARCHAR(30)  NOT NULL,
    entidad_id      UUID         NOT NULL,
    accion          VARCHAR(40)  NOT NULL,
    valor_anterior  JSONB,
    valor_nuevo     JSONB,
    ip              INET,

    CONSTRAINT pk_evento_auditoria PRIMARY KEY (id),
    CONSTRAINT uk_evento_auditoria_consultorio_id UNIQUE (consultorio_id, id),
    -- Sin ON DELETE CASCADE: el trigger rechazaría el borrado en cascada (design D-5, PA-07).
    CONSTRAINT fk_evento_auditoria_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorio (id),
    CONSTRAINT ck_evento_auditoria_actor_tipo CHECK (actor_tipo IN ('ODONTOLOGO', 'PACIENTE', 'SISTEMA'))
);

CREATE INDEX ix_evento_auditoria_consultorio_ocurrido ON evento_auditoria (consultorio_id, ocurrido_at DESC);
CREATE INDEX ix_evento_auditoria_consultorio_entidad ON evento_auditoria (consultorio_id, entidad, entidad_id);

-- Append-only en la base (RN-AD-02, design D-1), en dos capas.
-- 1) Trigger: frena UPDATE, DELETE y TRUNCATE aun a un rol con todos los privilegios. TRUNCATE no dispara
--    triggers de fila, por eso lleva uno de sentencia.
CREATE FUNCTION evento_auditoria_inmutable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'evento_auditoria es append-only: % no permitido', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER tg_evento_auditoria_inmutable_fila
    BEFORE UPDATE OR DELETE ON evento_auditoria
    FOR EACH ROW EXECUTE FUNCTION evento_auditoria_inmutable();

CREATE TRIGGER tg_evento_auditoria_inmutable_sentencia
    BEFORE TRUNCATE ON evento_auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION evento_auditoria_inmutable();

-- 2) Permisos: los privilegios por defecto de C-01 le dan UPDATE y DELETE al rol de la aplicación sobre toda
--    tabla nueva; se quitan explícitamente.
REVOKE UPDATE, DELETE, TRUNCATE ON evento_auditoria FROM "${app-user}";
GRANT INSERT, SELECT ON evento_auditoria TO "${app-user}";
