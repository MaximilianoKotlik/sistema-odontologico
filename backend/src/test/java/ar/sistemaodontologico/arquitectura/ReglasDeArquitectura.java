package ar.sistemaodontologico.arquitectura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;

/** Reglas de capas de knowledge-base/08_arquitectura_propuesta.md. */
public final class ReglasDeArquitectura {

    private static final String INFRAESTRUCTURA = "infrastructure";

    private ReglasDeArquitectura() {
    }

    /** La capa {@code domain} no depende de Spring ni de la API de persistencia. */
    public static ArchRule dominioSinFramework() {
        return noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")
                .as("la capa domain no depende de Spring ni de jakarta.persistence")
                .allowEmptyShould(true);
    }

    private static final java.util.List<String> PREFIJOS_DE_CONSULTA =
            java.util.List.of("buscar", "listar", "existe", "contar", "eliminar");
    private static final String CONSULTORIO_ID = "ar.sistemaodontologico.compartido.domain.ConsultorioId";

    /**
     * Todo repositorio de negocio filtra por consultorio (C-02, design D9; regla dura 6): en las clases
     * {@code *Repositorio*}, cada método público {@code buscar*}, {@code listar*}, {@code existe*},
     * {@code contar*} o {@code eliminar*} recibe un {@code ConsultorioId}. Las excepciones se listan por
     * nombre simple de la clase y cada una cita la decisión que la justifica.
     */
    public static ArchRule repositoriosSiempreConTenant(java.util.Set<String> exceptuados) {
        return classes()
                .that().haveSimpleNameContaining("Repositorio")
                .should(consultarSiempreConConsultorio(exceptuados))
                .as("los repositorios de negocio reciben ConsultorioId en todo método de consulta")
                .allowEmptyShould(true);
    }

