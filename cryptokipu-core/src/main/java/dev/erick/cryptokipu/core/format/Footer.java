package dev.erick.cryptokipu.core.format;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.zip.CRC32C;

/**
 * Footer: {@code offsetManifest(8) ‖ longitudManifest(8) ‖ generación(8) ‖ "CKEND"(5) ‖ CRC32C(4)}.
 * No contiene slots ni datos secretos. El CRC detecta footers dañados o basura al final del archivo.
 */
public record Footer(long offsetManifestMija, long longitudManifestMija, long generacionMija) {

    public static final int TAMANO_MIJA = 8 + 8 + 8 + 5 + 4;
    /** Posición del magic dentro del footer. */
    public static final int OFFSET_MAGIC_MIJA = 24;

    public Footer {
        if (offsetManifestMija < LimitesFormato.OFFSET_DATOS_MIJA || longitudManifestMija <= 0
                || longitudManifestMija > LimitesFormato.MANIFEST_MAX_BYTES_MIJA || generacionMija < 1) {
            throw new IllegalArgumentException("Footer inválido");
        }
    }

    public byte[] codificar() {
        ByteBuffer bufferMija = ByteBuffer.allocate(TAMANO_MIJA).putLong(offsetManifestMija)
                .putLong(longitudManifestMija).putLong(generacionMija).put(LimitesFormato.MAGIC_FOOTER_MIJA);
        bufferMija.putInt(crc(bufferMija.array()));
        return bufferMija.array();
    }

    /**
     * Interpreta {@code bytesMija} como footer ubicado en {@code posicionMija}.
     * Vacío si el magic, el CRC o la coherencia (el manifest termina justo antes del footer) fallan.
     */
    public static Optional<Footer> leer(byte[] bytesMija, long posicionMija) {
        if (bytesMija.length != TAMANO_MIJA) {
            return Optional.empty();
        }
        ByteBuffer bufferMija = ByteBuffer.wrap(bytesMija);
        long offsetMija = bufferMija.getLong();
        long longitudMija = bufferMija.getLong();
        long generacionMija = bufferMija.getLong();
        byte[] magicMija = new byte[LimitesFormato.MAGIC_FOOTER_MIJA.length];
        bufferMija.get(magicMija);
        int crcMija = bufferMija.getInt();
        if (!Arrays.equals(magicMija, LimitesFormato.MAGIC_FOOTER_MIJA) || crcMija != crc(bytesMija)) {
            return Optional.empty();
        }
        try {
            Footer footerMija = new Footer(offsetMija, longitudMija, generacionMija);
            return offsetMija + longitudMija == posicionMija ? Optional.of(footerMija) : Optional.empty();
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    private static int crc(byte[] bytesMija) {
        CRC32C crcMija = new CRC32C();
        crcMija.update(bytesMija, 0, TAMANO_MIJA - 4);
        return (int) crcMija.getValue();
    }
}
