package dev.erick.cryptokipu.core.crypto;

import java.util.Objects;
import java.util.Optional;

/**
 * Envuelve la clave maestra (32 B) con una KEK usando AES-256-GCM y nonce aleatorio en cada
 * envoltura (plan, Fase 2.3). La AAD es la Zona A del header más el índice y tipo de slot.
 */
public final class KeyWrap {

    private final ProveedorNonce nonceMija;

    public KeyWrap(ProveedorNonce nonceMija) {
        this.nonceMija = Objects.requireNonNull(nonceMija, "nonceMija");
    }

    public MkEnvuelta envolver(SecureBytes kekMija, SecureBytes claveMaestraMija, byte[] datosAsociadosMija) {
        byte[] nonceNuevoMija = nonceMija.aleatorio();
        byte[] cifradoMija = claveMaestraMija.conBytes(
                maestraMija -> AesGcmAead.cifrar(kekMija, nonceNuevoMija, datosAsociadosMija, maestraMija));
        return new MkEnvuelta(nonceNuevoMija, cifradoMija);
    }

    /** Vacío si la KEK no corresponde o si el slot o la AAD fueron alterados. */
    public static Optional<SecureBytes> desenvolver(SecureBytes kekMija, MkEnvuelta envueltaMija,
            byte[] datosAsociadosMija) {
        Objects.requireNonNull(envueltaMija, "envueltaMija");
        try {
            return Optional.of(SecureBytes.envolver(
                    AesGcmAead.descifrar(kekMija, envueltaMija.nonceMija(), datosAsociadosMija,
                            envueltaMija.cifradoMija())));
        } catch (FalloAutenticacion _) {
            return Optional.empty();
        }
    }

    /**
     * Clave maestra envuelta: no es secreta, pero se copian los arreglos para que sea inmutable.
     *
     * @param nonceMija   12 B
     * @param cifradoMija MK cifrada (32 B) ‖ tag (16 B)
     */
    public record MkEnvuelta(byte[] nonceMija, byte[] cifradoMija) {

        public MkEnvuelta {
            nonceMija = Objects.requireNonNull(nonceMija, "nonceMija").clone();
            cifradoMija = Objects.requireNonNull(cifradoMija, "cifradoMija").clone();
        }

        @Override
        public byte[] nonceMija() {
            return nonceMija.clone();
        }

        @Override
        public byte[] cifradoMija() {
            return cifradoMija.clone();
        }

        @Override
        public boolean equals(Object otroMija) {
            return otroMija instanceof MkEnvuelta otraMija
                    && java.util.Arrays.equals(nonceMija, otraMija.nonceMija)
                    && java.util.Arrays.equals(cifradoMija, otraMija.cifradoMija);
        }

        @Override
        public int hashCode() {
            return 31 * java.util.Arrays.hashCode(nonceMija) + java.util.Arrays.hashCode(cifradoMija);
        }

        @Override
        public String toString() {
            return "MkEnvuelta[***]";
        }
    }
}
