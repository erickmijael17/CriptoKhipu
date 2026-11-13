package dev.erick.cryptokipu.core.format;

import static dev.erick.cryptokipu.core.format.LimitesFormato.*;

import dev.erick.cryptokipu.core.crypto.AesGcmAead;
import dev.erick.cryptokipu.core.crypto.FalloAutenticacion;
import dev.erick.cryptokipu.core.crypto.HkdfDeriver;
import dev.erick.cryptokipu.core.crypto.ProveedorNonce;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Serializa y cifra el manifest.
 *
 * <p>En disco: {@code nonce(12) ‖ AES-GCM(K_manifest, texto, AAD = vaultId ‖ generación)}.
 * El nonce es aleatorio en cada escritura (la misma clave cifra muchas versiones del manifest).
 */
public final class ManifestCodec {

    private static final int VERSION_MIJA = 1;

    private ManifestCodec() {
    }

    public static byte[] cifrar(Manifest manifestMija, SecureBytes claveManifestMija, byte[] vaultIdMija,
            ProveedorNonce proveedorMija) {
        byte[] textoMija = serializar(manifestMija);
        try {
            byte[] nonceMija = proveedorMija.aleatorio();
            byte[] cifradoMija = AesGcmAead.cifrar(claveManifestMija, nonceMija,
                    datosAsociados(vaultIdMija, manifestMija.generacionMija()), textoMija);
            return ByteBuffer.allocate(nonceMija.length + cifradoMija.length).put(nonceMija).put(cifradoMija).array();
        } finally {
            Arrays.fill(textoMija, (byte) 0);
        }
    }

    public static Manifest descifrar(byte[] almacenadoMija, SecureBytes claveManifestMija, byte[] vaultIdMija,
            long generacionEsperadaMija) {
        if (almacenadoMija.length < ProveedorNonce.LONGITUD_NONCE_MIJA + AesGcmAead.LONGITUD_TAG_MIJA
                || almacenadoMija.length > MANIFEST_MAX_BYTES_MIJA) {
            throw integridad();
        }
        byte[] nonceMija = Arrays.copyOf(almacenadoMija, ProveedorNonce.LONGITUD_NONCE_MIJA);
        byte[] cifradoMija = Arrays.copyOfRange(almacenadoMija, ProveedorNonce.LONGITUD_NONCE_MIJA,
                almacenadoMija.length);
        byte[] textoMija;
        try {
            textoMija = AesGcmAead.descifrar(claveManifestMija, nonceMija,
                    datosAsociados(vaultIdMija, generacionEsperadaMija), cifradoMija);
        } catch (FalloAutenticacion _) {
            throw integridad();
        }
        try {
            Manifest manifestMija = deserializar(textoMija);
            if (manifestMija.generacionMija() != generacionEsperadaMija) {
                throw integridad();
            }
            return manifestMija;
        } finally {
            Arrays.fill(textoMija, (byte) 0);
        }
    }

    static byte[] serializar(Manifest manifestMija) {
        try (ByteArrayOutputStream bytesMija = new ByteArrayOutputStream();
                DataOutputStream salidaMija = new DataOutputStream(bytesMija)) {
            salidaMija.writeByte(VERSION_MIJA);
            salidaMija.writeLong(manifestMija.generacionMija());
            salidaMija.writeInt(manifestMija.entradasMija().size());
            for (EntradaManifest entradaMija : manifestMija.entradasMija()) {
                salidaMija.write(entradaMija.fileIdMija());
                escribirTexto(salidaMija, entradaMija.rutaMija());
                salidaMija.writeLong(entradaMija.tamanoMija());
                salidaMija.writeLong(entradaMija.modificadoMija());
                escribirTexto(salidaMija, entradaMija.tipoMimeMija());
                salidaMija.writeLong(entradaMija.offsetMija());
                salidaMija.writeLong(entradaMija.longitudCifradaMija());
            }
            salidaMija.flush();
            if (bytesMija.size() > MANIFEST_MAX_BYTES_MIJA - ProveedorNonce.LONGITUD_NONCE_MIJA
                    - AesGcmAead.LONGITUD_TAG_MIJA) {
                throw new ExcepcionBoveda(new ErrorBoveda.ParametrosFueraDeRango("manifest"));
            }
            return bytesMija.toByteArray();
        } catch (IOException errorMija) {
            throw new UncheckedIOException(errorMija);
        }
    }

    static Manifest deserializar(byte[] textoMija) {
        ByteBuffer bufferMija = ByteBuffer.wrap(textoMija);
        try {
            int versionMija = Byte.toUnsignedInt(bufferMija.get());
            if (versionMija != VERSION_MIJA) {
                throw new ExcepcionBoveda(new ErrorBoveda.FormatoNoSoportado(versionMija));
            }
            long generacionMija = bufferMija.getLong();
            int cantidadMija = bufferMija.getInt();
            if (cantidadMija < 0 || cantidadMija > MANIFEST_MAX_ENTRADAS_MIJA) {
                throw new ExcepcionBoveda(new ErrorBoveda.ParametrosFueraDeRango("manifest.entradas"));
            }
            List<EntradaManifest> entradasMija = new ArrayList<>(Math.min(cantidadMija, 1024));
            for (int indiceMija = 0; indiceMija < cantidadMija; indiceMija++) {
                byte[] fileIdMija = new byte[HkdfDeriver.LONGITUD_FILE_ID_MIJA];
                bufferMija.get(fileIdMija);
                String rutaMija = leerTexto(bufferMija, RUTA_MAX_BYTES_MIJA);
                long tamanoMija = bufferMija.getLong();
                long modificadoMija = bufferMija.getLong();
                String mimeMija = leerTexto(bufferMija, MIME_MAX_BYTES_MIJA);
                long offsetMija = bufferMija.getLong();
                long longitudMija = bufferMija.getLong();
                entradasMija.add(new EntradaManifest(fileIdMija, rutaMija, tamanoMija, modificadoMija, mimeMija,
                        offsetMija, longitudMija));
            }
            if (bufferMija.hasRemaining()) {
                throw integridad();
            }
            return new Manifest(generacionMija, entradasMija);
        } catch (BufferUnderflowException | IllegalArgumentException _) {
            throw integridad();
        }
    }

    private static byte[] datosAsociados(byte[] vaultIdMija, long generacionMija) {
        return ByteBuffer.allocate(vaultIdMija.length + Long.BYTES).put(vaultIdMija).putLong(generacionMija).array();
    }

    private static void escribirTexto(DataOutputStream salidaMija, String textoMija) throws IOException {
        byte[] bytesMija = textoMija.getBytes(StandardCharsets.UTF_8);
        salidaMija.writeShort(bytesMija.length);
        salidaMija.write(bytesMija);
    }

    private static String leerTexto(ByteBuffer bufferMija, int maximoMija) {
        int longitudMija = Short.toUnsignedInt(bufferMija.getShort());
        if (longitudMija > maximoMija) {
            throw new IllegalArgumentException("Texto demasiado largo");
        }
        byte[] bytesMija = new byte[longitudMija];
        bufferMija.get(bytesMija);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytesMija)).toString();
        } catch (CharacterCodingException _) {
            throw new IllegalArgumentException("UTF-8 inválido");
        }
    }

    private static ExcepcionBoveda integridad() {
        return new ExcepcionBoveda(new ErrorBoveda.IntegridadFallida(ErrorBoveda.IntegridadFallida.Zona.MANIFEST));
    }
}
