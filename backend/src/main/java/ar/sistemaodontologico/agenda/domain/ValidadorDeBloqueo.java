package ar.sistemaodontologico.agenda.domain;

import ar.sistemaodontologico.compartido.domain.error.ConflictoDeNegocio;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Un bloqueo no pisa turnos activos, tampoco sobreturnos (RN-AG-05, spec bloqueos). Devuelve **todos** los turnos en
 * conflicto, para que el odontólogo los cancele o los reprograme primero. Los bloqueos superpuestos son válidos
 * (design D10), así que no mira otros bloqueos.
 */
public final class ValidadorDeBloqueo {

    public static final String CODIGO_CON_TURNOS = "BLOQUEO_CON_TURNOS";

    private ValidadorDeBloqueo() {
    }

    public static void validar(Bloqueo bloqueo, Collection<Turno> turnos) {
        List<String> enConflicto = turnos.stream()
                .filter(Turno::estaActivo)
                .filter(turno -> turno.intervalo().seSolapaCon(bloqueo.intervalo()))
                .map(turno -> turno.id().valor().toString())
                .toList();
        if (!enConflicto.isEmpty()) {
            throw new ConflictoDeNegocio(CODIGO_CON_TURNOS, "El bloqueo se superpone con turnos activos",
                    Map.of(ValidadorDeSolapamiento.PROPIEDAD_TURNOS, enConflicto));
        }
    }
}
