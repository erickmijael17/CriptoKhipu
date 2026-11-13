package dev.erick.cryptokipu.infra.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.app.puertos.AbridorSlots;
import dev.erick.cryptokipu.app.puertos.Origen;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.KeyWrap;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.crypto.ProveedorNonce;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.format.Header;
import dev.erick.cryptokipu.core.format.HeaderCodec;
import dev.erick.cryptokipu.core.format.LimitesFormato;
import dev.erick.cryptokipu.core.format.MetadatosPrincipal;
import dev.erick.cryptokipu.core.format.Slot;
import dev.erick.cryptokipu.core.format.TipoSlot;
import dev.erick.cryptokipu.core.format.ZonaA;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32C;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ContenedorKipuTest {

    private static final FuenteAleatoria FUENTE_MIJA = FuenteAleatoria.delSistema();
    private static final InfoDestino NTFS_AMPLIO_MIJA = new InfoDestino("NTFS", Long.MAX_VALUE);

    @TempDir
    Path carpetaMija;

    private Path bovedaMija;
    private SecureBytes kekMija;
    private SecureBytes maestraMija;

    /** Simula un corte de energía: no es IOException, así que no se ejecuta ninguna limpieza. */
    static final class CorteSimulado extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @BeforeEach
    void preparar() {
        bovedaMija = carpetaMija.resolve("prueba.kipu");
        kekMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32));
        maestraMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32));
    }

    @AfterEach
    void limpiar() {
        kekMija.close();
        maestraMija.close();
    }

    // ------------------------------------------------------------------ ciclo básico

    @Test
    void crearAgregarCerrarAbrirYLeer() throws IOException {
        Path vacioMija = archivo("vacío.txt", 0);
        Path grandeMija = archivo("video.mp4", 3 * (1 << 20) + 5);
        Path textoMija = archivo("año.txt", 1000);
        List<EntradaManifest> entradasMija;
        try (ContenedorKipu contenedorMija = crear()) {
            entradasMija = contenedorMija.agregar(List.of(new Origen(vacioMija, "docs/vacío.txt"),
                    new Origen(grandeMija, "videos/video.mp4"), new Origen(textoMija, "fotos/año 2026/ñandú.txt")));
        }

        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            assertEquals(3, contenedorMija.manifest().entradasMija().size());
            assertEquals("fotos/año 2026/ñandú.txt", contenedorMija.manifest().entradasMija().get(2).rutaMija());
            assertArrayEquals(Files.readAllBytes(vacioMija), extraer(contenedorMija, entradasMija.get(0)));
            assertArrayEquals(Files.readAllBytes(grandeMija), extraer(contenedorMija, entradasMija.get(1)));
            assertArrayEquals(Files.readAllBytes(textoMija), extraer(contenedorMija, entradasMija.get(2)));
            assertEquals(3 * (1 << 20) + 5, contenedorMija.manifest().entradasMija().get(1).tamanoMija());
        }
    }

    @Test
    void claveMaestraEquivocadaEsIntegridadFallidaDelHeader() throws IOException {
        crear().close();
        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, opciones(Interruptor.NINGUNO_MIJA));
                SecureBytes otraMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32))) {
            esperarIntegridad(() -> contenedorMija.desbloquear(otraMija), ErrorBoveda.IntegridadFallida.Zona.HEADER);
        }
    }

    // ------------------------------------------------------------------ manipulación (2.6)

    @Test
    void unBitEnUnBlobEsIntegridadFallidaDelBloque() throws IOException {
        EntradaManifest entradaMija;
        try (ContenedorKipu contenedorMija = crear()) {
            entradaMija = contenedorMija.agregar(List.of(new Origen(archivo("a.bin", 5000), "a.bin"))).getFirst();
        }
        voltearBit(entradaMija.offsetMija() + 100);

        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            esperarIntegridad(() -> extraer(contenedorMija, entradaMija), ErrorBoveda.IntegridadFallida.Zona.BLOQUE);
        }
    }

    @Test
    void unBitEnElManifestEsIntegridadFallidaDelManifest() throws IOException {
        long finDatosMija;
        try (ContenedorKipu contenedorMija = crear()) {
            EntradaManifest entradaMija =
                    contenedorMija.agregar(List.of(new Origen(archivo("a.bin", 5000), "a.bin"))).getFirst();
            finDatosMija = entradaMija.offsetMija() + entradaMija.longitudCifradaMija();
        }
        voltearBit(finDatosMija + 20);

        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, opciones(Interruptor.NINGUNO_MIJA))) {
            esperarIntegridad(() -> contenedorMija.desbloquear(maestraMija), ErrorBoveda.IntegridadFallida.Zona.MANIFEST);
        }
    }

    @Test
    void unBitEnAmbasCopiasDelHeaderEsIntegridadFallida() throws IOException {
        crear().close();
        voltearBit(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA + 10);
        voltearBit(LimitesFormato.OFFSET_HEADER_RESPALDO_MIJA + 10);

        esperarIntegridad(() -> ContenedorKipu.abrir(bovedaMija, opciones(Interruptor.NINGUNO_MIJA)).close(),
                ErrorBoveda.IntegridadFallida.Zona.HEADER);
    }

    @Test
    void manipulacionDeLaZonaBConCrcRecalculadoLaDetectaElMac() throws IOException {
        crear().close();
        for (long baseMija : new long[] {LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA,
                LimitesFormato.OFFSET_HEADER_RESPALDO_MIJA}) {
            byte[] bloqueMija = leerBloque(baseMija);
            bloqueMija[ZonaA.TAMANO_MIJA + 11] ^= 0x01; // umbral de autodestrucción
            recalcularCrc(bloqueMija);
            escribirBloque(baseMija, bloqueMija);
        }

        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, opciones(Interruptor.NINGUNO_MIJA))) {
            esperarIntegridad(() -> contenedorMija.desbloquear(maestraMija), ErrorBoveda.IntegridadFallida.Zona.HEADER);
        }
    }

    @Test
    void unBitEnLaZonaCNoImpideAbrir() throws IOException {
        crear().close();
        voltearBit(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA + HeaderCodec.OFFSET_ZONA_C_MIJA + 500);

        abrirYDesbloquear().close();
    }

    @Test
    void headerPrimarioDestruidoAbreConElRespaldoYLoRepara() throws IOException {
        crear().close();
        escribirBloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA, new byte[LimitesFormato.TAMANO_HEADER_MIJA]);

        abrirYDesbloquear().close();

        byte[] primarioMija = leerBloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA);
        byte[] respaldoMija = leerBloque(LimitesFormato.OFFSET_HEADER_RESPALDO_MIJA);
        assertArrayEquals(java.util.Arrays.copyOf(respaldoMija, HeaderCodec.OFFSET_ZONA_C_MIJA),
                java.util.Arrays.copyOf(primarioMija, HeaderCodec.OFFSET_ZONA_C_MIJA));
    }

    @Test
    void headerDeRespaldoDestruidoSeReparaDesdeElPrimario() throws IOException {
        crear().close();
        escribirBloque(LimitesFormato.OFFSET_HEADER_RESPALDO_MIJA, FUENTE_MIJA.bytes(LimitesFormato.TAMANO_HEADER_MIJA));

        abrirYDesbloquear().close();

        HeaderCodec.decodificar(leerBloque(LimitesFormato.OFFSET_HEADER_RESPALDO_MIJA));
    }

    /**
     * Primario falso con generación + 1 y CRC válido. Variante A: Zona B alterada (el slot abre pero el
     * MAC falla). Variante B: slot 0 reemplazado por basura (el slot no abre). En ambos casos se abre
     * con el respaldo, y el respaldo no se toca en ningún momento antes de un desbloqueo correcto.
     */
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void primarioFalsoMasNuevoNoDestruyeElRespaldo(boolean slotBasuraMija) throws IOException {
        crear().close();
        byte[] respaldoOriginalMija = leerBloque(LimitesFormato.OFFSET_HEADER_RESPALDO_MIJA);
        byte[] falsoMija = leerBloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA);
        ByteBuffer bufferMija = ByteBuffer.wrap(falsoMija);
        bufferMija.putLong(ZonaA.TAMANO_MIJA, bufferMija.getLong(ZonaA.TAMANO_MIJA) + 1); // generación + 1
        if (slotBasuraMija) {
            byte[] basuraMija = FUENTE_MIJA.bytes(Slot.TAMANO_MIJA - 1);
            // Zona B: generación(8) umbral(4) llave(1) nSlots(1) → el slot 0 empieza en +14; se salta su tipo.
            bufferMija.put(ZonaA.TAMANO_MIJA + 14 + 1, basuraMija);
        } else {
            bufferMija.putInt(ZonaA.TAMANO_MIJA + 8, 3); // umbral de autodestrucción
        }
        recalcularCrc(falsoMija);
        escribirBloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA, falsoMija);

        // 1) Abrir y cerrar sin desbloquear: nada cambia.
        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, opciones(Interruptor.NINGUNO_MIJA))) {
            assertEquals(2, contenedorMija.header().generacionMija(), "se intenta primero la copia más nueva");
        }
        assertArrayEquals(respaldoOriginalMija, leerBloque(LimitesFormato.OFFSET_HEADER_RESPALDO_MIJA));
        assertArrayEquals(falsoMija, leerBloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA));

        // 2) Desbloqueo fallido (KEK equivocada): nada cambia.
        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, opciones(Interruptor.NINGUNO_MIJA));
                SecureBytes otraKekMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32))) {
            ExcepcionBoveda excepcionMija = assertThrows(ExcepcionBoveda.class,
                    () -> contenedorMija.desbloquear(abridor(otraKekMija)));
            assertInstanceOf(ErrorBoveda.CredencialesIncorrectas.class, excepcionMija.error());
        }
        assertArrayEquals(respaldoOriginalMija, leerBloque(LimitesFormato.OFFSET_HEADER_RESPALDO_MIJA));
        assertArrayEquals(falsoMija, leerBloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA));

        // 3) Desbloqueo correcto: gana el respaldo (MAC válido) y solo entonces se repara el primario.
        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, opciones(Interruptor.NINGUNO_MIJA))) {
            contenedorMija.desbloquear(abridor(kekMija));
            assertEquals(1, contenedorMija.header().generacionMija());
        }
        assertArrayEquals(respaldoOriginalMija, leerBloque(LimitesFormato.OFFSET_HEADER_RESPALDO_MIJA));
        assertArrayEquals(java.util.Arrays.copyOf(respaldoOriginalMija, HeaderCodec.OFFSET_ZONA_C_MIJA),
                java.util.Arrays.copyOf(leerBloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA),
                        HeaderCodec.OFFSET_ZONA_C_MIJA));
    }

    @Test
    void ningunSlotAbreEsCredencialesIncorrectas() throws IOException {
        crear().close();
        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, opciones(Interruptor.NINGUNO_MIJA));
                SecureBytes otraKekMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32))) {
            ExcepcionBoveda excepcionMija = assertThrows(ExcepcionBoveda.class,
                    () -> contenedorMija.desbloquear(abridor(otraKekMija)));
            assertInstanceOf(ErrorBoveda.CredencialesIncorrectas.class, excepcionMija.error());
            assertThrows(IllegalStateException.class, contenedorMija::manifest);
        }
    }

    @Test
    void pistaQueApuntaAUnFooterViejoCargaElEstadoMasNuevo() throws IOException {
        long footerViejoMija;
        try (ContenedorKipu contenedorMija = crear()) {
            contenedorMija.agregar(List.of(new Origen(archivo("uno.bin", 4000), "uno.bin")));
            footerViejoMija = contenedorMija.tamanoEnDisco() - 33;
            contenedorMija.agregar(List.of(new Origen(archivo("dos.bin", 4000), "dos.bin")));
            contenedorMija.agregar(List.of(new Origen(archivo("tres.bin", 4000), "tres.bin")));
        }
        Files.write(bovedaMija, FUENTE_MIJA.bytes(777), StandardOpenOption.APPEND); // invalida el footer final
        byte[] primarioMija = leerBloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA);
        ByteBuffer.wrap(primarioMija).putLong(HeaderCodec.OFFSET_ZONA_C_MIJA + 4, footerViejoMija);
        escribirBloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA, primarioMija);

        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            assertEquals(3, contenedorMija.manifest().entradasMija().size());
            assertEquals(4, contenedorMija.manifest().generacionMija());
        }
    }

    @Test
    void lecturasConcurrentesPorElMismoCanal() throws Exception {
        Path unoMija = archivo("uno.bin", 3 * (1 << 20) + 11);
        Path dosMija = archivo("dos.bin", 2 * (1 << 20) + 7);
        try (ContenedorKipu contenedorMija = crear()) {
            List<EntradaManifest> entradasMija = contenedorMija.agregar(List.of(new Origen(unoMija, "uno.bin"),
                    new Origen(dosMija, "dos.bin")));
            try (var ejecutorMija = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                List<java.util.concurrent.Future<byte[]>> futurosMija = new ArrayList<>();
                for (int vueltaMija = 0; vueltaMija < 4; vueltaMija++) {
                    for (EntradaManifest entradaMija : entradasMija) {
                        futurosMija.add(ejecutorMija.submit(() -> extraer(contenedorMija, entradaMija)));
                    }
                }
                for (int indiceMija = 0; indiceMija < futurosMija.size(); indiceMija++) {
                    Path esperadoMija = indiceMija % 2 == 0 ? unoMija : dosMija;
                    assertArrayEquals(Files.readAllBytes(esperadoMija), futurosMija.get(indiceMija).get());
                }
            }
        }
    }

    @Test
    void fsyncDelDirectorioSoloDondeElSistemaLoPermite() {
        boolean esWindowsMija = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
        assertEquals(!esWindowsMija, ContenedorKipu.sincronizarDirectorio(carpetaMija));
    }

    @Test
    void detectaFatEnLosTresSistemasOperativos() {
        for (String tipoMija : List.of("FAT32", "FAT", "vfat", "msdos")) {
            assertTrue(new InfoDestino(tipoMija, 0).esFat(), tipoMija);
        }
        for (String tipoMija : List.of("NTFS", "exFAT", "ext4", "apfs", "hfs", "btrfs")) {
            assertFalse(new InfoDestino(tipoMija, 0).esFat(), tipoMija);
        }
    }

    // ------------------------------------------------------------------ cortes y fallos de escritura

    @ParameterizedTest
    @EnumSource(value = Interruptor.Paso.class, names = {"BLOBS_ESCRITOS", "MANIFEST_ESCRITO", "FOOTER_ESCRITO"})
    void corteDuranteAgregarDejaUnEstadoValido(Interruptor.Paso pasoMija) throws IOException {
        Path primeroMija = archivo("uno.bin", 2 * (1 << 20) + 3);
        try (ContenedorKipu contenedorMija = crear()) {
            contenedorMija.agregar(List.of(new Origen(primeroMija, "uno.bin")));
        }
        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, opciones(cortarEn(pasoMija)))) {
            contenedorMija.desbloquear(maestraMija);
            assertThrows(CorteSimulado.class,
                    () -> contenedorMija.agregar(List.of(new Origen(archivo("dos.bin", 70_000), "dos.bin"))));
        }

        int entradasMija;
        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            entradasMija = contenedorMija.manifest().entradasMija().size();
            // Antes del footer → estado anterior; con el footer escrito → estado nuevo. Nunca corrupto.
            assertEquals(pasoMija == Interruptor.Paso.FOOTER_ESCRITO ? 2 : 1, entradasMija);
            assertArrayEquals(Files.readAllBytes(primeroMija),
                    extraer(contenedorMija, contenedorMija.manifest().entradasMija().getFirst()));
            contenedorMija.agregar(List.of(new Origen(archivo("tres.bin", 10), "tres.bin")));
        }
        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            assertEquals(entradasMija + 1, contenedorMija.manifest().entradasMija().size());
        }
    }

    @Test
    void corteTrasEscribirElRespaldoDelHeaderUsaElRespaldoNuevo() throws IOException {
        crear().close();
        Slot nuevoMija;
        try (ContenedorKipu contenedorMija =
                ContenedorKipu.abrir(bovedaMija, opciones(cortarEn(Interruptor.Paso.RESPALDO_ESCRITO)))) {
            contenedorMija.desbloquear(maestraMija);
            List<Slot> slotsMija = new ArrayList<>(contenedorMija.header().slotsMija());
            nuevoMija = Slot.vacio(FUENTE_MIJA);
            slotsMija.set(3, nuevoMija);
            assertThrows(CorteSimulado.class, () -> contenedorMija.reescribirHeader(slotsMija, 0));
        }

        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            assertEquals(2, contenedorMija.header().generacionMija());
            assertEquals(nuevoMija, contenedorMija.header().slotsMija().get(3));
        }
        assertEquals(2, HeaderCodec.decodificar(leerBloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA)).generacionMija());
    }

    @Test
    void discoLlenoAMitadDeEscribirNoCorrompeLaBoveda() throws IOException {
        try (ContenedorKipu contenedorMija = crear()) {
            contenedorMija.agregar(List.of(new Origen(archivo("uno.bin", 1000), "uno.bin")));
        }
        long tamanoAntesMija = Files.size(bovedaMija);
        Interruptor llenoMija = pasoMija -> {
            if (pasoMija == Interruptor.Paso.BLOBS_ESCRITOS) {
                throw new IOException("No queda espacio en el dispositivo");
            }
        };
        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, opciones(llenoMija))) {
            contenedorMija.desbloquear(maestraMija);
            assertThrows(IOException.class,
                    () -> contenedorMija.agregar(List.of(new Origen(archivo("dos.bin", 300_000), "dos.bin"))));
            assertEquals(1, contenedorMija.manifest().entradasMija().size());
        }

        assertEquals(tamanoAntesMija, Files.size(bovedaMija));
        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            assertEquals(1, contenedorMija.manifest().entradasMija().size());
        }
    }

    @Test
    void espacioInsuficienteSeDetectaAntesDeEscribir() throws IOException {
        crear().close();
        long tamanoAntesMija = Files.size(bovedaMija);
        ContenedorKipu.Opciones pocoEspacioMija = new ContenedorKipu.Opciones(FUENTE_MIJA,
                _ -> new InfoDestino("NTFS", 10_000), Interruptor.NINGUNO_MIJA);

        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, pocoEspacioMija)) {
            contenedorMija.desbloquear(maestraMija);
            ExcepcionBoveda excepcionMija = assertThrows(ExcepcionBoveda.class,
                    () -> contenedorMija.agregar(List.of(new Origen(archivo("grande.bin", 50_000), "g.bin"))));
            assertInstanceOf(ErrorBoveda.EspacioInsuficiente.class, excepcionMija.error());
        }
        assertEquals(tamanoAntesMija, Files.size(bovedaMija));
    }

    @Test
    void fat32BloqueaUnaBovedaDeMasDe4GiBAntesDeEscribir() throws IOException {
        crear().close();
        long tamanoAntesMija = Files.size(bovedaMija);
        Path dispersoMija = carpetaMija.resolve("disperso.bin");
        try (SeekableByteChannel canalMija = Files.newByteChannel(dispersoMija, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, StandardOpenOption.SPARSE)) {
            canalMija.position(LimitesFormato.LIMITE_FAT32_MIJA + 10);
            canalMija.write(ByteBuffer.wrap(new byte[] {1}));
        }
        ContenedorKipu.Opciones fatMija = new ContenedorKipu.Opciones(FUENTE_MIJA,
                _ -> new InfoDestino("FAT32", Long.MAX_VALUE), Interruptor.NINGUNO_MIJA);

        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, fatMija)) {
            contenedorMija.desbloquear(maestraMija);
            ExcepcionBoveda excepcionMija = assertThrows(ExcepcionBoveda.class,
                    () -> contenedorMija.agregar(List.of(new Origen(dispersoMija, "enorme.bin"))));
            assertInstanceOf(ErrorBoveda.LimiteSistemaArchivos.class, excepcionMija.error());
        } finally {
            Files.deleteIfExists(dispersoMija);
        }
        assertEquals(tamanoAntesMija, Files.size(bovedaMija));
        abrirYDesbloquear().close();
    }

    @Test
    void basuraAlFinalYPistaDanadaSeRecuperaConLaBusquedaHaciaAtras() throws IOException {
        try (ContenedorKipu contenedorMija = crear()) {
            contenedorMija.agregar(List.of(new Origen(archivo("uno.bin", 3 * (1 << 20)), "uno.bin")));
        }
        Files.write(bovedaMija, FUENTE_MIJA.bytes(2 * (1 << 20) + 77), StandardOpenOption.APPEND);
        byte[] primarioMija = leerBloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA);
        ByteBuffer.wrap(primarioMija).putLong(HeaderCodec.OFFSET_ZONA_C_MIJA + 4, 0);
        escribirBloque(LimitesFormato.OFFSET_HEADER_PRIMARIO_MIJA, primarioMija);

        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            assertEquals(1, contenedorMija.manifest().entradasMija().size());
            contenedorMija.agregar(List.of(new Origen(archivo("dos.bin", 10), "dos.bin")));
        }
        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            assertEquals(2, contenedorMija.manifest().entradasMija().size());
        }
    }

    // ------------------------------------------------------------------ bloqueo, rotación, compactación

    @Test
    void dosAperturasSimultaneasFallanConBovedaEnUso() throws IOException {
        crear().close();
        try (ContenedorKipu primeraMija = ContenedorKipu.abrir(bovedaMija, opciones(Interruptor.NINGUNO_MIJA))) {
            ExcepcionBoveda excepcionMija = assertThrows(ExcepcionBoveda.class,
                    () -> ContenedorKipu.abrir(bovedaMija, opciones(Interruptor.NINGUNO_MIJA)));
            assertInstanceOf(ErrorBoveda.BovedaEnUso.class, excepcionMija.error());
            assertFalse(primeraMija.esSoloLectura());
        }
    }

    @Test
    void rotarElSlotNoDejaRastroDelSlotViejoEnElArchivo() throws IOException {
        byte[] cifradoViejoMija;
        try (ContenedorKipu contenedorMija = crear()) {
            cifradoViejoMija = contenedorMija.header().slotsMija().getFirst().envueltaMija().cifradoMija();
            try (SecureBytes nuevaKekMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32))) {
                List<Slot> slotsMija = new ArrayList<>(contenedorMija.header().slotsMija());
                ZonaA zonaMija = contenedorMija.header().zonaAMija();
                slotsMija.set(0, Slot.envolver(zonaMija, 0, TipoSlot.PRINCIPAL, new MetadatosPrincipal(false, 0), FUENTE_MIJA.bytes(32), nuevaKekMija,
                        maestraMija, new KeyWrap(new ProveedorNonce(FUENTE_MIJA))));
                contenedorMija.reescribirHeader(slotsMija, 0);
            }
        }

        assertFalse(contiene(Files.readAllBytes(bovedaMija), cifradoViejoMija));
        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            assertEquals(2, contenedorMija.header().generacionMija());
        }
    }

    @Test
    void eliminarYCompactarQuitaLosBloquesDelArchivo() throws IOException {
        // En Windows el bloqueo impide leer el archivo con otro descriptor: se inspecciona con la bóveda cerrada.
        Path conservadoMija = archivo("conservar.bin", 2 * (1 << 20));
        EntradaManifest borrarMija;
        try (ContenedorKipu contenedorMija = crear()) {
            borrarMija = contenedorMija.agregar(List.of(
                    new Origen(archivo("borrar.bin", 3 * (1 << 20)), "borrar.bin"),
                    new Origen(conservadoMija, "conservar.bin"))).getFirst();
        }
        byte[] muestraEliminadaMija = leer(borrarMija.offsetMija() + 4096, 64);
        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            contenedorMija.eliminar(borrarMija.fileIdMija());
        }
        assertTrue(contiene(Files.readAllBytes(bovedaMija), muestraEliminadaMija), "sigue dentro hasta compactar");
        long tamanoAntesMija = Files.size(bovedaMija);
        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            contenedorMija.compactar();
            assertEquals(1, contenedorMija.manifest().entradasMija().size());
        }

        assertTrue(Files.size(bovedaMija) < tamanoAntesMija - 3 * (1 << 20));
        assertFalse(contiene(Files.readAllBytes(bovedaMija), muestraEliminadaMija));
        assertFalse(Files.exists(carpetaMija.resolve("prueba.kipu.tmp")));
        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            assertArrayEquals(Files.readAllBytes(conservadoMija),
                    extraer(contenedorMija, contenedorMija.manifest().entradasMija().getFirst()));
        }
    }

    @Test
    void corteAntesDeReemplazarEnLaCompactacionConservaElOriginal() throws IOException {
        Path unoMija = archivo("uno.bin", 5000);
        try (ContenedorKipu contenedorMija = crear()) {
            EntradaManifest entradaMija = contenedorMija.agregar(List.of(new Origen(unoMija, "uno.bin"),
                    new Origen(archivo("dos.bin", 5000), "dos.bin"))).get(1);
            contenedorMija.eliminar(entradaMija.fileIdMija());
        }
        try (ContenedorKipu contenedorMija =
                ContenedorKipu.abrir(bovedaMija, opciones(cortarEn(Interruptor.Paso.COMPACTACION_LISTA)))) {
            contenedorMija.desbloquear(maestraMija);
            assertThrows(CorteSimulado.class, contenedorMija::compactar);
        }

        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            assertArrayEquals(Files.readAllBytes(unoMija),
                    extraer(contenedorMija, contenedorMija.manifest().entradasMija().getFirst()));
            contenedorMija.compactar();
        }
    }

    @Test
    void intentosFallidosSeGuardanEnLaZonaCSinRomperElMac() throws IOException {
        crear().close();
        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, opciones(Interruptor.NINGUNO_MIJA))) {
            assertEquals(1, contenedorMija.registrarIntentoFallido());
            assertEquals(2, contenedorMija.registrarIntentoFallido());
        }
        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            assertEquals(2, contenedorMija.header().intentosFallidosMija());
            contenedorMija.reiniciarIntentos();
        }
        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            assertEquals(0, contenedorMija.header().intentosFallidosMija());
        }
    }

    @Test
    void archivoDeSoloLecturaSeAbreSinPermitirEscrituras() throws IOException {
        try (ContenedorKipu contenedorMija = crear()) {
            contenedorMija.agregar(List.of(new Origen(archivo("uno.bin", 100), "uno.bin")));
        }
        assertTrue(bovedaMija.toFile().setReadOnly());
        try (ContenedorKipu contenedorMija = abrirYDesbloquear()) {
            assertTrue(contenedorMija.esSoloLectura());
            assertEquals(1, contenedorMija.manifest().entradasMija().size());
            assertThrows(IllegalStateException.class,
                    () -> contenedorMija.agregar(List.of(new Origen(archivo("dos.bin", 1), "dos.bin"))));
        } finally {
            assertTrue(bovedaMija.toFile().setWritable(true));
        }
    }

    // ------------------------------------------------------------------ utilidades

    private ContenedorKipu.Opciones opciones(Interruptor interruptorMija) {
        return new ContenedorKipu.Opciones(FUENTE_MIJA, _ -> NTFS_AMPLIO_MIJA, interruptorMija);
    }

    private static Interruptor cortarEn(Interruptor.Paso objetivoMija) {
        return pasoMija -> {
            if (pasoMija == objetivoMija) {
                throw new CorteSimulado();
            }
        };
    }

    /** Abre el slot 0 de cada candidato con la KEK de prueba, como hará la Fase 3. */
    private AbridorSlots abridor(SecureBytes kekUsadaMija) {
        return headerMija -> headerMija.slotsMija().getFirst().abrir(headerMija.zonaAMija(), 0, headerMija.metadatosPrincipal(), kekUsadaMija);
    }

    private ContenedorKipu crear() throws IOException {
        ZonaA zonaMija = ZonaA.nueva(ParametrosArgon2.ESTANDAR_MIJA, FUENTE_MIJA);
        List<Slot> slotsMija = new ArrayList<>();
        slotsMija.add(Slot.envolver(zonaMija, 0, TipoSlot.PRINCIPAL, new MetadatosPrincipal(false, 0), FUENTE_MIJA.bytes(32), kekMija, maestraMija,
                new KeyWrap(new ProveedorNonce(FUENTE_MIJA))));
        for (int indiceMija = 1; indiceMija < 7; indiceMija++) {
            slotsMija.add(Slot.vacio(FUENTE_MIJA));
        }
        return ContenedorKipu.crear(bovedaMija, Header.nuevo(zonaMija, slotsMija, 0), maestraMija,
                opciones(Interruptor.NINGUNO_MIJA));
    }

    private ContenedorKipu abrirYDesbloquear() throws IOException {
        ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, opciones(Interruptor.NINGUNO_MIJA));
        try {
            contenedorMija.desbloquear(maestraMija);
            return contenedorMija;
        } catch (IOException | RuntimeException errorMija) {
            contenedorMija.close();
            throw errorMija;
        }
    }

    private Path archivo(String nombreMija, int tamanoMija) throws IOException {
        byte[] datosMija = new byte[tamanoMija];
        new Random(nombreMija.hashCode() * 31L + tamanoMija).nextBytes(datosMija);
        Path rutaMija = carpetaMija.resolve("origen-" + nombreMija);
        Files.write(rutaMija, datosMija);
        return rutaMija;
    }

    private static byte[] extraer(ContenedorKipu contenedorMija, EntradaManifest entradaMija) throws IOException {
        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream();
        contenedorMija.extraer(entradaMija.fileIdMija(), salidaMija);
        return salidaMija.toByteArray();
    }

    private void voltearBit(long posicionMija) throws IOException {
        try (RandomAccessFile archivoMija = new RandomAccessFile(bovedaMija.toFile(), "rw")) {
            archivoMija.seek(posicionMija);
            int valorMija = archivoMija.read();
            archivoMija.seek(posicionMija);
            archivoMija.write(valorMija ^ 0x01);
        }
    }

    private byte[] leer(long posicionMija, int longitudMija) throws IOException {
        try (RandomAccessFile archivoMija = new RandomAccessFile(bovedaMija.toFile(), "r")) {
            byte[] datosMija = new byte[longitudMija];
            archivoMija.seek(posicionMija);
            archivoMija.readFully(datosMija);
            return datosMija;
        }
    }

    private byte[] leerBloque(long posicionMija) throws IOException {
        return leer(posicionMija, LimitesFormato.TAMANO_HEADER_MIJA);
    }

    private void escribirBloque(long posicionMija, byte[] bloqueMija) throws IOException {
        try (RandomAccessFile archivoMija = new RandomAccessFile(bovedaMija.toFile(), "rw")) {
            archivoMija.seek(posicionMija);
            archivoMija.write(bloqueMija);
        }
    }

    private static void recalcularCrc(byte[] bloqueMija) {
        CRC32C crcMija = new CRC32C();
        crcMija.update(bloqueMija, 0, HeaderCodec.OFFSET_CRC_MIJA);
        ByteBuffer.wrap(bloqueMija).putInt(HeaderCodec.OFFSET_CRC_MIJA, (int) crcMija.getValue());
    }

    private static boolean contiene(byte[] dondeMija, byte[] buscadoMija) {
        outer:
        for (int indiceMija = 0; indiceMija <= dondeMija.length - buscadoMija.length; indiceMija++) {
            for (int jMija = 0; jMija < buscadoMija.length; jMija++) {
                if (dondeMija[indiceMija + jMija] != buscadoMija[jMija]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static void esperarIntegridad(Executable accionMija, ErrorBoveda.IntegridadFallida.Zona zonaMija) {
        ExcepcionBoveda excepcionMija = assertThrows(ExcepcionBoveda.class, accionMija);
        ErrorBoveda.IntegridadFallida errorMija =
                assertInstanceOf(ErrorBoveda.IntegridadFallida.class, excepcionMija.error());
        assertEquals(zonaMija, errorMija.zonaMija());
    }
}
