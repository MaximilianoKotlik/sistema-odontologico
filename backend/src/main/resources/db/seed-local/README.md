# Seed local (solo desarrollo)

`R__demo.sql` carga un consultorio de demo cuando el backend arranca con el perfil `local`
(`docker compose up --build`). `application-local.yml` agrega esta carpeta a `spring.flyway.locations`;
con el perfil por defecto o en los tests no se carga (`SeedLocalTest`).

| Dato | Valor |
|------|-------|
| Consultorio | "Consultorio Demo", slug `demo` |
| Plan | `ACTIVO` (pago): sin aviso de prueba ni solo lectura |
| Usuario | `demo@consultorio.test` |
| Contraseña | `demo-local-2026` (**solo para desarrollo local**) |
| Prestaciones | Consulta 30', Limpieza 45', Extracción 60', Control 20' |
| Disponibilidad | lunes a viernes, 09:00–13:00 y 15:00–19:00 |
| Pacientes (C-10) | 5 ficticios, `Paciente Uno Prueba` … `Paciente Cinco Prueba`, DNI `90000001` a `90000005` |
| Turnos (C-10) | 10 de la semana en curso, entre ellos 1 sobreturno solapado y 1 cancelado |
| Bloqueo (C-10) | jueves de 15:00 a 19:00, "Curso", sin turnos activos |

Todos los datos son ficticios (regla dura 17). Si cambiás la contraseña, regenerá el hash BCrypt de costo 12
y actualizá `SeedLocalTest`, que verifica que el hash corresponde a esta contraseña.

**Las fechas de los turnos y del bloqueo se fijan en la primera carga**: son relativas al lunes de la semana en curso. Como Flyway no vuelve a correr el archivo si no cambia, la semana siguiente siguen en la anterior. Para traerlas a la semana actual, recreá el volumen (`docker compose down -v`).

Es una migración repetible: Flyway la vuelve a correr cuando cambia su contenido. Usa ids fijos y
`ON CONFLICT DO NOTHING`, así que no duplica filas. Para empezar de cero: `docker compose down -v`.
