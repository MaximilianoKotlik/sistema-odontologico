# Agenda Odontológica SaaS — Base de Conocimiento

Base de conocimiento canónica del proyecto. Se construyó a partir del Discovery confirmado
([`../discovery/discovery.md`](../discovery/discovery.md)), del informe competitivo
([`../discovery/informe-discovery.md`](../discovery/informe-discovery.md)) y de las decisiones
técnicas que el usuario confirmó el 2026-11-16.

> **Nombre comercial**: a definir. En toda la KB se lo llama "el sistema".

## Resumen ejecutivo

SaaS web multi-consultorio para odontólogos independientes de Argentina. Cada consultorio es un
tenant con un único usuario (el odontólogo). La v1 resuelve **solo la agenda**: agenda diaria y
semanal, bloqueos, sobreturnos marcados, duración por prestación, prevención de solapamientos y una
página pública donde el paciente, sin crear cuenta, reserva, reprograma o cancela su turno. El
diferenciador es la **transparencia**: precios públicos en ARS, exportación completa de datos,
registro de auditoría y cumplimiento legal declarado con honestidad. Stack: Java 21 + Spring Boot +
PostgreSQL en el backend, TypeScript vanilla + Vite + Tailwind (PWA) en el frontend, todo corriendo
en local con Docker Compose.

## Índice de archivos

| Archivo | Propósito |
|---------|-----------|
| [01_vision_y_objetivos.md](01_vision_y_objetivos.md) | Propósito, objetivos por actor, alcance v1, fuera de alcance, métricas de éxito |
| [02_descripcion_general.md](02_descripcion_general.md) | Stack, arquitectura de alto nivel, integraciones (ninguna en v1), resumen de la API REST |
| [03_actores_y_roles.md](03_actores_y_roles.md) | Odontólogo autenticado y paciente anónimo, matriz de permisos, rutas públicas vs privadas, cómo se agregaría RBAC |
| [04_modelo_de_datos.md](04_modelo_de_datos.md) | Dominios, ERD en mermaid, entidades con atributos/constraints/índices, aislamiento por `consultorio_id`, seed data |
| [05_reglas_de_negocio.md](05_reglas_de_negocio.md) | Reglas `RN-{DOMINIO}-NN` de tenant, auth, agenda, reserva pública, auditoría, exportación y plan |
| [06_funcionalidades.md](06_funcionalidades.md) | Épicas e historias de usuario con criterios Given/When/Then (insumo directo para TDD) |
| [07_flujos_principales.md](07_flujos_principales.md) | Flujos extremo a extremo con diagramas de secuencia mermaid y casos de error |
| [08_arquitectura_propuesta.md](08_arquitectura_propuesta.md) | Directorios, paquetes, capas, seguridad JWT, OpenAPI, Flyway, Docker Compose, variables de entorno, estrategia de tests, PWA |
| [09_decisiones_y_supuestos.md](09_decisiones_y_supuestos.md) | Decisiones `DD-NN` con alternativas y trade-offs; supuestos `SU-NN` con riesgo y validación |
| [10_preguntas_abiertas.md](10_preguntas_abiertas.md) | Inconsistencias y preguntas priorizadas (identidad del paciente, hosting, precios, notificaciones, legal) |
| [11_marco_legal_argentino.md](11_marco_legal_argentino.md) | Ley 25.326 (AAIP, datos sensibles, ARCO), Ley 26.529 (historia clínica) e implicancias para v1 |

## Cómo navegar

1. **Entender el dominio** → [01](01_vision_y_objetivos.md), [03](03_actores_y_roles.md)
2. **Entender los datos** → [04](04_modelo_de_datos.md)
3. **Entender las reglas** → [05](05_reglas_de_negocio.md)
4. **Entender la arquitectura** → [02](02_descripcion_general.md), [08](08_arquitectura_propuesta.md)
5. **Implementar** → [06](06_funcionalidades.md) (criterios de aceptación = tests), [07](07_flujos_principales.md)
6. **Antes de codificar** → [09](09_decisiones_y_supuestos.md), [10](10_preguntas_abiertas.md)
7. **Antes de tocar datos personales o pensar en v2** → [11](11_marco_legal_argentino.md)

## Convenciones de trazabilidad

| Prefijo | Significado | Dónde vive |
|---------|-------------|------------|
| `RN-XX-NN` | Regla de negocio por dominio | [05](05_reglas_de_negocio.md) |
| `US-NNN` | Historia de usuario | [06](06_funcionalidades.md) |
| `FL-NN` | Flujo principal | [07](07_flujos_principales.md) |
| `DD-NN` | Decisión documentada | [09](09_decisiones_y_supuestos.md) |
| `SU-NN` | Supuesto a validar | [09](09_decisiones_y_supuestos.md) |
| `PA-NN` / `IN-NN` | Pregunta abierta / inconsistencia | [10](10_preguntas_abiertas.md) |
| `LG-NN` | Obligación legal derivada | [11](11_marco_legal_argentino.md) |

## Niveles de gobernanza por dominio

| Dominio | Nivel | Implicancia para agentes y desarrolladores |
|---------|-------|---------------------------------------------|
| `auth` (registro, login, JWT, recuperación de contraseña) | **CRITICAL** | Solo análisis; ningún cambio de código sin aprobación humana explícita |
| `exportacion`, `auditoria`, aislamiento de tenant | **HIGH** | Proponer y esperar revisión antes de escribir |
| `agenda`, `reserva` | **MEDIUM** | Implementar con checkpoints y exponer decisiones no obvias |
| `consultorio` (CRUD de configuración), catálogos | **LOW** | Autonomía si los tests pasan |

## Estado

- Versión de la KB: 1.0 — 2026-11-16.
- Fuente: Discovery interactivo + decisiones técnicas confirmadas por el usuario.
- Próximo paso del flujo de fundación: roadmap de changes (`CHANGES.md`).
