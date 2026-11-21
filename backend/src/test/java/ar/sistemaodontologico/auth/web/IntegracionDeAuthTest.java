package ar.sistemaodontologico.auth.web;

import ar.sistemaodontologico.compartido.IntegracionPostgresTest;
import ar.sistemaodontologico.compartido.application.EnviadorDeEmails;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pruebas.reloj.ConfiguracionDeRelojDePrueba;
import pruebas.reloj.RelojDePrueba;
import pruebas.seguridad.ClienteDeAuth;
import pruebas.seguridad.ControladorPrivadoDePrueba;

/**
 * Base de los tests de integración de C-05: reloj avanzable, controlador privado de prueba y un
 * {@link EnviadorDeEmails} falso. Todos los que la extienden comparten <em>un</em> contexto de Spring, así la suite no
 * agota las conexiones del PostgreSQL de Testcontainers con un pool por clase.
 */
@Import({ConfiguracionDeRelojDePrueba.class, ControladorPrivadoDePrueba.class})
abstract class IntegracionDeAuthTest extends IntegracionPostgresTest {

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected RelojDePrueba reloj;

    @MockitoBean
    protected EnviadorDeEmails enviador;

    protected ClienteDeAuth auth;

    @BeforeEach
    void clienteDeAuth() {
        auth = new ClienteDeAuth(http, ORIGEN_FRONTEND);
    }
}
