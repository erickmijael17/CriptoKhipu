package dev.erick.cryptokipu.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fase 8: el auto-bloqueo cierra de verdad la sesión (clave maestra en cero), espera a las operaciones en curso y
 * dispara los {@code alCerrar} (reproductor, visores, portapapeles).
 */
class AutoBloqueoTest {

    private static final long MINUTO_MS_MIJA = Duration.ofMinutes(1).toMillis();

    @TempDir
    Path carpetaMija;

    private final AtomicLong relojMsMija = new AtomicLong(1_000_000);
    private final AtomicBoolean ocupadoMija = new AtomicBoolean();
    private final List<Boolean> bloqueosMija = new ArrayList<>();
    private Contenedor appMija;
    private EstadoApp estadoMija;
    private AutoBloqueo autoMija;
    private SesionBoveda sesionMija;
    private Path bovedaMija;

    @BeforeEach
    void abrirBoveda() throws Exception {
        appMija = BancoUi.contenedor(carpetaMija);
        bovedaMija = carpetaMija.resolve("b.kipu");
        sesionMija = BancoUi.asistenteListo(appMija, bovedaMija, List.of()).crear(ProgresoOperacion.NINGUNO_MIJA)
                .sesionMija();
        estadoMija = new EstadoApp(appMija);
        autoMija = new AutoBloqueo(estadoMija, ocupadoMija::get, relojMsMija::get);
        autoMija.alBloquear(bloqueosMija::add);
        estadoMija.abrir(bovedaMija, sesionMija);
    }

    private void pasarMinutos(long minutosMija) {
        relojMsMija.addAndGet(minutosMija * MINUTO_MS_MIJA);
    }

    // ------------------------------------------------------------------ inactividad

    @Test
    void trasCincoMinutosSinActividadCierraLaSesionYLaClaveQuedaInutilizable() {
        pasarMinutos(4);
        autoMija.revisar();
        assertTrue(estadoMija.abiertaProperty().get(), "a los 4 minutos todavía no");
        assertFalse(sesionMija.cerrada());

        pasarMinutos(1);
        autoMija.revisar();

        assertFalse(estadoMija.abiertaProperty().get());
        assertTrue(sesionMija.cerrada(), "el auto-bloqueo debe CERRAR la sesión (clave maestra en cero)");
        assertThrows(IllegalStateException.class, sesionMija::manifest);
        assertEquals(List.of(true), bloqueosMija);
    }

    @Test
    void laActividadReiniciaElPlazo() {
        pasarMinutos(4);
        autoMija.actividad();
        pasarMinutos(4);
        autoMija.revisar();
        assertTrue(estadoMija.abiertaProperty().get(), "hubo actividad hace 4 minutos");

        pasarMinutos(1);
        autoMija.revisar();
        assertFalse(estadoMija.abiertaProperty().get());
    }

    @Test
    void ceroMinutosDesactivaElBloqueo() {
        autoMija.usarMinutos(0);
        pasarMinutos(24 * 60);
        autoMija.revisar();

        assertTrue(estadoMija.abiertaProperty().get());
        assertTrue(bloqueosMija.isEmpty());
    }

    @Test
    void losMinutosSonConfigurables() {
        autoMija.usarMinutos(1);
        pasarMinutos(1);
        autoMija.revisar();

        assertTrue(sesionMija.cerrada());
    }

    // ------------------------------------------------------------------ espera a lo que está en curso

    @Test
    void conUnaOperacionEnCursoEsperaYBloqueaCuandoTermina() {
        ocupadoMija.set(true);
        pasarMinutos(30);
        autoMija.revisar();
        assertTrue(estadoMija.abiertaProperty().get(), "no se interrumpe una operación en curso");
        assertFalse(sesionMija.cerrada());

        ocupadoMija.set(false);
        autoMija.revisar();

        assertTrue(sesionMija.cerrada());
        assertEquals(1, bloqueosMija.size());
    }

    @Test
    void conRotacionObligatoriaNoBloqueaPorqueGastariaElToken() {
        estadoMija.rotacionObligatoriaProperty().set(true);
        pasarMinutos(30);
        autoMija.revisar();
        assertTrue(estadoMija.abiertaProperty().get());
        assertFalse(sesionMija.cerrada());

        estadoMija.rotacionTerminada();
        autoMija.revisar();
        assertTrue(sesionMija.cerrada());
    }

