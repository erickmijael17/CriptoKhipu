package dev.erick.cryptokipu.infra.casos;

import static dev.erick.cryptokipu.infra.casos.Banco.CLAVE_MIJA;
import static dev.erick.cryptokipu.infra.casos.Banco.FRASE_MIJA;
import static dev.erick.cryptokipu.infra.casos.Banco.PIN_MIJA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.app.casos.AbrirEnMemoria;
import dev.erick.cryptokipu.app.casos.AgregarArchivos;
import dev.erick.cryptokipu.app.casos.Compactar;
import dev.erick.cryptokipu.app.casos.EliminarArchivo;
import dev.erick.cryptokipu.app.casos.ExtraerArchivo;
import dev.erick.cryptokipu.app.casos.InspeccionarBoveda;
import dev.erick.cryptokipu.app.casos.PoliticaColision;
import dev.erick.cryptokipu.app.casos.RegenerarTokens;
import dev.erick.cryptokipu.app.casos.RotarCredenciales;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.app.casos.VerificarIntegridad;
import dev.erick.cryptokipu.app.mensajes.CatalogoMensajes;
import dev.erick.cryptokipu.core.auth.CambioCredenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fase 8, punto 8: ejecuta el flujo completo (crear → abrir → agregar → extraer → token → rotar, más los caminos de
 * error) con el registro en DEBUG ({@link CapturaDeLogs} acepta todos los niveles), captura TODO lo que sale por el
 * registro, por stdout, por stderr y por los mensajes de las excepciones, y comprueba que no aparece ninguna
 * contraseña de prueba, ningún token, ningún nombre de archivo interno ni ningún volcado de bytes.
 */
class AuditoriaDeFugasTest {

    private static final String NUEVO_PIN_MIJA = "906523417805";
    private static final String NUEVA_CLAVE_MIJA = "Otra Clave Distinta 2027";
    private static final String NUEVA_FRASE_MIJA = "el zorro cruza el rio";
    private static final String NOMBRE_INTERNO_1_MIJA = "proyecto-reservado/contrato-confidencial-zorro.pdf";
    private static final String NOMBRE_INTERNO_2_MIJA = "proyecto-reservado/diario-intimo-lince.txt";
    private static final String FRAGMENTO_1_MIJA = "contrato-confidencial-zorro";
    private static final String FRAGMENTO_2_MIJA = "diario-intimo-lince";
    private static final String CARPETA_INTERNA_MIJA = "proyecto-reservado";
    private static final ReporteProgreso NADA_MIJA = ReporteProgreso.NINGUNO_MIJA;

    @TempDir
    Path carpetaMija;

    private Banco bancoMija;
    private PrintStream salidaOriginalMija;
    private PrintStream erroresOriginalMija;
    private final ByteArrayOutputStream salidaCapturadaMija = new ByteArrayOutputStream();
    private final ByteArrayOutputStream erroresCapturadosMija = new ByteArrayOutputStream();
    private final List<String> mensajesDeErrorMija = new ArrayList<>();
    private final List<String> secretosMija = new ArrayList<>();

