package dev.erick.cryptokipu.core.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureBytesYNonceTest {

    @Test
    void cerrarBloqueaElAccesoYEsIdempotente() {
        SecureBytes seguroMija = SecureBytes.copiaDe(new byte[] {1, 2, 3, 4});

        seguroMija.close();
        seguroMija.close();

        assertTrue(seguroMija.estaCerrado());
        assertThrows(IllegalStateException.class, seguroMija::bytes);
        assertThrows(IllegalStateException.class, seguroMija::longitud);
        assertThrows(IllegalStateException.class, () -> seguroMija.conBytes(copiaMija -> copiaMija.length));
    }

    /**
     * Fase 8: el bloque fuera del heap se pone en cero ANTES de liberarse. Se observa justo entre el
     * {@code fill(0)} y el {@code arena.close()}; si close() no pusiera el bloque en cero, aquí quedarían los datos.
     */
    @Test
    void cerrarPoneEnCeroElMemorySegmentAntesDeLiberarlo() {
        byte[][] vistoMija = new byte[1][];
        SecureBytes.observarLiberacion(segmentoMija -> vistoMija[0] = segmentoMija.toArray(ValueLayout.JAVA_BYTE));
        try {
            SecureBytes seguroMija = SecureBytes.copiaDe(new byte[] {9, 8, 7, 6, 5, 4, 3, 2, 1});
            assertEquals(9, seguroMija.bytes()[0]);

            seguroMija.close();
        } finally {
            SecureBytes.observarLiberacion(_ -> { });
        }

        assertTrue(vistoMija[0] != null && vistoMija[0].length >= 9, "el observador no se ejecutó");
        assertArrayEquals(new byte[vistoMija[0].length], vistoMija[0], "el bloque no quedó en cero antes de liberarse");
    }

    @Test
    void envolverTomaPosesionDelArregloYLoBorraDeInmediato() {
        byte[] datosMija = {1, 2, 3, 4};
        try (SecureBytes seguroMija = SecureBytes.envolver(datosMija)) {
            assertArrayEquals(new byte[4], datosMija, "el arreglo original no debe seguir con la clave");
            assertArrayEquals(new byte[] {1, 2, 3, 4}, seguroMija.bytes());
        }
    }

    @Test
    void conBytesBorraLaCopiaTemporalAunqueElUsoFalle() {
        byte[][] copiaMija = new byte[1][];
        try (SecureBytes seguroMija = SecureBytes.copiaDe(new byte[] {7, 7, 7, 7})) {
            assertThrows(IllegalArgumentException.class, () -> seguroMija.conBytes(temporalMija -> {
                copiaMija[0] = temporalMija;
                throw new IllegalArgumentException("fallo del uso");
            }));
        }

        assertArrayEquals(new byte[4], copiaMija[0]);
    }

    @Test
    void bytesDevuelveUnaCopiaQueNoAfectaAlOriginal() {
        try (SecureBytes seguroMija = SecureBytes.copiaDe(new byte[] {1, 2, 3})) {
            byte[] copiaMija = seguroMija.bytes();
            Arrays.fill(copiaMija, (byte) 0);

            assertArrayEquals(new byte[] {1, 2, 3}, seguroMija.bytes());
        }
    }

    @Test
    void sePuedeLeerDesdeVariosHilosVirtualesYCerrarDesdeOtro() throws Exception {
        SecureBytes seguroMija = SecureBytes.copiaDe(new byte[32]);
        try (var ejecutorMija = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tareasMija = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int indiceMija = 0; indiceMija < 16; indiceMija++) {
                tareasMija.add(ejecutorMija.submit(() -> seguroMija.conBytes(copiaMija -> copiaMija.length)));
            }
            for (var tareaMija : tareasMija) {
                assertEquals(32, tareaMija.get());
            }
            ejecutorMija.submit(seguroMija::close).get();
        }
        assertTrue(seguroMija.estaCerrado());
    }

    /**
     * Fase 9: las claves salen de un pool bloqueado una sola vez. 1000 claves vivas caben en un único bloque de 64 KiB:
     * o todas quedan en páginas bloqueadas o (si el sistema lo impide) hay UN aviso único, nunca uno por clave.
     */
    @Test
    void milClavesVivasSalenDelPoolBloqueadoOHayUnSoloAviso() {
        List<SecureBytes> vivasMija = new ArrayList<>();
        int bloquesAntesMija = PoolMemoria.global().bloques();
        try {
            for (int indiceMija = 0; indiceMija < 1000; indiceMija++) {
                byte[] materialMija = new byte[32];
                materialMija[0] = (byte) indiceMija;
                materialMija[1] = (byte) (indiceMija >> 8);
                vivasMija.add(SecureBytes.envolver(materialMija));
            }
            assertTrue(PoolMemoria.global().bloques() - bloquesAntesMija <= 1, "1000 claves caben en un bloque");
            long bloqueadasMija = vivasMija.stream().filter(SecureBytes::bloqueadoEnRam).count();
            if (bloqueadasMija != vivasMija.size()) {
                assertTrue(BloqueoMemoria.avisoEmitido(), "si el SO lo impide debe haber un aviso único");
            }
            for (int indiceMija = 0; indiceMija < 1000; indiceMija += 111) {
                byte[] copiaMija = vivasMija.get(indiceMija).bytes();
                assertEquals((byte) indiceMija, copiaMija[0]);
                assertEquals((byte) (indiceMija >> 8), copiaMija[1]);
            }
        } finally {
            vivasMija.forEach(SecureBytes::close);
        }
    }

    @Test
    void unaRanuraLiberadaSePoneEnCeroYSeReutilizaSinFiltrarLaClaveAnterior() {
        SecureBytes primeraMija = SecureBytes.copiaDe(new byte[] {7, 7, 7, 7, 7, 7, 7, 7});
        primeraMija.close();
        try (SecureBytes segundaMija = SecureBytes.copiaDe(new byte[] {1})) {
            assertArrayEquals(new byte[] {1}, segundaMija.bytes());
        }
        // Si se reutilizó la misma ranura, nada de la primera clave puede verse: se observa el bloque completo.
        byte[][] vistoMija = new byte[1][];
        SecureBytes.observarLiberacion(segmentoMija -> vistoMija[0] = segmentoMija.toArray(ValueLayout.JAVA_BYTE));
        try {
            SecureBytes.copiaDe(new byte[] {9, 9, 9}).close();
        } finally {
            SecureBytes.observarLiberacion(_ -> { });
        }
        assertArrayEquals(new byte[vistoMija[0].length], vistoMija[0]);
    }

    @Test
    void lasClavesGrandesUsanSuPropiaArenaYTambienSeBorran() {
        byte[] grandeMija = new byte[200];
        Arrays.fill(grandeMija, (byte) 5);
        SecureBytes seguroMija = SecureBytes.envolver(grandeMija);
        assertEquals(200, seguroMija.longitud());
        assertEquals(5, seguroMija.bytes()[199]);
        seguroMija.close();
        assertThrows(IllegalStateException.class, seguroMija::bytes);
    }

    /** El bloqueo en RAM es best-effort: puede ser true o false según el sistema, pero nunca debe lanzar. */
    @Test
    void elBloqueoEnRamEsBestEffortYNuncaLanza() {
        assertDoesNotThrow(() -> {
            for (int indiceMija = 0; indiceMija < 200; indiceMija++) { // más páginas de las que el sistema suele permitir
                try (SecureBytes seguroMija = SecureBytes.copiaDe(new byte[32])) {
                    assertEquals(32, seguroMija.longitud());
                    seguroMija.bloqueadoEnRam();
                }
            }
        });
        // Abiertos a la vez: si el sistema se queda sin cuota, los últimos simplemente no se bloquean.
        List<SecureBytes> vivosMija = new ArrayList<>();
        try {
            for (int indiceMija = 0; indiceMija < 100; indiceMija++) {
                vivosMija.add(SecureBytes.copiaDe(new byte[32]));
            }
            assertEquals(100, vivosMija.size());
        } finally {
            vivosMija.forEach(SecureBytes::close);
        }
    }

    @Test
    void copiaDeNoComparteElArregloOriginal() {
        byte[] datosMija = {5, 6, 7};
        try (SecureBytes seguroMija = SecureBytes.copiaDe(datosMija)) {
            datosMija[0] = 0;
            assertEquals(5, seguroMija.bytes()[0]);
        }
    }

    @Test
    void toStringNuncaMuestraElContenido() {
        try (SecureBytes seguroMija = SecureBytes.copiaDe(new byte[] {0x41, 0x42})) {
            assertEquals("SecureBytes[***]", seguroMija.toString());
        }
    }

    @Test
    void nonceDeBloqueEsCerosMasIndice() {
        assertArrayEquals(HexFormat.of().parseHex("000000000000000000000000"), ProveedorNonce.deBloque(0));
        assertArrayEquals(HexFormat.of().parseHex("000000000000000000000102"), ProveedorNonce.deBloque(258));
        assertThrows(IllegalArgumentException.class, () -> ProveedorNonce.deBloque(-1));
    }

    @Test
    void noncesAleatoriosMiden12YNoSeRepiten() {
        ProveedorNonce proveedorMija = new ProveedorNonce(FuenteAleatoria.delSistema());
        Set<String> vistosMija = new HashSet<>();
        for (int indiceMija = 0; indiceMija < 10_000; indiceMija++) {
            byte[] nonceMija = proveedorMija.aleatorio();
            assertEquals(12, nonceMija.length);
            assertTrue(vistosMija.add(HexFormat.of().formatHex(nonceMija)));
        }
        assertFalse(vistosMija.isEmpty());
    }
}
