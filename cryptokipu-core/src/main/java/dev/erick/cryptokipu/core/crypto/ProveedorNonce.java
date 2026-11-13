package dev.erick.cryptokipu.core.crypto;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Única fuente de nonces de 12 B para AES-GCM (plan, Fase 1 y 2.3).
 *
 * <ul>
 *   <li>{@link #aleatorio()}: slots y manifest, que se reescriben con la misma clave.</li>
 *   <li>{@link #deBloque(long)}: {@code 0⁴ ‖ índice(8 B)} para los bloques de un archivo; seguro
 *       porque cada escritura de archivo usa un {@code fileId} nuevo y por tanto una clave nueva.</li>
 * </ul>
 */
public final class ProveedorNonce {

    public static final int LONGITUD_NONCE_MIJA = 12;

    private final FuenteAleatoria fuenteMija;

    public ProveedorNonce(FuenteAleatoria fuenteMija) {
        this.fuenteMija = Objects.requireNonNull(fuenteMija, "fuenteMija");
    }

    public byte[] aleatorio() {
        return fuenteMija.bytes(LONGITUD_NONCE_MIJA);
    }

    public static byte[] deBloque(long indiceMija) {
        if (indiceMija < 0) {
            throw new IllegalArgumentException("indiceMija negativo");
        }
        return ByteBuffer.allocate(LONGITUD_NONCE_MIJA).putInt(0).putLong(indiceMija).array();
    }
}
