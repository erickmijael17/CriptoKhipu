package dev.erick.cryptokipu.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.app.casos.DesbloquearBoveda;
import dev.erick.cryptokipu.cli.comandos.Cli;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.crypto.FuenteAleatoria;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import dev.erick.cryptokipu.infra.configuracion.PreferenciasStore;
import dev.erick.cryptokipu.infra.storage.ContenedorKipu;
import dev.erick.cryptokipu.infra.storage.InfoDestino;
import dev.erick.cryptokipu.infra.storage.Interruptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** La CLI ejecutada en el mismo proceso, con stdin, stdout, terminal y cancelación sustituidos. */
class CliEnProcesoTest {

    static final String PIN_MIJA = "123456789012";
    static final String CLAVE_MIJA = "Clave Segura 2026";
    static final String FRASE_MIJA = "mi perro nato corre feliz";

    @TempDir
    Path carpetaMija;

    private Path bovedaMija;
    private Contenedor appMija;

    record Ejecucion(int codigoMija, String salidaMija, String erroresMija) {
    }

    @BeforeEach
    void preparar() {
        bovedaMija = carpetaMija.resolve("b.kipu");
        appMija = new Contenedor(ContenedorKipu.Opciones.porDefecto(), PreferenciasStore.portatil(carpetaMija));
    }

    Ejecucion kipu(String entradaMija, Charset charsetEntradaMija, boolean ttyMija, Contenedor contenedorMija,
            Cancelacion cancelacionMija, String... argumentosMija) {
        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream();
        ByteArrayOutputStream erroresMija = new ByteArrayOutputStream();
        PrintStream erroresImpresosMija = new PrintStream(erroresMija, true, StandardCharsets.UTF_8);
        Entorno entornoMija = new Entorno(contenedorMija, new PrintStream(salidaMija, true, StandardCharsets.UTF_8),
                erroresImpresosMija, new ByteArrayInputStream(entradaMija.getBytes(charsetEntradaMija)), Optional.empty(),
                () -> ttyMija, cancelacionMija == null ? new Cancelacion(erroresImpresosMija) : cancelacionMija);
        int codigoMija = Cli.ejecutar(argumentosMija, entornoMija);
        return new Ejecucion(codigoMija, salidaMija.toString(StandardCharsets.UTF_8),
                erroresMija.toString(StandardCharsets.UTF_8));
    }

    Ejecucion kipu(String entradaMija, String... argumentosMija) {
        return kipu(entradaMija, StandardCharsets.UTF_8, false, appMija, null, argumentosMija);
    }

    static String lineas(String... valoresMija) {
        return String.join("\n", valoresMija) + "\n";
    }

    static String credencialesDobles() {
        return lineas(PIN_MIJA, PIN_MIJA, CLAVE_MIJA, CLAVE_MIJA, FRASE_MIJA, FRASE_MIJA);
    }

    static String credenciales() {
        return lineas(PIN_MIJA, CLAVE_MIJA, FRASE_MIJA);
    }

    private void crear(String... extrasMija) {
        List<String> argumentosMija = new ArrayList<>(List.of("create", "--secretos-stdin", "--sin-llave",
                "--perfil", "ligero", "--out", bovedaMija.toString(), "--tokens-out",
                carpetaMija.resolve("tokens.txt").toString()));
        argumentosMija.addAll(List.of(extrasMija));
        Ejecucion ejecucionMija = kipu(credencialesDobles(), argumentosMija.toArray(String[]::new));
        assertEquals(0, ejecucionMija.codigoMija(), ejecucionMija.erroresMija());
    }

    // ------------------------------------------------------------------ secretos

    @Test
    void ningunaOpcionAceptaUnSecretoComoArgumento() {
        assertEquals(CodigosSalida.USO_MIJA, kipu("", "list", "b.kipu", "--pin", PIN_MIJA).codigoMija());
        assertEquals(CodigosSalida.USO_MIJA, kipu("", "rotate", "b.kipu", "--f1", PIN_MIJA).codigoMija(),
                "--f1 es un interruptor sin valor");
        assertEquals(CodigosSalida.USO_MIJA, kipu("", "create", "--out", "x.kipu", "--sin-llave",
                "--password=" + CLAVE_MIJA).codigoMija());
    }

