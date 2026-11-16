package dev.erick.cryptokipu.app.casos;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Retardo creciente entre intentos fallidos de desbloqueo (UI y CLI): tras el fallo 1 y el 2 no hay espera;
 * después 2, 4, 8 y 16 s y, desde el sexto fallo seguido, 30 s como máximo.
 *
 * <p>Solo vive en memoria: reiniciar la aplicación lo reinicia. <b>No es la defensa real</b> (la defensa real es el
 * costo de Argon2id y la autodestrucción): evita que una persona ante la pantalla pruebe combinaciones a ráfagas.
 * No conoce el motivo del fallo, así que nunca revela qué factor era incorrecto; solo se cuenta como fallo una
 * credencial incorrecta, no un error de entrada y salida.
 */
public final class RetardoIntentos {

    /** Espera (s) que sigue al fallo número 1, 2, 3… Del sexto en adelante vale {@link #MAXIMO_SEGUNDOS_MIJA}. */
    private static final long[] SEGUNDOS_MIJA = {0, 0, 2, 4, 8, 16};
    public static final long MAXIMO_SEGUNDOS_MIJA = 30;

    private final LongSupplier relojNanosMija;
    private int fallosSeguidosMija;
    private long libreEnNanosMija;

    public RetardoIntentos() {
        this(System::nanoTime);
    }

    /** @param relojNanosMija reloj monótono en nanosegundos (se sustituye en pruebas) */
    public RetardoIntentos(LongSupplier relojNanosMija) {
        this.relojNanosMija = Objects.requireNonNull(relojNanosMija, "relojNanosMija");
    }

    /** Segundos de espera que siguen al fallo seguido número {@code numeroFalloMija} (1 = el primero). */
    public static long segundosTrasFallo(int numeroFalloMija) {
        if (numeroFalloMija < 1) {
            throw new IllegalArgumentException("numeroFalloMija debe ser >= 1");
        }
        return numeroFalloMija <= SEGUNDOS_MIJA.length ? SEGUNDOS_MIJA[numeroFalloMija - 1] : MAXIMO_SEGUNDOS_MIJA;
    }

    /** Anota una credencial incorrecta y arranca la espera que corresponde. */
    public synchronized void registrarFallo() {
        fallosSeguidosMija++;
        libreEnNanosMija = relojNanosMija.getAsLong() + Duration.ofSeconds(segundosTrasFallo(fallosSeguidosMija)).toNanos();
    }

    /** Un desbloqueo correcto reinicia la cuenta. */
    public synchronized void registrarExito() {
        fallosSeguidosMija = 0;
        libreEnNanosMija = 0;
    }

    /** Lo que falta para poder intentar otra vez ({@link Duration#ZERO} si ya se puede). */
    public synchronized Duration restante() {
        if (fallosSeguidosMija == 0) {
            return Duration.ZERO;
        }
        long faltaNanosMija = libreEnNanosMija - relojNanosMija.getAsLong();
        return faltaNanosMija <= 0 ? Duration.ZERO : Duration.ofNanos(faltaNanosMija);
    }

    /** Segundos enteros que se muestran en la cuenta regresiva (redondeo hacia arriba). */
    public long segundosRestantes() {
        Duration faltaMija = restante();
        return (faltaMija.toNanos() + 999_999_999L) / 1_000_000_000L;
    }

    public synchronized int fallosSeguidos() {
        return fallosSeguidosMija;
    }

    public boolean puedeIntentar() {
        return restante().isZero();
    }
}
