package ar.sistemaodontologico.reserva.application;

import ar.sistemaodontologico.agenda.application.AgendaParaReserva;
import ar.sistemaodontologico.agenda.application.AgendaParaReserva.DiaConHuecos;
import ar.sistemaodontologico.agenda.application.AgendaParaReserva.ReglasDeHuecos;
import ar.sistemaodontologico.compartido.application.TenantContext;
import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import ar.sistemaodontologico.compartido.domain.error.RecursoNoEncontrado;
import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import ar.sistemaodontologico.consultorio.application.ConsultorioRepositorio;
import ar.sistemaodontologico.consultorio.domain.ConfiguracionDelConsultorio;
import ar.sistemaodontologico.consultorio.domain.ReglasDeReserva;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Lo común de las rutas públicas: fija el tenant desde el slug o desde el token, nunca desde el body ni la query
 * (spec reserva-publica), decide si el consultorio toma turnos (design D13) y calcula sus huecos (design D5).
 */
@Component
class ConsultorioPublico {

    private static final int DIAS_POR_DEFECTO = 14;
    private static final int DIAS_MAXIMOS = 31;
    private static final String PLAN_VENCIDO = "VENCIDO";

    private final ConsultorioRepositorio consultorios;
    private final AgendaParaReserva agenda;
    private final TenantContext tenant;
    private final Clock reloj;

    ConsultorioPublico(ConsultorioRepositorio consultorios, AgendaParaReserva agenda, TenantContext tenant, Clock reloj) {
        this.consultorios = consultorios;
        this.agenda = agenda;
        this.tenant = tenant;
        this.reloj = reloj;
    }

    ConfiguracionDelConsultorio porSlug(String slug) {
        ConsultorioId id = consultorios.buscarIdPorSlug(slug).orElseThrow(() -> new RecursoNoEncontrado("consultorio"));
        return porId(id);
    }

    ConfiguracionDelConsultorio porId(ConsultorioId id) {
        tenant.establecer(id);
        return consultorios.buscarConfiguracion(id).orElseThrow(() -> new RecursoNoEncontrado("consultorio"));
    }

    static boolean tomaTurnos(ConfiguracionDelConsultorio consultorio) {
        return consultorio.datos().reservaPublicaActiva() && !PLAN_VENCIDO.equals(consultorio.plan());
    }

    static void exigirQueTomeTurnos(ConfiguracionDelConsultorio consultorio) {
        if (!tomaTurnos(consultorio)) {
            throw ErroresPublicos.reservaNoDisponible(consultorio.datos().telefonoContacto());
        }
    }

    static ZoneId zona(ConfiguracionDelConsultorio consultorio) {
        return ZoneId.of(consultorio.datos().zonaHoraria());
    }

    Instant ahora() {
        return reloj.instant();
    }

    /** Rango pedido, con los valores por defecto de design D5: de hoy a hoy + 13 días, máximo 31 días. */
    HuecosPublicos huecos(ConfiguracionDelConsultorio consultorio, int duracionMin, LocalDate desde, LocalDate hasta,
            Optional<UUID> sinTurno) {
        ZoneId zona = zona(consultorio);
        LocalDate inicio = desde != null ? desde : ahora().atZone(zona).toLocalDate();
        LocalDate fin = hasta != null ? hasta : inicio.plusDays(DIAS_POR_DEFECTO - 1L);
        if (fin.isBefore(inicio) || ChronoUnit.DAYS.between(inicio, fin) >= DIAS_MAXIMOS) {
            throw new ValidacionDeNegocio("VALIDACION", "El rango de fechas no es válido");
        }
        List<HuecosPublicos.Dia> dias = agenda
                .huecos(consultorio.id(), inicio, fin, reglas(consultorio, duracionMin), ahora(), sinTurno).stream()
                .map(dia -> new HuecosPublicos.Dia(dia.fecha(), dia.atiende(), dia.huecos()))
                .toList();
        return new HuecosPublicos(zona.getId(), dias);
    }

    /** Los huecos de la fecha local de ese instante. */
    List<Instant> huecosDelDia(ConfiguracionDelConsultorio consultorio, int duracionMin, Instant instante, Instant ahora,
            Optional<UUID> sinTurno) {
        LocalDate fecha = instante.atZone(zona(consultorio)).toLocalDate();
        return agenda.huecos(consultorio.id(), fecha, fecha, reglas(consultorio, duracionMin), ahora, sinTurno).stream()
                .map(DiaConHuecos::huecos)
                .findFirst()
                .orElse(List.of());
    }

    private static ReglasDeHuecos reglas(ConfiguracionDelConsultorio consultorio, int duracionMin) {
        ReglasDeReserva reglas = consultorio.datos().reglas();
        return new ReglasDeHuecos(duracionMin, reglas.pasoHuecosMin(), reglas.anticipacionReservaMin(),
                reglas.horizonteReservaDias());
    }
}
