package dev.erick.cryptokipu.core.format;

import static dev.erick.cryptokipu.core.format.LimitesFormato.*;

import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32C;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Serializa el header en un bloque fijo de 16 KiB.
 *
 * <pre>
 * [0, 103)     Zona A
 * [103, 768)   Zona B: generación(8) umbral(4) requiereLlave(1) nSlots(1) 7 × slot(93)  (posiciones sin usar en cero)
 * [768, 800)   headerMAC = HMAC-SHA256(K_hdr, Zona A ‖ Zona B)
 * [800, 804)   CRC32C(Zona A ‖ Zona B ‖ MAC): detecta daño accidental sin necesitar la clave
 * [804, 816)   Zona C: intentosFallidos(4) pistaFooter(8)            (no autenticada)
 * [816, 16384) relleno aleatorio
 * </pre>
 */
public final class HeaderCodec {

    public static final int TAMANO_ZONA_B_MIJA = 8 + 4 + 1 + 1 + SLOTS_MAX_MIJA * Slot.TAMANO_MIJA;
    public static final int OFFSET_MAC_MIJA = ZonaA.TAMANO_MIJA + TAMANO_ZONA_B_MIJA;
    public static final int OFFSET_CRC_MIJA = OFFSET_MAC_MIJA + LONGITUD_MAC_MIJA;
    public static final int OFFSET_ZONA_C_MIJA = OFFSET_CRC_MIJA + 4;
    public static final int TAMANO_ZONA_C_MIJA = 4 + 8;

    private HeaderCodec() {
    }

    public static byte[] codificar(Header headerMija, FuenteAleatoria fuenteMija) {
        byte[] bloqueMija = fuenteMija.bytes(TAMANO_HEADER_MIJA);
        ByteBuffer bufferMija = ByteBuffer.wrap(bloqueMija);
        bufferMija.put(bytesAutenticados(headerMija));
        bufferMija.put(headerMija.macMija());
        bufferMija.putInt(crc(bloqueMija));
        bufferMija.put(codificarZonaC(headerMija.intentosFallidosMija(), headerMija.pistaFooterMija()));
        return bloqueMija;
    }

    /** Lee un bloque de 16 KiB. CRC primero; después formato y límites. */
    public static Header decodificar(byte[] bloqueMija) {
        Objects.requireNonNull(bloqueMija, "bloqueMija");
        if (bloqueMija.length != TAMANO_HEADER_MIJA) {
            throw integridad();
        }
        ByteBuffer bufferMija = ByteBuffer.wrap(bloqueMija);
        if (bufferMija.getInt(OFFSET_CRC_MIJA) != crc(bloqueMija)) {
            throw integridad();
        }
        try {
            ZonaA zonaAMija = ZonaA.decodificar(bufferMija);
            long generacionMija = bufferMija.getLong();
            int umbralMija = bufferMija.getInt();
            int llaveMija = Byte.toUnsignedInt(bufferMija.get());
            int cantidadMija = Byte.toUnsignedInt(bufferMija.get());
            if (llaveMija > 1 || cantidadMija < SLOTS_MIN_MIJA || cantidadMija > SLOTS_MAX_MIJA || generacionMija < 0
                    || umbralMija < 0) {
                throw new ExcepcionBoveda(new ErrorBoveda.ParametrosFueraDeRango("zonaB"));
            }
            List<Slot> slotsMija = new ArrayList<>(cantidadMija);
            for (int indiceMija = 0; indiceMija < cantidadMija; indiceMija++) {
                slotsMija.add(Slot.leer(bufferMija));
            }
            bufferMija.position(OFFSET_MAC_MIJA);
            byte[] macMija = new byte[LONGITUD_MAC_MIJA];
            bufferMija.get(macMija);
            bufferMija.position(OFFSET_ZONA_C_MIJA);
            int intentosMija = Math.max(0, bufferMija.getInt());
            long pistaMija = Math.max(0, bufferMija.getLong());
            return new Header(zonaAMija, slotsMija, generacionMija, umbralMija, llaveMija == 1, macMija, intentosMija,
                    pistaMija);
        } catch (BufferUnderflowException | IllegalArgumentException _) {
            throw integridad();
        }
    }

    /** Zona A ‖ Zona B (lo que cubre el MAC). */
    public static byte[] bytesAutenticados(Header headerMija) {
        ByteBuffer bufferMija = ByteBuffer.allocate(OFFSET_MAC_MIJA);
        bufferMija.put(headerMija.zonaAMija().codificar());
        bufferMija.putLong(headerMija.generacionMija());
        bufferMija.putInt(headerMija.umbralAutodestruccionMija());
        bufferMija.put((byte) (headerMija.requiereLlaveMija() ? 1 : 0));
        bufferMija.put((byte) headerMija.slotsMija().size());
        headerMija.slotsMija().forEach(slotMija -> slotMija.escribir(bufferMija));
        return bufferMija.array();
    }

    public static byte[] calcularMac(Header headerMija, SecureBytes claveHeaderMija) {
        try {
            Mac hmacMija = Mac.getInstance("HmacSHA256");
            hmacMija.init(new SecretKeySpec(claveHeaderMija.bytes(), "HmacSHA256"));
            return hmacMija.doFinal(bytesAutenticados(headerMija));
        } catch (GeneralSecurityException errorMija) {
            throw new IllegalStateException("HmacSHA256 no disponible", errorMija);
        }
    }

    /** Comparación en tiempo constante. */
    public static boolean macValido(Header headerMija, SecureBytes claveHeaderMija) {
        return MessageDigest.isEqual(calcularMac(headerMija, claveHeaderMija), headerMija.macMija());
    }

    /**
     * AAD de un slot: {@code Zona A ‖ índice ‖ tipo}; el slot principal agrega {@code ‖ metadatos}
     * (requiereLlave ‖ versión de política ‖ umbral). Los slots de token no llevan metadatos para que
     * cambiar la llave o el umbral no los invalide.
     */
    public static byte[] datosAsociadosSlot(ZonaA zonaAMija, int indiceMija, TipoSlot tipoMija,
            MetadatosPrincipal metadatosMija) {
        boolean esPrincipalMija = tipoMija == TipoSlot.PRINCIPAL;
        if (esPrincipalMija == (metadatosMija == null)) {
            throw new IllegalArgumentException("Metadatos solo y siempre en el slot principal");
        }
        byte[] extraMija = esPrincipalMija ? metadatosMija.codificar() : new byte[0];
        return ByteBuffer.allocate(ZonaA.TAMANO_MIJA + 2 + extraMija.length).put(zonaAMija.codificar())
                .put((byte) indiceMija).put((byte) tipoMija.codigo()).put(extraMija).array();
    }

    public static byte[] codificarZonaC(int intentosMija, long pistaFooterMija) {
        return ByteBuffer.allocate(TAMANO_ZONA_C_MIJA).putInt(intentosMija).putLong(pistaFooterMija).array();
    }

    private static int crc(byte[] bloqueMija) {
        CRC32C crcMija = new CRC32C();
        crcMija.update(bloqueMija, 0, OFFSET_CRC_MIJA);
        return (int) crcMija.getValue();
    }

    private static ExcepcionBoveda integridad() {
        return new ExcepcionBoveda(new ErrorBoveda.IntegridadFallida(ErrorBoveda.IntegridadFallida.Zona.HEADER));
    }
}
