package ar.sistemaodontologico.arquitectura.fixtures.exportacion.application;

import ar.sistemaodontologico.compartido.application.SesionActual;

/** Fixture: viola la regla, porque el streaming corre fuera de la petición (C-19, design D2). */
public class ExportacionQueLeeLaSesion {

    private SesionActual sesion;
}
