package ar.sistemaodontologico.reserva.application;

import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Errores estables de la API pública (spec reserva-publica y gestion-turno-paciente). */
final class ErroresPublicos {

    static final String RESERVA_NO_DISPONIBLE = "RESERVA_NO_DISPONIBLE";
    static final String HUECO_NO_DISPONIBLE = "HUECO_NO_DISPONIBLE";
    static final String FUERA_DE_PLAZO = "FUERA_DE_PLAZO";

    private ErroresPublicos() {
    }

    static ConflictoDeNegocio reservaNoDisponible(String telefono) {
        return new ConflictoDeNegocio(RESERVA_NO_DISPONIBLE, "El consultorio no toma turnos online", conTelefono(telefono));
    }

    static ConflictoDeNegocio fueraDePlazo(String telefono) {
        return new ConflictoDeNegocio(FUERA_DE_PLAZO, "Ya pasó el plazo para cambiar este turno", conTelefono(telefono));
    }

    static ConflictoDeNegocio huecoNoDisponible(List<Instant> huecosDelDia) {
        return new ConflictoDeNegocio(HUECO_NO_DISPONIBLE, "Ese horario ya no está disponible",
                Map.of("huecos", huecosDelDia.stream().map(Instant::toString).toList()));
    }

    /** El mismo error para todo token inválido: no dice la causa (design D3). */
    static RecursoNoEncontrado tokenInvalido() {
        return new RecursoNoEncontrado("turno");
    }

    private static Map<String, Object> conTelefono(String telefono) {
        return telefono == null ? Map.of() : Map.of("telefonoContacto", telefono);
    }
}
