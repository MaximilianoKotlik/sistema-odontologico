package ar.sistemaodontologico.agenda.infrastructure;

import ar.sistemaodontologico.agenda.application.BusquedaDePacientesRepositorio;
import ar.sistemaodontologico.agenda.application.PacienteEncontrado;
import ar.sistemaodontologico.agenda.domain.CriterioDeBusqueda;
import ar.sistemaodontologico.agenda.domain.CriterioDeBusqueda.PorDigitos;
import ar.sistemaodontologico.agenda.domain.CriterioDeBusqueda.PorPalabras;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * El buscador de pacientes con JdbcClient (C-12, design D7). Las palabras van como prefijo contra el nombre y el
 * apellido en minúsculas y sin tildes ({@code unaccent}, V8): "perez" encuentra a "Pérez". Sin índice propio: la
 * búsqueda ya queda acotada a los pacientes de un consultorio. Los {@code %}, {@code _} y {@code \} que escribe el
 * usuario se escapan: no son comodines.
 */
@Repository
class BusquedaDePacientesRepositorioJdbc implements BusquedaDePacientesRepositorio {

    private final JdbcClient jdbc;

    BusquedaDePacientesRepositorioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PacienteEncontrado> listarCoincidencias(ConsultorioId consultorio, CriterioDeBusqueda criterio,
            int limite) {
        String condicion;
        List<String> patrones;
        switch (criterio) {
            case PorDigitos(String digitos) -> {
                condicion = "(dni LIKE ? ESCAPE '\\' OR telefono_e164 LIKE ? ESCAPE '\\')";
                patrones = List.of(sinComodines(digitos) + "%", "%" + sinComodines(digitos) + "%");
            }
            case PorPalabras(List<String> palabras) -> {
                condicion = "(" + String.join(" OR ", palabras.stream()
                        .map(palabra -> "lower(unaccent(apellido)) LIKE unaccent(?) ESCAPE '\\'"
                                + " OR lower(unaccent(nombre)) LIKE unaccent(?) ESCAPE '\\'")
                        .toList()) + ")";
                patrones = palabras.stream()
                        .flatMap(palabra -> java.util.stream.Stream.of(sinComodines(palabra) + "%",
                                sinComodines(palabra) + "%"))
                        .toList();
            }
        }
        JdbcClient.StatementSpec consulta = jdbc.sql("""
                SELECT id, nombre, apellido, dni, telefono_e164, requiere_revision
                  FROM paciente
                 WHERE consultorio_id = ? AND %s
                 ORDER BY lower(apellido), lower(nombre), id
                 LIMIT ?""".formatted(condicion))
                .param(consultorio.valor());
        for (String patron : patrones) {
            consulta = consulta.param(patron);
        }
        return consulta.param(limite)
                .query((fila, numero) -> new PacienteEncontrado(fila.getObject("id", UUID.class),
                        fila.getString("nombre"), fila.getString("apellido"), fila.getString("dni"),
                        fila.getString("telefono_e164"), fila.getBoolean("requiere_revision")))
                .list();
    }

    private static String sinComodines(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
