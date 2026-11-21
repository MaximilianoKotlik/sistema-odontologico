-- C-12 agenda-api-odontologo (design D5; C-15 D14, DD-30): marca de un cambio del paciente que el odontólogo no vio.
-- La escribe C-15 al reservar, reprogramar o cancelar por el link, y la limpia POST /api/v1/turnos/{id}/visto.
-- Es una marca de lectura de la interfaz, no un dato del turno: limpiarla no genera evento (DD-39).
-- IF NOT EXISTS: la crea el primero que se mergee entre C-12 y C-15, y el otro no la duplica (decisión de Maxi en #30).
ALTER TABLE turno ADD COLUMN IF NOT EXISTS novedad_paciente_at TIMESTAMPTZ;
