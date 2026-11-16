package dev.erick.cryptokipu.infra.casos;

import static dev.erick.cryptokipu.infra.casos.Banco.PIN_MIJA;
import static dev.erick.cryptokipu.infra.casos.Banco.esperar;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.app.casos.AgregarArchivos;
import dev.erick.cryptokipu.app.casos.CrearBoveda;
import dev.erick.cryptokipu.app.casos.EliminarArchivo;
import dev.erick.cryptokipu.app.casos.ExtraerArchivo;
import dev.erick.cryptokipu.app.casos.InspeccionarBoveda;
import dev.erick.cryptokipu.app.casos.PoliticaColision;
import dev.erick.cryptokipu.app.casos.RegenerarTokens;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.app.casos.VerificarIntegridad;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.crypto.ParametrosArgon2;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Casos de uso: nombres libres, regeneración de tokens, verificación, eliminación y resultados (mutantes de PIT, Fase 9). */
class CasosDeUsoMutantesTest {

    private static final ReporteProgreso NADA_MIJA = ReporteProgreso.NINGUNO_MIJA;

    @TempDir
    Path carpetaMija;

    private Banco bancoMija;

    @BeforeEach
    void preparar() throws IOException {
        bancoMija = new Banco(carpetaMija);
    }

    private AgregarArchivos.Resultado agregar(SesionBoveda sesionMija, Path origenMija, PoliticaColision politicaMija)
            throws IOException {
        return bancoMija.appMija.agregarArchivos().ejecutar(new AgregarArchivos.Comando(sesionMija, List.of(origenMija), "",
                politicaMija, NADA_MIJA));
    }

    private static Set<String> rutas(SesionBoveda sesionMija) {
        return sesionMija.manifest().entradasMija().stream().map(EntradaManifest::rutaMija).collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------ nombres libres (RENOMBRAR)

    @Test
    void agregarConRenombrarNumeraDesdeDosAntesDeLaExtension() throws IOException {
        bancoMija.crear();
        Path conExtensionMija = bancoMija.archivo("a.txt", 10);
        Path sinExtensionMija = bancoMija.archivo("sin-extension", 10);
        Path ocultoMija = bancoMija.archivo(".oculto", 10);
        Path ensamblado = bancoMija.archivo("dir.con.puntos/b.tar.gz", 10);
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            for (int vueltaMija = 0; vueltaMija < 3; vueltaMija++) {
                for (Path origenMija : List.of(conExtensionMija, sinExtensionMija, ocultoMija)) {
                    agregar(sesionMija, origenMija, PoliticaColision.RENOMBRAR);
                }
            }
            agregar(sesionMija, ensamblado, PoliticaColision.RENOMBRAR);
            agregar(sesionMija, ensamblado, PoliticaColision.RENOMBRAR);

            assertEquals(Set.of("a.txt", "a (2).txt", "a (3).txt", "sin-extension", "sin-extension (2)",
                    "sin-extension (3)", ".oculto", ".oculto (2)", ".oculto (3)", "b.tar.gz", "b.tar (2).gz"),
                    rutas(sesionMija));
        }
    }

