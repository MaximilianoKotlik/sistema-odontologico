# Agenda Odontológica SaaS (repo `sistema-odontologico`)

SaaS web para odontólogos independientes de Argentina. La v1 resuelve solo la agenda: agenda diaria y semanal, bloqueos, sobreturnos y reserva pública por link sin cuenta.

- Contexto del dominio: [knowledge-base/](knowledge-base/README.md)
- Plan de trabajo y responsables: [CHANGES.md](CHANGES.md)
- Instrucciones para agentes: [CLAUDE.md](CLAUDE.md) / [AGENTS.md](AGENTS.md)

## Requisitos

| Herramienta | Versión | Para qué |
|-------------|---------|----------|
| Docker Desktop (con Compose v2) | reciente, **corriendo** | Entorno local y tests de integración (Testcontainers) |
| JDK | 21 o superior | Backend. Gradle descarga el toolchain 21 si tenés otra versión |
| Node.js | 20.19 o superior | Frontend |

## Arranque local

```bash
cp .env.example .env        # y cambiá las contraseñas
docker compose up --build
```

| Servicio | URL |
|----------|-----|
| Frontend (Vite) | http://localhost:5173 |
| Backend | http://localhost:8080 (salud: `/actuator/health`) |
| Swagger UI (solo perfil `local`) | http://localhost:8080/swagger-ui.html |
| Mailpit (emails capturados) | http://localhost:8025 |

Los roles de la base (`app_owner` para migraciones, `app_rw` para la aplicación) los crea `docker/postgres/init/01-roles.sh` **solo cuando se crea el volumen**. Si cambiás ese script o las credenciales, recreá la base (borra los datos locales):

```bash
docker compose down -v
```

### Cuentas de demo

El perfil `local` carga dos consultorios ficticios (detalle en [backend/src/main/resources/db/seed-local/README.md](backend/src/main/resources/db/seed-local/README.md)). Las contraseñas valen **solo en local**.

| Cuenta | Contraseña | Qué muestra |
|--------|------------|-------------|
| `demo@consultorio.test` | `demo-local-2026` | Plan activo con la agenda completa: todos los estados, sobreturno, bloqueo, novedad sin ver, paciente para revisar e historial |
| `vencido@consultorio.test` | `demo-local-2026` | Plan vencido: agenda en solo lectura y exportación |

Páginas del paciente, sin cuenta:

- Reserva: http://localhost:5173/c/demo (y http://localhost:5173/c/demo-vencido, que no toma turnos online)
- Gestión de un turno de demo: http://localhost:5173/t/demo-gestion-de-turno-solo-local-0000000001

### Recorrido de prueba

1. Entrá con `demo@consultorio.test` y mirá la agenda de la semana: el aviso de la novedad del paciente y el de "1 paciente para revisar".
2. Abrí el turno del miércoles a las 11:00: el historial muestra la reserva y la reprogramación que hizo el paciente.
3. En **Pacientes**, revisá a `Paciente Seis Prueba`: reservó con otro nombre.
4. Creá un turno con **Nuevo turno** y cancelalo desde su detalle.
5. Abrí http://localhost:5173/c/demo en otra ventana, reservá como paciente y usá el link que te da para cancelar.
6. Entrá con `vencido@consultorio.test`: la agenda está en solo lectura y podés exportar los datos.

Las fechas del seed se fijan en la primera carga. Si los turnos quedaron en una semana anterior, recreá la base con `docker compose down -v`.

## Tests

```bash
cd backend && ./gradlew test        # unitarios, ArchUnit e integración con PostgreSQL 16 (necesita Docker)
cd frontend && npm ci && npm test   # Vitest
cd frontend && npm run typecheck && npm run lint
```

### Humo E2E

Recorridos críticos en Chromium con viewport de teléfono (C-23): el odontólogo entra, ve la agenda, crea y cancela un turno, ve el paciente para revisar y el historial; el paciente reserva desde la página pública y cancela con su link; el consultorio vencido queda en solo lectura. Corren contra el stack de Compose con el seed de demo, así que primero levantalo:

```bash
docker compose up -d --build --wait
cd e2e && npm ci && npx playwright install chromium
npm run humo
```

- Cada recorrido crea un paciente con un nombre único y cancela al final el turno que creó, así se puede correr varias veces sobre el mismo stack.
- La reserva pública tiene un límite de 10 por hora por IP: si corrés el humo muchas veces seguidas, esperá o reiniciá el backend.
- Con una base cargada hace semanas o con datos tuyos encima, recreala (`docker compose down -v`) para que el seed quede en la semana actual.
- Otra URL: `HUMO_BASE_URL=http://otro-host:5173 npm run humo`.
- En CI corre en el job `E2E (humo)`. Si falla, el reporte, las capturas, el video y la traza quedan como artefacto del job.

## Tipos de la API en el frontend

`frontend/src/shared/api-types.ts` se genera desde el contrato OpenAPI del backend y **no se edita a mano**. Después de cambiar un endpoint:

```bash
cd frontend
npm run api:types                                         # con el backend corriendo en :8080
npm run api:types -- ../backend/build/openapi/openapi.json  # o desde el archivo que exporta ./gradlew test
```

El CI falla si el archivo commiteado no coincide con el contrato.

## Página `/seguridad` antes de publicar

El texto de `/seguridad` sale de `frontend/src/contenido/seguridad.json`: cada afirmación declara su respaldo y los changes que tienen que estar `[x]` para que sea cierta (C-22, RN-PL-06). Antes de publicar una versión:

```bash
cd frontend
npm run seguridad:revisar   # falla si una frase depende de un change sin [x], no tiene respaldo o quedan (TBD)
```

No corre en CI a propósito: falla mientras falten el proveedor del VPS, el SMTP y el email de seguridad. Es parte del criterio de salida de C-23 en `CHANGES.md`. Necesita Node 22.6 o posterior.

## Flujo de trabajo

Una rama `feat/C-NN-nombre` por change, un PR por change, conventional commits en español y Strict TDD. El detalle está en [CHANGES.md](CHANGES.md) y en las reglas duras de [CLAUDE.md](CLAUDE.md).
