package dev.erick.cryptokipu.core.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
 * Cifrado y descifrado AES-256-GCM por bloques de 1 MiB (ChunkCipher), medido sobre 16 MiB: el resultado de
 * cada benchmark es el tiempo por 16 MiB; docs/RENDIMIENTO.md lo convierte a MB/s.
 * {@code ./gradlew :cryptokipu-core:jmh -Pjmh.includes=Cifrado}
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CifradoBenchmark {

    private static final int TAMANO_MIJA = 16 << 20;
    private SecureBytes claveMija;
    private final byte[] fileIdMija = new byte[16];
    private byte[] claroMija;
    private byte[] cifradoMija;

    @Setup
    public void preparar() throws IOException {
        claveMija = SecureBytes.copiaDe(new byte[32]);
        claroMija = new byte[TAMANO_MIJA];
        new java.util.Random(1).nextBytes(claroMija);
        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream(TAMANO_MIJA + 1024);
        ChunkCipher.cifrar(claveMija, fileIdMija, new ByteArrayInputStream(claroMija), salidaMija);
        cifradoMija = salidaMija.toByteArray();
    }

    @TearDown
    public void limpiar() {
        claveMija.close();
    }

    @Benchmark
    public long cifrar16MiB() throws IOException {
        return ChunkCipher.cifrar(claveMija, fileIdMija, new ByteArrayInputStream(claroMija),
                new ByteArrayOutputStream(TAMANO_MIJA + 1024));
    }

    @Benchmark
    public long descifrar16MiB() throws IOException {
        return ChunkCipher.descifrar(claveMija, fileIdMija, new ByteArrayInputStream(cifradoMija),
                new ByteArrayOutputStream(TAMANO_MIJA));
    }
}
