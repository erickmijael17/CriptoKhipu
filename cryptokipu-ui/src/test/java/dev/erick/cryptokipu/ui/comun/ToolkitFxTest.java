package dev.erick.cryptokipu.ui.comun;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erick.cryptokipu.ui.viewmodel.HiloFx;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.PasswordField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pruebas que necesitan el toolkit de JavaFX (25 no tiene modo sin pantalla y Monocle solo llega a 21). Se omiten
 * si no hay pantalla; en CI de Linux se ejecutan con {@code xvfb-run}.
 */
@Tag("toolkit")
class ToolkitFxTest {

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

    private static <T> T enFx(java.util.concurrent.Callable<T> accionMija) throws Exception {
        assumeTrue(disponibleMija, "Sin pantalla: prueba del toolkit omitida");
        CompletableFuture<T> resultadoMija = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                resultadoMija.complete(accionMija.call());
            } catch (Throwable errorMija) {
                resultadoMija.completeExceptionally(errorMija);
            }
        });
        return resultadoMija.get(10, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------ PasswordField

    @Test
    void leerYLimpiarCopiaCaracterPorCaracterYVaciaElCampo() throws Exception {
        enFx(() -> {
            PasswordField campoMija = new PasswordField();
            campoMija.setText("Kipu2Demo2026");
            char[] leidoMija = CampoSecreto.leerYLimpiar(campoMija);
            assertArrayEquals("Kipu2Demo2026".toCharArray(), leidoMija);
            assertEquals(0, campoMija.getLength(), "El campo debe quedar vacío");
            assertEquals("", campoMija.getText());
            return null;
        });
    }

    @Test
    void copiarYCortarNoLlevanElSecretoAlPortapapeles() throws Exception {
        enFx(() -> {
            Clipboard portapapelesMija = Clipboard.getSystemClipboard();
            ClipboardContent centinelaMija = new ClipboardContent();
            centinelaMija.putString("centinela");
            portapapelesMija.setContent(centinelaMija);
            PasswordField campoMija = new PasswordField();
            CampoSecreto.endurecer(campoMija, "F2");
            campoMija.setText("Kipu2Demo2026");
            campoMija.selectAll();
            campoMija.copy();
            assertEquals("centinela", portapapelesMija.getString());
            campoMija.cut();
            assertEquals("centinela", portapapelesMija.getString());
            assertEquals("Kipu2Demo2026", campoMija.getText(), "Cortar no debe modificar el campo");
            portapapelesMija.clear();
            return null;
        });
    }

    /** Fase 8: copiar un token sigue funcionando como texto normal y el guardia puede leerlo y limpiarlo. */
    @Test
    void copiarSinHistorialDejaElTextoLegibleYSePuedeLimpiar() throws Exception {
        enFx(() -> {
            PortapapelesFx portapapelesMija = new PortapapelesFx();
            portapapelesMija.copiarSinHistorial("CKP-AAAAA-BBBBB");
            assertEquals(java.util.Optional.of("CKP-AAAAA-BBBBB"), portapapelesMija.texto());
            portapapelesMija.limpiar();
            assertEquals(java.util.Optional.empty(), portapapelesMija.texto());
            return null;
        });
    }

    /**
     * Fase 8, investigación del historial del portapapeles de Windows (Win+V): se enumeran los formatos que el
     * SISTEMA tiene en el portapapeles (EnumClipboardFormats, desde otro proceso) y debe estar el que Windows
     * documenta para excluir el contenido del historial y de la nube.
     */
    @Test
    void windowsRecibeLosFormatosQueExcluyenElContenidoDelHistorial() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"), "solo Windows");
        enFx(() -> {
            new PortapapelesFx().copiarSinHistorial("CKP-AAAAA-BBBBB");
            return null;
        });
        String guionMija = String.join("\n",
                "Add-Type -TypeDefinition @'",
                "using System; using System.Text; using System.Runtime.InteropServices;",
                "public static class Cb {",
                "  [DllImport(\"user32.dll\")] public static extern bool OpenClipboard(IntPtr h);",
                "  [DllImport(\"user32.dll\")] public static extern bool CloseClipboard();",
                "  [DllImport(\"user32.dll\")] public static extern uint EnumClipboardFormats(uint f);",
                "  [DllImport(\"user32.dll\", CharSet=CharSet.Unicode)] public static extern int GetClipboardFormatName(uint f, StringBuilder s, int n);",
                "  public static string Nombres() {",
                "    var r = new StringBuilder();",
                "    for (int i = 0; i < 20 && !OpenClipboard(IntPtr.Zero); i++) System.Threading.Thread.Sleep(100);",
                "    uint f = 0;",
                "    while ((f = EnumClipboardFormats(f)) != 0) { var s = new StringBuilder(256); if (GetClipboardFormatName(f, s, 256) > 0) r.Append(s).Append(';'); }",
                "    CloseClipboard();",
                "    return r.ToString();",
                "  }",
                "}",
                "'@",
                "[Cb]::Nombres()");
        // Windows pierde las comillas dobles de los argumentos: el guion va a un archivo (en build/jvm-pruebas/tmp).
        java.nio.file.Path guionArchivoMija = java.nio.file.Files.createTempFile("formatos-portapapeles", ".ps1");
        java.nio.file.Files.writeString(guionArchivoMija, guionMija);
        Process procesoMija = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy",
                "Bypass", "-File", guionArchivoMija.toString()).redirectErrorStream(true).start();
        String salidaMija = new String(procesoMija.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(procesoMija.waitFor(60, TimeUnit.SECONDS));
        java.nio.file.Files.deleteIfExists(guionArchivoMija);
        enFx(() -> {
            Clipboard.getSystemClipboard().clear();
            return null;
        });
        assertTrue(salidaMija.contains("ExcludeClipboardContentFromMonitorProcessing"), salidaMija);
        assertTrue(salidaMija.contains("CanIncludeInClipboardHistory"), salidaMija);
        assertTrue(salidaMija.contains("CanUploadToCloudClipboard"), salidaMija);
    }

    @Test
    void sinMenuContextualYConTextoAccesibleSinSecreto() throws Exception {
        enFx(() -> {
            PasswordField campoMija = new PasswordField();
            CampoSecreto.endurecer(campoMija, "Contraseña numérica (F1)");
            campoMija.setText("482915736028");
            assertNull(campoMija.getContextMenu());
            ContextMenuEvent eventoMija = new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 1, 1, 1, 1,
                    false, null);
            AtomicBoolean llegoMija = new AtomicBoolean();
            campoMija.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, _ -> llegoMija.set(true));
            campoMija.fireEvent(eventoMija);
            assertFalse(llegoMija.get(), "El filtro consume la petición de menú contextual");
            assertFalse(campoMija.getAccessibleText().contains("482915736028"));
            return null;
        });
    }

    // ------------------------------------------------------------------ hilos

    @Test
    void elEjecutorCorreFueraDelHiloDeJavaFxYEntregaEnEl() throws Exception {
        AtomicReference<String> hiloTrabajoMija = new AtomicReference<>();
        CompletableFuture<String> hiloEntregaMija = new CompletableFuture<>();
        enFx(() -> {
            EjecutorTareas ejecutorMija = new EjecutorTareas(new VistaNula());
            ejecutorMija.ejecutar(_ -> {
                HiloFx.exigirFuera();
                hiloTrabajoMija.set(Thread.currentThread().getName());
                return Thread.currentThread().isVirtual();
            }, virtualMija -> hiloEntregaMija.complete(Thread.currentThread().getName() + "|" + virtualMija),
                    hiloEntregaMija::completeExceptionally);
            return null;
        });
        String entregaMija = hiloEntregaMija.get(10, TimeUnit.SECONDS);
        assertEquals(HiloFx.NOMBRE_HILO_FX_MIJA + "|true", entregaMija);
        assertNotEquals(HiloFx.NOMBRE_HILO_FX_MIJA, hiloTrabajoMija.get());
    }

    @Test
    void laGuardiaDetectaElHiloRealDeJavaFx() throws Exception {
        boolean rechazadoMija = enFx(() -> {
            assertTrue(Platform.isFxApplicationThread());
            try {
                HiloFx.exigirFuera();
                return false;
            } catch (IllegalStateException _) {
                return true;
            }
        });
        assertTrue(rechazadoMija);
    }

    @Test
    void cancelarDescartaYCierraElResultado() throws Exception {
        AtomicBoolean cerradoMija = new AtomicBoolean();
        AtomicBoolean entregadoMija = new AtomicBoolean();
        CountDownLatch empezoMija = new CountDownLatch(1);
        CountDownLatch seguirMija = new CountDownLatch(1);
        VistaNula vistaMija = new VistaNula();
        EjecutorTareas ejecutorMija = enFx(() -> {
            EjecutorTareas nuevoMija = new EjecutorTareas(vistaMija);
            nuevoMija.ejecutar(_ -> {
                empezoMija.countDown();
                seguirMija.await();
                return (AutoCloseable) () -> cerradoMija.set(true);
            }, _ -> entregadoMija.set(true), _ -> { });
            return nuevoMija;
        });
        assertTrue(empezoMija.await(10, TimeUnit.SECONDS));
        assertTrue(ejecutorMija.enCurso());
        enFx(() -> {
            vistaMija.cancelarMija.run();
            return null;
        });
        seguirMija.countDown();
        ejecutorMija.cancelarYEsperar(java.time.Duration.ofSeconds(10));
        enFx(() -> null); // deja correr el onSucceeded encolado
        enFx(() -> null);
        assertTrue(cerradoMija.get(), "El resultado descartado (p. ej. una sesión) se cierra");
        assertFalse(entregadoMija.get(), "El resultado de una operación cancelada no se entrega");
    }

    /** Vista de progreso vacía que recuerda la acción de cancelar. */
    private static final class VistaNula implements EjecutorTareas.VistaProgreso {
        private volatile Runnable cancelarMija = () -> { };

        @Override
        public void mostrar(Runnable alCancelarMija) {
            cancelarMija = alCancelarMija;
        }

        @Override
        public void etapa(String claveTextoMija, boolean cancelableMija) {
            // Sin interfaz.
        }

        @Override
        public void progreso(double fraccionMija) {
            // Sin interfaz.
        }

        @Override
        public void ocultar() {
            // Sin interfaz.
        }
    }
}
