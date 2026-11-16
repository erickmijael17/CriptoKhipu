package dev.erick.cryptokipu.core.crypto;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Reserva única de memoria fuera del heap para las claves (Fase 9): bloques de 64 KiB reservados y bloqueados en RAM
 * ({@code VirtualLock}/{@code mlock}) UNA vez, partidos en ranuras de 64 bytes. Reservar y liberar una clave es tomar y
 * devolver una ranura, sin llamadas al sistema por clave (antes: una página bloqueada por clave, y la cuota de Windows
 * se agotaba con unas decenas). Se amplía con otro bloque cuando se llena. Las ranuras liberadas se ponen en cero y se
 * reutilizan; los bloques viven lo que dure el proceso.
 */
final class PoolMemoria {

    static final long RANURA_MIJA = 64;
    static final long BLOQUE_MIJA = 64 * 1024;
    private static final long RANURAS_POR_BLOQUE_MIJA = BLOQUE_MIJA / RANURA_MIJA;

    /** Una ranura prestada. {@code bloqueadaMija}: su bloque está bloqueado en RAM. */
    record Ranura(MemorySegment segmentoMija, boolean bloqueadaMija, int indiceBloqueMija, int indiceRanuraMija) {
    }

    private static final PoolMemoria GLOBAL_MIJA = new PoolMemoria();

    private final Arena arenaMija = Arena.ofShared();
    private final List<Bloque> bloquesMija = new ArrayList<>();
    private final Deque<Ranura> libresMija = new ArrayDeque<>();

    private record Bloque(MemorySegment segmentoMija, boolean bloqueadoMija) {
    }

    private PoolMemoria() {
    }

    static PoolMemoria global() {
        return GLOBAL_MIJA;
    }

    /** Toma una ranura (en cero); amplía el pool con otro bloque bloqueado si no queda ninguna. */
    synchronized Ranura tomar() {
        if (libresMija.isEmpty()) {
            ampliar();
        }
        return libresMija.pop();
    }

    /** Devuelve la ranura (quien llama ya la puso en cero). */
    synchronized void devolver(Ranura ranuraMija) {
        libresMija.push(ranuraMija);
    }

    synchronized int bloques() {
        return bloquesMija.size();
    }

    private void ampliar() {
        MemorySegment segmentoMija = arenaMija.allocate(BLOQUE_MIJA, BloqueoMemoria.pagina());
        boolean bloqueadoMija = BloqueoMemoria.bloquear(segmentoMija, BLOQUE_MIJA);
        int indiceBloqueMija = bloquesMija.size();
        bloquesMija.add(new Bloque(segmentoMija, bloqueadoMija));
        for (int indiceMija = (int) RANURAS_POR_BLOQUE_MIJA - 1; indiceMija >= 0; indiceMija--) {
            libresMija.push(new Ranura(segmentoMija.asSlice(indiceMija * RANURA_MIJA, RANURA_MIJA), bloqueadoMija,
                    indiceBloqueMija, indiceMija));
        }
    }
}
