package dev.erick.cryptokipu.core.format;

import static dev.erick.cryptokipu.core.format.LimitesFormato.ARGON2_ITERACIONES_MAX_MIJA;
import static dev.erick.cryptokipu.core.format.LimitesFormato.ARGON2_ITERACIONES_MIN_MIJA;
import static dev.erick.cryptokipu.core.format.LimitesFormato.ARGON2_MEMORIA_MAX_KIB_MIJA;
import static dev.erick.cryptokipu.core.format.LimitesFormato.ARGON2_MEMORIA_MIN_KIB_MIJA;
import static dev.erick.cryptokipu.core.format.LimitesFormato.ARGON2_PARALELISMO_MAX_MIJA;
import static dev.erick.cryptokipu.core.format.LimitesFormato.ARGON2_PARALELISMO_MIN_MIJA;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Fronteras exactas y álgebra (equals, copias defensivas) de los registros del formato. Cada comprobación mata un
 * grupo de mutantes de PIT (Fase 9): un {@code <} cambiado por {@code <=}, un {@code ||} por {@code &&}, un campo
 * omitido en {@code equals}…
 */
class LimitesDelFormatoTest {

    private static final FuenteAleatoria FUENTE_MIJA = FuenteAleatoria.delSistema();
    private static final ParametrosArgon2 ARGON_MIJA = new ParametrosArgon2(ARGON2_MEMORIA_MIN_KIB_MIJA, 2, 1);

    private static ZonaA zona() {
        return ZonaA.nueva(ARGON_MIJA, FUENTE_MIJA);
    }

    private static ZonaA zona(ParametrosArgon2 argonMija) {
        return new ZonaA(argonMija, new byte[32], new byte[32], new byte[16]);
    }

    private static ExcepcionBoveda decodificarFalla(byte[] bytesMija) {
        return assertThrows(ExcepcionBoveda.class, () -> ZonaA.decodificar(ByteBuffer.wrap(bytesMija)));
    }

    private static byte[] zonaBytes(Consumer<ByteBuffer> modificarMija) {
        byte[] bytesMija = zona(ARGON_MIJA).codificar();
        modificarMija.accept(ByteBuffer.wrap(bytesMija));
        return bytesMija;
    }

    private static void esParametros(ExcepcionBoveda errorMija, String campoMija) {
        assertEquals(campoMija, assertInstanceOf(ErrorBoveda.ParametrosFueraDeRango.class, errorMija.error()).campoMija());
    }

    private static void esFormato(ExcepcionBoveda errorMija, int valorMija) {
        assertEquals(valorMija, assertInstanceOf(ErrorBoveda.FormatoNoSoportado.class, errorMija.error())
                .versionFormatoMija());
    }

    // ------------------------------------------------------------------ ZonaA

    @Test
    void zonaIdaYVueltaConservaTodosLosCampos() {
        ZonaA origenMija = zona();
        byte[] bytesMija = origenMija.codificar();
        assertEquals(ZonaA.TAMANO_MIJA, bytesMija.length);

        ZonaA leidaMija = ZonaA.decodificar(ByteBuffer.wrap(bytesMija));

        assertEquals(origenMija, leidaMija);
        assertEquals(origenMija.hashCode(), leidaMija.hashCode());
        assertArrayEquals(origenMija.salMija(), leidaMija.salMija());
        assertArrayEquals(origenMija.salTokensMija(), leidaMija.salTokensMija());
        assertArrayEquals(origenMija.vaultIdMija(), leidaMija.vaultIdMija());
        assertEquals(ARGON_MIJA, leidaMija.argonMija());
    }

