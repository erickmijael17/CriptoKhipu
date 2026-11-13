package dev.erick.cryptokipu.visores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erick.cryptokipu.app.puertos.VisorDocumentos;
import dev.erick.cryptokipu.app.puertos.VisorDocumentos.DocumentoAbierto;
import dev.erick.cryptokipu.app.puertos.VisorDocumentos.Imagen;
import dev.erick.cryptokipu.app.puertos.VisorDocumentos.Paginado;
import dev.erick.cryptokipu.app.puertos.VisorDocumentos.Textual;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.util.TempFile;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

/**
 * Los visores abren PDF, Word, Excel y PowerPoint SOLO desde un arreglo en memoria y no crean archivos temporales
 * (la JVM de pruebas usa {@code build/jvm-pruebas/tmp} como {@code java.io.tmpdir}, igual que el modo portátil).
 */
class VisoresEnMemoriaTest {

    private final VisoresEnMemoria visorMija = new VisoresEnMemoria();

    @Test
    void soportaSoloLosCuatroFormatosDeDocumento() {
        for (String extensionMija : List.of("pdf", "PDF", "docx", "xlsx", "pptx")) {
            assertTrue(visorMija.soporta(extensionMija), extensionMija);
        }
        for (String extensionMija : List.of("doc", "xls", "ppt", "txt", "png", "mp4", "")) {
            assertFalse(visorMija.soporta(extensionMija), extensionMija);
        }
    }

    @Test
    void elPdfSeRenderizaPaginaPorPagina() throws Exception {
        try (DocumentoAbierto abiertoMija = abrir("pdf", pdf(3))) {
            Paginado paginadoMija = assertInstanceOf(Paginado.class, abiertoMija);
            assertEquals(3, paginadoMija.paginas());
            Imagen paginaMija = paginadoMija.renderizar(1, 600);
            assertEquals(600, paginaMija.anchoMija(), "el ancho pedido se respeta");
            assertTrue(hayTinta(paginaMija), "la página tiene texto, no está en blanco");
        }
    }

    @Test
    void elAnchoPedidoSeLimitaParaNoReservarImagenesGigantes() throws Exception {
        try (DocumentoAbierto abiertoMija = abrir("pdf", pdf(1))) {
            Imagen paginaMija = ((Paginado) abiertoMija).renderizar(0, Integer.MAX_VALUE);
            assertTrue(paginaMija.anchoMija() <= 2400, "ancho " + paginaMija.anchoMija());
        }
    }

    @Test
    void wordSeMuestraComoTextoConSusTablas() throws Exception {
        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream();
        try (XWPFDocument documentoMija = new XWPFDocument()) {
            documentoMija.createParagraph().createRun().setText("Informe del quipu mayor");
            XWPFTable tablaMija = documentoMija.createTable(1, 2);
            tablaMija.getRow(0).getCell(0).setText("celda uno");
            tablaMija.getRow(0).getCell(1).setText("celda dos");
            documentoMija.write(salidaMija);
        }
        try (DocumentoAbierto abiertoMija = abrir("docx", salidaMija.toByteArray())) {
            String textoMija = assertInstanceOf(Textual.class, abiertoMija).contenidoMija();
            assertTrue(textoMija.contains("Informe del quipu mayor"), textoMija);
            assertTrue(textoMija.contains("celda uno") && textoMija.contains("celda dos"), textoMija);
        }
    }

    @Test
    void excelSeMuestraPorHojasYFilas() throws Exception {
        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream();
        try (XSSFWorkbook libroMija = new XSSFWorkbook()) {
            Sheet hojaMija = libroMija.createSheet("Presupuesto");
            hojaMija.createRow(0).createCell(0).setCellValue("Concepto");
            hojaMija.getRow(0).createCell(1).setCellValue(1234.5);
            hojaMija.createRow(1).createCell(0).setCellValue("Total");
            libroMija.write(salidaMija);
        }
        try (DocumentoAbierto abiertoMija = abrir("xlsx", salidaMija.toByteArray())) {
            String textoMija = assertInstanceOf(Textual.class, abiertoMija).contenidoMija();
            assertTrue(textoMija.contains("== Presupuesto =="), textoMija);
            assertTrue(textoMija.contains("Concepto") && textoMija.contains("Total"), textoMija);
        }
    }

    @Test
    void powerPointSeDibujaComoImagenSinPantalla() throws Exception {
        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream();
        try (XMLSlideShow presentacionMija = new XMLSlideShow()) {
            for (int indiceMija = 1; indiceMija <= 2; indiceMija++) {
                XSLFSlide diapositivaMija = presentacionMija.createSlide();
                XSLFTextBox cajaMija = diapositivaMija.createTextBox();
                cajaMija.setAnchor(new Rectangle2D.Double(40, 40, 500, 120));
                cajaMija.setText("Diapositiva " + indiceMija + " del quipu");
            }
            presentacionMija.write(salidaMija);
        }
        try (DocumentoAbierto abiertoMija = abrir("pptx", salidaMija.toByteArray())) {
            Paginado paginadoMija = assertInstanceOf(Paginado.class, abiertoMija);
            assertEquals(2, paginadoMija.paginas());
            Imagen diapositivaMija = paginadoMija.renderizar(0, 800);
            assertEquals(800, diapositivaMija.anchoMija());
            assertTrue(hayTinta(diapositivaMija));
        }
    }

