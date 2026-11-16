package dev.erick.cryptokipu.core.format;

import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.crypto.ProveedorNonce;
import dev.erick.cryptokipu.core.crypto.SecureBytes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Apertura de una bóveda con muchas entradas: descifrado y parseo del manifest ({@code ManifestCodec.descifrar}).
 * {@code ./gradlew :cryptokipu-core:jmh -Pjmh.includes=Manifest}
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ManifestBenchmark {

    @Param({"10000", "100000"})
    public int entradasMija;

    private SecureBytes claveMija;
    private final byte[] vaultIdMija = new byte[16];
    private byte[] almacenadoMija;

    @Setup
    public void preparar() {
        claveMija = SecureBytes.copiaDe(new byte[32]);
        List<EntradaManifest> listaMija = new ArrayList<>(entradasMija);
        FuenteAleatoria fuenteMija = FuenteAleatoria.delSistema();
        for (int indiceMija = 0; indiceMija < entradasMija; indiceMija++) {
            byte[] idMija = fuenteMija.bytes(16);
            listaMija.add(new EntradaManifest(idMija, "carpeta" + indiceMija % 100 + "/archivo-" + indiceMija + ".dat",
                    1000L + indiceMija, 1_700_000_000_000L + indiceMija, "application/octet-stream",
                    LimitesFormato.OFFSET_DATOS_MIJA + indiceMija * 1100L, 1016L));
        }
        almacenadoMija = ManifestCodec.cifrar(new Manifest(3, listaMija), claveMija, vaultIdMija,
                new ProveedorNonce(fuenteMija));
    }

    @Benchmark
    public Manifest abrirManifest() {
        return ManifestCodec.descifrar(almacenadoMija, claveMija, vaultIdMija, 3);
    }
}
