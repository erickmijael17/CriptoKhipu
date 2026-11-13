package dev.erick.cryptokipu.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.app.puertos.VisorDocumentos;
import dev.erick.cryptokipu.core.format.EntradaManifest;
import dev.erick.cryptokipu.infra.configuracion.Contenedor;
import dev.erick.cryptokipu.ui.viewmodel.ExploradorViewModel.VistaPrevia;
import dev.erick.cryptokipu.visores.VisoresEnMemoria;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Criterio de salida de la Fase 7 (automatizable): una sesión que abre una imagen, un PDF, un Word, un texto y un
 * MP4 (por el servidor local) NO deja archivos nuevos ni el contenido descifrado en ningún lado.
 *
 * <p>La JVM de pruebas usa {@code build/jvm-pruebas/tmp} como {@code java.io.tmpdir} (igual que el modo portátil usa
 * {@code $APPDIR/tmp}); el "anfitrión" son la carpeta temporal REAL del sistema, {@code ~/.openjfx}, {@code ~/.java}
 * y el perfil del usuario. Se comparan dos fotos (antes y después) y, además, se busca un marcador único del texto
 * claro en todos esos lugares.
 *
 * <p>Lo que esta prueba NO puede ver (va en THREAT_MODEL): pagefile/swap, cachés de fuentes del sistema operativo y
 * lo que hagan aplicaciones externas.
 */
class SinRastrosEnElAnfitrionTest {

    /** Texto único: si aparece en cualquier archivo fuera de la bóveda, el contenido se filtró a disco. */
    private static final String MARCADOR_MIJA = "MARCADOR-" + HexFormat.of().formatHex(aleatorios());

    private static byte[] aleatorios() {
        byte[] bytesMija = new byte[8];
        new java.security.SecureRandom().nextBytes(bytesMija);
        return bytesMija;
    }

    @TempDir
    Path carpetaMija;