    private static ArchCondition<JavaClass> consultarSiempreConConsultorio(java.util.Set<String> exceptuados) {
        return new ArchCondition<>("recibir ConsultorioId en todo método de consulta") {
            @Override
            public void check(JavaClass clase, ConditionEvents eventos) {
                if (exceptuada(clase, null, exceptuados)) {
                    return;
                }
                clase.getMethods().stream()
                        .filter(metodo -> metodo.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC))
                        .filter(metodo -> PREFIJOS_DE_CONSULTA.stream().anyMatch(metodo.getName()::startsWith))
                        .filter(metodo -> !exceptuada(clase, metodo.getName(), exceptuados))
                        .filter(metodo -> metodo.getRawParameterTypes().stream()
                                .noneMatch(tipo -> tipo.getFullName().equals(CONSULTORIO_ID)))
                        .forEach(metodo -> eventos.add(SimpleConditionEvent.violated(metodo,
                                clase.getSimpleName() + "." + metodo.getName() + " no recibe ConsultorioId")));
            }
        };
    }

    /**
     * Una excepción es {@code "Interfaz"} (toda la interfaz) o {@code "Interfaz.metodo"} (solo ese método). Cubre la
     * interfaz nombrada y las clases que la implementan, no cualquier clase cuyo nombre empiece igual: un futuro
     * {@code ConsultorioRepositorioDePrestaciones} sigue sujeto a la regla (revisión de Mati en #16).
     */
    private static boolean exceptuada(JavaClass clase, String metodo, java.util.Set<String> exceptuados) {
        return exceptuados.stream().anyMatch(excepcion -> {
            int punto = excepcion.indexOf('.');
            String nombreExceptuado = punto < 0 ? excepcion : excepcion.substring(0, punto);
            boolean esOImplementa = clase.getSimpleName().equals(nombreExceptuado)
                    || clase.getAllRawInterfaces().stream().anyMatch(i -> i.getSimpleName().equals(nombreExceptuado));
            if (!esOImplementa) {
                return false;
            }
            return punto < 0 || (metodo != null && metodo.equals(excepcion.substring(punto + 1)));
        });
    }

    /**
     * Las interfaces de Spring Data se usan solo dentro de {@code infrastructure}: si llegan a
     * {@code application}, exponen {@code findById} y {@code findAll} sin tenant (C-02, design D9).
     */
    public static ArchRule springDataSoloEnInfraestructura() {
        return noClasses()
                .that().resideOutsideOfPackage("..infrastructure..")
                .should().dependOnClassesThat().areAssignableTo("org.springframework.data.repository.Repository")
                .as("Spring Data se usa solo en la capa infrastructure")
                .allowEmptyShould(true);
    }

    /**
     * La hora sale solo del {@link java.time.Clock} inyectado (C-02, design D2): nadie llama a los
     * {@code now()} sin argumentos de java.time, que usan el reloj del sistema y no se pueden fijar en tests.
     */
    public static ArchRule horaSoloDesdeElClock() {
        return noClasses()
                .should().callMethod(java.time.Instant.class, "now")
                .orShould().callMethod(java.time.LocalDate.class, "now")
                .orShould().callMethod(java.time.LocalDateTime.class, "now")
                .orShould().callMethod(java.time.LocalTime.class, "now")
                .orShould().callMethod(java.time.ZonedDateTime.class, "now")
                .orShould().callMethod(java.time.OffsetDateTime.class, "now")
                .as("la hora se toma solo del Clock inyectado, nunca con now() sin argumentos")
                .allowEmptyShould(true);
    }

    /**
     * Los módulos auditan solo por el puerto {@code PublicadorDeAuditoria} de {@code compartido}: ninguna clase
     * fuera de {@code auditoria} depende del módulo de auditoría (C-03, design D-3).
     */
    public static ArchRule auditoriaSoloPorElPuerto(String paqueteRaiz) {
        String auditoria = paqueteRaiz + ".auditoria..";
        return noClasses()
                .that().resideInAPackage(paqueteRaiz + "..")
                .and().resideOutsideOfPackage(auditoria)
                .should().dependOnClassesThat().resideInAPackage(auditoria)
                .as("ningún módulo fuera de auditoria depende de auditoria: se audita por el puerto de compartido")
                .allowEmptyShould(true);
    }

    /** Ningún módulo usa clases de la capa {@code infrastructure} de otro módulo. */
    public static ArchRule infraestructuraPrivadaDelModulo(String paqueteRaiz) {
        return classes()
                .that().resideInAPackage(paqueteRaiz + "..")
                .should(noUsarInfraestructuraAjena(paqueteRaiz))
                .as("ningún módulo accede a la capa infrastructure de otro módulo")
                .allowEmptyShould(true);
    }

    private static ArchCondition<JavaClass> noUsarInfraestructuraAjena(String paqueteRaiz) {
        return new ArchCondition<>("no usar la infraestructura de otro módulo") {
            @Override
            public void check(JavaClass origen, ConditionEvents eventos) {
                Optional<String> moduloOrigen = modulo(origen, paqueteRaiz);
                for (Dependency dependencia : origen.getDirectDependenciesFromSelf()) {
                    JavaClass destino = dependencia.getTargetClass();
                    Optional<String> moduloDestino = modulo(destino, paqueteRaiz);
                    boolean esInfraestructuraAjena = moduloDestino.isPresent()
                            && esInfraestructura(destino, paqueteRaiz)
                            && !moduloDestino.equals(moduloOrigen);
                    if (esInfraestructuraAjena) {
                        eventos.add(SimpleConditionEvent.violated(dependencia, dependencia.getDescription()));
                    }
                }
            }
        };
    }

    private static Optional<String> modulo(JavaClass clase, String paqueteRaiz) {
        String paquete = clase.getPackageName();
        if (!paquete.startsWith(paqueteRaiz + ".")) {
            return Optional.empty();
        }
        String resto = paquete.substring(paqueteRaiz.length() + 1);
        int punto = resto.indexOf('.');
        return Optional.of(punto < 0 ? resto : resto.substring(0, punto));
    }

    private static boolean esInfraestructura(JavaClass clase, String paqueteRaiz) {
        String relativo = clase.getPackageName().substring(paqueteRaiz.length() + 1);
        return relativo.contains("." + INFRAESTRUCTURA);
    }
}
