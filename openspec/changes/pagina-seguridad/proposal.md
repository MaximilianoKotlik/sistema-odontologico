# Proposal

> Change **C-22** de [CHANGES.md](../../../CHANGES.md) · Governance **ALTO** (afirmaciones públicas sobre datos de salud) · Depende de `C-03` (`auditoria-base`, `[x]`), `C-06` (`frontend-shell-pwa`, `[x]`) y `C-19` (`exportacion-datos`) · Responsable: Octi · Aprobación: Maxi.
>
> Es trabajo anticipado. El mockup ya está aprobado (#40, versión `1609d16`) y la propuesta de C-19 está en `main` (#32). El código se escribe recién cuando C-19 esté `[x]`. La página **no se publica** hasta resolver PA-02 (proveedor, país y backups del VPS), el proveedor SMTP, el email de seguridad y el TLS del VPS.

## Why

La transparencia es el diferenciador de la v1, y para un odontólogo la primera pregunta antes de cargar pacientes es qué pasa con esos datos (Discovery D.3, US-062). La competencia lo resuelve con afirmaciones comerciales; acá se responde con honestidad, incluido lo que todavía falta (RN-PL-05). El riesgo es legal: una frase que promete de más es peor que no tener página. Por eso cada afirmación tiene que estar respaldada por algo implementado y revisarse antes de cada versión (RN-PL-06).

## What Changes

- **Página pública `/seguridad`** (sin sesión ni shell), con la estructura aprobada: aviso de "sin revisión legal" arriba de todo, índice y ocho temas (quién es responsable, qué datos guardamos, aislamiento por consultorio, registro de cambios, exportación, acceso, dónde están los datos y lo que todavía no está), fecha de última actualización y pie con precios, términos y privacidad.
- **Contenido en una fuente versionada** (`frontend/src/contenido/seguridad.json`): textos, fecha de actualización y, para cada afirmación, su **respaldo** (regla, decisión, change o test) y los changes que tienen que estar terminados para que sea cierta. La página se arma desde esa fuente al compilar; no hay endpoint ni estados de carga.
- **Revisión de las afirmaciones antes de publicar** (RN-PL-06): un comando, `npm run seguridad:revisar`, falla si alguna afirmación depende de un change que no está `[x]` en `CHANGES.md`, si alguna no tiene respaldo o si quedan marcadores `(TBD)`. Lo corre la checklist de release de C-23.
- **Ruta** `/seguridad` en las páginas públicas de `publico.ts` (C-21).
- **Tests Vitest**: la página muestra la fecha y "Lo que todavía no está", su texto coincide con la fuente, cada afirmación tiene respaldo y no hay frases prohibidas (certificaciones, "cumplimos la ley", "100 % seguro").

**Fuera de alcance**:
- Los textos de términos y privacidad (`docs/legal/`, DD-28) y su revisión por un abogado.
- Resolver PA-02, elegir el proveedor SMTP o definir el email de seguridad: la página los muestra como `(TBD)` y no se publica hasta tenerlos.
- La verificación en dos pasos (DD-35), que la página declara como faltante.

## Capabilities

### New Capabilities

- `pagina-seguridad`: la página pública que explica cómo se tratan los datos, qué garantías hay y qué falta, armada desde una fuente versionada en la que cada afirmación declara su respaldo y se revisa antes de publicar.

### Modified Capabilities

- Ninguna. La ruta pública vive en `publico.ts` (C-21), fuera del shell, y `pwa-offline` no cachea nada nuevo.

## Impact

- **Código**: `frontend/src/contenido/seguridad.json`, `frontend/src/features/seguridad/**`, la página en `frontend/src/pages/`, la tabla de `frontend/src/publico.ts` y el script `frontend/scripts/seguridad-revisar.mjs`. Sin cambios en `backend/`.
- **Dependencias**: ninguna nueva.
- **Coordinación**:
  - **C-19 (Mati)**: "Tus datos son tuyos" describe la exportación tal como la define C-19 (ZIP con JSON, CSV y PDF, sin secretos, también con plan `VENCIDO`). Si cambia al implementar, se actualiza la fuente.
  - **C-18 (Mati)**: "Podés consultarlo desde tu cuenta" depende de C-18; si no está `[x]` al publicar, el comando de revisión lo marca y la frase se saca.
  - **C-15 y C-16**: la frase del link del paciente depende de los dos.
  - **C-23 (Maxi)**: suma `npm run seguridad:revisar` a la checklist de release.
- **Bloqueos de publicación** (no de implementación): PA-02, proveedor SMTP, email de seguridad (con el dominio, DD-34) y TLS del VPS.
- **Preguntas abiertas**: toca PA-02 (Alta, acotada por DD-25). La propuesta es implementar la página con `(TBD)` en el proveedor, el país y los backups, y bloquear solo su **publicación** hasta resolverla (el comando de revisión falla con marcadores `(TBD)`). **Pregunta para Maxi**: ¿esto cumple la regla dura 16 o hay que resolver PA-02 antes de escribir el código?
