package dev.erick.cryptokipu.core.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Vectores de "The Galois/Counter Mode of Operation (GCM)", McGrew y Viega (NIST), casos 13–16. */
class AesGcmAeadTest {

    private static final HexFormat HEX_MIJA = HexFormat.of();
    private static final String CLAVE_15_MIJA = "feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308";
    private static final String NONCE_15_MIJA = "cafebabefacedbaddecaf888";
    private static final String TEXTO_15_MIJA = "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72"
            + "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255";
    private static final String CIFRADO_15_MIJA = "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa"
            + "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662898015ad";

    @Test
    void caso13ClaveCeroSinTexto() throws Exception {
        verificar("00".repeat(32), "00".repeat(12), "", "", "", "530f8afbc74536b9a963b4f1c4cb738b");
    }

    @Test
    void caso14ClaveCeroUnBloque() throws Exception {
        verificar("00".repeat(32), "00".repeat(12), "", "00".repeat(16), "cea7403d4d606b6e074ec5d3baf39d18",
                "d0d1c8a799996bf0265b98b5d48ab919");
    }

    @Test
    void caso15SinDatosAsociados() throws Exception {
        verificar(CLAVE_15_MIJA, NONCE_15_MIJA, "", TEXTO_15_MIJA, CIFRADO_15_MIJA, "b094dac5d93471bdec1a502270e3cc6c");
    }

    @Test
    void caso16ConDatosAsociados() throws Exception {
        verificar(CLAVE_15_MIJA, NONCE_15_MIJA, "feedfacedeadbeeffeedfacedeadbeefabaddad2",
                TEXTO_15_MIJA.substring(0, 120), CIFRADO_15_MIJA.substring(0, 120), "76fc6ece0f4e1768cddf8853bb2d551b");
    }

    @Test
    void detectaUnBitCambiadoEnElCifrado() {
        byte[] cifradoMija = cifrarCaso15();
        cifradoMija[5] ^= 0x01;
        assertThrows(FalloAutenticacion.class, () -> descifrarCaso15(cifradoMija, new byte[0]));
    }

    @Test
    void detectaUnBitCambiadoEnElTag() {
        byte[] cifradoMija = cifrarCaso15();
        cifradoMija[cifradoMija.length - 1] ^= 0x01;
        assertThrows(FalloAutenticacion.class, () -> descifrarCaso15(cifradoMija, new byte[0]));
    }

    @Test
    void detectaDatosAsociadosDistintos() {
        byte[] cifradoMija = cifrarCaso15();
        assertThrows(FalloAutenticacion.class, () -> descifrarCaso15(cifradoMija, new byte[] {1}));
    }

    @Test
    void rechazaClaveONonceDeLongitudIncorrecta() {
        try (SecureBytes cortaMija = SecureBytes.copiaDe(new byte[16]);
                SecureBytes buenaMija = SecureBytes.copiaDe(new byte[32])) {
            assertThrows(IllegalArgumentException.class, () -> AesGcmAead.cifrar(cortaMija, new byte[12], null, new byte[1]));
            assertThrows(IllegalArgumentException.class, () -> AesGcmAead.cifrar(buenaMija, new byte[16], null, new byte[1]));
        }
    }

    @Test
    void cifradoMasCortoQueElTagEsFalloDeAutenticacion() {
        try (SecureBytes claveMija = SecureBytes.copiaDe(new byte[32])) {
            assertThrows(FalloAutenticacion.class, () -> AesGcmAead.descifrar(claveMija, new byte[12], null, new byte[5]));
        }
    }

    private static byte[] cifrarCaso15() {
        try (SecureBytes claveMija = SecureBytes.copiaDe(HEX_MIJA.parseHex(CLAVE_15_MIJA))) {
            return AesGcmAead.cifrar(claveMija, HEX_MIJA.parseHex(NONCE_15_MIJA), null, HEX_MIJA.parseHex(TEXTO_15_MIJA));
        }
    }

    private static byte[] descifrarCaso15(byte[] cifradoMija, byte[] datosAsociadosMija) throws FalloAutenticacion {
        try (SecureBytes claveMija = SecureBytes.copiaDe(HEX_MIJA.parseHex(CLAVE_15_MIJA))) {
            return AesGcmAead.descifrar(claveMija, HEX_MIJA.parseHex(NONCE_15_MIJA), datosAsociadosMija, cifradoMija);
        }
    }

    private static void verificar(String claveMija, String nonceMija, String datosAsociadosMija, String textoMija,
            String cifradoEsperadoMija, String tagEsperadoMija) throws FalloAutenticacion {
        byte[] nonceBytesMija = HEX_MIJA.parseHex(nonceMija);
        byte[] aadMija = HEX_MIJA.parseHex(datosAsociadosMija);
        try (SecureBytes claveBytesMija = SecureBytes.copiaDe(HEX_MIJA.parseHex(claveMija))) {
            byte[] salidaMija = AesGcmAead.cifrar(claveBytesMija, nonceBytesMija, aadMija, HEX_MIJA.parseHex(textoMija));

            assertArrayEquals(HEX_MIJA.parseHex(cifradoEsperadoMija + tagEsperadoMija), salidaMija);
            assertArrayEquals(HEX_MIJA.parseHex(textoMija),
                    AesGcmAead.descifrar(claveBytesMija, nonceBytesMija, aadMija, salidaMija));
        }
    }
}
