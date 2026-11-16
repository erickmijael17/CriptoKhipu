package dev.erick.cryptokipu.ui.rendimiento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Presupuesto de rendimiento (Fase 9): arranque en frío de la interfaz MENOS de 2 s, medido sobre la imagen
 * {@code jlink} (módulos explícitos, sin classpath), desde que se lanza el proceso hasta que la ventana dibuja su
 * primer cuadro ({@code -Dcryptokipu.arranqueArchivo}: la aplicación escribe ese archivo y sale). Se toma la mediana
 * de 3 arranques; el primero, con la imagen recién creada, es el más "frío".
 *
 * <p>Necesita pantalla (en Linux, Xvfb). La imagen se construye con las mismas opciones que el instalador.
 */
@Tag("toolkit")
class ArranqueEnFrioTest {

    private static final long PRESUPUESTO_MS_MIJA = 2000;
    private static final boolean WINDOWS_MIJA = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

    @TempDir
    Path carpetaMija;

    private static String ejecutar(List<String> ordenMija) throws IOException, InterruptedException {
        Process procesoMija = new ProcessBuilder(ordenMija).redirectErrorStream(true).start();
        String salidaMija = new String(procesoMija.getInputStream().readAllBytes());
        assertTrue(procesoMija.waitFor(180, TimeUnit.SECONDS), "tiempo agotado: " + ordenMija);
        assertEquals(0, procesoMija.exitValue(), salidaMija);
        return salidaMija;
    }

    @Test
    void laInterfazArrancaEnMenosDeDosSegundosSobreLaImagenJlink() throws Exception {
        String sistemaMija = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        assumeTrue(!(sistemaMija.contains("linux") && System.getenv("DISPLAY") == null
                && System.getenv("WAYLAND_DISPLAY") == null), "Sin pantalla: prueba omitida");
        String rutaModulosMija = System.getProperty("ui.rutaModulos");
        assumeTrue(rutaModulosMija != null, "Falta -Dui.rutaModulos (se define en la tarea pruebasToolkit)");

        Path imagenMija = carpetaMija.resolve("imagen");
        Path jlinkMija = Path.of(System.getProperty("java.home"), "bin", WINDOWS_MIJA ? "jlink.exe" : "jlink");
        ejecutar(List.of(jlinkMija.toString(), "--module-path", rutaModulosMija, "--add-modules",
                "dev.erick.cryptokipu.ui,dev.erick.cryptokipu.visores", "--ignore-signing-information",
                "--strip-debug", "--no-header-files", "--no-man-pages", "--compress", "zip-6",
                "--output", imagenMija.toString()));
        Path javaMija = imagenMija.resolve("bin").resolve(WINDOWS_MIJA ? "java.exe" : "java");

        List<Long> tiemposMija = new ArrayList<>();
        for (int intentoMija = 0; intentoMija < 3; intentoMija++) {
            Path marcaMija = carpetaMija.resolve("listo-" + intentoMija);
            Path portatilMija = Files.createDirectories(carpetaMija.resolve("portatil-" + intentoMija));
            List<String> ordenMija = List.of(javaMija.toString(), "-XX:-UsePerfData", "-XX:+DisableAttachMechanism",
                    "--enable-native-access=javafx.graphics,dev.erick.cryptokipu.core",
                    "-Dcryptokipu.portatil=" + portatilMija, "-Dcryptokipu.arranqueArchivo=" + marcaMija,
                    "-Djava.io.tmpdir=" + portatilMija, "-m", "dev.erick.cryptokipu.ui/dev.erick.cryptokipu.ui.Launcher");
            long inicioMija = System.nanoTime();
            Process procesoMija = new ProcessBuilder(ordenMija).redirectErrorStream(true).start();
            String salidaMija = new String(procesoMija.getInputStream().readAllBytes());
            assertTrue(procesoMija.waitFor(60, TimeUnit.SECONDS), "la interfaz no terminó de arrancar");
            long milisMija = (System.nanoTime() - inicioMija) / 1_000_000;
            assertTrue(Files.exists(marcaMija), "la ventana no llegó a dibujarse: " + salidaMija);
            tiemposMija.add(milisMija);
        }
        List<Long> ordenadosMija = new ArrayList<>(tiemposMija);
        Collections.sort(ordenadosMija);
        long medianaMija = ordenadosMija.get(1);
        System.getLogger(ArranqueEnFrioTest.class.getName()).log(System.Logger.Level.INFO,
                "Arranque de la interfaz sobre la imagen jlink (ms): {0}, mediana {1}", tiemposMija, medianaMija);
        Files.writeString(Path.of(System.getProperty("java.io.tmpdir")).resolve("arranque-ms.txt"),
                tiemposMija + " mediana=" + medianaMija);
        assertTrue(medianaMija < PRESUPUESTO_MS_MIJA, "Arranque en frío " + tiemposMija + " ms; mediana " + medianaMija
                + " ms (presupuesto < " + PRESUPUESTO_MS_MIJA + " ms)");
        assertTrue(new File(javaMija.toString()).exists());
    }
}
