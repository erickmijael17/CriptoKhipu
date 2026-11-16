package dev.erick.cryptokipu.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.cli.comandos.Cli;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import dev.erick.cryptokipu.infra.configuracion.PreferenciasStore;
import dev.erick.cryptokipu.infra.storage.ContenedorKipu;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fase 8: en la consola interactiva, tras cada credencial incorrecta hay un retardo creciente (0, 0, 2, 4, 8, 16 y
 * luego 30 s) con cuenta regresiva en stderr. Con {@code --secretos-stdin} no hay reintento: un proceso, un intento.
 */
class CliRetardoIntentosTest {

    private static final String PIN_MIJA = "123456789012";
    private static final String CLAVE_MIJA = "Clave Segura 2026";
    private static final String FRASE_MIJA = "mi perro nato corre feliz";

    @TempDir
    Path carpetaMija;

    private Path bovedaMija;
    private Contenedor appMija;

    /** Lector de consola que entrega los secretos de un guion: tres líneas (F1, F2, F3) por intento. */
    private static final class LectorGuionado implements LectorSecretos {
        private final Deque<String> valoresMija = new ArrayDeque<>();

        void intento(String pinMija, String claveMija, String fraseMija) {
            valoresMija.add(pinMija);
            valoresMija.add(claveMija);
            valoresMija.add(fraseMija);
        }

        @Override
        public char[] leerSecreto(String etiquetaMija) {
            return valoresMija.removeFirst().toCharArray();
        }

        @Override
        public char[] leerLinea(String etiquetaMija) {
            return valoresMija.removeFirst().toCharArray();
        }
    }

    record Salida(int codigoMija, String erroresMija) {
    }

    @BeforeEach
    void crearBoveda() {
        bovedaMija = carpetaMija.resolve("b.kipu");
        appMija = new Contenedor(ContenedorKipu.Opciones.porDefecto(), PreferenciasStore.portatil(carpetaMija));
        ByteArrayOutputStream erroresMija = new ByteArrayOutputStream();
        PrintStream impresosMija = new PrintStream(erroresMija, true, StandardCharsets.UTF_8);
        String entradaMija = String.join("\n", PIN_MIJA, PIN_MIJA, CLAVE_MIJA, CLAVE_MIJA, FRASE_MIJA, FRASE_MIJA) + "\n";
        Entorno entornoMija = new Entorno(appMija, new PrintStream(new ByteArrayOutputStream()), impresosMija,
                new ByteArrayInputStream(entradaMija.getBytes(StandardCharsets.UTF_8)), Optional.empty(), () -> false,
                new Cancelacion(impresosMija));
        int codigoMija = Cli.ejecutar(new String[] {"create", "--secretos-stdin", "--sin-llave", "--perfil", "ligero",
                "--out", bovedaMija.toString(), "--tokens-out", carpetaMija.resolve("t.txt").toString()}, entornoMija);
        assertEquals(0, codigoMija, erroresMija.toString(StandardCharsets.UTF_8));
    }

    private Salida listar(LectorGuionado lectorMija, List<Duration> pausasMija) {
        ByteArrayOutputStream erroresMija = new ByteArrayOutputStream();
        PrintStream impresosMija = new PrintStream(erroresMija, true, StandardCharsets.UTF_8);
        Entorno entornoMija = new Entorno(appMija, new PrintStream(new ByteArrayOutputStream()), impresosMija,
                new ByteArrayInputStream(new byte[0]), Optional.of(lectorMija), () -> true,
                new Cancelacion(impresosMija), pausasMija::add);
        int codigoMija = Cli.ejecutar(new String[] {"list", bovedaMija.toString()}, entornoMija);
        return new Salida(codigoMija, erroresMija.toString(StandardCharsets.UTF_8));
    }

