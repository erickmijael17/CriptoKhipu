package dev.erick.cryptokipu.app.casos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RetardoIntentosTest {

    private final AtomicLong relojMija = new AtomicLong();
    private final RetardoIntentos retardoMija = new RetardoIntentos(relojMija::get);

    private void pasar(long segundosMija) {
        relojMija.addAndGet(Duration.ofSeconds(segundosMija).toNanos());
    }

    @Test
    void laSecuenciaEsCeroCeroDosCuatroOchoDieciseisYTreinta() {
        long[] esperadoMija = {0, 0, 2, 4, 8, 16, 30, 30, 30};
        for (int indiceMija = 0; indiceMija < esperadoMija.length; indiceMija++) {
            assertEquals(esperadoMija[indiceMija], RetardoIntentos.segundosTrasFallo(indiceMija + 1),
                    "fallo " + (indiceMija + 1));
        }
        assertThrows(IllegalArgumentException.class, () -> RetardoIntentos.segundosTrasFallo(0));
    }

    @Test
    void losDosPrimerosFallosNoEsperanYElTerceroBloqueaDosSegundos() {
        retardoMija.registrarFallo();
        assertTrue(retardoMija.puedeIntentar());
        retardoMija.registrarFallo();
        assertTrue(retardoMija.puedeIntentar());

        retardoMija.registrarFallo();
        assertFalse(retardoMija.puedeIntentar());
        assertEquals(2, retardoMija.segundosRestantes());
        pasar(1);
        assertEquals(1, retardoMija.segundosRestantes());
        pasar(1);
        assertTrue(retardoMija.puedeIntentar());
    }

    @Test
    void laEsperaCrece_y_seTopaEnTreintaSegundos() {
        for (int indiceMija = 0; indiceMija < 6; indiceMija++) {
            retardoMija.registrarFallo();
            pasar(100);
        }
        retardoMija.registrarFallo(); // séptimo fallo
        assertEquals(30, retardoMija.segundosRestantes());
        pasar(30);
        retardoMija.registrarFallo(); // octavo: sigue en 30
        assertEquals(30, retardoMija.segundosRestantes());
    }

    @Test
    void unExitoReiniciaLaCuentaYLaEspera() {
        for (int indiceMija = 0; indiceMija < 4; indiceMija++) {
            retardoMija.registrarFallo();
        }
        assertFalse(retardoMija.puedeIntentar());

        retardoMija.registrarExito();

        assertTrue(retardoMija.puedeIntentar());
        assertEquals(0, retardoMija.fallosSeguidos());
        retardoMija.registrarFallo();
        assertTrue(retardoMija.puedeIntentar(), "tras un éxito, el primer fallo vuelve a no esperar");
    }
}
