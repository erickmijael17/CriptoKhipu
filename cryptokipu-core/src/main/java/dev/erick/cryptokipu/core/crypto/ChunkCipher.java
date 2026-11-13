package dev.erick.cryptokipu.core.crypto;

import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Cifrado de un archivo en bloques de 1 MiB con AES-256-GCM (plan, Fase 1; ADR-002).
 *
 * <ul>
 *   <li>{@code nonce = 0⁴ ‖ índice(8 B)}; la clave es {@code K_file}, única por {@code fileId}.</li>
 *   <li>{@code AAD = fileId(16 B) ‖ índice(8 B) ‖ esÚltimo(1 B)}: se detecta reordenar, quitar,
 *       truncar o mezclar bloques de otro archivo.</li>
 *   <li>Un archivo vacío produce un único bloque final de 16 B (solo el tag), así que incluso
 *       borrar todo el contenido cifrado se detecta.</li>
 *   <li>Cada bloque se verifica antes de entregar sus bytes; la memoria es constante.</li>
 * </ul>
 */
public final class ChunkCipher {

    public static final int TAMANO_BLOQUE_MIJA = 1 << 20;
    public static final int TAMANO_BLOQUE_CIFRADO_MIJA = TAMANO_BLOQUE_MIJA + AesGcmAead.LONGITUD_TAG_MIJA;

    private ChunkCipher() {
    }

    /** Cifra todo {@code entradaMija} hacia {@code salidaMija}. Devuelve el número de bloques escritos. */
    public static long cifrar(SecureBytes claveArchivoMija, byte[] fileIdMija, InputStream entradaMija,
            OutputStream salidaMija) throws IOException {
        validarFileId(fileIdMija);
        PushbackInputStream lectorMija = new PushbackInputStream(Objects.requireNonNull(entradaMija), 1);
        byte[] bufferMija = new byte[TAMANO_BLOQUE_MIJA];
        long indiceMija = 0;
        try {
            while (true) {
                int leidosMija = lectorMija.readNBytes(bufferMija, 0, TAMANO_BLOQUE_MIJA);
                boolean esUltimoMija = leidosMija < TAMANO_BLOQUE_MIJA || finDeFlujo(lectorMija);
                byte[] textoMija = Arrays.copyOf(bufferMija, leidosMija);
                try {
                    salidaMija.write(cifrarBloque(claveArchivoMija, fileIdMija, indiceMija, esUltimoMija, textoMija));
                } finally {
                    Arrays.fill(textoMija, (byte) 0);
                }
                indiceMija++;
                if (esUltimoMija) {
                    return indiceMija;
                }
            }
        } finally {
            Arrays.fill(bufferMija, (byte) 0);
        }
    }

    /**
     * Descifra y verifica todo el flujo. Lanza {@link ExcepcionBoveda} con
     * {@link ErrorBoveda.IntegridadFallida} si algún bloque falla o el flujo está truncado.
     */
    public static long descifrar(SecureBytes claveArchivoMija, byte[] fileIdMija, InputStream entradaMija,
            OutputStream salidaMija) throws IOException {
        validarFileId(fileIdMija);
        PushbackInputStream lectorMija = new PushbackInputStream(Objects.requireNonNull(entradaMija), 1);
        byte[] bufferMija = new byte[TAMANO_BLOQUE_CIFRADO_MIJA];
        long indiceMija = 0;
        while (true) {
            int leidosMija = lectorMija.readNBytes(bufferMija, 0, TAMANO_BLOQUE_CIFRADO_MIJA);
            boolean esUltimoMija = leidosMija < TAMANO_BLOQUE_CIFRADO_MIJA || finDeFlujo(lectorMija);
            byte[] textoMija;
            try {
                textoMija = descifrarBloque(claveArchivoMija, fileIdMija, indiceMija, esUltimoMija,
                        Arrays.copyOf(bufferMija, leidosMija));
            } catch (FalloAutenticacion _) {
                throw new ExcepcionBoveda(new ErrorBoveda.IntegridadFallida(ErrorBoveda.IntegridadFallida.Zona.BLOQUE));
            }
            try {
                salidaMija.write(textoMija);
            } finally {
                Arrays.fill(textoMija, (byte) 0);
            }
            indiceMija++;
            if (esUltimoMija) {
                return indiceMija;
            }
        }
    }

    public static byte[] cifrarBloque(SecureBytes claveArchivoMija, byte[] fileIdMija, long indiceMija,
            boolean esUltimoMija, byte[] textoMija) {
        if (textoMija.length > TAMANO_BLOQUE_MIJA) {
            throw new IllegalArgumentException("El bloque supera 1 MiB");
        }
        return AesGcmAead.cifrar(claveArchivoMija, ProveedorNonce.deBloque(indiceMija),
                datosAsociados(fileIdMija, indiceMija, esUltimoMija), textoMija);
    }

    public static byte[] descifrarBloque(SecureBytes claveArchivoMija, byte[] fileIdMija, long indiceMija,
            boolean esUltimoMija, byte[] cifradoMija) throws FalloAutenticacion {
        return AesGcmAead.descifrar(claveArchivoMija, ProveedorNonce.deBloque(indiceMija),
                datosAsociados(fileIdMija, indiceMija, esUltimoMija), cifradoMija);
    }

    /** Tamaño exacto del resultado cifrado para un archivo de {@code tamanoMija} bytes. */
    public static long tamanoCifrado(long tamanoMija) {
        long bloquesMija = Math.max(1, (tamanoMija + TAMANO_BLOQUE_MIJA - 1) / TAMANO_BLOQUE_MIJA);
        return tamanoMija + bloquesMija * AesGcmAead.LONGITUD_TAG_MIJA;
    }

    static byte[] datosAsociados(byte[] fileIdMija, long indiceMija, boolean esUltimoMija) {
        validarFileId(fileIdMija);
        return ByteBuffer.allocate(HkdfDeriver.LONGITUD_FILE_ID_MIJA + Long.BYTES + 1)
                .put(fileIdMija)
                .putLong(indiceMija)
                .put((byte) (esUltimoMija ? 1 : 0))
                .array();
    }

    private static boolean finDeFlujo(PushbackInputStream lectorMija) throws IOException {
        int siguienteMija = lectorMija.read();
        if (siguienteMija == -1) {
            return true;
        }
        lectorMija.unread(siguienteMija);
        return false;
    }

    private static void validarFileId(byte[] fileIdMija) {
        Objects.requireNonNull(fileIdMija, "fileIdMija");
        if (fileIdMija.length != HkdfDeriver.LONGITUD_FILE_ID_MIJA) {
            throw new IllegalArgumentException("fileId debe medir 16 B");
        }
    }
}
