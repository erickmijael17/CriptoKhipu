package dev.erick.cryptokipu.ui.viewmodel;

import static dev.erick.cryptokipu.ui.viewmodel.BancoUi.F1_MIJA;
import static dev.erick.cryptokipu.ui.viewmodel.BancoUi.F2_MIJA;
import static dev.erick.cryptokipu.ui.viewmodel.BancoUi.F3_MIJA;
import static dev.erick.cryptokipu.ui.viewmodel.BancoUi.c;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fase 8: retardo creciente entre intentos fallidos en la UI (0, 0, 2, 4, 8, 16, 30 s) con cuenta regresiva y la
 * autodestrucción configurable (doble confirmación). Las mutaciones «saltarse el retardo» rompen estas pruebas.
 */
class RetardoDesbloqueoTest {

    @TempDir
    Path carpetaMija;

    private final AtomicLong relojNanosMija = new AtomicLong();
    private Contenedor appMija;
    private Path bovedaMija;
    private DesbloqueoViewModel desbloqueoMija;

    @BeforeEach
    void prepararBoveda() throws Exception {
        appMija = BancoUi.contenedor(carpetaMija);
        bovedaMija = carpetaMija.resolve("b.kipu");
        BancoUi.asistenteListo(appMija, bovedaMija, List.of()).crear(ProgresoOperacion.NINGUNO_MIJA).sesionMija().close();
        desbloqueoMija = new DesbloqueoViewModel(appMija, MemoriaDisponible::bytes, relojNanosMija::get);
        desbloqueoMija.rutaProperty().set(bovedaMija);
    }

    private void fallar() {
        assertThrows(ExcepcionBoveda.class, () -> desbloqueoMija.desbloquear(c("999999999999"), c(F2_MIJA), c(F3_MIJA)));
        desbloqueoMija.actualizarEspera();
    }

    private void pasarSegundos(long segundosMija) {
        relojNanosMija.addAndGet(Duration.ofSeconds(segundosMija).toNanos());
        desbloqueoMija.actualizarEspera();
    }

    @Test
    void losDosPrimerosFallosNoEsperan() {
        fallar();
        assertTrue(desbloqueoMija.puedeIntentar());
        fallar();
        assertTrue(desbloqueoMija.puedeIntentar());
        assertEquals(0, desbloqueoMija.segundosEsperaProperty().get());
    }

    @Test
    void elTercerFalloBloqueaDosSegundosConCuentaRegresivaVisible() throws Exception {
        fallar();
        fallar();
        fallar();

        assertFalse(desbloqueoMija.puedeIntentar());
        assertEquals(2, desbloqueoMija.segundosEsperaProperty().get());
        assertTrue(desbloqueoMija.avisoEsperaProperty().get().contains("2"), desbloqueoMija.avisoEsperaProperty().get());

        pasarSegundos(1);
        assertEquals(1, desbloqueoMija.segundosEsperaProperty().get());
        pasarSegundos(1);
        assertEquals(0, desbloqueoMija.segundosEsperaProperty().get());
        assertEquals("", desbloqueoMija.avisoEsperaProperty().get());
    }

    /** Mutación: «saltarse el retardo» (aceptar el intento aunque haya espera) rompe esta prueba. */
    @Test
    void durantelaEsperaNoSePuedeIntentarNiConLasCredencialesCorrectas() {
        fallar();
        fallar();
        fallar();

        IllegalStateException errorMija = assertThrows(IllegalStateException.class,
                () -> desbloqueoMija.desbloquear(c(F1_MIJA), c(F2_MIJA), c(F3_MIJA)));
        assertTrue(errorMija.getMessage().contains("espera"), errorMija.getMessage());
        assertThrows(IllegalStateException.class, () -> desbloqueoMija.recuperar(c("CKP-AAAAA"), c(F1_MIJA)));
    }

    @Test
    void despuesDeLaEsperaSeAbreYUnExitoReiniciaLaCuenta() throws Exception {
        fallar();
        fallar();
        fallar();
        pasarSegundos(2);

        try (SesionBoveda sesionMija = desbloqueoMija.desbloquear(c(F1_MIJA), c(F2_MIJA), c(F3_MIJA))) {
            assertFalse(sesionMija.cerrada());
        }

        fallar();
        assertTrue(desbloqueoMija.puedeIntentar(), "tras un éxito, el primer fallo vuelve a no esperar");
    }

    @Test
    void laEsperaSeTopaEnTreintaSegundos() {
        for (int indiceMija = 0; indiceMija < 6; indiceMija++) {
            fallar();
            pasarSegundos(100);
        }
        fallar(); // séptimo
        assertEquals(30, desbloqueoMija.segundosEsperaProperty().get());
    }

    @Test
    void laEsperaEsPorBovedaYNoRevelaQueFactorFallo() {
        fallar();
        fallar();
        fallar();
        Path otraMija = carpetaMija.resolve("otra.kipu");
        desbloqueoMija.rutaProperty().set(otraMija);
        desbloqueoMija.actualizarEspera();
        assertTrue(desbloqueoMija.puedeIntentar(), "la espera es de la bóveda que falló, no de otra");

        desbloqueoMija.rutaProperty().set(bovedaMija);
        desbloqueoMija.actualizarEspera();
        String avisoMija = desbloqueoMija.avisoEsperaProperty().get().toLowerCase(java.util.Locale.ROOT);
        assertFalse(avisoMija.contains("f1") || avisoMija.contains("f2") || avisoMija.contains("f3"), avisoMija);
    }

    // ------------------------------------------------------------------ autodestrucción: doble confirmación

    @Test
    void activarLaAutodestruccionExigeDobleConfirmacionYDesactivarla_no() {
        assertFalse(SeguridadViewModel.exigeDobleConfirmacion(0));
        assertTrue(SeguridadViewModel.exigeDobleConfirmacion(1));
        assertTrue(SeguridadViewModel.exigeDobleConfirmacion(10));
    }

    @Test
    void laSegundaConfirmacionPideEscribirLaPalabra() {
        String palabraMija = SeguridadViewModel.palabraConfirmacion();
        assertFalse(palabraMija.isBlank());
        assertTrue(SeguridadViewModel.palabraCorrecta(palabraMija));
        assertTrue(SeguridadViewModel.palabraCorrecta("  " + palabraMija.toLowerCase(java.util.Locale.ROOT) + " "));
        assertFalse(SeguridadViewModel.palabraCorrecta("destruir ya"));
        assertFalse(SeguridadViewModel.palabraCorrecta(""));
        assertFalse(SeguridadViewModel.palabraCorrecta(null));
    }
}
