package dev.erick.cryptokipu.core.format;

import java.nio.ByteBuffer;

/**
 * Metadatos de la Zona B que la interfaz necesita <b>antes</b> de desbloquear y que por eso van en la
 * AAD del slot principal: {@code requiereLlave ‖ versiónPolítica ‖ umbralAutodestrucción}.
 * Si alguien los manipula en una copia del header, el slot 0 de esa copia no abre y se usa la otra.
 *
 * @param requiereLlaveMija el slot principal usa F4
 * @param umbralMija        intentos fallidos que disparan la autodestrucción (0 = desactivada)
 */
public record MetadatosPrincipal(boolean requiereLlaveMija, int umbralMija) {

    public MetadatosPrincipal {
        if (umbralMija < 0) {
            throw new IllegalArgumentException("umbralMija negativo");
        }
    }

    /** llave(1) ‖ versión de política(2) ‖ umbral(4). */
    public byte[] codificar() {
        return ByteBuffer.allocate(7).put((byte) (requiereLlaveMija ? 1 : 0))
                .putShort((short) LimitesFormato.VERSION_POLITICA_MIJA).putInt(umbralMija).array();
    }
}
