package ar.sistemaodontologico.consultorio.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Franja semanal de atención, con horas locales a la zona del consultorio (RN-TE-05). Es el intervalo semiabierto
 * {@code [inicio, fin)}: una franja que termina a las 13:00 y otra que empieza a las 13:00 no se solapan (design D5).
 *
 * @param diaSemana ISO: 1 = lunes … 7 = domingo
 */
public record Franja(int diaSemana, LocalTime inicio, LocalTime fin) {

    public static final String CODIGO_INVALIDA = "FRANJA_INVALIDA";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    public Franja {
        if (diaSemana < 1 || diaSemana > 7 || inicio == null || fin == null || !fin.isAfter(inicio)
                || !enMultiploDe5(inicio) || !enMultiploDe5(fin)) {
            throw new ValidacionDeNegocio(CODIGO_INVALIDA,
                    "Cada franja necesita un día de 1 a 7 y un fin posterior al inicio, en múltiplos de 5 minutos");
        }
    }

    boolean seSolapaCon(Franja otra) {
        return diaSemana == otra.diaSemana && inicio.isBefore(otra.fin) && otra.inicio.isBefore(fin);
    }

    Map<String, Object> comoMapaAuditable() {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("dia_semana", diaSemana);
        campos.put("hora_inicio", inicio.format(HORA));
        campos.put("hora_fin", fin.format(HORA));
        return campos;
    }

    /** Extiende RN-AG-08 a las franjas (design D5): sin segundos y en minutos múltiplos de 5. */
    private static boolean enMultiploDe5(LocalTime hora) {
        return Objects.equals(hora.withSecond(0).withNano(0), hora) && hora.getMinute() % 5 == 0;
    }
}
