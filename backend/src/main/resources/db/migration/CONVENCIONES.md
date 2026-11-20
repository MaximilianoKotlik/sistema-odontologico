# Convenciones de migraciones

Fuente: `knowledge-base/04_modelo_de_datos.md` §Convenciones y §Aislamiento por tenant, y C-02 (`design.md` D7).
Toda migración nueva las cumple; quien revisa el PR las verifica.

## Archivos

- Nombre `V{n}__{descripcion}.sql`, en SQL plano. Hasta que se mergea, el número es provisorio: al rebasar sobre `main` se pasa al siguiente libre. **Una migración mergeada no se edita nunca**: cualquier ajuste va en una nueva.
- `ddl-auto=validate`: el esquema lo define solo Flyway.

## Tablas de negocio (toda tabla salvo `consultorio`)

```sql
CREATE TABLE ejemplo (
    id              UUID        NOT NULL,           -- UUIDv7 generado en la aplicación
    consultorio_id  UUID        NOT NULL,
    padre_id        UUID        NOT NULL,           -- referencia a otra tabla de negocio
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),  -- solo si es mutable

    CONSTRAINT pk_ejemplo PRIMARY KEY (id),
    -- Destino de las FKs compuestas que apunten a esta tabla.
    CONSTRAINT uk_ejemplo_consultorio_id UNIQUE (consultorio_id, id),
    CONSTRAINT fk_ejemplo_consultorio FOREIGN KEY (consultorio_id) REFERENCES consultorio (id),
    -- FK compuesta: la base impide apuntar a un registro de otro consultorio.
    CONSTRAINT fk_ejemplo_padre FOREIGN KEY (consultorio_id, padre_id) REFERENCES padre (consultorio_id, id)
);

-- Todo índice de consulta empieza por consultorio_id.
CREATE INDEX ix_ejemplo_consultorio_created ON ejemplo (consultorio_id, created_at);
```

Reglas:

1. `consultorio_id UUID NOT NULL` con FK a `consultorio(id)`.
2. `UNIQUE (consultorio_id, id)` en toda tabla que pueda ser destino de una FK.
3. Las referencias entre tablas de negocio son **compuestas**: `(consultorio_id, x_id) → x (consultorio_id, id)`. Nunca una FK simple a `x(id)`.
4. Los índices de consulta empiezan por `consultorio_id`.
5. Instantes en `TIMESTAMPTZ` (UTC). Montos en `NUMERIC`. Enums como `VARCHAR` + `CHECK`.
6. Las constraints llevan nombre (`pk_`, `uk_`, `fk_`, `ck_`, `ix_`): el manejador de errores mapea algunas por nombre (por ejemplo, `uk_consultorio_slug` → `SLUG_EN_USO`).
7. Los secretos se guardan solo como hash.

## Permisos

Los privilegios por defecto de C-01 (`docker/postgres/init/01-roles.sh`) le dan a `app_rw` `SELECT, INSERT, UPDATE, DELETE` sobre **toda** tabla nueva de `app_owner`. Si una tabla no debe permitir alguna operación, la migración la quita explícitamente con el placeholder del rol de la aplicación, y un test de integración lo verifica:

```sql
REVOKE DELETE ON consultorio FROM "${app-user}";
```

Un `GRANT` acotado no alcanza: no quita lo que ya dieron los privilegios por defecto.

## Tests

Cada tabla nueva lleva:

- un test de integración (extiende `IntegracionPostgresTest`) que verifique sus constraints y permisos;
- un test de aislamiento en cada endpoint que la use: otro consultorio responde 404 (regla dura 6). El modelo está en `CompartidoAislamientoTest`.