    @Test
    void abrirYRenderizarNoCreaArchivosTemporales() throws Exception {
        Set<String> antesMija = contenidoTmp();
        try (DocumentoAbierto pdfMija = abrir("pdf", pdf(2))) {
            ((Paginado) pdfMija).renderizar(0, 500);
            ((Paginado) pdfMija).renderizar(1, 500);
        }
        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream();
        try (XSSFWorkbook libroMija = new XSSFWorkbook()) {
            libroMija.createSheet("a").createRow(0).createCell(0).setCellValue("x");
            libroMija.write(salidaMija);
        }
        abrir("xlsx", salidaMija.toByteArray()).close();
        assertEquals(antesMija, contenidoTmp(), "PDFBox y POI no pueden escribir en java.io.tmpdir");
    }

    @Test
    void poiNoPuedeCrearTemporales() {
        // Mutación: sin la estrategia que lanza, POI crearía un archivo en java.io.tmpdir y este test falla.
        ConfiguracionSegura.aplicar();
        assertThrows(IOException.class, () -> TempFile.createTempFile("poi", ".tmp"));
        assertThrows(IOException.class, () -> TempFile.createTempDirectory("poi"));
    }

    @Test
    void imageIoNoUsaCacheEnDisco() {
        // Mutación: con ImageIO.setUseCache(true) las imágenes grandes se decodificarían vía archivos temporales.
        ConfiguracionSegura.aplicar();
        assertFalse(ImageIO.getUseCache());
    }

    @Test
    void laCacheDeFlujosDePdfBoxEsSoloMemoria() throws Exception {
        // Mutación: createTempFileOnlyStreamCache() deja archivos en java.io.tmpdir y este test falla.
        Set<String> antesMija = contenidoTmp();
        try (var cacheMija = VisoresEnMemoria.CACHE_PDF_MIJA.create(); var bufferMija = cacheMija.createBuffer()) {
            byte[] bloqueMija = new byte[1 << 20];
            for (int indiceMija = 0; indiceMija < 20; indiceMija++) {
                bufferMija.write(bloqueMija);
            }
            assertEquals(20L << 20, bufferMija.length());
            assertEquals(antesMija, contenidoTmp());
        }
    }

    @Test
    void unaBombaZipSeRechazaAntesDeDescomprimirla() throws Exception {
        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream();
        try (ZipOutputStream zipMija = new ZipOutputStream(salidaMija)) {
            zipMija.putNextEntry(new ZipEntry("[Content_Types].xml"));
            byte[] ceroMija = new byte[1 << 20];
            for (int indiceMija = 0; indiceMija < 64; indiceMija++) { // 64 MiB de ceros: comprime ~1000:1
                zipMija.write(ceroMija);
            }
            zipMija.closeEntry();
        }
        byte[] bombaMija = salidaMija.toByteArray();
        assertTrue(bombaMija.length < 200_000, "la bomba es pequeña comprimida: " + bombaMija.length);
        for (String extensionMija : List.of("docx", "xlsx", "pptx")) {
            assertThrows(Exception.class, () -> abrir(extensionMija, bombaMija).close(), extensionMija);
        }
    }

    @Test
    void unDocumentoMasGrandeQueElLimiteNoSeLee() {
        CanalEnMemoria canalMija = new CanalEnMemoria(new byte[16]);
        assertThrows(IOException.class, () -> visorMija.abrir("pdf", canalMija, VisorDocumentos.TAMANO_MAXIMO_MIJA + 1));
        assertThrows(IOException.class, () -> visorMija.abrir("pdf", canalMija, -1));
    }

    @Test
    void unPdfDanadoFallaConIoException() {
        byte[] basuraMija = "%PDF-1.7 esto no es un pdf".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        assertThrows(IOException.class, () -> abrir("pdf", basuraMija));
    }

    // ------------------------------------------------------------------ ayudas

    private DocumentoAbierto abrir(String extensionMija, byte[] contenidoMija) throws IOException {
        return visorMija.abrir(extensionMija, new CanalEnMemoria(contenidoMija), contenidoMija.length);
    }

    private static byte[] pdf(int paginasMija) throws IOException {
        ByteArrayOutputStream salidaMija = new ByteArrayOutputStream();
        try (PDDocument documentoMija = new PDDocument()) {
            for (int indiceMija = 1; indiceMija <= paginasMija; indiceMija++) {
                PDPage paginaMija = new PDPage(PDRectangle.A4);
                documentoMija.addPage(paginaMija);
                try (PDPageContentStream contenidoMija = new PDPageContentStream(documentoMija, paginaMija)) {
                    contenidoMija.beginText();
                    contenidoMija.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 36);
                    contenidoMija.newLineAtOffset(72, 700);
                    contenidoMija.showText("Pagina " + indiceMija);
                    contenidoMija.endText();
                }
            }
            documentoMija.save(salidaMija);
        }
        return salidaMija.toByteArray();
    }

    /** Hay al menos un píxel que no es blanco. */
    private static boolean hayTinta(Imagen imagenMija) {
        return Arrays.stream(imagenMija.argbMija()).anyMatch(pixelMija -> (pixelMija & 0xFFFFFF) != 0xFFFFFF);
    }

    /** Rutas (relativas) de todo lo que hay en {@code java.io.tmpdir}, salvo la caché de fuentes de PDFBox. */
    private static Set<String> contenidoTmp() throws IOException {
        Path tmpMija = Path.of(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> recorridoMija = Files.walk(tmpMija)) {
            return recorridoMija.filter(rutaMija -> !rutaMija.equals(tmpMija))
                    .map(rutaMija -> tmpMija.relativize(rutaMija).toString())
                    .filter(rutaMija -> !rutaMija.startsWith("cryptokipu-fuentes-"))
                    .collect(Collectors.toSet());
        }
    }
}
