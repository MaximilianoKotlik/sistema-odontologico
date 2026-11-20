package ar.sistemaodontologico.auth.application;

import java.time.Duration;

/** Vigencias configuradas de la sesión (RN-AU-02): access de 15 min y refresh de 14 días por defecto. */
public record VigenciasDeSesion(Duration access, Duration refresh) {
}
