# Verificación visual y recorrido de US-070 (C-17, tareas 5.1 y 5.2)

Fecha: 2026-11-22. Stack local con `docker compose up -d --build postgres backend mailpit` (perfil `local`, seed
`R__demo.sql`) y el frontend con `npm run dev`. Navegador: el navegador integrado de Claude Code. Solo datos ficticios
del seed y de las reservas de prueba (regla dura 17).

## Recorrido de US-070 con la base real (5.2)

1. Reserva por `POST /api/v1/public/consultorios/demo/turnos` con el DNI `90000001` (de "Paciente Uno Prueba") y otro
   nombre ("Pacienta Uno Prueba"): 201, turno `RESERVADO` el lunes 23/11 a las 10:30.
2. Al entrar como `demo@consultorio.test`, la agenda muestra "1 paciente para revisar" junto al aviso de cambios, y
   "Pacientes" tiene el contador 1 (`agenda-aviso-desktop-1440x900.jpg`).
3. "Revisar pacientes" abre `/app/pacientes?requiereRevision=true` con el filtro activo, la etiqueta "Revisar" y la
   línea «Pacienta Uno Prueba» "al reservar hoy" (`pacientes-revision-desktop-1440x900.jpg`).
4. La revisión compara los tres datos que difieren, con "Distinto" (ícono y texto), la línea de lo que coincide y el
   turno de origen "lunes 23 de noviembre, 10:30 · Consulta" (`paciente-comparar-desktop-1440x900.jpg`).
5. "Usar «Pacienta Uno Prueba»" corrige la ficha y vuelve a la lista con "Listo: la ficha de Uno Prueba, Pacienta quedó
   revisada." y el contador baja de 1 a 0 (`pacientes-revisado-desktop-1440x900.jpg`).
6. El Registro de cambios muestra "Revisaste un paciente" solo con lo modificado: Para revisar Sí → No, Nombre,
   Apellido y Email, sin lo informado. Al verificarlo aparecieron "apellido" y "email" sin etiqueta; se corrigió en
   `features/auditoria/catalogo.ts` con su test.
7. Segunda reserva con el DNI `90000002`: "Corregir a mano" con el nombre vacío y un email sin dominio muestra
   "Revisá 2 datos" con links a cada campo y no envía nada; con datos válidos guarda, vuelve a la lista con el aviso y
   el contador queda en 0 (`pacientes-revisado-mobile-390x844.jpg`).

## Visual contra los mockups aprobados (5.1, versión `dcb700b`)

| Pantalla | 360 | 390 | 1440 | Resultado |
|----------|-----|-----|------|-----------|
| Agenda con el aviso | — | — | ✓ | Aviso pegado al de cambios, misma jerarquía |
| Lista "Requieren revisión" | — | ✓ | ✓ | Igual al mockup |
| Hoja "Más" con contador | — | ✓ | — | "Pacientes" con el número y el texto para lectores |
| Revisión (comparar) | ✓ | ✓ | ✓ | En mobile se apila por dato con "Guardado" y "En la reserva" |
| Corrección con errores | — | ✓ | — | Resumen enlazado y error debajo de cada campo |
| Lista vacía después de revisar | — | ✓ | ✓ | "No hay pacientes para revisar" con "Ver todos" |

Comprobado con el DOM en cada pantalla: sin scroll horizontal, un solo `h1` y objetivos táctiles de al menos 44 px.
En la consola solo aparece el 401 esperado del intento de restaurar la sesión antes del login.

Defectos encontrados y corregidos durante la verificación, cada uno con su test:

- El link del turno de origen medía menos de 44 px.
- Con un solo dato coincidente decía "Coinciden el teléfono": ahora dice "Coincide".
- El subtítulo de la corrección mezclaba el email ("«Paciente Dos Pruebas sin email»"): ahora nombra solo nombre y
  apellido, o "no dejó email".
- El Registro de cambios mostraba "apellido" y "email" con el nombre técnico.

Diferencias con el mockup:

- El aviso "Listo" dice "la ficha de … quedó revisada" (pendiente de aprobación, ver `design.md`).
- Las comillas son «» como en la spec; el mockup usa “”.

No se capturaron: los estados de error, sin conexión y 404 en el navegador (los cubren los tests de Vitest de
`lista.test.ts` y `revision.test.ts`).
