package dev.erick.cryptokipu.core.crypto;

import java.io.Serial;

/**
 * El tag de AES-GCM no coincide: clave equivocada o datos alterados.
 * Las capas superiores la traducen a {@code IntegridadFallida} o {@code CredencialesIncorrectas}.
 */
public final class FalloAutenticacion extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;

    public FalloAutenticacion() {
        super("FalloAutenticacion", null, false, false);
    }
}
