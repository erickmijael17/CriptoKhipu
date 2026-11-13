package dev.erick.cryptokipu.core.crypto;

/**
 * Costo de Argon2id. Los límites anti-DoS y anti-debilitamiento del header se aplican en el
 * parser (Fase 2.2); aquí solo se validan los requisitos del propio algoritmo (RFC 9106).
 *
 * @param memoriaKibMija   memoria en KiB ({@code m})
 * @param iteracionesMija  pasadas ({@code t})
 * @param paralelismoMija  carriles ({@code p})
 */
public record ParametrosArgon2(int memoriaKibMija, int iteracionesMija, int paralelismoMija) {

    /*
     * Calibración (JMH, Argon2Benchmark, Ryzen 7 7730U, Bouncy Castle 1.87):
     * BC calcula los carriles en secuencia, así que p=4 no acelera (p=1 y p=4 miden igual) y el tiempo
     * es proporcional a m·t. Con t=2: 256 MiB ≈ 0,9 s · 512 MiB ≈ 2,0 s · 1 GiB ≈ 4,0 s por derivación.
     * Se mantiene p=4: no cuesta tiempo con BC y permite paralelizar si se cambia de implementación.
     */

    /** 256 MiB, t=2, p=4 (≈ 0,9 s). Perfil por defecto. */
    public static final ParametrosArgon2 ESTANDAR_MIJA = new ParametrosArgon2(256 * 1024, 2, 4);
    /** 512 MiB, t=2, p=4 (≈ 2 s). */
    public static final ParametrosArgon2 ALTO_MIJA = new ParametrosArgon2(512 * 1024, 2, 4);
    /** 1 GiB, t=2, p=4 (≈ 4 s). */
    public static final ParametrosArgon2 PARANOICO_MIJA = new ParametrosArgon2(1024 * 1024, 2, 4);

    public ParametrosArgon2 {
        if (iteracionesMija < 1) {
            throw new IllegalArgumentException("iteracionesMija debe ser >= 1");
        }
        if (paralelismoMija < 1 || paralelismoMija > 0xFFFFFF) {
            throw new IllegalArgumentException("paralelismoMija fuera de rango");
        }
        if (memoriaKibMija < 8 * paralelismoMija) {
            throw new IllegalArgumentException("memoriaKibMija debe ser >= 8 * paralelismoMija");
        }
    }

    /** Memoria que reservará Argon2, en bytes. */
    public long memoriaBytes() {
        return memoriaKibMija * 1024L;
    }
}
