package ar.sistemaodontologico.consultorio.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita las tareas programadas: hoy solo {@link TareaDeVencimiento} (C-20, design D2). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ConfiguracionDeTareasDelPlan {
}
