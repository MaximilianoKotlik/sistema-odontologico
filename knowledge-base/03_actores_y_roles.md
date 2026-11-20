# Actores y Roles

> Fuente: [`../docs/discovery/discovery.md`](../docs/discovery/discovery.md), punto 2. La v1 tiene **dos únicos
> actores humanos** y **no tiene roles ni permisos configurables**.

## Actores del sistema

| Actor | Descripción | Cómo interactúa | Identificación |
|-------|-------------|-----------------|----------------|
| **Odontólogo** (usuario autenticado) | Dueño y único usuario de su consultorio (tenant). Una cuenta por consultorio y un consultorio por cuenta | SPA/PWA privada: agenda, configuración, auditoría, exportación | Email + contraseña → JWT con claims `sub` (usuario_id) y `cid` (consultorio_id) |
| **Paciente** (anónimo) | Persona que atiende en el consultorio. No tiene cuenta ni contraseña | Página pública del consultorio (`/c/{slug}`) y link de gestión de su turno (`/t/{token}`) | Datos mínimos (nombre, apellido, teléfono; email opcional). La posesión del token de gestión habilita reprogramar/cancelar **ese** turno |
| **Visitante** | Cualquier persona en la landing o en precios | Páginas públicas de marketing, registro | Ninguna |
| **Sistema** (actor técnico) | Procesos internos: expiración de tokens, marcado de prueba vencida | Tareas programadas (`@Scheduled`) dentro del backend | Registrado en auditoría como `actor_tipo = SISTEMA` |

> El paciente **no** es un usuario del sistema: no se loguea, no tiene sesión y no ve nada fuera de su
> propio turno. Ver la decisión de identidad en [DD-09](09_decisiones_y_supuestos.md) y la pregunta
> abierta [PA-01](10_preguntas_abiertas.md).

## RBAC — Matriz de permisos

En v1 **no hay RBAC**. La autorización se reduce a dos reglas:

1. **Autenticado ⇒ dueño de su tenant**: todo request privado opera exclusivamente sobre el
   `consultorio_id` que viaja en el JWT ([RN-TE-01](05_reglas_de_negocio.md)). Nunca se acepta un
   `consultorio_id` desde el body o la URL de una ruta privada.
2. **Token de gestión ⇒ dueño de un turno**: el paciente solo puede leer, reprogramar o cancelar el
   turno asociado a su token ([RN-RP-06](05_reglas_de_negocio.md)).

| Actor | Recurso | C | R | U | D | Notas |
|-------|---------|---|---|---|---|-------|
| Odontólogo | Consultorio propio | — (en registro) | ✅ | ✅ | ❌ | La baja de cuenta se pide por soporte en v1 ([PA-07](10_preguntas_abiertas.md)) |
| Odontólogo | Prestaciones | ✅ | ✅ | ✅ | Desactivar | No se borran si tienen turnos |
| Odontólogo | Disponibilidad | ✅ | ✅ | ✅ | ✅ | Reemplazo completo de la semana tipo |
| Odontólogo | Bloqueos | ✅ | ✅ | ❌ | ✅ | Para cambiar un bloqueo se borra y se crea otro |
| Odontólogo | Turnos y sobreturnos | ✅ | ✅ | ✅ (horario, estado) | ❌ | Se cancelan, nunca se borran ([RN-AG-07](05_reglas_de_negocio.md)) |
| Odontólogo | Pacientes | Implícito | ✅ | ✅ | ❌ | Se crean al reservar o al cargar un turno |
| Odontólogo | Auditoría | ❌ | ✅ | ❌ | ❌ | Append-only, nadie edita ni borra |
| Odontólogo | Exportación | — | ✅ | — | — | Descarga completa |
| Paciente (con token) | Su turno | — | ✅ | Reprogramar | Cancelar | Sujeto a anticipación mínima |
| Paciente (sin token) | Huecos y prestaciones públicas | — | ✅ | — | — | Solo datos públicos del consultorio |
| Paciente (sin token) | Turno nuevo | ✅ | — | — | — | Reserva por link |
| Visitante | Planes y precios | — | ✅ | — | — | |

## Rutas públicas vs privadas

### Frontend (SPA)

| Ruta | Tipo | Pantalla |
|------|------|----------|
| `/` | Pública | Landing con propuesta de valor |
| `/precios` | Pública | Planes en ARS y prueba gratuita (sin tarjeta) |
| `/seguridad` | Pública | Seguridad, tratamiento de datos y cumplimiento (con lo que aún no está) |
| `/privacidad`, `/terminos` | Pública | Política de privacidad y términos ([11](11_marco_legal_argentino.md)) |
| `/registro`, `/login`, `/recuperar`, `/recuperar/confirmar` | Pública | Cuenta del odontólogo |
| `/c/{slug}` | Pública | Reserva del paciente |
| `/t/{token}` | Pública (con token) | Gestión del turno: ver, reprogramar, cancelar |
| `/app/agenda` (día/semana) | Privada | Agenda |
| `/app/turnos/nuevo`, `/app/turnos/{id}` | Privada | Alta y detalle de turno/sobreturno |
| `/app/bloqueos` | Privada | Bloqueos |
| `/app/configuracion` (consultorio, prestaciones, disponibilidad) | Privada | Configuración |
| `/app/auditoria` | Privada | Registro de cambios |
| `/app/exportar` | Privada | Exportación |

### Backend (API)

| Patrón | Acceso | Protección adicional |
|--------|--------|----------------------|
| `/api/v1/auth/**` | `permitAll` | Rate limit por IP y por email; respuestas que no revelan si el email existe |
| `/api/v1/public/**` | `permitAll` | Rate limit por IP; validación estricta; nunca expone datos de otros pacientes |
| `/api/v1/**` (resto) | `authenticated` (JWT válido) | Filtro de tenant por claim `cid` |
| `/actuator/health` | `permitAll` | Solo estado, sin detalles |
| `/v3/api-docs`, `/swagger-ui/**` | `permitAll` en perfil `local`; deshabilitado en otros perfiles | |

## Cómo se agregaría RBAC (post-v1)

Cuando entre multi-profesional y recepción (ítem 7 del backlog), el modelo previsto es:

1. Separar `Usuario` de `Consultorio` con una tabla `membresia(usuario_id, consultorio_id, rol)`;
   hoy la relación es 1:1 directa, por lo que la migración es aditiva.
2. Roles iniciales: `TITULAR` (equivalente al odontólogo de v1), `PROFESIONAL`, `RECEPCION`.
3. El JWT pasa a llevar `roles`; los controladores usan `@PreAuthorize` con permisos por recurso
   (p. ej. `turno:escribir`, `historia:leer`). Recepción **no** accede a historia clínica.
4. `Turno` incorpora `profesional_id` y la exclusión de solapamientos pasa a ser por profesional (y
   por sillón si se modela).
5. La auditoría ya registra `actor_id` y `actor_tipo`, por lo que no requiere cambios de forma.

Este cambio toca `auth`, que es dominio **CRITICAL**: requiere propuesta y aprobación humana
explícita antes de escribir código.
