package ar.sistemaodontologico.auditoria.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.util.List;
import java.util.Map;

/** Error 400 {@code VALIDACION} con el campo del filtro que falló (C-18, spec consulta-auditoria). */
public final class ErrorDeFiltro {

    private ErrorDeFiltro() {
    }

    public static ValidacionDeNegocio en(String campo) {
        return new ValidacionDeNegocio("VALIDACION", "Hay datos inválidos",
                Map.of("errores", List.of(Map.of("campo", campo, "codigo", "INVALIDO"))));
    }
}
