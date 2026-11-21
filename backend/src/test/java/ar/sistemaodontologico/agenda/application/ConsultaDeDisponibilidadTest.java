package ar.sistemaodontologico.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static pruebas.agenda.AgendaDePrueba.lunesA;

import ar.sistemaodontologico.agenda.domain.DisponibilidadSemanal;
import ar.sistemaodontologico.agenda.domain.FranjaSemanal;
import ar.sistemaodontologico.agenda.domain.Intervalo;
import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pruebas.agenda.AgendaDePrueba;

/**
 * La disponibilidad del consultorio para el dominio de agenda, armada con la grilla y la zona de C-08 (C-11, tarea
 * 2.3; design D1).
 */
class ConsultaDeDisponibilidadTest extends IntegracionPostgresTest {

    @Autowired
    private ConsultaDeDisponibilidad consulta;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void laGrillaDelConsultorioEnSuZona() {
        AgendaDePrueba agenda = AgendaDePrueba.nueva(jdbc).franja(1, "13:00", "14:00");

        DisponibilidadSemanal disponibilidad = consulta.de(new ConsultorioId(agenda.consultorio()));

        assertThat(disponibilidad.zona()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
        assertThat(disponibilidad.franjasDe(DayOfWeek.MONDAY))
                .containsExactly(new FranjaSemanal(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(14, 0)));
        assertThat(disponibilidad.franjasDe(DayOfWeek.SATURDAY)).isEmpty();
        assertThat(disponibilidad.cubre(new Intervalo(lunesA("13:30"), lunesA("14:00")))).isTrue();
    }

    @Test
    void otraZonaHoraria() {
        AgendaDePrueba agenda = AgendaDePrueba.nueva(jdbc);
        jdbc.update("UPDATE consultorio SET zona_horaria = 'America/Argentina/Cordoba' WHERE id = ?",
                agenda.consultorio());

        assertThat(consulta.de(new ConsultorioId(agenda.consultorio())).zona())
                .isEqualTo(ZoneId.of("America/Argentina/Cordoba"));
    }
}