    @Test
    void unaSesionConImagenPdfWordTextoYMp4NoDejaRastros() throws Exception {
        List<Path> originalesMija = crearOriginales(carpetaMija.resolve("originales"));
        byte[] mp4Mija = Files.readAllBytes(carpetaMija.resolve("originales/viaje.mp4"));
        Contenedor appMija = BancoUi.contenedor(carpetaMija);
        AsistenteCreacionViewModel asistenteMija = BancoUi.asistenteListo(appMija, carpetaMija.resolve("rastros.kipu"),
                originalesMija);
        AsistenteCreacionViewModel.Creada creadaMija = asistenteMija.crear(ProgresoOperacion.NINGUNO_MIJA);
        creadaMija.tokensMija().forEach(tokenMija -> tokenMija.close());
        Path cacheFuentesMija = Path.of(System.getProperty("java.io.tmpdir"));

        Instantanea antesMija = Instantanea.tomar();
        EstadoApp estadoMija = new EstadoApp(appMija);
        estadoMija.abrir(creadaMija.rutaMija(), creadaMija.sesionMija());
        ExploradorViewModel exploradorMija = new ExploradorViewModel(appMija, List.of(new VisoresEnMemoria()));
        ExploradorViewModel.FuenteServidor servidorMija = estadoMija::servidorMedios;

        // Imagen: se decodifica desde la memoria.
        VistaPrevia.Imagen imagenMija = assertInstanceOf(VistaPrevia.Imagen.class,
                vistaPrevia(estadoMija, exploradorMija, "foto.png", servidorMija));
        BufferedImage decodificadaMija = ImageIO.read(new java.io.ByteArrayInputStream(imagenMija.bytesMija()));
        assertEquals(64, decodificadaMija.getWidth());

        // Texto.
        VistaPrevia.Texto textoMija = assertInstanceOf(VistaPrevia.Texto.class,
                vistaPrevia(estadoMija, exploradorMija, "notas.txt", servidorMija));
        assertTrue(textoMija.contenidoMija().contains(MARCADOR_MIJA));

        // PDF: página renderizada bajo demanda.
        VistaPrevia.Documento pdfMija = assertInstanceOf(VistaPrevia.Documento.class,
                vistaPrevia(estadoMija, exploradorMija, "informe.pdf", servidorMija));
        try (pdfMija) {
            VisorDocumentos.Paginado paginasMija = assertInstanceOf(VisorDocumentos.Paginado.class,
                    pdfMija.documentoMija());
            assertEquals(2, paginasMija.paginas());
            assertNotNull(paginasMija.renderizar(1, 700));
        }

        // Word.
        VistaPrevia.Documento wordMija = assertInstanceOf(VistaPrevia.Documento.class,
                vistaPrevia(estadoMija, exploradorMija, "carta.docx", servidorMija));
        try (wordMija) {
            assertTrue(assertInstanceOf(VisorDocumentos.Textual.class, wordMija.documentoMija()).contenidoMija()
                    .contains(MARCADOR_MIJA));
        }

        // MP4: el servidor local entrega los bytes con Range (adelantar y retroceder) sin tocar el disco.
        VistaPrevia.Medio medioMija = assertInstanceOf(VistaPrevia.Medio.class,
                vistaPrevia(estadoMija, exploradorMija, "viaje.mp4", servidorMija));
        assertTrue(medioMija.videoMija());
        try (HttpClient clienteMija = HttpClient.newHttpClient()) {
            for (int inicioMija : new int[] {mp4Mija.length - 4096, 0, 700_000, 100_000, 0}) {
                HttpResponse<byte[]> respuestaMija = clienteMija.send(HttpRequest.newBuilder(
                        URI.create(medioMija.urlMija())).header("Range", "bytes=" + inicioMija + "-" + (inicioMija + 4095))
                        .build(), HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(206, respuestaMija.statusCode());
                assertArrayEquals(Arrays.copyOfRange(mp4Mija, inicioMija, inicioMija + 4096), respuestaMija.body());
            }
        }
        estadoMija.retirarMedio(medioMija.tokenMija());

        // Cierra la sesión: servidor apagado, sin carpetas temporales.
        estadoMija.cerrar();
        Instantanea despuesMija = Instantanea.tomar();

        assertEquals(antesMija.sistemaMija(), despuesMija.sistemaMija(),
                "carpeta temporal del sistema, ~/.openjfx, ~/.java y perfil: sin archivos nuevos");
        assertEquals(antesMija.tmpDeLaJvmMija(), despuesMija.tmpDeLaJvmMija(), "java.io.tmpdir (en modo portátil, $APPDIR/tmp)");
        Set<Path> enTmpMija = buscarMarcador(cacheFuentesMija);
        enTmpMija.removeIf(archivoMija -> archivoMija.startsWith(carpetaMija)); // los originales de la propia prueba
        assertEquals(Set.of(), enTmpMija, "el texto claro no está en ningún archivo temporal");
        for (Path sitioMija : Instantanea.sitiosDelAnfitrion()) {
            assertEquals(Set.of(), buscarMarcador(sitioMija), "texto claro en " + sitioMija);
        }
    }

    @Test
    void laCopiaParaAplicacionExternaSeBorraAlCerrarLaSesion() throws Exception {
        Path origenMija = carpetaMija.resolve("originales");
        crearOriginales(origenMija);
        Contenedor appMija = BancoUi.contenedor(carpetaMija);
        AsistenteCreacionViewModel.Creada creadaMija = BancoUi.asistenteListo(appMija,
                carpetaMija.resolve("externa.kipu"), List.of(origenMija.resolve("notas.txt")))
                .crear(ProgresoOperacion.NINGUNO_MIJA);
        creadaMija.tokensMija().forEach(tokenMija -> tokenMija.close());
        EstadoApp estadoMija = new EstadoApp(appMija);
        estadoMija.abrir(creadaMija.rutaMija(), creadaMija.sesionMija());
        EntradaManifest entradaMija = estadoMija.sesion().manifest().entradasMija().getFirst();

        Path carpetaTemporalMija = estadoMija.espacioExterno().carpeta();
        Path copiaMija = new ExploradorViewModel(appMija).copiaParaAplicacionExterna(estadoMija.sesion(), entradaMija,
                carpetaTemporalMija, ProgresoOperacion.NINGUNO_MIJA);
        assertTrue(Files.readString(copiaMija).contains(MARCADOR_MIJA));
        assertTrue(copiaMija.startsWith(carpetaTemporalMija));

        estadoMija.cerrar();
        assertFalse(Files.exists(copiaMija), "la copia sin cifrar no sobrevive a la sesión");
        assertFalse(Files.exists(carpetaTemporalMija));
    }

    // ------------------------------------------------------------------ ayudas

    private static VistaPrevia vistaPrevia(EstadoApp estadoMija, ExploradorViewModel exploradorMija, String nombreMija,
            ExploradorViewModel.FuenteServidor servidorMija) throws IOException {
        EntradaManifest entradaMija = estadoMija.sesion().manifest().entradasMija().stream()
                .filter(candidataMija -> candidataMija.rutaMija().equals(nombreMija)).findFirst().orElseThrow();
        return exploradorMija.vistaPrevia(estadoMija.sesion(), entradaMija, servidorMija);
    }

    private static List<Path> crearOriginales(Path carpetaMija) throws IOException {
        Files.createDirectories(carpetaMija);
        BufferedImage imagenMija = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream pngMija = new ByteArrayOutputStream();
        ImageIO.write(imagenMija, "png", pngMija);
        Files.write(carpetaMija.resolve("foto.png"), pngMija.toByteArray());

        Files.writeString(carpetaMija.resolve("notas.txt"), "Notas privadas " + MARCADOR_MIJA + "\n");

        ByteArrayOutputStream pdfMija = new ByteArrayOutputStream();
        try (PDDocument documentoMija = new PDDocument()) {
            for (int indiceMija = 1; indiceMija <= 2; indiceMija++) {
                PDPage paginaMija = new PDPage(PDRectangle.A4);
                documentoMija.addPage(paginaMija);
                try (PDPageContentStream contenidoMija = new PDPageContentStream(documentoMija, paginaMija)) {
                    contenidoMija.beginText();
                    contenidoMija.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 24);
                    contenidoMija.newLineAtOffset(72, 700);
                    contenidoMija.showText(MARCADOR_MIJA + " pagina " + indiceMija);
                    contenidoMija.endText();
                }
            }
            documentoMija.save(pdfMija);
        }
        Files.write(carpetaMija.resolve("informe.pdf"), pdfMija.toByteArray());

        ByteArrayOutputStream wordMija = new ByteArrayOutputStream();
        try (XWPFDocument documentoMija = new XWPFDocument()) {
            documentoMija.createParagraph().createRun().setText("Carta " + MARCADOR_MIJA);
            documentoMija.write(wordMija);
        }
        Files.write(carpetaMija.resolve("carta.docx"), wordMija.toByteArray());

        byte[] videoMija = new byte[1_000_000];
        new Random(5).nextBytes(videoMija);
        Files.write(carpetaMija.resolve("viaje.mp4"), videoMija);
        return List.of(carpetaMija.resolve("foto.png"), carpetaMija.resolve("notas.txt"),
                carpetaMija.resolve("informe.pdf"), carpetaMija.resolve("carta.docx"), carpetaMija.resolve("viaje.mp4"));
    }

