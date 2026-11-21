package ar.sistemaodontologico.arquitectura.fixtures.auditoria.application;

/** Fixture: clase del módulo de auditoría (la usan sus propias clases, nunca otros módulos). */
public class RegistroDirectoDePrueba {

    public void registrar(String accion) {
        new ValidadorInternoDePrueba().validar(accion);
    }
}
