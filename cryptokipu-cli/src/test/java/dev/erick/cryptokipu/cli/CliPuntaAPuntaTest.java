package dev.erick.cryptokipu.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Criterio de salida de la Fase 5: la CLI real como subproceso ({@code ProcessBuilder} + {@code --secretos-stdin}).
 * create → info → list → add → extract --todo → SHA-256 → verify → compact → recover → rotate → abrir con lo nuevo.
 */
class CliPuntaAPuntaTest {

    private static final String PIN_MIJA = "123456789012";
    private static final String CLAVE_MIJA = "Clave Segura 2026";
    private static final String FRASE_MIJA = "Mi Pérro Ñato corre feliz";
    private static final boolean WINDOWS_MIJA = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

    @TempDir
    Path carpetaMija;

    record Resultado(int codigoMija, String salidaMija, String erroresMija) {
    }

    private Resultado kipu(String entradaMija, String... argumentosMija) throws IOException, InterruptedException {
        List<String> comandoMija = new ArrayList<>(List.of(java(), "-cp", System.getProperty("kipu.classpath"),
                "dev.erick.cryptokipu.cli.Main"));
        comandoMija.addAll(List.of(argumentosMija));
        return lanzar(comandoMija, entradaMija);
    }

    private Resultado lanzar(List<String> comandoMija, String entradaMija) throws IOException, InterruptedException {
        Process procesoMija = new ProcessBuilder(comandoMija).directory(carpetaMija.toFile()).start();
        try (OutputStream stdinMija = procesoMija.getOutputStream()) {
            stdinMija.write(entradaMija.getBytes(StandardCharsets.UTF_8));
        }
        String salidaMija = new String(procesoMija.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String erroresMija = new String(procesoMija.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(procesoMija.waitFor(120, TimeUnit.SECONDS), "la CLI no terminó");
        return new Resultado(procesoMija.exitValue(), salidaMija, erroresMija);
    }

    private static String java() {
        return Path.of(System.getProperty("java.home"), "bin", WINDOWS_MIJA ? "java.exe" : "java").toString();
    }

    private static String lineas(String... valoresMija) {
        return String.join("\n", valoresMija) + "\n";
    }

    private static void ok(Resultado resultadoMija) {
        assertEquals(0, resultadoMija.codigoMija(), resultadoMija.erroresMija());
    }

    @Test
    void scriptCompletoComoSubproceso() throws Exception {
        Path bovedaMija = carpetaMija.resolve("prueba.kipu");
        Path llaveMija = carpetaMija.resolve("mi.kipukey");
        Path fotosMija = Files.createDirectories(carpetaMija.resolve("origen/fotos/año 2026"));
        Files.write(fotosMija.resolve("ñandú.jpg"), new java.util.Random(1).ints(200_000).collect(
                java.io.ByteArrayOutputStream::new, (bufferMija, valorMija) -> bufferMija.write(valorMija),
                (unoMija, otroMija) -> unoMija.writeBytes(otroMija.toByteArray())).toByteArray());
        Files.write(carpetaMija.resolve("origen/fotos/video.mp4"), new byte[3 * (1 << 20) + 11]);
        Files.writeString(carpetaMija.resolve("origen/nota.txt"), "contenido de la nota");

        ok(kipu("", "keyfile", "--generar", llaveMija.toString()));
        ok(kipu(lineas(PIN_MIJA, PIN_MIJA, CLAVE_MIJA, CLAVE_MIJA, FRASE_MIJA, FRASE_MIJA), "create",
                "--secretos-stdin", "--keyfile", llaveMija.toString(), "--perfil", "ligero", "--out",
                bovedaMija.toString(), "--tokens-out", "tokens.txt", carpetaMija.resolve("origen/nota.txt").toString()));

        Resultado infoMija = kipu("", "info", "--json", bovedaMija.toString());
        ok(infoMija);
        assertTrue(infoMija.salidaMija().contains("\"requiereLlave\":true"), infoMija.salidaMija());

        String credencialesMija = lineas(PIN_MIJA, CLAVE_MIJA, FRASE_MIJA);
        ok(kipu(credencialesMija, "add", "--secretos-stdin", "--keyfile", llaveMija.toString(), bovedaMija.toString(),
                carpetaMija.resolve("origen/fotos").toString()));

        Resultado listaMija = kipu(credencialesMija, "list", "--secretos-stdin", "--json", "--keyfile",
                llaveMija.toString(), bovedaMija.toString());
        ok(listaMija);
        assertTrue(listaMija.salidaMija().contains("\"ruta\":\"fotos/año 2026/ñandú.jpg\""), listaMija.salidaMija());

        Path destinoMija = carpetaMija.resolve("extraido");
        ok(kipu(credencialesMija, "extract", "--secretos-stdin", "--keyfile", llaveMija.toString(), "--todo", "--to",
                destinoMija.toString(), bovedaMija.toString()));
        assertEquals(hashes(carpetaMija.resolve("origen")), hashes(destinoMija));

        ok(kipu(credencialesMija, "verify", "--secretos-stdin", "--keyfile", llaveMija.toString(), bovedaMija.toString()));
        ok(kipu(credencialesMija, "compact", "--secretos-stdin", "--yes", "--keyfile", llaveMija.toString(),
                bovedaMija.toString()));

        // recover: token + F1, y en la misma ejecución credenciales nuevas (sin llave)
        String tokenMija = Files.readAllLines(carpetaMija.resolve("tokens.txt")).stream()
                .filter(lineaMija -> lineaMija.contains("CKP-")).findFirst().orElseThrow().substring(3);
        String nuevoPinMija = "555555555555";
        ok(kipu(lineas(tokenMija, PIN_MIJA, nuevoPinMija, nuevoPinMija, "Clave Nueva 2028", "Clave Nueva 2028",
                "frase nueva y distinta", "frase nueva y distinta"), "recover", "--secretos-stdin", "--quitar-llave",
                "--tokens-out", "tokens-nuevos.txt", bovedaMija.toString()));

        // rotate --f2 con las credenciales nuevas y abrir con el resultado
        ok(kipu(lineas(nuevoPinMija, "Clave Nueva 2028", "frase nueva y distinta", "Otra Clave 2029", "Otra Clave 2029"),
                "rotate", "--secretos-stdin", "--yes", "--f2", bovedaMija.toString()));
        Resultado finalMija = kipu(lineas(nuevoPinMija, "Otra Clave 2029", "frase nueva y distinta"), "list",
                "--secretos-stdin", bovedaMija.toString());
        ok(finalMija);
        assertTrue(finalMija.salidaMija().contains("fotos/video.mp4"));

        // códigos de error
        assertEquals(3, kipu(credencialesMija, "list", "--secretos-stdin", bovedaMija.toString()).codigoMija());
        assertEquals(2, kipu("", "list", bovedaMija.toString(), "--pin", PIN_MIJA).codigoMija());
        assertEquals(2, kipu(lineas(PIN_MIJA, PIN_MIJA, CLAVE_MIJA, CLAVE_MIJA, FRASE_MIJA, FRASE_MIJA), "create",
                "--secretos-stdin", "--sin-llave", "--out", "otra.kipu").codigoMija(), "tokens a una tubería");
        String tokenViejoMija = Files.readAllLines(carpetaMija.resolve("tokens-nuevos.txt")).stream()
                .filter(lineaMija -> lineaMija.contains("CKP-")).findFirst().orElseThrow().substring(3);
        String malEscritoMija = tokenViejoMija.substring(0, 5) + (tokenViejoMija.charAt(5) == 'A' ? 'B' : 'A')
                + tokenViejoMija.substring(6);
        assertEquals(8, kipu(lineas(malEscritoMija), "recover", "--secretos-stdin", "--quitar-llave", "--tokens-out",
                "x.txt", bovedaMija.toString()).codigoMija());
    }

    /** Fase 7/10: la CLI corre como imagen jlink (módulos explícitos: picocli y Bouncy Castle traen module-info). */
    @Test
    void funcionaComoImagenJlink() throws Exception {
        List<String> rutaModulosMija = Stream.of(System.getProperty("kipu.classpath").split(File.pathSeparator))
                .filter(elementoMija -> Files.exists(Path.of(elementoMija))).toList();
        Path imagenMija = carpetaMija.resolve("imagen");
        Path jlinkMija = Path.of(System.getProperty("java.home"), "bin", WINDOWS_MIJA ? "jlink.exe" : "jlink");
        Resultado enlazadoMija = lanzar(List.of(jlinkMija.toString(), "--module-path",
                String.join(File.pathSeparator, rutaModulosMija), "--add-modules", "dev.erick.cryptokipu.cli",
                "--ignore-signing-information", "--strip-debug", "--no-header-files", "--no-man-pages",
                "--output", imagenMija.toString()), "");
        assertEquals(0, enlazadoMija.codigoMija(), enlazadoMija.erroresMija() + enlazadoMija.salidaMija());

        Path javaImagenMija = imagenMija.resolve("bin").resolve(WINDOWS_MIJA ? "java.exe" : "java");
        Resultado versionMija = lanzar(List.of(javaImagenMija.toString(), "-m",
                "dev.erick.cryptokipu.cli/dev.erick.cryptokipu.cli.Main", "--version"), "");
        assertEquals(0, versionMija.codigoMija(), versionMija.erroresMija());
        assertTrue(versionMija.salidaMija().contains("CryptoKipu"));

        Path llaveMija = carpetaMija.resolve("k.kipukey");
        assertEquals(0, lanzar(List.of(javaImagenMija.toString(), "-m",
                "dev.erick.cryptokipu.cli/dev.erick.cryptokipu.cli.Main", "keyfile", "--generar", llaveMija.toString()),
                "").codigoMija());
        assertEquals(64, Files.size(llaveMija));
    }

    /** SIGTERM (como Ctrl+C) a mitad de agregar: código 130 y la bóveda queda igual. Windows no envía señales. */
    @Test
    void senalDeTerminacionCancelaSinEscribirElFooter() throws Exception {
        assumeFalse(WINDOWS_MIJA, "En Windows Process.destroy() termina el proceso sin ejecutar los shutdown hooks");
        Path bovedaMija = carpetaMija.resolve("s.kipu");
        ok(kipu(lineas(PIN_MIJA, PIN_MIJA, CLAVE_MIJA, CLAVE_MIJA, FRASE_MIJA, FRASE_MIJA), "create", "--secretos-stdin",
                "--sin-llave", "--perfil", "ligero", "--out", bovedaMija.toString(), "--tokens-out", "t.txt"));
        Path grandeMija = carpetaMija.resolve("grande.bin");
        try (OutputStream salidaMija = Files.newOutputStream(grandeMija)) {
            byte[] bloqueMija = new byte[1 << 20];
            for (int indiceMija = 0; indiceMija < 400; indiceMija++) {
                salidaMija.write(bloqueMija);
            }
        }
        byte[] antesMija = Files.readAllBytes(bovedaMija);

        Process procesoMija = new ProcessBuilder(java(), "-cp", System.getProperty("kipu.classpath"),
                "dev.erick.cryptokipu.cli.Main", "add", "--secretos-stdin", bovedaMija.toString(), grandeMija.toString())
                .directory(carpetaMija.toFile()).start();
        try (OutputStream stdinMija = procesoMija.getOutputStream()) {
            stdinMija.write(lineas(PIN_MIJA, CLAVE_MIJA, FRASE_MIJA).getBytes(StandardCharsets.UTF_8));
        }
        esperarProgreso(procesoMija.getErrorStream());
        procesoMija.destroy(); // SIGTERM
        assertTrue(procesoMija.waitFor(60, TimeUnit.SECONDS));
        assertEquals(130, procesoMija.exitValue());
        assertTrue(java.util.Arrays.equals(antesMija, Files.readAllBytes(bovedaMija)), "sin footer nuevo");
    }

    private static void esperarProgreso(InputStream erroresMija) throws IOException {
        StringBuilder vistoMija = new StringBuilder();
        int caracterMija;
        while ((caracterMija = erroresMija.read()) != -1) {
            vistoMija.append((char) caracterMija);
            if (vistoMija.indexOf("Progreso: 10%") >= 0) {
                return;
            }
        }
    }

    private static Map<String, String> hashes(Path raizMija) throws IOException, NoSuchAlgorithmException {
        Map<String, String> resultadoMija = new TreeMap<>();
        try (Stream<Path> archivosMija = Files.walk(raizMija)) {
            for (Path archivoMija : archivosMija.filter(Files::isRegularFile).toList()) {
                resultadoMija.put(raizMija.relativize(archivoMija).toString().replace('\\', '/'),
                        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(Files.readAllBytes(archivoMija))));
            }
        }
        return resultadoMija;
    }
}