    @BeforeEach
    void capturar() throws IOException {
        bancoMija = new Banco(carpetaMija);
        CapturaDeLogs.limpiar();
        salidaOriginalMija = System.out;
        erroresOriginalMija = System.err;
        System.setOut(new PrintStream(salidaCapturadaMija, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(erroresCapturadosMija, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restaurar() {
        System.setOut(salidaOriginalMija);
        System.setErr(erroresOriginalMija);
    }

    private void anotarError(Throwable errorMija) {
        mensajesDeErrorMija.add(errorMija.toString() + " | " + errorMija.getMessage()
                + (errorMija instanceof ExcepcionBoveda bovedaMija ? " | " + CatalogoMensajes.mensaje(bovedaMija.error())
                        : ""));
    }

    @Test
    void elFlujoCompletoNoFiltraSecretosNiNombresNiVolcados() throws Exception {
        // ---- crear (umbral 3, para recorrer también la autodestrucción) ----
        List<TokenRecuperacion> tokensMija = bancoMija.crear(3);
        for (TokenRecuperacion tokenMija : tokensMija) {
            char[] formateadoMija = tokenMija.formatear();
            secretosMija.add(new String(formateadoMija));
            secretosMija.add(new String(formateadoMija).replace("-", ""));
        }
        secretosMija.addAll(List.of(PIN_MIJA, CLAVE_MIJA, FRASE_MIJA, NUEVO_PIN_MIJA, NUEVA_CLAVE_MIJA, NUEVA_FRASE_MIJA));

        Path origen1Mija = bancoMija.archivo(NOMBRE_INTERNO_1_MIJA, 70_000);
        bancoMija.archivo(NOMBRE_INTERNO_2_MIJA, 2_000_000);

        // ---- abrir → agregar → extraer → abrir en memoria → verificar → eliminar → compactar ----
        Path destinoMija = carpetaMija.resolve("extraido");
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            AgregarArchivos.Resultado agregadoMija = bancoMija.appMija.agregarArchivos().ejecutar(
                    new AgregarArchivos.Comando(sesionMija, List.of(origen1Mija.getParent()), "",
                            PoliticaColision.OMITIR, NADA_MIJA));
            assertEquals(2, agregadoMija.agregadosMija().size());
            bancoMija.appMija.extraerArchivo().ejecutar(new ExtraerArchivo.Comando(sesionMija, List.of(), destinoMija,
                    PoliticaColision.OMITIR, NADA_MIJA));
            EntradaManifest primeraMija = sesionMija.manifest().entradasMija().getFirst();
            try (var canalMija = bancoMija.appMija.abrirEnMemoria().ejecutar(
                    new AbrirEnMemoria.Comando(sesionMija, primeraMija.fileIdMija()))) {
                assertTrue(canalMija.isOpen());
            }
            assertTrue(bancoMija.appMija.verificarIntegridad()
                    .ejecutar(new VerificarIntegridad.Comando(sesionMija, NADA_MIJA)).todoCorrecto());
            bancoMija.appMija.eliminarArchivo().ejecutar(new EliminarArchivo.Comando(sesionMija,
                    sesionMija.manifest().entradasMija().get(1).fileIdMija(), false));
            bancoMija.appMija.compactar().ejecutar(new Compactar.Comando(sesionMija));
            // Al intentar agregar una ruta inexistente también hay un camino de error con nombres.
            try {
                bancoMija.appMija.agregarArchivos().ejecutar(new AgregarArchivos.Comando(sesionMija,
                        List.of(carpetaMija.resolve("no-existe-" + FRAGMENTO_1_MIJA)), "", PoliticaColision.OMITIR,
                        NADA_MIJA));
            } catch (IOException | RuntimeException errorMija) {
                anotarError(errorMija);
            }
        }
        bancoMija.appMija.inspeccionarBoveda().ejecutar(new InspeccionarBoveda.Comando(bancoMija.bovedaMija));

        // ---- errores de credenciales: cada factor y un token mal escrito ----
        for (var malasMija : List.of(
                Banco.credenciales("999999999999", CLAVE_MIJA, FRASE_MIJA, bancoMija.llaveMija),
                Banco.credenciales(PIN_MIJA, "Clave Equivocada 2026", FRASE_MIJA, bancoMija.llaveMija))) {
            try {
                bancoMija.abrir(malasMija).close();
            } catch (ExcepcionBoveda errorMija) {
                anotarError(errorMija);
            }
            malasMija.close();
        }
        try {
            bancoMija.recuperar(new TokenRecuperacion("CKPAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".toCharArray()), PIN_MIJA);
        } catch (RuntimeException | IOException errorMija) {
            anotarError(errorMija);
        }

        // ---- token: recuperar → rotación obligatoria → rotar → regenerar tokens ----
        try (SesionBoveda sesionMija = bancoMija.recuperar(tokensMija.getFirst(), PIN_MIJA)) {
            assertTrue(sesionMija.rotacionObligatoria());
            assertThrows(ExcepcionBoveda.class, sesionMija::manifest); // RotacionPendiente
            // Tras usar un token no se conocen las credenciales vigentes: se eligen los tres factores nuevos
            // (y, como tampoco se tiene la llave, se quita).
            var nuevosMija = bancoMija.appMija.rotarCredenciales().ejecutar(new RotarCredenciales.Comando(sesionMija,
                    Optional.empty(), new CambioCredenciales(Optional.of(NUEVO_PIN_MIJA.toCharArray()),
                            Optional.of(NUEVA_CLAVE_MIJA.toCharArray()), Optional.of(NUEVA_FRASE_MIJA.toCharArray()),
                            new CambioCredenciales.CambioLlave.Quitar(), OptionalInt.empty())));
            for (TokenRecuperacion tokenMija : nuevosMija.tokensNuevosMija()) {
                char[] formateadoMija = tokenMija.formatear();
                secretosMija.add(new String(formateadoMija));
                secretosMija.add(new String(formateadoMija).replace("-", ""));
            }
        }
        try (SesionBoveda sesionMija = bancoMija.abrir(Banco.credenciales(NUEVO_PIN_MIJA, NUEVA_CLAVE_MIJA,
                NUEVA_FRASE_MIJA, null))) {
            var regeneradosMija = bancoMija.appMija.regenerarTokens().ejecutar(new RegenerarTokens.Comando(sesionMija,
                    Banco.credenciales(NUEVO_PIN_MIJA, NUEVA_CLAVE_MIJA, NUEVA_FRASE_MIJA, null)));
            for (TokenRecuperacion tokenMija : regeneradosMija.tokensMija()) {
                char[] formateadoMija = tokenMija.formatear();
                secretosMija.add(new String(formateadoMija));
                secretosMija.add(new String(formateadoMija).replace("-", ""));
            }
        }

        // ---- autodestrucción: tres intentos con la clave vieja (que ya no sirve) destruyen la bóveda ----
        for (int intentoMija = 0; intentoMija < 3; intentoMija++) {
            try {
                bancoMija.abrir().close();
            } catch (ExcepcionBoveda errorMija) {
                anotarError(errorMija);
            }
        }
        try {
            bancoMija.abrir().close();
        } catch (ExcepcionBoveda errorMija) {
            anotarError(errorMija);
        }
        assertTrue(mensajesDeErrorMija.stream().anyMatch(mensajeMija -> mensajeMija.contains("BovedaDestruida")),
                "el flujo debía llegar hasta la autodestrucción: " + mensajesDeErrorMija);

        // ---- auditoría ----
        List<String> registrosMija = CapturaDeLogs.registros();
        assertFalse(registrosMija.isEmpty() && salidaCapturadaMija.size() == 0 && mensajesDeErrorMija.isEmpty(),
                "la auditoría no capturó nada: ¿se registró el LoggerFinder de pruebas?");
        List<String> todoMija = new ArrayList<>(registrosMija);
        todoMija.add(salidaCapturadaMija.toString(StandardCharsets.UTF_8));
        todoMija.add(erroresCapturadosMija.toString(StandardCharsets.UTF_8));
        todoMija.addAll(mensajesDeErrorMija);
        String textoMija = String.join("\n", todoMija);
        String minusculasMija = textoMija.toLowerCase(Locale.ROOT);

        for (String secretoMija : secretosMija) {
            assertFalse(textoMija.contains(secretoMija), () -> "Filtró un secreto de prueba de " + secretoMija.length()
                    + " caracteres");
        }
        for (String fragmentoMija : List.of(FRAGMENTO_1_MIJA, FRAGMENTO_2_MIJA, CARPETA_INTERNA_MIJA)) {
            assertFalse(minusculasMija.contains(fragmentoMija.toLowerCase(Locale.ROOT)),
                    () -> "Filtró un nombre de archivo interno: " + fragmentoMija);
        }
        assertFalse(Pattern.compile("[0-9a-fA-F]{32,}").matcher(textoMija).find(), "Hay un volcado hexadecimal");
        assertFalse(Pattern.compile("(\\b-?\\d{1,3}, ){8,}").matcher(textoMija).find(), "Hay un volcado de bytes");
        assertFalse(Pattern.compile("\\[B@[0-9a-f]+").matcher(textoMija).find(), "Hay una referencia a byte[]");
        assertFalse(Pattern.compile("[A-Za-z0-9+/]{40,}={0,2}").matcher(textoMija).find(), "Hay un bloque base64");
        assertTrue(Files.isDirectory(destinoMija), "el flujo sí extrajo los archivos (la auditoría vio datos reales)");
    }
}
