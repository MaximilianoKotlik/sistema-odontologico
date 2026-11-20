package ar.sistemaodontologico.auth.application;

/** Puerto: versión de los términos que se registra al aceptarlos (design D7, DD-23). Nunca la manda el cliente. */
@FunctionalInterface
public interface TerminosVigentes {

    String version();
}
