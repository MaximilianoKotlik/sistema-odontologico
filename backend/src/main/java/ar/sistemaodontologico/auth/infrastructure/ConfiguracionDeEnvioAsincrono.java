package ar.sistemaodontologico.auth.infrastructure;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Ejecutor acotado para los emails de autenticación (design D4): 2 hilos y una cola de 100. Si la cola se llena,
 * el email se descarta (y queda en el log) en lugar de frenar la respuesta de quien pidió la recuperación.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
class ConfiguracionDeEnvioAsincrono {

    static final String EJECUTOR_DE_EMAILS = "ejecutorDeEmails";

    @Bean(EJECUTOR_DE_EMAILS)
    ThreadPoolTaskExecutor ejecutorDeEmails() {
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(2);
        ejecutor.setMaxPoolSize(2);
        ejecutor.setQueueCapacity(100);
        ejecutor.setThreadNamePrefix("emails-");
        ejecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy() {
            @Override
            public void rejectedExecution(Runnable tarea, ThreadPoolExecutor ejecutor) {
                org.slf4j.LoggerFactory.getLogger(ConfiguracionDeEnvioAsincrono.class)
                        .warn("Cola de emails llena: se descartó un envío");
            }
        });
        return ejecutor;
    }
}
