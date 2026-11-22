-- Búsqueda de pacientes sin distinguir tildes: "perez" encuentra a "Pérez" (sugerencia de Maxi en #48).
-- unaccent es una extensión confiable desde PostgreSQL 13: la crea el dueño del esquema, como btree_gist y citext en V1.
CREATE EXTENSION IF NOT EXISTS unaccent;