    @Test
    void zonaDecodificaLosLimitesInclusivosDeArgon() {
        for (int[] valoresMija : new int[][] {
            {ARGON2_MEMORIA_MIN_KIB_MIJA, ARGON2_ITERACIONES_MIN_MIJA, ARGON2_PARALELISMO_MIN_MIJA},
            {ARGON2_MEMORIA_MAX_KIB_MIJA, ARGON2_ITERACIONES_MAX_MIJA, ARGON2_PARALELISMO_MAX_MIJA}}) {
            ZonaA origenMija = zona(new ParametrosArgon2(valoresMija[0], valoresMija[1], valoresMija[2]));
            ZonaA leidaMija = assertDoesNotThrow(() -> ZonaA.decodificar(ByteBuffer.wrap(origenMija.codificar())));
            assertEquals(origenMija, leidaMija);
        }
    }

    @Test
    void zonaRechazaCadaParametroFueraDeRangoAlDecodificar() {
        esParametros(decodificarFalla(zonaBytes(bufferMija -> bufferMija.putInt(13, ARGON2_MEMORIA_MIN_KIB_MIJA - 1))),
                "argon2.m");
        esParametros(decodificarFalla(zonaBytes(bufferMija -> bufferMija.putInt(13, ARGON2_MEMORIA_MAX_KIB_MIJA + 1))),
                "argon2.m");
        esParametros(decodificarFalla(zonaBytes(bufferMija -> bufferMija.putInt(13, -1))), "argon2.m");
        esParametros(decodificarFalla(zonaBytes(bufferMija -> bufferMija.put(17, (byte) (ARGON2_ITERACIONES_MIN_MIJA - 1)))),
                "argon2.t");
        esParametros(decodificarFalla(zonaBytes(bufferMija -> bufferMija.put(17, (byte) (ARGON2_ITERACIONES_MAX_MIJA + 1)))),
                "argon2.t");
        esParametros(decodificarFalla(zonaBytes(bufferMija -> bufferMija.put(18, (byte) 0))), "argon2.p");
        esParametros(decodificarFalla(zonaBytes(bufferMija -> bufferMija.put(18, (byte) (ARGON2_PARALELISMO_MAX_MIJA + 1)))),
                "argon2.p");
        esParametros(decodificarFalla(zonaBytes(bufferMija -> bufferMija.putInt(99, 1))), "tamanoBloque");
        esParametros(decodificarFalla(zonaBytes(bufferMija -> bufferMija.putInt(99, LimitesFormato.TAMANO_BLOQUE_MIJA + 1))),
                "tamanoBloque");
    }

    @Test
    void zonaRechazaMagicVersionYAlgoritmosDesconocidos() {
        ExcepcionBoveda magicMija = decodificarFalla(zonaBytes(bufferMija -> bufferMija.put(4, (byte) 'X')));
        assertInstanceOf(ErrorBoveda.IntegridadFallida.class, magicMija.error());
        esFormato(decodificarFalla(zonaBytes(bufferMija -> bufferMija.putShort(5, (short) 2))), 2);
        esFormato(decodificarFalla(zonaBytes(bufferMija -> bufferMija.put(7, (byte) 2))), 2);
        esFormato(decodificarFalla(zonaBytes(bufferMija -> bufferMija.put(8, (byte) 3))), 3);
        esFormato(decodificarFalla(zonaBytes(bufferMija -> bufferMija.put(9, (byte) 4))), 4);
        esFormato(decodificarFalla(zonaBytes(bufferMija -> bufferMija.putShort(10, (short) 9))), 9);
        esFormato(decodificarFalla(zonaBytes(bufferMija -> bufferMija.put(12, (byte) 7))), 7);
        // Un valor sin signo grande (0xFFFF) se informa tal cual, no como negativo.
        esFormato(decodificarFalla(zonaBytes(bufferMija -> bufferMija.putShort(5, (short) 0xFFFF))), 0xFFFF);
        esFormato(decodificarFalla(zonaBytes(bufferMija -> bufferMija.put(7, (byte) 0xFF))), 0xFF);
    }

