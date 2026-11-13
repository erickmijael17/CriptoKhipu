package dev.erick.cryptokipu.arquitectura;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifica que las pruebas corren con las opciones de JVM centralizadas
 * (buildSrc/OpcionesJvm.kt): sin hsperfdata, sin volcados y temporales dentro de build/.
 */
class OpcionesJvmPruebasTest {

    @Test
    void laJvmDePruebasRecibeLasOpcionesDeSeguridad() {
        List<String> argumentosMija = ManagementFactory.getRuntimeMXBean().getInputArguments();

        for (String opcionMija : List.of(
                "-XX:-UsePerfData",
                "-XX:-HeapDumpOnOutOfMemoryError",
                "-XX:-CreateCoredumpOnCrash",
                "-XX:+DisableAttachMechanism",
                "-XX:MaxRAMPercentage=70")) {
            assertTrue(argumentosMija.contains(opcionMija), () -> "Falta la opción " + opcionMija);
        }
        assertTrue(argumentosMija.stream().anyMatch(argumentoMija -> argumentoMija.startsWith("-XX:ErrorFile=")),
                "Falta -XX:ErrorFile");
    }

    @Test
    void losTemporalesQuedanDentroDeBuild() {
        Path temporalMija = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();

        assertTrue(temporalMija.toString().replace('\\', '/').contains("/build/jvm-pruebas/tmp"),
                () -> "java.io.tmpdir apunta fuera de build/: " + temporalMija);
    }
}
