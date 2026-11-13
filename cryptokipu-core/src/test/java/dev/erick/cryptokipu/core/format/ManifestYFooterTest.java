package dev.erick.cryptokipu.core.format;

import static dev.erick.cryptokipu.core.format.FormatoPrueba.FUENTE_MIJA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.core.crypto.ProveedorNonce;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManifestYFooterTest {

    private final ProveedorNonce nonceMija = new ProveedorNonce(FUENTE_MIJA);
    private final byte[] vaultIdMija = FUENTE_MIJA.bytes(16);

    private Manifest manifestDePrueba() {
        return Manifest.vacio().conAgregadas(List.of(
                new EntradaManifest(FUENTE_MIJA.bytes(16), "fotos/año 2026/ñandú.jpg", 123_456, 1_700_000_000_000L,
                        "image/jpeg", LimitesFormato.OFFSET_DATOS_MIJA, 123_472),
                new EntradaManifest(FUENTE_MIJA.bytes(16), "vacío.txt", 0, 0, "text/plain",
                        LimitesFormato.OFFSET_DATOS_MIJA + 123_472, 16)));
    }

    @Test
    void manifestIdaYVueltaConTildes() {
        Manifest originalMija = manifestDePrueba();
        try (SecureBytes claveMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32))) {
            byte[] cifradoMija = ManifestCodec.cifrar(originalMija, claveMija, vaultIdMija, nonceMija);

            assertEquals(originalMija, ManifestCodec.descifrar(cifradoMija, claveMija, vaultIdMija, 2));
        }
    }

    @Test
    void dosCifradosSeguidosUsanNoncesDistintos() {
        try (SecureBytes claveMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32))) {
            byte[] primeroMija = ManifestCodec.cifrar(manifestDePrueba(), claveMija, vaultIdMija, nonceMija);
            byte[] segundoMija = ManifestCodec.cifrar(manifestDePrueba(), claveMija, vaultIdMija, nonceMija);

            assertFalse(Arrays.equals(primeroMija, 0, 12, segundoMija, 0, 12));
        }
    }

    @Test
    void generacionDistintaOBitCambiadoEsIntegridadFallida() {
        try (SecureBytes claveMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32))) {
            byte[] cifradoMija = ManifestCodec.cifrar(manifestDePrueba(), claveMija, vaultIdMija, nonceMija);
            esperarManifest(() -> ManifestCodec.descifrar(cifradoMija, claveMija, vaultIdMija, 1));

            byte[] alteradoMija = cifradoMija.clone();
            alteradoMija[20] ^= 0x01;
            esperarManifest(() -> ManifestCodec.descifrar(alteradoMija, claveMija, vaultIdMija, 2));

            byte[] otraBovedaMija = FUENTE_MIJA.bytes(16);
            esperarManifest(() -> ManifestCodec.descifrar(cifradoMija, claveMija, otraBovedaMija, 2));
        }
    }

    @Test
    void demasiadasEntradasSeRechazanSinReservarMemoria() {
        byte[] textoMija = ByteBuffer.allocate(13).put((byte) 1).putLong(2).putInt(2_000_000).array();

        ExcepcionBoveda excepcionMija = assertThrows(ExcepcionBoveda.class,
                () -> ManifestCodec.deserializar(textoMija));
        assertInstanceOf(ErrorBoveda.ParametrosFueraDeRango.class, excepcionMija.error());
    }

    @Test
    void textoBasuraNuncaLanzaExcepcionesNoControladas() {
        for (int intentoMija = 0; intentoMija < 2_000; intentoMija++) {
            byte[] basuraMija = FUENTE_MIJA.bytes(1 + intentoMija % 300);
            basuraMija[0] = 1;
            assertThrows(ExcepcionBoveda.class, () -> ManifestCodec.deserializar(basuraMija));
        }
    }

    @Test
    void footerIdaYVueltaYValidaciones() {
        Footer footerMija = new Footer(40_000, 500, 3);
        byte[] bytesMija = footerMija.codificar();

        assertEquals(footerMija, Footer.leer(bytesMija, 40_500).orElseThrow());
        assertTrue(Footer.leer(bytesMija, 40_501).isEmpty(), "posición incoherente");
        byte[] alteradoMija = bytesMija.clone();
        alteradoMija[2] ^= 0x01;
        assertTrue(Footer.leer(alteradoMija, 40_500).isEmpty(), "CRC");
        assertTrue(Footer.leer(FUENTE_MIJA.bytes(Footer.TAMANO_MIJA), 40_500).isEmpty(), "basura");
    }

    @Test
    void entradaNoMuestraLaRutaEnToString() {
        assertFalse(manifestDePrueba().entradasMija().getFirst().toString().contains("ñandú"));
    }

    private static void esperarManifest(org.junit.jupiter.api.function.Executable accionMija) {
        ExcepcionBoveda excepcionMija = assertThrows(ExcepcionBoveda.class, accionMija);
        ErrorBoveda.IntegridadFallida errorMija =
                assertInstanceOf(ErrorBoveda.IntegridadFallida.class, excepcionMija.error());
        assertEquals(ErrorBoveda.IntegridadFallida.Zona.MANIFEST, errorMija.zonaMija());
    }
}
