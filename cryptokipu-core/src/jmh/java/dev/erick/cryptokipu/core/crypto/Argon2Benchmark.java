package dev.erick.cryptokipu.core.crypto;

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
 * Tiempo de una derivación Argon2id (Bouncy Castle) por perfil y paralelismo.
 * Si {@code p=4} tarda lo mismo que {@code p=1}, BC calcula los carriles de forma secuencial.
 *
 * <p>Ejecutar: {@code ./gradlew :cryptokipu-core:jmh}
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class Argon2Benchmark {

    @Param({"262144", "524288", "1048576"})
    public int memoriaKibMija;

    @Param({"1", "4"})
    public int paralelismoMija;

    @Param({"2", "3"})
    public int iteracionesMija;

    private ParametrosArgon2 parametrosMija;
    private final byte[] entradaMija = new byte[60];
    private final byte[] salMija = new byte[32];
    private final byte[] secretoMija = new byte[32];

    @Setup
    public void preparar() {
        parametrosMija = new ParametrosArgon2(memoriaKibMija, iteracionesMija, paralelismoMija);
    }

    @Benchmark
    public byte[] derivar() {
        try (SecureBytes claveMija = Argon2idKdf.derivar(entradaMija, salMija, secretoMija, parametrosMija)) {
            return claveMija.bytes().clone();
        }
    }
}
