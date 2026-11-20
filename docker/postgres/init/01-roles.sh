#!/bin/sh
# Crea los roles de la base (design D4). Corre una sola vez, al crear el volumen de datos.
#   app_owner (DB_OWNER_USER): dueño del esquema; lo usa solo Flyway.
#   app_rw    (DB_APP_USER):   rol de la aplicación; lee y escribe datos, no altera el esquema.
set -eu

: "${DB_OWNER_USER:?Falta DB_OWNER_USER}"
: "${DB_OWNER_PASSWORD:?Falta DB_OWNER_PASSWORD}"
: "${DB_APP_USER:?Falta DB_APP_USER}"
: "${DB_APP_PASSWORD:?Falta DB_APP_PASSWORD}"

psql -v ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  -v owner="$DB_OWNER_USER" \
  -v owner_pw="$DB_OWNER_PASSWORD" \
  -v app="$DB_APP_USER" \
  -v app_pw="$DB_APP_PASSWORD" \
  -v db="$POSTGRES_DB" <<'SQL'
CREATE ROLE :"owner" LOGIN PASSWORD :'owner_pw';
CREATE ROLE :"app" LOGIN PASSWORD :'app_pw';

ALTER DATABASE :"db" OWNER TO :"owner";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE :"db" TO :"app";

ALTER SCHEMA public OWNER TO :"owner";
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"app";

ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" IN SCHEMA public
  GRANT EXECUTE ON FUNCTIONS TO :"app";
SQL
