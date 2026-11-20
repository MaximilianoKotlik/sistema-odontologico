package ar.sistemaodontologico.arquitectura.fixtures.auditoria.application;

/** Fixture: dependencia interna del módulo de auditoría, permitida. */
public class ValidadorInternoDePrueba {

    public void validar(String accion) {
        if (accion == null) {
            throw new IllegalArgumentException("accion");
        }
    }
}
