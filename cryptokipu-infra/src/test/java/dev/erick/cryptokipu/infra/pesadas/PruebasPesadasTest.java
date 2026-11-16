package dev.erick.cryptokipu.infra.pesadas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import dev.erick.cryptokipu.core.format.MetadatosPrincipal;
import dev.erick.cryptokipu.core.format.Slot;
import dev.erick.cryptokipu.core.format.TipoSlot;
import dev.erick.cryptokipu.core.format.ZonaA;
import dev.erick.cryptokipu.infra.storage.ContenedorKipu;
import dev.erick.cryptokipu.infra.storage.InfoDestino;
import dev.erick.cryptokipu.infra.storage.Interruptor;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pruebas pesadas (Fase 9), fuera de {@code check}: {@code ./gradlew :cryptokipu-infra:pruebasPesadas}.
 * Necesitan hasta ~15 GB libres en el disco temporal y varios minutos.
 *
 * <ul>
 *   <li>un archivo de 5 GB (agregar, leer completo con SHA-256);</li>
 *   <li>100 000 archivos pequeños (agregar y reabrir);</li>
 *   <li>compactar una bóveda de 5 GB;</li>
 *   <li>acercarse al límite de 4 GiB de un destino FAT32 simulado.</li>
 * </ul>
 */
@Tag("pesada")
@Timeout(value = 60, unit = java.util.concurrent.TimeUnit.MINUTES)
class PruebasPesadasTest {

    private static final long GIB_MIJA = 1L << 30;
    private static final FuenteAleatoria FUENTE_MIJA = FuenteAleatoria.delSistema();

    @TempDir
    Path carpetaMija;

    private SecureBytes kekMija;
    private SecureBytes maestraMija;
    private Path bovedaMija;

    @BeforeEach
    void preparar() {
        kekMija = SecureBytes.copiaDe(FUENTE_MIJA.bytes(32));
        maestraMija = SecureBytes.copiaDe(FUENTE_MIJA.bytes(32));
        bovedaMija = carpetaMija.resolve("pesada.kipu");
    }

    private ContenedorKipu crear(InfoDestino destinoMija) throws IOException {
        ZonaA zonaMija = ZonaA.nueva(new ParametrosArgon2(64 * 1024, 2, 1), FUENTE_MIJA);
        List<Slot> slotsMija = new ArrayList<>();
        slotsMija.add(Slot.envolver(zonaMija, 0, TipoSlot.PRINCIPAL, new MetadatosPrincipal(false, 0),
                FUENTE_MIJA.bytes(32), kekMija, maestraMija, new KeyWrap(new ProveedorNonce(FUENTE_MIJA))));
        for (int indiceMija = 1; indiceMija < 7; indiceMija++) {
            slotsMija.add(Slot.vacio(FUENTE_MIJA));
        }
        return ContenedorKipu.crear(bovedaMija, Header.nuevo(zonaMija, slotsMija, 0), maestraMija,
                new ContenedorKipu.Opciones(FUENTE_MIJA, _ -> destinoMija, Interruptor.NINGUNO_MIJA));
    }

    /** Escribe {@code tamanoMija} bytes pseudoaleatorios deterministas y devuelve su SHA-256. */
    private static String escribirPatron(Path destinoMija, long tamanoMija, long semillaMija) throws IOException {
        MessageDigest resumenMija = sha256();
        SplittableRandom aleatorioMija = new SplittableRandom(semillaMija);
        byte[] bloqueMija = new byte[1 << 20];
        ByteBuffer vistaMija = ByteBuffer.wrap(bloqueMija);
        try (OutputStream salidaMija = Files.newOutputStream(destinoMija)) {
            long restanteMija = tamanoMija;
            while (restanteMija > 0) {
                for (int indiceMija = 0; indiceMija + 8 <= bloqueMija.length; indiceMija += 8) {
                    vistaMija.putLong(indiceMija, aleatorioMija.nextLong());
                }
                int cuantosMija = (int) Math.min(bloqueMija.length, restanteMija);
                salidaMija.write(bloqueMija, 0, cuantosMija);
                resumenMija.update(bloqueMija, 0, cuantosMija);
                restanteMija -= cuantosMija;
            }
        }
        return HexFormat.of().formatHex(resumenMija.digest());
    }

