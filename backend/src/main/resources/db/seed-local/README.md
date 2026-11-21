# Seed local (solo desarrollo)

`R__demo.sql` carga un consultorio de demo cuando el backend arranca con el perfil `local`
(`docker compose up --build`). `application-local.yml` agrega esta carpeta a `spring.flyway.locations`;
con el perfil por defecto o en los tests no se carga (`SeedLocalTest`).

| Dato | Valor |
|------|-------|
| Consultorio | "Consultorio Demo", slug `demo` |
| Usuario | `demo@consultorio.test` |
| Contraseña | `demo-local-2026` (**solo para desarrollo local**) |
| Prestaciones | Consulta 30', Limpieza 45', Extracción 60', Control 20' |
| Disponibilidad | lunes a viernes, 09:00–13:00 y 15:00–19:00 |

Todos los datos son ficticios (regla dura 17). Si cambiás la contraseña, regenerá el hash BCrypt de costo 12
y actualizá `SeedLocalTest`, que verifica que el hash corresponde a esta contraseña.

Es una migración repetible: Flyway la vuelve a correr cuando cambia su contenido. Usa ids fijos y
`ON CONFLICT DO NOTHING`, así que no duplica filas. Para empezar de cero: `docker compose down -v`.
