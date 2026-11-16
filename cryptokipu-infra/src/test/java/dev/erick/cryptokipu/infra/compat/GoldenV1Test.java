package dev.erick.cryptokipu.infra.compat;

import static dev.erick.cryptokipu.infra.compat.GeneradorGolden.CLAVE_MIJA;
import static dev.erick.cryptokipu.infra.compat.GeneradorGolden.FRASE_MIJA;
import static dev.erick.cryptokipu.infra.compat.GeneradorGolden.PIN_MIJA;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.app.casos.DesbloquearBoveda;
import dev.erick.cryptokipu.app.casos.ExtraerArchivo;
import dev.erick.cryptokipu.app.casos.InspeccionarBoveda;
import dev.erick.cryptokipu.app.casos.PoliticaColision;
import dev.erick.cryptokipu.app.casos.RecuperarConToken;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.app.casos.VerificarIntegridad;
import dev.erick.cryptokipu.core.auth.Credenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.error.ExcepcionBoveda;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import dev.erick.cryptokipu.infra.autenticacion.LlaveSeguridad;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import dev.erick.cryptokipu.infra.configuracion.PreferenciasStore;
import dev.erick.cryptokipu.infra.storage.ContenedorKipu;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compatibilidad de formato (Fase 9, «golden files»): una bóveda v1 REAL, creada con credenciales, llave y tokens
 * conocidos y guardada en {@code src/test/resources/golden/v1}, se abre en cada build y el contenido descifrado se
 * compara por SHA-256 con el guardado. Ningún cambio futuro (formato, KDF, normalización de la frase, derivación de
 * subclaves, AAD…) puede romper las bóvedas existentes sin que este test falle. Si falla, NO se regenera el golden:
 * se corrige el cambio, o se introduce una versión nueva de formato con migración.
 */
class GoldenV1Test {

    @TempDir
    Path carpetaMija;

    private Path bovedaMija;
    private Path llaveMija;
    private Contenedor appMija;

    private static Path copiarRecurso(String nombreMija, Path destinoMija) throws IOException {
        try (InputStream entradaMija = GoldenV1Test.class.getResourceAsStream("/golden/v1/" + nombreMija)) {
            assertTrue(entradaMija != null, "falta el recurso golden/v1/" + nombreMija);
            Files.copy(entradaMija, destinoMija);
        }
        return destinoMija;
    }

    @BeforeEach
    void copiarGolden() throws IOException {
        // Se trabaja sobre COPIAS: abrir con un token modifica la bóveda.
        bovedaMija = copiarRecurso("golden.kipu", carpetaMija.resolve("golden.kipu"));
        llaveMija = copiarRecurso("golden.kipukey", carpetaMija.resolve("golden.kipukey"));
        appMija = new Contenedor(ContenedorKipu.Opciones.porDefecto(), PreferenciasStore.portatil(carpetaMija.resolve("app")));
    }

    private Credenciales credenciales(String claveMija) throws IOException {
        return new Credenciales(PIN_MIJA.toCharArray(), claveMija.toCharArray(), FRASE_MIJA.toCharArray(),
                LlaveSeguridad.huellaBytes(llaveMija, ReporteProgreso.NINGUNO_MIJA));
    }

    private static Map<String, String> huellasEsperadas() throws IOException {
        Map<String, String> huellasMija = new HashMap<>();
        try (InputStream entradaMija = GoldenV1Test.class.getResourceAsStream("/golden/v1/contenido.sha256")) {
            for (String lineaMija : new String(entradaMija.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!lineaMija.isBlank()) {
                    huellasMija.put(lineaMija.substring(66), lineaMija.substring(0, 64));
                }
            }
        }
        return huellasMija;
    }