    /** Archivos de los lugares del anfitrión que contienen el marcador del texto claro. */
    private static Set<Path> buscarMarcador(Path raizMija) throws IOException {
        Set<Path> hallazgosMija = new TreeSet<>();
        if (!Files.isDirectory(raizMija)) {
            return hallazgosMija;
        }
        byte[] marcadorMija = MARCADOR_MIJA.getBytes(StandardCharsets.ISO_8859_1);
        try (Stream<Path> recorridoMija = Files.walk(raizMija, 4)) {
            for (Path archivoMija : (Iterable<Path>) recorridoMija.filter(Files::isRegularFile)::iterator) {
                try {
                    if (Files.size(archivoMija) < (64L << 20) && indice(Files.readAllBytes(archivoMija), marcadorMija) >= 0) {
                        hallazgosMija.add(archivoMija);
                    }
                } catch (IOException _) {
                    // Archivo en uso por otro proceso (carpeta temporal compartida): no es nuestro.
                }
            }
        }
        // El propio texto de la prueba puede estar en la carpeta de la JVM de pruebas solo si se filtró.
        return hallazgosMija;
    }

    private static int indice(byte[] pajarMija, byte[] agujaMija) {
        outer:
        for (int desdeMija = 0; desdeMija <= pajarMija.length - agujaMija.length; desdeMija++) {
            for (int jMija = 0; jMija < agujaMija.length; jMija++) {
                if (pajarMija[desdeMija + jMija] != agujaMija[jMija]) {
                    continue outer;
                }
            }
            return desdeMija;
        }
        return -1;
    }

