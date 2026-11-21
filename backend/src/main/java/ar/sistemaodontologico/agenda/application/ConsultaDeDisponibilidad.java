package ar.sistemaodontologico.agenda.application;

import ar.sistemaodontologico.agenda.domain.DisponibilidadSemanal;
import ar.sistemaodontologico.agenda.domain.FranjaSemanal;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import ar.sistemaodontologico.consultorio.application.ConsultorioRepositorio;
import ar.sistemaodontologico.consultorio.application.DisponibilidadRepositorio;
import java.time.DayOfWeek;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Arma la {@link DisponibilidadSemanal} del consultorio con su grilla y su zona horaria de C-08 (design D1). Los
 * módulos hablan por {@code application}: el dominio de agenda no conoce la {@code GrillaSemanal}.
 */
@Component
public class ConsultaDeDisponibilidad {

    private final DisponibilidadRepositorio disponibilidad;
    private final ConsultorioRepositorio consultorios;

    public ConsultaDeDisponibilidad(DisponibilidadRepositorio disponibilidad, ConsultorioRepositorio consultorios) {
        this.disponibilidad = disponibilidad;
        this.consultorios = consultorios;
    }

    public DisponibilidadSemanal de(ConsultorioId consultorio) {
        ZoneId zona = consultorios.buscarConfiguracion(consultorio)
                .map(configuracion -> ZoneId.of(configuracion.datos().zonaHoraria()))
                .orElseThrow(() -> new RecursoNoEncontrado("consultorio"));
        return new DisponibilidadSemanal(disponibilidad.consultar(consultorio).franjas().stream()
                .map(franja -> new FranjaSemanal(DayOfWeek.of(franja.diaSemana()), franja.inicio(), franja.fin()))
                .toList(), zona);
    }
}
