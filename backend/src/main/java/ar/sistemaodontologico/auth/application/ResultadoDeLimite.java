package ar.sistemaodontologico.auth.application;

/** Resultado de consumir un cupo. Si se rechaza, cuántos segundos esperar (valor de {@code Retry-After}). */
public record ResultadoDeLimite(boolean permitido, long segundosParaReintentar) {

    private static final ResultadoDeLimite PERMITIDO = new ResultadoDeLimite(true, 0);

    public static ResultadoDeLimite permitir() {
        return PERMITIDO;
    }

    public static ResultadoDeLimite rechazar(long segundosParaReintentar) {
        return new ResultadoDeLimite(false, Math.max(1, segundosParaReintentar));
    }
}
