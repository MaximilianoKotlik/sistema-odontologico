package ar.sistemaodontologico.auth.application;

/**
 * Se superó un límite de solicitudes (C-05, design D8). {@code auth.web} lo traduce a 429
 * {@code DEMASIADAS_SOLICITUDES} con {@code Retry-After}. No lleva la clave (email o usuario) en el mensaje.
 */
public class LimiteExcedido extends RuntimeException {

    private final long segundosParaReintentar;

    public LimiteExcedido(long segundosParaReintentar) {
        super("Se superó el límite de solicitudes", null, false, false);
        this.segundosParaReintentar = segundosParaReintentar;
    }

    public long segundosParaReintentar() {
        return segundosParaReintentar;
    }
}
