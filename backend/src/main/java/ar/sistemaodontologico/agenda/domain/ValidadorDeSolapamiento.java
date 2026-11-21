package ar.sistemaodontologico.agenda.domain;

import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Dónde puede caer un turno (RN-AG-01, 03 y 04; design D4). Recibe los turnos y bloqueos del ámbito ya filtrados por
 * quien llama (design D6): en v1, los del consultorio. Valida en orden fijo, para que el error sea estable cuando hay
 * varios problemas a la vez:
 * <ol>
 *   <li>bloqueo ({@code HORARIO_BLOQUEADO}), también para el sobreturno;</li>
 *   <li>disponibilidad ({@code FUERA_DE_DISPONIBILIDAD}), salvo el sobreturno;</li>
 *   <li>solapamiento con turnos activos que no son sobreturno ({@code TURNO_SOLAPADO}), salvo el sobreturno.</li>
 * </ol>
 */
public final class ValidadorDeSolapamiento {

    public static final String CODIGO_SOLAPADO = "TURNO_SOLAPADO";
    public static final String CODIGO_BLOQUEADO = "HORARIO_BLOQUEADO";
    public static final String CODIGO_FUERA_DE_DISPONIBILIDAD = "FUERA_DE_DISPONIBILIDAD";
    public static final String PROPIEDAD_TURNOS = "turnosEnConflicto";
    public static final String PROPIEDAD_BLOQUEOS = "bloqueosEnConflicto";

    private ValidadorDeSolapamiento() {
    }

    public static void validar(Turno candidato, Collection<Turno> turnos, Collection<Bloqueo> bloqueos,
            DisponibilidadSemanal disponibilidad) {
        Intervalo horario = candidato.intervalo();
        List<String> bloqueosEnConflicto = bloqueos.stream()
                .filter(bloqueo -> bloqueo.intervalo().seSolapaCon(horario))
                .map(bloqueo -> bloqueo.id().valor().toString())
                .toList();
        if (!bloqueosEnConflicto.isEmpty()) {
            throw new ConflictoDeNegocio(CODIGO_BLOQUEADO, "El horario está bloqueado",
                    Map.of(PROPIEDAD_BLOQUEOS, bloqueosEnConflicto));
        }
        if (candidato.esSobreturno()) {
            return;
        }
        if (!disponibilidad.cubre(horario)) {
            throw new ConflictoDeNegocio(CODIGO_FUERA_DE_DISPONIBILIDAD, "El horario está fuera de la disponibilidad");
        }
        List<String> turnosEnConflicto = turnos.stream()
                .filter(otro -> !otro.id().equals(candidato.id()))
                .filter(otro -> otro.estaActivo() && !otro.esSobreturno())
                .filter(otro -> otro.intervalo().seSolapaCon(horario))
                .map(otro -> otro.id().valor().toString())
                .toList();
        if (!turnosEnConflicto.isEmpty()) {
            throw new ConflictoDeNegocio(CODIGO_SOLAPADO, "El horario se superpone con otro turno",
                    Map.of(PROPIEDAD_TURNOS, turnosEnConflicto));
        }
    }
}
