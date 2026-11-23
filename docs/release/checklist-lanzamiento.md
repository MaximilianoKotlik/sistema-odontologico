# Checklist de lanzamiento de la v1

> C-23 (`e2e-hardening-release`, design D9). Hasta completar este checklist **no se cargan pacientes reales ni se
> publica `/seguridad`** (DD-40). El checklist no bloquea el cierre de C-23: se resuelve después, fuera del change.
> Cada punto tiene un dueño y una casilla. La evidencia técnica de lo que ya está hecho vive en
> [revision-seguridad.md](revision-seguridad.md).

## Requisitos de DD-40 (bloquean el lanzamiento)

- [ ] **1. Proveedor del VPS** elegido, con los datos en Argentina. Dueño: Maxi.
- [ ] **2. Backups** según la política de DD-40: uno completo de Postgres por día, cifrado, guardado fuera del VPS y
      también en Argentina, con retención de 30 días y **una restauración de prueba** antes del lanzamiento (después,
      una por mes). Dueño: Maxi.
- [ ] **3. TLS** activo en el dominio de producción, con HSTS (el backend lo manda fuera del perfil `local`). Dueño:
      Maxi.
- [ ] **4. Proveedor de email (SMTP)** elegido y declarado en la política de privacidad. Si está fuera de Argentina,
      documentar la transferencia internacional del email del paciente (LG-09, DD-26). Dueño: Maxi.
- [ ] **5. Email de seguridad** de `/seguridad`, ligado al dominio (DD-34, #40). Reemplaza el `(TBD)` de
      `frontend/src/contenido/seguridad.json`. Dueño: Maxi.

## Textos legales (DD-28)

- [ ] Revisión de un abogado especializado en datos personales y salud de `docs/legal/terminos-y-condiciones.md`,
      `docs/legal/politica-de-privacidad.md` y `/seguridad`. Dueño: Maxi.
- [ ] Completar en la política de privacidad el `[COMPLETAR: backups definidos (PA-02)]` con la política de DD-40.
      Dueño: Maxi, junto con la revisión.
- [ ] Publicar la versión revisada: cambiar `TERMINOS_VERSION_VIGENTE` y `PRIVACIDAD_VERSION_VIGENTE`, y sacar los
      avisos de borrador (`docs/legal/README.md` §checklist). Dueño: Maxi.

## `/seguridad`

- [ ] Filas 15, 16 y 17 de [revision-seguridad.md](revision-seguridad.md#página-seguridad-d10-rn-pl-06) resueltas: email
      de seguridad, VPS y backups en Argentina ya contratados (la página lo afirma en presente) y proveedores nombrados.
      Dueño: Maxi.
- [ ] `cd frontend && npm run seguridad:revisar` pasa sin errores. Dueño: Maxi.

## Despliegue

- [ ] Reverse proxy delante del backend, con TLS. Dueño: Maxi.
- [ ] `FORWARD_HEADERS_STRATEGY=native` en el backend y, si el proxy no está en una red privada,
      `server.tomcat.remoteip.internal-proxies` con su IP (`backend/README.md` §IP de origen detrás de un proxy).
      Verificar que dos IPs distintas no comparten el cupo de login. Dueño: Maxi.
- [ ] Secretos de producción generados para el VPS y fuera del repositorio: `JWT_SECRET` (`openssl rand -base64 48`),
      credenciales de `app_owner` y `app_rw`, `POSTGRES_PASSWORD` y SMTP. Dueño: Maxi.
- [ ] `APP_FRONTEND_ORIGIN` con el dominio de producción y `SPRING_PROFILES_ACTIVE` sin `local` (el seed de demo no
      se carga, `SeedLocalTest`). Dueño: Maxi.
- [ ] Servir el **build** del frontend (`npm run build`, imagen con `frontend/nginx.conf`) en lugar del dev server, y
      repetir el humo contra él: `HUMO_BASE_URL=https://<dominio> npm run humo` con una cuenta de prueba. Dueño: Maxi.
- [ ] Tarea diaria de vencimiento de pruebas activa (`PLAN_VENCIMIENTO_CRON`). Dueño: Maxi.

## Valores por defecto

- [ ] Validar con odontólogos los valores por defecto del consultorio: anticipación de reserva (2 h) y de
      cancelación (24 h), horizonte de reserva, paso de los huecos y duraciones de las prestaciones. Dueño: Maxi.

## Métricas del piloto

Cómo se miden las métricas de `knowledge-base/01_vision_y_objetivos.md` §Métricas de éxito, con consultas sobre la
base de producción (solo conteos, sin datos de pacientes):

- [ ] **Reservas por link**: `turno` con `origen = 'LINK'` sobre el total, por consultorio, a 60 días.
- [ ] **Activación**: consultorios con disponibilidad y al menos 1 prestación activa en su primera semana, y con al
      menos una reserva por link (proxy de "compartió el link").
- [ ] **Retención**: consultorios con al menos 1 turno cargado por semana durante 4 semanas seguidas.
- [ ] **Conversión de prueba**: eventos `PLAN_ACTIVADO` sobre consultorios registrados.
- [ ] **Solapamientos indebidos**: 0, garantizado por la restricción `EXCLUDE` de la base.
- [ ] **Tiempo de reserva**: sin medición en v1 (no hay analítica, DD-16). Se mide en el piloto acompañando a
      pacientes reales, o se agrega un registro anónimo en otro change.

Dueño de las métricas: Maxi. Las consultas se escriben y se prueban antes del lanzamiento.

## Protección de `main`

- [ ] Sumar `E2E (humo)` a los checks requeridos de la protección de `main` en GitHub (tarea 6.4 de C-23). Dueño:
      Maxi.
