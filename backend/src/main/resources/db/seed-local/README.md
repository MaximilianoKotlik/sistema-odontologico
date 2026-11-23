# Seed local (solo desarrollo)

`R__demo.sql` y `R__demo_vencido.sql` cargan dos consultorios de demo cuando el backend arranca con el perfil
`local` (`docker compose up --build`). `application-local.yml` agrega esta carpeta a `spring.flyway.locations`;
con el perfil por defecto o en los tests no se carga ninguno (`SeedLocalTest`).

## Cuentas

| Consultorio | Slug | Plan | Usuario | Contraseña |
|-------------|------|------|---------|------------|
| Consultorio Demo | `demo` | `ACTIVO` (pago): sin aviso de prueba ni solo lectura | `demo@consultorio.test` | `demo-local-2026` |
| Consultorio Vencido (demo) | `demo-vencido` | `VENCIDO`: agenda en solo lectura | `vencido@consultorio.test` | `demo-local-2026` |

Las contraseñas valen **solo para desarrollo local**.

## Qué se ve en `demo`

| Dato | Valor | Dónde se ve |
|------|-------|-------------|
| Prestaciones | Consulta 30', Limpieza 45', Extracción 60', Control 20' | Configuración y página pública |
| Disponibilidad | lunes a viernes, 09:00–13:00 y 15:00–19:00 | Configuración y huecos de la página pública |
| Pacientes | 6 ficticios, `Paciente Uno Prueba` … `Paciente Seis Prueba`, DNI `90000001` a `90000006` | Pacientes |
| Turnos | 14 de la semana en curso: reservados, confirmados, 1 cancelado por el paciente y 1 ausente; manuales y por link | Agenda |
| Sobreturno | lunes 09:10, solapado con el turno de las 09:00 | Agenda (marca de sobreturno) |
| Bloqueo | jueves de 15:00 a 19:00, "Curso", sin turnos activos | Agenda y Bloqueos |
| Novedad sin ver | miércoles 11:00, Paciente Uno: lo reprogramó desde el link (del martes 11:30) | Agenda (marca de novedad) |
| Paciente para revisar | `Paciente Seis Prueba` reservó el jueves 09:00 como "Paciente Seys", con otro email | Pacientes (contador y revisión) |
| Historial | eventos de creación, confirmación, cancelación, ausencia y reprogramación | Detalle del turno y Registro de cambios |
| Link de gestión | viernes 11:00, Paciente Dos (ver abajo) | Página del paciente |

### Link de gestión de demo

```
http://localhost:5173/t/demo-gestion-de-turno-solo-local-0000000001
```

Abre el turno del viernes a las 11:00 y deja cancelarlo o reprogramarlo, como lo vería el paciente. En la base se
guarda solo el SHA-256 del token, igual que con uno real. Este valor en claro vale **solo en local**: el seed no se
carga en otros entornos.

Si lo cancelás, el link queda en "turno cancelado". Para volver a tenerlo reservado, recreá el volumen (abajo).

## Qué se ve en `demo-vencido`

- La agenda en solo lectura, con el aviso de plan vencido y "Exportar mis datos".
- La página pública (`http://localhost:5173/c/demo-vencido`) no toma turnos online y muestra el teléfono.
- 2 prestaciones, 2 pacientes (`Paciente Vencido Uno Prueba` y `Paciente Vencido Dos Prueba`, DNI `90000011` y
  `90000012`) y 2 turnos de la semana en curso.

## Mantenimiento

Todos los datos son ficticios (regla dura 17). Si cambiás la contraseña, regenerá el hash BCrypt de costo 12
y actualizá `SeedLocalTest`, que verifica que el hash corresponde a esta contraseña.

**Las fechas de los turnos, del bloqueo y de los eventos se fijan en la primera carga**: son relativas al lunes de la
semana en curso. Como Flyway no vuelve a correr el archivo si no cambia, la semana siguiente siguen en la anterior.
Para traerlas a la semana actual, recreá el volumen (`docker compose down -v`).

Son migraciones repetibles: Flyway las vuelve a correr cuando cambia su contenido. Usan ids fijos y
`ON CONFLICT DO NOTHING`, así que no duplican filas. Si tu base se cargó antes de C-23, los turnos nuevos entran en la
semana actual y los viejos quedan en la suya: para empezar de cero, `docker compose down -v`.
