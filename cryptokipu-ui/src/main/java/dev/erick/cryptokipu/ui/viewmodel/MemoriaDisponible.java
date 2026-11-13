package dev.erick.cryptokipu.ui.viewmodel;

import java.lang.management.ManagementFactory;

/**
 * Memoria que Argon2id podrá usar: el mínimo entre lo que le queda al heap de la JVM (Bouncy Castle reserva la
 * matriz de Argon2 en el heap) y la RAM física libre del equipo. Se consulta antes de derivar para advertir.
 */
public final class MemoriaDisponible {

    private MemoriaDisponible() {
    }

    public static long bytes() {
        Runtime jvmMija = Runtime.getRuntime();
        long heapLibreMija = jvmMija.maxMemory() - (jvmMija.totalMemory() - jvmMija.freeMemory());
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean
                sistemaMija) {
            return Math.min(heapLibreMija, sistemaMija.getFreeMemorySize());
        }
        return heapLibreMija;
    }
}