    /**
     * Guardia estructural: en todos los subcomandos, toda opción que acepta un valor está en esta lista de opciones
     * no secretas. Agregar una opción con valor (o dar aridad a --f1/--f2/--f3) obliga a revisar esta lista.
     */
    @Test
    void soloOpcionesNoSecretasAceptanValor() {
        java.util.Set<String> permitidasMija = java.util.Set.of("--out", "--keyfile", "--perfil", "--umbral",
                "--tokens-out", "--si-existe", "--ruta", "--to", "--llave", "--generar", "--secretos-charset");
        picocli.CommandLine raizMija = Cli.lineaDeComandos(new Entorno(appMija, System.out, System.err,
                new ByteArrayInputStream(new byte[0]), Optional.empty(), () -> false, new Cancelacion(System.err)));
        List<picocli.CommandLine> comandosMija = new ArrayList<>(raizMija.getSubcommands().values());
        comandosMija.add(raizMija);
        for (picocli.CommandLine comandoMija : comandosMija) {
            for (picocli.CommandLine.Model.OptionSpec opcionMija : comandoMija.getCommandSpec().options()) {
                if (opcionMija.arity().max() > 0 && !opcionMija.usageHelp() && !opcionMija.versionHelp()) {
                    assertTrue(permitidasMija.contains(opcionMija.longestName()),
                            comandoMija.getCommandName() + " " + opcionMija.longestName() + " acepta un valor");
                }
            }
        }
    }

    @Test
    void sinConsolaNiSecretosStdinEsErrorDeUso() throws IOException {
        crear();
        Ejecucion ejecucionMija = kipu(credenciales(), "list", bovedaMija.toString());
        assertEquals(CodigosSalida.USO_MIJA, ejecucionMija.codigoMija());
        assertTrue(ejecucionMija.erroresMija().contains("--secretos-stdin"));
    }

    @Test
    void laConfirmacionDeUnSecretoNuevoDebeCoincidir() {
        Ejecucion ejecucionMija = kipu(lineas(PIN_MIJA, "999999999999"), "create", "--secretos-stdin", "--sin-llave",
                "--out", bovedaMija.toString(), "--tokens-out", carpetaMija.resolve("t.txt").toString());
        assertEquals(CodigosSalida.USO_MIJA, ejecucionMija.codigoMija());
        assertFalse(Files.exists(bovedaMija));
    }

    /** Una frase con ñ y tildes ingresada en cp850 (consola de Windows) abre igual que desde la interfaz. */
    @Test
    void laFraseEnCp850AbreIgualQueDesdeLaInterfaz() throws IOException {
        String fraseMija = "Canción del Ñandú feliz";
        Charset cp850Mija = Charset.forName("IBM850");
        Ejecucion ejecucionMija = kipu(lineas(PIN_MIJA, PIN_MIJA, CLAVE_MIJA, CLAVE_MIJA, fraseMija, fraseMija),
                cp850Mija, false, appMija, null, "create", "--secretos-stdin", "--secretos-charset", "IBM850",
                "--sin-llave", "--perfil", "ligero", "--out", bovedaMija.toString(), "--tokens-out",
                carpetaMija.resolve("t.txt").toString());
        assertEquals(0, ejecucionMija.codigoMija(), ejecucionMija.erroresMija());

        try (Credenciales desdeUiMija = new Credenciales(PIN_MIJA.toCharArray(), CLAVE_MIJA.toCharArray(),
                fraseMija.toCharArray(), null)) {
            appMija.desbloquearBoveda().ejecutar(new DesbloquearBoveda.Comando(bovedaMija, desdeUiMija)).close();
        }
    }

    // ------------------------------------------------------------------ tokens

    @Test
    void sinTerminalNiTokensOutLosTokensNoSeImprimen() {
        Ejecucion ejecucionMija = kipu(credencialesDobles(), "create", "--secretos-stdin", "--sin-llave",
                "--perfil", "ligero", "--out", bovedaMija.toString());
        assertEquals(CodigosSalida.USO_MIJA, ejecucionMija.codigoMija());
        assertFalse(ejecucionMija.salidaMija().contains("CKP-"));
        assertFalse(Files.exists(bovedaMija), "se comprueba antes de crear nada");
    }

