package dev.erick.cryptokipu.core.error;

import java.io.Serial;
import java.util.Objects;

/**
 * Excepción no comprobada que transporta un {@link ErrorBoveda}.
 *
 * <p>El mensaje es solo el nombre del tipo de error, para que nunca aparezcan datos
 * en logs o trazas aunque alguien registre la excepción completa.
 */
public final class ExcepcionBoveda extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient ErrorBoveda errorMija;

    public ExcepcionBoveda(ErrorBoveda errorMija) {
        super(Objects.requireNonNull(errorMija, "errorMija").getClass().getSimpleName(), null, false, false);
        this.errorMija = errorMija;
    }

    public ErrorBoveda error() {
        return errorMija;
    }
}
