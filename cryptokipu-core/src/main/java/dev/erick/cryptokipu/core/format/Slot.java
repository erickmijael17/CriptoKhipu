package dev.erick.cryptokipu.core.format;

import dev.erick.cryptokipu.core.crypto.AesGcmAead;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.KeyWrap;
import dev.erick.cryptokipu.core.crypto.ProveedorNonce;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Key slot: la clave maestra envuelta con la KEK de un juego de credenciales o de un token.
 *
 * @param tipoMija     tipo de slot
 * @param salMija      sal propia de Argon2 (32 B)
 * @param envueltaMija MK cifrada con nonce aleatorio
 */
public record Slot(TipoSlot tipoMija, byte[] salMija, KeyWrap.MkEnvuelta envueltaMija) {

    /** tipo(1) sal(32) nonce(12) MK cifrada + tag(48). */
    public static final int TAMANO_MIJA = 1 + LimitesFormato.LONGITUD_SAL_MIJA + ProveedorNonce.LONGITUD_NONCE_MIJA
            + AesGcmAead.LONGITUD_CLAVE_MIJA + AesGcmAead.LONGITUD_TAG_MIJA;
    private static final int LONGITUD_CIFRADO_MIJA = AesGcmAead.LONGITUD_CLAVE_MIJA + AesGcmAead.LONGITUD_TAG_MIJA;

    public Slot {
        Objects.requireNonNull(tipoMija, "tipoMija");
        Objects.requireNonNull(envueltaMija, "envueltaMija");
        salMija = Objects.requireNonNull(salMija, "salMija").clone();
        if (salMija.length != LimitesFormato.LONGITUD_SAL_MIJA) {
            throw new IllegalArgumentException("salMija debe medir 32 B");
        }
        if (envueltaMija.nonceMija().length != ProveedorNonce.LONGITUD_NONCE_MIJA
                || envueltaMija.cifradoMija().length != LONGITUD_CIFRADO_MIJA) {
            throw new IllegalArgumentException("MK envuelta con tamaño inválido");
        }
    }

    /**
     * Envuelve la clave maestra en un slot. La AAD es <b>siempre</b> {@code Zona A ‖ índice ‖ tipo}, y en el
     * slot principal además {@code ‖ metadatos} ({@link HeaderCodec#datosAsociadosSlot}): cambiar parámetros,
     * sales, política, la marca de llave o el umbral impide abrirlo, y un slot no puede moverse ni cambiar de tipo.
     *
     * @param metadatosMija obligatorio en el slot principal; {@code null} en los de token
     */
    public static Slot envolver(ZonaA zonaAMija, int indiceMija, TipoSlot tipoMija, MetadatosPrincipal metadatosMija,
            byte[] salMija, SecureBytes kekMija, SecureBytes claveMaestraMija, KeyWrap envolturaMija) {
        if (tipoMija == TipoSlot.VACIO) {
            throw new IllegalArgumentException("Un slot vacío no envuelve claves");
        }
        return new Slot(tipoMija, salMija, envolturaMija.envolver(kekMija, claveMaestraMija,
                HeaderCodec.datosAsociadosSlot(zonaAMija, indiceMija, tipoMija, metadatosMija)));
    }

    /** Intenta abrir este slot (que ocupa la posición {@code indiceMija}) con la KEK dada. */
    public Optional<SecureBytes> abrir(ZonaA zonaAMija, int indiceMija, MetadatosPrincipal metadatosMija,
            SecureBytes kekMija) {
        if (tipoMija == TipoSlot.VACIO) {
            return Optional.empty();
        }
        return KeyWrap.desenvolver(kekMija, envueltaMija,
                HeaderCodec.datosAsociadosSlot(zonaAMija, indiceMija, tipoMija,
                        tipoMija == TipoSlot.PRINCIPAL ? metadatosMija : null));
    }

    /** Slot sin clave: bytes aleatorios indistinguibles de un slot real. */
    public static Slot vacio(FuenteAleatoria fuenteMija) {
        return new Slot(TipoSlot.VACIO, fuenteMija.bytes(LimitesFormato.LONGITUD_SAL_MIJA),
                new KeyWrap.MkEnvuelta(fuenteMija.bytes(ProveedorNonce.LONGITUD_NONCE_MIJA),
                        fuenteMija.bytes(LONGITUD_CIFRADO_MIJA)));
    }

    @Override
    public byte[] salMija() {
        return salMija.clone();
    }

    void escribir(ByteBuffer bufferMija) {
        bufferMija.put((byte) tipoMija.codigo()).put(salMija).put(envueltaMija.nonceMija())
                .put(envueltaMija.cifradoMija());
    }

    static Slot leer(ByteBuffer bufferMija) {
        TipoSlot tipoMija = TipoSlot.desdeCodigo(Byte.toUnsignedInt(bufferMija.get()));
        byte[] salMija = new byte[LimitesFormato.LONGITUD_SAL_MIJA];
        bufferMija.get(salMija);
        byte[] nonceMija = new byte[ProveedorNonce.LONGITUD_NONCE_MIJA];
        bufferMija.get(nonceMija);
        byte[] cifradoMija = new byte[LONGITUD_CIFRADO_MIJA];
        bufferMija.get(cifradoMija);
        return new Slot(tipoMija, salMija, new KeyWrap.MkEnvuelta(nonceMija, cifradoMija));
    }

    @Override
    public boolean equals(Object otroMija) {
        return otroMija instanceof Slot otroSlotMija && tipoMija == otroSlotMija.tipoMija
                && Arrays.equals(salMija, otroSlotMija.salMija) && envueltaMija.equals(otroSlotMija.envueltaMija);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tipoMija, Arrays.hashCode(salMija), envueltaMija);
    }

    @Override
    public String toString() {
        return "Slot[" + tipoMija + "]";
    }
}