    private static String sha256Canal(SeekableByteChannel canalMija) throws IOException {
        MessageDigest resumenMija = sha256();
        ByteBuffer bufferMija = ByteBuffer.allocate(1 << 20);
        while (canalMija.read(bufferMija) > 0) {
            resumenMija.update(bufferMija.array(), 0, bufferMija.position());
            bufferMija.clear();
        }
        return HexFormat.of().formatHex(resumenMija.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException errorMija) {
            throw new IllegalStateException(errorMija);
        }
    }

    private static void espacioLibre(Path carpetaMija, long bytesMija) throws IOException {
        long libreMija = Files.getFileStore(carpetaMija).getUsableSpace();
        assumeTrue(libreMija > bytesMija, "Hacen falta " + bytesMija / GIB_MIJA + " GiB libres (hay " + libreMija / GIB_MIJA + ")");
    }

    private static EntradaManifest entradaDe(ContenedorKipu contenedorMija, String rutaMija) {
        return contenedorMija.manifest().entradasMija().stream().filter(entradaMija -> entradaMija.rutaMija().equals(rutaMija))
                .findFirst().orElseThrow();
    }

    @Test
    void unArchivoDeCincoGigabytesSeAgregaYSeLeeCompletoConElMismoSha256() throws IOException {
        espacioLibre(carpetaMija, 11 * GIB_MIJA);
        Path origenMija = carpetaMija.resolve("cinco-gb.bin");
        String esperadoMija = escribirPatron(origenMija, 5 * GIB_MIJA, 5);
        try (ContenedorKipu contenedorMija = crear(new InfoDestino("NTFS", Long.MAX_VALUE))) {
            contenedorMija.agregar(List.of(new Origen(origenMija, "cinco-gb.bin")));
            Files.delete(origenMija);
            EntradaManifest entradaMija = entradaDe(contenedorMija, "cinco-gb.bin");
            assertEquals(5 * GIB_MIJA, entradaMija.tamanoMija());
            try (SeekableByteChannel canalMija = contenedorMija.abrirLectura(entradaMija.fileIdMija())) {
                assertEquals(esperadoMija, sha256Canal(canalMija));
            }
        }
        // Se reabre desde cero: el contenido sigue íntegro.
        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, ContenedorKipu.Opciones.porDefecto())) {
            contenedorMija.desbloquear(maestraMija);
            EntradaManifest entradaMija = entradaDe(contenedorMija, "cinco-gb.bin");
            try (SeekableByteChannel canalMija = contenedorMija.abrirLectura(entradaMija.fileIdMija())) {
                assertEquals(esperadoMija, sha256Canal(canalMija));
            }
        }
    }

    @Test
    void cienMilArchivosPequenosSeAgregaYSeReabre() throws IOException {
        Path origenMija = Files.createDirectories(carpetaMija.resolve("pequenos"));
        int totalMija = 100_000;
        for (int indiceMija = 0; indiceMija < totalMija; indiceMija++) {
            Path carpetaArchivoMija = origenMija.resolve("d" + indiceMija / 1000);
            Files.createDirectories(carpetaArchivoMija);
            Files.writeString(carpetaArchivoMija.resolve("f" + indiceMija + ".txt"), "archivo número " + indiceMija);
        }
        long inicioMija = System.nanoTime();
        try (ContenedorKipu contenedorMija = crear(new InfoDestino("NTFS", Long.MAX_VALUE))) {
            List<Origen> origenesMija = new ArrayList<>(totalMija);
            for (int indiceMija = 0; indiceMija < totalMija; indiceMija++) {
                origenesMija.add(new Origen(origenMija.resolve("d" + indiceMija / 1000).resolve("f" + indiceMija + ".txt"),
                        "d" + indiceMija / 1000 + "/f" + indiceMija + ".txt"));
            }
            contenedorMija.agregar(origenesMija);
            assertEquals(totalMija, contenedorMija.manifest().entradasMija().size());
        }
        long agregarMsMija = (System.nanoTime() - inicioMija) / 1_000_000;
        long reabrirInicioMija = System.nanoTime();
        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, ContenedorKipu.Opciones.porDefecto())) {
            contenedorMija.desbloquear(maestraMija);
            assertEquals(totalMija, contenedorMija.manifest().entradasMija().size());
            EntradaManifest entradaMija = entradaDe(contenedorMija, "d42/f42500.txt");
            try (SeekableByteChannel canalMija = contenedorMija.abrirLectura(entradaMija.fileIdMija())) {
                ByteBuffer bufferMija = ByteBuffer.allocate(100);
                int leidosMija = canalMija.read(bufferMija);
                assertEquals("archivo número 42500", new String(bufferMija.array(), 0, leidosMija));
            }
        }
        long reabrirMsMija = (System.nanoTime() - reabrirInicioMija) / 1_000_000;
        System.getLogger(PruebasPesadasTest.class.getName()).log(System.Logger.Level.INFO,
                "100000 archivos: agregar {0} ms, reabrir y leer {1} ms", agregarMsMija, reabrirMsMija);
        assertTrue(reabrirMsMija < 60_000, "reabrir una bóveda de 100 000 entradas tardó " + reabrirMsMija + " ms");
    }

    @Test
    void compactarUnaBovedaDeCincoGigabytesConservaLoQueQueda() throws IOException {
        espacioLibre(carpetaMija, 16 * GIB_MIJA);
        Path conservadoMija = carpetaMija.resolve("conservado.bin");
        Path descartadoMija = carpetaMija.resolve("descartado.bin");
        String esperadoMija = escribirPatron(conservadoMija, 2 * GIB_MIJA + 512 * (1L << 20), 21);
        escribirPatron(descartadoMija, 2 * GIB_MIJA + 512 * (1L << 20), 22);
        long antesMija;
        try (ContenedorKipu contenedorMija = crear(new InfoDestino("NTFS", Long.MAX_VALUE))) {
            contenedorMija.agregar(List.of(new Origen(conservadoMija, "conservado.bin"),
                    new Origen(descartadoMija, "descartado.bin")));
            Files.delete(conservadoMija);
            Files.delete(descartadoMija);
            contenedorMija.eliminar(entradaDe(contenedorMija, "descartado.bin").fileIdMija());
            antesMija = Files.size(bovedaMija);
            assertTrue(antesMija > 5 * GIB_MIJA, "la bóveda debe pasar de 5 GiB antes de compactar: " + antesMija);
            contenedorMija.compactar();
            assertTrue(Files.size(bovedaMija) < antesMija - 2 * GIB_MIJA, "compactar debe liberar el archivo eliminado");
            try (SeekableByteChannel canalMija = contenedorMija.abrirLectura(
                    entradaDe(contenedorMija, "conservado.bin").fileIdMija())) {
                assertEquals(esperadoMija, sha256Canal(canalMija));
            }
        }
    }

    @Test
    void cercaDelLimiteDeCuatroGibEnFat32SimuladoSeBloqueaSinDanarLaBoveda() throws IOException {
        espacioLibre(carpetaMija, 9 * GIB_MIJA);
        InfoDestino fat32Mija = new InfoDestino("FAT32", Long.MAX_VALUE);
        Path grandeMija = carpetaMija.resolve("casi-4gib.bin");
        String esperadoMija = escribirPatron(grandeMija, 4 * GIB_MIJA - 64 * (1L << 20), 33);
        Path extraMija = carpetaMija.resolve("extra.bin");
        escribirPatron(extraMija, 128L * (1 << 20), 34);
        try (ContenedorKipu contenedorMija = crear(fat32Mija)) {
            contenedorMija.agregar(List.of(new Origen(grandeMija, "casi-4gib.bin")));
            Files.delete(grandeMija);
            long tamanoAntesMija = Files.size(bovedaMija);
            assertTrue(tamanoAntesMija < 4 * GIB_MIJA, "sigue por debajo de 4 GiB: " + tamanoAntesMija);

            ExcepcionBoveda errorMija = assertThrows(ExcepcionBoveda.class,
                    () -> contenedorMija.agregar(List.of(new Origen(extraMija, "extra.bin"))));
            assertInstanceOf(ErrorBoveda.LimiteSistemaArchivos.class, errorMija.error());
            assertEquals(tamanoAntesMija, Files.size(bovedaMija), "el bloqueo ocurre ANTES de escribir");
        }
        try (ContenedorKipu contenedorMija = ContenedorKipu.abrir(bovedaMija, new ContenedorKipu.Opciones(FUENTE_MIJA,
                _ -> fat32Mija, Interruptor.NINGUNO_MIJA))) {
            contenedorMija.desbloquear(maestraMija);
            assertEquals(1, contenedorMija.manifest().entradasMija().size());
            try (SeekableByteChannel canalMija = contenedorMija.abrirLectura(
                    entradaDe(contenedorMija, "casi-4gib.bin").fileIdMija())) {
                assertEquals(esperadoMija, sha256Canal(canalMija));
            }
        }
    }
}
