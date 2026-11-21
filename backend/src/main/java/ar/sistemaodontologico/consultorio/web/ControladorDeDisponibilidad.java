package ar.sistemaodontologico.consultorio.web;

import ar.sistemaodontologico.consultorio.application.ReemplazarDisponibilidad;
import ar.sistemaodontologico.consultorio.domain.Franja;
import ar.sistemaodontologico.consultorio.domain.GrillaSemanal;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET/PUT /api/v1/disponibilidad} (US-011, design D10): horas {@code "HH:mm"} locales a la zona del
 * consultorio. El {@code PUT} reemplaza la grilla completa. Día, orden y solapamiento los valida el dominio, para
 * responder {@code FRANJA_INVALIDA} o {@code FRANJAS_SOLAPADAS}.
 */
@RestController
@RequestMapping("/api/v1/disponibilidad")
class ControladorDeDisponibilidad {

    /** Siete días con varias franjas cada uno sobra; el tope evita cuerpos enormes. */
    static final int MAXIMO_DE_FRANJAS = 70;

    record FranjaDto(
            @NotNull Integer diaSemana,
            @NotNull @JsonFormat(pattern = "HH:mm[:ss]") LocalTime horaInicio,
            @NotNull @JsonFormat(pattern = "HH:mm[:ss]") LocalTime horaFin) {

        static FranjaDto de(Franja franja) {
            return new FranjaDto(franja.diaSemana(), franja.inicio(), franja.fin());
        }
    }

    record GrillaRequest(@NotNull @Size(max = MAXIMO_DE_FRANJAS) List<@NotNull @Valid FranjaDto> franjas) {
    }

    record GrillaResponse(List<FranjaResponse> franjas) {

        static GrillaResponse de(GrillaSemanal grilla) {
            return new GrillaResponse(grilla.franjas().stream().map(FranjaResponse::de).toList());
        }
    }

    record FranjaResponse(int diaSemana, @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
            @JsonFormat(pattern = "HH:mm") LocalTime horaFin) {

        static FranjaResponse de(Franja franja) {
            return new FranjaResponse(franja.diaSemana(), franja.inicio(), franja.fin());
        }
    }

    private final ReemplazarDisponibilidad disponibilidad;

    ControladorDeDisponibilidad(ReemplazarDisponibilidad disponibilidad) {
        this.disponibilidad = disponibilidad;
    }

    @GetMapping
    GrillaResponse consultar() {
        return GrillaResponse.de(disponibilidad.consultar());
    }

    @PutMapping
    GrillaResponse reemplazar(@Valid @RequestBody GrillaRequest pedido) {
        List<Franja> franjas = pedido.franjas().stream()
                .map(franja -> new Franja(franja.diaSemana(), franja.horaInicio(), franja.horaFin()))
                .toList();
        return GrillaResponse.de(disponibilidad.reemplazar(franjas));
    }
}