    @Test
    void trasTresFallosSeEsperaDosSegundosYLuegoSeAbre() {
        LectorGuionado lectorMija = new LectorGuionado();
        lectorMija.intento("999999999999", CLAVE_MIJA, FRASE_MIJA);   // fallo 1: sin espera
        lectorMija.intento(PIN_MIJA, "otra clave 1A", FRASE_MIJA);    // fallo 2: sin espera
        lectorMija.intento(PIN_MIJA, CLAVE_MIJA, "otra frase nueva"); // fallo 3: 2 s
        lectorMija.intento(PIN_MIJA, CLAVE_MIJA, FRASE_MIJA);         // correcto
        List<Duration> pausasMija = new ArrayList<>();

        Salida salidaMija = listar(lectorMija, pausasMija);

        assertEquals(0, salidaMija.codigoMija(), salidaMija.erroresMija());
        assertEquals(2, pausasMija.size(), "solo el tercer fallo espera: 2 pausas de 1 s");
        assertTrue(pausasMija.stream().allMatch(pausaMija -> pausaMija.equals(Duration.ofSeconds(1))));
        assertTrue(salidaMija.erroresMija().contains("Espera 2 s"), salidaMija.erroresMija());
        assertTrue(salidaMija.erroresMija().contains("Espera 1 s"), salidaMija.erroresMija());
    }

    @Test
    void laEsperaCreceConCadaFalloSeguido() {
        LectorGuionado lectorMija = new LectorGuionado();
        for (int indiceMija = 0; indiceMija < 5; indiceMija++) {
            lectorMija.intento("999999999999", CLAVE_MIJA, FRASE_MIJA);
        }
        lectorMija.intento(PIN_MIJA, CLAVE_MIJA, FRASE_MIJA);
        List<Duration> pausasMija = new ArrayList<>();

        Salida salidaMija = listar(lectorMija, pausasMija);

        assertEquals(0, salidaMija.codigoMija(), salidaMija.erroresMija());
        // Fallos 1..5 → 0 + 0 + 2 + 4 + 8 = 14 s en pausas de 1 s.
        assertEquals(14, pausasMija.size());
    }

    @Test
    void elMensajeNuncaDiceQueFactorFallo() {
        LectorGuionado lectorMija = new LectorGuionado();
        lectorMija.intento("999999999999", CLAVE_MIJA, FRASE_MIJA);
        lectorMija.intento(PIN_MIJA, CLAVE_MIJA, FRASE_MIJA);

        Salida salidaMija = listar(lectorMija, new ArrayList<>());

        assertEquals(0, salidaMija.codigoMija(), salidaMija.erroresMija());
        String minusculasMija = salidaMija.erroresMija().toLowerCase(java.util.Locale.ROOT);
        assertTrue(minusculasMija.contains("credenciales incorrectas"));
        assertFalse(minusculasMija.contains("f1") && minusculasMija.contains("incorrecta"),
                "no debe indicar cuál factor falló");
    }

    @Test
    void conSecretosStdinNoHayReintentoNiEspera() throws Exception {
        ByteArrayOutputStream erroresMija = new ByteArrayOutputStream();
        PrintStream impresosMija = new PrintStream(erroresMija, true, StandardCharsets.UTF_8);
        List<Duration> pausasMija = new ArrayList<>();
        String entradaMija = String.join("\n", "999999999999", CLAVE_MIJA, FRASE_MIJA) + "\n";
        Entorno entornoMija = new Entorno(appMija, new PrintStream(new ByteArrayOutputStream()), impresosMija,
                new ByteArrayInputStream(entradaMija.getBytes(StandardCharsets.UTF_8)), Optional.empty(), () -> false,
                new Cancelacion(impresosMija), pausasMija::add);

        int codigoMija = Cli.ejecutar(new String[] {"list", "--secretos-stdin", bovedaMija.toString()}, entornoMija);

        assertEquals(CodigosSalida.CREDENCIALES_MIJA, codigoMija);
        assertTrue(pausasMija.isEmpty());
        assertTrue(Files.exists(bovedaMija));
    }
}
