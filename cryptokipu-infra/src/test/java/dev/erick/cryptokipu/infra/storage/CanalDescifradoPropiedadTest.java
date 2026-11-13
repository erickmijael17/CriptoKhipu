package dev.erick.cryptokipu.infra.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import dev.erick.cryptokipu.app.puertos.Origen;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.KeyWrap;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.crypto.ProveedorNonce;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.format.Header;
import dev.erick.cryptokipu.core.format.MetadatosPrincipal;
import dev.erick.cryptokipu.core.format.Slot;
import dev.erick.cryptokipu.core.format.TipoSlot;
import dev.erick.cryptokipu.core.format.ZonaA;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;

/** Lecturas en posiciones y longitudes aleatorias del canal descifrado == mismos bytes del original (jqwik). */
class CanalDescifradoPropiedadTest {

    private static final int TAMANO_MIJA = 3 * (1 << 20) + 4321;
    private static final FuenteAleatoria FUENTE_MIJA = FuenteAleatoria.delSistema();
    private static Path carpetaMija;
    private static byte[] originalMija;
    private static ContenedorKipu contenedorMija;
    private static SeekableByteChannel canalMija;

    @BeforeContainer
    static void preparar() throws IOException {
        carpetaMija = Files.createTempDirectory("canal-propiedad");
        originalMija = FUENTE_MIJA.bytes(TAMANO_MIJA);
        Path origenMija = carpetaMija.resolve("datos.bin");
        Files.write(origenMija, originalMija);
        try (SecureBytes kekMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32));
                SecureBytes maestraMija = SecureBytes.envolver(FUENTE_MIJA.bytes(32))) {
            ZonaA zonaMija = ZonaA.nueva(new ParametrosArgon2(64 * 1024, 2, 1), FUENTE_MIJA);
            List<Slot> slotsMija = new ArrayList<>();
            slotsMija.add(Slot.envolver(zonaMija, 0, TipoSlot.PRINCIPAL, new MetadatosPrincipal(false, 0),
                    FUENTE_MIJA.bytes(32), kekMija, maestraMija, new KeyWrap(new ProveedorNonce(FUENTE_MIJA))));
            contenedorMija = ContenedorKipu.crear(carpetaMija.resolve("p.kipu"), Header.nuevo(zonaMija, slotsMija, 0),
                    maestraMija, ContenedorKipu.Opciones.porDefecto());
        }
        EntradaManifest entradaMija = contenedorMija.agregar(List.of(new Origen(origenMija, "datos.bin"))).getFirst();
        canalMija = contenedorMija.abrirLectura(entradaMija.fileIdMija());
    }

    @AfterContainer
    static void limpiar() throws IOException {
        contenedorMija.close();
        try (var archivosMija = Files.walk(carpetaMija)) {
            for (Path rutaMija : archivosMija.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(rutaMija);
            }
        }
    }

    @Property(tries = 300)
    void cualquierRangoDevuelveLosBytesOriginales(@ForAll @IntRange(min = 0, max = TAMANO_MIJA - 1) int posicionMija,
            @ForAll @IntRange(min = 1, max = 2 * (1 << 20) + 7) int largoPedidoMija) throws IOException {
        int largoMija = Math.min(largoPedidoMija, TAMANO_MIJA - posicionMija);
        canalMija.position(posicionMija);
        ByteBuffer bufferMija = ByteBuffer.allocate(largoMija);
        while (bufferMija.hasRemaining() && canalMija.read(bufferMija) > 0) {
            // leer hasta llenar
        }
        assertArrayEquals(Arrays.copyOfRange(originalMija, posicionMija, posicionMija + largoMija), bufferMija.array());
    }
}