    @Test
    void conTerminalLosTokensVanAStdout() {
        Ejecucion ejecucionMija = kipu(credencialesDobles(), StandardCharsets.UTF_8, true, appMija, null, "create",
                "--secretos-stdin", "--sin-llave", "--perfil", "ligero", "--out", bovedaMija.toString());
        assertEquals(0, ejecucionMija.codigoMija(), ejecucionMija.erroresMija());
        assertEquals(6, ejecucionMija.salidaMija().lines().filter(lineaMija -> lineaMija.startsWith("CKP-")).count());
    }

    @Test
    void tokensOutNuncaSobrescribe() throws IOException {
        Path existenteMija = Files.writeString(carpetaMija.resolve("ya.txt"), "importante");
        Ejecucion ejecucionMija = kipu(credencialesDobles(), "create", "--secretos-stdin", "--sin-llave",
                "--out", bovedaMija.toString(), "--tokens-out", existenteMija.toString());
        assertEquals(CodigosSalida.USO_MIJA, ejecucionMija.codigoMija());
        assertEquals("importante", Files.readString(existenteMija));
    }

    // ------------------------------------------------------------------ códigos de salida reales

    @Test
    void credencialesIncorrectasDanTres() throws IOException {
        crear();
        Ejecucion ejecucionMija = kipu(lineas(PIN_MIJA, "Clave Mala 2026", FRASE_MIJA), "list", "--secretos-stdin",
                bovedaMija.toString());
        assertEquals(3, ejecucionMija.codigoMija());
        assertTrue(ejecucionMija.erroresMija().contains("Credenciales incorrectas."));
    }

    @Test
    void tokenMalEscritoDaOchoAntesDeTocarLaBoveda() throws IOException {
        crear();
        String tokenMija = Files.readAllLines(carpetaMija.resolve("tokens.txt")).stream()
                .filter(lineaMija -> lineaMija.contains("CKP-")).findFirst().orElseThrow().substring(3);
        String alteradoMija = tokenMija.substring(0, 6) + (tokenMija.charAt(6) == 'A' ? 'B' : 'A') + tokenMija.substring(7);
        byte[] antesMija = Files.readAllBytes(bovedaMija);
        Ejecucion ejecucionMija = kipu(lineas(alteradoMija), "recover", "--secretos-stdin", "--quitar-llave",
                "--tokens-out", carpetaMija.resolve("nuevos.txt").toString(), bovedaMija.toString());
        assertEquals(8, ejecucionMija.codigoMija(), ejecucionMija.erroresMija());
        assertTrue(java.util.Arrays.equals(antesMija, Files.readAllBytes(bovedaMija)));
    }

    @Test
    void bovedaEnUsoDaCinco() throws IOException {
        crear();
        ContenedorKipu abiertaMija = ContenedorKipu.abrir(bovedaMija, ContenedorKipu.Opciones.porDefecto());
        try {
            assertEquals(5, kipu(credenciales(), "list", "--secretos-stdin", bovedaMija.toString()).codigoMija());
        } finally {
            abiertaMija.close();
        }
    }

    @Test
    void autodestruccionDaSeis() throws IOException {
        crear("--umbral", "1");
        assertEquals(6, kipu(lineas(PIN_MIJA, CLAVE_MIJA, "frase equivocada aqui"), "list", "--secretos-stdin",
                bovedaMija.toString()).codigoMija());
        assertEquals(6, kipu("", "info", bovedaMija.toString()).codigoMija());
    }

    @Test
    void sinEspacioDaSiete() throws IOException {
        crear();
        Path archivoMija = Files.write(carpetaMija.resolve("a.bin"), new byte[100_000]);
        Contenedor llenoMija = new Contenedor(new ContenedorKipu.Opciones(FuenteAleatoria.delSistema(),
                _ -> new InfoDestino("NTFS", 1000), Interruptor.NINGUNO_MIJA), PreferenciasStore.portatil(carpetaMija));
        Ejecucion ejecucionMija = kipu(credenciales(), StandardCharsets.UTF_8, false, llenoMija, null, "add",
                "--secretos-stdin", bovedaMija.toString(), archivoMija.toString());
        assertEquals(7, ejecucionMija.codigoMija(), ejecucionMija.erroresMija());
    }