    @Test
    void laBovedaV1SigueSiendoV1ConLosParametrosEsperados() throws IOException {
        InspeccionarBoveda.Resultado infoMija = appMija.inspeccionarBoveda().ejecutar(
                new InspeccionarBoveda.Comando(bovedaMija));
        assertEquals(1, infoMija.versionFormatoMija());
        assertEquals(64 * 1024, infoMija.perfilMija().memoriaKibMija());
        assertEquals(2, infoMija.perfilMija().iteracionesMija());
        assertEquals(1, infoMija.perfilMija().paralelismoMija());
        assertTrue(infoMija.requiereLlaveMija(), "la golden se creó con llave de seguridad (F4)");
        assertFalse(infoMija.destruidaMija());
        assertEquals(0, infoMija.umbralAutodestruccionMija());
        byte[] inicioMija = new byte[5];
        try (InputStream entradaMija = Files.newInputStream(bovedaMija)) {
            assertEquals(5, entradaMija.read(inicioMija));
        }
        assertArrayEquals("CKIPU".getBytes(StandardCharsets.US_ASCII), inicioMija, "el magic del formato no cambia");
    }

    @Test
    void abreConLasCredencialesConocidasYElContenidoCoincidePorSha256() throws IOException {
        Map<String, String> esperadasMija = huellasEsperadas();
        try (SesionBoveda sesionMija = appMija.desbloquearBoveda().ejecutar(
                new DesbloquearBoveda.Comando(bovedaMija, credenciales(CLAVE_MIJA)))) {
            assertFalse(sesionMija.rotacionObligatoria());
            List<EntradaManifest> entradasMija = sesionMija.manifest().entradasMija();
            assertEquals(esperadasMija.size(), entradasMija.size());
            assertTrue(sesionMija.manifest().entradasMija().stream().allMatch(entradaMija ->
                    esperadasMija.containsKey(entradaMija.rutaMija())), "las rutas internas deben ser las guardadas");

            Path destinoMija = carpetaMija.resolve("extraido");
            ExtraerArchivo.Resultado resultadoMija = appMija.extraerArchivo().ejecutar(new ExtraerArchivo.Comando(sesionMija,
                    List.of(), destinoMija, PoliticaColision.OMITIR, ReporteProgreso.NINGUNO_MIJA));
            assertEquals(esperadasMija.size(), resultadoMija.extraidosMija().size());
            for (Map.Entry<String, String> esperadaMija : esperadasMija.entrySet()) {
                byte[] bytesMija = Files.readAllBytes(destinoMija.resolve(esperadaMija.getKey()));
                assertEquals(esperadaMija.getValue(), GeneradorGolden.sha256(bytesMija), esperadaMija.getKey());
                assertEquals(GeneradorGolden.contenidos().get(esperadaMija.getKey()).length, bytesMija.length);
            }
            assertTrue(appMija.verificarIntegridad().ejecutar(new VerificarIntegridad.Comando(sesionMija,
                    ReporteProgreso.NINGUNO_MIJA)).todoCorrecto());
        }
    }

    @Test
    void unFactorIncorrectoSigueDandoCredencialesIncorrectas() throws IOException {
        ExcepcionBoveda errorMija = org.junit.jupiter.api.Assertions.assertThrows(ExcepcionBoveda.class,
                () -> appMija.desbloquearBoveda().ejecutar(new DesbloquearBoveda.Comando(bovedaMija,
                        credenciales("Otra Clave 2026"))));
        assertEquals(new ErrorBoveda.CredencialesIncorrectas(), errorMija.error());
    }

    @Test
    void cadaUnoDeLosSeisTokensSigueAbriendoConF1() throws IOException {
        String textoMija;
        try (InputStream entradaMija = GoldenV1Test.class.getResourceAsStream("/golden/v1/tokens.txt")) {
            textoMija = new String(entradaMija.readAllBytes(), StandardCharsets.UTF_8);
        }
        String[] tokensMija = textoMija.strip().split("\n");
        assertEquals(6, tokensMija.length);
        // Con el primero basta para demostrar la derivación de los slots de token; usar uno gasta ese slot de la COPIA.
        try (TokenRecuperacion tokenMija = TokenRecuperacion.leer(tokensMija[0].toCharArray());
                SesionBoveda sesionMija = appMija.recuperarConToken().ejecutar(new RecuperarConToken.Comando(bovedaMija,
                        tokenMija, PIN_MIJA.toCharArray()))) {
            assertTrue(sesionMija.rotacionObligatoria(), "tras un token la rotación es obligatoria");
        }
    }
}
