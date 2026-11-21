package ar.sistemaodontologico.consultorio.web;

import ar.sistemaodontologico.consultorio.domain.ReglasDeReserva;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** El paso de huecos es 5, 10, 15, 20 o 30 minutos; el error sale como {@code PASO_DE_HUECOS} en el campo. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasoDeHuecos.Validador.class)
@interface PasoDeHuecos {

    String message() default "El paso de huecos tiene que ser 5, 10, 15, 20 o 30 minutos";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<PasoDeHuecos, Integer> {

        @Override
        public boolean isValid(Integer valor, ConstraintValidatorContext contexto) {
            return valor == null || ReglasDeReserva.PASOS_PERMITIDOS.contains(valor);
        }
    }
}
