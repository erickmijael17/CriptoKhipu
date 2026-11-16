package dev.erick.cryptokipu.ui.rendimiento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erick.cryptokipu.app.casos.AgregarArchivos;
import dev.erick.cryptokipu.app.casos.CrearBoveda;
import dev.erick.cryptokipu.app.casos.PoliticaColision;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import dev.erick.cryptokipu.infra.configuracion.PreferenciasStore;
import dev.erick.cryptokipu.ui.ContenedorUi;
import dev.erick.cryptokipu.ui.CryptoKipuApp;
import dev.erick.cryptokipu.ui.comun.DetectorBloqueos;
import dev.erick.cryptokipu.ui.comun.Navegador;
import dev.erick.cryptokipu.ui.viewmodel.HiloFx;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Presupuesto de rendimiento (Fase 9): durante un flujo automatizado de la interfaz (recorrer todas las pantallas,
 * crear y abrir una bóveda, explorar, bloquear) el detector de bloqueos NO registra ningún bloqueo del hilo de JavaFX
 * mayor de 100 ms entre pulsos. Las operaciones largas (Argon2id, cifrado) corren en hilos virtuales; lo que queda en
 * el hilo de JavaFX es solo cargar vistas y actualizar controles.
 */
@Tag("toolkit")
class PresupuestoHiloFxTest {

    private static boolean disponibleMija;

    @BeforeAll
    static void iniciarToolkit() throws InterruptedException {
        String sistemaMija = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (sistemaMija.contains("linux") && System.getenv("DISPLAY") == null
                && System.getenv("WAYLAND_DISPLAY") == null) {
            return;
        }
        CountDownLatch listoMija = new CountDownLatch(1);
        try {
            Platform.startup(listoMija::countDown);
            Platform.setImplicitExit(false);
        } catch (IllegalStateException _) {
            listoMija.countDown(); // ya iniciado por otra prueba
        } catch (RuntimeException | UnsatisfiedLinkError _) {
            return;
        }
        disponibleMija = listoMija.await(10, TimeUnit.SECONDS);
        if (disponibleMija) {
            HiloFx.toolkitIniciado();
        }
    }

    private static <T> T enFx(Callable<T> accionMija) throws Exception {
        CompletableFuture<T> resultadoMija = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                resultadoMija.complete(accionMija.call());
            } catch (Throwable errorMija) {
                resultadoMija.completeExceptionally(errorMija);
            }
        });
        return resultadoMija.get(60, TimeUnit.SECONDS);
    }

    /** Deja correr varios pulsos para que el detector vea el costo real de cada paso. */
    private static void esperarPulsos() throws InterruptedException {
        Thread.sleep(350);
    }

    @TempDir
    Path carpetaMija;

    @Test
    void ningunBloqueoDelHiloDeJavaFxSuperaLos100ms() throws Exception {
        assumeTrue(disponibleMija, "Sin pantalla: prueba del toolkit omitida");
        Path origenMija = Files.createDirectories(carpetaMija.resolve("origen"));
        Files.write(origenMija.resolve("nota.txt"), "hola".getBytes());
        Files.write(origenMija.resolve("datos.bin"), new byte[300_000]);
        Path bovedaMija = carpetaMija.resolve("flujo.kipu");

        Object[] contexto = new Object[2];
        enFx(() -> {
            ContenedorUi uiMija = CryptoKipuApp.componer(PreferenciasStore.enMemoria());
            Stage ventanaMija = new Stage();
            uiMija.usarVentana(ventanaMija);
            ventanaMija.setScene(CryptoKipuApp.escena(uiMija));
            ventanaMija.show();
            DetectorBloqueos detectorMija = new DetectorBloqueos();
            detectorMija.start();
            contexto[0] = uiMija;
            contexto[1] = detectorMija;
            return null;
        });
        ContenedorUi uiMija = (ContenedorUi) contexto[0];
        DetectorBloqueos detectorMija = (DetectorBloqueos) contexto[1];
        esperarPulsos();

        // Recorrer las pantallas que no necesitan bóveda abierta.
        for (Navegador.Vista vistaMija : List.of(Navegador.Vista.ASISTENTE, Navegador.Vista.AJUSTES,
                Navegador.Vista.INICIO)) {
            enFx(() -> {
                uiMija.navegador().mostrar(vistaMija);
                return null;
            });
            esperarPulsos();
        }

        // Crear la bóveda como lo haría el asistente (Argon2id fuera del hilo de JavaFX) y abrirla en la interfaz.
        Credenciales credencialesMija = new Credenciales("482915736028".toCharArray(), "Kipu2Demo2026".toCharArray(),
                "el condor pasa el rio".toCharArray(), null);
        try (CrearBoveda.Resultado creadaMija = uiMija.app().crearBoveda().ejecutar(new CrearBoveda.Comando(bovedaMija,
                credencialesMija, new ParametrosArgon2(64 * 1024, 2, 1), 0))) {
            uiMija.app().agregarArchivos().ejecutar(new AgregarArchivos.Comando(creadaMija.sesionMija(),
                    List.of(origenMija), "", PoliticaColision.OMITIR, ReporteProgreso.NINGUNO_MIJA));
        }
        enFx(() -> {
            uiMija.desbloqueo().rutaProperty().set(bovedaMija);
            uiMija.navegador().mostrar(Navegador.Vista.DESBLOQUEO);
            return null;
        });
        esperarPulsos();
        var sesionMija = uiMija.app().desbloquearBoveda().ejecutar(new dev.erick.cryptokipu.app.casos.DesbloquearBoveda
                .Comando(bovedaMija, new Credenciales("482915736028".toCharArray(), "Kipu2Demo2026".toCharArray(),
                        "el condor pasa el rio".toCharArray(), null)));
        enFx(() -> {
            uiMija.estado().abrir(bovedaMija, sesionMija); // el shell muestra el explorador
            return null;
        });
        esperarPulsos();
        for (Navegador.Vista vistaMija : List.of(Navegador.Vista.SEGURIDAD, Navegador.Vista.AJUSTES,
                Navegador.Vista.EXPLORADOR)) {
            enFx(() -> {
                uiMija.navegador().mostrar(vistaMija);
                return null;
            });
            esperarPulsos();
        }
        enFx(() -> {
            uiMija.autoBloqueo().bloquearAhora(); // bloquear también cierra visores y limpia temporales
            return null;
        });
        esperarPulsos();

        long peorMija = detectorMija.peorMilisegundos();
        enFx(() -> {
            detectorMija.stop();
            return null;
        });
        assertEquals(0, uiMija.estado().abiertaProperty().get() ? 1 : 0);
        assertTrue(peorMija <= 100, "El hilo de JavaFX estuvo bloqueado " + peorMija + " ms (presupuesto: 100 ms)");
    }
}
