# Spec Delta

## Purpose

Permite que cualquier visitante conozca los planes, importes y condiciones de la prueba antes de registrarse, con precios públicos en ARS y una fecha de actualización verificable, sin exponer datos de consultorios ni de pacientes.

## ADDED Requirements

### Requirement: Catálogo público sin autenticación

El sistema MUST exponer el catálogo de planes mediante `GET /api/v1/public/planes` sin exigir cuenta, token ni pertenencia a un consultorio. La respuesta MUST ser 200 con la lista de planes cuando la fuente está disponible y MUST usar Problem Details con un `codigo` estable cuando no lo está.

#### Scenario: Consulta de un visitante anónimo

- **WHEN** un visitante sin sesión solicita `GET /api/v1/public/planes`
- **THEN** el sistema responde 200 con el catálogo de planes y no exige credenciales

#### Scenario: Fuente del catálogo no disponible

- **WHEN** la fuente versionada de planes no puede cargarse o es inválida
- **THEN** el sistema responde un Problem Details con `codigo` `PLANES_NO_DISPONIBLES` y no devuelve importes parciales

### Requirement: Importes exactos en ARS

Cada plan MUST publicar su importe como texto decimal exacto con dos decimales, moneda `ARS` e impuestos incluidos. El sistema MUST NOT usar tipos de punto flotante para representar precios en ninguna capa.

#### Scenario: Consulta del catálogo con planes vigentes

- **WHEN** un visitante consulta el catálogo
- **THEN** cada plan incluye un importe textual con dos decimales, `moneda` `ARS` e `impuestosIncluidos` en `true`

#### Scenario: Importe sin definir

- **WHEN** un plan de la fuente no tiene un importe válido
- **THEN** el catálogo no publica ese plan como disponible y no muestra un marcador como precio real

### Requirement: Fecha de última actualización

El catálogo MUST exponer la fecha de última actualización como instante UTC y la página MUST mostrarla en español rioplatense con la zona horaria `America/Argentina/Buenos_Aires`.

#### Scenario: Consulta de la fecha desde la API

- **WHEN** un visitante consulta `GET /api/v1/public/planes`
- **THEN** la respuesta incluye `actualizadoEn` como instante UTC

#### Scenario: Visualización de la fecha en la página

- **WHEN** un visitante abre `/precios`
- **THEN** la fecha se muestra en formato local de es-AR con la zona `America/Argentina/Buenos_Aires`

### Requirement: Página pública de precios

La página `/precios` MUST ser accesible sin autenticación y MUST mostrar para cada plan su nombre, qué incluye y su importe en ARS, además de la prueba gratuita y la fecha de actualización.

#### Scenario: Visita sin iniciar sesión

- **WHEN** un visitante abre `/precios` sin iniciar sesión
- **THEN** ve la lista de planes con nombre, inclusiones, importe en ARS y la fecha de actualización

#### Scenario: Error al cargar el catálogo

- **WHEN** la página no puede cargar el catálogo
- **THEN** muestra un estado de error reintentable y no inventa importes ni inclusiones

### Requirement: Divulgación honesta de la prueba

La página MUST declarar explícitamente que la prueba gratuita dura 30 días y no requiere tarjeta ni ningún medio de pago. MUST NOT afirmar funcionalidades de cobro, certificaciones ni cumplimiento que no estén implementados.

#### Scenario: Lectura de las condiciones de la prueba

- **WHEN** un visitante abre `/precios`
- **THEN** ve la leyenda de prueba gratuita de 30 días sin tarjeta ni medio de pago

#### Scenario: Ausencia de afirmaciones no respaldadas

- **WHEN** se revisa el contenido de `/precios`
- **THEN** no aparecen claims de cobro automático, 2FA, certificaciones ni cumplimiento legal verificado

### Requirement: Presentación mobile-first y accesible

La página MUST ser legible y operable a 360 px de ancho sin scroll horizontal, con textos en español rioplatense con voseo, foco visible, objetivos táctiles de al menos 44 px y contraste AA; MUST respetar `prefers-reduced-motion`.

#### Scenario: Navegación en pantalla chica

- **WHEN** un visitante abre `/precios` en un viewport de 360 px
- **THEN** el contenido se lee sin scroll horizontal y los botones tienen al menos 44 px de alto

#### Scenario: Navegación con teclado y movimiento reducido

- **WHEN** un visitante navega con teclado y tiene activado `prefers-reduced-motion`
- **THEN** el foco es visible y no hay animaciones ni transiciones