    /**
     * Foto del anfitrión: lo que había en la carpeta temporal real del sistema, ~/.openjfx, ~/.java y el perfil del
     * usuario, y lo que hay en el {@code java.io.tmpdir} de la JVM.
     *
     * <p>La carpeta temporal del sistema la usan otros programas a la vez; por eso solo se cuentan los archivos cuyo
     * nombre delata a CryptoKipu o a sus bibliotecas (los que el código de esta fase podría crear).
     */
    record Instantanea(Set<String> sistemaMija, Set<String> tmpDeLaJvmMija) {

        private static final List<String> SOSPECHOSOS_MIJA = List.of("cryptokipu", "kipu", "pdfbox", "fontbox", "poi-",
                "poifiles", "imageio", "hsperfdata", "foto.", "notas.", "informe.", "carta.", "viaje.", ".pdfbox.cache");

        static List<Path> sitiosDelAnfitrion() {
            Path perfilMija = Path.of(System.getProperty("user.home"));
            return List.of(sistemaTemporal(), perfilMija.resolve(".openjfx"), perfilMija.resolve(".java"));
        }

        static Path sistemaTemporal() {
            String sistemaMija = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (sistemaMija.contains("win")) {
                String temporalMija = Optional.ofNullable(System.getenv("TEMP")).orElse(System.getenv("TMP"));
                if (temporalMija != null) {
                    return Path.of(temporalMija);
                }
            }
            return Path.of("/tmp");
        }

        static Instantanea tomar() throws IOException {
            Set<String> sistemaMija = new TreeSet<>();
            Path perfilMija = Path.of(System.getProperty("user.home"));
            for (Path sitioMija : sitiosDelAnfitrion()) {
                listar(sitioMija, sitioMija.equals(sistemaTemporal()), sistemaMija);
            }
            try (Stream<Path> primerNivelMija = Files.list(perfilMija)) {
                primerNivelMija.map(rutaMija -> "perfil:" + rutaMija.getFileName()).forEach(sistemaMija::add);
            }
            Set<String> jvmMija = new TreeSet<>();
            Path tmpMija = Path.of(System.getProperty("java.io.tmpdir"));
            listar(tmpMija, false, jvmMija);
            // La caché de fuentes de PDFBox es una carpeta propia que se borra al salir; su contenido no cuenta.
            jvmMija.removeIf(rutaMija -> rutaMija.contains("cryptokipu-fuentes-"));
            return new Instantanea(sistemaMija, jvmMija);
        }

        private static void listar(Path raizMija, boolean soloSospechososMija, Set<String> destinoMija)
                throws IOException {
            if (!Files.isDirectory(raizMija)) {
                return;
            }
            try (Stream<Path> recorridoMija = Files.walk(raizMija, 3)) {
                for (Path rutaMija : (Iterable<Path>) recorridoMija.filter(candidataMija -> !candidataMija.equals(raizMija))
                        ::iterator) {
                    String nombreMija = rutaMija.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!soloSospechososMija || SOSPECHOSOS_MIJA.stream().anyMatch(nombreMija::contains)) {
                        destinoMija.add(raizMija.relativize(rutaMija).toString());
                    }
                }
            } catch (java.io.UncheckedIOException _) {
                // Carpeta que otro proceso borra mientras se recorre.
            }
        }
    }
}
