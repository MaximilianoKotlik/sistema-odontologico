package ar.sistemaodontologico.auth.domain;

import ar.sistemaodontologico.compartido.domain.ConsultorioId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Estado de acceso del odontólogo: contraseña, versión de credenciales y bloqueo por fallos. Reglas puras e
 * inmutables; cada operación devuelve el nuevo estado (C-05, design D1 y D6, RN-AU-03).
 */
public record Usuario(UUID id, ConsultorioId consultorio, String email, String passwordHash, int credencialesVersion,
                      int intentosFallidos, Instant bloqueadoHasta, Instant passwordCambiadoAt) {

    public static final int FALLOS_PARA_BLOQUEAR = 5;
    public static final Duration DURACION_DEL_BLOQUEO = Duration.ofMinutes(15);

    public boolean estaBloqueado(Instant ahora) {
        return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
    }

    /** Minutos que faltan para que termine el bloqueo, redondeados hacia arriba (FL-01). */
    public long minutosDeBloqueoRestantes(Instant ahora) {
        if (!estaBloqueado(ahora)) {
            return 0;
        }
        long nanos = Duration.between(ahora, bloqueadoHasta).toNanos();
        long nanosPorMinuto = Duration.ofMinutes(1).toNanos();
        return (nanos + nanosPorMinuto - 1) / nanosPorMinuto;
    }

    /** Un fallo durante el bloqueo no lo extiende ni suma; al quinto fallo se bloquea y el contador vuelve a 0. */
    public Usuario registrarFallo(Instant ahora) {
        if (estaBloqueado(ahora)) {
            return this;
        }
        int fallos = intentosFallidos + 1;
        if (fallos >= FALLOS_PARA_BLOQUEAR) {
            return conAcceso(0, ahora.plus(DURACION_DEL_BLOQUEO));
        }
        return conAcceso(fallos, null);
    }

    public Usuario registrarExito() {
        return conAcceso(0, null);
    }

    /** Nueva contraseña: invalida los access tokens emitidos (sube la versión) y desbloquea la cuenta. */
    public Usuario establecerContrasena(String hashNuevo, Instant ahora) {
        return new Usuario(id, consultorio, email, hashNuevo, credencialesVersion + 1, 0, null, ahora);
    }

    private Usuario conAcceso(int fallos, Instant hasta) {
        return new Usuario(id, consultorio, email, passwordHash, credencialesVersion, fallos, hasta, passwordCambiadoAt);
    }

    @Override
    public String toString() {
        return "Usuario[id=" + id + ", consultorio=" + consultorio + ", intentosFallidos=" + intentosFallidos
                + ", bloqueadoHasta=" + bloqueadoHasta + "]";
    }
}
