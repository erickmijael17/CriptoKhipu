package dev.erick.cryptokipu.cli;

import java.time.Duration;

/**
 * Espera entre intentos fallidos de desbloqueo en la consola interactiva (Fase 8). Es un puerto para poder probar el
 * retardo sin dormir de verdad; {@link #REAL_MIJA} duerme el hilo.
 */
@FunctionalInterface
public interface Pausa {

    Pausa REAL_MIJA = duracionMija -> Thread.sleep(duracionMija);

    void dormir(Duration duracionMija) throws InterruptedException;
}
