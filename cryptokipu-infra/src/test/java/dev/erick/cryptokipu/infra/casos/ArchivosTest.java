package dev.erick.cryptokipu.infra.casos;

import static dev.erick.cryptokipu.infra.casos.Banco.PIN_MIJA;
import static dev.erick.cryptokipu.infra.casos.Banco.esperar;
import static dev.erick.cryptokipu.infra.casos.Banco.sha256;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.erick.cryptokipu.app.casos.AbrirEnMemoria;
import dev.erick.cryptokipu.app.casos.AgregarArchivos;
import dev.erick.cryptokipu.app.casos.Compactar;
import dev.erick.cryptokipu.app.casos.EliminarArchivo;
import dev.erick.cryptokipu.app.casos.ExtraerArchivo;
import dev.erick.cryptokipu.app.casos.PoliticaColision;
import dev.erick.cryptokipu.app.casos.RotarCredenciales;
import dev.erick.cryptokipu.app.casos.SesionBoveda;
import dev.erick.cryptokipu.app.casos.VerificarIntegridad;
import dev.erick.cryptokipu.app.puertos.Origen;
import dev.erick.cryptokipu.core.auth.CambioCredenciales;
import dev.erick.cryptokipu.core.auth.TokenRecuperacion;
import dev.erick.cryptokipu.core.error.ErrorBoveda;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.core.progreso.ReporteProgreso;
import dev.erick.cryptokipu.infra.storage.ContenedorKipu;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Ciclo completo sin UI y casos límite de agregar/extraer/leer (Fase 4, puntos 3–6 y 9). */
class ArchivosTest {

    private static final ReporteProgreso NADA_MIJA = ReporteProgreso.NINGUNO_MIJA;

    @TempDir
    Path carpetaMija;

    private Banco bancoMija;

    @BeforeEach
    void preparar() throws IOException {
        bancoMija = new Banco(carpetaMija);
    }

    private AgregarArchivos.Resultado agregar(SesionBoveda sesionMija, List<Path> origenesMija, PoliticaColision politicaMija,
            ReporteProgreso progresoMija) throws IOException {
        return bancoMija.appMija.agregarArchivos().ejecutar(new AgregarArchivos.Comando(sesionMija, origenesMija, "",
                politicaMija, progresoMija));
    }

    private ExtraerArchivo.Resultado extraer(SesionBoveda sesionMija, Path destinoMija, PoliticaColision politicaMija)
            throws IOException {
        return bancoMija.appMija.extraerArchivo().ejecutar(new ExtraerArchivo.Comando(sesionMija, List.of(), destinoMija,
                politicaMija, NADA_MIJA));
    }

    // ------------------------------------------------------------------ 9: ciclo completo

    @Test
    void cicloCompletoSinInterfaz() throws IOException {
        List<TokenRecuperacion> tokensMija = bancoMija.crear();
        Path fotosMija = carpetaMija.resolve("origen/fotos");
        Map<String, String> originalesMija = new HashMap<>();
        for (String nombreMija : List.of("fotos/año 2026/ñandú.jpg", "fotos/video.mp4", "fotos/vacío.txt",
                "fotos/sub/profunda/nota.md")) {
            Path archivoMija = bancoMija.archivo(nombreMija, nombreMija.endsWith(".mp4") ? 3 * (1 << 20) + 17
                    : nombreMija.contains("vacío") ? 0 : 5000);
            originalesMija.put(nombreMija, sha256(archivoMija));
        }

        // agregar carpeta (recursivo)
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            AgregarArchivos.Resultado resultadoMija = agregar(sesionMija, List.of(fotosMija), PoliticaColision.OMITIR,
                    NADA_MIJA);
            assertEquals(4, resultadoMija.agregadosMija().size());
            assertTrue(resultadoMija.omitidosMija().isEmpty());
        }

        // extraer y comparar SHA-256
        Path destinoMija = carpetaMija.resolve("extraido");
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            ExtraerArchivo.Resultado resultadoMija = extraer(sesionMija, destinoMija, PoliticaColision.OMITIR);
            assertEquals(4, resultadoMija.extraidosMija().size());
            for (Map.Entry<String, String> originalMija : originalesMija.entrySet()) {
                assertEquals(originalMija.getValue(), sha256(destinoMija.resolve(originalMija.getKey())));
            }
            try (var partesMija = Files.walk(destinoMija)) {
                assertTrue(partesMija.noneMatch(rutaMija -> rutaMija.toString().endsWith(".part")));
            }

