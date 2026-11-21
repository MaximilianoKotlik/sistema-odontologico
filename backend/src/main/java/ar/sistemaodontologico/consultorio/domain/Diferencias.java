package ar.sistemaodontologico.consultorio.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Campos que cambiaron entre dos estados, con su valor anterior y nuevo (RN-AD-05, design D4). Si no cambió
 * ninguno, el caso de uso no publica el evento.
 */
public record Diferencias(Map<String, Object> anterior, Map<String, Object> nuevo) {

    public static Diferencias entre(Map<String, Object> antes, Map<String, Object> despues) {
        Map<String, Object> anterior = new LinkedHashMap<>();
        Map<String, Object> nuevo = new LinkedHashMap<>();
        despues.forEach((campo, valor) -> {
            Object previo = antes.get(campo);
            if (!Objects.equals(previo, valor)) {
                anterior.put(campo, previo);
                nuevo.put(campo, valor);
            }
        });
        return new Diferencias(Collections.unmodifiableMap(anterior), Collections.unmodifiableMap(nuevo));
    }

    public boolean hayCambios() {
        return !nuevo.isEmpty();
    }
}
