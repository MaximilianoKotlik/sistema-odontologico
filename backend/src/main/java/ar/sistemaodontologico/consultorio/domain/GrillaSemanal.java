package ar.sistemaodontologico.consultorio.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Franjas de atención de la semana, ordenadas por día e inicio y sin solaparse (US-011, spec disponibilidad-semanal,
 * design D5). Dos grillas con las mismas franjas en otro orden son iguales: así un guardado idéntico no se audita.
 */
public record GrillaSemanal(List<Franja> franjas) {

    public static final String CODIGO_SOLAPADAS = "FRANJAS_SOLAPADAS";
    public static final GrillaSemanal VACIA = new GrillaSemanal(List.of());

    private static final Comparator<Franja> POR_DIA_E_INICIO =
            Comparator.comparingInt(Franja::diaSemana).thenComparing(Franja::inicio).thenComparing(Franja::fin);

    public GrillaSemanal {
        List<Franja> ordenadas = new ArrayList<>(franjas);
        ordenadas.sort(POR_DIA_E_INICIO);
        rechazarSolapadas(ordenadas);
        franjas = List.copyOf(ordenadas);
    }

    /** Las grillas completas para la auditoría: {@code {franjas: [...]}} (design D4). */
    public Map<String, Object> comoMapaAuditable() {
        return Map.of("franjas", franjas.stream().map(Franja::comoMapaAuditable).toList());
    }

    /** Ordenadas, alcanza con comparar cada franja con la siguiente del mismo día. */
    private static void rechazarSolapadas(List<Franja> ordenadas) {
        TreeSet<Integer> dias = new TreeSet<>();
        for (int i = 1; i < ordenadas.size(); i++) {
            Franja anterior = ordenadas.get(i - 1);
            Franja actual = ordenadas.get(i);
            if (anterior.seSolapaCon(actual)) {
                dias.add(actual.diaSemana());
            }
        }
        if (!dias.isEmpty()) {
            throw new ValidacionDeNegocio(CODIGO_SOLAPADAS, "Hay franjas que se solapan el mismo día",
                    Map.of("dias", List.copyOf(dias)));
        }
    }
}