            // eliminar + compactar
            EntradaManifest videoMija = sesionMija.manifest().entradasMija().stream()
                    .filter(entradaMija -> entradaMija.rutaMija().endsWith("video.mp4")).findFirst().orElseThrow();
            long antesMija = Files.size(bancoMija.bovedaMija);
            bancoMija.appMija.eliminarArchivo().ejecutar(new EliminarArchivo.Comando(sesionMija,
                    videoMija.fileIdMija(), false));
            bancoMija.appMija.compactar().ejecutar(new Compactar.Comando(sesionMija));
            assertTrue(Files.size(bancoMija.bovedaMija) < antesMija - 3 * (1 << 20));
            assertEquals(3, sesionMija.manifest().entradasMija().size());
        }

        // recuperar con token → rotar → verificar integridad
        try (SesionBoveda sesionMija = bancoMija.recuperar(tokensMija.get(4), PIN_MIJA)) {
            bancoMija.appMija.rotarCredenciales().ejecutar(new RotarCredenciales.Comando(sesionMija, Optional.empty(),
                    new CambioCredenciales(Optional.of("555555555555".toCharArray()),
                            Optional.of("Clave Nueva 2028".toCharArray()),
                            Optional.of("frase nueva y distinta".toCharArray()),
                            new CambioCredenciales.CambioLlave.Quitar(), OptionalInt.empty())));
            VerificarIntegridad.Resultado informeMija = bancoMija.appMija.verificarIntegridad()
                    .ejecutar(new VerificarIntegridad.Comando(sesionMija, NADA_MIJA));
            assertEquals(3, informeMija.informesMija().size());
            assertTrue(informeMija.todoCorrecto());
        }
        bancoMija.abrir(Banco.credenciales("555555555555", "Clave Nueva 2028", "frase nueva y distinta", null)).close();
    }

    // ------------------------------------------------------------------ 3: agregar

    @Test
    void cancelarAMitadDeAgregarDejaLaBovedaComoEstaba() throws IOException {
        bancoMija.crear();
        Path grandeMija = bancoMija.archivo("grande.bin", 5 * (1 << 20));
        long tamanoAntesMija = Files.size(bancoMija.bovedaMija);
        ReporteProgreso cancelarMija = new ReporteProgreso() {
            private long vistoMija;

            @Override
            public void avanzar(long hechoMija, long totalMija) {
                vistoMija = hechoMija;
            }

            @Override
            public boolean cancelado() {
                return vistoMija > 2 * (1 << 20);
            }
        };
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            esperar(ErrorBoveda.OperacionCancelada.class,
                    () -> agregar(sesionMija, List.of(grandeMija), PoliticaColision.OMITIR, cancelarMija));
            assertTrue(sesionMija.manifest().entradasMija().isEmpty());
        }
        assertEquals(tamanoAntesMija, Files.size(bancoMija.bovedaMija));
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            assertTrue(sesionMija.manifest().entradasMija().isEmpty());
        }
    }

    @Test
    void unArchivoQueCambiaDuranteLaLecturaSeDescarta() throws IOException {
        bancoMija.crear();
        Path cambianteMija = bancoMija.archivo("cambiante.log", 3 * (1 << 20));
        Path estableMija = bancoMija.archivo("estable.txt", 1000);
        boolean[] crecioMija = {false};
        ReporteProgreso agrandarMija = (_, _) -> {
            if (!crecioMija[0]) {
                crecioMija[0] = true;
                try {
                    Files.write(cambianteMija, new byte[4096], StandardOpenOption.APPEND);
                } catch (IOException errorMija) {
                    throw new IllegalStateException(errorMija);
                }
            }
        };
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            AgregarArchivos.Resultado resultadoMija = agregar(sesionMija, List.of(cambianteMija, estableMija),
                    PoliticaColision.OMITIR, agrandarMija);
            assertEquals(1, resultadoMija.agregadosMija().size());
            assertEquals("estable.txt", resultadoMija.agregadosMija().getFirst().rutaMija());
            assertEquals(AgregarArchivos.Motivo.CAMBIO_DURANTE_LECTURA,
                    resultadoMija.omitidosMija().getFirst().motivoMija());
        }
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            assertTrue(bancoMija.appMija.verificarIntegridad()
                    .ejecutar(new VerificarIntegridad.Comando(sesionMija, NADA_MIJA)).todoCorrecto());
        }
    }

    @Test
    void politicasDeColisionAlAgregar() throws IOException {
        bancoMija.crear();
        Path unoMija = bancoMija.archivo("a/Nota.txt", 100);
        Path otroMija = bancoMija.archivo("b/nota.TXT", 200);
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            agregar(sesionMija, List.of(unoMija), PoliticaColision.OMITIR, NADA_MIJA);

            AgregarArchivos.Resultado omitirMija = agregar(sesionMija, List.of(otroMija), PoliticaColision.OMITIR, NADA_MIJA);
            assertEquals(AgregarArchivos.Motivo.YA_EXISTE, omitirMija.omitidosMija().getFirst().motivoMija());

            AgregarArchivos.Resultado renombrarMija = agregar(sesionMija, List.of(otroMija), PoliticaColision.RENOMBRAR,
                    NADA_MIJA);
            assertEquals("nota (2).TXT", renombrarMija.agregadosMija().getFirst().rutaMija());

            agregar(sesionMija, List.of(otroMija), PoliticaColision.REEMPLAZAR, NADA_MIJA);
            List<EntradaManifest> entradasMija = sesionMija.manifest().entradasMija();
            assertEquals(2, entradasMija.size());
            assertTrue(entradasMija.stream().anyMatch(entradaMija -> entradaMija.rutaMija().equals("nota.TXT")
                    && entradaMija.tamanoMija() == 200));
        }
    }

    @Test
    void losEnlacesSimbolicosNoSeSiguen() throws IOException {
        bancoMija.crear();
        Path carpetaOrigenMija = carpetaMija.resolve("origen/conenlace");
        Files.createDirectories(carpetaOrigenMija);
        Files.write(carpetaOrigenMija.resolve("real.txt"), new byte[] {1, 2, 3});
        Path secretoMija = bancoMija.archivo("fuera/secreto.txt", 10);
        try {
            Files.createSymbolicLink(carpetaOrigenMija.resolve("enlace.txt"), secretoMija);
            Files.createSymbolicLink(carpetaOrigenMija.resolve("bucle"), carpetaOrigenMija);
        } catch (IOException | UnsupportedOperationException errorMija) {
            assumeTrue(false, "El sistema no permite crear enlaces simbólicos sin privilegios");
        }
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            AgregarArchivos.Resultado resultadoMija = agregar(sesionMija, List.of(carpetaOrigenMija),
                    PoliticaColision.OMITIR, NADA_MIJA);
            assertEquals(List.of("conenlace/real.txt"),
                    resultadoMija.agregadosMija().stream().map(EntradaManifest::rutaMija).toList());
            assertEquals(2, resultadoMija.omitidosMija().stream()
                    .filter(omitidoMija -> omitidoMija.motivoMija() == AgregarArchivos.Motivo.ENLACE_SIMBOLICO).count());
        }
    }

    // ------------------------------------------------------------------ 4: extraer (zip-slip)

    @Test
    void manifestFabricadoConRutasMaliciosasNoEscribeFueraDelDestino() throws IOException {
        bancoMija.crear();
        Path origenMija = bancoMija.archivo("x.bin", 50);
        List<String> maliciosasMija = List.of("../escapado.txt", "a/../../escapado2.txt", "/absoluta.txt",
                "C:/Windows/win.ini", "CON", "aux.txt", "dos:puntos.txt", "ok.txt");
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            ContenedorKipu contenedorMija = (ContenedorKipu) sesionMija.contenedor();
            contenedorMija.agregar(maliciosasMija.stream().map(rutaMija -> new Origen(origenMija, rutaMija)).toList());

            Path destinoMija = carpetaMija.resolve("zona/destino");
            ExtraerArchivo.Resultado resultadoMija = extraer(sesionMija, destinoMija, PoliticaColision.OMITIR);

            assertEquals(List.of("ok.txt"), resultadoMija.extraidosMija().stream()
                    .map(ExtraerArchivo.Extraido::rutaInternaMija).toList());
            assertEquals(7, resultadoMija.rechazadosMija().size());
            assertTrue(resultadoMija.rechazadosMija().stream()
                    .allMatch(rechazoMija -> rechazoMija.motivoMija() == ExtraerArchivo.Motivo.RUTA_PELIGROSA));
            assertFalse(Files.exists(carpetaMija.resolve("zona/escapado.txt")));
            assertFalse(Files.exists(carpetaMija.resolve("zona/escapado2.txt")));
            assertFalse(Files.exists(carpetaMija.resolve("absoluta.txt")));
        }
    }

    @Test
    void unBloqueManipuladoNoDejaNiArchivoNiPart() throws IOException {
        bancoMija.crear();
        Path grandeMija = bancoMija.archivo("grande.bin", 3 * (1 << 20));
        EntradaManifest entradaMija;
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            entradaMija = agregar(sesionMija, List.of(grandeMija), PoliticaColision.OMITIR, NADA_MIJA)
                    .agregadosMija().getFirst();
        }
        voltearBit(entradaMija.offsetMija() + 2L * ((1 << 20) + 16) + 10); // tercer bloque
        Path destinoMija = carpetaMija.resolve("salida");
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            ExtraerArchivo.Resultado resultadoMija = extraer(sesionMija, destinoMija, PoliticaColision.OMITIR);
            assertEquals(ExtraerArchivo.Motivo.INTEGRIDAD, resultadoMija.rechazadosMija().getFirst().motivoMija());
            VerificarIntegridad.Resultado informeMija = bancoMija.appMija.verificarIntegridad()
                    .ejecutar(new VerificarIntegridad.Comando(sesionMija, NADA_MIJA));
            assertEquals(VerificarIntegridad.Estado.CORRUPTO, informeMija.informesMija().getFirst().estadoMija());
        }
        try (var contenidoMija = Files.list(destinoMija)) {
            assertEquals(0, contenidoMija.count(), "ni grande.bin ni grande.bin.part");
        }
    }

    @Test
    void nuncaSobrescribeSinPoliticaYDetectaMayusculas() throws IOException {
        bancoMija.crear();
        Path origenMija = bancoMija.archivo("d.txt", 30);
        Path destinoMija = carpetaMija.resolve("salida");
        Files.createDirectories(destinoMija);
        Files.writeString(destinoMija.resolve("d.txt"), "mío");
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            ((ContenedorKipu) sesionMija.contenedor()).agregar(List.of(new Origen(origenMija, "d.txt"),
                    new Origen(origenMija, "D.TXT")));

            ExtraerArchivo.Resultado omitirMija = extraer(sesionMija, destinoMija, PoliticaColision.OMITIR);
            assertEquals("mío", Files.readString(destinoMija.resolve("d.txt")));
            boolean sinMayusculasMija = System.getProperty("os.name").toLowerCase().matches(".*(win|mac).*");
            assertEquals(sinMayusculasMija ? 2 : 1, omitirMija.rechazadosMija().size());

            ExtraerArchivo.Resultado renombrarMija = extraer(sesionMija, destinoMija, PoliticaColision.RENOMBRAR);
            assertEquals(2, renombrarMija.extraidosMija().size());
            assertEquals("mío", Files.readString(destinoMija.resolve("d.txt")));

            extraer(sesionMija, destinoMija, PoliticaColision.REEMPLAZAR);
            assertArrayEquals(Files.readAllBytes(origenMija), Files.readAllBytes(destinoMija.resolve("d.txt")));
        }
    }

    @Test
    void restauraLaFechaDeModificacion() throws IOException {
        bancoMija.crear();
        Path origenMija = bancoMija.archivo("viejo.txt", 10);
        Files.setLastModifiedTime(origenMija, java.nio.file.attribute.FileTime.fromMillis(1_600_000_000_000L));
        Path destinoMija = carpetaMija.resolve("salida");
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            agregar(sesionMija, List.of(origenMija), PoliticaColision.OMITIR, NADA_MIJA);
            extraer(sesionMija, destinoMija, PoliticaColision.OMITIR);
        }
        assertEquals(1_600_000_000_000L, Files.getLastModifiedTime(destinoMija.resolve("viejo.txt")).toMillis());
    }

    // ------------------------------------------------------------------ 5: AbrirEnMemoria

    @Test
    void elCanalEnMemoriaPermiteSaltarYFallaSoloEnElBloqueManipulado() throws IOException {
        bancoMija.crear();
        Path grandeMija = bancoMija.archivo("video.mp4", 3 * (1 << 20) + 333);
        byte[] originalMija = Files.readAllBytes(grandeMija);
        EntradaManifest entradaMija;
        try (SesionBoveda sesionMija = bancoMija.abrir()) {
            entradaMija = agregar(sesionMija, List.of(grandeMija), PoliticaColision.OMITIR, NADA_MIJA)
                    .agregadosMija().getFirst();
            try (SeekableByteChannel canalMija = bancoMija.appMija.abrirEnMemoria()
                    .ejecutar(new AbrirEnMemoria.Comando(sesionMija, entradaMija.fileIdMija()))) {
                assertEquals(originalMija.length, canalMija.size());
                Random azarMija = new Random(7);
                for (int vueltaMija = 0; vueltaMija < 200; vueltaMija++) {
                    int posicionMija = azarMija.nextInt(originalMija.length);
                    int largoMija = Math.min(1 + azarMija.nextInt(3 * (1 << 20)), originalMija.length - posicionMija);
                    assertArrayEquals(java.util.Arrays.copyOfRange(originalMija, posicionMija, posicionMija + largoMija),
                            leer(canalMija, posicionMija, largoMija));
                }
                canalMija.position(originalMija.length);
                assertEquals(-1, canalMija.read(ByteBuffer.allocate(10)));
            }
        }
        voltearBit(entradaMija.offsetMija() + ((1 << 20) + 16) + 5); // segundo bloque
        try (SesionBoveda sesionMija = bancoMija.abrir();
                SeekableByteChannel canalMija = bancoMija.appMija.abrirEnMemoria()
                        .ejecutar(new AbrirEnMemoria.Comando(sesionMija, entradaMija.fileIdMija()))) {
            assertArrayEquals(java.util.Arrays.copyOfRange(originalMija, 0, 1000), leer(canalMija, 0, 1000));
            assertArrayEquals(java.util.Arrays.copyOfRange(originalMija, 2 * (1 << 20), 2 * (1 << 20) + 50),
                    leer(canalMija, 2 * (1 << 20), 50));
            esperar(ErrorBoveda.IntegridadFallida.class, () -> leer(canalMija, (1 << 20) + 100, 10));
        }
    }

    @Test
    void cerrarLaSesionCierraLosCanalesAbiertos() throws IOException {
        bancoMija.crear();
        Path origenMija = bancoMija.archivo("a.txt", 100);
        SesionBoveda sesionMija = bancoMija.abrir();
        EntradaManifest entradaMija = agregar(sesionMija, List.of(origenMija), PoliticaColision.OMITIR, NADA_MIJA)
                .agregadosMija().getFirst();
        SeekableByteChannel canalMija = bancoMija.appMija.abrirEnMemoria()
                .ejecutar(new AbrirEnMemoria.Comando(sesionMija, entradaMija.fileIdMija()));
        sesionMija.close();
        assertFalse(canalMija.isOpen());
    }

    // ------------------------------------------------------------------ utilidades

    private static byte[] leer(SeekableByteChannel canalMija, long posicionMija, int largoMija) throws IOException {
        canalMija.position(posicionMija);
        ByteBuffer bufferMija = ByteBuffer.allocate(largoMija);
        while (bufferMija.hasRemaining() && canalMija.read(bufferMija) > 0) {
            // leer hasta llenar
        }
        return bufferMija.array();
    }

    private void voltearBit(long posicionMija) throws IOException {
        try (RandomAccessFile archivoMija = new RandomAccessFile(bancoMija.bovedaMija.toFile(), "rw")) {
            archivoMija.seek(posicionMija);
            int valorMija = archivoMija.read();
            archivoMija.seek(posicionMija);
            archivoMija.write(valorMija ^ 0x01);
        }
    }
}
