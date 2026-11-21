# Spec Delta

## Purpose

Asistente que lleva al odontólogo recién registrado a tener su consultorio, sus prestaciones y sus horarios configurados y su link público listo para compartir (FL-02).

## ADDED Requirements

### Requirement: Apertura del asistente
Al entrar a la app, el odontólogo SHALL ser llevado a `/app/bienvenida` mientras su consultorio no tenga ninguna prestación activa. Con al menos una prestación activa, la app MUST abrir la agenda como siempre.

#### Scenario: Primer ingreso
- **GIVEN** un consultorio recién registrado, sin prestaciones
- **WHEN** el odontólogo ingresa
- **THEN** se abre el asistente en el paso 1

#### Scenario: Consultorio ya configurado
- **GIVEN** un consultorio con prestaciones activas
- **WHEN** el odontólogo ingresa
- **THEN** se abre `/app/agenda` y no el asistente

### Requirement: Pasos del asistente
El asistente SHALL tener cuatro pasos en este orden: Consultorio, Prestaciones, Horarios y Compartir. Cada paso MUST mostrar su número y el total ("Paso 2 de 4") y un indicador de progreso que no dependa solo del color.

#### Scenario: Indicador de progreso
- **WHEN** el odontólogo está en el paso de Prestaciones
- **THEN** ve "Paso 2 de 4" y el paso 1 marcado como completo con ícono y texto

### Requirement: Guardado por paso
Al confirmar un paso, el asistente MUST guardarlo en la API antes de avanzar. Si el guardado falla, MUST quedarse en el paso, conservar lo cargado y mostrar el error junto al campo que corresponde.

#### Scenario: Paso 1 guardado
- **WHEN** el odontólogo confirma nombre, slug y teléfono en el paso 1
- **THEN** se envía la configuración del consultorio y, con respuesta 200, se pasa al paso 2

#### Scenario: Slug tomado en el paso 1
- **WHEN** el slug elegido responde 409 `SLUG_EN_USO`
- **THEN** el asistente se queda en el paso 1, marca el campo del slug y ofrece las sugerencias de la respuesta

### Requirement: Datos precargados
El paso 1 SHALL mostrar el nombre y el slug que el consultorio ya tiene desde el registro, para que el odontólogo solo los confirme o los cambie.

#### Scenario: Nombre del registro
- **GIVEN** un consultorio registrado como "Consultorio Demo Sur" con slug `consultorio-demo-sur`
- **WHEN** se abre el paso 1
- **THEN** los campos muestran esos valores

### Requirement: Teléfono obligatorio en el paso 1
El paso 1 MUST exigir el teléfono de contacto para avanzar, porque es lo que ve el paciente cuando no puede cancelar por el link o el consultorio no toma turnos online.

#### Scenario: Sin teléfono
- **GIVEN** el paso 1 con el teléfono vacío
- **WHEN** el odontólogo toca "Siguiente"
- **THEN** no avanza, no se envía nada y el campo del teléfono queda marcado con "Poné un teléfono de contacto"

### Requirement: Mínimo para avanzar
El paso de Prestaciones MUST exigir al menos una prestación activa para avanzar. El paso de Horarios MUST exigir al menos una franja para avanzar.

#### Scenario: Sin prestaciones
- **GIVEN** el paso 2 sin ninguna prestación cargada
- **WHEN** el odontólogo toca "Siguiente"
- **THEN** no avanza y ve "Agregá al menos una prestación para seguir"

### Requirement: Atajos de carga
El paso de Prestaciones SHALL ofrecer atajos para agregar con un toque prestaciones comunes con una duración sugerida, editables después. El paso de Horarios SHALL ofrecer un atajo que carga lunes a viernes de 09:00 a 13:00 y de 15:00 a 19:00.

#### Scenario: Atajo de prestación
- **WHEN** el odontólogo toca el atajo "Limpieza · 45 min"
- **THEN** se crea la prestación "Limpieza" de 45 min y aparece en la lista del paso

#### Scenario: Atajo de horarios
- **GIVEN** la grilla vacía
- **WHEN** el odontólogo toca "Lunes a viernes, 9 a 13 y 15 a 19"
- **THEN** la grilla muestra esas dos franjas en los cinco días, todavía sin guardar

### Requirement: Paso Compartir
El último paso SHALL mostrar el link público completo `/c/{slug}` con un botón "Copiar link" que confirma la copia con texto. El botón "Compartir" MUST mostrarse solo si el navegador ofrece compartir de forma nativa.

#### Scenario: Copiar link
- **WHEN** el odontólogo toca "Copiar link"
- **THEN** el link queda en el portapapeles y el botón dice "¡Copiado!" de forma que lo anuncie un lector de pantalla

#### Scenario: Navegador sin compartir nativo
- **GIVEN** un navegador sin Web Share API
- **WHEN** se muestra el paso 4
- **THEN** no aparece el botón "Compartir" y sí "Copiar link"

### Requirement: Salir y retomar
El odontólogo SHALL poder salir del asistente en cualquier paso con "Terminar después". Lo ya guardado MUST conservarse, y el asistente MUST volver a abrirse en el próximo ingreso mientras no haya prestaciones activas.

#### Scenario: Salir en el paso 2
- **GIVEN** el paso 1 guardado y ninguna prestación cargada
- **WHEN** el odontólogo toca "Terminar después" y vuelve a ingresar más tarde
- **THEN** se abre el asistente con los datos del paso 1 ya guardados
