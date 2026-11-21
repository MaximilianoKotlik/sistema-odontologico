package ar.sistemaodontologico.plan.domain;

/** La fuente de planes falta o es inválida: no se publica nada, ni siquiera parcialmente (spec public-plans). */
public class CatalogoInvalido extends RuntimeException {

    public CatalogoInvalido(String motivo) {
        super(motivo);
    }

    public CatalogoInvalido(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
