package ar.sistemaodontologico.agenda.domain;

import ar.sistemaodontologico.compartido.domain.telefono.NormalizadorDeTelefono;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Datos mínimos del paciente dentro del consultorio (spec pacientes): identidad por {@link Dni} (DD-24), teléfono de
 * contacto obligatorio en E.164 y sin unicidad (DD-32), email opcional. Sin datos clínicos (Ley 25.326). Mientras tiene
 * la revisión pendiente lleva lo {@linkplain DatosInformados informado} en la reserva que la disparó (DD-38); sin
 * revisión pendiente, {@code informados} es {@code null}. Ningún dato personal aparece en {@link #toString()}
 * (RN-GL-01).
 */
public record Paciente(PacienteId id, String nombre, String apellido, Dni dni, String telefonoE164, String email,
        OrigenPaciente origen, boolean requiereRevision, Consentimiento consentimiento, DatosInformados informados) {

    public static final int LARGO_MAXIMO_NOMBRE = 80;
    private static final int LARGO_MAXIMO_EMAIL = 254;
    /** Sintaxis simple: algo, una arroba, un dominio con punto y sin espacios. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Paciente {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dni, "dni");
        Objects.requireNonNull(origen, "origen");
        nombre = Textos.obligatorio(nombre, LARGO_MAXIMO_NOMBRE);
        apellido = Textos.obligatorio(apellido, LARGO_MAXIMO_NOMBRE);
        telefonoE164 = NormalizadorDeTelefono.normalizar(telefonoE164);
        email = normalizarEmail(email);
        // LG-03: quien reserva por el link acepta la política de privacidad.
        if (origen == OrigenPaciente.LINK && consentimiento == null) {
            throw Textos.invalido();
        }
        // DD-38: lo informado existe solo con la revisión pendiente (ck_paciente_informado).
        if (informados != null && !requiereRevision) {
            throw Textos.invalido();
        }
    }

    /** Un paciente nuevo, sin revisión pendiente. El DNI y el teléfono llegan como los escribió la persona. */
    public static Paciente nuevo(PacienteId id, String nombre, String apellido, String dni, String telefono,
            String email, OrigenPaciente origen, Consentimiento consentimiento) {
        return new Paciente(id, nombre, apellido, new Dni(dni), telefono, email, origen, false, consentimiento, null);
    }

    /**
     * El mismo paciente, con revisión pendiente y sin tocar sus datos (RN-RP-03), guardando lo informado en la reserva
     * que la disparó. Si ya estaba marcado, lo informado se reemplaza: queda la última reserva (DD-38).
     */
    public Paciente marcadoParaRevision(DatosInformados informadosEnLaReserva) {
        Objects.requireNonNull(informadosEnLaReserva, "informadosEnLaReserva");
        return new Paciente(id, nombre, apellido, dni, telefonoE164, email, origen, true, consentimiento,
                informadosEnLaReserva);
    }

    /**
     * Si los datos que llegan para su DNI no coinciden con los guardados, sin distinguir mayúsculas ni espacios en los
     * extremos. C-15 lo usa para reutilizar el paciente sin sobrescribirlo y marcarlo para revisión (DD-24).
     */
    public boolean datosDifierenDe(String otroNombre, String otroApellido) {
        return !iguales(nombre, otroNombre) || !iguales(apellido, otroApellido);
    }

    @Override
    public String toString() {
        return "Paciente[id=" + id.valor() + ", origen=" + origen + ", requiereRevision=" + requiereRevision + "]";
    }

    private static boolean iguales(String guardado, String recibido) {
        return recibido != null && guardado.equalsIgnoreCase(recibido.strip());
    }

    static String normalizarEmail(String email) {
        String recortado = Textos.opcional(email, LARGO_MAXIMO_EMAIL);
        if (recortado == null) {
            return null;
        }
        if (!EMAIL.matcher(recortado).matches()) {
            throw Textos.invalido();
        }
        return recortado.toLowerCase(Locale.ROOT);
    }
}
