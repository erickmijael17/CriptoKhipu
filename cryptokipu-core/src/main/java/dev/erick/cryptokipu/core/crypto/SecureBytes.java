package dev.erick.cryptokipu.core.crypto;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Bytes sensibles (claves, credenciales derivadas) guardados <b>fuera del heap</b>.
 *
 * <ul>
 *   <li>Hasta 64 bytes (todas las claves) viven en una ranura del {@link PoolMemoria}: un bloque reservado y bloqueado
 *       en RAM una sola vez ({@code VirtualLock}/{@code mlock}, best-effort). Más grandes, en su propia
 *       {@link Arena#ofShared()}.</li>
 *   <li>{@link #close()}: pone en cero la ranura (o el bloque) y la devuelve al pool (o cierra la arena). Idempotente.
 *       Después, cualquier acceso lanza {@link IllegalStateException}.</li>
 *   <li>Las bibliotecas de JCA exigen {@code byte[]}: {@link #conBytes} crea la copia justo antes de usarla y la
 *       borra justo después. {@link #bytes()} devuelve una copia que <b>quien llama debe borrar</b>.</li>
 *   <li>Lecturas y cierre se serializan en la instancia: una ranura reutilizada nunca se lee con una referencia vieja.</li>
 *   <li>{@link #toString()} nunca muestra el contenido.</li>
 * </ul>
 */
public final class SecureBytes implements AutoCloseable {

    /** Solo para pruebas: observa el bloque DESPUÉS de ponerlo en cero y ANTES de liberarlo. */
    private static volatile Consumer<MemorySegment> ganchoLiberacionMija = _ -> { };

    private final int longitudMija;
    private final MemorySegment segmentoMija;
    private final PoolMemoria.Ranura ranuraMija;
    private final Arena arenaPropiaMija;
    private final boolean bloqueadoEnRamMija;
    private boolean cerradoMija;

    private SecureBytes(byte[] datosMija) {
        this.longitudMija = datosMija.length;
        if (datosMija.length <= PoolMemoria.RANURA_MIJA) {
            this.ranuraMija = PoolMemoria.global().tomar();
            this.arenaPropiaMija = null;
            this.segmentoMija = ranuraMija.segmentoMija();
            this.bloqueadoEnRamMija = ranuraMija.bloqueadaMija();
        } else {
            this.ranuraMija = null;
            this.arenaPropiaMija = Arena.ofShared();
            long paginaMija = BloqueoMemoria.pagina();
            long tamanoMija = (datosMija.length + paginaMija - 1) / paginaMija * paginaMija;
            this.segmentoMija = arenaPropiaMija.allocate(tamanoMija, paginaMija);
            this.bloqueadoEnRamMija = BloqueoMemoria.bloquear(segmentoMija, tamanoMija);
        }
        MemorySegment.copy(datosMija, 0, segmentoMija, ValueLayout.JAVA_BYTE, 0, datosMija.length);
    }

    /** Toma posesión del arreglo: lo copia fuera del heap y <b>lo borra</b>. Quien llama no debe seguir usándolo. */
    public static SecureBytes envolver(byte[] datosMija) {
        Objects.requireNonNull(datosMija, "datosMija");
        try {
            return new SecureBytes(datosMija);
        } finally {
            Arrays.fill(datosMija, (byte) 0);
        }
    }

    /** Copia el arreglo; el original sigue siendo responsabilidad de quien llama. */
    public static SecureBytes copiaDe(byte[] datosMija) {
        return new SecureBytes(Objects.requireNonNull(datosMija, "datosMija"));
    }

    /**
     * Copia temporal de los bytes en el heap para entregarla a JCA y borrarla enseguida. Preferir este método a
     * {@link #bytes()}: la copia se borra aunque {@code usoMija} lance una excepción.
     */
    public <T> T conBytes(Function<byte[], T> usoMija) {
        byte[] copiaMija = bytes();
        try {
            return usoMija.apply(copiaMija);
        } finally {
            Arrays.fill(copiaMija, (byte) 0);
        }
    }

    /** Variante de {@link #conBytes(Function)} sin resultado. */
    public void usarBytes(Consumer<byte[]> usoMija) {
        conBytes(copiaMija -> {
            usoMija.accept(copiaMija);
            return null;
        });
    }

    /** Copia nueva de los bytes en el heap. <b>Quien la recibe debe borrarla</b> ({@link Arrays#fill}). */
    public synchronized byte[] bytes() {
        verificarAbierto();
        return segmentoMija.asSlice(0, longitudMija).toArray(ValueLayout.JAVA_BYTE);
    }

    public synchronized int longitud() {
        verificarAbierto();
        return longitudMija;
    }

    public synchronized boolean estaCerrado() {
        return cerradoMija;
    }

    /** {@code true} si el sistema aceptó bloquear la memoria en RAM (best-effort: puede ser {@code false}). */
    public boolean bloqueadoEnRam() {
        return bloqueadoEnRamMija;
    }

    @Override
    public synchronized void close() {
        if (cerradoMija) {
            return;
        }
        cerradoMija = true;
        segmentoMija.fill((byte) 0);
        ganchoLiberacionMija.accept(segmentoMija);
        if (ranuraMija != null) {
            PoolMemoria.global().devolver(ranuraMija);
        } else {
            try {
                if (bloqueadoEnRamMija) {
                    BloqueoMemoria.desbloquear(segmentoMija, segmentoMija.byteSize());
                }
            } finally {
                arenaPropiaMija.close();
            }
        }
    }

    @Override
    public String toString() {
        return "SecureBytes[***]";
    }

    /** Solo para pruebas del mismo paquete. */
    static void observarLiberacion(Consumer<MemorySegment> observadorMija) {
        ganchoLiberacionMija = Objects.requireNonNull(observadorMija, "observadorMija");
    }

    private void verificarAbierto() {
        if (cerradoMija) {
            throw new IllegalStateException("SecureBytes ya fue cerrado");
        }
    }
}