    @Test
    void agregarConOmitirNoDuplicaYElResultadoLoInforma() throws IOException {
        bancoMija.crear();
        Path origenMija = bancoMija.archivo("a.txt", 10);
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            assertEquals(1, agregar(sesionMija, origenMija, PoliticaColision.OMITIR).agregadosMija().size());
            AgregarArchivos.Resultado segundoMija = agregar(sesionMija, origenMija, PoliticaColision.OMITIR);
            assertEquals(0, segundoMija.agregadosMija().size());
            assertEquals(1, segundoMija.omitidosMija().size());
            assertEquals(Set.of("a.txt"), rutas(sesionMija));
        }
    }

    // ------------------------------------------------------------------ nombres libres al extraer

    @Test
    void extraerConRenombrarNumeraSinPisarLoExistente() throws IOException {
        bancoMija.crear();
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            agregar(sesionMija, bancoMija.archivo("a.txt", 10), PoliticaColision.OMITIR);
            agregar(sesionMija, bancoMija.archivo("sin-extension", 10), PoliticaColision.OMITIR);
            agregar(sesionMija, bancoMija.archivo(".oculto", 10), PoliticaColision.OMITIR);
            Path destinoMija = carpetaMija.resolve("salida");
            for (int vueltaMija = 0; vueltaMija < 3; vueltaMija++) {
                ExtraerArchivo.Resultado resultadoMija = bancoMija.appMija.extraerArchivo().ejecutar(
                        new ExtraerArchivo.Comando(sesionMija, List.of(), destinoMija, PoliticaColision.RENOMBRAR, NADA_MIJA));
                assertEquals(3, resultadoMija.extraidosMija().size());
            }
            try (var listaMija = Files.list(destinoMija)) {
                assertEquals(Set.of("a.txt", "a (2).txt", "a (3).txt", "sin-extension", "sin-extension (2)",
                        "sin-extension (3)", ".oculto", ".oculto (2)", ".oculto (3)"),
                        listaMija.map(rutaMija -> rutaMija.getFileName().toString()).collect(Collectors.toSet()));
            }
            ExtraerArchivo.Resultado omitidoMija = bancoMija.appMija.extraerArchivo().ejecutar(new ExtraerArchivo.Comando(
                    sesionMija, List.of(), destinoMija, PoliticaColision.OMITIR, NADA_MIJA));
            assertEquals(0, omitidoMija.extraidosMija().size());
            assertEquals(3, omitidoMija.rechazadosMija().size());
        }
    }

    @Test
    void extraerSoloLosSeleccionadosYNingunoSiLaListaEstaVacia() throws IOException {
        bancoMija.crear();
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            agregar(sesionMija, bancoMija.archivo("uno.txt", 10), PoliticaColision.OMITIR);
            agregar(sesionMija, bancoMija.archivo("dos.txt", 10), PoliticaColision.OMITIR);
            EntradaManifest dosMija = sesionMija.manifest().entradasMija().stream()
                    .filter(entradaMija -> entradaMija.rutaMija().equals("dos.txt")).findFirst().orElseThrow();
            Path destinoMija = carpetaMija.resolve("sel");
            ExtraerArchivo.Resultado resultadoMija = bancoMija.appMija.extraerArchivo().ejecutar(new ExtraerArchivo.Comando(
                    sesionMija, List.of(dosMija.fileIdMija()), destinoMija, PoliticaColision.OMITIR, NADA_MIJA));
            assertEquals(1, resultadoMija.extraidosMija().size());
            assertTrue(Files.exists(destinoMija.resolve("dos.txt")));
            assertFalse(Files.exists(destinoMija.resolve("uno.txt")));
        }
    }

    // ------------------------------------------------------------------ InspeccionarBoveda.Resultado

    @Test
    void intentosRestantesSeCalculanSoloConAutodestruccionActiva() {
        ParametrosArgon2 perfilMija = new ParametrosArgon2(64 * 1024, 2, 1);
        assertEquals(-1, new InspeccionarBoveda.Resultado(1, perfilMija, false, false, false, 0, 7).intentosRestantes());
        assertEquals(3, new InspeccionarBoveda.Resultado(1, perfilMija, false, false, false, 5, 2).intentosRestantes());
        assertEquals(5, new InspeccionarBoveda.Resultado(1, perfilMija, false, false, false, 5, 0).intentosRestantes());
        assertEquals(0, new InspeccionarBoveda.Resultado(1, perfilMija, false, false, false, 5, 5).intentosRestantes());
        assertEquals(0, new InspeccionarBoveda.Resultado(1, perfilMija, false, false, false, 5, 9).intentosRestantes());
    }

    @Test
    void inspeccionarInformaPerfilLlaveYIntentos() throws IOException {
        bancoMija.crear(4);
        esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.abrir(Banco.credenciales("999999999999",
                Banco.CLAVE_MIJA, Banco.FRASE_MIJA, bancoMija.llaveMija)));
        InspeccionarBoveda.Resultado infoMija = bancoMija.appMija.inspeccionarBoveda()
                .ejecutar(new InspeccionarBoveda.Comando(bancoMija.bovedaMija));
        assertEquals(1, infoMija.versionFormatoMija());
        assertEquals(Banco.RAPIDO_MIJA, infoMija.perfilMija());
        assertTrue(infoMija.requiereLlaveMija());
        assertFalse(infoMija.destruidaMija());
        assertFalse(infoMija.soloLecturaMija());
        assertEquals(4, infoMija.umbralAutodestruccionMija());
        assertEquals(1, infoMija.intentosFallidosMija());
        assertEquals(3, infoMija.intentosRestantes());
    }

    // ------------------------------------------------------------------ RegenerarTokens

    @Test
    void regenerarTokensDaSeisNuevosYLosViejosDejanDeServir() throws IOException {
        List<TokenRecuperacion> viejosMija = bancoMija.crear();
        List<TokenRecuperacion> nuevosMija;
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            RegenerarTokens.Resultado resultadoMija = bancoMija.appMija.regenerarTokens()
                    .ejecutar(new RegenerarTokens.Comando(sesionMija, bancoMija.vigentes()));
            nuevosMija = resultadoMija.tokensMija();
            assertEquals(6, nuevosMija.size());
            assertEquals(6, nuevosMija.stream().map(tokenMija -> new String(tokenMija.formatear())).distinct().count());
            for (TokenRecuperacion nuevoMija : nuevosMija) {
                for (TokenRecuperacion viejoMija : viejosMija) {
                    assertFalse(java.util.Arrays.equals(nuevoMija.formatear(), viejoMija.formatear()));
                }
            }
        }
        esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.recuperar(viejosMija.getFirst(), PIN_MIJA));
        try (SesionBoveda sesionMija = bancoMija.recuperar(nuevosMija.getFirst(), PIN_MIJA)) {
            assertTrue(sesionMija.rotacionObligatoria());
        }
    }

    @Test
    void regenerarTokensExigeLasCredencialesVigentes() throws IOException {
        bancoMija.crear();
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.appMija.regenerarTokens().ejecutar(
                    new RegenerarTokens.Comando(sesionMija, Banco.credenciales(PIN_MIJA, "Clave Mala 2026",
                            Banco.FRASE_MIJA, bancoMija.llaveMija))));
        }
    }

    // ------------------------------------------------------------------ VerificarIntegridad

    @Test
    void verificarIntegridadInformaCadaArchivoYDetectaUnBloqueDanado() throws IOException {
        bancoMija.crear();
        EntradaManifest victimaMija;
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            agregar(sesionMija, bancoMija.archivo("bueno.bin", 5000), PoliticaColision.OMITIR);
            agregar(sesionMija, bancoMija.archivo("malo.bin", 5000), PoliticaColision.OMITIR);
            VerificarIntegridad.Resultado sanoMija = bancoMija.appMija.verificarIntegridad()
                    .ejecutar(new VerificarIntegridad.Comando(sesionMija, NADA_MIJA));
            assertTrue(sanoMija.todoCorrecto());
            assertEquals(2, sanoMija.informesMija().size());
            assertTrue(sanoMija.informesMija().stream().allMatch(informeMija ->
                    informeMija.estadoMija() == VerificarIntegridad.Estado.CORRECTO));
            victimaMija = sesionMija.manifest().entradasMija().stream()
                    .filter(entradaMija -> entradaMija.rutaMija().equals("malo.bin")).findFirst().orElseThrow();
        }
        try (RandomAccessFile archivoMija = new RandomAccessFile(bancoMija.bovedaMija.toFile(), "rw")) {
            archivoMija.seek(victimaMija.offsetMija() + 100);
            int actualMija = archivoMija.read();
            archivoMija.seek(victimaMija.offsetMija() + 100);
            archivoMija.write(actualMija ^ 0x55);
        }
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            VerificarIntegridad.Resultado danadoMija = bancoMija.appMija.verificarIntegridad()
                    .ejecutar(new VerificarIntegridad.Comando(sesionMija, NADA_MIJA));
            assertFalse(danadoMija.todoCorrecto());
            assertEquals(2, danadoMija.informesMija().size());
            VerificarIntegridad.Informe malMija = danadoMija.informesMija().stream()
                    .filter(informeMija -> informeMija.estadoMija() == VerificarIntegridad.Estado.CORRUPTO).findFirst()
                    .orElseThrow();
            assertEquals("malo.bin", malMija.rutaInternaMija());
            assertEquals(1, danadoMija.informesMija().stream().filter(informeMija ->
                    informeMija.estadoMija() == VerificarIntegridad.Estado.CORRECTO).count());
        }
        assertTrue(new VerificarIntegridad.Resultado(List.of()).todoCorrecto());
    }

    // ------------------------------------------------------------------ EliminarArchivo y SesionBoveda

    @Test
    void eliminarSinCompactarDejaLosBloquesYCompactandoLosQuita() throws IOException {
        bancoMija.crear();
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            agregar(sesionMija, bancoMija.archivo("uno.bin", 200_000), PoliticaColision.OMITIR);
            agregar(sesionMija, bancoMija.archivo("dos.bin", 200_000), PoliticaColision.OMITIR);
            byte[] idUnoMija = sesionMija.manifest().entradasMija().get(0).fileIdMija();
            byte[] idDosMija = sesionMija.manifest().entradasMija().get(1).fileIdMija();
            long antesMija = Files.size(bancoMija.bovedaMija);

            byte[] copiaMija = idUnoMija.clone();
            EliminarArchivo.Comando comandoMija = new EliminarArchivo.Comando(sesionMija, copiaMija, false);
            copiaMija[0] ^= 1; // el comando guardó su propia copia
            assertArrayEquals(idUnoMija, comandoMija.fileIdMija());
            bancoMija.appMija.eliminarArchivo().ejecutar(comandoMija);
            assertEquals(1, sesionMija.manifest().entradasMija().size());
            assertTrue(Files.size(bancoMija.bovedaMija) >= antesMija, "sin compactar el archivo no encoge");

            bancoMija.appMija.eliminarArchivo().ejecutar(new EliminarArchivo.Comando(sesionMija, idDosMija, true));
            assertEquals(0, sesionMija.manifest().entradasMija().size());
            assertTrue(Files.size(bancoMija.bovedaMija) < antesMija - 300_000, "compactando se liberan los bloques");
        }
    }

    @Test
    void laSesionSeDescribeSinDatosYSeCierraUnaSolaVez() throws IOException {
        bancoMija.crear();
        SesionBoveda sesionMija = bancoMija.abrir();
        assertEquals("SesionBoveda[rotacionObligatoria=false, cerrada=false]", sesionMija.toString());
        assertFalse(sesionMija.cerrada());
        sesionMija.close();
        sesionMija.close();
        assertTrue(sesionMija.cerrada());
        assertEquals("SesionBoveda[rotacionObligatoria=false, cerrada=true]", sesionMija.toString());
        assertThrows(IllegalStateException.class, sesionMija::manifest);
    }

    @Test
    void crearBovedaCierraSuSesionYBorraSusTokensAlCerrarElResultado() throws IOException {
        Path destinoMija = carpetaMija.resolve("nueva.kipu");
        CrearBoveda.Resultado resultadoMija = bancoMija.appMija.crearBoveda().ejecutar(new CrearBoveda.Comando(destinoMija,
                bancoMija.vigentes(), Banco.RAPIDO_MIJA, 0));
        assertEquals(6, resultadoMija.tokensMija().size());
        assertFalse(resultadoMija.sesionMija().cerrada());
        resultadoMija.close();
        assertTrue(resultadoMija.sesionMija().cerrada());
        assertThrows(NullPointerException.class, () -> new CrearBoveda.Comando(null, bancoMija.vigentes(),
                Banco.RAPIDO_MIJA, 0));
        assertThrows(NullPointerException.class, () -> new CrearBoveda.Comando(destinoMija, null, Banco.RAPIDO_MIJA, 0));
        assertThrows(NullPointerException.class, () -> new CrearBoveda.Comando(destinoMija, bancoMija.vigentes(), null, 0));
    }

    // ------------------------------------------------------------------ recuperar con token

    @Test
    void conUnTokenValidoPeroUnPinIncorrectoSumaUnIntentoYNoAbre() throws IOException {
        List<TokenRecuperacion> tokensMija = bancoMija.crear(5);
        esperar(ErrorBoveda.CredencialesIncorrectas.class, () -> bancoMija.recuperar(tokensMija.getFirst(), "999999999999"));
        assertEquals(1, bancoMija.headerEn(0).intentosFallidosMija());
        try (SesionBoveda sesionMija = bancoMija.recuperar(tokensMija.getFirst(), PIN_MIJA)) {
            assertTrue(sesionMija.rotacionObligatoria());
        }
    }

    @Test
    void recuperarEnUnaBovedaDestruidaDaBovedaDestruida() throws IOException {
        List<TokenRecuperacion> tokensMija = bancoMija.crear(1);
        esperar(ErrorBoveda.BovedaDestruida.class, () -> bancoMija.abrir(Banco.credenciales("999999999999",
                Banco.CLAVE_MIJA, Banco.FRASE_MIJA, bancoMija.llaveMija)));
        esperar(ErrorBoveda.BovedaDestruida.class, () -> bancoMija.recuperar(tokensMija.getFirst(), PIN_MIJA));
        esperar(ErrorBoveda.BovedaDestruida.class, () -> bancoMija.abrir());
    }
}