    @Test
    void integridadFallidaDaCuatro() throws IOException {
        crear();
        Path archivoMija = Files.write(carpetaMija.resolve("a.bin"), FuenteAleatoria.delSistema().bytes(5000));
        assertEquals(0, kipu(credenciales(), "add", "--secretos-stdin", bovedaMija.toString(), archivoMija.toString())
                .codigoMija());
        try (RandomAccessFile archivoBovedaMija = new RandomAccessFile(bovedaMija.toFile(), "rw")) {
            long posicionMija = 32 * 1024 + 2000; // dentro del primer blob
            archivoBovedaMija.seek(posicionMija);
            int valorMija = archivoBovedaMija.read();
            archivoBovedaMija.seek(posicionMija);
            archivoBovedaMija.write(valorMija ^ 1);
        }
        Ejecucion ejecucionMija = kipu(credenciales(), "verify", "--secretos-stdin", "--json", bovedaMija.toString());
        assertEquals(4, ejecucionMija.codigoMija());
        assertTrue(ejecucionMija.salidaMija().contains("\"estado\":\"CORRUPTO\""));
    }

    /** Ctrl+C a mitad de agregar: 130, sin footer nuevo (la bóveda queda igual). */
    @Test
    void cancelarAMitadDeAgregarDaCientoTreintaYNoEscribeElFooter() throws IOException {
        crear();
        Path grandeMija = Files.write(carpetaMija.resolve("grande.bin"), new byte[6 * (1 << 20)]);
        byte[] antesMija = Files.readAllBytes(bovedaMija);
        PrintStream nadaMija = new PrintStream(PrintStream.nullOutputStream());
        Cancelacion ctrlCMija = new Cancelacion(nadaMija) {
            @Override
            public synchronized void avanzar(long hechoMija, long totalMija) {
                if (hechoMija > 2 * (1 << 20)) {
                    cancelar(); // lo que hace el shutdown hook al recibir Ctrl+C
                }
            }
        };
        Ejecucion ejecucionMija = kipu(credenciales(), StandardCharsets.UTF_8, false, appMija, ctrlCMija, "add",
                "--secretos-stdin", bovedaMija.toString(), grandeMija.toString());
        assertEquals(130, ejecucionMija.codigoMija(), ejecucionMija.erroresMija());
        assertTrue(java.util.Arrays.equals(antesMija, Files.readAllBytes(bovedaMija)), "sin footer nuevo");
        assertEquals(0, kipu(credenciales(), "list", "--secretos-stdin", bovedaMija.toString()).codigoMija(),
                "la sesión se cerró y soltó el bloqueo");
    }

    // ------------------------------------------------------------------ confirmaciones

    @Test
    void compactarPideConfirmacionSalvoConYes() throws IOException {
        crear();
        assertEquals(CodigosSalida.USO_MIJA, kipu(lineas("n"), "compact", "--secretos-stdin", bovedaMija.toString())
                .codigoMija());
        assertEquals(0, kipu(lineas("s", PIN_MIJA, CLAVE_MIJA, FRASE_MIJA), "compact", "--secretos-stdin",
                bovedaMija.toString()).codigoMija());
        assertEquals(0, kipu(credenciales(), "compact", "--secretos-stdin", "--yes", bovedaMija.toString()).codigoMija());
    }

    @Test
    void todosLosErroresTienenUnCodigoDistintoDeCero() {
        List<ErrorBoveda> erroresMija = List.of(new ErrorBoveda.CredencialesIncorrectas(),
                new ErrorBoveda.IntegridadFallida(ErrorBoveda.IntegridadFallida.Zona.BLOQUE),
                new ErrorBoveda.FormatoNoSoportado(9), new ErrorBoveda.ParametrosFueraDeRango("x"),
                new ErrorBoveda.EspacioInsuficiente(2, 1), new ErrorBoveda.LimiteSistemaArchivos("FAT32", 1),
                new ErrorBoveda.BovedaEnUso(), new ErrorBoveda.BovedaDestruida(), new ErrorBoveda.MemoriaInsuficiente(2, 1),
                new ErrorBoveda.OperacionCancelada(), new ErrorBoveda.TokenMalEscrito(), new ErrorBoveda.RotacionPendiente());
        List<Integer> esperadosMija = List.of(3, 4, 4, 4, 7, 7, 5, 6, 1, 130, 8, 9);
        for (int indiceMija = 0; indiceMija < erroresMija.size(); indiceMija++) {
            assertEquals(esperadosMija.get(indiceMija), CodigosSalida.de(erroresMija.get(indiceMija)),
                    erroresMija.get(indiceMija).toString());
        }
    }
}
