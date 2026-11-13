package dev.erick.cryptokipu.core.format;

import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.KeyWrap;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.crypto.ProveedorNonce;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32C;

/** Utilidades compartidas por las pruebas de formato. */
final class FormatoPrueba {

    static final FuenteAleatoria FUENTE_MIJA = FuenteAleatoria.delSistema();

    private FormatoPrueba() {
    }

    /** Header con el slot 0 envolviendo {@code claveMaestraMija} con {@code kekMija} y 6 slots vacíos. */
    static Header header(SecureBytes kekMija, SecureBytes claveMaestraMija) {
        ZonaA zonaMija = ZonaA.nueva(ParametrosArgon2.ESTANDAR_MIJA, FUENTE_MIJA);
        KeyWrap envolturaMija = new KeyWrap(new ProveedorNonce(FUENTE_MIJA));
        List<Slot> slotsMija = new ArrayList<>();
        slotsMija.add(Slot.envolver(zonaMija, 0, TipoSlot.PRINCIPAL, new MetadatosPrincipal(false, 0), FUENTE_MIJA.bytes(32), kekMija,
                claveMaestraMija, envolturaMija));
        for (int indiceMija = 1; indiceMija < 7; indiceMija++) {
            slotsMija.add(Slot.vacio(FUENTE_MIJA));
        }
        return Header.nuevo(zonaMija, slotsMija, 0);
    }

    /** Recalcula el CRC estructural de un bloque de header (simula una manipulación "cuidadosa"). */
    static void recalcularCrc(byte[] bloqueMija) {
        CRC32C crcMija = new CRC32C();
        crcMija.update(bloqueMija, 0, HeaderCodec.OFFSET_CRC_MIJA);
        ByteBuffer.wrap(bloqueMija).putInt(HeaderCodec.OFFSET_CRC_MIJA, (int) crcMija.getValue());
    }
}
