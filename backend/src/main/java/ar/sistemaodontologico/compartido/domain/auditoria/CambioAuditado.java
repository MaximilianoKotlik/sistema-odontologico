package ar.sistemaodontologico.compartido.domain.auditoria;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cambio que un caso de uso pide auditar (spec auditoria, RN-AD-01, RN-AD-05). El instante lo pone el
 * publicador con el reloj del sistema. Los valores guardan solo los campos modificados y nunca secretos.
 *
 * @param valorAnterior campos antes del cambio; {@code null} en un alta
 * @param valorNuevo campos después del cambio; {@code null} en una baja
 * @param ip IP de origen, si la operación vino de una petición
 */
public record CambioAuditado(
        ConsultorioId consultorio,
        Actor actor,
        String entidad,
        UUID entidadId,
        String accion,
        Map<String, Object> valorAnterior,
        Map<String, Object> valorNuevo,
        String ip) {

    private static final Pattern FORMATO_ACCION = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");

    public CambioAuditado {
        Objects.requireNonNull(consultorio, "consultorio");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(entidad, "entidad");
        Objects.requireNonNull(entidadId, "entidadId");
        Objects.requireNonNull(accion, "accion");
        if (!FORMATO_ACCION.matcher(accion).matches()) {
            throw new IllegalArgumentException("La acción debe ser MAYUSCULAS_CON_GUIONES_BAJOS");
        }
        valorAnterior = copiaSinSecretos(valorAnterior);
        valorNuevo = copiaSinSecretos(valorNuevo);
    }

    private static Map<String, Object> copiaSinSecretos(Map<String, Object> valores) {
        if (valores == null) {
            return null;
        }
        rechazarSecretos(valores);
        return Collections.unmodifiableMap(new LinkedHashMap<>(valores));
    }

    private static void rechazarSecretos(Map<?, ?> valores) {
        for (Map.Entry<?, ?> entrada : valores.entrySet()) {
            String clave = String.valueOf(entrada.getKey()).toLowerCase(Locale.ROOT);
            if (esSecreto(clave)) {
                // No se incluye el valor en el mensaje: es justamente el secreto.
                throw new IllegalArgumentException("Un evento de auditoría no puede contener el campo " + clave);
            }
            if (entrada.getValue() instanceof Map<?, ?> anidado) {
                rechazarSecretos(anidado);
            }
        }
    }

    // Claves exactas y sufijos que identifican un secreto (spec auditoria). La comparación es en minúsculas.
    private static final java.util.Set<String> CLAVES_SECRETAS =
            java.util.Set.of("password", "contrasena", "contraseña", "token", "secret");
    private static final java.util.List<String> SUFIJOS_SECRETOS = java.util.List.of("_token", "_hash", "_secret");

    private static boolean esSecreto(String clave) {
        return CLAVES_SECRETAS.contains(clave) || SUFIJOS_SECRETOS.stream().anyMatch(clave::endsWith);
    }
}