    // ------------------------------------------------------------------ al minimizar

    @Test
    void alMinimizarBloqueaSoloSiEstaActivado() {
        autoMija.alMinimizar();
        assertTrue(estadoMija.abiertaProperty().get(), "desactivado por defecto");

        autoMija.bloquearAlMinimizar(true);
        autoMija.alMinimizar();

        assertTrue(sesionMija.cerrada());
        assertEquals(1, bloqueosMija.size());
    }

    @Test
    void alMinimizarConUnaOperacionEnCursoEsperaAQueTermine() {
        autoMija.bloquearAlMinimizar(true);
        ocupadoMija.set(true);
        autoMija.alMinimizar();
        assertFalse(sesionMija.cerrada());

        ocupadoMija.set(false);
        autoMija.revisar();

        assertTrue(sesionMija.cerrada());
    }

    // ------------------------------------------------------------------ qué ocurre al bloquear

    @Test
    void alBloquearSeEjecutanLosAlCerrarYLaRutaQuedaParaElDesbloqueo() {
        int[] cierresMija = {0};
        estadoMija.alCerrar(() -> cierresMija[0]++);
        pasarMinutos(5);

        autoMija.revisar();

        assertEquals(1, cierresMija[0], "reproductor, visores y portapapeles se limpian al bloquear");
        assertEquals(bovedaMija, estadoMija.ultimaRuta(), "la misma ruta se precarga en la pantalla de desbloqueo");
    }

    @Test
    void sinBovedaAbiertaNoHaceNada() {
        estadoMija.cerrar();
        bloqueosMija.clear();
        pasarMinutos(60);

        autoMija.revisar();

        assertTrue(bloqueosMija.isEmpty());
    }

    @Test
    void abrirUnaBovedaReiniciaElPlazo() throws Exception {
        pasarMinutos(4);
        estadoMija.cerrar();
        SesionBoveda otraMija = appMija.desbloquearBoveda().ejecutar(new dev.erick.cryptokipu.app.casos
                .DesbloquearBoveda.Comando(bovedaMija, new dev.erick.cryptokipu.core.auth.Credenciales(
                        BancoUi.c(BancoUi.F1_MIJA), BancoUi.c(BancoUi.F2_MIJA), BancoUi.c(BancoUi.F3_MIJA), null)));
        estadoMija.abrir(bovedaMija, otraMija);

        pasarMinutos(4);
        autoMija.revisar();

        assertTrue(estadoMija.abiertaProperty().get(), "el plazo empezó al abrir, no hace 8 minutos");
        estadoMija.cerrar();
    }

    // ------------------------------------------------------------------ carpeta temporal de apps externas

    @Test
    void alBloquearLimpiaLaCarpetaTemporalYInformaSiEstaLimpia() throws IOException {
        Path carpetaTemporalMija = estadoMija.espacioExterno().carpeta();
        Files.writeString(carpetaTemporalMija.resolve("copia.txt"), "contenido descifrado");

        assertTrue(estadoMija.bloquear(), "sin aplicaciones externas, la carpeta se borra completa");

        assertFalse(Files.exists(carpetaTemporalMija));
        assertNull(estadoMija.sesionProperty().get());
    }

    @Test
    void siUnaAplicacionExternaTieneUnArchivoAbiertoAvisaYLaCarpetaQuedaParaDespues() throws IOException {
        assumeTrue(System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win"),
                "solo Windows impide borrar un archivo abierto");
        Path carpetaTemporalMija = estadoMija.espacioExterno().carpeta();
        Path copiaMija = carpetaTemporalMija.resolve("abierta.txt");
        Files.writeString(copiaMija, "contenido descifrado");

        // FileInputStream no comparte el permiso de borrado en Windows (como una aplicación externa real);
        // Files.newInputStream sí lo comparte y dejaría borrar el archivo.
        try (InputStream aplicacionExternaMija = new FileInputStream(copiaMija.toFile())) {
            assertTrue(aplicacionExternaMija.read() >= 0);

            assertFalse(estadoMija.bloquear(), "debe avisar: una aplicación externa mantiene archivos abiertos");
        }
        assertTrue(sesionMija.cerrada(), "aun así la sesión se cierra");
    }
}
