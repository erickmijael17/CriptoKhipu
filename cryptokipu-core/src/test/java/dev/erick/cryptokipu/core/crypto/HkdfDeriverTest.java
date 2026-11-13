package dev.erick.cryptokipu.core.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class HkdfDeriverTest {

    private static final HexFormat HEX_MIJA = HexFormat.of();

    /** RFC 5869, caso de prueba 1 (SHA-256). */
    @Test
    void vectorOficialRfc5869Caso1() {
        byte[] materialMija = new byte[22];
        Arrays.fill(materialMija, (byte) 0x0b);
        try (SecureBytes okmMija = HkdfDeriver.derivar(SecureBytes.copiaDe(materialMija),
                HEX_MIJA.parseHex("000102030405060708090a0b0c"),
                HEX_MIJA.parseHex("f0f1f2f3f4f5f6f7f8f9"), 42)) {
            assertArrayEquals(HEX_MIJA.parseHex(
                    "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"),
                    okmMija.bytes());
        }
    }

    /** RFC 5869, caso de prueba 3 (sin sal ni info). */
    @Test
    void vectorOficialRfc5869Caso3() {
        byte[] materialMija = new byte[22];
        Arrays.fill(materialMija, (byte) 0x0b);
        try (SecureBytes okmMija = HkdfDeriver.derivar(SecureBytes.copiaDe(materialMija), null, new byte[0], 42)) {
            assertArrayEquals(HEX_MIJA.parseHex(
                    "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"),
                    okmMija.bytes());
        }
    }

    @Test
    void lasSubclavesSonDistintasEntreSi() {
        byte[] maestraMija = new byte[32];
        Arrays.fill(maestraMija, (byte) 0x42);
        try (SecureBytes claveMaestraMija = SecureBytes.copiaDe(maestraMija);
                SecureBytes headerMija = HkdfDeriver.claveHeaderMac(claveMaestraMija);
                SecureBytes manifestMija = HkdfDeriver.claveManifest(claveMaestraMija);
                SecureBytes archivoUnoMija = HkdfDeriver.claveArchivo(claveMaestraMija, new byte[16]);
                SecureBytes archivoDosMija = HkdfDeriver.claveArchivo(claveMaestraMija, unos(16))) {
            assertFalse(Arrays.equals(headerMija.bytes(), manifestMija.bytes()));
            assertFalse(Arrays.equals(manifestMija.bytes(), archivoUnoMija.bytes()));
            assertFalse(Arrays.equals(archivoUnoMija.bytes(), archivoDosMija.bytes()));
        }
    }

    @Test
    void rechazaFileIdDeLongitudIncorrecta() {
        try (SecureBytes claveMaestraMija = SecureBytes.copiaDe(new byte[32])) {
            assertThrows(IllegalArgumentException.class, () -> HkdfDeriver.claveArchivo(claveMaestraMija, new byte[8]));
        }
    }

    private static byte[] unos(int longitudMija) {
        byte[] datosMija = new byte[longitudMija];
        Arrays.fill(datosMija, (byte) 1);
        return datosMija;
    }
}
