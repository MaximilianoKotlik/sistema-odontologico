# Spec Delta

## Purpose

Permite que cualquier visitante, y sobre todo el odontólogo antes de cargar datos de sus pacientes, entienda cómo se tratan los datos, qué garantías hay y qué todavía falta, con afirmaciones que siempre corresponden a algo implementado (US-062, RN-PL-05, RN-PL-06).

## ADDED Requirements

### Requirement: Página pública de seguridad

La página `/seguridad` SHALL mostrarse sin iniciar sesión y sin el shell del odontólogo, con el título, la fecha de última actualización, un índice y los temas: quién es responsable de qué, qué datos guardamos, aislamiento por consultorio, registro de cambios, exportación, acceso, dónde están los datos y lo que todavía no está. MUST NOT enviar credenciales en sus pedidos.

#### Scenario: Visita sin sesión

- **WHEN** un visitante sin sesión abre `/seguridad`
- **THEN** ve el título "Seguridad y datos", la fecha "Última actualización: …", el índice y los ocho temas, sin barra de navegación de la app

### Requirement: Aviso de que no hubo revisión legal

Mientras los textos legales no tengan revisión profesional (DD-28), la página MUST mostrar arriba de todo, antes del índice, que la página y los textos legales todavía no tuvieron revisión de un abogado y que no se declara cumplimiento legal ni certificaciones, con un link a "Lo que todavía no está".

#### Scenario: Primer contenido de la página

- **WHEN** se abre `/seguridad`
- **THEN** lo primero después del título y la fecha es el aviso de "sin revisión de un abogado", con el link a "Lo que todavía no está"

### Requirement: Lo que todavía no está

La página MUST incluir la sección "Lo que todavía no está", con cada ítem marcado con ícono y texto (no solo color), que declare como mínimo: la falta de revisión legal profesional, la falta de verificación en dos pasos, la ausencia de certificaciones, lo que falta definir del servidor y las copias de seguridad, y cómo funciona hoy la baja de la cuenta.

#### Scenario: Sección completa

- **WHEN** se abre `/seguridad`
- **THEN** "Lo que todavía no está" menciona la revisión legal, la verificación en dos pasos, las certificaciones, el país del servidor y las copias de seguridad, y la baja de la cuenta

### Requirement: Sin afirmaciones que no se puedan respaldar

La página MUST NOT afirmar cumplimiento legal, certificaciones, seguridad total ni funcionalidades que no existan (RN-PL-05, LG-10). Las capacidades técnicas se describen en lenguaje llano y sin términos inexactos (por ejemplo, la contraseña se guarda como una huella que no se puede revertir, no "cifrada").

#### Scenario: Frases prohibidas

- **WHEN** se revisa el texto de la página
- **THEN** no aparece "cumplimos la ley", "certificado", "certificación ISO" (salvo para decir que no la hay), "100 % seguro" ni "contraseña cifrada"

### Requirement: Contenido desde una fuente versionada

El texto de la página, su fecha de actualización y sus temas SHALL salir de una única fuente versionada en el repositorio, no escrita en los componentes (RN-PL-06). Cambiar un texto MUST requerir cambiar solo esa fuente.

#### Scenario: El texto coincide con la fuente

- **WHEN** se compara el texto que muestra la página con la fuente versionada
- **THEN** cada título, párrafo e ítem de la página está en la fuente, y la fecha mostrada es la de la fuente en formato es-AR

### Requirement: Cada afirmación declara su respaldo

Cada afirmación de la fuente MUST declarar su respaldo (regla, decisión, change o test que la hace cierta) y los changes que tienen que estar terminados para que sea cierta. El respaldo MUST NOT mostrarse en la página pública.

#### Scenario: Afirmación sin respaldo

- **GIVEN** una afirmación nueva en la fuente sin respaldo
- **WHEN** se corren los tests
- **THEN** fallan indicando la afirmación sin respaldo

### Requirement: Revisión antes de publicar

Antes de publicar una versión, una revisión automática MUST fallar si alguna afirmación depende de un change que no está completado en `CHANGES.md`, si alguna no tiene respaldo o si la fuente tiene marcadores `(TBD)` (proveedor del servidor, proveedor de email, email de seguridad). Mientras falle, la página MUST NOT publicarse.

#### Scenario: Depende de un change sin terminar

- **GIVEN** la afirmación "Podés consultarlo desde tu cuenta" depende de C-18 y C-18 no está completado
- **WHEN** se corre la revisión
- **THEN** falla y nombra la afirmación y el change pendiente

#### Scenario: Quedan marcadores pendientes

- **GIVEN** el proveedor del servidor todavía figura como `(TBD)`
- **WHEN** se corre la revisión
- **THEN** falla y lista los marcadores pendientes

### Requirement: Uso desde el celular y accesibilidad

La página MUST funcionar sin scroll horizontal desde 360 px, con un solo `h1`, jerarquía de títulos correcta, foco visible, links del índice y del pie con área táctil de al menos 44 px y contraste AA. En mobile, la tabla de roles se apila.

#### Scenario: Pantalla de 360 px

- **WHEN** la página se abre en un viewport de 360 px
- **THEN** no hay scroll horizontal, la tabla de roles se lee apilada y los links del índice miden al menos 44 px de alto
