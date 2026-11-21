package ar.sistemaodontologico.agenda.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.Instant;
import java.util.Objects;

/**
 * Intervalo semiabierto {@code [inicio, fin)} de instantes (design D3): uno que termina a las 10:30 no se solapa con
 * uno que empieza a las 10:30.
 */
public record Intervalo(Instant inicio, Instant fin) {

    public static final String CODIGO_HORARIO_INVALIDO = "HORARIO_INVALIDO";
    private static final long SEGUNDOS_DE_LA_GRILLA = 300;

    public Intervalo {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fin, "fin");
        if (!fin.isAfter(inicio)) {
            throw new ValidacionDeNegocio(CODIGO_HORARIO_INVALIDO, "El fin tiene que ser posterior al inicio");
        }
    }

    public boolean seSolapaCon(Intervalo otro) {
        return inicio.isBefore(otro.fin) && otro.inicio.isBefore(fin);
    }

    /** Si {@code otro} cae completo dentro de este intervalo. */
    public boolean contiene(Intervalo otro) {
        return !otro.inicio.isBefore(inicio) && !otro.fin.isAfter(fin);
    }

    /** Si uno termina justo cuando empieza el otro. */
    public boolean tocaA(Intervalo otro) {
        return fin.equals(otro.inicio) || otro.fin.equals(inicio);
    }

    /** RN-AG-08: inicio y fin en múltiplos de 5 minutos, sin segundos ni fracciones. */
    public boolean estaAlineadoA5Minutos() {
        return estaEnLaGrilla(inicio) && estaEnLaGrilla(fin);
    }

    static boolean estaEnLaGrilla(Instant instante) {
        return instante.getNano() == 0 && Math.floorMod(instante.getEpochSecond(), SEGUNDOS_DE_LA_GRILLA) == 0;
    }
}
