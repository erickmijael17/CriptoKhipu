package dev.erick.cryptokipu.infra.storage;

import dev.erick.cryptokipu.app.puertos.Origen;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.KeyWrap;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.crypto.ProveedorNonce;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
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
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Latencia de una lectura aleatoria (seek + lectura de 4 KiB) en el canal de {@code AbrirEnMemoria}: cada acceso
 * posiciona el canal y descifra el bloque de 1 MiB que contiene esa posición (con la caché de un bloque).
 * {@code ./gradlew :cryptokipu-infra:jmh}
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class LecturaAleatoriaBenchmark {

    private static final int TAMANO_ARCHIVO_MIJA = 64 << 20;
    private Path carpetaMija;
    private ContenedorKipu contenedorMija;
    private SeekableByteChannel canalMija;
    private final SplittableRandom aleatorioMija = new SplittableRandom(7);
    private final ByteBuffer bufferMija = ByteBuffer.allocate(4096);

    @Setup
    public void preparar() throws IOException {
        FuenteAleatoria fuenteMija = FuenteAleatoria.delSistema();
        carpetaMija = Files.createTempDirectory("jmh-lectura");
        ZonaA zonaMija = ZonaA.nueva(new ParametrosArgon2(64 * 1024, 2, 1), fuenteMija);
        SecureBytes kekMija = SecureBytes.copiaDe(fuenteMija.bytes(32));
        SecureBytes maestraMija = SecureBytes.copiaDe(fuenteMija.bytes(32));
        List<Slot> slotsMija = new ArrayList<>();
        slotsMija.add(Slot.envolver(zonaMija, 0, TipoSlot.PRINCIPAL, new MetadatosPrincipal(false, 0),
                fuenteMija.bytes(32), kekMija, maestraMija, new KeyWrap(new ProveedorNonce(fuenteMija))));
        for (int indiceMija = 1; indiceMija < 7; indiceMija++) {
            slotsMija.add(Slot.vacio(fuenteMija));
        }
        ContenedorKipu.Opciones opcionesMija = new ContenedorKipu.Opciones(fuenteMija,
                _ -> new InfoDestino("NTFS", Long.MAX_VALUE), Interruptor.NINGUNO_MIJA);
        contenedorMija = ContenedorKipu.crear(carpetaMija.resolve("b.kipu"), Header.nuevo(zonaMija, slotsMija, 0),
                maestraMija, opcionesMija);
        Path origenMija = carpetaMija.resolve("grande.bin");
        Files.write(origenMija, fuenteMija.bytes(TAMANO_ARCHIVO_MIJA));
        byte[] idMija = contenedorMija.agregar(List.of(new Origen(origenMija, "grande.bin"))).getFirst().fileIdMija();
        Files.delete(origenMija);
        canalMija = contenedorMija.abrirLectura(idMija);
    }

    @TearDown
    public void limpiar() throws IOException {
        canalMija.close();
        contenedorMija.close();
        try (var archivosMija = Files.walk(carpetaMija)) {
            archivosMija.sorted(java.util.Comparator.reverseOrder()).forEach(rutaMija -> rutaMija.toFile().delete());
        }
    }

    @Benchmark
    public int lecturaAleatoria4KiB() throws IOException {
        canalMija.position(aleatorioMija.nextLong(TAMANO_ARCHIVO_MIJA - 4096L));
        bufferMija.clear();
        return canalMija.read(bufferMija);
    }

    /** Peor caso: cada lectura cae en un bloque distinto, así que siempre se descifra 1 MiB. */
    @Benchmark
    public int lecturaEnBloqueDistinto() throws IOException {
        long bloqueMija = aleatorioMija.nextLong(TAMANO_ARCHIVO_MIJA / (1 << 20));
        canalMija.position(bloqueMija * (1 << 20) + 17);
        bufferMija.clear();
        return canalMija.read(bufferMija);
    }
}