    @Test
    void zonaValidaSusParametrosYLongitudesAlConstruirse() {
        assertThrows(ExcepcionBoveda.class, () -> zona(new ParametrosArgon2(ARGON2_MEMORIA_MIN_KIB_MIJA - 1, 2, 1)));
        assertThrows(ExcepcionBoveda.class, () -> zona(new ParametrosArgon2(ARGON2_MEMORIA_MAX_KIB_MIJA + 1, 2, 1)));
        assertThrows(ExcepcionBoveda.class, () -> zona(new ParametrosArgon2(ARGON2_MEMORIA_MIN_KIB_MIJA, 1, 1)));
        assertThrows(ExcepcionBoveda.class, () -> zona(new ParametrosArgon2(ARGON2_MEMORIA_MIN_KIB_MIJA,
                ARGON2_ITERACIONES_MAX_MIJA + 1, 1)));
        assertThrows(ExcepcionBoveda.class, () -> zona(new ParametrosArgon2(ARGON2_MEMORIA_MIN_KIB_MIJA, 2,
                ARGON2_PARALELISMO_MAX_MIJA + 1)));
        assertThrows(IllegalArgumentException.class, () -> new ZonaA(ARGON_MIJA, new byte[31], new byte[32], new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new ZonaA(ARGON_MIJA, new byte[32], new byte[33], new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new ZonaA(ARGON_MIJA, new byte[32], new byte[32], new byte[15]));
        assertThrows(NullPointerException.class, () -> new ZonaA(null, new byte[32], new byte[32], new byte[16]));
    }

    @Test
    void zonaEqualsComparaCadaCampoYLasCopiasSonDefensivas() {
        ZonaA baseMija = zona();
        ZonaA igualMija = new ZonaA(baseMija.argonMija(), baseMija.salMija(), baseMija.salTokensMija(), baseMija.vaultIdMija());
        assertEquals(baseMija, igualMija);
        assertEquals(baseMija.hashCode(), igualMija.hashCode());
        byte[] otraSalMija = baseMija.salMija();
        otraSalMija[0]++;
        assertNotEquals(baseMija, new ZonaA(baseMija.argonMija(), otraSalMija, baseMija.salTokensMija(), baseMija.vaultIdMija()));
        byte[] otraSalTokensMija = baseMija.salTokensMija();
        otraSalTokensMija[0]++;
        assertNotEquals(baseMija, new ZonaA(baseMija.argonMija(), baseMija.salMija(), otraSalTokensMija, baseMija.vaultIdMija()));
        byte[] otroIdMija = baseMija.vaultIdMija();
        otroIdMija[0]++;
        assertNotEquals(baseMija, new ZonaA(baseMija.argonMija(), baseMija.salMija(), baseMija.salTokensMija(), otroIdMija));
        assertNotEquals(baseMija, new ZonaA(new ParametrosArgon2(ARGON2_MEMORIA_MIN_KIB_MIJA, 3, 1), baseMija.salMija(),
                baseMija.salTokensMija(), baseMija.vaultIdMija()));
        assertNotEquals(baseMija, "no es una zona");
        assertNotEquals(baseMija, null);
        // Lo que devuelven los accesores es una copia: modificarla no cambia la zona.
        baseMija.salMija()[0]++;
        baseMija.salTokensMija()[0]++;
        baseMija.vaultIdMija()[0]++;
        assertEquals(igualMija, baseMija);
        assertFalse(baseMija.toString().contains("sal"));
        assertTrue(baseMija.toString().startsWith("ZonaA["));
    }

    // ------------------------------------------------------------------ Header

    private static Header headerBase() {
        return Header.nuevo(zona(), List.of(Slot.vacio(FUENTE_MIJA)), 0);
    }

    @Test
    void headerValidaSlotsMacYNegativos() {
        ZonaA zonaMija = zona();
        List<Slot> unoMija = List.of(Slot.vacio(FUENTE_MIJA));
        List<Slot> sieteMija = new ArrayList<>();
        for (int indiceMija = 0; indiceMija < 7; indiceMija++) {
            sieteMija.add(Slot.vacio(FUENTE_MIJA));
        }
        byte[] macMija = new byte[32];
        assertDoesNotThrow(() -> new Header(zonaMija, unoMija, 1, 0, false, macMija, 0, 0));
        assertDoesNotThrow(() -> new Header(zonaMija, sieteMija, 1, 0, false, macMija, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Header(zonaMija, List.of(), 1, 0, false, macMija, 0, 0));
        List<Slot> ochoMija = new ArrayList<>(sieteMija);
        ochoMija.add(Slot.vacio(FUENTE_MIJA));
        assertThrows(IllegalArgumentException.class, () -> new Header(zonaMija, ochoMija, 1, 0, false, macMija, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Header(zonaMija, unoMija, 1, 0, false, new byte[31], 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Header(zonaMija, unoMija, 1, 0, false, new byte[33], 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Header(zonaMija, unoMija, -1, 0, false, macMija, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Header(zonaMija, unoMija, 1, -1, false, macMija, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Header(zonaMija, unoMija, 1, 0, false, macMija, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Header(zonaMija, unoMija, 1, 0, false, macMija, 0, -1));
        assertDoesNotThrow(() -> new Header(zonaMija, unoMija, 0, 0, false, macMija, 0, 0));
        assertThrows(NullPointerException.class, () -> new Header(null, unoMija, 1, 0, false, macMija, 0, 0));
        assertThrows(NullPointerException.class, () -> new Header(zonaMija, unoMija, 1, 0, false, null, 0, 0));
    }

    @Test
    void headerEqualsComparaCadaCampo() {
        ZonaA zonaMija = zona();
        List<Slot> slotsMija = List.of(Slot.vacio(FUENTE_MIJA), Slot.vacio(FUENTE_MIJA));
        byte[] macMija = new byte[32];
        Header baseMija = new Header(zonaMija, slotsMija, 5, 3, true, macMija, 2, 99);
        assertEquals(baseMija, new Header(zonaMija, slotsMija, 5, 3, true, macMija, 2, 99));
        assertEquals(baseMija.hashCode(), new Header(zonaMija, slotsMija, 5, 3, true, macMija, 2, 99).hashCode());
        byte[] otroMacMija = new byte[32];
        otroMacMija[31] = 1;
        for (Header distintoMija : List.of(
                new Header(zona(), slotsMija, 5, 3, true, macMija, 2, 99),
                new Header(zonaMija, List.of(Slot.vacio(FUENTE_MIJA), Slot.vacio(FUENTE_MIJA)), 5, 3, true, macMija, 2, 99),
                new Header(zonaMija, slotsMija, 6, 3, true, macMija, 2, 99),
                new Header(zonaMija, slotsMija, 5, 4, true, macMija, 2, 99),
                new Header(zonaMija, slotsMija, 5, 3, false, macMija, 2, 99),
                new Header(zonaMija, slotsMija, 5, 3, true, otroMacMija, 2, 99),
                new Header(zonaMija, slotsMija, 5, 3, true, macMija, 3, 99),
                new Header(zonaMija, slotsMija, 5, 3, true, macMija, 2, 100))) {
            assertNotEquals(baseMija, distintoMija);
        }
        assertNotEquals(baseMija, "otra cosa");
        baseMija.macMija()[0]++;
        assertArrayEquals(macMija, baseMija.macMija(), "macMija() devuelve una copia");
        assertTrue(baseMija.toString().contains("generacion=5"));
    }

    @Test
    void headerDerivadosCambianSoloLoQueDicen() {
        Header baseMija = new Header(zona(), List.of(Slot.vacio(FUENTE_MIJA)), 7, 3, true, new byte[32], 2, 99);
        byte[] macNuevoMija = new byte[32];
        macNuevoMija[0] = 9;

        Header conMacMija = baseMija.conMac(macNuevoMija);
        assertArrayEquals(macNuevoMija, conMacMija.macMija());
        assertEquals(7, conMacMija.generacionMija());
        assertEquals(2, conMacMija.intentosFallidosMija());

        List<Slot> otrosMija = List.of(Slot.vacio(FUENTE_MIJA), Slot.vacio(FUENTE_MIJA));
        Header conZonaBMija = baseMija.conZonaB(otrosMija, 5, false);
        assertEquals(8, conZonaBMija.generacionMija());
        assertEquals(5, conZonaBMija.umbralAutodestruccionMija());
        assertFalse(conZonaBMija.requiereLlaveMija());
        assertEquals(otrosMija, conZonaBMija.slotsMija());
        assertArrayEquals(new byte[32], conZonaBMija.macMija(), "el MAC se pone en cero: hay que recalcularlo");
        assertEquals(2, conZonaBMija.intentosFallidosMija(), "la zona C se conserva");
        assertEquals(99, conZonaBMija.pistaFooterMija());
        assertTrue(baseMija.conZonaB(otrosMija, 5).requiereLlaveMija(), "sin indicarlo conserva requiereLlave");
        assertEquals(8, baseMija.conZonaB(otrosMija, 5).generacionMija());

        Header conZonaCMija = baseMija.conZonaC(11, 222);
        assertEquals(11, conZonaCMija.intentosFallidosMija());
        assertEquals(222, conZonaCMija.pistaFooterMija());
        assertEquals(7, conZonaCMija.generacionMija());
        assertEquals(3, conZonaCMija.umbralAutodestruccionMija());
        assertTrue(conZonaCMija.requiereLlaveMija());
        assertEquals(baseMija.slotsMija(), conZonaCMija.slotsMija());

        assertTrue(baseMija.estaDestruido(), "un slot vacío en el índice 0 es una bóveda destruida");
        assertEquals(new MetadatosPrincipal(true, 3), baseMija.metadatosPrincipal());
        assertEquals(1, Header.nuevo(zona(), List.of(Slot.vacio(FUENTE_MIJA)), 0).generacionMija());
        assertFalse(Header.nuevo(zona(), List.of(Slot.vacio(FUENTE_MIJA)), 0).requiereLlaveMija());
        assertTrue(Header.nuevo(zona(), List.of(Slot.vacio(FUENTE_MIJA)), 0, true).requiereLlaveMija());
    }

    // ------------------------------------------------------------------ MetadatosPrincipal y ParametrosArgon2

    @Test
    void metadatosCodificanSiete_bytes_y_rechazanUmbralNegativo() {
        assertArrayEquals(new byte[] {1, 0, 1, 0, 0, 0, 5}, new MetadatosPrincipal(true, 5).codificar());
        assertArrayEquals(new byte[] {0, 0, 1, 0, 0, 1, 0}, new MetadatosPrincipal(false, 256).codificar());
        assertThrows(IllegalArgumentException.class, () -> new MetadatosPrincipal(false, -1));
        assertDoesNotThrow(() -> new MetadatosPrincipal(false, 0));
    }

    @Test
    void parametrosDeArgonValidanSusFronterasExactas() {
        assertThrows(IllegalArgumentException.class, () -> new ParametrosArgon2(1024, 0, 1));
        assertDoesNotThrow(() -> new ParametrosArgon2(1024, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosArgon2(1024, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosArgon2(1 << 30, 1, 0x1000000));
        assertDoesNotThrow(() -> new ParametrosArgon2(8 * 0xFFFFFF, 1, 0xFFFFFF));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosArgon2(7, 1, 1));
        assertDoesNotThrow(() -> new ParametrosArgon2(8, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ParametrosArgon2(31, 1, 4));
        assertDoesNotThrow(() -> new ParametrosArgon2(32, 1, 4));
        assertEquals(64L * 1024 * 1024, new ParametrosArgon2(65536, 2, 1).memoriaBytes());
        assertEquals(3L * 1024 * 1024 * 1024, new ParametrosArgon2(3 * 1024 * 1024, 2, 1).memoriaBytes(),
                "memoriaBytes no debe desbordar int");
    }

    // ------------------------------------------------------------------ EntradaManifest

    private static EntradaManifest entrada(byte[] idMija, String rutaMija, long tamanoMija, long fechaMija, String mimeMija,
            long offsetMija, long cifradaMija) {
        return new EntradaManifest(idMija, rutaMija, tamanoMija, fechaMija, mimeMija, offsetMija, cifradaMija);
    }

    @Test
    void entradaValidaCadaFronteraExacta() {
        byte[] idMija = new byte[16];
        long datosMija = LimitesFormato.OFFSET_DATOS_MIJA;
        assertDoesNotThrow(() -> entrada(idMija, "a", 0, 0, "", datosMija, 0));
        assertThrows(IllegalArgumentException.class, () -> entrada(new byte[15], "a", 0, 0, "", datosMija, 0));
        assertThrows(IllegalArgumentException.class, () -> entrada(new byte[17], "a", 0, 0, "", datosMija, 0));
        assertThrows(IllegalArgumentException.class, () -> entrada(idMija, "   ", 0, 0, "", datosMija, 0));
        assertThrows(IllegalArgumentException.class, () -> entrada(idMija, "", 0, 0, "", datosMija, 0));
        assertDoesNotThrow(() -> entrada(idMija, "x".repeat(LimitesFormato.RUTA_MAX_BYTES_MIJA), 0, 0, "", datosMija, 0));
        assertThrows(IllegalArgumentException.class,
                () -> entrada(idMija, "x".repeat(LimitesFormato.RUTA_MAX_BYTES_MIJA + 1), 0, 0, "", datosMija, 0));
        // La longitud se mide en bytes UTF-8, no en caracteres: «ñ» ocupa 2.
        assertThrows(IllegalArgumentException.class,
                () -> entrada(idMija, "ñ".repeat(LimitesFormato.RUTA_MAX_BYTES_MIJA / 2 + 1), 0, 0, "", datosMija, 0));
        assertDoesNotThrow(() -> entrada(idMija, "ñ".repeat(LimitesFormato.RUTA_MAX_BYTES_MIJA / 2), 0, 0, "", datosMija, 0));
        assertDoesNotThrow(() -> entrada(idMija, "a", 0, 0, "m".repeat(LimitesFormato.MIME_MAX_BYTES_MIJA), datosMija, 0));
        assertThrows(IllegalArgumentException.class,
                () -> entrada(idMija, "a", 0, 0, "m".repeat(LimitesFormato.MIME_MAX_BYTES_MIJA + 1), datosMija, 0));
        assertThrows(IllegalArgumentException.class, () -> entrada(idMija, "a", -1, 0, "", datosMija, 0));
        assertThrows(IllegalArgumentException.class, () -> entrada(idMija, "a", 0, 0, "", datosMija - 1, 0));
        assertThrows(IllegalArgumentException.class, () -> entrada(idMija, "a", 0, 0, "", datosMija, -1));
        assertDoesNotThrow(() -> entrada(idMija, "a", 0, -5, "", datosMija, 0)); // una fecha anterior a 1970 es válida
        assertThrows(NullPointerException.class, () -> entrada(null, "a", 0, 0, "", datosMija, 0));
        assertThrows(NullPointerException.class, () -> entrada(idMija, null, 0, 0, "", datosMija, 0));
        assertThrows(NullPointerException.class, () -> entrada(idMija, "a", 0, 0, null, datosMija, 0));
    }

    @Test
    void entradaEqualsToStringYCopias() {
        byte[] idMija = new byte[16];
        idMija[0] = 1;
        long datosMija = LimitesFormato.OFFSET_DATOS_MIJA;
        EntradaManifest baseMija = entrada(idMija, "carpeta/nota.txt", 10, 20, "text/plain", datosMija, 30);
        assertEquals(baseMija, entrada(idMija, "carpeta/nota.txt", 10, 20, "text/plain", datosMija, 30));
        byte[] otroIdMija = new byte[16];
        for (EntradaManifest distintaMija : List.of(
                entrada(otroIdMija, "carpeta/nota.txt", 10, 20, "text/plain", datosMija, 30),
                entrada(idMija, "carpeta/otra.txt", 10, 20, "text/plain", datosMija, 30),
                entrada(idMija, "carpeta/nota.txt", 11, 20, "text/plain", datosMija, 30),
                entrada(idMija, "carpeta/nota.txt", 10, 21, "text/plain", datosMija, 30),
                entrada(idMija, "carpeta/nota.txt", 10, 20, "text/html", datosMija, 30),
                entrada(idMija, "carpeta/nota.txt", 10, 20, "text/plain", datosMija + 1, 30),
                entrada(idMija, "carpeta/nota.txt", 10, 20, "text/plain", datosMija, 31))) {
            assertNotEquals(baseMija, distintaMija);
        }
        assertNotEquals(baseMija, "otra cosa");
        baseMija.fileIdMija()[0] = 77;
        assertEquals(1, baseMija.fileIdMija()[0], "fileIdMija() devuelve una copia");
        assertEquals("EntradaManifest[tamano=10]", baseMija.toString());
        assertFalse(baseMija.toString().contains("nota"), "el nombre del archivo nunca se muestra");

        EntradaManifest movidaMija = baseMija.conOffset(datosMija + 100);
        assertEquals(datosMija + 100, movidaMija.offsetMija());
        assertEquals(baseMija.rutaMija(), movidaMija.rutaMija());
        assertEquals(baseMija.tamanoMija(), movidaMija.tamanoMija());
        assertEquals(baseMija.modificadoMija(), movidaMija.modificadoMija());
        assertEquals(baseMija.tipoMimeMija(), movidaMija.tipoMimeMija());
        assertEquals(baseMija.longitudCifradaMija(), movidaMija.longitudCifradaMija());
        assertArrayEquals(baseMija.fileIdMija(), movidaMija.fileIdMija());
    }

    // ------------------------------------------------------------------ Footer

    @Test
    void footerValidaCadaFronteraExacta() {
        long minimoMija = LimitesFormato.OFFSET_DATOS_MIJA;
        assertDoesNotThrow(() -> new Footer(minimoMija, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Footer(minimoMija - 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Footer(minimoMija, 0, 1));
        assertDoesNotThrow(() -> new Footer(minimoMija, LimitesFormato.MANIFEST_MAX_BYTES_MIJA, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Footer(minimoMija, LimitesFormato.MANIFEST_MAX_BYTES_MIJA + 1L, 1));
        assertThrows(IllegalArgumentException.class, () -> new Footer(minimoMija, 1, 0));
    }

    @Test
    void footerLeerExigeMagicCrcLongitudYPosicionExacta() {
        Footer footerMija = new Footer(LimitesFormato.OFFSET_DATOS_MIJA, 100, 4);
        byte[] bytesMija = footerMija.codificar();
        long posicionMija = footerMija.offsetManifestMija() + footerMija.longitudManifestMija();
        assertEquals(Footer.TAMANO_MIJA, bytesMija.length);
        assertEquals(Optional.of(footerMija), Footer.leer(bytesMija, posicionMija));
        assertEquals(Optional.empty(), Footer.leer(bytesMija, posicionMija + 1));
        assertEquals(Optional.empty(), Footer.leer(bytesMija, posicionMija - 1));
        assertEquals(Optional.empty(), Footer.leer(new byte[Footer.TAMANO_MIJA - 1], posicionMija));
        assertEquals(Optional.empty(), Footer.leer(new byte[Footer.TAMANO_MIJA + 1], posicionMija));
        byte[] magicRotoMija = bytesMija.clone();
        magicRotoMija[Footer.OFFSET_MAGIC_MIJA] ^= 1;
        assertEquals(Optional.empty(), Footer.leer(magicRotoMija, posicionMija));
        byte[] crcRotoMija = bytesMija.clone();
        crcRotoMija[Footer.TAMANO_MIJA - 1] ^= 1;
        assertEquals(Optional.empty(), Footer.leer(crcRotoMija, posicionMija));
        for (int indiceMija = 0; indiceMija < Footer.TAMANO_MIJA; indiceMija++) {
            byte[] danadoMija = bytesMija.clone();
            danadoMija[indiceMija] ^= 0x10;
            assertEquals(Optional.empty(), Footer.leer(danadoMija, posicionMija), "byte " + indiceMija);
        }
        // Un footer cuyos campos son inválidos pero con CRC correcto tampoco se acepta.
        byte[] invalidoMija = ByteBuffer.allocate(Footer.TAMANO_MIJA).putLong(1).putLong(1).putLong(1)
                .put(LimitesFormato.MAGIC_FOOTER_MIJA).putInt(0).array();
        java.util.zip.CRC32C crcMija = new java.util.zip.CRC32C();
        crcMija.update(invalidoMija, 0, Footer.TAMANO_MIJA - 4);
        ByteBuffer.wrap(invalidoMija).putInt(Footer.TAMANO_MIJA - 4, (int) crcMija.getValue());
        assertEquals(Optional.empty(), Footer.leer(invalidoMija, 2));
    }

    // ------------------------------------------------------------------ Manifest

    private static EntradaManifest entradaN(int numeroMija) {
        byte[] idMija = new byte[16];
        idMija[0] = (byte) numeroMija;
        return entrada(idMija, "f" + numeroMija, numeroMija, 0, "", LimitesFormato.OFFSET_DATOS_MIJA, 1);
    }

    @Test
    void manifestValidaGeneracionYCantidad() {
        assertThrows(IllegalArgumentException.class, () -> new Manifest(0, List.of()));
        assertDoesNotThrow(() -> new Manifest(1, List.of()));
        EntradaManifest unaMija = entradaN(1);
        List<EntradaManifest> maximoMija = java.util.Collections.nCopies(LimitesFormato.MANIFEST_MAX_ENTRADAS_MIJA, unaMija);
        assertDoesNotThrow(() -> new Manifest(1, maximoMija));
        List<EntradaManifest> demasiadasMija = java.util.Collections.nCopies(LimitesFormato.MANIFEST_MAX_ENTRADAS_MIJA + 1,
                unaMija);
        assertThrows(IllegalArgumentException.class, () -> new Manifest(1, demasiadasMija));
        assertEquals(1, Manifest.vacio().generacionMija());
        assertTrue(Manifest.vacio().entradasMija().isEmpty());
    }

    @Test
    void manifestAgregaQuitaYBuscaConGeneracionSiguiente() {
        Manifest baseMija = new Manifest(5, List.of(entradaN(1), entradaN(2), entradaN(3)));
        byte[] idDosMija = entradaN(2).fileIdMija();

        assertEquals(Optional.of(entradaN(2)), baseMija.buscar(idDosMija));
        assertEquals(Optional.empty(), baseMija.buscar(entradaN(9).fileIdMija()));
        assertEquals(Optional.empty(), Manifest.vacio().buscar(idDosMija));

        Manifest agregadoMija = baseMija.conAgregadas(List.of(entradaN(4)));
        assertEquals(6, agregadoMija.generacionMija());
        assertEquals(List.of(entradaN(1), entradaN(2), entradaN(3), entradaN(4)), agregadoMija.entradasMija());

        Manifest sinDosMija = baseMija.sinEntrada(idDosMija);
        assertEquals(6, sinDosMija.generacionMija());
        assertEquals(List.of(entradaN(1), entradaN(3)), sinDosMija.entradasMija());

        Manifest cambiadoMija = baseMija.conCambios(List.of(idDosMija, entradaN(1).fileIdMija()), List.of(entradaN(7)));
        assertEquals(6, cambiadoMija.generacionMija());
        assertEquals(List.of(entradaN(3), entradaN(7)), cambiadoMija.entradasMija());
        assertEquals(baseMija.entradasMija(), baseMija.conCambios(List.of(), List.of()).entradasMija());

        Manifest reemplazadoMija = baseMija.conEntradas(List.of(entradaN(8)));
        assertEquals(6, reemplazadoMija.generacionMija());
        assertEquals(List.of(entradaN(8)), reemplazadoMija.entradasMija());
        assertEquals(3, baseMija.entradasMija().size(), "el original no cambia");
    }
}
