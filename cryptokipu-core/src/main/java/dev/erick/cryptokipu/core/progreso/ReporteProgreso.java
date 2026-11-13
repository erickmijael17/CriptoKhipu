package dev.erick.cryptokipu.core.progreso;

import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;

/** Progreso y cancelación de operaciones largas (hash de la llave, cifrado, Argon2). */
public interface ReporteProgreso {

    ReporteProgreso NINGUNO_MIJA = (_, _) -> { };

    /** {@code totalMija} puede ser -1 si no se conoce. */
    void avanzar(long hechoMija, long totalMija);

    default boolean cancelado() {
        return false;
    }

    /** Lanza {@code OperacionCancelada} si el usuario canceló. */
    default void verificarCancelacion() {
        if (cancelado()) {
            throw new ExcepcionBoveda(new ErrorBoveda.OperacionCancelada());
        }
    }
}
