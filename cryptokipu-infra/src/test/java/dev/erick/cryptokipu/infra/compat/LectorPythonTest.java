package dev.erick.cryptokipu.infra.compat;

import static dev.erick.cryptokipu.infra.compat.GeneradorGolden.CLAVE_MIJA;
import static dev.erick.cryptokipu.infra.compat.GeneradorGolden.FRASE_MIJA;
import static dev.erick.cryptokipu.infra.compat.GeneradorGolden.PIN_MIJA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Prueba de que la especificación está completa (Fase 9): un lector independiente en Python
 * ({@code tools/lector_kipu.py}, solo {@code cryptography} y {@code argon2-cffi}, sin código del proyecto) descifra la
 * bóveda golden v1 y los SHA-256 coinciden. Se omite si no hay Python con esas dos bibliotecas
 * ({@code pip install cryptography argon2-cffi}); en CI de Linux se instalan.
 */
class LectorPythonTest {

    @TempDir
    Path carpetaMija;

    private static String python() {
        for (String ejecutableMija : List.of("python3", "python", "py")) {
            try {
                Process procesoMija = new ProcessBuilder(ejecutableMija, "-c",
                        "import cryptography, argon2, _argon2_cffi_bindings").redirectErrorStream(true).start();
                procesoMija.getInputStream().readAllBytes();
                if (procesoMija.waitFor(30, TimeUnit.SECONDS) && procesoMija.exitValue() == 0) {
                    return ejecutableMija;
                }
            } catch (IOException | InterruptedException _) {
                // Se prueba el siguiente nombre.
            }
        }
        return null;
    }

    private static Path recurso(String nombreMija, Path destinoMija) throws IOException {
        try (InputStream entradaMija = LectorPythonTest.class.getResourceAsStream("/golden/v1/" + nombreMija)) {
            Files.copy(entradaMija, destinoMija);
        }
        return destinoMija;
    }

    private static String ejecutar(List<String> ordenMija, int esperadoMija) throws IOException, InterruptedException {
        Process procesoMija = new ProcessBuilder(ordenMija).redirectErrorStream(true).start();
        String salidaMija = new String(procesoMija.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(procesoMija.waitFor(120, TimeUnit.SECONDS), "el lector de Python no terminó");
        assertEquals(esperadoMija, procesoMija.exitValue(), salidaMija);
        return salidaMija;
    }

    @Test
    void elLectorIndependienteDescifraLaGoldenSoloConLaEspecificacion() throws Exception {
        String pythonMija = python();
        assumeTrue(pythonMija != null, "Sin Python con cryptography y argon2-cffi: prueba omitida");
        Path lectorMija = Path.of("..", "tools", "lector_kipu.py").toAbsolutePath().normalize();
        assumeTrue(Files.isRegularFile(lectorMija), "no se encontró " + lectorMija);
        Path bovedaMija = recurso("golden.kipu", carpetaMija.resolve("golden.kipu"));
        Path llaveMija = recurso("golden.kipukey", carpetaMija.resolve("golden.kipukey"));
        Path huellasMija = recurso("contenido.sha256", carpetaMija.resolve("contenido.sha256"));

        List<String> ordenMija = new ArrayList<>(List.of(pythonMija, "-X", "utf8", lectorMija.toString(),
                bovedaMija.toString(), "--f1", PIN_MIJA, "--f2", CLAVE_MIJA, "--f3", FRASE_MIJA,
                "--llave", llaveMija.toString(), "--verificar", huellasMija.toString()));
        String salidaMija = ejecutar(ordenMija, 0);

        assertEquals(5, salidaMija.lines().filter(lineaMija -> lineaMija.endsWith("OK")).count(), salidaMija);
        assertTrue(!salidaMija.contains("DISTINTO"), salidaMija);
    }

    @Test
    void elLectorRechazaUnFactorIncorrecto() throws Exception {
        String pythonMija = python();
        assumeTrue(pythonMija != null, "Sin Python con cryptography y argon2-cffi: prueba omitida");
        Path lectorMija = Path.of("..", "tools", "lector_kipu.py").toAbsolutePath().normalize();
        assumeTrue(Files.isRegularFile(lectorMija), "no se encontró " + lectorMija);
        Path bovedaMija = recurso("golden.kipu", carpetaMija.resolve("golden.kipu"));
        Path llaveMija = recurso("golden.kipukey", carpetaMija.resolve("golden.kipukey"));

        ejecutar(List.of(pythonMija, "-X", "utf8", lectorMija.toString(), bovedaMija.toString(), "--f1", PIN_MIJA,
                "--f2", "Otra Clave 2026", "--f3", FRASE_MIJA, "--llave", llaveMija.toString()), 3);
    }
}
