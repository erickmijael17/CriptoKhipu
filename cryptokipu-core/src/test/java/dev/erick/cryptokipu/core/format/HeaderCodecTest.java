package dev.erick.cryptokipu.core.format;

import static dev.erick.cryptokipu.core.format.FormatoPrueba.FUENTE_MIJA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.core.crypto.KeyWrap;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeaderCodecTest {

    /** Offset del campo {@code m} de Argon2 dentro de la zona A. */
    private static final int OFFSET_MEMORIA_MIJA = 5 + 2 + 1 + 1 + 1 + 2 + 1;

    private SecureBytes kekMija;
    private SecureBytes maestraMija;
    private SecureBytes claveHeaderMija;
    private Header headerMija;

    @BeforeEach
    void preparar() {
        kekMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32));
        maestraMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32));
        claveHeaderMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32));
        Header sinMacMija = FormatoPrueba.header(kekMija, maestraMija);
        headerMija = sinMacMija.conMac(HeaderCodec.calcularMac(sinMacMija, claveHeaderMija));
    }

    @AfterEach
    void limpiar() {
        kekMija.close();
        maestraMija.close();
        claveHeaderMija.close();
    }

    @Test
    void idaYVueltaConservaElHeaderYMide16KiB() {
        byte[] bloqueMija = HeaderCodec.codificar(headerMija, FUENTE_MIJA);

        assertEquals(16 * 1024, bloqueMija.length);
        Header leidoMija = HeaderCodec.decodificar(bloqueMija);
        assertEquals(headerMija, leidoMija);
        assertTrue(HeaderCodec.macValido(leidoMija, claveHeaderMija));
    }

    @Test
    void elSlotPrincipalAbreConLaZonaAComoAad() {
        Header leidoMija = HeaderCodec.decodificar(HeaderCodec.codificar(headerMija, FUENTE_MIJA));
        byte[] aadMija = HeaderCodec.datosAsociadosSlot(leidoMija.zonaAMija(), 0, TipoSlot.PRINCIPAL, leidoMija.metadatosPrincipal());

        try (SecureBytes recuperadaMija = KeyWrap.desenvolver(kekMija, leidoMija.slotsMija().getFirst().envueltaMija(),
                aadMija).orElseThrow()) {
            assertTrue(Arrays.equals(maestraMija.bytes(), recuperadaMija.bytes()));
        }
    }

    @Test
    void elSlotUsaLaZonaAElIndiceYElTipoComoAad() {
        Slot slotMija = headerMija.slotsMija().getFirst();
        ZonaA zonaMija = headerMija.zonaAMija();
        ZonaA otraZonaMija = ZonaA.nueva(zonaMija.argonMija(), FUENTE_MIJA);

        try (SecureBytes abiertaMija = slotMija.abrir(zonaMija, 0, headerMija.metadatosPrincipal(), kekMija).orElseThrow()) {
            assertTrue(Arrays.equals(maestraMija.bytes(), abiertaMija.bytes()));
        }
        assertTrue(slotMija.abrir(otraZonaMija, 0, headerMija.metadatosPrincipal(), kekMija).isEmpty(), "otra Zona A");
        assertTrue(slotMija.abrir(zonaMija, 3, headerMija.metadatosPrincipal(), kekMija).isEmpty(), "otra posición");
        Slot comoTokenMija = new Slot(TipoSlot.TOKEN, slotMija.salMija(), slotMija.envueltaMija());
        assertTrue(comoTokenMija.abrir(zonaMija, 0, headerMija.metadatosPrincipal(), kekMija).isEmpty(), "otro tipo");
        assertTrue(Slot.vacio(FUENTE_MIJA).abrir(zonaMija, 0, headerMija.metadatosPrincipal(), kekMija).isEmpty(), "vacío");
    }

    @Test
    void unBitEnZonaAOZonaBRompeElCrc() {
        for (int offsetMija : new int[] {7, ZonaA.TAMANO_MIJA + 20, HeaderCodec.OFFSET_MAC_MIJA + 3}) {
            byte[] bloqueMija = HeaderCodec.codificar(headerMija, FUENTE_MIJA);
            bloqueMija[offsetMija] ^= 0x01;

            esperarIntegridad(bloqueMija);
        }
    }

    @Test
    void manipulacionConCrcRecalculadoLaDetectaElMac() {
        byte[] bloqueMija = HeaderCodec.codificar(headerMija, FUENTE_MIJA);
        bloqueMija[ZonaA.TAMANO_MIJA + 3] ^= 0x01; // generación
        FormatoPrueba.recalcularCrc(bloqueMija);

        assertFalse(HeaderCodec.macValido(HeaderCodec.decodificar(bloqueMija), claveHeaderMija));
    }

    @Test
    void cambiarLaZonaAImpideAbrirElSlot() {
        byte[] bloqueMija = HeaderCodec.codificar(headerMija, FUENTE_MIJA);
        bloqueMija[OFFSET_MEMORIA_MIJA + 3] ^= 0x01; // m = 256 MiB ± 1 KiB, sigue en rango
        FormatoPrueba.recalcularCrc(bloqueMija);
        Header alteradoMija = HeaderCodec.decodificar(bloqueMija);

        assertTrue(KeyWrap.desenvolver(kekMija, alteradoMija.slotsMija().getFirst().envueltaMija(),
                HeaderCodec.datosAsociadosSlot(alteradoMija.zonaAMija(), 0, TipoSlot.PRINCIPAL, alteradoMija.metadatosPrincipal())).isEmpty());
    }

    @Test
    void laZonaCNoAfectaNiCrcNiMac() {
        byte[] bloqueMija = HeaderCodec.codificar(headerMija, FUENTE_MIJA);
        ByteBuffer.wrap(bloqueMija).putInt(HeaderCodec.OFFSET_ZONA_C_MIJA, 42);
        bloqueMija[HeaderCodec.OFFSET_ZONA_C_MIJA + 200] ^= 0x7F; // relleno

        Header leidoMija = HeaderCodec.decodificar(bloqueMija);
        assertEquals(42, leidoMija.intentosFallidosMija());
        assertTrue(HeaderCodec.macValido(leidoMija, claveHeaderMija));
    }

    @Test
    void memoriaDe64GiBSeRechazaAntesDeDerivarEnMenosDe10Ms() {
        byte[] bloqueMija = HeaderCodec.codificar(headerMija, FUENTE_MIJA);
        ByteBuffer.wrap(bloqueMija).putInt(OFFSET_MEMORIA_MIJA, 64 * 1024 * 1024); // KiB → 64 GiB
        FormatoPrueba.recalcularCrc(bloqueMija);
        esperarError(bloqueMija, ErrorBoveda.ParametrosFueraDeRango.class); // calentamiento

        long inicioMija = System.nanoTime();
        esperarError(bloqueMija, ErrorBoveda.ParametrosFueraDeRango.class);
        long milisegundosMija = (System.nanoTime() - inicioMija) / 1_000_000;

        assertTrue(milisegundosMija < 10, () -> "Tardó " + milisegundosMija + " ms");
    }

    @Test
    void parametrosDebilesTambienSeRechazan() {
        byte[] bloqueMija = HeaderCodec.codificar(headerMija, FUENTE_MIJA);
        ByteBuffer.wrap(bloqueMija).putInt(OFFSET_MEMORIA_MIJA, 1024); // 1 MiB < 64 MiB
        FormatoPrueba.recalcularCrc(bloqueMija);

        esperarError(bloqueMija, ErrorBoveda.ParametrosFueraDeRango.class);
    }

    @Test
    void versionDesconocidaEsFormatoNoSoportado() {
        byte[] bloqueMija = HeaderCodec.codificar(headerMija, FUENTE_MIJA);
        ByteBuffer.wrap(bloqueMija).putShort(5, (short) 2);
        FormatoPrueba.recalcularCrc(bloqueMija);

        esperarError(bloqueMija, ErrorBoveda.FormatoNoSoportado.class);
    }

    @Test
    void cantidadDeSlotsFueraDeRango() {
        byte[] bloqueMija = HeaderCodec.codificar(headerMija, FUENTE_MIJA);
        bloqueMija[ZonaA.TAMANO_MIJA + 13] = 9; // nSlots
        FormatoPrueba.recalcularCrc(bloqueMija);

        esperarError(bloqueMija, ErrorBoveda.ParametrosFueraDeRango.class);
    }

    @Test
    void bloquesBasuraNuncaLanzanExcepcionesNoControladas() {
        for (int intentoMija = 0; intentoMija < 2_000; intentoMija++) {
            byte[] basuraMija = FUENTE_MIJA.bytes(16 * 1024);
            if (intentoMija % 2 == 0) {
                FormatoPrueba.recalcularCrc(basuraMija);
            }
            assertThrows(ExcepcionBoveda.class, () -> HeaderCodec.decodificar(basuraMija));
        }
        assertThrows(ExcepcionBoveda.class, () -> HeaderCodec.decodificar(new byte[10]));
    }

    private static void esperarIntegridad(byte[] bloqueMija) {
        ErrorBoveda.IntegridadFallida errorMija = esperarError(bloqueMija, ErrorBoveda.IntegridadFallida.class);
        assertEquals(ErrorBoveda.IntegridadFallida.Zona.HEADER, errorMija.zonaMija());
    }

    private static <T extends ErrorBoveda> T esperarError(byte[] bloqueMija, Class<T> tipoMija) {
        ExcepcionBoveda excepcionMija = assertThrows(ExcepcionBoveda.class, () -> HeaderCodec.decodificar(bloqueMija));
        return assertInstanceOf(tipoMija, excepcionMija.error());
    }
}
